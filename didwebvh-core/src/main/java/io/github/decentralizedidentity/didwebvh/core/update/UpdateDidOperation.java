package io.github.decentralizedidentity.didwebvh.core.update;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.crypto.EntryHashGenerator;
import io.github.decentralizedidentity.didwebvh.core.model.DataIntegrityProof;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofGenerator;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

/**
 * Implements the standard DID update flow from spec section 3.6.3.
 */
final class UpdateDidOperation {

    private UpdateDidOperation() {
    }

    static UpdateDidResult execute(UpdateDidConfig config) {
        validate(config);

        LogEntry entry = buildEntry(
                config.getExistingState().getLastEntry(),
                config.getSigner(),
                config.getNewDocument(),
                config.getChangedParameters());

        return new UpdateDidResult(Collections.singletonList(entry));
    }

    /**
     * Build and sign a single new log entry succeeding {@code previous}.
     *
     * @param previous        the entry this new entry follows
     * @param signer          the signing key (must be in the active updateKeys)
     * @param newDocument     the new DID Document, or {@code null} to carry forward the existing one
     * @param changedParams   parameter delta, or {@code null} for an empty delta {@code {}}
     */
    static LogEntry buildEntry(LogEntry previous, Signer signer,
                               JsonObject newDocument, Parameters changedParams) {
        String predecessorVersionId = previous.getVersionId();
        int nextVersion = previous.getVersionNumber() + 1;

        JsonObject state = newDocument != null
                ? newDocument
                : previous.getState().deepCopy();

        Parameters params = changedParams != null ? changedParams : new Parameters();

        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant prev = Instant.parse(previous.getVersionTime());
        if (!now.isAfter(prev)) {
            now = prev.plusSeconds(1);
        }
        String versionTime = now.toString();
        LogEntry entry = new LogEntry()
                .setVersionId(predecessorVersionId)
                .setVersionTime(versionTime)
                .setParameters(params)
                .setState(state);

        // Hash uses predecessorVersionId, replacing whatever versionId is currently set
        String entryHash = EntryHashGenerator.generate(
                entry.toJsonLine(), predecessorVersionId);
        entry.setVersionId(nextVersion + "-" + entryHash);

        DataIntegrityProof proof = ProofGenerator.generate(signer, entry);
        entry.setProof(Collections.singletonList(proof));
        return entry;
    }

    private static void validate(UpdateDidConfig config) {
        if (config.getExistingState() == null) {
            throw new ValidationException("existingState is required");
        }
        if (config.getSigner() == null) {
            throw new ValidationException("signer is required");
        }
        if (config.getExistingState().getLastEntry() == null) {
            throw new ValidationException(
                    "existingState must contain at least one log entry");
        }
        if (config.getExistingState().isDeactivated()) {
            throw new ValidationException("cannot update a deactivated DID");
        }
    }
}
