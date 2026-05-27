package io.github.decentralizedidentity.didwebvh.core.crypto;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;

/**
 * Computes the entry-hash portion of a did:webvh {@code versionId}
 * ({@code <versionNumber>-<entryHash>}) per spec §4.2: strip the proof,
 * substitute the predecessor {@code versionId}, JCS-canonicalize, then
 * apply {@code sha2-256} multihash with base58btc multibase encoding.
 */
public final class EntryHashGenerator {

    private EntryHashGenerator() {
    }

    public static String generate(String entryJson, String predecessorVersionId) {
        JsonObject entry = JsonSupport.compact().fromJson(entryJson, JsonObject.class);
        entry.remove("proof");
        entry.addProperty("versionId", predecessorVersionId);
        byte[] canonical = Jcs.canonicalize(entry);
        byte[] multihash = MultihashUtil.hashAndEncode(canonical);
        return Base58Btc.encode(multihash);
    }

    public static boolean verify(LogEntry entry, String predecessorVersionId) {
        String entryJson = entry.toJsonLine();
        String expectedHash = generate(entryJson, predecessorVersionId);
        return expectedHash.equals(entry.getEntryHash());
    }
}
