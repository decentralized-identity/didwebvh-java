package io.github.decentralizedidentity.didwebvh.core.signing;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.crypto.Base58Btc;
import io.github.decentralizedidentity.didwebvh.core.crypto.Jcs;
import io.github.decentralizedidentity.didwebvh.core.crypto.MultihashUtil;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Generates Data Integrity proofs for log entries using eddsa-jcs-2022. */
public final class ProofGenerator {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    private ProofGenerator() {
    }

    /**
     * Generate a Data Integrity proof over a log entry.
     *
     * <p>The proof field is stripped internally before canonicalization.
     *
     * @param signer   the signer to use
     * @param logEntry the log entry to sign
     * @return a populated {@link DataIntegrityProof}
     */
    public static DataIntegrityProof generate(Signer signer, LogEntry logEntry) {
        DataIntegrityProof proof = DataIntegrityProof.defaults()
                .setVerificationMethod(signer.verificationMethod())
                .setCreated(ISO_UTC.format(Instant.now()));

        JsonObject doc = toJsonWithoutProof(logEntry);
        byte[] hashData = buildHashData(proof, doc);
        byte[] signature = signer.sign(hashData);
        proof.setProofValue(Base58Btc.encodeMultibase(signature));
        return proof;
    }

    /**
     * Build the eddsa-jcs-2022 hash-to-sign:
     * {@code SHA256(JCS(proofConfig)) || SHA256(JCS(document))}.
     * proofConfig is the proof without {@code proofValue}.
     */
    public static byte[] buildHashData(DataIntegrityProof proof, JsonObject document) {
        JsonObject proofConfig = JsonSupport.compact().toJsonTree(proof)
                .getAsJsonObject();
        proofConfig.remove("proofValue");
        byte[] proofHash = MultihashUtil.sha256(Jcs.canonicalize(proofConfig));
        byte[] docHash = MultihashUtil.sha256(Jcs.canonicalize(document));
        byte[] out = new byte[proofHash.length + docHash.length];
        System.arraycopy(proofHash, 0, out, 0, proofHash.length);
        System.arraycopy(docHash, 0, out, proofHash.length, docHash.length);
        return out;
    }

    /** Serialize a LogEntry to JsonObject with the {@code proof} key removed. */
    static JsonObject toJsonWithoutProof(LogEntry logEntry) {
        JsonObject json = JsonSupport.compact().toJsonTree(logEntry)
                .getAsJsonObject();
        json.remove("proof");
        return json;
    }
}
