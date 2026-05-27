package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DidDocumentTest {

    @Test
    void readsSingleStringOrArrayController() {
        JsonObject j = new JsonObject();
        j.addProperty("id", "did:webvh:QmSCID:example.com");
        j.addProperty("controller", "did:key:z6MkA");
        DidDocument doc = new DidDocument(j);
        assertThat(doc.getId()).isEqualTo("did:webvh:QmSCID:example.com");
        assertThat(doc.getController()).containsExactly("did:key:z6MkA");

        JsonArray arr = new JsonArray();
        arr.add("did:key:z6MkA");
        arr.add("did:key:z6MkB");
        j.add("controller", arr);
        assertThat(new DidDocument(j).getController()).containsExactly("did:key:z6MkA", "did:key:z6MkB");
    }

    @Test
    void withIdReturnsNewInstanceAndDoesNotMutateOriginal() {
        JsonObject j = new JsonObject();
        j.addProperty("id", "did:webvh:a:example.com");
        DidDocument original = new DidDocument(j);
        DidDocument updated = original.withId("did:webvh:b:example.com");
        assertThat(updated.getId()).isEqualTo("did:webvh:b:example.com");
        assertThat(original.getId()).isEqualTo("did:webvh:a:example.com");
        assertThat(updated).isNotEqualTo(original);
    }
}
