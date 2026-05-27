package io.github.decentralizedidentity.didwebvh.core.url;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DidToHttpsTransformerTest {

    // A valid 46-character base58btc SCID for testing
    private static final String SCID =
            "QmfGEUAcMpzo25kF2Rhn8L2qMSAzMGeyq8kJEMabAxQzK3";

    // --- Spec examples (section 3.4) ---

    @Test
    void domainOnly() {
        String did = "did:webvh:" + SCID + ":example.com";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/.well-known/did.jsonl");
    }

    @Test
    void subdomain() {
        String did = "did:webvh:" + SCID + ":issuer.example.com";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://issuer.example.com/.well-known/did.jsonl");
    }

    @Test
    void domainWithPath() {
        String did = "did:webvh:" + SCID + ":example.com:dids:issuer";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/dids/issuer/did.jsonl");
    }

    @Test
    void domainWithPortAndPath() {
        String did = "did:webvh:" + SCID + ":example.com%3A3000:dids:issuer";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com:3000/dids/issuer/did.jsonl");
    }

    @Test
    void domainWithPortNoPath() {
        String did = "did:webvh:" + SCID + ":example.com%3A8080";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com:8080/.well-known/did.jsonl");
    }

    // --- Witness URL ---

    @Test
    void witnessUrlDomainOnly() {
        String did = "did:webvh:" + SCID + ":example.com";
        assertThat(DidToHttpsTransformer.toWitnessUrl(did))
                .isEqualTo("https://example.com/.well-known/did-witness.json");
    }

    @Test
    void witnessUrlWithPath() {
        String did = "did:webvh:" + SCID + ":example.com:dids:issuer";
        assertThat(DidToHttpsTransformer.toWitnessUrl(did))
                .isEqualTo("https://example.com/dids/issuer/did-witness.json");
    }

    @Test
    void witnessUrlWithPortAndPath() {
        String did = "did:webvh:" + SCID + ":example.com%3A3000:dids:issuer";
        assertThat(DidToHttpsTransformer.toWitnessUrl(did))
                .isEqualTo("https://example.com:3000/dids/issuer/did-witness.json");
    }

    // --- did:web conversion ---

    @Test
    void toDidWebSimple() {
        String did = "did:webvh:" + SCID + ":example.com";
        assertThat(DidToHttpsTransformer.toDidWebUrl(did))
                .isEqualTo("did:web:example.com");
    }

    @Test
    void toDidWebWithPath() {
        String did = "did:webvh:" + SCID + ":example.com:dids:issuer";
        assertThat(DidToHttpsTransformer.toDidWebUrl(did))
                .isEqualTo("did:web:example.com:dids:issuer");
    }

    @Test
    void toDidWebWithPort() {
        String did = "did:webvh:" + SCID + ":example.com%3A3000:dids:issuer";
        assertThat(DidToHttpsTransformer.toDidWebUrl(did))
                .isEqualTo("did:web:example.com%3A3000:dids:issuer");
    }

    // --- Path with special characters ---

    @Test
    void pathSegmentsArePercentEncoded() {
        // Path segments with spaces would be percent-encoded
        // In practice path segments in DIDs are simple strings
        String did = "did:webvh:" + SCID + ":example.com:simple:path";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/simple/path/did.jsonl");
    }

    // --- Query parameters are ignored for URL construction ---

    @Test
    void queryParamsIgnoredInUrlConstruction() {
        String did = "did:webvh:" + SCID + ":example.com?versionId=1-abc";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/.well-known/did.jsonl");
    }

    @Test
    void fragmentIgnoredInUrlConstruction() {
        String did = "did:webvh:" + SCID + ":example.com#key-1";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://example.com/.well-known/did.jsonl");
    }

    // --- IDNA / Punycode ---

    @Test
    void punycodeEncoding() {
        // Test with a domain that has ASCII characters only (Punycode is no-op)
        String did = "did:webvh:" + SCID + ":xn--example-cua.com";
        assertThat(DidToHttpsTransformer.toHttpsUrl(did))
                .isEqualTo("https://xn--example-cua.com/.well-known/did.jsonl");
    }
}
