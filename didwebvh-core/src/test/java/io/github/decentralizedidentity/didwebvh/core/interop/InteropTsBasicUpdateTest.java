package io.github.decentralizedidentity.didwebvh.core.interop;

import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.validate.LogChainValidator;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interop regression: TS implementation always serialises
 * {@code "nextKeyHashes": []} and {@code "witness": {}} (plus
 * {@code "watchers": []}) even when those features are not configured.
 * The Java log-chain validator must treat these as the "feature not active"
 * case, not reject them.
 *
 * <p>Source: swcurran/didwebvh-test-suite, vectors/basic-update/ts/did.jsonl.
 */
class InteropTsBasicUpdateTest {

    @Test
    void validatesTsBasicUpdateLog() throws IOException {
        String jsonl = readVector("/interop/basic-update-ts/did.jsonl");
        List<LogEntry> entries = parseJsonl(jsonl);

        ValidationResult result = new LogChainValidator().validate(entries, null);

        assertThat(result.isValid())
                .as("TS basic-update log must validate; failure: %s", result.getFailureReason())
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
        try (InputStream in = InteropTsBasicUpdateTest.class.getResourceAsStream(resourcePath)) {
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
