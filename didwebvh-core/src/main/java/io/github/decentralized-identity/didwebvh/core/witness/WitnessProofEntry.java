package io.github.decentralizedidentity.didwebvh.core.witness;

import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One witness proof attached to a specific log versionId. */
public final class WitnessProofEntry {

    private String versionId;
    private List<DataIntegrityProof> proof;

    public WitnessProofEntry() {
        // empty
    }

    public WitnessProofEntry(String versionId, List<DataIntegrityProof> proof) {
        this.versionId = versionId;
        this.proof = proof == null ? null : Collections.unmodifiableList(new ArrayList<>(proof));
    }

    public String getVersionId() {
        return versionId;
    }

    public List<DataIntegrityProof> getProof() {
        return proof;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WitnessProofEntry)) {
            return false;
        }
        WitnessProofEntry that = (WitnessProofEntry) o;
        return Objects.equals(versionId, that.versionId) && Objects.equals(proof, that.proof);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, proof);
    }
}
