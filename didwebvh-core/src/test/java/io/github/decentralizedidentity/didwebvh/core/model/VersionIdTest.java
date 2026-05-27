package io.github.decentralizedidentity.didwebvh.core.model;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VersionIdTest {

    @Test
    void parsesNumberAndHash() {
        VersionId v = VersionId.parse("1-QmHash");
        assertThat(v.getVersionNumber()).isEqualTo(1);
        assertThat(v.getEntryHash()).isEqualTo("QmHash");
        assertThat(v.toString()).isEqualTo("1-QmHash");
        assertThat(v.isPreliminary()).isFalse();
    }

    @Test
    void parsesMultiDigitNumber() {
        VersionId v = VersionId.parse("42-abc-def");
        assertThat(v.getVersionNumber()).isEqualTo(42);
        assertThat(v.getEntryHash()).isEqualTo("abc-def");
    }

    @Test
    void rejectsInvalid() {
        assertThatThrownBy(() -> VersionId.parse(null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VersionId.parse("")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VersionId.parse("nohash")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VersionId.parse("-hash")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VersionId.parse("1-")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VersionId.parse("x-hash")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VersionId.of(0, "h")).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> VersionId.of(1, "")).isInstanceOf(ValidationException.class);
    }

    @Test
    void preliminaryUsesScidAsRawString() {
        VersionId v = VersionId.preliminary("QmSCID");
        assertThat(v.isPreliminary()).isTrue();
        assertThat(v.toString()).isEqualTo("QmSCID");
        assertThat(v.getEntryHash()).isEqualTo("QmSCID");
    }

    @Test
    void equalsAndHashCode() {
        assertThat(VersionId.of(1, "h")).isEqualTo(VersionId.of(1, "h"));
        assertThat(VersionId.of(1, "h")).isNotEqualTo(VersionId.of(2, "h"));
        assertThat(VersionId.of(1, "h").hashCode()).isEqualTo(VersionId.of(1, "h").hashCode());
    }
}
