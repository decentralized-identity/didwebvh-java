package io.github.decentralizedidentity.didwebvh.core.validate;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofVerifier;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofEntry;

import java.util.List;

/** Validates witness proofs for entries that require witnessing. */
public final class WitnessValidator {

    /**
     * Validate witness proofs for entries starting at {@code fromEntryIndex}.
     *
     * <p>For each entry at or after {@code fromEntryIndex} where witnessing is active,
     * verifies that at least {@code threshold} valid proofs exist from authorized witnesses.
     *
     * @param entries         the full log entry list (used to accumulate parameters)
     * @param witnessProofs   the witness proof collection from {@code did-witness.json}
     * @param fromEntryIndex  index of the first entry to check (inclusive)
     * @return the validation result
     */
    public WitnessValidationResult validate(List<LogEntry> entries,
                                            WitnessProofCollection witnessProofs,
                                            int fromEntryIndex) {
        if (witnessProofs == null) {
            return WitnessValidationResult.failure(-1, "witness proofs collection is null");
        }

        // Reconstruct active parameters up to fromEntryIndex
        Parameters activeParams = new Parameters();
        for (int i = 0; i < fromEntryIndex && i < entries.size(); i++) {
            Parameters ep = entries.get(i).getParameters();
            if (ep != null) {
                activeParams = activeParams.merge(ep);
            }
        }

        for (int i = fromEntryIndex; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);
            Parameters entryParams = entry.getParameters() != null
                    ? entry.getParameters() : new Parameters();
            activeParams = activeParams.merge(entryParams);

            WitnessConfig witnessConfig = activeParams.getWitness();
            if (witnessConfig == null || !witnessConfig.isActive()) {
                continue;
            }

            WitnessProofEntry witnessProofEntry =
                    findWitnessProof(witnessProofs, entry.getVersionId());
            if (witnessProofEntry == null) {
                return WitnessValidationResult.failure(i,
                        "missing witness proof for entry " + entry.getVersionId());
            }

            // The signed document is {"versionId": "<versionId>"}
            JsonObject signedDoc = new JsonObject();
            signedDoc.addProperty("versionId", entry.getVersionId());

            List<WitnessEntry> authorizedWitnesses = witnessConfig.getWitnesses();
            int validProofCount = 0;

            for (DataIntegrityProof proof : witnessProofEntry.getProof()) {
                String multikey = ProofVerifier.extractMultikey(proof.getVerificationMethod());
                String witnessDid = "did:key:" + multikey;
                if (!isAuthorizedWitness(witnessDid, authorizedWitnesses)) {
                    continue;
                }
                if (ProofVerifier.verify(proof, signedDoc)) {
                    validProofCount++;
                }
            }

            if (validProofCount < witnessConfig.getThreshold()) {
                return WitnessValidationResult.failure(i,
                        "insufficient witness proofs for entry " + entry.getVersionId()
                                + ": need " + witnessConfig.getThreshold()
                                + ", got " + validProofCount);
            }
        }

        return WitnessValidationResult.success();
    }

    private boolean isAuthorizedWitness(String witnessDid,
                                        List<WitnessEntry> authorizedWitnesses) {
        for (WitnessEntry w : authorizedWitnesses) {
            if (witnessDid.equals(w.getId())) {
                return true;
            }
        }
        return false;
    }

    private WitnessProofEntry findWitnessProof(WitnessProofCollection collection,
                                               String versionId) {
        for (WitnessProofEntry entry : collection.getEntries()) {
            if (versionId.equals(entry.getVersionId())) {
                return entry;
            }
        }
        return null;
    }
}
