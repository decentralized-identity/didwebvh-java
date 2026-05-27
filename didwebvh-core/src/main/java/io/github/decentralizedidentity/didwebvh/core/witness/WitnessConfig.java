package io.github.decentralizedidentity.didwebvh.core.witness;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Witness configuration: a threshold and a list of witnesses. */
public final class WitnessConfig {

    private final int threshold;
    private final List<WitnessEntry> witnesses;

    public WitnessConfig(int threshold, List<WitnessEntry> witnesses) {
        this.threshold = threshold;
        this.witnesses = witnesses == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(witnesses);
    }

    /**
     * Return a config representing "no witnesses" ({@code witness: {}} in the spec).
     *
     * <p>Used as the default accumulated value for a chain that has never configured witnesses.
     * A config is considered active only if {@link #getWitnesses()} is non-empty.
     */
    public static WitnessConfig empty() {
        return new WitnessConfig(0, Collections.emptyList());
    }

    /** Return {@code true} if this config has at least one witness configured. */
    public boolean isActive() {
        return !witnesses.isEmpty();
    }

    public int getThreshold() {
        return threshold;
    }

    public List<WitnessEntry> getWitnesses() {
        return witnesses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WitnessConfig)) {
            return false;
        }
        WitnessConfig that = (WitnessConfig) o;
        return threshold == that.threshold && Objects.equals(witnesses, that.witnesses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(threshold, witnesses);
    }
}
