package io.github.decentralizedidentity.didwebvh.signing.local;

import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofVerifier;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class LocalKeySignerTest {

    private static LogEntry sampleEntry() {
        return new LogEntry()
                .setVersionId("1-test")
                .setVersionTime("2025-06-01T00:00:00Z")
                .setParameters(new Parameters().setMethod("did:webvh:1.0"));
    }

    @Test
    void generateProducesValidSigner() {
        LocalKeySigner signer = LocalKeySigner.generate();

        assertThat(signer.keyType()).isEqualTo("Ed25519");
        assertThat(signer.getPublicKeyMultikey()).startsWith("z6Mk");
        assertThat(signer.verificationMethod())
                .startsWith("did:key:z6Mk")
                .contains("#z6Mk");
    }

    @Test
    void signAndVerifyRoundTrip() {
        LocalKeySigner signer = LocalKeySigner.generate();
        LogEntry entry = sampleEntry();

        DataIntegrityProof proof = ProofGenerator.generate(signer, entry);
        assertThat(ProofVerifier.verify(proof, entry)).isTrue();
    }

    @Test
    void jsonRoundTrip() {
        LocalKeySigner original = LocalKeySigner.generate();
        String json = original.toJson();

        LocalKeySigner restored = LocalKeySigner.fromJson(json);

        assertThat(restored.keyType()).isEqualTo(original.keyType());
        assertThat(restored.getPublicKeyMultikey())
                .isEqualTo(original.getPublicKeyMultikey());
        assertThat(restored.verificationMethod())
                .isEqualTo(original.verificationMethod());

        // sign with restored, verify with original's public key
        LogEntry entry = sampleEntry();
        DataIntegrityProof proof = ProofGenerator.generate(restored, entry);
        assertThat(ProofVerifier.verify(proof, entry)).isTrue();
    }

    @Test
    void fromPrivateKeyRecoversPublicKey() {
        LocalKeySigner original = LocalKeySigner.generate();
        String json = original.toJson();
        LocalKeySigner fromJson = LocalKeySigner.fromJson(json);

        assertThat(fromJson.getPublicKeyMultikey())
                .isEqualTo(original.getPublicKeyMultikey());
    }

    @Test
    void fromJsonWithKnownVector() {
        LocalKeySigner signer = LocalKeySigner.generate();
        String json = signer.toJson();

        assertThat(json).contains("\"kty\":\"OKP\"");
        assertThat(json).contains("\"crv\":\"Ed25519\"");
        assertThat(json).contains("\"x\":");
        assertThat(json).contains("\"d\":");

        LocalKeySigner restored = LocalKeySigner.fromJson(json);
        assertThat(restored.getPublicKeyMultikey())
                .isEqualTo(signer.getPublicKeyMultikey());
    }

    @Test
    void isAuthorizedWithMatchingKey() {
        LocalKeySigner signer = LocalKeySigner.generate();
        LogEntry entry = sampleEntry();

        DataIntegrityProof proof = ProofGenerator.generate(signer, entry);

        assertThat(ProofVerifier.isAuthorized(proof,
                Collections.singletonList(signer.getPublicKeyMultikey())))
                .isTrue();
    }

    @Test
    void isNotAuthorizedWithDifferentKey() {
        LocalKeySigner signer1 = LocalKeySigner.generate();
        LocalKeySigner signer2 = LocalKeySigner.generate();
        LogEntry entry = sampleEntry();

        DataIntegrityProof proof = ProofGenerator.generate(signer1, entry);

        assertThat(ProofVerifier.isAuthorized(proof,
                Collections.singletonList(signer2.getPublicKeyMultikey())))
                .isFalse();
    }

    @Test
    void publicKeyMultikeyDecodesCorrectly() {
        LocalKeySigner signer = LocalKeySigner.generate();
        String multikey = signer.getPublicKeyMultikey();

        String keyType = MultikeyUtil.keyTypeFromMultikey(multikey);
        assertThat(keyType).isEqualTo("Ed25519");

        byte[] raw = MultikeyUtil.decode(multikey);
        assertThat(raw).hasSize(32);
    }
}
