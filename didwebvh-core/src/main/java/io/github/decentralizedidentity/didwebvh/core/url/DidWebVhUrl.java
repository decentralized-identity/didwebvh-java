package io.github.decentralizedidentity.didwebvh.core.url;

import io.github.decentralizedidentity.didwebvh.core.UrlParseException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parsed representation of a {@code did:webvh} DID URL.
 *
 * <p>Format: {@code did:webvh:<SCID>:<domain>[:<path>...][?query][#fragment]}
 */
public final class DidWebVhUrl {

    private static final String DID_PREFIX = "did:webvh:";
    private static final int SCID_LENGTH = 46;
    private static final String BASE58_ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";

    private final String scid;
    private final String domain;
    private final List<String> pathSegments;
    private final String fragment;
    private final Map<String, String> queryParams;

    private DidWebVhUrl(String scid, String domain, List<String> pathSegments,
                        String fragment, Map<String, String> queryParams) {
        this.scid = scid;
        this.domain = domain;
        this.pathSegments = Collections.unmodifiableList(new ArrayList<>(pathSegments));
        this.fragment = fragment;
        this.queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
    }

    /**
     * Parse a {@code did:webvh} DID URL string into its components.
     *
     * @param didUrl the full DID URL string
     * @return the parsed URL
     * @throws UrlParseException if the URL is invalid
     */
    public static DidWebVhUrl parse(String didUrl) {
        if (didUrl == null || didUrl.isEmpty()) {
            throw new UrlParseException("DID URL must not be null or empty");
        }
        if (!didUrl.startsWith(DID_PREFIX)) {
            throw new UrlParseException(
                    "DID URL must start with '" + DID_PREFIX + "', got: " + didUrl);
        }

        String remainder = didUrl.substring(DID_PREFIX.length());

        // Extract fragment
        String fragment = null;
        int hashIdx = remainder.indexOf('#');
        if (hashIdx >= 0) {
            fragment = remainder.substring(hashIdx + 1);
            remainder = remainder.substring(0, hashIdx);
        }

        // Extract query parameters
        Map<String, String> queryParams = new LinkedHashMap<>();
        int questionIdx = remainder.indexOf('?');
        if (questionIdx >= 0) {
            String queryString = remainder.substring(questionIdx + 1);
            remainder = remainder.substring(0, questionIdx);
            parseQueryParams(queryString, queryParams);
        }

        // Split remaining into colon-separated segments
        String[] segments = remainder.split(":", -1);
        if (segments.length < 2) {
            throw new UrlParseException(
                    "DID URL must contain at least SCID and domain segments, got: " + didUrl);
        }

        // First segment is the SCID
        String scid = segments[0];
        validateScid(scid);

        // Second segment is the domain (may contain %3A for port)
        String domain = segments[1];
        validateDomain(domain);

        // Remaining segments are path
        List<String> pathSegments = new ArrayList<>();
        for (int i = 2; i < segments.length; i++) {
            if (segments[i].isEmpty()) {
                throw new UrlParseException(
                        "Path segments must not be empty (found '::' in DID URL): " + didUrl);
            }
            pathSegments.add(segments[i]);
        }

        return new DidWebVhUrl(scid, domain, pathSegments, fragment, queryParams);
    }

    private static void validateScid(String scid) {
        if (scid.length() != SCID_LENGTH) {
            throw new UrlParseException(
                    "SCID must be " + SCID_LENGTH + " base58btc characters, got "
                            + scid.length() + " characters: " + scid);
        }
        for (int i = 0; i < scid.length(); i++) {
            if (BASE58_ALPHABET.indexOf(scid.charAt(i)) < 0) {
                throw new UrlParseException(
                        "SCID contains invalid base58btc character '"
                                + scid.charAt(i) + "' at position " + i);
            }
        }
    }

    private static void validateDomain(String domain) {
        if (domain.isEmpty()) {
            throw new UrlParseException("Domain must not be empty");
        }

        // Domain segment (after splitting by ':') cannot contain a raw colon.
        // Ports are encoded as %3A in the DID string; after splitting they
        // appear inside the domain segment as the literal "%3A" substring.

        // Decode percent-encoded port for validation
        String decoded = domain;
        boolean hasPercentPort = domain.toLowerCase().contains("%3a");
        if (hasPercentPort) {
            decoded = domain.replaceAll("(?i)%3A", ":");
        }

        // Extract host part (without port) for validation
        String host = decoded;
        int portIdx = decoded.lastIndexOf(':');
        if (portIdx >= 0 && hasPercentPort) {
            String portStr = decoded.substring(portIdx + 1);
            try {
                int port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    throw new UrlParseException(
                            "Port must be between 1 and 65535, got: " + port);
                }
            } catch (NumberFormatException e) {
                throw new UrlParseException(
                        "Invalid port number in domain: " + portStr);
            }
            host = decoded.substring(0, portIdx);
        }

        // Reject IP addresses (IPv4 and IPv6)
        if (isIpAddress(host)) {
            throw new UrlParseException(
                    "Domain must be a DNS name, not an IP address: " + host);
        }
    }

    private static boolean isIpAddress(String host) {
        // IPv6 in brackets
        if (host.startsWith("[") && host.endsWith("]")) {
            return true;
        }
        // IPv4: all digits and dots, at least one dot
        if (host.contains(".") && host.matches("[0-9.]+")) {
            return true;
        }
        return false;
    }

    private static void parseQueryParams(String query, Map<String, String> params) {
        if (query.isEmpty()) {
            return;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                params.put(pair.substring(0, eq), pair.substring(eq + 1));
            } else {
                params.put(pair, "");
            }
        }
    }

    public String getScid() {
        return scid;
    }

    public String getDomain() {
        return domain;
    }

    /**
     * Returns the decoded domain with port (if present).
     * Percent-encoded port ({@code %3A}) is decoded to a colon.
     */
    public String getDecodedDomain() {
        return domain.replaceAll("(?i)%3A", ":");
    }

    /**
     * Returns just the hostname, without port.
     */
    public String getHost() {
        String decoded = getDecodedDomain();
        int portIdx = decoded.lastIndexOf(':');
        if (portIdx >= 0 && domain.toLowerCase().contains("%3a")) {
            return decoded.substring(0, portIdx);
        }
        return decoded;
    }

    /**
     * Returns the port number, or -1 if no port is specified.
     */
    public int getPort() {
        String decoded = getDecodedDomain();
        int portIdx = decoded.lastIndexOf(':');
        if (portIdx >= 0 && domain.toLowerCase().contains("%3a")) {
            return Integer.parseInt(decoded.substring(portIdx + 1));
        }
        return -1;
    }

    public List<String> getPathSegments() {
        return pathSegments;
    }

    public String getFragment() {
        return fragment;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    /**
     * Reconstructs the DID string (without query and fragment).
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(DID_PREFIX)
                .append(scid)
                .append(':')
                .append(domain);
        for (String seg : pathSegments) {
            sb.append(':').append(seg);
        }
        if (!queryParams.isEmpty()) {
            sb.append('?');
            boolean first = true;
            for (Map.Entry<String, String> entry : queryParams.entrySet()) {
                if (!first) {
                    sb.append('&');
                }
                sb.append(entry.getKey());
                if (!entry.getValue().isEmpty()) {
                    sb.append('=').append(entry.getValue());
                }
                first = false;
            }
        }
        if (fragment != null) {
            sb.append('#').append(fragment);
        }
        return sb.toString();
    }

    /**
     * Returns the base DID string (without query parameters and fragment).
     */
    public String toBaseDid() {
        StringBuilder sb = new StringBuilder(DID_PREFIX)
                .append(scid)
                .append(':')
                .append(domain);
        for (String seg : pathSegments) {
            sb.append(':').append(seg);
        }
        return sb.toString();
    }
}
