package io.github.decentralizedidentity.didwebvh.core.validate;

/** Result of a witness proof validation run. */
public final class WitnessValidationResult {

    private final boolean valid;
    private final String failureReason;
    private final int failedEntryIndex;

    private WitnessValidationResult(boolean valid, String failureReason, int failedEntryIndex) {
        this.valid = valid;
        this.failureReason = failureReason;
        this.failedEntryIndex = failedEntryIndex;
    }

    public static WitnessValidationResult success() {
        return new WitnessValidationResult(true, null, -1);
    }

    public static WitnessValidationResult failure(int failedEntryIndex, String reason) {
        return new WitnessValidationResult(false, reason, failedEntryIndex);
    }

    public boolean isValid() {
        return valid;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public int getFailedEntryIndex() {
        return failedEntryIndex;
    }
}
