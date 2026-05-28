package io.github.decentralizedidentity.didwebvh.core.interop;

import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidationResult;
import io.github.decentralizedidentity.didwebvh.core.validate.WitnessValidator;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interop regression: a witness proof from DID-B replayed verbatim into DID-A's
 * {@code did-witness.json} must be rejected. The witness signing payload
 * ({@code {"versionId": "<hash>"}}) carries no DID binding, so resolvers must
 * filter proofs to those whose versionId is present in <em>this</em> log
 * before counting them toward the threshold (spec §3.7.8). When the proof's
 * versionId is not in this log, the entry's witness threshold cannot be met
 * and resolution fails with {@code invalidDid}.
 *
 * <p>Source: swcurran/didwebvh-test-suite,
 * vectors/negative-cross-did-witness-replay/ts/.
 */
class InteropNegativeCrossDidWitnessReplayTest {

    @Test
    void replayedCrossDidWitnessProofIsRejected() throws IOException {
        List<LogEntry> entries = parseJsonl(readVector(
                "/interop/negative-cross-did-witness-replay-ts/did.jsonl"));
        WitnessProofCollection proofs = parseProofs(readVector(
                "/interop/negative-cross-did-witness-replay-ts/did-witness.json"));

        WitnessValidationResult result = new WitnessValidator().validate(entries, proofs, 0);

        assertThat(result.isValid())
                .as("replayed cross-DID witness proof must be rejected; got: %s",
                        result.getFailureReason())
                .isFalse();
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
        try (InputStream in = InteropNegativeCrossDidWitnessReplayTest.class
                .getResourceAsStream(resourcePath)) {
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
