package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ResolveResultTest {

    @Test
    void metadataRoundTrip() {
        ResolutionMetadata meta = new ResolutionMetadata()
                .setVersionId("1-QmHash")
                .setVersionTime("2026-04-15T00:00:00Z")
                .setCreated("2026-04-15T00:00:00Z")
                .setUpdated("2026-04-15T00:00:00Z")
                .setScid("QmSCID")
                .setPortable(Boolean.TRUE)
                .setDeactivated(Boolean.FALSE)
                .setTtl("3600")
                .setWitness(new WitnessConfig(1, Collections.singletonList(new WitnessEntry("did:key:z6Mk1"))))
                .setWatchers(Collections.singletonList("https://watcher.example/"));

        Gson gson = JsonSupport.compact();
        ResolutionMetadata decoded = gson.fromJson(gson.toJson(meta), ResolutionMetadata.class);
        assertThat(decoded).isEqualTo(meta);
    }

    @Test
    void resolveResultWrapsDocumentAndMetadata() {
        JsonObject j = new JsonObject();
        j.addProperty("id", "did:webvh:QmSCID:example.com");
        ResolveResult r = new ResolveResult()
                .setDidDocument(new DidDocument(j))
                .setMetadata(new ResolutionMetadata().setVersionId("1-QmHash"));

        assertThat(r.getDidDocument().getId()).isEqualTo("did:webvh:QmSCID:example.com");
        assertThat(r.getMetadata().getVersionId()).isEqualTo("1-QmHash");
        assertThat(r.getError()).isNull();
    }
}
