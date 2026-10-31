package io.github.decentralizedidentity.didwebvh.core.resolve;

/** Options that select a specific DID log version during resolution. */
public final class ResolveOptions {

    private final String versionId;
    private final String versionTime;
    private final Integer versionNumber;
    private final WitnessFetchMode witnessFetchMode;
    private final boolean didScidIds;

    private ResolveOptions(Builder builder) {
        this.versionId = builder.versionId;
        this.versionTime = builder.versionTime;
        this.versionNumber = builder.versionNumber;
        this.witnessFetchMode = builder.witnessFetchMode;
        this.didScidIds = builder.didScidIds;
    }

    /** Returns a new builder for {@link ResolveOptions}. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a {@link ResolveOptions} with no version selector and proactive witness fetch. */
    public static ResolveOptions defaults() {
        return builder().build();
    }

    /** Returns the versionId selector, or {@code null} if not set. */
    public String getVersionId() {
        return versionId;
    }

    /** Returns the versionTime selector, or {@code null} if not set. */
    public String getVersionTime() {
        return versionTime;
    }

    /** Returns the versionNumber selector, or {@code null} if not set. */
    public Integer getVersionNumber() {
        return versionNumber;
    }

    /**
     * Returns whether the log's entries name the DID as {@code did:scid:vh:1:<SCID>} rather than
     * as a did:webvh (see {@link Builder#didScidIds(boolean)}).
     */
    public boolean isDidScidIds() {
        return didScidIds;
    }

    /** Returns the witness fetch mode. */
    public WitnessFetchMode getWitnessFetchMode() {
        return witnessFetchMode;
    }

    ResolveOptions withFallbacks(ResolveOptions fallback) {
        if (fallback == null) {
            return this;
        }
        return builder()
                .versionId(versionId != null ? versionId : fallback.versionId)
                .versionTime(versionTime != null ? versionTime : fallback.versionTime)
                .versionNumber(versionNumber != null ? versionNumber : fallback.versionNumber)
                .witnessFetchMode(witnessFetchMode)
                .didScidIds(didScidIds)
                .build();
    }

    boolean hasMultipleVersionSelectors() {
        int count = 0;
        if (versionId != null) {
            count++;
        }
        if (versionTime != null) {
            count++;
        }
        if (versionNumber != null) {
            count++;
        }
        return count > 1;
    }

    /** Controls when HTTP resolution retrieves {@code did-witness.json}. */
    public enum WitnessFetchMode {
        /** Fetch witness proofs opportunistically and ignore 404 unless the log requires them. */
        PROACTIVE,
        /** Fetch witness proofs only after the log is parsed and witness parameters are active. */
        WHEN_REQUIRED
    }

    /** Builder for {@link ResolveOptions}. */
    public static final class Builder {
        private String versionId;
        private String versionTime;
        private Integer versionNumber;
        private WitnessFetchMode witnessFetchMode = WitnessFetchMode.PROACTIVE;
        private boolean didScidIds;

        private Builder() {
        }

        /** Selects a specific DID log entry by its full versionId string. */
        public Builder versionId(String versionId) {
            this.versionId = versionId;
            return this;
        }

        /** Selects the log entry that was active at the given ISO 8601 UTC instant. */
        public Builder versionTime(String versionTime) {
            this.versionTime = versionTime;
            return this;
        }

        /** Selects a specific DID log entry by its version number. */
        public Builder versionNumber(Integer versionNumber) {
            this.versionNumber = versionNumber;
            return this;
        }

        /** Sets how witness proofs are fetched during HTTP resolution. */
        public Builder witnessFetchMode(WitnessFetchMode witnessFetchMode) {
            this.witnessFetchMode = witnessFetchMode == null
                    ? WitnessFetchMode.PROACTIVE : witnessFetchMode;
            return this;
        }

        /**
         * Resolves a log whose entries name the DID as {@code did:scid:vh:1:<SCID>}, the did:scid
         * vh:1 form, rather than as a did:webvh: such a log is checked by the same rules, except that
         * every entry's {@code state.id} must be exactly {@code did:scid:vh:1:<scid>} (no host, path,
         * query or fragment), and no implicit {@code #files} or {@code #whois} service is added, since
         * the DID names no host. For resolving from a log (for example
         * {@link DidResolver#resolveFromLog(String, String, ResolveOptions)}), where a did:scid's
         * {@code src} gives the log.
         */
        public Builder didScidIds(boolean didScidIds) {
            this.didScidIds = didScidIds;
            return this;
        }

        /** Builds and returns the {@link ResolveOptions}. */
        public ResolveOptions build() {
            return new ResolveOptions(this);
        }
    }
}
