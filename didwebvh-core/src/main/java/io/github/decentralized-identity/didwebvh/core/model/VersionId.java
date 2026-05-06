package io.github.decentralizedidentity.didwebvh.core.model;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;

import java.util.Objects;

/**
 * A did:webvh version identifier of the form {@code "<versionNumber>-<entryHash>"}.
 *
 * <p>For the preliminary first entry of a new log, the raw value is the SCID itself
 * (no leading number and dash); use {@link #preliminary(String)} for that case.
 */
public final class VersionId {

    private final int versionNumber;
    private final String entryHash;
    private final boolean preliminary;

    private VersionId(int versionNumber, String entryHash, boolean preliminary) {
        this.versionNumber = versionNumber;
        this.entryHash = entryHash;
        this.preliminary = preliminary;
    }

    public static VersionId of(int versionNumber, String entryHash) {
        if (versionNumber < 1) {
            throw new ValidationException("versionNumber must be >= 1, was " + versionNumber);
        }
        if (entryHash == null || entryHash.isEmpty()) {
            throw new ValidationException("entryHash must be non-empty");
        }
        return new VersionId(versionNumber, entryHash, false);
    }

    /** Placeholder versionId used when computing the first entry hash; the raw value is the SCID. */
    public static VersionId preliminary(String scid) {
        if (scid == null || scid.isEmpty()) {
            throw new ValidationException("scid must be non-empty");
        }
        return new VersionId(0, scid, true);
    }

    public static VersionId parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            throw new ValidationException("versionId must be non-empty");
        }
        int dash = raw.indexOf('-');
        if (dash <= 0 || dash == raw.length() - 1) {
            throw new ValidationException("versionId must be of the form '<number>-<hash>': " + raw);
        }
        int number;
        try {
            number = Integer.parseInt(raw.substring(0, dash));
        } catch (NumberFormatException e) {
            throw new ValidationException("versionId has non-numeric version: " + raw, e);
        }
        return of(number, raw.substring(dash + 1));
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public boolean isPreliminary() {
        return preliminary;
    }

    @Override
    public String toString() {
        return preliminary ? entryHash : versionNumber + "-" + entryHash;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof VersionId)) {
            return false;
        }
        VersionId that = (VersionId) o;
        return versionNumber == that.versionNumber
                && preliminary == that.preliminary
                && Objects.equals(entryHash, that.entryHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionNumber, entryHash, preliminary);
    }
}
