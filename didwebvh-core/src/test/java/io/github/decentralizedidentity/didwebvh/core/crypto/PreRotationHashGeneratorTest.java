package io.github.decentralizedidentity.didwebvh.core.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PreRotationHashGeneratorTest {

    @Test
    void generateHashProducesBase58btcResult() {
        byte[] publicKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            publicKey[i] = (byte) (i + 1);
        }
        String multikey = MultikeyUtil.encode("Ed25519", publicKey);
        String hash = PreRotationHashGenerator.generateHash(multikey);
        // Spec 3.7.7: base58btc(multihash(multikey)) — no multibase prefix.
        // A SHA-256 multihash encoded in base58btc starts with "Qm" and is 46 chars.
        assertThat(hash).hasSize(46).startsWith("Qm");
    }

    @Test
    void generateHashIsDeterministic() {
        String multikey = "z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK";
        String hash1 = PreRotationHashGenerator.generateHash(multikey);
        String hash2 = PreRotationHashGenerator.generateHash(multikey);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void differentKeysProduceDifferentHashes() {
        byte[] key1 = new byte[32];
        byte[] key2 = new byte[32];
        key2[0] = 1;
        String multikey1 = MultikeyUtil.encode("Ed25519", key1);
        String multikey2 = MultikeyUtil.encode("Ed25519", key2);
        String hash1 = PreRotationHashGenerator.generateHash(multikey1);
        String hash2 = PreRotationHashGenerator.generateHash(multikey2);
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
