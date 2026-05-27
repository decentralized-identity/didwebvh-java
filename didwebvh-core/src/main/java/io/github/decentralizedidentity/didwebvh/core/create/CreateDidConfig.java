package io.github.decentralizedidentity.didwebvh.core.create;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.signing.Signer;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Builder for configuring a new DID creation.
 *
 * <p>Required fields: {@code domain} and {@code signer}.
 */
public final class CreateDidConfig {

    private final String domain;
    private final Signer signer;
    private String path;
    private Boolean portable;
    private Integer ttl;
    private List<String> alsoKnownAs;
    private List<String> controllers;
    private WitnessConfig witness;
    private List<String> watchers;
    private List<String> nextKeyHashes;
    private JsonObject additionalDocumentContent;

    public CreateDidConfig(String domain, Signer signer) {
        this.domain = domain;
        this.signer = signer;
    }

    public CreateDidConfig path(String path) {
        this.path = path;
        return this;
    }

    public CreateDidConfig portable(boolean portable) {
        this.portable = portable;
        return this;
    }

    public CreateDidConfig ttl(int ttl) {
        this.ttl = ttl;
        return this;
    }

    public CreateDidConfig alsoKnownAs(List<String> alsoKnownAs) {
        this.alsoKnownAs = alsoKnownAs == null
                ? null : new ArrayList<>(alsoKnownAs);
        return this;
    }

    /**
     * Set the DID Document {@code controller} property.
     *
     * <p>Per DID Core v1.0 §5.1.2 {@code controller} is optional; the webvh spec adds
     * no requirement on top of that.  This method lets callers opt into explicit
     * controller values:
     *
     * <ul>
     *   <li>{@code null} (default) – emit {@code "controller": "<this DID>"}, the
     *       historical behaviour;</li>
     *   <li>empty list – omit the {@code controller} property entirely;</li>
     *   <li>one element – emit {@code "controller": "<did>"} (string form);</li>
     *   <li>multiple elements – emit {@code "controller": [...]} (array form).</li>
     * </ul>
     */
    public CreateDidConfig controllers(List<String> controllers) {
        this.controllers = controllers == null
                ? null : new ArrayList<>(controllers);
        return this;
    }

    public CreateDidConfig witness(WitnessConfig witness) {
        this.witness = witness;
        return this;
    }

    public CreateDidConfig watchers(List<String> watchers) {
        this.watchers = watchers == null
                ? null : new ArrayList<>(watchers);
        return this;
    }

    public CreateDidConfig nextKeyHashes(List<String> nextKeyHashes) {
        this.nextKeyHashes = nextKeyHashes == null
                ? null : new ArrayList<>(nextKeyHashes);
        return this;
    }

    public CreateDidConfig additionalDocumentContent(JsonObject content) {
        this.additionalDocumentContent = content == null
                ? null : content.deepCopy();
        return this;
    }

    /** Execute the DID creation and return the result. */
    public CreateDidResult execute() {
        return CreateDidOperation.execute(this);
    }

    // Package-private accessors for CreateDidOperation
    String getDomain() {
        return domain;
    }

    Signer getSigner() {
        return signer;
    }

    String getPath() {
        return path;
    }

    Boolean getPortable() {
        return portable;
    }

    Integer getTtl() {
        return ttl;
    }

    List<String> getAlsoKnownAs() {
        return alsoKnownAs;
    }

    List<String> getControllers() {
        return controllers;
    }

    WitnessConfig getWitness() {
        return witness;
    }

    List<String> getWatchers() {
        return watchers;
    }

    List<String> getNextKeyHashes() {
        return nextKeyHashes;
    }

    JsonObject getAdditionalDocumentContent() {
        return additionalDocumentContent;
    }
}
