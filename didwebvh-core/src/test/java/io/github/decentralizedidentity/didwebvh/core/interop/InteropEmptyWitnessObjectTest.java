package io.github.decentralizedidentity.didwebvh.core.interop;

import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Interop regression: Python and TS implementations serialize an empty
 * {@code "witness": {}} object in parameters when no witnesses are configured.
 * The spec permits this (an empty object means "no witnesses"); the Java
 * library used to NPE because Gson bypasses the no-args constructor and left
 * the internal {@code witnesses} list as {@code null}.
 *
 * <p>Source: swcurran/didwebvh-test-suite, vectors/basic-create/python/did.jsonl.
 */
class InteropEmptyWitnessObjectTest {

    @Test
    void parsesEmptyWitnessObjectWithoutNpe() throws IOException {
        String line = readVector("/interop/basic-create-python/did.jsonl").trim();

        LogEntry entry = LogEntry.fromJsonLine(line);

        WitnessConfig witness = entry.getParameters().getWitness();
        assertThat(witness).isNotNull();
        assertThatCode(witness::isActive).doesNotThrowAnyException();
        assertThat(witness.isActive()).isFalse();
        assertThat(witness.getWitnesses()).isEmpty();
        assertThat(witness.getThreshold()).isZero();
    }

    private static String readVector(String resourcePath) throws IOException {
        try (InputStream in = InteropEmptyWitnessObjectTest.class.getResourceAsStream(resourcePath)) {
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
