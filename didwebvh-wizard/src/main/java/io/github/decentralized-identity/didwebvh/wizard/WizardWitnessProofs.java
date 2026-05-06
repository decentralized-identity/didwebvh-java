package io.github.decentralizedidentity.didwebvh.wizard;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;
import io.github.decentralizedidentity.didwebvh.signing.local.LocalKeySigner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects witness proofs for newly appended log entries and maintains the
 * {@code did-witness.json} file next to {@code did.jsonl} (spec section 3.7.8).
 *
 * <p>Per spec, proofs are over the JCS-canonicalized document
 * {@code {"versionId": "<versionId>"}}, and the {@code did-witness.json} file MUST be
 * published before the corresponding {@code did.jsonl} entry.
 */
final class WizardWitnessProofs {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    private final WizardIo io;
    private final WizardPrompts ask;

    WizardWitnessProofs(WizardIo io) {
        this.io = io;
        this.ask = new WizardPrompts(io);
    }

    /**
     * For each new entry that needs witnessing, prompt the user for witness signer keys
     * and write an updated {@code did-witness.json}.  Throws {@link WizardException} if
     * the threshold cannot be reached.
     *
     * @param priorParams effective parameters AFTER all existing log entries, BEFORE the
     *                    first new entry (i.e. {@code existingState.accumulateParameters()})
     * @param newEntries  new entries about to be appended (in order)
     * @param workDir     directory that holds {@code did-witness.json}
     */
    void collectForNewEntries(Parameters priorParams, List<LogEntry> newEntries, Path workDir) {
        Parameters accumulated = priorParams;
        List<WitnessProofEntry> newProofEntries = new ArrayList<>();
        for (LogEntry entry : newEntries) {
            WitnessConfig authorized = authorizedWitnesses(accumulated, entry.getParameters());
            if (authorized != null && authorized.isActive()) {
                newProofEntries.add(buildProofEntry(entry.getVersionId(), authorized, workDir));
            }
            if (entry.getParameters() != null) {
                accumulated = accumulated.merge(entry.getParameters());
            }
        }
        if (newProofEntries.isEmpty()) {
            return;
        }
        Path witnessPath = workDir.resolve(WizardFiles.DID_WITNESS);
        List<WitnessProofEntry> merged = mergeWithExisting(witnessPath, newProofEntries);
        WizardFiles.write(witnessPath, serialize(merged));
        io.println("Wrote " + newProofEntries.size() + " witness proof entr"
                + (newProofEntries.size() == 1 ? "y" : "ies")
                + " to " + WizardFiles.DID_WITNESS);
    }

    /**
     * Per spec: witness proofs are required when either the merged config AFTER this
     * entry's delta is active (normal case) OR when this entry sets witness back to
     * {@code {}} while it was previously active (transition-off case).  In both cases
     * the authorized witness set for signing is the previously active set when turning
     * witnesses off, otherwise the newly merged set.
     */
    private WitnessConfig authorizedWitnesses(Parameters priorParams, Parameters entryDelta) {
        WitnessConfig priorActive = priorParams.getWitness();
        Parameters merged = priorParams.merge(entryDelta);
        WitnessConfig mergedActive = merged.getWitness();
        boolean turningOff = priorActive != null && priorActive.isActive()
                && (mergedActive == null || !mergedActive.isActive());
        if (turningOff) {
            return priorActive;
        }
        return mergedActive;
    }

    private WitnessProofEntry buildProofEntry(String versionId, WitnessConfig authorized,
                                              Path workDir) {
        io.println("");
        io.println("Entry " + versionId + " requires witness approval.");
        io.println("  Threshold: " + authorized.getThreshold()
                + " of " + authorized.getWitnesses().size() + " witness(es).");
        JsonObject signedDoc = new JsonObject();
        signedDoc.addProperty("versionId", versionId);

        List<DataIntegrityProof> proofs = new ArrayList<>();
        java.util.Set<String> signedBy = new java.util.HashSet<>();

        // Auto-sign with every stored witness secret that matches the authorized set.
        // We intentionally do not stop at the threshold: a valid proof from a witness
        // we can actually sign for should always be published, both to keep the log
        // robust against one of the other witnesses becoming unreachable and because
        // the threshold is a lower bound, not an upper bound.
        for (WitnessEntry w : authorized.getWitnesses()) {
            String multikey = w.getId().substring("did:key:".length());
            LocalKeySigner stored = WizardWitnessKeys.findByMultikey(workDir, multikey);
            if (stored != null) {
                proofs.add(signProof(stored, signedDoc));
                signedBy.add(w.getId());
                io.println("  Auto-signed with stored secret for " + w.getId());
            }
        }

        // If threshold not yet met, prompt for additional witness secret paths.
        while (proofs.size() < authorized.getThreshold()) {
            int remaining = authorized.getThreshold() - proofs.size();
            io.println("");
            io.println("  Remaining authorized witnesses without proofs:");
            for (WitnessEntry w : authorized.getWitnesses()) {
                if (!signedBy.contains(w.getId())) {
                    io.println("    - " + w.getId());
                }
            }
            String keyPathStr = ask.askRequired(
                    "Path to witness signing-key JSON (" + remaining + " more needed): ");
            LocalKeySigner witnessSigner;
            try {
                witnessSigner = LocalKeySigner.fromJson(WizardFiles.read(Path.of(keyPathStr)));
            } catch (RuntimeException e) {
                io.printError("Could not load witness key: " + e.getMessage());
                continue;
            }
            String witnessDid = "did:key:" + witnessSigner.getPublicKeyMultikey();
            if (!isAuthorized(witnessDid, authorized.getWitnesses())) {
                io.printError(witnessDid + " is not in the authorized witness list.");
                continue;
            }
            if (signedBy.contains(witnessDid)) {
                io.printError(witnessDid + " already provided a proof.");
                continue;
            }
            WizardWitnessKeys.save(workDir, witnessSigner);
            proofs.add(signProof(witnessSigner, signedDoc));
            signedBy.add(witnessDid);
            io.println("Accepted proof from " + witnessDid);
        }
        return new WitnessProofEntry(versionId, proofs);
    }

    private boolean isAuthorized(String witnessDid, List<WitnessEntry> authorized) {
        for (WitnessEntry w : authorized) {
            if (witnessDid.equals(w.getId())) {
                return true;
            }
        }
        return false;
    }

    private DataIntegrityProof signProof(LocalKeySigner signer, JsonObject signedDoc) {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod(signer.verificationMethod())
                .setCreated(ISO_UTC.format(Instant.now()));
        byte[] hashData = ProofGenerator.buildHashData(proof, signedDoc);
        byte[] signature = signer.sign(hashData);
        proof.setProofValue(Base58Btc.encodeMultibase(signature));
        return proof;
    }

    private List<WitnessProofEntry> mergeWithExisting(Path witnessPath,
                                                      List<WitnessProofEntry> newEntries) {
        Map<String, WitnessProofEntry> byVersion = new LinkedHashMap<>();
        if (Files.exists(witnessPath)) {
            WitnessProofEntry[] arr = JsonSupport.compact().fromJson(
                    WizardFiles.read(witnessPath), WitnessProofEntry[].class);
            if (arr != null) {
                for (WitnessProofEntry e : arr) {
                    byVersion.put(e.getVersionId(), e);
                }
            }
        }
        for (WitnessProofEntry e : newEntries) {
            byVersion.put(e.getVersionId(), e);
        }
        return new ArrayList<>(byVersion.values());
    }

    private String serialize(List<WitnessProofEntry> entries) {
        JsonArray arr = new JsonArray();
        for (WitnessProofEntry e : entries) {
            arr.add(JsonSupport.compact().toJsonTree(e));
        }
        return JsonSupport.compact().toJson(arr);
    }
}
