package io.github.decentralizedidentity.didwebvh.core.update;

import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;

/**
 * Builder for configuring DID deactivation (spec section 3.6.4).
 *
 * <p>Required: {@code existingState} and {@code signer} (the current authorised signing key).
 *
 * <p>If pre-rotation is active (the state has non-empty {@code nextKeyHashes}), a second signer
 * is required via {@link #nextRotationSigner(Signer)}.  The operation will:
 * <ol>
 *   <li>Emit an intermediate entry signed by {@code signer} that reveals the next keys and
 *       clears {@code nextKeyHashes}.</li>
 *   <li>Emit the final deactivation entry signed by {@code nextRotationSigner}.</li>
 * </ol>
 */
public final class DeactivateDidConfig {

    private final DidWebVhState existingState;
    private final Signer signer;
    private Signer nextRotationSigner;

    public DeactivateDidConfig(DidWebVhState existingState, Signer signer) {
        this.existingState = existingState;
        this.signer = signer;
    }

    /**
     * The signer whose public key was committed to in the previous entry's
     * {@code nextKeyHashes}.  Required when the DID was created (or last updated)
     * with pre-rotation enabled.
     */
    public DeactivateDidConfig nextRotationSigner(Signer nextRotationSigner) {
        this.nextRotationSigner = nextRotationSigner;
        return this;
    }

    /** Execute the deactivation and return the result (one or two entries). */
    public UpdateDidResult execute() {
        return DeactivateDidOperation.execute(this);
    }

    DidWebVhState getExistingState() {
        return existingState;
    }

    Signer getSigner() {
        return signer;
    }

    Signer getNextRotationSigner() {
        return nextRotationSigner;
    }
}
