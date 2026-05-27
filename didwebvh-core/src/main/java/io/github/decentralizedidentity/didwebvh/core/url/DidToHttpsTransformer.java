package io.github.decentralizedidentity.didwebvh.core.url;

import java.net.IDN;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;

/**
 * Transforms {@code did:webvh} DIDs to HTTPS URLs per spec section 3.4.
 */
public final class DidToHttpsTransformer {

    private static final String DID_LOG_FILE = "did.jsonl";
    private static final String WITNESS_FILE = "did-witness.json";
    private static final String WELL_KNOWN = ".well-known";

    private DidToHttpsTransformer() {
    }

    /**
     * Transform a {@code did:webvh} DID to the HTTPS URL of its DID Log file.
     *
     * @param did the full did:webvh DID string
     * @return the HTTPS URL for {@code did.jsonl}
     */
    public static String toHttpsUrl(String did) {
        return buildUrl(did, DID_LOG_FILE);
    }

    /**
     * Transform a {@code did:webvh} DID to the HTTPS URL of its witness proof file.
     *
     * @param did the full did:webvh DID string
     * @return the HTTPS URL for {@code did-witness.json}
     */
    public static String toWitnessUrl(String did) {
        return buildUrl(did, WITNESS_FILE);
    }

    /**
     * Convert a {@code did:webvh} DID to the equivalent {@code did:web} DID.
     *
     * <p>The SCID segment is removed and the method name changes from
     * {@code webvh} to {@code web}. All other segments remain the same.
     *
     * @param didWebVh the did:webvh DID string
     * @return the equivalent did:web DID string
     */
    public static String toDidWebUrl(String didWebVh) {
        DidWebVhUrl parsed = DidWebVhUrl.parse(didWebVh);

        StringBuilder sb = new StringBuilder("did:web:");
        sb.append(parsed.getDomain());
        for (String seg : parsed.getPathSegments()) {
            sb.append(':').append(seg);
        }
        return sb.toString();
    }

    private static String buildUrl(String did, String filename) {
        DidWebVhUrl parsed = DidWebVhUrl.parse(did);

        // Step 3: Transform domain segment
        String host = parsed.getHost();
        int port = parsed.getPort();

        // Apply Unicode normalization (NFC) and IDNA/Punycode
        host = Normalizer.normalize(host, Normalizer.Form.NFC);
        host = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);

        // Step 4: Transform path segments
        List<String> pathSegments = parsed.getPathSegments();

        // Step 5: Reconstruct HTTPS URL
        StringBuilder url = new StringBuilder("https://");
        url.append(host);
        if (port > 0) {
            url.append(':').append(port);
        }
        url.append('/');

        if (pathSegments.isEmpty()) {
            url.append(WELL_KNOWN).append('/').append(filename);
        } else {
            for (int i = 0; i < pathSegments.size(); i++) {
                if (i > 0) {
                    url.append('/');
                }
                url.append(percentEncode(pathSegments.get(i)));
            }
            url.append('/').append(filename);
        }

        return url.toString();
    }

    /**
     * Percent-encode a path segment per RFC 3986.
     * Unreserved characters (ALPHA / DIGIT / "-" / "." / "_" / "~") are not encoded.
     */
    static String percentEncode(String segment) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = segment.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            int c = b & 0xFF;
            if (isUnreserved(c)) {
                sb.append((char) c);
            } else {
                sb.append('%');
                sb.append(Character.toUpperCase(Character.forDigit(c >> 4, 16)));
                sb.append(Character.toUpperCase(Character.forDigit(c & 0x0F, 16)));
            }
        }
        return sb.toString();
    }

    private static boolean isUnreserved(int c) {
        return (c >= 'A' && c <= 'Z')
                || (c >= 'a' && c <= 'z')
                || (c >= '0' && c <= '9')
                || c == '-' || c == '.' || c == '_' || c == '~';
    }
}
