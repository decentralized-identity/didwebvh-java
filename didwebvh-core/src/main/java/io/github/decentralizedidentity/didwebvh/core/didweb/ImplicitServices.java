package io.github.decentralizedidentity.didwebvh.core.didweb;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.url.DidToHttpsTransformer;

/**
 * Adds the implicit {@code #files} and {@code #whois} services defined for
 * {@code did:webvh} by spec §3.8 and §3.9 to a DID Document.
 *
 * <p>These services are defined by the method itself and MUST be present in
 * the resolved DID Document unless the controller has overridden them by
 * declaring services with the same id (or fragment) explicitly.
 */
public final class ImplicitServices {

    private static final String FILES_ID = "#files";
    private static final String WHOIS_ID = "#whois";
    private static final String WELL_KNOWN_SEGMENT = "/.well-known/";
    private static final String DID_LOG_FILENAME = "did.jsonl";
    private static final String WHOIS_FILENAME = "whois.vp";

    private ImplicitServices() {
    }

    /**
     * Mutate {@code doc} in place, appending the implicit {@code #files} and
     * {@code #whois} services if not already present (compared by service
     * fragment, either as bare fragment or fully qualified id).
     *
     * @param doc the DID Document JSON object (mutated in place)
     * @param did the DID string used to construct fully-qualified service ids
     */
    public static void addTo(JsonObject doc, String did) {
        if (doc == null || did == null || did.isEmpty()) {
            return;
        }
        String httpsBase = httpsBase(did);

        JsonArray services;
        JsonElement existing = doc.get("service");
        if (existing != null && existing.isJsonArray()) {
            services = existing.getAsJsonArray();
        } else {
            services = new JsonArray();
            doc.add("service", services);
        }

        if (!hasServiceWithId(services, did, FILES_ID)) {
            JsonObject files = new JsonObject();
            files.addProperty("id", did + FILES_ID);
            files.addProperty("type", "relativeRef");
            files.addProperty("serviceEndpoint", httpsBase);
            services.add(files);
        }

        if (!hasServiceWithId(services, did, WHOIS_ID)) {
            JsonObject whois = new JsonObject();
            whois.addProperty("@context",
                    "https://identity.foundation/linked-vp/contexts/v1");
            whois.addProperty("id", did + WHOIS_ID);
            whois.addProperty("type", "LinkedVerifiablePresentation");
            whois.addProperty("serviceEndpoint", httpsBase + WHOIS_FILENAME);
            services.add(whois);
        }
    }

    /**
     * Compute the HTTPS base URL for implicit service endpoints: the
     * DID-to-HTTPS URL with the trailing {@code did.jsonl} filename removed
     * and any {@code .well-known/} segment stripped.
     */
    static String httpsBase(String did) {
        String didLogUrl = DidToHttpsTransformer.toHttpsUrl(did);
        String base = didLogUrl.substring(0,
                didLogUrl.length() - DID_LOG_FILENAME.length());
        int idx = base.indexOf(WELL_KNOWN_SEGMENT);
        if (idx >= 0) {
            base = base.substring(0, idx + 1) + base.substring(idx + WELL_KNOWN_SEGMENT.length());
        }
        return base;
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
}
