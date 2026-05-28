package io.github.decentralizedidentity.didwebvh.core.interop;

import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.validate.LogChainValidator;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interop regression: when Key Pre-Rotation is active, the spec
 * (§3.7.5, lines 1078-1082) requires that each subsequent log entry be
 * signed by the {@code updateKeys} of the <i>current</i> entry — not the
 * previous one. The Rust and Java-EECC pre-rotation-consume vectors exercise
 * this: every entry rotates to a new key whose hash was committed by the
 * previous entry's {@code nextKeyHashes}, and the new entry is signed by its
 * own key.
 *
 * <p>Source: swcurran/didwebvh-test-suite, vectors/pre-rotation-consume/&lt;impl&gt;/did.jsonl.
 */
class InteropPreRotationConsumeTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"rust", "java-eecc"})
    void validatesPreRotationConsumeLog(String impl) throws IOException {
        String jsonl = readVector("/interop/pre-rotation-consume-" + impl + "/did.jsonl");
        List<LogEntry> entries = parseJsonl(jsonl);

        ValidationResult result = new LogChainValidator().validate(entries, null);

        assertThat(result.isValid())
                .as("pre-rotation-consume %s log must validate; failure: %s", impl, result.getFailureReason())
                .isTrue();
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
        try (InputStream in = InteropPreRotationConsumeTest.class.getResourceAsStream(resourcePath)) {
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
