package io.github.decentralizedidentity.didwebvh.core.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Base58BtcTest {

    @Test
    void roundTrip() {
        byte[] data = "Hello, World!".getBytes(StandardCharsets.UTF_8);
        String encoded = Base58Btc.encode(data);
        byte[] decoded = Base58Btc.decode(encoded);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void knownVector() {
        byte[] data = new byte[]{0x00, 0x01, 0x02};
        String encoded = Base58Btc.encode(data);
        assertThat(Base58Btc.decode(encoded)).isEqualTo(data);
    }

    @Test
    void multibaseRoundTrip() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        String multibase = Base58Btc.encodeMultibase(data);
        assertThat(multibase).startsWith("z");
        byte[] decoded = Base58Btc.decodeMultibase(multibase);
        assertThat(decoded).isEqualTo(data);
    }

    @Test
    void decodeMultibaseRejectsWrongPrefix() {
        assertThatThrownBy(() -> Base58Btc.decodeMultibase("m1234"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected multibase base58btc prefix 'z'");
    }

    @Test
    void decodeMultibaseRejectsEmpty() {
        assertThatThrownBy(() -> Base58Btc.decodeMultibase(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
