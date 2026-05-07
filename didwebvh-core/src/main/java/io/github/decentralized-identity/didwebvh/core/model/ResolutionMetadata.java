package io.github.decentralizedidentity.didwebvh.core.model;

import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Metadata returned alongside a resolved DID Document. */
public final class ResolutionMetadata {

    private String versionId;
    private String versionTime;
    private String created;
    private String updated;
    private String scid;
    private Boolean portable;
    private Boolean deactivated;
    private String ttl;
    private WitnessConfig witness;
    private List<String> watchers;

    public ResolutionMetadata() {
        // empty
    }

    public String getVersionId() {
        return versionId;
    }

    public ResolutionMetadata setVersionId(String versionId) {
        this.versionId = versionId;
        return this;
    }

    public String getVersionTime() {
        return versionTime;
    }

    public ResolutionMetadata setVersionTime(String versionTime) {
        this.versionTime = versionTime;
        return this;
    }

    public String getCreated() {
        return created;
    }

    public ResolutionMetadata setCreated(String created) {
        this.created = created;
        return this;
    }

    public String getUpdated() {
        return updated;
    }

    public ResolutionMetadata setUpdated(String updated) {
        this.updated = updated;
        return this;
    }

    public String getScid() {
        return scid;
    }

    public ResolutionMetadata setScid(String scid) {
        this.scid = scid;
        return this;
    }

    public Boolean getPortable() {
        return portable;
    }

    public ResolutionMetadata setPortable(Boolean portable) {
        this.portable = portable;
        return this;
    }

    public Boolean getDeactivated() {
        return deactivated;
    }

    public ResolutionMetadata setDeactivated(Boolean deactivated) {
        this.deactivated = deactivated;
        return this;
    }

    public String getTtl() {
        return ttl;
    }

    public ResolutionMetadata setTtl(String ttl) {
        this.ttl = ttl;
        return this;
    }

    public WitnessConfig getWitness() {
        return witness;
    }

    public ResolutionMetadata setWitness(WitnessConfig witness) {
        this.witness = witness;
        return this;
    }

    public List<String> getWatchers() {
        return watchers;
    }

    public ResolutionMetadata setWatchers(List<String> watchers) {
        this.watchers = watchers == null ? null : Collections.unmodifiableList(new ArrayList<>(watchers));
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ResolutionMetadata)) {
            return false;
        }
        ResolutionMetadata that = (ResolutionMetadata) o;
        return Objects.equals(versionId, that.versionId)
                && Objects.equals(versionTime, that.versionTime)
                && Objects.equals(created, that.created)
                && Objects.equals(updated, that.updated)
                && Objects.equals(scid, that.scid)
                && Objects.equals(portable, that.portable)
                && Objects.equals(deactivated, that.deactivated)
                && Objects.equals(ttl, that.ttl)
                && Objects.equals(witness, that.witness)
                && Objects.equals(watchers, that.watchers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(versionId, versionTime, created, updated, scid,
                portable, deactivated, ttl, witness, watchers);
    }
}
