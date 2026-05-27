package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.JsonObject;

import java.util.Objects;

/** Result of resolving a did:webvh DID URL. */
public final class ResolveResult {

    private DidDocument didDocument;
    private ResolutionMetadata metadata;
    private String error;
    private JsonObject problemDetails;

    public ResolveResult() {
        // empty
    }

    public DidDocument getDidDocument() {
        return didDocument;
    }

    public ResolveResult setDidDocument(DidDocument didDocument) {
        this.didDocument = didDocument;
        return this;
    }

    public ResolutionMetadata getMetadata() {
        return metadata;
    }

    public ResolveResult setMetadata(ResolutionMetadata metadata) {
        this.metadata = metadata;
        return this;
    }

    public String getError() {
        return error;
    }

    public ResolveResult setError(String error) {
        this.error = error;
        return this;
    }

    public JsonObject getProblemDetails() {
        return problemDetails;
    }

    public ResolveResult setProblemDetails(JsonObject problemDetails) {
        this.problemDetails = problemDetails;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResolveResult)) {
            return false;
        }
        ResolveResult that = (ResolveResult) o;
        return Objects.equals(didDocument, that.didDocument)
                && Objects.equals(metadata, that.metadata)
                && Objects.equals(error, that.error)
                && Objects.equals(problemDetails, that.problemDetails);
    }

    @Override
    public int hashCode() {
        return Objects.hash(didDocument, metadata, error, problemDetails);
    }
}
