package io.github.decentralizedidentity.didwebvh.core.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Thin wrapper around a {@link JsonObject} representing a DID Document.
 * No validation is performed – this is purely a convenience container.
 */
public final class DidDocument {

    private final JsonObject json;

    public DidDocument(JsonObject json) {
        this.json = json == null ? new JsonObject() : json;
    }

    public JsonObject asJsonObject() {
        return json;
    }

    public String getId() {
        return stringOrNull("id");
    }

    public List<String> getController() {
        return stringOrArray("controller");
    }

    public List<String> getAlsoKnownAs() {
        return stringOrArray("alsoKnownAs");
    }

    public JsonArray getVerificationMethod() {
        return arrayOrNull("verificationMethod");
    }

    public JsonArray getService() {
        return arrayOrNull("service");
    }

    /** Returns a new DidDocument with the {@code id} field replaced. */
    public DidDocument withId(String id) {
        JsonObject copy = json.deepCopy();
        copy.addProperty("id", id);
        return new DidDocument(copy);
    }

    private String stringOrNull(String name) {
        JsonElement el = json.get(name);
        return (el == null || el.isJsonNull()) ? null : el.getAsString();
    }

    private JsonArray arrayOrNull(String name) {
        JsonElement el = json.get(name);
        return (el != null && el.isJsonArray()) ? el.getAsJsonArray() : null;
    }

    private List<String> stringOrArray(String name) {
        JsonElement el = json.get(name);
        if (el == null || el.isJsonNull()) {
            return null;
        }
        if (el.isJsonArray()) {
            List<String> out = new ArrayList<>();
            for (JsonElement item : el.getAsJsonArray()) {
                out.add(item.getAsString());
            }
            return Collections.unmodifiableList(out);
        }
        return Collections.singletonList(el.getAsString());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof DidDocument)) {
            return false;
        }
        return Objects.equals(json, ((DidDocument) o).json);
    }

    @Override
    public int hashCode() {
        return Objects.hash(json);
    }
}
