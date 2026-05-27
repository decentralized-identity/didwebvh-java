package io.github.decentralizedidentity.didwebvh.core.model;

import java.util.Objects;

/** Data Integrity proof attached to a log entry or witness proof. */
public final class DataIntegrityProof {

    public static final String DEFAULT_TYPE = "DataIntegrityProof";
    public static final String DEFAULT_CRYPTOSUITE = "eddsa-jcs-2022";
    public static final String DEFAULT_PROOF_PURPOSE = "assertionMethod";

    private String type;
    private String cryptosuite;
    private String verificationMethod;
    private String proofPurpose;
    private String created;
    private String proofValue;

    public DataIntegrityProof() {
        // empty
    }

    /** Proof with the did:webvh:1.0 defaults pre-populated. */
    public static DataIntegrityProof defaults() {
        DataIntegrityProof p = new DataIntegrityProof();
        p.type = DEFAULT_TYPE;
        p.cryptosuite = DEFAULT_CRYPTOSUITE;
        p.proofPurpose = DEFAULT_PROOF_PURPOSE;
        return p;
    }

    public String getType() {
        return type;
    }

    public DataIntegrityProof setType(String type) {
        this.type = type;
        return this;
    }

    public String getCryptosuite() {
        return cryptosuite;
    }

    public DataIntegrityProof setCryptosuite(String cryptosuite) {
        this.cryptosuite = cryptosuite;
        return this;
    }

    public String getVerificationMethod() {
        return verificationMethod;
    }

    public DataIntegrityProof setVerificationMethod(String verificationMethod) {
        this.verificationMethod = verificationMethod;
        return this;
    }

    public String getProofPurpose() {
        return proofPurpose;
    }

    public DataIntegrityProof setProofPurpose(String proofPurpose) {
        this.proofPurpose = proofPurpose;
        return this;
    }

    public String getCreated() {
        return created;
    }

    public DataIntegrityProof setCreated(String created) {
        this.created = created;
        return this;
    }

    public String getProofValue() {
        return proofValue;
    }

    public DataIntegrityProof setProofValue(String proofValue) {
        this.proofValue = proofValue;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DataIntegrityProof)) {
            return false;
        }
        DataIntegrityProof that = (DataIntegrityProof) o;
        return Objects.equals(type, that.type)
                && Objects.equals(cryptosuite, that.cryptosuite)
                && Objects.equals(verificationMethod, that.verificationMethod)
                && Objects.equals(proofPurpose, that.proofPurpose)
                && Objects.equals(created, that.created)
                && Objects.equals(proofValue, that.proofValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, cryptosuite, verificationMethod, proofPurpose, created, proofValue);
    }
}
