package io.github.decentralizedidentity.didwebvh.core.resolve;

import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/** Fetches did:webvh resolution artifacts over HTTP(S). */
public final class HttpDidFetcher implements RemoteDidFetcher {

    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    // Implementation safety limit, not a did:webvh spec rule. Bodies are streamed and
    // rejected while reading so a malicious endpoint cannot force unbounded memory use.
    public static final int DEFAULT_MAX_RESPONSE_SIZE = 200 * 1024;

    private final OkHttpClient client;
    private final Duration timeout;
    private final int maxResponseSize;

    /** Creates a fetcher with the default 10 s timeout and 200 KB response size limit. */
    public HttpDidFetcher() {
        this(DEFAULT_TIMEOUT, DEFAULT_MAX_RESPONSE_SIZE);
    }

    /**
     * Creates a fetcher with the given timeout and maximum response size.
     *
     * @param timeout         read/connect/call timeout; must be positive
     * @param maxResponseSize upper bound on response body size in bytes; must be positive
     */
    public HttpDidFetcher(Duration timeout, int maxResponseSize) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        if (maxResponseSize < 1) {
            throw new IllegalArgumentException("maxResponseSize must be positive");
        }
        this.timeout = timeout;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .callTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .build();
        this.maxResponseSize = maxResponseSize;
    }

    /** Returns the configured read/connect/call timeout. */
    public Duration getTimeout() {
        return timeout;
    }

    /** Returns the maximum allowed response body size in bytes. */
    public int getMaxResponseSize() {
        return maxResponseSize;
    }

    /** Fetches the {@code did.jsonl} log from the given HTTPS URL. */
    @Override
    public String fetchDidLog(String httpsUrl) {
        return fetch(httpsUrl, "did log");
    }

    /** Fetches the {@code did-witness.json} proof file from the given HTTPS URL. */
    @Override
    public String fetchWitnessProofs(String witnessUrl) {
        return fetch(witnessUrl, "witness proofs");
    }

    private String fetch(String url, String label) {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String error = response.code() == 404 ? "notFound" : "httpError";
                throw new ResolutionException("Unable to fetch " + label
                        + ": HTTP " + response.code(), error);
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ResolutionException("Unable to fetch " + label
                        + ": empty response body", "httpError");
            }
            long contentLength = body.contentLength();
            if (contentLength > maxResponseSize) {
                throw new ResolutionException("Unable to fetch " + label
                        + ": response exceeds " + maxResponseSize + " bytes", "httpError");
            }
            return readBounded(body.byteStream(), label);
        } catch (IOException e) {
            throw new ResolutionException("Unable to fetch " + label + ": "
                    + e.getMessage(), "httpError", e);
        }
    }

    private String readBounded(InputStream input, String label) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxResponseSize) {
                throw new ResolutionException("Unable to fetch " + label
                        + ": response exceeds " + maxResponseSize + " bytes", "httpError");
            }
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
