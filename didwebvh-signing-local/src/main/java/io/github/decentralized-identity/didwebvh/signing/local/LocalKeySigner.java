package io.github.decentralizedidentity.didwebvh.signing.local;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.decentralizedidentity.didwebvh.core.SigningException;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Signer backed by a local Ed25519 key pair stored in memory.
 *
 * <p>Keys can be generated, loaded from raw bytes, or deserialized from a
 * JWK-like JSON format ({@code kty=OKP, crv=Ed25519}).
 */
public final class LocalKeySigner implements Signer {

    private final Ed25519PrivateKeyParameters privateKey;
    private final Ed25519PublicKeyParameters publicKey;
    private final String multikey;

    private LocalKeySigner(Ed25519PrivateKeyParameters privateKey,
                           Ed25519PublicKeyParameters publicKey) {
        this.privateKey = privateKey;
        this.publicKey = publicKey;
        this.multikey = MultikeyUtil.encode(
                MultikeyUtil.ED25519_KEY_TYPE, publicKey.getEncoded());
    }

    /** Generate a new random Ed25519 key pair. */
    public static LocalKeySigner generate() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        return new LocalKeySigner(
                (Ed25519PrivateKeyParameters) pair.getPrivate(),
                (Ed25519PublicKeyParameters) pair.getPublic());
    }

    /**
     * Load from a JWK-like JSON string:
     * {@code {"kty":"OKP","crv":"Ed25519","x":"<base64url>","d":"<base64url>"}}.
     */
    public static LocalKeySigner fromJson(String json) {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
        byte[] privateBytes = base64UrlDecode(obj.get("d").getAsString());
        byte[] publicBytes = base64UrlDecode(obj.get("x").getAsString());
        return new LocalKeySigner(
                new Ed25519PrivateKeyParameters(privateBytes, 0),
                new Ed25519PublicKeyParameters(publicBytes, 0));
    }

    /** Load from raw 32-byte Ed25519 private key seed. */
    public static LocalKeySigner fromPrivateKey(byte[] privateKeyBytes) {
        Ed25519PrivateKeyParameters priv =
                new Ed25519PrivateKeyParameters(privateKeyBytes, 0);
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        return new LocalKeySigner(priv, pub);
    }

    /** Serialize key pair to JWK-like JSON. */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("kty", "OKP");
        obj.addProperty("crv", "Ed25519");
        obj.addProperty("x", base64UrlEncode(publicKey.getEncoded()));
        obj.addProperty("d", base64UrlEncode(privateKey.getEncoded()));
        return obj.toString();
    }

    /** Return the multikey-encoded public key (e.g. {@code z6Mk...}). */
    public String getPublicKeyMultikey() {
        return multikey;
    }

    @Override
    public String keyType() {
        return MultikeyUtil.ED25519_KEY_TYPE;
    }

    @Override
    public String verificationMethod() {
        return "did:key:" + multikey + "#" + multikey;
    }

    @Override
    public byte[] sign(byte[] data) throws SigningException {
        try {
            Ed25519Signer signer = new Ed25519Signer();
            signer.init(true, privateKey);
            signer.update(data, 0, data.length);
            return signer.generateSignature();
        } catch (Exception e) {
            throw new SigningException("Ed25519 signing failed: " + e.getMessage(), e);
        }
    }

    private static String base64UrlEncode(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static byte[] base64UrlDecode(String encoded) {
        return Base64.getUrlDecoder().decode(encoded);
    }
}
