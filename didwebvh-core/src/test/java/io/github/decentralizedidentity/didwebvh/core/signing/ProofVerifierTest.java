package io.github.decentralizedidentity.didwebvh.core.signing;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProofVerifierTest {

    private static Ed25519PrivateKeyParameters privKey;
    private static String multikey;

    @BeforeAll
    static void setUp() {
        Ed25519KeyPairGenerator gen = new Ed25519KeyPairGenerator();
        gen.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair pair = gen.generateKeyPair();
        privKey = (Ed25519PrivateKeyParameters) pair.getPrivate();
        Ed25519PublicKeyParameters pub = (Ed25519PublicKeyParameters) pair.getPublic();
        multikey = MultikeyUtil.encode(MultikeyUtil.ED25519_KEY_TYPE, pub.getEncoded());
    }

    private DataIntegrityProof signDocument(JsonObject doc) {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod("did:key:" + multikey + "#" + multikey)
                .setCreated(Instant.now().toString());
        byte[] hashData = ProofGenerator.buildHashData(proof, doc);
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privKey);
        signer.update(hashData, 0, hashData.length);
        byte[] signature = signer.generateSignature();
        proof.setProofValue(Base58Btc.encodeMultibase(signature));
        return proof;
    }

    @Test
    void isAuthorizedWithMatchingKey() {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod("did:key:z6MkKey1#z6MkKey1");

        assertThat(ProofVerifier.isAuthorized(proof,
                Arrays.asList("z6MkKey1", "z6MkKey2"))).isTrue();
    }

    @Test
    void isAuthorizedRejectsNonMatchingKey() {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod("did:key:z6MkKeyX#z6MkKeyX");

        assertThat(ProofVerifier.isAuthorized(proof,
                Arrays.asList("z6MkKey1", "z6MkKey2"))).isFalse();
    }

    @Test
    void isAuthorizedRejectsEmptyKeyList() {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod("did:key:z6MkKey1#z6MkKey1");

        assertThat(ProofVerifier.isAuthorized(proof,
                Collections.emptyList())).isFalse();
    }

    @Test
    void extractMultikeyFromFragment() {
        assertThat(ProofVerifier.extractMultikey("did:key:z6Mk123#z6Mk123"))
                .isEqualTo("z6Mk123");
    }

    @Test
    void extractMultikeyFromDidKeyWithoutFragment() {
        assertThat(ProofVerifier.extractMultikey("did:key:z6Mk456"))
                .isEqualTo("z6Mk456");
    }

    @Test
    void extractMultikeyThrowsOnNull() {
        assertThatThrownBy(() -> ProofVerifier.extractMultikey(null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void extractMultikeyThrowsOnInvalidFormat() {
        assertThatThrownBy(() -> ProofVerifier.extractMultikey("invalid"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void verifyJsonObjectValidSignature() {
        JsonObject doc = new JsonObject();
        doc.addProperty("versionId", "1-abc");
        DataIntegrityProof proof = signDocument(doc);

        assertThat(ProofVerifier.verify(proof, doc)).isTrue();
    }

    @Test
    void verifyJsonObjectRejectsTamperedDocument() {
        JsonObject doc = new JsonObject();
        doc.addProperty("versionId", "1-abc");
        DataIntegrityProof proof = signDocument(doc);

        JsonObject tampered = new JsonObject();
        tampered.addProperty("versionId", "2-different");

        assertThat(ProofVerifier.verify(proof, tampered)).isFalse();
    }

    @Test
    void verifyJsonObjectRejectsTamperedProofValue() {
        JsonObject doc = new JsonObject();
        doc.addProperty("versionId", "1-abc");
        DataIntegrityProof proof = signDocument(doc);

        // Replace proofValue with a signature over different data
        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, privKey);
        byte[] wrongBytes = "WRONG".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        signer.update(wrongBytes, 0, wrongBytes.length);
        String wrongProof = Base58Btc.encodeMultibase(signer.generateSignature());

        DataIntegrityProof badProof = DataIntegrityProof.defaults()
                .setVerificationMethod(proof.getVerificationMethod())
                .setCreated(proof.getCreated())
                .setProofValue(wrongProof);

        assertThat(ProofVerifier.verify(badProof, doc)).isFalse();
    }
}
