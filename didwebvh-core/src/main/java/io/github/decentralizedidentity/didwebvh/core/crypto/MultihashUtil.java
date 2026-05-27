package io.github.decentralizedidentity.didwebvh.core.crypto;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Multihash (varint prefix) + base58btc multibase helpers used for SCID and
 * entry-hash encoding in did:webvh. Currently hard-wired to {@code sha2-256}.
 */
public final class MultihashUtil {

    public static final int SHA2_256_CODE = 0x12;
    public static final int SHA2_256_LENGTH = 32;

    private MultihashUtil() {
    }

    public static byte[] hashAndEncode(byte[] data) {
        byte[] digest = sha256(data);
        return encode(SHA2_256_CODE, digest);
    }

    public static byte[] encode(int algorithmCode, byte[] digest) {
        byte[] multihash = new byte[2 + digest.length];
        multihash[0] = (byte) algorithmCode;
        multihash[1] = (byte) digest.length;
        System.arraycopy(digest, 0, multihash, 2, digest.length);
        return multihash;
    }

    public static int extractAlgorithm(byte[] multihash) {
        if (multihash.length < 2) {
            throw new ValidationException("Multihash too short");
        }
        return multihash[0] & 0xFF;
    }

    public static byte[] extractDigest(byte[] multihash) {
        if (multihash.length < 2) {
            throw new ValidationException("Multihash too short");
        }
        int length = multihash[1] & 0xFF;
        if (multihash.length < 2 + length) {
            throw new ValidationException(
                    "Multihash digest length mismatch: declared " + length
                            + " but only " + (multihash.length - 2) + " bytes available");
        }
        byte[] digest = new byte[length];
        System.arraycopy(multihash, 2, digest, 0, length);
        return digest;
    }

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new ValidationException("SHA-256 not available", e);
        }
    }
}
