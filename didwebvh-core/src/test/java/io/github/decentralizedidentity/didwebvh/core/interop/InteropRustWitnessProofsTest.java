package io.github.decentralizedidentity.didwebvh.core.interop;

import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidationResult;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidator;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interop regression: per spec §3.7.8 (lines 1244-1247, 1298-1304), a witness
 * proof at versionId V implicitly approves all prior log entries; the DID
 * Controller SHOULD prune did-witness.json to keep only the latest proof per
 * witness. Resolvers MUST accept this. The Rust witness-update vector has a
 * single proof entry at versionId 2 covering both log entries; the Rust
 * witness-threshold vector encodes witness ids as bare multikeys rather than
 * the {@code did:key:&lt;multikey&gt;} form.
 *
 * <p>Source: swcurran/didwebvh-test-suite, vectors/{witness-update,witness-threshold}/rust/.
 */
class InteropRustWitnessProofsTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"witness-update", "witness-threshold"})
    void validatesRustWitnessProofs(String vector) throws IOException {
        List<LogEntry> entries = parseJsonl(readVector("/interop/" + vector + "-rust/did.jsonl"));
        WitnessProofCollection proofs = parseProofs(readVector("/interop/" + vector + "-rust/did-witness.json"));

        WitnessValidationResult result = new WitnessValidator().validate(entries, proofs, 0);

        assertThat(result.isValid())
                .as("rust %s witness proofs must verify; failure: %s", vector, result.getFailureReason())
                .isTrue();
    }

    private static WitnessProofCollection parseProofs(String json) {
        WitnessProofEntry[] arr = JsonSupport.compact().fromJson(json, WitnessProofEntry[].class);
        return new WitnessProofCollection(Arrays.asList(arr));
    }

    private static List<LogEntry> parseJsonl(String jsonl) {
        List<LogEntry> out = new ArrayList<>();
        for (String line : jsonl.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                out.add(LogEntry.fromJsonLine(trimmed));
            }
        }
        return out;
    }

    private static String readVector(String resourcePath) throws IOException {
        try (InputStream in = InteropRustWitnessProofsTest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + resourcePath);
            }
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
