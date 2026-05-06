package io.github.decentralizedidentity.didwebvh.core.crypto;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ScidGeneratorTest {

    private LogEntry buildPreliminaryEntry() {
        String placeholder = ScidGenerator.placeholder();
        JsonObject state = new JsonObject();
        state.addProperty("id", "did:webvh:" + placeholder + ":example.com");

        Parameters params = new Parameters()
                .setMethod("did:webvh:1.0")
                .setScid(placeholder)
                .setUpdateKeys(Collections.singletonList("z6MkTest"));

        return new LogEntry()
                .setVersionId(placeholder)
                .setVersionTime("2024-01-01T00:00:00Z")
                .setParameters(params)
                .setState(state);
    }

    @Test
    void generateProducesSpecScidLength() {
        String scid = ScidGenerator.generate(buildPreliminaryEntry());
        assertThat(scid).hasSize(46);
    }

    @Test
    void generateIsDeterministic() {
        LogEntry entry = buildPreliminaryEntry();
        String scid1 = ScidGenerator.generate(entry);
        String scid2 = ScidGenerator.generate(entry);
        assertThat(scid1).isEqualTo(scid2);
    }

    @Test
    void verifyRoundTrip() {
        LogEntry preliminary = buildPreliminaryEntry();
        String scid = ScidGenerator.generate(preliminary);

        String placeholder = ScidGenerator.placeholder();
        String json = preliminary.toJsonLine().replace(placeholder, scid);
        LogEntry entryWithScid = LogEntry.fromJsonLine(json);

        DataIntegrityProof fakeProof = DataIntegrityProof.defaults()
                .setProofValue("zFakeProof");
        entryWithScid.setProof(Collections.singletonList(fakeProof));

        assertThat(ScidGenerator.verify(scid, entryWithScid)).isTrue();
    }

    @Test
    void verifyRejectsTamperedEntry() {
        LogEntry preliminary = buildPreliminaryEntry();
        String scid = ScidGenerator.generate(preliminary);

        String placeholder = ScidGenerator.placeholder();
        String json = preliminary.toJsonLine().replace(placeholder, scid);
        String tampered = json.replace("example.com", "evil.com");
        LogEntry tamperedEntry = LogEntry.fromJsonLine(tampered);

        assertThat(ScidGenerator.verify(scid, tamperedEntry)).isFalse();
    }
}
