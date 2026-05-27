package io.github.decentralizedidentity.didwebvh.core.update;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;

/**
 * Builder for configuring a standard DID update (spec section 3.6.3).
 *
 * <p>Required fields: {@code existingState} and {@code signer}.
 */
public final class UpdateDidConfig {

    private final DidWebVhState existingState;
    private final Signer signer;
    private JsonObject newDocument;
    private Parameters changedParameters;

    public UpdateDidConfig(DidWebVhState existingState, Signer signer) {
        this.existingState = existingState;
        this.signer = signer;
    }

    /** Replace the DID Document state. If not set the existing state document is kept. */
    public UpdateDidConfig newDocument(JsonObject document) {
        this.newDocument = document == null ? null : document.deepCopy();
        return this;
    }

    /**
     * Partial parameters to change.  Only non-null fields are applied on top of the
     * current active parameters.  Pass {@code new Parameters()} (all-null) to include
     * an empty {@code {}} parameters delta.
     */
    public UpdateDidConfig changedParameters(Parameters parameters) {
        this.changedParameters = parameters;
        return this;
    }

    /** Execute the update and return the result. */
    public UpdateDidResult execute() {
        return UpdateDidOperation.execute(this);
    }

    DidWebVhState getExistingState() {
        return existingState;
    }

    Signer getSigner() {
        return signer;
    }

    JsonObject getNewDocument() {
        return newDocument;
    }

    Parameters getChangedParameters() {
        return changedParameters;
    }
}
