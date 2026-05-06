package io.github.decentralizedidentity.didwebvh.core.signing;

import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;

class ProofGeneratorTest {

    private static Signer testSigner;

    @BeforeAll
    static void setUp() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        Ed25519PrivateKeyParameters priv =
                (Ed25519PrivateKeyParameters) pair.getPrivate();
        Ed25519PublicKeyParameters pub =
                (Ed25519PublicKeyParameters) pair.getPublic();

        String multikey = MultikeyUtil.encode(
                MultikeyUtil.ED25519_KEY_TYPE, pub.getEncoded());
        String vm = "did:key:" + multikey + "#" + multikey;

        testSigner = new Signer() {
            @Override
            public String keyType() {
                return "Ed25519";
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

    private static LogEntry sampleEntry() {
        return new LogEntry()
                .setVersionId("1-abc")
                .setVersionTime("2025-01-01T00:00:00Z")
                .setParameters(new Parameters().setMethod("did:webvh:1.0"));
    }

    @Test
    void generateProducesValidStructure() {
        LogEntry entry = sampleEntry();

        DataIntegrityProof proof = ProofGenerator.generate(testSigner, entry);

        assertThat(proof.getType()).isEqualTo(DataIntegrityProof.DEFAULT_TYPE);
        assertThat(proof.getCryptosuite())
                .isEqualTo(DataIntegrityProof.DEFAULT_CRYPTOSUITE);
        assertThat(proof.getProofPurpose())
                .isEqualTo(DataIntegrityProof.DEFAULT_PROOF_PURPOSE);
        assertThat(proof.getVerificationMethod())
                .isEqualTo(testSigner.verificationMethod());
        assertThat(proof.getCreated()).isNotNull();
        assertThat(proof.getProofValue()).startsWith("z");
    }

    @Test
    void generatedProofVerifies() {
        LogEntry entry = sampleEntry();

        DataIntegrityProof proof = ProofGenerator.generate(testSigner, entry);

        assertThat(ProofVerifier.verify(proof, entry)).isTrue();
    }

    @Test
    void tamperedDataFailsVerification() {
        LogEntry entry = sampleEntry();

        DataIntegrityProof proof = ProofGenerator.generate(testSigner, entry);

        LogEntry tampered = sampleEntry().setVersionId("2-xyz");

        assertThat(ProofVerifier.verify(proof, tampered)).isFalse();
    }

    @Test
    void tamperedProofValueFailsVerification() {
        LogEntry entry = sampleEntry();

        DataIntegrityProof proof = ProofGenerator.generate(testSigner, entry);

        // decode, flip a byte, re-encode
        byte[] sig = Base58Btc.decodeMultibase(proof.getProofValue());
        sig[0] ^= 0xFF;
        proof.setProofValue(Base58Btc.encodeMultibase(sig));

        assertThat(ProofVerifier.verify(proof, entry)).isFalse();
    }
}
