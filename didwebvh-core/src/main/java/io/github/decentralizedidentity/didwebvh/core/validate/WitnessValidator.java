package io.github.decentralizedidentity.didwebvh.core.validate;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.model.VersionId;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofVerifier;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Validates witness proofs for entries that require witnessing. */
public final class WitnessValidator {

    /**
     * Validate witness proofs for entries starting at {@code fromEntryIndex}.
     *
     * <p>Spec §3.7.8 (lines 1244-1247, 1286-1304): a witness proof at versionId V
     * implicitly approves every prior log entry. The DID Controller is expected
     * to prune {@code did-witness.json} so only the latest proof per witness
     * remains, and resolvers MUST accept this. Proofs whose {@code versionId} is
     * not present in the published log are ignored.
     *
     * <p>For each log entry with active witnessing, this validator counts the
     * distinct authorized witnesses that have signed at least one valid proof
     * at a versionId in the published log whose version number is greater than
     * or equal to the entry's. If that count meets the entry's threshold the
     * entry is witnessed.
     *
     * @param entries         the full log entry list (used to accumulate parameters)
     * @param witnessProofs   the witness proof collection from {@code did-witness.json}
     * @param fromEntryIndex  index of the first entry to check (inclusive)
     * @return the validation result
     */
    public WitnessValidationResult validate(List<LogEntry> entries,
                                            WitnessProofCollection witnessProofs,
                                            int fromEntryIndex) {
        if (witnessProofs == null) {
            return WitnessValidationResult.failure(-1, "witness proofs collection is null");
        }

        Map<String, Integer> publishedVersionNumbers = publishedVersionNumbers(entries);
        List<VerifiedProof> verifiedProofs = verifyProofs(witnessProofs, publishedVersionNumbers);

        Parameters activeParams = new Parameters();
        for (int i = 0; i < fromEntryIndex && i < entries.size(); i++) {
            Parameters ep = entries.get(i).getParameters();
            if (ep != null) {
                activeParams = activeParams.merge(ep);
            }
        }

        for (int i = fromEntryIndex; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            Parameters entryParams = entry.getParameters() != null
                    ? entry.getParameters() : new Parameters();
            WitnessConfig priorConfig = activeParams.getWitness();
            activeParams = activeParams.merge(entryParams);
            WitnessConfig afterConfig = activeParams.getWitness();

            // Spec §3.7.5 (lines 884-889): a replacement witness list "becomes
            // active AFTER the new DID log has been published". So while
            // witnesses are already active, every entry — including one that
            // changes the list (e.g. two witnesses down to one) or sets it to
            // {} — MUST be witnessed by the list in effect BEFORE this entry;
            // the replacement only governs subsequent entries. This also stops
            // an attacker from shrinking or disabling oversight in a single
            // forged entry and slipping it past the resolver. The sole
            // exception is the first activation, where there is no prior list.
            boolean priorActive = priorConfig != null && priorConfig.isActive();
            boolean afterActive = afterConfig != null && afterConfig.isActive();
            WitnessConfig witnessConfig;
            if (priorActive) {
                witnessConfig = priorConfig;
            } else if (afterActive) {
                // First activation ({} -> active): the new list approves this entry.
                witnessConfig = afterConfig;
            } else {
                continue;
            }

            int entryVersion = VersionId.parse(entry.getVersionId()).getVersionNumber();
            List<WitnessEntry> authorized = witnessConfig.getWitnesses();

            Set<String> approvers = new HashSet<>();
            for (VerifiedProof vp : verifiedProofs) {
                if (vp.versionNumber < entryVersion) {
                    continue;
                }
                String matched = matchAuthorizedWitness(vp.signerMultikey, authorized);
                if (matched != null) {
                    approvers.add(matched);
                }
            }

            if (approvers.size() < witnessConfig.getThreshold()) {
                return WitnessValidationResult.failure(i,
                        "insufficient witness proofs for entry " + entry.getVersionId()
                                + ": need " + witnessConfig.getThreshold()
                                + ", got " + approvers.size());
            }
        }

        return WitnessValidationResult.success();
    }

    /** Map from each entry's full versionId string to its parsed version number. */
    private static Map<String, Integer> publishedVersionNumbers(List<LogEntry> entries) {
        Map<String, Integer> out = new HashMap<>();
        for (LogEntry e : entries) {
            out.put(e.getVersionId(), VersionId.parse(e.getVersionId()).getVersionNumber());
        }
        return out;
    }

    /**
     * Verify every proof in the witness file once and keep the ones that
     * (a) are published, (b) verify against {"versionId": "<vid>"}. Returns one
     * entry per verified proof with the signer's multikey already extracted.
     */
    private static List<VerifiedProof> verifyProofs(WitnessProofCollection witnessProofs,
                                                    Map<String, Integer> publishedVersionNumbers) {
        List<VerifiedProof> out = new java.util.ArrayList<>();
        for (WitnessProofEntry entry : witnessProofs.getEntries()) {
            Integer versionNumber = publishedVersionNumbers.get(entry.getVersionId());
            if (versionNumber == null) {
                // Spec line 1294: ignore proofs for unpublished entries.
                continue;
            }
            JsonObject signedDoc = new JsonObject();
            signedDoc.addProperty("versionId", entry.getVersionId());
            if (entry.getProof() == null) {
                continue;
            }
            for (DataIntegrityProof proof : entry.getProof()) {
                if (!ProofVerifier.verify(proof, signedDoc)) {
                    continue;
                }
                String signerMultikey = ProofVerifier.extractMultikey(proof.getVerificationMethod());
                out.add(new VerifiedProof(versionNumber, signerMultikey));
            }
        }
        return out;
    }

    /**
     * Return the canonical authorized-witness id matching {@code signerMultikey},
     * or {@code null} if not authorized. Accepts the spec form
     * {@code did:key:&lt;multikey&gt;} and the bare-multikey form some
     * implementations emit; comparison is on the multikey portion.
     */
    private static String matchAuthorizedWitness(String signerMultikey,
                                                 List<WitnessEntry> authorized) {
        for (WitnessEntry w : authorized) {
            if (signerMultikey.equals(stripDidKey(w.getId()))) {
                return w.getId();
            }
        }
        return null;
    }

    private static String stripDidKey(String witnessId) {
        if (witnessId == null) {
            return null;
        }
        String prefix = "did:key:";
        return witnessId.startsWith(prefix) ? witnessId.substring(prefix.length()) : witnessId;
    }

    /** Pre-verified proof entry indexed by its (published) version number and signer. */
    private static final class VerifiedProof {
        final int versionNumber;
        final String signerMultikey;

        VerifiedProof(int versionNumber, String signerMultikey) {
            this.versionNumber = versionNumber;
            this.signerMultikey = signerMultikey;
        }
    }
}
