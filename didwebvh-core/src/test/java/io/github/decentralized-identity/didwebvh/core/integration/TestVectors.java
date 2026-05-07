package io.github.decentralizedidentity.didwebvh.core.integration;

import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shared loader and deterministic signer factory for the spec test vectors
 * under {@code src/test/resources/test-vectors/}.
 *
 * <p>The seed bytes here are the same seeds used by
 * {@link TestVectorGenerator} when producing the committed vectors, so
 * resolved DIDs, multikeys, and SCIDs are reproducible.
 */
final class TestVectors {

    static final String RESOURCE_ROOT = "/test-vectors/";

    // Deterministic 32-byte Ed25519 seeds used across the vector files.
    // Values are arbitrary but committed so vectors can be regenerated
    // byte-for-byte by running TestVectorGenerator.
    static final byte[] AUTHOR_SEED = seed((byte) 0x01);
    static final byte[] UPDATE_SEED = seed((byte) 0x02);
    static final byte[] NEXT_SEED = seed((byte) 0x03);
    static final byte[] WITNESS_SEED = seed((byte) 0x04);

    private TestVectors() {
    }

    static String readResource(String name) {
        String path = RESOURCE_ROOT + name;
        try (InputStream in = TestVectors.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test vector: " + path);
            }
            byte[] buf = new byte[4096];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + path, e);
        }
    }

    static List<LogEntry> parseLog(String jsonl) {
        List<LogEntry> entries = new ArrayList<>();
        for (String line : jsonl.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                entries.add(LogEntry.fromJsonLine(trimmed));
            }
        }
        return entries;
    }

    static WitnessProofCollection parseWitnessProofs(String json) {
        WitnessProofEntry[] arr = JsonSupport.compact()
                .fromJson(json, WitnessProofEntry[].class);
        return new WitnessProofCollection(Arrays.asList(arr));
    }

    /** Build a deterministic Ed25519 signer from a 32-byte seed. */
    static Signer seededSigner(byte[] seed) {
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(seed, 0);
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        String multikey = MultikeyUtil.encode(
                MultikeyUtil.ED25519_KEY_TYPE, pub.getEncoded());
        String vm = "did:key:" + multikey + "#" + multikey;
        return new Signer() {
            @Override
            public String keyType() {
                return MultikeyUtil.ED25519_KEY_TYPE;
            }

            @Override
            public String verificationMethod() {
                return vm;
            }

            @Override
            public byte[] sign(byte[] data) {
                Ed25519Signer s = new Ed25519Signer();
                s.init(true, priv);
                s.update(data, 0, data.length);
                return s.generateSignature();
            }
        };
    }

    private static byte[] seed(byte tag) {
        byte[] out = new byte[32];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) (tag ^ i);
        }
        return out;
    }
}
