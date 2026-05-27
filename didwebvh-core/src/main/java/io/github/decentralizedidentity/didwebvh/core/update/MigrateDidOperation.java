package io.github.decentralizedidentity.didwebvh.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.url.DidWebVhUrl;

import java.util.Collections;

/**
 * Implements DID migration from spec section 3.7.6.
 *
 * <p>The SCID is preserved; only the domain (and optional path) change.  All DID references
 * in the document are rewritten and the previous DID is added to {@code alsoKnownAs}.
 */
final class MigrateDidOperation {

    private MigrateDidOperation() {
    }

    static UpdateDidResult execute(MigrateDidConfig config) {
        validate(config);

        String oldDid = config.getExistingState().getDid();
        DidWebVhUrl parsed = DidWebVhUrl.parse(oldDid);

        // Reconstruct new DID preserving SCID
        String newDid = buildNewDid(parsed.getScid(), config.getNewDomain(),
                config.getNewPath());

        LogEntry previous = config.getExistingState().getLastEntry();
        JsonObject newDoc = rewriteDocument(previous.getState().deepCopy(), oldDid, newDid);

        // Carry forward only the document change; no parameter delta needed
        LogEntry entry = UpdateDidOperation.buildEntry(
                previous,
                config.getSigner(),
                newDoc,
                new Parameters());

        return new UpdateDidResult(Collections.singletonList(entry));
    }

    private static void validate(MigrateDidConfig config) {
        if (config.getExistingState() == null) {
            throw new ValidationException("existingState is required");
        }
        if (config.getSigner() == null) {
            throw new ValidationException("signer is required");
        }
        if (config.getNewDomain() == null || config.getNewDomain().isEmpty()) {
            throw new ValidationException("newDomain is required");
        }
        if (config.getExistingState().getLastEntry() == null) {
            throw new ValidationException(
                    "existingState must contain at least one log entry");
        }
        if (config.getExistingState().isDeactivated()) {
            throw new ValidationException("cannot migrate a deactivated DID");
        }
        Parameters active = config.getExistingState().accumulateParameters();
        if (!Boolean.TRUE.equals(active.getPortable())) {
            throw new ValidationException(
                    "DID is not portable; set portable=true at creation time to enable migration");
        }
    }

    /** Build {@code did:webvh:<scid>:<newDomain>[:<newPath>]}. */
    private static String buildNewDid(String scid, String newDomain, String newPath) {
        StringBuilder sb = new StringBuilder("did:webvh:")
                .append(scid)
                .append(':')
                .append(newDomain);
        if (newPath != null && !newPath.isEmpty()) {
            sb.append(':').append(newPath);
        }
        return sb.toString();
    }

    /**
     * Replace every occurrence of {@code oldDid} in the document JSON with {@code newDid},
     * and ensure the previous DID appears in {@code alsoKnownAs}.
     */
    private static JsonObject rewriteDocument(JsonObject doc,
                                              String oldDid, String newDid) {
        // String-replace all references (id, controller, key refs, etc.)
        String json = doc.toString().replace(oldDid, newDid);
        JsonObject rewritten = JsonSupport.compact().fromJson(json, JsonObject.class);

        // Add previous DID to alsoKnownAs (deduplicated)
        addAlsoKnownAs(rewritten, oldDid);

        return rewritten;
    }

    private static void addAlsoKnownAs(JsonObject doc, String previousDid) {
        JsonArray aka;
        JsonElement existing = doc.get("alsoKnownAs");
        if (existing != null && existing.isJsonArray()) {
            aka = existing.getAsJsonArray();
            // Check for duplicate
            for (JsonElement el : aka) {
                if (previousDid.equals(el.getAsString())) {
                    return;
                }
            }
        } else {
            aka = new JsonArray();
        }
        aka.add(previousDid);
        doc.add("alsoKnownAs", aka);
    }
}
