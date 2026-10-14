package org.biobuzz.sim.truth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// The hidden robot of one scenario (sim/scenarios/*.json). Every physical number the setup programs measure lives
// here. A value is a number, {"min": a, "max": b} (picked once per scenario run) or {"value": v, "jitter": j}
// (v plus up to +/- j). The picked values are what the report card compares the students' results against.
public final class Truth {
    private final JsonObject root;
    private final Random rng;
    private final Map<String, Object> picked = Collections.synchronizedMap(new LinkedHashMap<>());
    public final String file;
    public final long seed;

    private Truth(JsonObject root, long seed, String file) {
        this.root = root;
        this.seed = seed;
        this.rng = new Random(mix(seed, "rng"));
        this.file = file;
    }

    // each value gets its own random stream from (seed, path): values do not depend on the order parts read them,
    // and neighbouring seeds give unrelated robots (java.util.Random's first draws for seeds 1, 2, 3 are alike)
    static long mix(long seed, String path) {
        long z = seed * 0x9E3779B97F4A7C15L + path.hashCode() * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public static Truth load(File f, long seed) throws IOException {
        try (InputStreamReader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            JsonElement e = new JsonParser().parse(r);
            return new Truth(e.getAsJsonObject(), seed, f.getName());
        }
    }

    public static Truth empty() {
        return new Truth(new JsonObject(), 1, "(none)");
    }

    public String name() {
        return str("name", file);
    }

    public String description() {
        return str("description", "");
    }

    private JsonElement find(String path) {
        JsonElement e = root;
        for (String part : path.split("\\.")) {
            if (e == null || !e.isJsonObject()) return null;
            e = e.getAsJsonObject().get(part);
        }
        return e;
    }

    public synchronized double num(String path, double def) {
        Object known = picked.get(path);
        if (known instanceof Double) return (Double) known;
        JsonElement e = find(path);
        double v = def;
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) {
            v = e.getAsDouble();
        } else if (e != null && e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("min") && o.has("max")) {
                double lo = o.get("min").getAsDouble(), hi = o.get("max").getAsDouble();
                v = lo + new Random(mix(seed, path)).nextDouble() * (hi - lo);
            } else if (o.has("value")) {
                double j = o.has("jitter") ? o.get("jitter").getAsDouble() : 0;
                v = o.get("value").getAsDouble() + (new Random(mix(seed, path)).nextDouble() * 2 - 1) * j;
            }
        }
        picked.put(path, v);
        return v;
    }

    // a hidden value between lo and hi unless the scenario sets it; its own random stream, so adding one never
    // changes any other hidden value of the same seed
    public synchronized double range(String path, double lo, double hi) {
        if (picked.get(path) instanceof Double || find(path) != null) return num(path, lo);
        double v = lo + new Random(mix(seed, path)).nextDouble() * (hi - lo);
        picked.put(path, v);
        return v;
    }

    public synchronized boolean bool(String path, boolean def) {
        Object known = picked.get(path);
        if (known instanceof Boolean) return (Boolean) known;
        JsonElement e = find(path);
        boolean v = def;
        if (e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean()) v = e.getAsBoolean();
        else if (e != null && e.isJsonObject() && e.getAsJsonObject().has("chance"))
            v = new Random(mix(seed, path)).nextDouble() < e.getAsJsonObject().get("chance").getAsDouble();
        picked.put(path, v);
        return v;
    }

    public synchronized String str(String path, String def) {
        JsonElement e = find(path);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
    }

    public JsonObject section(String path) {
        JsonElement e = find(path);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
    }

    public List<JsonObject> faults() {
        List<JsonObject> out = new ArrayList<>();
        JsonElement e = root.get("faults");
        if (e instanceof JsonArray) for (JsonElement f : (JsonArray) e) if (f.isJsonObject()) out.add(f.getAsJsonObject());
        return out;
    }

    public JsonObject fault(String type) {
        for (JsonObject f : faults()) if (type.equals(f.has("type") ? f.get("type").getAsString() : "")) return f;
        return null;
    }

    public Random random() {
        return rng;
    }

    // every value read so far: the hidden truth for the report card
    public Map<String, Object> picked() {
        synchronized (picked) {
            return new LinkedHashMap<>(picked);
        }
    }
}
