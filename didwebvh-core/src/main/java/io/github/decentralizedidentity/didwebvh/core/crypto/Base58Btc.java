package io.github.decentralizedidentity.didwebvh.core.crypto;

import io.github.novacrypto.base58.Base58;

/**
 * Base58 (Bitcoin alphabet) encoding with optional Multibase {@code z}-prefix
 * framing, as used throughout the did:webvh spec for SCIDs, entry hashes,
 * and {@code publicKeyMultibase} values.
 */
public final class Base58Btc {

    private static final char MULTIBASE_PREFIX = 'z';

    private Base58Btc() {
    }

    public static String encode(byte[] data) {
        return Base58.base58Encode(data);
    }

    public static byte[] decode(String encoded) {
        return Base58.base58Decode(encoded);
    }

    public static String encodeMultibase(byte[] data) {
        return MULTIBASE_PREFIX + encode(data);
    }

    public static byte[] decodeMultibase(String multibase) {
        if (multibase.isEmpty() || multibase.charAt(0) != MULTIBASE_PREFIX) {
            throw new IllegalArgumentException(
                    "Expected multibase base58btc prefix 'z', got: "
                            + (multibase.isEmpty() ? "<empty>" : multibase.charAt(0)));
        }
        return decode(multibase.substring(1));
    }
}
