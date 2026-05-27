package io.github.decentralizedidentity.didwebvh.core.update;

import io.github.decentralizedidentity.didwebvh.core.DidWebVhState;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;

/**
 * Builder for configuring a DID migration (spec section 3.7.6).
 *
 * <p>Required fields: {@code existingState}, {@code signer}, and {@code newDomain}.
 * The DID must have been created with {@code portable: true}.
 */
public final class MigrateDidConfig {

    private final DidWebVhState existingState;
    private final Signer signer;
    private final String newDomain;
    private String newPath;

    public MigrateDidConfig(DidWebVhState existingState, Signer signer, String newDomain) {
        this.existingState = existingState;
        this.signer = signer;
        this.newDomain = newDomain;
    }

    /** Optional path component for the new DID (e.g. {@code "dids:issuer"}). */
    public MigrateDidConfig newPath(String path) {
        this.newPath = path;
        return this;
    }

    /** Execute the migration and return the result. */
    public UpdateDidResult execute() {
        return MigrateDidOperation.execute(this);
    }

    DidWebVhState getExistingState() {
        return existingState;
    }

    Signer getSigner() {
        return signer;
    }

    String getNewDomain() {
        return newDomain;
    }

    String getNewPath() {
        return newPath;
    }
}
