package org.biobuzz.sim.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

// JSON for the browser: NaN and infinities become null (the robot code is full of NaN until values are measured).
public final class Json {
    private static final TypeAdapter<Number> SAFE = new TypeAdapter<Number>() {
        @Override
        public void write(JsonWriter out, Number v) throws IOException {
            if (v == null || Double.isNaN(v.doubleValue()) || Double.isInfinite(v.doubleValue())) out.nullValue();
            else out.value(v);
        }

        @Override
        public Number read(JsonReader in) throws IOException {
            return in.nextDouble();
        }
    };
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Double.class, SAFE).registerTypeAdapter(double.class, SAFE)
            .registerTypeAdapter(Float.class, SAFE).registerTypeAdapter(float.class, SAFE)
            .disableHtmlEscaping().create();

    public static String write(Object o) {
        return GSON.toJson(o);
    }

    public static JsonObject parse(String s) {
        if (s == null || s.trim().isEmpty()) return new JsonObject();
        JsonElement e = new JsonParser().parse(s);
        return e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    public static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }
}
