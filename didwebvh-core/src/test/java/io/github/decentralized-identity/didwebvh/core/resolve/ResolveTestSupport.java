package io.github.decentralizedidentity.didwebvh.core.resolve;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.crypto.EntryHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;

final class ResolveTestSupport {

    private ResolveTestSupport() {
    }

    static LogEntry buildUpdateEntry(LogEntry previous, Signer updateSigner,
                                     Parameters changedParams, JsonObject newState) {
        int newVersion = previous.getVersionNumber() + 1;
        JsonObject state = newState == null ? previous.getState().deepCopy() : newState;
        Parameters params = changedParams == null ? new Parameters() : changedParams;
        LogEntry entry = new LogEntry()
                .setVersionId(previous.getVersionId())
                .setVersionTime(Instant.now().toString())
                .setParameters(params)
                .setState(state);

        String entryHash = EntryHashGenerator.generate(entry.toJsonLine(),
                previous.getVersionId());
        entry.setVersionId(newVersion + "-" + entryHash);
        return entry.setProof(Collections.singletonList(
                ProofGenerator.generate(updateSigner, entry)));
    }

    static WitnessProofCollection witnessProofs(String versionId,
                                                Signer witnessSigner) {
        JsonObject doc = new JsonObject();
        doc.addProperty("versionId", versionId);
        WitnessProofEntry entry = new WitnessProofEntry(versionId,
                Collections.singletonList(signDocument(witnessSigner, doc)));
        return new WitnessProofCollection(Collections.singletonList(entry));
    }

    static DataIntegrityProof signDocument(Signer signer, JsonObject doc) {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod(signer.verificationMethod())
                .setCreated(Instant.now().toString());
        byte[] hashData = io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator
                .buildHashData(proof, doc);
        byte[] signature = signer.sign(hashData);
        return proof.setProofValue(Base58Btc.encodeMultibase(signature));
    }

    static Signer makeTestSigner() {
        Ed25519KeyPairGenerator generator = new Ed25519KeyPairGenerator();
        generator.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        AsymmetricCipherKeyPair pair = generator.generateKeyPair();
        Ed25519PrivateKeyParameters privateKey =
                (Ed25519PrivateKeyParameters) pair.getPrivate();
        Ed25519PublicKeyParameters publicKey =
                (Ed25519PublicKeyParameters) pair.getPublic();
        String multikey = MultikeyUtil.encode(MultikeyUtil.ED25519_KEY_TYPE,
                publicKey.getEncoded());
        String verificationMethod = "did:key:" + multikey + "#" + multikey;

        return new Signer() {
            @Override
            public String keyType() {
                return "Ed25519";
            }

            @Override
            public String verificationMethod() {
                return verificationMethod;
            }

            @Override
            public byte[] sign(byte[] data) {
                Ed25519Signer signer = new Ed25519Signer();
                signer.init(true, privateKey);
                signer.update(data, 0, data.length);
                return signer.generateSignature();
            }
        };
    }

    static String extractMultikey(String verificationMethod) {
        int hash = verificationMethod.indexOf('#');
        if (hash >= 0) {
            return verificationMethod.substring(hash + 1);
        }
        return verificationMethod.substring("did:key:".length());
    }
}
