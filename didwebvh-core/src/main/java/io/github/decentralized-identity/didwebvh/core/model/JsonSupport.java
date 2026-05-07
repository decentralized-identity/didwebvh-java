package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/** Shared Gson instance configured for did:webvh model serialization. */
public final class JsonSupport {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private static final Gson COMPACT = new GsonBuilder()
            .disableHtmlEscaping()
            .create();

    private JsonSupport() {
        // no instances
    }

    /** Lenient Gson that preserves explicit nulls (used for round-trip tests). */
    public static Gson gson() {
        return GSON;
    }

    /** Gson that omits null fields – suitable for producing canonical log lines. */
    public static Gson compact() {
        return COMPACT;
    }
}
