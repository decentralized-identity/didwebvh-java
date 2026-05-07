package io.github.decentralizedidentity.didwebvh.core.signing;

import io.github.decentralizedidentity.didwebvh.core.SigningException;

/**
 * Abstraction for signing operations used when creating or updating a DID.
 *
 * <p>Implementations live outside the core module (e.g. {@code didwebvh-signing-local}).
 */
public interface Signer {

    /** Key algorithm name, e.g. {@code "Ed25519"}. */
    String keyType();

    /**
     * DID Key verification method URI, e.g.
     * {@code "did:key:z6Mk...#z6Mk..."}.
     */
    String verificationMethod();

    /**
     * Sign the given data and return the raw signature bytes.
     *
     * @param data bytes to sign
     * @return raw signature
     * @throws SigningException if signing fails
     */
    byte[] sign(byte[] data) throws SigningException;
}
