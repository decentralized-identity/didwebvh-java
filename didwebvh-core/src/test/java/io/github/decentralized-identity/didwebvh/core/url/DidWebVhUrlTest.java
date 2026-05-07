package io.github.decentralizedidentity.didwebvh.core.url;

import io.github.decentralizedidentity.didwebvh.core.UrlParseException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DidWebVhUrlTest {

    // A valid 46-character base58btc SCID for testing
    private static final String SCID =
            "QmfGEUAcMpzo25kF2Rhn8L2qMSAzMGeyq8kJEMabAxQzK3";

    @Test
    void parseSimpleDomain() {
        DidWebVhUrl url = DidWebVhUrl.parse("did:webvh:" + SCID + ":example.com");

        assertThat(url.getScid()).isEqualTo(SCID);
        assertThat(url.getDomain()).isEqualTo("example.com");
        assertThat(url.getHost()).isEqualTo("example.com");
        assertThat(url.getPort()).isEqualTo(-1);
        assertThat(url.getPathSegments()).isEmpty();
        assertThat(url.getFragment()).isNull();
        assertThat(url.getQueryParams()).isEmpty();
    }

    @Test
    void parseSubdomain() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":issuer.example.com");

        assertThat(url.getDomain()).isEqualTo("issuer.example.com");
        assertThat(url.getHost()).isEqualTo("issuer.example.com");
        assertThat(url.getPathSegments()).isEmpty();
    }

    @Test
    void parseWithPath() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com:dids:issuer");

        assertThat(url.getDomain()).isEqualTo("example.com");
        assertThat(url.getPathSegments()).containsExactly("dids", "issuer");
    }

    @Test
    void parseWithPercentEncodedPort() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com%3A3000:dids:issuer");

        assertThat(url.getDomain()).isEqualTo("example.com%3A3000");
        assertThat(url.getDecodedDomain()).isEqualTo("example.com:3000");
        assertThat(url.getHost()).isEqualTo("example.com");
        assertThat(url.getPort()).isEqualTo(3000);
        assertThat(url.getPathSegments()).containsExactly("dids", "issuer");
    }

    @Test
    void parseWithPortNoPath() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com%3A8080");

        assertThat(url.getHost()).isEqualTo("example.com");
        assertThat(url.getPort()).isEqualTo(8080);
        assertThat(url.getPathSegments()).isEmpty();
    }

    @Test
    void parseWithFragment() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com#key-1");

        assertThat(url.getDomain()).isEqualTo("example.com");
        assertThat(url.getFragment()).isEqualTo("key-1");
    }

    @Test
    void parseWithQueryParams() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com?versionId=1-abc&versionTime=2024-01-01");

        assertThat(url.getQueryParams())
                .containsEntry("versionId", "1-abc")
                .containsEntry("versionTime", "2024-01-01");
    }

    @Test
    void parseWithQueryAndFragment() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com:path?versionNumber=2#key-1");

        assertThat(url.getPathSegments()).containsExactly("path");
        assertThat(url.getQueryParams()).containsEntry("versionNumber", "2");
        assertThat(url.getFragment()).isEqualTo("key-1");
    }

    @Test
    void toStringRoundTrip() {
        String did = "did:webvh:" + SCID + ":example.com%3A3000:dids:issuer";
        DidWebVhUrl url = DidWebVhUrl.parse(did);
        assertThat(url.toString()).isEqualTo(did);
    }

    @Test
    void toStringRoundTripWithQueryAndFragment() {
        String did = "did:webvh:" + SCID + ":example.com:path?versionId=1-abc#key-1";
        DidWebVhUrl url = DidWebVhUrl.parse(did);
        assertThat(url.toString()).isEqualTo(did);
    }

    @Test
    void toBaseDidStripsQueryAndFragment() {
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com:path?versionId=1#key-1");
        assertThat(url.toBaseDid()).isEqualTo(
                "did:webvh:" + SCID + ":example.com:path");
    }

    // --- Invalid DID URLs ---

    @Test
    void rejectNull() {
        assertThatThrownBy(() -> DidWebVhUrl.parse(null))
                .isInstanceOf(UrlParseException.class);
    }

    @Test
    void rejectEmpty() {
        assertThatThrownBy(() -> DidWebVhUrl.parse(""))
                .isInstanceOf(UrlParseException.class);
    }

    @Test
    void rejectWrongPrefix() {
        assertThatThrownBy(() -> DidWebVhUrl.parse("did:web:" + SCID + ":example.com"))
                .isInstanceOf(UrlParseException.class)
                .hasMessageContaining("did:webvh:");
    }

    @Test
    void rejectShortScid() {
        assertThatThrownBy(() -> DidWebVhUrl.parse("did:webvh:abc:example.com"))
                .isInstanceOf(UrlParseException.class)
                .hasMessageContaining("46");
    }

    @Test
    void rejectScidWithInvalidChars() {
        // 'l' (lowercase L) is not in the base58btc alphabet
        String badScid = "Al1AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA1";
        assertThatThrownBy(() -> DidWebVhUrl.parse("did:webvh:" + badScid + ":example.com"))
                .isInstanceOf(UrlParseException.class)
                .hasMessageContaining("invalid base58btc character");
    }

    @Test
    void rejectIpAddress() {
        assertThatThrownBy(() -> DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":192.168.1.1"))
                .isInstanceOf(UrlParseException.class)
                .hasMessageContaining("IP address");
    }

    @Test
    void rawColonParsesAsPath() {
        // Raw colon is the path separator — "3000" becomes a path segment, not a port
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com:3000");
        assertThat(url.getDomain()).isEqualTo("example.com");
        assertThat(url.getPort()).isEqualTo(-1);
        assertThat(url.getPathSegments()).containsExactly("3000");
    }

    @Test
    void rejectEmptyPathSegment() {
        assertThatThrownBy(() -> DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com::issuer"))
                .isInstanceOf(UrlParseException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectMissingDomain() {
        assertThatThrownBy(() -> DidWebVhUrl.parse("did:webvh:" + SCID))
                .isInstanceOf(UrlParseException.class)
                .hasMessageContaining("at least SCID and domain");
    }

    @Test
    void casInsensitivePercentEncoding() {
        // %3a (lowercase) should also work
        DidWebVhUrl url = DidWebVhUrl.parse(
                "did:webvh:" + SCID + ":example.com%3a443");
        assertThat(url.getPort()).isEqualTo(443);
        assertThat(url.getHost()).isEqualTo("example.com");
    }
}
