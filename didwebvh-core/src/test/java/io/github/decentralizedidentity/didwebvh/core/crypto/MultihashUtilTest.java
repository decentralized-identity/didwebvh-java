package io.github.decentralizedidentity.didwebvh.core.crypto;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultihashUtilTest {

    @Test
    void hashAndEncodeProducesValidMultihash() {
        byte[] data = "hello".getBytes(StandardCharsets.UTF_8);
        byte[] multihash = MultihashUtil.hashAndEncode(data);

        assertThat(multihash[0] & 0xFF).isEqualTo(MultihashUtil.SHA2_256_CODE);
        assertThat(multihash[1] & 0xFF).isEqualTo(MultihashUtil.SHA2_256_LENGTH);
        assertThat(multihash).hasSize(2 + 32);
    }

    @Test
    void extractAlgorithm() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        byte[] multihash = MultihashUtil.hashAndEncode(data);
        assertThat(MultihashUtil.extractAlgorithm(multihash)).isEqualTo(MultihashUtil.SHA2_256_CODE);
    }

    @Test
    void extractDigestMatchesSha256() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        byte[] multihash = MultihashUtil.hashAndEncode(data);
        byte[] digest = MultihashUtil.extractDigest(multihash);

        byte[] expectedDigest = MultihashUtil.sha256(data);
        assertThat(digest).isEqualTo(expectedDigest);
    }

    @Test
    void encodeAndExtractRoundTrip() {
        byte[] digest = new byte[32];
        for (int i = 0; i < 32; i++) {
            digest[i] = (byte) i;
        }
        byte[] multihash = MultihashUtil.encode(MultihashUtil.SHA2_256_CODE, digest);
        assertThat(MultihashUtil.extractAlgorithm(multihash)).isEqualTo(MultihashUtil.SHA2_256_CODE);
        assertThat(MultihashUtil.extractDigest(multihash)).isEqualTo(digest);
    }

    @Test
    void extractFromTooShortThrows() {
        assertThatThrownBy(() -> MultihashUtil.extractAlgorithm(new byte[]{0x12}))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> MultihashUtil.extractDigest(new byte[]{0x12}))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void extractDigestLengthMismatchThrows() {
        byte[] bad = new byte[]{0x12, 0x20, 0x01};
        assertThatThrownBy(() -> MultihashUtil.extractDigest(bad))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("mismatch");
    }
}
