package io.github.decentralizedidentity.didwebvh.core.witness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Collection of witness proof entries, matching the {@code did-witness.json} file layout. */
public final class WitnessProofCollection {

    private List<WitnessProofEntry> entries;

    public WitnessProofCollection() {
        // empty
    }

    public WitnessProofCollection(List<WitnessProofEntry> entries) {
        this.entries = entries == null ? null : Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<WitnessProofEntry> getEntries() {
        return entries == null ? Collections.emptyList() : entries;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WitnessProofCollection)) {
            return false;
        }
        return Objects.equals(entries, ((WitnessProofCollection) o).entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entries);
    }
}
