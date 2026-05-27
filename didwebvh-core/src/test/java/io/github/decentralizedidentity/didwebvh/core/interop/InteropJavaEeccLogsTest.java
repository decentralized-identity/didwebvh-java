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
 * Interop regression: java-eecc-produced logs were reported as failing with
 * "versionTime must be after previous entry at entry N" — see GitHub issue #2.
 * Investigation showed the root cause was canonicalization mismatch on the
 * empty {@code witness: {}} parameter, which short-circuited validation before
 * the versionTime check; once the witness round-trip was fixed, these logs
 * validate. This test pins that behaviour.
 *
 * <p>Source: swcurran/didwebvh-test-suite, vectors/&lt;name&gt;/java-eecc/did.jsonl.
 */
class InteropJavaEeccLogsTest {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "basic-update",
            "deactivate",
            "key-rotation",
            "multi-update",
            "services",
            "witness-update"
    })
    void validatesJavaEeccLog(String vector) throws IOException {
        String jsonl = readVector("/interop/" + vector + "-java-eecc/did.jsonl");
        List<LogEntry> entries = parseJsonl(jsonl);

        ValidationResult result = new LogChainValidator().validate(entries, null);

        assertThat(result.isValid())
                .as("java-eecc %s log must validate; failure: %s", vector, result.getFailureReason())
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
        try (InputStream in = InteropJavaEeccLogsTest.class.getResourceAsStream(resourcePath)) {
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
