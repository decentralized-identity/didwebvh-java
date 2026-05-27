package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.Gson;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig;
import io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class ParametersTest {

    private final Gson gson = JsonSupport.compact();

    @Test
    void emptyParametersRoundTripToEmptyObject() {
        Parameters p = new Parameters();
        String json = gson.toJson(p);
        assertThat(json).isEqualTo("{}");
        assertThat(gson.fromJson(json, Parameters.class)).isEqualTo(p);
    }

    @Test
    void fullParametersRoundTrip() {
        WitnessConfig wc = new WitnessConfig(2, Arrays.asList(
                new WitnessEntry("did:key:z6Mk1"),
                new WitnessEntry("did:key:z6Mk2")));

        Parameters p = new Parameters()
                .setMethod("did:webvh:1.0")
                .setScid("QmSCID")
                .setUpdateKeys(Arrays.asList("z6MkA", "z6MkB"))
                .setNextKeyHashes(Collections.singletonList("Qm1"))
                .setWitness(wc)
                .setWatchers(Collections.singletonList("https://watcher.example/"))
                .setPortable(Boolean.TRUE)
                .setDeactivated(Boolean.FALSE)
                .setTtl(3600);

        String json = gson.toJson(p);
        Parameters decoded = gson.fromJson(json, Parameters.class);
        assertThat(decoded).isEqualTo(p);
    }

    @Test
    void mergeAppliesNonNullFieldsFromOther() {
        Parameters base = new Parameters()
                .setMethod("did:webvh:1.0")
                .setScid("QmSCID")
                .setUpdateKeys(Collections.singletonList("z6MkA"))
                .setPortable(Boolean.TRUE);

        Parameters delta = new Parameters()
                .setUpdateKeys(Collections.singletonList("z6MkZ"))
                .setDeactivated(Boolean.TRUE);

        Parameters merged = base.merge(delta);

        assertThat(merged.getMethod()).isEqualTo("did:webvh:1.0");
        assertThat(merged.getScid()).isEqualTo("QmSCID");
        assertThat(merged.getUpdateKeys()).containsExactly("z6MkZ");
        assertThat(merged.getPortable()).isTrue();
        assertThat(merged.getDeactivated()).isTrue();
        assertThat(base.getDeactivated()).isNull();
        assertThat(base.getUpdateKeys()).containsExactly("z6MkA");
    }

    @Test
    void mergeWithNullReturnsCopyOfBase() {
        Parameters base = new Parameters().setMethod("did:webvh:1.0");
        Parameters merged = base.merge(null);
        assertThat(merged).isEqualTo(base);
        assertThat(merged).isNotSameAs(base);
    }

    @Test
    void defaultsHaveExpectedValues() {
        Parameters d = Parameters.defaults();
        assertThat(d.getTtl()).isEqualTo(3600);
        assertThat(d.getPortable()).isFalse();
        assertThat(d.getDeactivated()).isFalse();
        assertThat(d.getWitness()).isNotNull();
        assertThat(d.getWitness().isActive()).isFalse();
        assertThat(d.getWatchers()).isNotNull().isEmpty();
    }

    @Test
    void defaultsOverriddenByMerge() {
        Parameters accumulated = Parameters.defaults()
                .merge(new Parameters().setTtl(7200));
        assertThat(accumulated.getTtl()).isEqualTo(7200);
    }

    @Test
    void defaultsPreservedWhenEntryOmitsTtl() {
        Parameters accumulated = Parameters.defaults()
                .merge(new Parameters().setMethod("did:webvh:1.0"));
        assertThat(accumulated.getTtl()).isEqualTo(3600);
    }

    @Test
    void zeroTtlDistinctFromDefault() {
        // ttl=0 means "do not cache" — must be stored as 0, not null or 3600
        Parameters accumulated = Parameters.defaults()
                .merge(new Parameters().setTtl(0));
        assertThat(accumulated.getTtl()).isEqualTo(0);
    }

    @Test
    void witnessCanBeClearedToEmpty() {
        io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig active =
                new io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig(1,
                        java.util.Collections.singletonList(
                                new io.github.decentralizedidentity.didwebvh.core.witness.WitnessEntry("did:key:z6Mk1")));
        Parameters withWitness = Parameters.defaults()
                .merge(new Parameters().setWitness(active));
        assertThat(withWitness.getWitness().isActive()).isTrue();

        // Entry sets witness = {} to turn off
        Parameters cleared = withWitness.merge(
                new Parameters().setWitness(
                        io.github.decentralizedidentity.didwebvh.core.witness.WitnessConfig.empty()));
        assertThat(cleared.getWitness().isActive()).isFalse();
    }

    @Test
    void watchersCanBeClearedToEmpty() {
        Parameters withWatchers = Parameters.defaults()
                .merge(new Parameters().setWatchers(
                        Collections.singletonList("https://watcher.example.com")));
        assertThat(withWatchers.getWatchers()).hasSize(1);

        // Entry sets watchers = [] to turn off
        Parameters cleared = withWatchers.merge(
                new Parameters().setWatchers(Collections.emptyList()));
        assertThat(cleared.getWatchers()).isEmpty();
    }
}
