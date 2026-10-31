package io.github.decentralizedidentity.didwebvh.core.resolve;

import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Logs whose entries name the DID as {@code did:scid:vh:1:<SCID>}:
 * {@link ResolveOptions.Builder#didScidIds}.
 */
class DidScidIdsTest {

    private static final String DID = "did:scid:vh:1:QmeyXk5HskUDXoEpbDhTmKbE1vpABYpHobnB5fuGMRbYfZ";
    private static final ResolveOptions DID_SCID_IDS = ResolveOptions.builder().didScidIds(true).build();

    private static String vector(String name) throws IOException {
        String path = "/test-vectors/did-scid/" + name + ".jsonl";
        try (InputStream in = DidScidIdsTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void resolvesALogThatNamesTheDidScid() throws IOException {
        ResolveResult result = new DidResolver()
                .resolveFromLog(vector("scid-three-entries"), DID, DID_SCID_IDS);

        assertThat(result.getDidDocument().getId()).isEqualTo(DID);
        assertThat(result.getMetadata().getVersionId())
                .isEqualTo("3-QmQiYUxbbjPc6Cxrq5bX8p1ifTPJNMA2o7smeqDCjsgmHw");
        assertThat(result.getDidDocument().asJsonObject().get("alsoKnownAs").toString())
                .isEqualTo("[\"urn:example:entry-3\"]");
        // A did:scid names no host, so no implicit #files or #whois.
        assertThat(result.getDidDocument().asJsonObject().has("service")).isFalse();
    }

    @Test
    void acceptsTheDidScidWithItsSrc() throws IOException {
        ResolveResult result = new DidResolver().resolveFromLog(vector("scid-genesis"),
                DID + "?src=example.com/did.jsonl", DID_SCID_IDS);

        assertThat(result.getDidDocument().getId()).isEqualTo(DID);
        assertThat(result.getMetadata().getVersionId()).startsWith("1-");
    }

    @Test
    void withoutTheOptionItIsRefused() throws IOException {
        String log = vector("scid-three-entries");

        assertThatThrownBy(() -> new DidResolver().resolveFromLog(log, null))
                .isInstanceOf(ResolutionException.class)
                .extracting("error")
                .isEqualTo("invalidDid");
    }

    @Test
    void refusesAnotherDidScid() throws IOException {
        String log = vector("scid-three-entries");

        assertThatThrownBy(() -> new DidResolver().resolveFromLog(log,
                "did:scid:vh:1:QmZQmhLrqzEQHPA6ywz31BbSJQ4K8KjVf1G3iKhSHG1wEp", DID_SCID_IDS))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("no entry has DID Document id matching");
    }

    @Test
    void refusesADidThatIsNotADidScid() throws IOException {
        String log = vector("scid-three-entries");

        assertThatThrownBy(() -> new DidResolver().resolveFromLog(log,
                "did:webvh:QmeyXk5HskUDXoEpbDhTmKbE1vpABYpHobnB5fuGMRbYfZ:example.com", DID_SCID_IDS))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("Not a did:scid:vh:1 DID");
    }

    @ParameterizedTest
    @ValueSource(strings = {"scid-with-host", "scid-with-query", "scid-with-fragment", "scid-other-scid",
            "scid-moves-to-webvh", "webvh-moves-to-scid"})
    void refusesAnIdThatIsNotExactlyTheDidScid(String name) throws IOException {
        String log = vector(name);

        assertThatThrownBy(() -> new DidResolver().resolveFromLog(log, null, DID_SCID_IDS))
                .isInstanceOf(ResolutionException.class)
                .hasMessageContaining("state.id must be did:scid:vh:1:");
    }

    @Test
    void refusesATamperedLog() throws IOException {
        String log = vector("scid-three-entries").replace("urn:example:entry-3", "urn:example:forged");

        assertThatThrownBy(() -> new DidResolver().resolveFromLog(log, DID, DID_SCID_IDS))
                .isInstanceOf(ResolutionException.class)
                .extracting("error")
                .isEqualTo("invalidDid");
    }
}
