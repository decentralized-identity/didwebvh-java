package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.ValidationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** A single entry in a did:webvh log (one JSONL line). */
public final class LogEntry {

    private String versionId;
    private String versionTime;
    private Parameters parameters;
    private JsonObject state;
    private List<DataIntegrityProof> proof;

    public LogEntry() {
        // empty
    }

    public String getVersionId() {
        return versionId;
    }

    public LogEntry setVersionId(String versionId) {
        this.versionId = versionId;
        return this;
    }

    public String getVersionTime() {
        return versionTime;
    }

    public LogEntry setVersionTime(String versionTime) {
        this.versionTime = versionTime;
        return this;
    }

    public Parameters getParameters() {
        return parameters;
    }

    public LogEntry setParameters(Parameters parameters) {
        this.parameters = parameters;
        return this;
    }

    public JsonObject getState() {
        return state;
    }

    public LogEntry setState(JsonObject state) {
        this.state = state;
        return this;
    }

    public List<DataIntegrityProof> getProof() {
        return proof;
    }

    public LogEntry setProof(List<DataIntegrityProof> proof) {
        this.proof = proof == null ? null : Collections.unmodifiableList(new ArrayList<>(proof));
        return this;
    }

    /** Serialize this entry to a compact single-line JSON string (suitable for JSONL). */
    public String toJsonLine() {
        return JsonSupport.compact().toJson(this);
    }

    /** Parse a single JSONL line into a LogEntry. */
    public static LogEntry fromJsonLine(String line) {
        if (line == null || line.isEmpty()) {
            throw new ValidationException("log entry line must be non-empty");
        }
        return JsonSupport.compact().fromJson(line, LogEntry.class);
    }

    public int getVersionNumber() {
        return VersionId.parse(versionId).getVersionNumber();
    }

    public String getEntryHash() {
        return VersionId.parse(versionId).getEntryHash();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LogEntry)) {
            return false;
        }
        LogEntry that = (LogEntry) o;
        return Objects.equals(versionId, that.versionId)
                && Objects.equals(versionTime, that.versionTime)
                && Objects.equals(parameters, that.parameters)
                && Objects.equals(state, that.state)
                && Objects.equals(proof, that.proof);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, versionTime, parameters, state, proof);
    }
}
