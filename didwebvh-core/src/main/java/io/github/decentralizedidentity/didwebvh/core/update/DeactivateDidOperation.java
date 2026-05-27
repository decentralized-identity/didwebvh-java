package io.github.decentralizedidentity.didwebvh.core.update;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.ProofVerifier;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements DID deactivation from spec section 3.6.4.
 *
 * <p>If pre-rotation is active, two entries are produced:
 * <ol>
 *   <li>Intermediate entry: reveals the next keys, clears {@code nextKeyHashes}.</li>
 *   <li>Final entry: sets {@code deactivated=true} and {@code updateKeys=[]}.</li>
 * </ol>
 * Without pre-rotation a single entry is sufficient.
 */
final class DeactivateDidOperation {

    private DeactivateDidOperation() {
    }

    static UpdateDidResult execute(DeactivateDidConfig config) {
        validate(config);

        Parameters active = config.getExistingState().accumulateParameters();
        boolean preRotationActive = active.getNextKeyHashes() != null
                && !active.getNextKeyHashes().isEmpty();

        List<LogEntry> newEntries = new ArrayList<>();

        LogEntry previous = config.getExistingState().getLastEntry();

        if (preRotationActive) {
            // Intermediate entry: signed by current key (signer), reveals the next keys
            // so that pre-rotation constraints are satisfied, then clears nextKeyHashes.
            String nextMultikey = ProofVerifier.extractMultikey(
                    config.getNextRotationSigner().verificationMethod());
            Parameters intermediate = new Parameters()
                    .setUpdateKeys(Collections.singletonList(nextMultikey))
                    .setNextKeyHashes(Collections.emptyList());
            LogEntry intermediateEntry = UpdateDidOperation.buildEntry(
                    previous,
                    config.getSigner(),
                    null,
                    intermediate);
            newEntries.add(intermediateEntry);
            previous = intermediateEntry;
        }

        // Final deactivation entry: signed by the current (or revealed next) signer
        Signer finalSigner =
                preRotationActive ? config.getNextRotationSigner() : config.getSigner();
        Parameters deactivation = new Parameters()
                .setDeactivated(Boolean.TRUE)
                .setUpdateKeys(Collections.emptyList());
        LogEntry finalEntry = UpdateDidOperation.buildEntry(
                previous,
                finalSigner,
                null,
                deactivation);
        newEntries.add(finalEntry);

        return new UpdateDidResult(newEntries);
    }

    private static void validate(DeactivateDidConfig config) {
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
            throw new ValidationException("DID is already deactivated");
        }
        Parameters active = config.getExistingState().accumulateParameters();
        boolean preRotationActive = active.getNextKeyHashes() != null
                && !active.getNextKeyHashes().isEmpty();
        if (preRotationActive && config.getNextRotationSigner() == null) {
            throw new ValidationException(
                    "pre-rotation is active; provide nextRotationSigner whose public key"
                            + " matches the committed nextKeyHashes");
        }
    }
}
