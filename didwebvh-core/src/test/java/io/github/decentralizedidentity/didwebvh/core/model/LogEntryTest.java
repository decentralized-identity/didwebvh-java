package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class LogEntryTest {

    @Test
    void toJsonLineIsSingleLineAndRoundTrips() {
        JsonObject state = new JsonObject();
        state.addProperty("id", "did:webvh:QmSCID:example.com");

        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod("did:key:z6MkA#z6MkA")
                .setCreated("2026-04-15T00:00:00Z")
                .setProofValue("zSig");

        LogEntry entry = new LogEntry()
                .setVersionId("1-QmEntry")
                .setVersionTime("2026-04-15T00:00:00Z")
                .setParameters(new Parameters().setMethod("did:webvh:1.0"))
                .setState(state)
                .setProof(Collections.singletonList(proof));

        String line = entry.toJsonLine();
        assertThat(line).doesNotContain("\n");
        LogEntry decoded = LogEntry.fromJsonLine(line);
        assertThat(decoded).isEqualTo(entry);
        assertThat(decoded.getVersionNumber()).isEqualTo(1);
        assertThat(decoded.getEntryHash()).isEqualTo("QmEntry");
    }

    @Test
    void handlesEmptyParameters() {
        LogEntry entry = new LogEntry()
                .setVersionId("2-QmNext")
                .setParameters(new Parameters())
                .setState(new JsonObject());
        String line = entry.toJsonLine();
        assertThat(line).contains("\"parameters\":{}");
        assertThat(LogEntry.fromJsonLine(line)).isEqualTo(entry);
    }
}
