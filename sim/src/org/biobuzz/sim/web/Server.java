package org.biobuzz.sim.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qualcomm.robotcore.hardware.configuration.ConfigurationType;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.biobuzz.sim.Sim;
import org.biobuzz.sim.loop.LoopRoutes;
import org.biobuzz.sim.phys.World;
import org.biobuzz.sim.rc.Devices;
import org.biobuzz.sim.rc.RobotConfig;
import org.biobuzz.sim.rc.RobotController;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

// The simulator's web server (JDK built-in): the Driver Station + Workbench page, a server-sent event stream of
// everything that changes, and small JSON endpoints for button presses and hand actions.
public final class Server {
    public interface Handler {
        Object handle(JsonObject body, Map<String, String> query) throws Exception;
    }

    private static final Map<String, Handler> ROUTES = new LinkedHashMap<>();
    private final Sim sim;
    private final File web;
    private HttpServer http;

    public Server(Sim sim, File web) {
        this.sim = sim;
        this.web = web;
        routes();
    }

    // subsystems with their own endpoints register them here (path under /api/)
    public static void route(String path, Handler h) {
        ROUTES.put(path, h);
    }

    public void start(int port) throws IOException {
        http = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 64);
        http.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "web");
            t.setDaemon(true);
            return t;
        }));
        http.createContext("/", this::handle);
        http.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        try {
            if (path.equals("/api/stream")) {
                stream(ex);
            } else if (path.startsWith("/api/")) {
                Handler h = ROUTES.get(path.substring(5));
                if (h == null) {
                    send(ex, 404, "text/plain", "no such endpoint");
                    return;
                }
                String body = new String(readAll(ex.getRequestBody()), StandardCharsets.UTF_8);
                Object result = h.handle(Json.parse(body), query(ex));
                send(ex, 200, "application/json", Json.write(result == null ? ok() : result));
            } else {
                serveFile(ex, path);
            }
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("error", e.getMessage() == null ? e.toString() : e.getMessage());
            send(ex, 500, "application/json", Json.write(err));
        }
    }

    private static Map<String, Object> ok() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", true);
        return m;
    }

    private void serveFile(HttpExchange ex, String path) throws IOException {
        if (path.equals("/")) path = "/index.html";
        File f = new File(web, path).getCanonicalFile();
        if (!f.getPath().startsWith(web.getCanonicalPath()) || !f.isFile()) {
            send(ex, 404, "text/plain", "not found");
            return;
        }
        String n = f.getName();
        String type = n.endsWith(".html") ? "text/html; charset=utf-8" : n.endsWith(".js") ? "text/javascript; charset=utf-8"
                : n.endsWith(".css") ? "text/css; charset=utf-8" : n.endsWith(".svg") ? "image/svg+xml"
                : n.endsWith(".png") ? "image/png" : n.endsWith(".json") ? "application/json" : "application/octet-stream";
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] b = Files.readAllBytes(f.toPath());
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }

    // 20 updates a second: Driver Station state (incl. telemetry, events), physical world, simulator status
    private void stream(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, 0);
        long lastEvent = Long.parseLong(query(ex).getOrDefault("since", "0"));
        try (OutputStream o = ex.getResponseBody()) {
            while (true) {
                String frame;
                try {
                    Map<String, Object> s = new LinkedHashMap<>();
                    Map<String, Object> ds = sim.ds.state(lastEvent);
                    long seen = lastEvent;
                    for (Object e : (List<?>) ds.get("events")) seen = Math.max(seen, (Long) ((Map<?, ?>) e).get("seq"));
                    RobotController rc = sim.rc;
                    ds.put("battery", rc == null ? null : rc.batteryVolts());
                    s.put("ds", ds);
                    World w = sim.world;
                    s.put("world", w == null ? null : w.snapshot());
                    s.put("sim", simState());
                    frame = Json.write(s);
                    lastEvent = seen;
                } catch (RuntimeException e) {
                    // a frame built mid-restart can fail; the next one will not
                    Thread.sleep(50);
                    continue;
                }
                o.write(("data: " + frame + "\n\n").getBytes(StandardCharsets.UTF_8));
                o.flush();
                Thread.sleep(50);
            }
        } catch (IOException | InterruptedException ignored) {
            // browser went away
        }
    }

    // the blue start pose of every AutoTool auto in the robot code (red = the same rotated 180 deg about mid-field):
    // the Field tab's place buttons put the robot exactly where that auto's Pinpoint starts
    private static final java.util.regex.Pattern AUTO_NAME = java.util.regex.Pattern.compile("@Autonomous\\(name = \"Blue_([^\"]+)\"");
    private static final java.util.regex.Pattern AUTO_START = java.util.regex.Pattern.compile(
            "Pose start = [^;]*poseFactory\\.of\\(\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*,\\s*(-?[\\d.]+)\\s*\\)");

    private List<Map<String, Object>> autoStarts() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        File[] files = new File(sim.ws.teamCodeSource(), "org/firstinspires/ftc/teamcode/opmodes").listFiles((d, n) -> n.endsWith(".java"));
        if (files == null) return out;
        Arrays.sort(files);
        for (File f : files) {
            String src = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            java.util.regex.Matcher name = AUTO_NAME.matcher(src), start = AUTO_START.matcher(src);
            if (!name.find() || !start.find()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name.group(1));
            m.put("x", Double.parseDouble(start.group(1)));
            m.put("y", Double.parseDouble(start.group(2)));
            m.put("headingDeg", Double.parseDouble(start.group(3)));
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> simState() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scenario", sim.scenario);
        m.put("seed", sim.seed);
        m.put("activeConfig", sim.activeConfigName());
        m.put("testMode", sim.testMode());
        m.put("buildError", sim.buildError);
        m.put("log", sim.log());
        return m;
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> q = new LinkedHashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return q;
        for (String kv : raw.split("&")) {
            int i = kv.indexOf('=');
            try {
                if (i < 0) q.put(URLDecoder.decode(kv, "UTF-8"), "");
                else q.put(URLDecoder.decode(kv.substring(0, i), "UTF-8"), URLDecoder.decode(kv.substring(i + 1), "UTF-8"));
            } catch (java.io.UnsupportedEncodingException ignored) {
            }
        }
        return q;
    }

    private static byte[] readAll(InputStream in) throws IOException {
        return in.readAllBytes();
    }

    private static void send(HttpExchange ex, int code, String type, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) {
            o.write(b);
        }
    }

    private RobotController rc() {
        RobotController r = sim.rc;
        if (r == null) throw new IllegalStateException("Robot Controller is not running");
        return r;
    }

    private void routes() {
        LoopRoutes.register(sim);
        route("views", (b, q) -> {
            File[] fs = new File(web, "views").listFiles((d, n) -> n.endsWith(".js"));
            List<String> out = new ArrayList<>();
            if (fs != null) {
                Arrays.sort(fs);
                for (File f : fs) out.add("views/" + f.getName());
            }
            return out;
        });
        route("gamepad", (b, q) -> {
            RobotController r = sim.rc;
            if (r != null) r.gamepads(obj(b, "user1"), obj(b, "user2"));
            return null;
        });
        route("ds/select", (b, q) -> {
            sim.ds.select(Json.str(b, "name", null));
            return null;
        });
        route("ds/init", (b, q) -> {
            sim.ds.init();
            return null;
        });
        route("ds/start", (b, q) -> {
            sim.ds.start();
            return null;
        });
        route("ds/stop", (b, q) -> {
            sim.ds.stop();
            return null;
        });
        route("ds/autoTimer", (b, q) -> {
            sim.ds.autoTimer = b.has("on") && b.get("on").getAsBoolean();
            return null;
        });
        route("robot/restart", (b, q) -> {
            sim.restartRobot();
            return null;
        });
        route("build", (b, q) -> {
            sim.build();
            return null;
        });
        route("hand", (b, q) -> {
            World w = sim.world;
            boolean done = w != null && w.hand(Json.str(b, "part", ""), Json.str(b, "action", ""), obj(b, "args") == null ? new JsonObject() : obj(b, "args"));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("ok", done);
            return m;
        });
        route("scenarios", (b, q) -> sim.scenarios());
        route("scenario", (b, q) -> {
            long seed = b.has("seed") && !b.get("seed").isJsonNull() ? b.get("seed").getAsLong() : System.currentTimeMillis() % 100000;
            sim.loadScenario(Json.str(b, "file", "A-perfect-robot.json"), seed, !b.has("fresh") || b.get("fresh").getAsBoolean());
            return null;
        });
        route("checkpoints", (b, q) -> sim.checkpoints());
        route("autos/starts", (b, q) -> autoStarts());
        route("testmode/start", (b, q) -> {
            sim.startTestMode();
            return null;
        });
        route("testmode/end", (b, q) -> {
            sim.endTestMode();
            return null;
        });
        route("checkpoint/save", (b, q) -> {
            sim.checkpoint(Json.str(b, "label", "saved by hand"));
            return null;
        });
        route("checkpoint/restore", (b, q) -> {
            sim.restore(Json.str(b, "name", ""));
            return null;
        });
        route("log", (b, q) -> sim.log());
        route("expected", (b, q) -> sim.world.expected());
        // programmer step on the sandbox: values into team-config.yaml (or the code settings), render, build, install
        route("sandbox/set", (b, q) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("render", sim.setTuning(Json.write(obj(b, "values") == null ? new JsonObject() : obj(b, "values"))));
            if (!b.has("build") || b.get("build").getAsBoolean()) sim.build();
            return m;
        });
        route("sandbox/update", (b, q) -> Json.parse(sim.updateSandbox().trim().replaceAll("(?s)^.*?(\\{\"kept\")", "$1")));
        route("truth", (b, q) -> sim.world.truth.picked());
        route("config", (b, q) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("active", sim.activeConfigName());
            m.put("names", sim.configNames());
            Map<String, Object> types = new LinkedHashMap<>();
            types.put("motor", Devices.list(ConfigurationType.DeviceFlavor.MOTOR));
            types.put("servo", Devices.list(ConfigurationType.DeviceFlavor.SERVO));
            types.put("i2c", Devices.list(ConfigurationType.DeviceFlavor.I2C));
            types.put("digital", Devices.list(ConfigurationType.DeviceFlavor.DIGITAL_IO));
            m.put("types", types);
            return m;
        });
        route("config/get", (b, q) -> sim.readConfig(q.get("name")));
        // coach shortcut: the configuration the setup guide asks for, on the default wiring
        route("config/preset", (b, q) -> {
            RobotConfig c = sim.presetConfig(Json.str(b, "name", "BIOBUZZ"));
            c.write(sim.configFile(c.name));
            return null;
        });
        route("config/scan", (b, q) -> sim.scanned(Json.str(b, "name", "NewConfig")));
        route("config/save", (b, q) -> {
            RobotConfig c = configFromJson(b);
            if (!c.name.matches("[A-Za-z0-9 _-]+")) throw new IllegalArgumentException("Use letters, numbers, spaces, - or _ in the name");
            c.write(sim.configFile(c.name));
            sim.note("configuration " + c.name + " saved");
            return null;
        });
        route("config/activate", (b, q) -> {
            String n = Json.str(b, "name", null);
            if (n == null || !sim.configFile(n).isFile()) throw new IllegalArgumentException("no configuration " + n);
            sim.setActiveConfig(n);
            sim.note("configuration " + n + " activated: restarting the robot");
            sim.restartRobot();
            sim.checkpoint("configuration " + n);
            return null;
        });
        route("config/delete", (b, q) -> {
            String n = Json.str(b, "name", "");
            Files.deleteIfExists(sim.configFile(n).toPath());
            return null;
        });
        route("files", (b, q) -> {
            File dir = new File(sim.ws.sdcard, "FIRST");
            List<Map<String, Object>> out = new ArrayList<>();
            File[] fs = dir.listFiles();
            if (fs != null) {
                Arrays.sort(fs);
                for (File f : fs) {
                    if (f.isDirectory() || f.getName().startsWith(".")) continue;
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", f.getName());
                    m.put("bytes", f.length());
                    m.put("modified", f.lastModified());
                    out.add(m);
                }
            }
            return out;
        });
        route("file", (b, q) -> {
            String n = q.getOrDefault("name", "");
            if (n.contains("/") || n.contains("..")) throw new IllegalArgumentException("bad name");
            File f = new File(sim.ws.sdcard, "FIRST/" + n);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", n);
            m.put("text", f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null);
            return m;
        });
    }

    private static JsonObject obj(JsonObject b, String k) {
        JsonElement e = b.get(k);
        return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
    }

    private static RobotConfig configFromJson(JsonObject b) {
        RobotConfig c = new RobotConfig(Json.str(b, "name", "").trim());
        JsonArray mods = b.has("modules") ? b.getAsJsonArray("modules") : new JsonArray();
        for (JsonElement me : mods) {
            JsonObject mo = me.getAsJsonObject();
            RobotConfig.Module m = new RobotConfig.Module(Json.str(mo, "name", "Hub"), mo.get("address").getAsInt());
            addDevices(mo, "motors", m.motors);
            addDevices(mo, "servos", m.servos);
            addDevices(mo, "i2c", m.i2c);
            addDevices(mo, "digital", m.digital);
            c.modules.add(m);
        }
        return c;
    }

    private static void addDevices(JsonObject mo, String key, List<RobotConfig.Device> out) {
        if (!mo.has(key)) return;
        for (JsonElement de : mo.getAsJsonArray(key)) {
            JsonObject d = de.getAsJsonObject();
            String name = Json.str(d, "name", "").trim(), type = Json.str(d, "type", "");
            if (name.isEmpty() || type.isEmpty() || type.equals("Nothing")) continue;
            out.add(new RobotConfig.Device(type, name, d.get("port").getAsInt(), d.has("bus") && !d.get("bus").isJsonNull() ? d.get("bus").getAsInt() : -1));
        }
    }
}
