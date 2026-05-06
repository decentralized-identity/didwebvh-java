package io.github.decentralizedidentity.didwebvh.core.resolve;

import io.github.decentralizedidentity.didwebvh.core.ResolutionException;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.url.DidToHttpsTransformer;
import io.github.decentralizedidentity.didwebvh.core.url.DidWebVhUrl;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/** High-level did:webvh resolver for HTTP, file, and in-memory logs. */
public class DidResolver {

    private final RemoteDidFetcher httpFetcher;
    private final FileDidFetcher fileFetcher;
    private final LogProcessor logProcessor;

    /** Creates a resolver with default HTTP settings (10 s timeout, 200 KB response limit). */
    public DidResolver() {
        this(new HttpDidFetcher(), new FileDidFetcher(), new LogProcessor());
    }

    /**
     * Creates a resolver with a custom HTTP timeout and the default 200 KB response size limit.
     *
     * @param timeout read/connect/call timeout; must be positive
     */
    public DidResolver(Duration timeout) {
        this(new HttpDidFetcher(timeout, HttpDidFetcher.DEFAULT_MAX_RESPONSE_SIZE),
                new FileDidFetcher(), new LogProcessor());
    }

    /**
     * Creates a resolver with a custom HTTP timeout and maximum response size.
     *
     * @param timeout         read/connect/call timeout; must be positive
     * @param maxResponseSize upper bound on response body size in bytes; must be positive
     */
    public DidResolver(Duration timeout, int maxResponseSize) {
        this(new HttpDidFetcher(timeout, maxResponseSize),
                new FileDidFetcher(), new LogProcessor());
    }

    DidResolver(HttpDidFetcher httpFetcher, FileDidFetcher fileFetcher,
                LogProcessor logProcessor) {
        this((RemoteDidFetcher) httpFetcher, fileFetcher, logProcessor);
    }

    DidResolver(RemoteDidFetcher httpFetcher, FileDidFetcher fileFetcher,
                LogProcessor logProcessor) {
        this.httpFetcher = httpFetcher;
        this.fileFetcher = fileFetcher;
        this.logProcessor = logProcessor;
    }

    /**
     * Resolves a did:webvh DID over HTTPS using default options.
     *
     * @param did the DID to resolve
     * @return the resolved document and metadata
     */
    public ResolveResult resolve(String did) {
        return resolve(did, ResolveOptions.defaults());
    }

    /**
     * Resolves a did:webvh DID over HTTPS with the given options.
     *
     * @param did     the DID to resolve; may include query parameters ({@code ?versionId=...})
     * @param options version selection and witness fetch configuration
     * @return the resolved document and metadata
     */
    public ResolveResult resolve(String did, ResolveOptions options) {
        DidWebVhUrl parsed = DidWebVhUrl.parse(did);
        String baseDid = parsed.toBaseDid();
        ResolveOptions effectiveOptions = (options == null ? ResolveOptions.defaults() : options)
                .withFallbacks(optionsFromQuery(parsed.getQueryParams()));
        if (effectiveOptions.hasMultipleVersionSelectors()) {
            throw new ResolutionException(
                    "Only one of versionId, versionTime, and versionNumber may be used",
                    "invalidDid");
        }

        String didLog = httpFetcher.fetchDidLog(DidToHttpsTransformer.toHttpsUrl(baseDid));
        String witness = null;
        if (effectiveOptions.getWitnessFetchMode()
                == ResolveOptions.WitnessFetchMode.PROACTIVE) {
            witness = fetchWitnessIfPresent(baseDid);
        }
        return logProcessor.process(didLog, witness, baseDid, effectiveOptions,
                () -> fetchRequiredWitness(baseDid));
    }

    /**
     * Resolves a DID log from a local file without any network access.
     *
     * @param didLogPath path to the {@code did.jsonl} file
     * @return the resolved document and metadata
     */
    public ResolveResult resolveFromFile(Path didLogPath) {
        return logProcessor.process(fileFetcher.fetchDidLog(didLogPath), null, null,
                ResolveOptions.defaults());
    }

    /**
     * Resolves a DID from an in-memory JSONL log string using default options.
     *
     * @param rawJsonl newline-delimited log entries ({@code did.jsonl} content)
     * @param did      the DID string to validate against the log, or {@code null} to skip DID check
     * @return the resolved document and metadata
     */
    public ResolveResult resolveFromLog(String rawJsonl, String did) {
        return resolveFromLog(rawJsonl, did, ResolveOptions.defaults());
    }

    /**
     * Resolves a DID from an in-memory JSONL log string with the given options.
     *
     * @param rawJsonl newline-delimited log entries ({@code did.jsonl} content)
     * @param did      the DID string to validate against the log, or {@code null} to skip DID check
     * @param options  version selection and witness fetch configuration
     * @return the resolved document and metadata
     */
    public ResolveResult resolveFromLog(String rawJsonl, String did, ResolveOptions options) {
        return logProcessor.process(rawJsonl, null, did, options);
    }

    private String fetchWitnessIfPresent(String did) {
        try {
            return httpFetcher.fetchWitnessProofs(DidToHttpsTransformer.toWitnessUrl(did));
        } catch (ResolutionException e) {
            if ("notFound".equals(e.getError())) {
                return null;
            }
            throw e;
        }
    }

    private String fetchRequiredWitness(String did) {
        try {
            return httpFetcher.fetchWitnessProofs(DidToHttpsTransformer.toWitnessUrl(did));
        } catch (ResolutionException e) {
            if ("notFound".equals(e.getError())) {
                throw new ResolutionException("Witness proofs are required but were not found",
                        "invalidDid", e);
            }
            throw e;
        }
    }

    private ResolveOptions optionsFromQuery(Map<String, String> queryParams) {
        ResolveOptions.Builder builder = ResolveOptions.builder();
        if (queryParams.containsKey("versionId")) {
            builder.versionId(queryParams.get("versionId"));
        }
        if (queryParams.containsKey("versionTime")) {
            builder.versionTime(queryParams.get("versionTime"));
        }
        if (queryParams.containsKey("versionNumber")) {
            try {
                builder.versionNumber(Integer.valueOf(queryParams.get("versionNumber")));
            } catch (NumberFormatException e) {
                throw new ResolutionException("Invalid versionNumber: "
                        + queryParams.get("versionNumber"), "invalidDid", e);
            }
        }
        return builder.build();
    }
}
