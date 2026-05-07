package io.github.decentralizedidentity.didwebvh.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.decentralizedidentity.didwebvh.core.model.JsonSupport;
import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;
import io.github.decentralizedidentity.didwebvh.core.model.Parameters;
import io.github.decentralizedidentity.didwebvh.core.validate.LogChainValidator;
import io.github.decentralizedidentity.didwebvh.core.validate.ValidationResult;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessProofCollection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the full mutable state of a did:webvh DID: log entries, witness proofs, and
 * the active parameters after validation.
 *
 * <p>This class is <em>not</em> thread-safe. Callers must synchronise if sharing across threads.
 */
public final class DidWebVhState {

    private String did;
    private final List<LogEntry> logEntries;
    private WitnessProofCollection witnessProofs;
    private Parameters activeParameters;
    private boolean validated;

    private DidWebVhState(String did, List<LogEntry> entries,
                          WitnessProofCollection witnessProofs) {
        this.did = did;
        this.logEntries = new ArrayList<>(entries);
        this.witnessProofs = witnessProofs;
    }

    /** Create a new state from a freshly created DID (single first entry). */
    public static DidWebVhState from(String did, LogEntry firstEntry) {
        return new DidWebVhState(did,
                Collections.singletonList(firstEntry), null);
    }

    /**
     * Build state by parsing a {@code did.jsonl} string.
     *
     * @param did   the DID being represented
     * @param jsonl the full contents of the {@code did.jsonl} file
     */
    public static DidWebVhState fromDidLog(String did, String jsonl) {
        List<LogEntry> entries = new ArrayList<>();
        for (String line : jsonl.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                entries.add(LogEntry.fromJsonLine(trimmed));
            }
        }
        return new DidWebVhState(did, entries, null);
    }

    /**
     * Serialize all log entries to a {@code did.jsonl} string (one entry per line,
     * no trailing newline).
     */
    public String toDidLog() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < logEntries.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(logEntries.get(i).toJsonLine());
        }
        return sb.toString();
    }

    /**
     * Serialize the full state to JSON for local caching.
     * Load it back with {@link #fromJson(String)}.
     */
    public String toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("did", did);
        JsonArray arr = new JsonArray();
        for (LogEntry entry : logEntries) {
            arr.add(entry.toJsonLine());
        }
        obj.add("logEntries", arr);
        if (witnessProofs != null) {
            obj.add("witnessProofs",
                    JsonSupport.compact().toJsonTree(witnessProofs));
        }
        return JsonSupport.compact().toJson(obj);
    }

    /** Load a previously serialised state (produced by {@link #toJson()}). */
    public static DidWebVhState fromJson(String json) {
        JsonObject obj = JsonSupport.compact().fromJson(json, JsonObject.class);
        String did = obj.get("did").getAsString();
        List<LogEntry> entries = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("logEntries")) {
            entries.add(LogEntry.fromJsonLine(el.getAsString()));
        }
        WitnessProofCollection witnessProofs = null;
        if (obj.has("witnessProofs")) {
            witnessProofs = JsonSupport.compact().fromJson(
                    obj.get("witnessProofs"), WitnessProofCollection.class);
        }
        return new DidWebVhState(did, entries, witnessProofs);
    }

    /**
     * Run full log-chain validation and update {@link #getActiveParameters()}.
     * Returns the {@link ValidationResult} – callers may inspect it for failure details.
     */
    public ValidationResult validate() {
        ValidationResult result = new LogChainValidator().validate(logEntries, did);
        if (result.isValid()) {
            activeParameters = result.getActiveParameters();
            validated = true;
        }
        return result;
    }

    /**
     * Append a new entry to the log and mark the state as unvalidated.
     * Call {@link #validate()} afterwards to confirm chain integrity.
     */
    public void appendEntry(LogEntry entry) {
        logEntries.add(entry);
        validated = false;
        activeParameters = null;
        JsonObject state = entry.getState();
        if (state != null && state.has("id")) {
            String entryDid = state.get("id").getAsString();
            if (entryDid != null && !entryDid.equals(did)) {
                did = entryDid;
            }
        }
    }

    public String getDid() {
        return did;
    }

    public List<LogEntry> getLogEntries() {
        return Collections.unmodifiableList(logEntries);
    }

    public WitnessProofCollection getWitnessProofs() {
        return witnessProofs;
    }

    public void setWitnessProofs(WitnessProofCollection witnessProofs) {
        this.witnessProofs = witnessProofs;
    }

    /**
     * The active parameters from the last {@link #validate()} call, or {@code null} if
     * the state has not been validated yet.
     */
    public Parameters getActiveParameters() {
        return activeParameters;
    }

    public boolean isValidated() {
        return validated;
    }

    /**
     * Returns {@code true} if the accumulated parameters across all entries indicate
     * the DID has been deactivated.  Does not perform cryptographic verification.
     */
    public boolean isDeactivated() {
        return Boolean.TRUE.equals(accumulateParameters().getDeactivated());
    }

    /** Returns the last log entry, or {@code null} if the log is empty. */
    public LogEntry getLastEntry() {
        return logEntries.isEmpty() ? null : logEntries.get(logEntries.size() - 1);
    }

    /**
     * Walk all entries and merge their parameter deltas to get the current effective
     * parameters, without performing cryptographic verification.
     */
    public Parameters accumulateParameters() {
        Parameters params = Parameters.defaults();
        for (LogEntry entry : logEntries) {
            if (entry.getParameters() != null) {
                params = params.merge(entry.getParameters());
            }
        }
        return params;
    }
}
