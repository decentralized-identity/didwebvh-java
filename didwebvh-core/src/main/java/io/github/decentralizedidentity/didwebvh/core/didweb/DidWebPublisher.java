package io.github.decentralizedidentity.didwebvh.core.didweb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.model.DidDocument;
import io.github.decentralizedidentity.didwebvh.core.url.DidToHttpsTransformer;
import io.github.decentralizedidentity.didwebvh.core.url.DidWebVhUrl;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Generates a parallel {@code did:web} DID Document from a resolved
 * {@code did:webvh} DID Document, as specified in spec section 3.7.10.
 */
public final class DidWebPublisher {

    private static final String FILES_ID = "#files";
    private static final String WHOIS_ID = "#whois";
    private static final String WELL_KNOWN_SEGMENT = "/.well-known/";
    private static final String DID_LOG_FILENAME = "did.jsonl";
    private static final String WHOIS_FILENAME = "whois.vp";

    private DidWebPublisher() {
    }

    /**
     * Convert a resolved {@code did:webvh} DID Document into the parallel
     * {@code did:web} DID Document per spec section 3.7.10.
     *
     * @param resolvedWebVh the resolved did:webvh DID Document
     * @return the parallel did:web DID Document
     */
    public static DidDocument toDidWeb(DidDocument resolvedWebVh) {
        if (resolvedWebVh == null) {
            throw new ValidationException(
                    "resolved did:webvh document is required");
        }
        String didWebVh = resolvedWebVh.getId();
        if (didWebVh == null || didWebVh.isEmpty()) {
            throw new ValidationException("did:webvh document missing id");
        }

        DidWebVhUrl parsed = DidWebVhUrl.parse(didWebVh);
        String scidPrefix = "did:webvh:" + parsed.getScid() + ":";
        String httpsBase = httpsBase(didWebVh);

        // Step 1: start from the resolved did:webvh DIDDoc (deep copy to avoid mutation).
        JsonObject doc = resolvedWebVh.asJsonObject().deepCopy();

        // Step 2: add implicit #files and #whois services if not already present.
        addImplicitServices(doc, didWebVh, httpsBase);

        // Step 3: text-replace did:webvh:<scid>: with did:web: across the whole document.
        String replaced = doc.toString().replace(scidPrefix, "did:web:");
        JsonObject webDoc = JsonParser.parseString(replaced).getAsJsonObject();

        // Steps 4 & 5: add the original did:webvh DID to alsoKnownAs and dedupe
        // (removing the did:web DID itself if it landed there from the replacement).
        String didWeb = toDidWebUrl(didWebVh);
        addAlsoKnownAs(webDoc, didWebVh, didWeb);

        return new DidDocument(webDoc);
    }

    /**
     * Convert a {@code did:webvh} DID string to the equivalent
     * {@code did:web} DID string.
     */
    public static String toDidWebUrl(String didWebVhUrl) {
        return DidToHttpsTransformer.toDidWebUrl(didWebVhUrl);
    }

    /**
     * Compute the HTTPS base URL for implicit service endpoints: the DID-to-HTTPS
     * URL with the trailing {@code did.jsonl} filename removed and any
     * {@code .well-known/} segment stripped.
     */
    private static String httpsBase(String didWebVh) {
        String didLogUrl = DidToHttpsTransformer.toHttpsUrl(didWebVh);
        // Strip the trailing did.jsonl filename.
        String base = didLogUrl.substring(0,
                didLogUrl.length() - DID_LOG_FILENAME.length());
        // Omit the ".well-known/" segment if present.
        int idx = base.indexOf(WELL_KNOWN_SEGMENT);
        if (idx >= 0) {
            base = base.substring(0, idx + 1) + base.substring(idx + WELL_KNOWN_SEGMENT.length());
        }
        return base;
    }

    private static void addImplicitServices(JsonObject doc, String didWebVh,
                                            String httpsBase) {
        JsonArray services;
        JsonElement existing = doc.get("service");
        if (existing != null && existing.isJsonArray()) {
            services = existing.getAsJsonArray();
        } else {
            services = new JsonArray();
            doc.add("service", services);
        }

        if (!hasServiceWithId(services, didWebVh, FILES_ID)) {
            JsonObject files = new JsonObject();
            files.addProperty("id", didWebVh + FILES_ID);
            files.addProperty("type", "relativeRef");
            files.addProperty("serviceEndpoint", httpsBase);
            services.add(files);
        }

        if (!hasServiceWithId(services, didWebVh, WHOIS_ID)) {
            JsonObject whois = new JsonObject();
            whois.addProperty("@context",
                    "https://identity.foundation/linked-vp/contexts/v1");
            whois.addProperty("id", didWebVh + WHOIS_ID);
            whois.addProperty("type", "LinkedVerifiablePresentation");
            whois.addProperty("serviceEndpoint", httpsBase + WHOIS_FILENAME);
            services.add(whois);
        }
    }

    private static boolean hasServiceWithId(JsonArray services, String did,
                                            String fragment) {
        String absolute = did + fragment;
        for (JsonElement el : services) {
            if (!el.isJsonObject()) {
                continue;
            }
            JsonElement idEl = el.getAsJsonObject().get("id");
            if (idEl == null || idEl.isJsonNull()) {
                continue;
            }
            String id = idEl.getAsString();
            if (fragment.equals(id) || absolute.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static void addAlsoKnownAs(JsonObject doc, String didWebVh,
                                       String didWeb) {
        Set<String> seen = new LinkedHashSet<>();
        JsonElement existing = doc.get("alsoKnownAs");
        if (existing != null && existing.isJsonArray()) {
            for (JsonElement el : existing.getAsJsonArray()) {
                if (el.isJsonPrimitive()) {
                    seen.add(el.getAsString());
                }
            }
        }
        seen.add(didWebVh);
        // Step 5: remove the did:web DID itself if it was duplicated in earlier steps.
        seen.remove(didWeb);

        JsonArray aka = new JsonArray();
        for (String entry : seen) {
            aka.add(entry);
        }
        doc.add("alsoKnownAs", aka);
    }
}
