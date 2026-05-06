package io.github.decentralizedidentity.didwebvh.core.validate;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.crypto.EntryHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.crypto.PreRotationHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.crypto.ScidGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.model.VersionId;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofVerifier;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

/** Validates the integrity of a did:webvh log chain (spec section 3.6.2). */
public final class LogChainValidator {

    public ValidationResult validate(List<LogEntry> entries, String expectedDid) {
        if (entries == null || entries.isEmpty()) {
            return ValidationResult.failure(-1, -1, "log must not be empty", null);
        }

        Parameters activeParams = Parameters.defaults();
        String previousVersionId = null;
        Instant previousVersionTime = null;
        boolean deactivated = false;
        boolean didFound = false;

        for (int i = 0; i < entries.size(); i++) {
            LogEntry entry = entries.get(i);

            // 0. Fast-fail: no entries allowed after deactivation
            if (deactivated) {
                return ValidationResult.failure(i - 1, i,
                        "entry after deactivation at entry " + (i + 1), activeParams);
            }

            // 1 & 2. Parse versionId and check version number
            VersionId vid;
            try {
                vid = VersionId.parse(entry.getVersionId());
            } catch (ValidationException e) {
                return ValidationResult.failure(i - 1, i,
                        "invalid versionId at entry " + (i + 1) + ": " + e.getMessage(),
                        activeParams);
            }
            int expectedVersion = i + 1;
            if (vid.getVersionNumber() != expectedVersion) {
                return ValidationResult.failure(i - 1, i,
                        "expected versionNumber " + expectedVersion + " but was "
                                + vid.getVersionNumber() + " at entry " + (i + 1),
                        activeParams);
            }

            // 3. Merge parameters
            Parameters entryParams = entry.getParameters() != null
                    ? entry.getParameters() : new Parameters();
            Parameters newActiveParams = activeParams.merge(entryParams);

            // 4. Validate parameters
            String paramError = validateParameters(entryParams, newActiveParams, activeParams, i);
            if (paramError != null) {
                return ValidationResult.failure(i - 1, i, paramError, activeParams);
            }
            // entryParams = the delta from this entry (only changed fields)
            // newActiveParams = fully accumulated state after applying entryParams
            // activeParams = previously accumulated state before this entry

            // 5. First entry: verify SCID
            if (i == 0) {
                String scid = newActiveParams.getScid();
                if (!ScidGenerator.verify(scid, entry)) {
                    return ValidationResult.failure(-1, 0, "SCID verification failed", activeParams);
                }
            }

            // 6. Verify entry hash
            String predecessorVersionId = (i == 0) ? newActiveParams.getScid() : previousVersionId;
            if (!EntryHashGenerator.verify(entry, predecessorVersionId)) {
                return ValidationResult.failure(i - 1, i,
                        "entry hash verification failed at entry " + (i + 1), activeParams);
            }

            // 7. Verify Data Integrity proof
            if (entry.getProof() == null || entry.getProof().isEmpty()) {
                return ValidationResult.failure(i - 1, i,
                        "missing proof at entry " + (i + 1), activeParams);
            }
            DataIntegrityProof proof = entry.getProof().get(0);
            // Authorization uses PREVIOUS active keys for i>0 so that key-rotation entries
            // are authorized by the old key (the new keys only take effect after validation).
            List<String> signingKeys = (i == 0)
                    ? newActiveParams.getUpdateKeys() : activeParams.getUpdateKeys();
            if (signingKeys == null || signingKeys.isEmpty()) {
                return ValidationResult.failure(i - 1, i,
                        "no active updateKeys at entry " + (i + 1), activeParams);
            }
            if (!ProofVerifier.isAuthorized(proof, signingKeys)) {
                return ValidationResult.failure(i - 1, i,
                        "signing key not in active updateKeys at entry " + (i + 1), activeParams);
            }
            if (!ProofVerifier.verify(proof, entry)) {
                return ValidationResult.failure(i - 1, i,
                        "proof signature invalid at entry " + (i + 1), activeParams);
            }

            // 8. Verify versionTime
            Instant vt;
            try {
                vt = Instant.parse(entry.getVersionTime());
            } catch (DateTimeParseException e) {
                return ValidationResult.failure(i - 1, i,
                        "invalid versionTime at entry " + (i + 1) + ": " + e.getMessage(),
                        activeParams);
            }
            if (previousVersionTime != null && !vt.isAfter(previousVersionTime)) {
                return ValidationResult.failure(i - 1, i,
                        "versionTime must be after previous entry at entry " + (i + 1), activeParams);
            }

            // 9. Check DID Document id
            if (entry.getState() != null && entry.getState().has("id")) {
                String docId = entry.getState().get("id").getAsString();
                if (expectedDid == null || docId.equals(expectedDid)) {
                    didFound = true;
                }
            }

            // 10. Pre-rotation: if previous entry set nextKeyHashes and this entry rotates keys
            if (i > 0 && entryParams.getUpdateKeys() != null) {
                List<String> prevNextKeyHashes = activeParams.getNextKeyHashes();
                if (prevNextKeyHashes != null && !prevNextKeyHashes.isEmpty()) {
                    for (String key : entryParams.getUpdateKeys()) {
                        String hash = PreRotationHashGenerator.generateHash(key);
                        if (!prevNextKeyHashes.contains(hash)) {
                            return ValidationResult.failure(i - 1, i,
                                    "updateKey hash not in previous nextKeyHashes at entry " + (i + 1),
                                    activeParams);
                        }
                    }
                }
            }

            // 11. Record deactivation for next iteration
            if (Boolean.TRUE.equals(newActiveParams.getDeactivated())) {
                deactivated = true;
            }

            activeParams = newActiveParams;
            previousVersionId = entry.getVersionId();
            previousVersionTime = vt;
        }

        // Last entry's versionTime must not be in the future. Allow small skew since
        // versionTime is second-precision and rapid updates may bump by 1s to stay strictly increasing.
        if (previousVersionTime != null
                && previousVersionTime.isAfter(Instant.now().plus(Duration.ofSeconds(60)))) {
            return ValidationResult.failure(entries.size() - 2, entries.size() - 1,
                    "last entry versionTime is in the future", activeParams);
        }

        if (expectedDid != null && !didFound) {
            return ValidationResult.failure(entries.size() - 1, -1,
                    "no entry has DID Document id matching expectedDid: " + expectedDid,
                    activeParams);
        }

        return ValidationResult.success(entries.size() - 1, activeParams);
    }

    /**
     * Validate parameter rules for a single entry.
     *
     * @param entryDelta  the parameter fields present in this entry only (the delta)
     * @param newActive   the fully accumulated parameters after applying {@code entryDelta}
     * @param prevActive  the accumulated parameters from the previous entry (before this delta)
     * @param index       0-based index of the entry in the log
     * @return an error message, or {@code null} if valid
     */
    private String validateParameters(Parameters entryDelta, Parameters newActive,
                                      Parameters prevActive, int index) {
        if (index == 0) {
            if (newActive.getMethod() == null) {
                return "first entry must specify method";
            }
            if (newActive.getScid() == null) {
                return "first entry must specify scid";
            }
            if (newActive.getUpdateKeys() == null || newActive.getUpdateKeys().isEmpty()) {
                return "first entry must specify updateKeys";
            }
        } else {
            if (entryDelta.getScid() != null) {
                return "scid must only appear in first entry";
            }
            if (entryDelta.getMethod() != null && prevActive.getMethod() != null
                    && compareMethod(entryDelta.getMethod(), prevActive.getMethod()) < 0) {
                return "method version must not decrease: " + entryDelta.getMethod()
                        + " < " + prevActive.getMethod();
            }
            // portable cannot be set from false to true after first entry
            if (Boolean.TRUE.equals(entryDelta.getPortable())
                    && !Boolean.TRUE.equals(prevActive.getPortable())) {
                return "portable can only be set to true in first entry";
            }
        }

        // Witness config bounds check – only applies when witnesses are actually configured
        WitnessConfig witness = newActive.getWitness();
        if (witness != null && witness.isActive()) {
            if (witness.getThreshold() < 1) {
                return "witness threshold must be >= 1";
            }
            if (witness.getThreshold() > witness.getWitnesses().size()) {
                return "witness threshold " + witness.getThreshold()
                        + " exceeds witness count " + witness.getWitnesses().size();
            }
        }

        return null;
    }

    private int compareMethod(String a, String b) {
        String va = extractMethodVersion(a);
        String vb = extractMethodVersion(b);
        if (va == null || vb == null) {
            return 0;
        }
        String[] pa = va.split("\\.", -1);
        String[] pb = vb.split("\\.", -1);
        int len = Math.min(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            try {
                int cmp = Integer.compare(Integer.parseInt(pa[i]), Integer.parseInt(pb[i]));
                if (cmp != 0) {
                    return cmp;
                }
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return Integer.compare(pa.length, pb.length);
    }

    private String extractMethodVersion(String method) {
        int lastColon = method.lastIndexOf(':');
        return lastColon >= 0 ? method.substring(lastColon + 1) : null;
    }
}
