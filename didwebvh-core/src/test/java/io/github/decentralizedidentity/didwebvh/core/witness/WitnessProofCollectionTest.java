package io.github.decentralizedidentity.didwebvh.core.witness;

import com.google.gson.Gson;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class WitnessProofCollectionTest {

    @Test
    void serializesWitnessConfigShape() {
        WitnessConfig wc = new WitnessConfig(2, Arrays.asList(
                new WitnessEntry("did:key:z6Mk1"),
                new WitnessEntry("did:key:z6Mk2")));
        Gson gson = JsonSupport.compact();
        String json = gson.toJson(wc);
        assertThat(json).isEqualTo(
                "{\"threshold\":2,\"witnesses\":[{\"id\":\"did:key:z6Mk1\"},{\"id\":\"did:key:z6Mk2\"}]}");
        assertThat(gson.fromJson(json, WitnessConfig.class)).isEqualTo(wc);
    }

    @Test
    void proofCollectionRoundTrip() {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod("did:key:z6Mk1#z6Mk1")
                .setProofValue("zSig");
        WitnessProofCollection coll = new WitnessProofCollection(
                Collections.singletonList(new WitnessProofEntry("1-QmHash", Collections.singletonList(proof))));
        Gson gson = JsonSupport.compact();
        String json = gson.toJson(coll);
        WitnessProofCollection decoded = gson.fromJson(json, WitnessProofCollection.class);
        assertThat(decoded).isEqualTo(coll);
        assertThat(decoded.getEntries()).hasSize(1);
        assertThat(decoded.getEntries().get(0).getVersionId()).isEqualTo("1-QmHash");
    }
}
