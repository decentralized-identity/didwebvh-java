package io.github.decentralizedidentity.didwebvh.core;

import io.github.decentralizedidentity.didwebvh.core.create.CreateDidConfig;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.ResolveResult;
import io.github.decentralizedidentity.didwebvh.core.resolve.DidResolver;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.update.DeactivateDidConfig;
import io.github.decentralizedidentity.didwebvh.core.update.MigrateDidConfig;
import io.github.decentralizedidentity.didwebvh.core.update.UpdateDidConfig;
import io.github.decentralizedidentity.didwebvh.core.validate.LogChainValidator;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;

import java.util.List;

/**
 * Main entry point for the did:webvh library.
 *
 * <p>Usage:
 * <pre>{@code
 * CreateDidResult result = DidWebVh.create("example.com", signer)
 *         .portable(true)
 *         .ttl(3600)
 *         .execute();
 *
 * DidWebVhState state = DidWebVhState.from(result.getDid(), result.getLogEntry());
 * UpdateDidResult updated = DidWebVh.update(state, signer)
 *         .newDocument(newDoc)
 *         .execute();
 * }</pre>
 */
public final class DidWebVh {

    private DidWebVh() {
    }

    /**
     * Begin configuring a new DID creation for the given domain and signer.
     *
     * @param domain the web domain (e.g. {@code "example.com"})
     * @param signer the signing key to use
     * @return a builder for further configuration
     */
    public static CreateDidConfig create(String domain, Signer signer) {
        return new CreateDidConfig(domain, signer);
    }

    /**
     * Begin configuring a standard DID update (spec section 3.6.3).
     *
     * @param state  the current DID state (all existing log entries)
     * @param signer the authorised signing key
     * @return a builder for further configuration
     */
    public static UpdateDidConfig update(DidWebVhState state, Signer signer) {
        return new UpdateDidConfig(state, signer);
    }

    /**
     * Begin configuring a DID migration to a new domain (spec section 3.7.6).
     * The DID must have been created with {@code portable: true}.
     *
     * @param state     the current DID state
     * @param signer    the authorised signing key
     * @param newDomain the target domain (e.g. {@code "new.example.com"})
     * @return a builder for further configuration
     */
    public static MigrateDidConfig migrate(DidWebVhState state, Signer signer,
                                           String newDomain) {
        return new MigrateDidConfig(state, signer, newDomain);
    }

    /**
     * Begin configuring DID deactivation (spec section 3.6.4).
     *
     * @param state  the current DID state
     * @param signer the authorised signing key
     * @return a builder for further configuration
     */
    public static DeactivateDidConfig deactivate(DidWebVhState state, Signer signer) {
        return new DeactivateDidConfig(state, signer);
    }

    /**
     * Validate a log chain against an expected DID.
     *
     * @param entries     the ordered list of log entries
     * @param expectedDid the DID being resolved (may be {@code null} to skip id check)
     * @return the validation result
     */
    public static ValidationResult validate(List<LogEntry> entries, String expectedDid) {
        return new LogChainValidator().validate(entries, expectedDid);
    }

    /**
     * Resolve a did:webvh DID over HTTPS.
     *
     * @param did the DID to resolve
     * @return the resolution result
     */
    public static ResolveResult resolve(String did) {
        return new DidResolver().resolve(did);
    }
}
