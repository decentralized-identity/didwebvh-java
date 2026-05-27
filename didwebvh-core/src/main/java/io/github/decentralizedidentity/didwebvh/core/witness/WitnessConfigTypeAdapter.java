package io.github.decentralizedidentity.didwebvh.core.witness;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Round-trips the spec's {@code witness} parameter: an empty witness config
 * serialises as {@code {}} (matching Python/TS implementations), and a
 * configured one as {@code {"threshold":N,"witnesses":[...]}}. Reading
 * accepts either form, plus a missing field; writing emits the spec form so
 * canonicalised hashes (SCID, entry hash, proof) align across implementations.
 */
final class WitnessConfigTypeAdapter extends TypeAdapter<WitnessConfig> {

    @Override
    public void write(JsonWriter out, WitnessConfig value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        if (value.isActive()) {
            out.name("threshold").value(value.getThreshold());
            out.name("witnesses").beginArray();
            for (WitnessEntry entry : value.getWitnesses()) {
                out.beginObject();
                out.name("id").value(entry.getId());
                out.endObject();
            }
            out.endArray();
        }
        out.endObject();
    }

    @Override
    public WitnessConfig read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        in.beginObject();
        int threshold = 0;
        List<WitnessEntry> witnesses = new ArrayList<>();
        while (in.hasNext()) {
            String name = in.nextName();
            if ("threshold".equals(name)) {
                threshold = in.nextInt();
            } else if ("witnesses".equals(name)) {
                in.beginArray();
                while (in.hasNext()) {
                    in.beginObject();
                    String id = null;
                    while (in.hasNext()) {
                        String field = in.nextName();
                        if ("id".equals(field)) {
                            id = in.nextString();
                        } else {
                            in.skipValue();
                        }
                    }
                    in.endObject();
                    witnesses.add(new WitnessEntry(id));
                }
                in.endArray();
            } else {
                in.skipValue();
            }
        }
        in.endObject();
        return new WitnessConfig(threshold, witnesses);
    }
}
