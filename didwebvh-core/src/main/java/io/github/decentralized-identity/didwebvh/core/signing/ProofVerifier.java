package io.github.decentralizedidentity.didwebvh.core.signing;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultikeyUtil;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.util.List;

/** Verifies Data Integrity proofs (eddsa-jcs-2022) on log entries. */
public final class ProofVerifier {

    private ProofVerifier() {
    }

    /**
     * Verify the cryptographic signature in a Data Integrity proof.
     *
     * <p>The proof field is stripped internally before canonicalization.
     *
     * @param proof    the proof to verify
     * @param logEntry the log entry that was signed
     * @return {@code true} if the signature is valid
     */
    public static boolean verify(DataIntegrityProof proof, LogEntry logEntry) {
        return verify(proof, ProofGenerator.toJsonWithoutProof(logEntry));
    }

    /**
     * Verify a Data Integrity proof over an arbitrary JSON document.
     *
     * @param proof    the proof to verify
     * @param document the JSON document that was signed (JCS-canonicalized internally)
     * @return {@code true} if the signature is valid
     */
    public static boolean verify(DataIntegrityProof proof, JsonObject document) {
        String multikey = extractMultikey(proof.getVerificationMethod());
        byte[] publicKeyBytes = MultikeyUtil.decode(multikey);

        byte[] hashData = ProofGenerator.buildHashData(proof, document);
        byte[] signature = Base58Btc.decodeMultibase(proof.getProofValue());

        Ed25519PublicKeyParameters publicKey =
                new Ed25519PublicKeyParameters(publicKeyBytes, 0);
        Ed25519Signer verifier = new Ed25519Signer();
        verifier.init(false, publicKey);
        verifier.update(hashData, 0, hashData.length);
        return verifier.verifySignature(signature);
    }

    /**
     * Check whether the proof's signing key is in the active update keys list.
     *
     * @param proof the proof whose verification method to check
     * @param activeUpdateKeys the currently active update keys (multikey format)
     * @return {@code true} if the signing key is authorized
     */
    public static boolean isAuthorized(DataIntegrityProof proof,
                                       List<String> activeUpdateKeys) {
        String multikey = extractMultikey(proof.getVerificationMethod());
        return activeUpdateKeys.contains(multikey);
    }

    /**
     * Extract the multikey portion from a {@code did:key:<multikey>#<multikey>} URI.
     */
    public static String extractMultikey(String verificationMethod) {
        if (verificationMethod == null) {
            throw new ValidationException("verificationMethod is null");
        }
        // did:key:z6Mk...#z6Mk...  ->  z6Mk...  (fragment part)
        int hashIdx = verificationMethod.indexOf('#');
        if (hashIdx >= 0) {
            return verificationMethod.substring(hashIdx + 1);
        }
        // Fallback: strip did:key: prefix
        String prefix = "did:key:";
        if (verificationMethod.startsWith(prefix)) {
            return verificationMethod.substring(prefix.length());
        }
        throw new ValidationException(
                "Cannot extract multikey from verificationMethod: "
                        + verificationMethod);
    }
}
