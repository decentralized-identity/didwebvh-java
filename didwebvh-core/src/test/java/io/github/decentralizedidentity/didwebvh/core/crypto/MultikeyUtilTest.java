package io.github.decentralizedidentity.didwebvh.core.crypto;

import io.github.decentralizedidentity.didwebvh.core.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MultikeyUtilTest {

    @Test
    void encodeDecodeEd25519RoundTrip() {
        byte[] publicKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            publicKey[i] = (byte) (i + 1);
        }
        String multikey = MultikeyUtil.encode("Ed25519", publicKey);
        assertThat(multikey).startsWith("z6Mk");

        byte[] decoded = MultikeyUtil.decode(multikey);
        assertThat(decoded).isEqualTo(publicKey);
    }

    @Test
    void keyTypeFromMultikey() {
        byte[] publicKey = new byte[32];
        String multikey = MultikeyUtil.encode("Ed25519", publicKey);
        assertThat(MultikeyUtil.keyTypeFromMultikey(multikey)).isEqualTo("Ed25519");
    }

    @Test
    void encodeUnsupportedKeyTypeThrows() {
        assertThatThrownBy(() -> MultikeyUtil.encode("P-256", new byte[32]))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unsupported key type");
    }

    @Test
    void decodeUnknownPrefixThrows() {
        byte[] data = new byte[]{0x00, 0x00, 0x01, 0x02};
        String encoded = Base58Btc.encodeMultibase(data);
        assertThatThrownBy(() -> MultikeyUtil.decode(encoded))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unknown multicodec prefix");
    }

    @Test
    void decodeTooShortThrows() {
        String encoded = Base58Btc.encodeMultibase(new byte[]{0x01});
        assertThatThrownBy(() -> MultikeyUtil.decode(encoded))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("too short");
    }
}
