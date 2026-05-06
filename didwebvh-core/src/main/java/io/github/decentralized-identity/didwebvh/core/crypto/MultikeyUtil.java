package io.github.decentralizedidentity.didwebvh.core.crypto;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;

/**
 * Encodes and decodes W3C Multikey public keys
 * ({@code z<base58btc(<varint-codec> || <raw-key>)>}). Only Ed25519
 * (codec {@code 0xed01}) is supported today — matching did:webvh v1.0.
 */
public final class MultikeyUtil {

    private static final byte[] ED25519_PREFIX = new byte[]{(byte) 0xed, 0x01};
    public static final String ED25519_KEY_TYPE = "Ed25519";

    private MultikeyUtil() {
    }

    public static String encode(String keyType, byte[] publicKeyBytes) {
        if (!ED25519_KEY_TYPE.equals(keyType)) {
            throw new ValidationException("Unsupported key type: " + keyType);
        }
        byte[] prefixed = new byte[ED25519_PREFIX.length + publicKeyBytes.length];
        System.arraycopy(ED25519_PREFIX, 0, prefixed, 0, ED25519_PREFIX.length);
        System.arraycopy(publicKeyBytes, 0, prefixed, ED25519_PREFIX.length, publicKeyBytes.length);
        return Base58Btc.encodeMultibase(prefixed);
    }

    public static byte[] decode(String multikey) {
        byte[] decoded = Base58Btc.decodeMultibase(multikey);
        if (decoded.length < 2) {
            throw new ValidationException("Multikey too short");
        }
        if (decoded[0] != ED25519_PREFIX[0] || decoded[1] != ED25519_PREFIX[1]) {
            throw new ValidationException(
                    "Unknown multicodec prefix: 0x"
                            + String.format("%02x%02x", decoded[0] & 0xFF, decoded[1] & 0xFF));
        }
        byte[] raw = new byte[decoded.length - 2];
        System.arraycopy(decoded, 2, raw, 0, raw.length);
        return raw;
    }

    public static String keyTypeFromMultikey(String multikey) {
        byte[] decoded = Base58Btc.decodeMultibase(multikey);
        if (decoded.length >= 2
                && decoded[0] == ED25519_PREFIX[0]
                && decoded[1] == ED25519_PREFIX[1]) {
            return ED25519_KEY_TYPE;
        }
        throw new ValidationException(
                "Unknown multicodec prefix: 0x"
                        + String.format("%02x%02x", decoded[0] & 0xFF, decoded[1] & 0xFF));
    }
}
