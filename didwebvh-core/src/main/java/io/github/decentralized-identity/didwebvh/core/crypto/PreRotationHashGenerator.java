package io.github.decentralizedidentity.didwebvh.core.crypto;

import java.nio.charset.StandardCharsets;

/**
 * Computes the pre-rotation commitment hash for a future key: the
 * multihash+multibase of the UTF-8 bytes of the multikey string.
 * See spec §3.5 (pre-rotation).
 */
public final class PreRotationHashGenerator {

    private PreRotationHashGenerator() {
    }

    public static String generateHash(String multikeyPublicKey) {
        byte[] keyBytes = multikeyPublicKey.getBytes(StandardCharsets.UTF_8);
        byte[] multihash = MultihashUtil.hashAndEncode(keyBytes);
        return Base58Btc.encode(multihash);
    }
}
