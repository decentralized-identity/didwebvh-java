package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.annotations.SerializedName;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * did:webvh log entry parameters (spec section 3.7.1).
 *
 * <p>All fields are optional; an entry with no parameter changes serializes to {@code {}}.
 * Use {@link #merge(Parameters)} to apply a later entry's changes onto the effective set.
 */
public final class Parameters {

    private String method;
    private String scid;
    @SerializedName("updateKeys")
    private List<String> updateKeys;
    @SerializedName("nextKeyHashes")
    private List<String> nextKeyHashes;
    private WitnessConfig witness;
    private List<String> watchers;
    private Boolean portable;
    private Boolean deactivated;
    private Integer ttl;

    public Parameters() {
        // empty
    }

    public String getMethod() {
        return method;
    }

    public Parameters setMethod(String method) {
        this.method = method;
        return this;
    }

    public String getScid() {
        return scid;
    }

    public Parameters setScid(String scid) {
        this.scid = scid;
        return this;
    }

    public List<String> getUpdateKeys() {
        return updateKeys;
    }

    public Parameters setUpdateKeys(List<String> updateKeys) {
        this.updateKeys = updateKeys == null ? null : Collections.unmodifiableList(new ArrayList<>(updateKeys));
        return this;
    }

    public List<String> getNextKeyHashes() {
        return nextKeyHashes;
    }

    public Parameters setNextKeyHashes(List<String> nextKeyHashes) {
        this.nextKeyHashes = nextKeyHashes == null
                ? null
                : Collections.unmodifiableList(new ArrayList<>(nextKeyHashes));
        return this;
    }

    public WitnessConfig getWitness() {
        return witness;
    }

    public Parameters setWitness(WitnessConfig witness) {
        this.witness = witness;
        return this;
    }

    public List<String> getWatchers() {
        return watchers;
    }

    public Parameters setWatchers(List<String> watchers) {
        this.watchers = watchers == null ? null : Collections.unmodifiableList(new ArrayList<>(watchers));
        return this;
    }

    public Boolean getPortable() {
        return portable;
    }

    public Parameters setPortable(Boolean portable) {
        this.portable = portable;
        return this;
    }

    public Boolean getDeactivated() {
        return deactivated;
    }

    public Parameters setDeactivated(Boolean deactivated) {
        this.deactivated = deactivated;
        return this;
    }

    public Integer getTtl() {
        return ttl;
    }

    public Parameters setTtl(Integer ttl) {
        this.ttl = ttl;
        return this;
    }

    /**
     * Return a new {@link Parameters} pre-populated with spec-defined defaults (section 3.7.1).
     *
     * <ul>
     *   <li>{@code ttl} = 3600</li>
     *   <li>{@code portable} = false</li>
     *   <li>{@code deactivated} = false</li>
     *   <li>{@code witness} = {@link WitnessConfig#empty()} (i.e. {@code {}}, no witnesses)</li>
     *   <li>{@code watchers} = {@code []} (empty list, no watchers)</li>
     * </ul>
     *
     * <p>Use this as the starting accumulator in a log-chain validation run so that
     * optional parameters resolve to their correct spec defaults rather than {@code null}.
     * Note: {@code ttl = 0} is a valid explicit value meaning "do not cache"; it is distinct
     * from an unset TTL.
     */
    public static Parameters defaults() {
        return new Parameters()
                .setTtl(3600)
                .setPortable(Boolean.FALSE)
                .setDeactivated(Boolean.FALSE)
                .setWitness(WitnessConfig.empty())
                .setWatchers(Collections.emptyList());
    }

    /**
     * Apply {@code other}'s non-null fields on top of this instance, returning a new object.
     * {@code this} and {@code other} are left unmodified.
     */
    public Parameters merge(Parameters other) {
        Parameters out = new Parameters()
                .setMethod(this.method)
                .setScid(this.scid)
                .setUpdateKeys(this.updateKeys)
                .setNextKeyHashes(this.nextKeyHashes)
                .setWitness(this.witness)
                .setWatchers(this.watchers)
                .setPortable(this.portable)
                .setDeactivated(this.deactivated)
                .setTtl(this.ttl);
        if (other == null) {
            return out;
        }
        if (other.method != null) {
            out.method = other.method;
        }
        if (other.scid != null) {
            out.scid = other.scid;
        }
        if (other.updateKeys != null) {
            out.updateKeys = other.updateKeys;
        }
        if (other.nextKeyHashes != null) {
            out.nextKeyHashes = other.nextKeyHashes;
        }
        if (other.witness != null) {
            out.witness = other.witness;
        }
        if (other.watchers != null) {
            out.watchers = other.watchers;
        }
        if (other.portable != null) {
            out.portable = other.portable;
        }
        if (other.deactivated != null) {
            out.deactivated = other.deactivated;
        }
        if (other.ttl != null) {
            out.ttl = other.ttl;
        }
        return out;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Parameters)) {
            return false;
        }
        Parameters that = (Parameters) o;
        return Objects.equals(method, that.method)
                && Objects.equals(scid, that.scid)
                && Objects.equals(updateKeys, that.updateKeys)
                && Objects.equals(nextKeyHashes, that.nextKeyHashes)
                && Objects.equals(witness, that.witness)
                && Objects.equals(watchers, that.watchers)
                && Objects.equals(portable, that.portable)
                && Objects.equals(deactivated, that.deactivated)
                && Objects.equals(ttl, that.ttl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, scid, updateKeys, nextKeyHashes, witness, watchers, portable, deactivated, ttl);
    }
}
