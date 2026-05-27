package io.github.decentralizedidentity.didwebvh.core.create;

import io.github.decentralizedidentity.didwebvh.core.model.LogEntry;

/** The result of a DID creation operation. */
public final class CreateDidResult {

    private final String did;
    private final LogEntry logEntry;
    private final String logLine;

    CreateDidResult(String did, LogEntry logEntry, String logLine) {
        this.did = did;
        this.logEntry = logEntry;
        this.logLine = logLine;
    }

    /** The full DID string, e.g. {@code did:webvh:<SCID>:example.com}. */
    public String getDid() {
        return did;
    }

    /** The first log entry. */
    public LogEntry getLogEntry() {
        return logEntry;
    }

    /** The compact JSONL line for {@code did.jsonl}. */
    public String getLogLine() {
        return logLine;
    }
}
