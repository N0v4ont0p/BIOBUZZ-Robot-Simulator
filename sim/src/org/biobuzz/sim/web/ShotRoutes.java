package org.biobuzz.sim.web;

import com.google.gson.JsonObject;

import org.biobuzz.sim.Sim;
import org.biobuzz.sim.phys.Shots;
import org.biobuzz.sim.phys.World;

import java.util.LinkedHashMap;
import java.util.Map;

// Launcher endpoints: every shot with what really happened (report card, shot camera), the coach's "what scores
// from here" solver, the shot-table check, and the true launcher servo positions.
//   GET /api/shots?since=<id>&samples=1      GET /api/shots/solve?side=0&distance=60
//   GET /api/shots/makeable?side=0           GET /api/launcher/guide
//   GET /api/shots/chance?side=0&distance=60&speed=1500&feed=0.5&n=24
//   GET /api/shots/band?side=0               (the distances a launcher scores from at its fixed launch angle)
public final class ShotRoutes {
    private static boolean registered;

    private ShotRoutes() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        Server.route("shots", (b, q) -> {
            int since = (int) num(b, q, "since", 0);
            boolean samples = num(b, q, "samples", 0) != 0;
            synchronized (World.LOCK) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("shots", shots().history(since, samples));
                return m;
            }
        });
        Server.route("shots/solve", (b, q) -> shots().solve((int) num(b, q, "side", 0), num(b, q, "distance", 60)));
        Server.route("shots/band", (b, q) -> shots().band((int) num(b, q, "side", 0)));
        Server.route("shots/makeable", (b, q) -> shots().makeable((int) num(b, q, "side", 0)));
        Server.route("shots/chance", (b, q) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("makeChance", shots().chance((int) num(b, q, "side", 0), num(b, q, "distance", 60), num(b, q, "speed", 0),
                    num(b, q, "feed", 0.5), (int) num(b, q, "n", 24)));
            return m;
        });
        Server.route("launcher/guide", (b, q) -> {
            synchronized (World.LOCK) {
                return shots().guide();
            }
        });
    }

    private static Shots shots() {
        Sim sim = Sim.get();
        World w = sim == null ? null : sim.world;
        if (w == null) throw new IllegalStateException("no robot loaded");
        return w.robot.shots;
    }

    private static double num(JsonObject b, Map<String, String> q, String k, double def) {
        if (b.has(k) && !b.get(k).isJsonNull()) return b.get(k).getAsDouble();
        String s = q.get(k);
        return s == null || s.isEmpty() ? def : Double.parseDouble(s);
    }
}
