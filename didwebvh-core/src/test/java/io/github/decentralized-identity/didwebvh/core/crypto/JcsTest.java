package io.github.decentralizedidentity.didwebvh.core.crypto;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class JcsTest {

    @Test
    void canonicalizeSortsKeys() {
        String input = "{\"b\":2,\"a\":1}";
        String result = new String(Jcs.canonicalize(input), StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("{\"a\":1,\"b\":2}");
    }

    @Test
    void canonicalizeNestedObjects() {
        String input = "{\"z\":{\"b\":true,\"a\":false},\"a\":1}";
        String result = new String(Jcs.canonicalize(input), StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("{\"a\":1,\"z\":{\"a\":false,\"b\":true}}");
    }

    @Test
    void canonicalizeArrayPreservesOrder() {
        String input = "{\"a\":[3,1,2]}";
        String result = new String(Jcs.canonicalize(input), StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("{\"a\":[3,1,2]}");
    }

    @Test
    void canonicalizeJsonObject() {
        JsonObject obj = new JsonObject();
        obj.addProperty("b", 2);
        obj.addProperty("a", 1);
        String result = new String(Jcs.canonicalize(obj), StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("{\"a\":1,\"b\":2}");
    }

    @Test
    void canonicalizeRemovesWhitespace() {
        String input = "{ \"b\" : 2 , \"a\" : 1 }";
        String result = new String(Jcs.canonicalize(input), StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("{\"a\":1,\"b\":2}");
    }

    @Test
    void canonicalizeUnicode() {
        String input = "{\"key\":\"\\u20ac\"}";
        String result = new String(Jcs.canonicalize(input), StandardCharsets.UTF_8);
        assertThat(result).contains("\u20ac");
    }
}
