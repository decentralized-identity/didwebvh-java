package io.github.decentralizedidentity.didwebvh.core.validate;

import io.github.decentralizedidentity.didwebvh.core.model.Parameters;

/** Result of a log chain validation run. */
public final class ValidationResult {

    private final boolean valid;
    private final int lastValidEntryIndex;
    private final String failureReason;
    private final int failedEntryIndex;
    private final Parameters activeParameters;

    private ValidationResult(boolean valid, int lastValidEntryIndex,
                              String failureReason, int failedEntryIndex,
                              Parameters activeParameters) {
        this.valid = valid;
        this.lastValidEntryIndex = lastValidEntryIndex;
        this.failureReason = failureReason;
        this.failedEntryIndex = failedEntryIndex;
        this.activeParameters = activeParameters;
    }

    public static ValidationResult success(int lastIndex, Parameters activeParameters) {
        return new ValidationResult(true, lastIndex, null, -1, activeParameters);
    }

    public static ValidationResult failure(int lastValidIndex, int failedIndex,
                                           String reason, Parameters activeParameters) {
        return new ValidationResult(false, lastValidIndex, reason, failedIndex, activeParameters);
    }

    public boolean isValid() {
        return valid;
    }

    public int getLastValidEntryIndex() {
        return lastValidEntryIndex;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getFailedEntryIndex() {
        return failedEntryIndex;
    }

    public Parameters getActiveParameters() {
        return activeParameters;
    }
}
