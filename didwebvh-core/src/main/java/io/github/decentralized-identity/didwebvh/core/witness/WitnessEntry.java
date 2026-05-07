package io.github.decentralizedidentity.didwebvh.core.witness;

import java.util.Objects;

/** A single witness identified by a did:key DID. */
public final class WitnessEntry {

    private final String id;

    public WitnessEntry(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WitnessEntry)) {
            return false;
        }
        return Objects.equals(id, ((WitnessEntry) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
