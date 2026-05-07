package io.github.decentralizedidentity.didwebvh.core.integration;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVh;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.create.CreateDidResult;
import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.crypto.PreRotationHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.update.UpdateDidResult;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Produces the committed test-vector files under
 * {@code didwebvh-core/src/test/resources/test-vectors/} using deterministic
 * Ed25519 seeds from {@link TestVectors}.
 *
 * <p>The vectors bake in a timestamp captured at generation time, but the
 * signing keys and therefore every derived SCID / entry-hash / proof is
 * reproducible. Re-running the {@code main} method regenerates all files
 * (timestamps will shift, SCIDs will change — commit the new output).
 */
public final class TestVectorGenerator {

    private TestVectorGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path root = args.length > 0
                ? Paths.get(args[0])
                : Paths.get("didwebvh-core/src/test/resources/test-vectors");
        Files.createDirectories(root);
        generate(root);
        System.out.println("Wrote test vectors to " + root.toAbsolutePath());
    }

    /** Regenerate every vector file into {@code outDir}. */
    static void generate(Path outDir) throws IOException {
        writeFirstLogEntryGood(outDir);
        writeFirstLogEntryTampered(outDir);
        writeMultiEntryLogAndWitness(outDir);
        writeDeactivatedDid(outDir);
        writeMigratedDid(outDir);
        writePreRotationLog(outDir);
    }

    private static void writeFirstLogEntryGood(Path outDir) throws IOException {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        writeJsonl(outDir.resolve("first-log-entry-good.jsonl"),
                Collections.singletonList(create.getLogLine()));
    }

    private static void writeFirstLogEntryTampered(Path outDir) throws IOException {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        // Tamper the DID Document id by appending a character. The proof
        // hash and entry hash will no longer match.
        LogEntry entry = LogEntry.fromJsonLine(create.getLogLine());
        JsonObject state = entry.getState();
        state.addProperty("id", state.get("id").getAsString() + "x");
        writeJsonl(outDir.resolve("first-log-entry-tampered.jsonl"),
                Collections.singletonList(entry.toJsonLine()));
    }

    private static void writeMultiEntryLogAndWitness(Path outDir) throws IOException {
        Signer author = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        Signer witnessSigner = TestVectors.seededSigner(TestVectors.WITNESS_SEED);
        String witnessDid = "did:key:" + multikeyOf(witnessSigner);
        WitnessConfig witness = new WitnessConfig(1,
                Collections.singletonList(new WitnessEntry(witnessDid)));

        CreateDidResult create = DidWebVh.create("example.com", author)
                .witness(witness)
                .execute();

        DidWebVhState state = DidWebVhState.from(create.getDid(), create.getLogEntry());
        JsonObject v2Doc = create.getLogEntry().getState().deepCopy();
        v2Doc.addProperty("alsoKnownAs-note", "update 1");
        UpdateDidResult up1 = DidWebVh.update(state, author)
                .newDocument(v2Doc)
                .execute();
        for (LogEntry e : up1.getNewEntries()) {
            state.appendEntry(e);
        }

        JsonObject v3Doc = state.getLastEntry().getState().deepCopy();
        v3Doc.addProperty("alsoKnownAs-note", "update 2");
        UpdateDidResult up2 = DidWebVh.update(state, author)
                .newDocument(v3Doc)
                .execute();
        for (LogEntry e : up2.getNewEntries()) {
            state.appendEntry(e);
        }

        List<String> lines = new java.util.ArrayList<>();
        for (LogEntry entry : state.getLogEntries()) {
            lines.add(entry.toJsonLine());
        }
        writeJsonl(outDir.resolve("multi-entry-log.jsonl"), lines);

        // Witness proofs for each version
        java.util.List<WitnessProofEntry> proofEntries = new java.util.ArrayList<>();
        for (LogEntry entry : state.getLogEntries()) {
            JsonObject signed = new JsonObject();
            signed.addProperty("versionId", entry.getVersionId());
            DataIntegrityProof wp = DataIntegrityProof.defaults()
                    .setVerificationMethod(witnessSigner.verificationMethod())
                    .setCreated(Instant.now().toString());
            byte[] hashData = ProofGenerator.buildHashData(wp, signed);
            byte[] sig = witnessSigner.sign(hashData);
            wp.setProofValue(Base58Btc.encodeMultibase(sig));
            proofEntries.add(new WitnessProofEntry(entry.getVersionId(),
                    Collections.singletonList(wp)));
        }
        String witnessJson = JsonSupport.compact().toJson(proofEntries);
        Files.write(outDir.resolve("multi-entry-witness.json"),
                witnessJson.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeDeactivatedDid(Path outDir) throws IOException {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer).execute();
        DidWebVhState state = DidWebVhState.from(create.getDid(), create.getLogEntry());

        UpdateDidResult deactivation = DidWebVh.deactivate(state, signer).execute();
        for (LogEntry e : deactivation.getNewEntries()) {
            state.appendEntry(e);
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (LogEntry e : state.getLogEntries()) {
            lines.add(e.toJsonLine());
        }
        writeJsonl(outDir.resolve("deactivated-did.jsonl"), lines);
    }

    private static void writeMigratedDid(Path outDir) throws IOException {
        Signer signer = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        CreateDidResult create = DidWebVh.create("example.com", signer)
                .portable(true)
                .execute();
        DidWebVhState state = DidWebVhState.from(create.getDid(), create.getLogEntry());

        UpdateDidResult migrated = DidWebVh.migrate(state, signer, "new.example.com")
                .execute();
        for (LogEntry e : migrated.getNewEntries()) {
            state.appendEntry(e);
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (LogEntry e : state.getLogEntries()) {
            lines.add(e.toJsonLine());
        }
        writeJsonl(outDir.resolve("migrated-did.jsonl"), lines);
    }

    private static void writePreRotationLog(Path outDir) throws IOException {
        Signer author = TestVectors.seededSigner(TestVectors.AUTHOR_SEED);
        Signer next = TestVectors.seededSigner(TestVectors.NEXT_SEED);

        String nextMultikey = multikeyOf(next);
        String nextKeyHash = PreRotationHashGenerator.generateHash(nextMultikey);

        CreateDidResult create = DidWebVh.create("example.com", author)
                .nextKeyHashes(Collections.singletonList(nextKeyHash))
                .execute();
        DidWebVhState state = DidWebVhState.from(create.getDid(), create.getLogEntry());

        // Rotate: reveal next key and commit another future rotation.
        String futureKeyHash = PreRotationHashGenerator.generateHash(
                multikeyOf(TestVectors.seededSigner(TestVectors.UPDATE_SEED)));
        Parameters rotation = new Parameters()
                .setUpdateKeys(Collections.singletonList(nextMultikey))
                .setNextKeyHashes(Collections.singletonList(futureKeyHash));
        UpdateDidResult rot = DidWebVh.update(state, author)
                .changedParameters(rotation)
                .execute();
        for (LogEntry e : rot.getNewEntries()) {
            state.appendEntry(e);
        }

        java.util.List<String> lines = new java.util.ArrayList<>();
        for (LogEntry e : state.getLogEntries()) {
            lines.add(e.toJsonLine());
        }
        writeJsonl(outDir.resolve("pre-rotation-log.jsonl"), lines);
    }

    private static String multikeyOf(Signer signer) {
        String vm = signer.verificationMethod();
        int hash = vm.indexOf('#');
        return hash >= 0 ? vm.substring(hash + 1) : vm.substring("did:key:".length());
    }

    private static void writeJsonl(Path file, List<String> lines) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        sb.append('\n');
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }

}
