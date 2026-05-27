package io.github.decentralizedidentity.didwebvh.core.crypto;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntryHashGeneratorTest {

    @Test
    void generateProducesBase58btcResult() {
        String json = "{\"versionId\":\"placeholder\",\"versionTime\":\"2024-01-01T00:00:00Z\","
                + "\"parameters\":{},\"state\":{\"id\":\"did:example:123\"}}";
        String hash = EntryHashGenerator.generate(json, "predecessor-id");
        // Spec 3.7.4: base58btc(multihash(JCS(entry))) — no multibase prefix.
        // A SHA-256 multihash encoded in base58btc starts with "Qm" and is 46 chars.
        assertThat(hash).hasSize(46).startsWith("Qm");
    }

    @Test
    void generateIsDeterministic() {
        String json = "{\"versionId\":\"v\",\"versionTime\":\"2024-01-01T00:00:00Z\","
                + "\"parameters\":{},\"state\":{}}";
        String hash1 = EntryHashGenerator.generate(json, "pred");
        String hash2 = EntryHashGenerator.generate(json, "pred");
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void differentPredecessorProducesDifferentHash() {
        String json = "{\"versionId\":\"v\",\"versionTime\":\"2024-01-01T00:00:00Z\","
                + "\"parameters\":{},\"state\":{}}";
        String hash1 = EntryHashGenerator.generate(json, "pred1");
        String hash2 = EntryHashGenerator.generate(json, "pred2");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void verifyMatchingEntry() {
        JsonObject state = new JsonObject();
        state.addProperty("id", "did:example:123");
        Parameters params = new Parameters().setMethod("did:webvh:1.0");

        LogEntry entry = new LogEntry()
                .setVersionId("placeholder")
                .setVersionTime("2024-01-01T00:00:00Z")
                .setParameters(params)
                .setState(state);

        String predecessorId = "some-scid";
        String entryJson = entry.toJsonLine();
        String hash = EntryHashGenerator.generate(entryJson, predecessorId);

        entry.setVersionId("1-" + hash);
        assertThat(EntryHashGenerator.verify(entry, predecessorId)).isTrue();
    }

    @Test
    void verifyRejectsTamperedState() {
        JsonObject state = new JsonObject();
        state.addProperty("id", "did:example:123");

        LogEntry entry = new LogEntry()
                .setVersionId("placeholder")
                .setVersionTime("2024-01-01T00:00:00Z")
                .setParameters(new Parameters())
                .setState(state);

        String predecessorId = "some-scid";
        String entryJson = entry.toJsonLine();
        String hash = EntryHashGenerator.generate(entryJson, predecessorId);

        state.addProperty("id", "did:example:TAMPERED");
        entry.setVersionId("1-" + hash).setState(state);

        assertThat(EntryHashGenerator.verify(entry, predecessorId)).isFalse();
    }
}
