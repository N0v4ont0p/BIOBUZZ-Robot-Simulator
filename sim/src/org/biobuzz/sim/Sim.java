package org.biobuzz.sim;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.qualcomm.robotcore.hardware.configuration.ConfigurationType;

import org.biobuzz.sim.phys.Robot;
import org.biobuzz.sim.phys.World;
import org.biobuzz.sim.rc.Devices;
import org.biobuzz.sim.rc.DriverStation;
import org.biobuzz.sim.rc.RobotConfig;
import org.biobuzz.sim.rc.RobotController;
import org.biobuzz.sim.rc.TeamCode;
import org.biobuzz.sim.truth.Truth;
import org.biobuzz.sim.web.Json;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Holds the simulator together: the hidden robot (World), the sandbox, the Robot Controller run, the Driver
// Station. Scenario load, Restart Robot and Install build all go through here.
public final class Sim {
    private static Sim instance;
    public final File simDir, repo;
    public final Workspace ws;
    public final DriverStation ds = new DriverStation();
    public volatile World world;
    public volatile RobotController rc;
    public volatile TeamCode code;
    public volatile String scenario;
    public volatile long seed;
    public volatile String buildLog = "";
    public volatile String buildError;
    public volatile long builtAt;
    private final List<String> log = new ArrayList<>();

    public Sim(File simDir, File work) {
        this.simDir = simDir;
        this.repo = simDir.getAbsoluteFile().getParentFile();
        ws = new Workspace(repo, work);
        instance = this;
    }

    public static Sim get() {
        return instance;
    }

    public void note(String s) {
        synchronized (log) {
            log.add(s);
            while (log.size() > 100) log.remove(0);
        }
        System.out.println("[sim] " + s);
    }

    public List<String> log() {
        synchronized (log) {
            return new ArrayList<>(log);
        }
    }

    public File scenarioDir() {
        return new File(simDir, "scenarios");
    }

    public List<Map<String, String>> scenarios() throws IOException {
        List<Map<String, String>> out = new ArrayList<>();
        File[] files = scenarioDir().listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return out;
        Arrays.sort(files);
        for (File f : files) {
            Truth t = Truth.load(f, 1);
            Map<String, String> m = new LinkedHashMap<>();
            m.put("file", f.getName());
            m.put("name", t.name());
            m.put("description", t.description());
            out.add(m);
        }
        return out;
    }

    // a new hidden robot; freshSandbox also throws away entered results and the robot's files
    public synchronized void loadScenario(String file, long seed, boolean freshSandbox) throws Exception {
        stopRobot();
        if (world != null) world.stop();
        File f = resolveScenario(new File(scenarioDir(), file), seed);
        Truth t = Truth.load(f, seed);
        this.scenario = file;
        this.seed = seed;
        world = new World(t);
        world.start();
        if (freshSandbox || !ws.exists()) {
            ws.reset();
            ws.clearRobot();
            JsonObject cfg = t.section("config");
            if ("preset".equals(t.str("config", "")) || cfg.has("preset") && cfg.get("preset").getAsBoolean()) {
                RobotConfig c = presetConfig("BIOBUZZ");
                JsonObject rename = cfg.has("rename") ? cfg.getAsJsonObject("rename") : new JsonObject();
                for (RobotConfig.Module m : c.modules)
                    for (List<RobotConfig.Device> ds : Arrays.asList(m.motors, m.servos, m.i2c, m.digital))
                        for (RobotConfig.Device d : ds) if (rename.has(d.name)) d.name = rename.get(d.name).getAsString();
                c.write(configFile(c.name));
                setActiveConfig(c.name);
            }
            code = null;
        }
        JsonObject session = new JsonObject();
        session.addProperty("file", file);
        session.addProperty("seed", seed);
        Files.write(sessionFile().toPath(), Json.write(session).getBytes(StandardCharsets.UTF_8));
        note("scenario " + t.name() + " (seed " + seed + ")");
        if (code == null) build();
        else startRobot();
    }

    // The scenario as this run sees it, written to <work>/scenario.json: "randomFaults": {"count": n, "pool": [...]}
    // picks n faults by seed; a fault's "set": {"battery.charge": 0.3} writes those hidden-robot values.
    private File resolveScenario(File f, long seed) throws IOException {
        JsonObject root = Json.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        JsonArray faults = root.has("faults") ? root.getAsJsonArray("faults") : new JsonArray();
        if (root.has("randomFaults")) {
            JsonObject r = root.remove("randomFaults").getAsJsonObject();
            List<JsonElement> pool = new ArrayList<>();
            r.getAsJsonArray("pool").forEach(pool::add);
            Collections.shuffle(pool, new Random(seed));
            for (int i = 0; i < Math.min(r.get("count").getAsInt(), pool.size()); i++) faults.add(pool.get(i));
        }
        root.add("faults", faults);
        for (JsonElement e : faults) {
            JsonObject set = e.getAsJsonObject().getAsJsonObject("set");
            if (set == null) continue;
            for (Map.Entry<String, JsonElement> kv : set.entrySet()) {
                JsonObject o = root;
                String[] path = kv.getKey().split("\\.");
                for (int i = 0; i < path.length - 1; i++) {
                    if (!o.has(path[i]) || !o.get(path[i]).isJsonObject()) o.add(path[i], new JsonObject());
                    o = o.getAsJsonObject(path[i]);
                }
                o.add(path[path.length - 1], kv.getValue());
            }
        }
        ws.root.mkdirs();
        File out = new File(ws.root, "scenario.json");
        Files.write(out.toPath(), Json.write(root).getBytes(StandardCharsets.UTF_8));
        return out;
    }

    // ---- the session (scenario and seed) and checkpoints, so a restart or a mistake never costs the work done

    public File sessionFile() {
        return new File(ws.root, "session.json");
    }

    // the last scenario and seed, or null when there is nothing to resume
    public JsonObject lastSession() {
        try {
            return ws.exists() && sessionFile().isFile() ? Json.parse(new String(Files.readAllBytes(sessionFile().toPath()), StandardCharsets.UTF_8)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    public File checkpointDir() {
        return new File(ws.root, "checkpoints");
    }

    // ponytail: full copies of sandbox + robot files + results (a few MB each), oldest dropped past 30
    // returns the checkpoint's name (null if it failed)
    public synchronized String checkpoint(String label) {
        try {
            String name = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new java.util.Date()) + " " + label.replaceAll("[^\\w .+-]", "");
            File d = new File(checkpointDir(), name);
            for (int i = 2; d.exists(); i++) d = new File(checkpointDir(), name + " " + i);
            Workspace.copyTree(ws.sandbox, new File(d, "sandbox"));
            Workspace.copyTree(ws.sdcard, new File(d, "robot-sdcard"));
            if (ws.outbox.isDirectory()) Workspace.copyTree(ws.outbox, new File(d, "outbox"));
            Files.copy(sessionFile().toPath(), new File(d, "session.json").toPath());
            File[] all = checkpointDir().listFiles(File::isDirectory);
            Arrays.sort(all);
            for (int i = 0; i < all.length - 30; i++) {
                // never drop the checkpoint test mode goes back to
                if (!all[i].getName().equals(testModeCheckpoint())) Workspace.deleteTree(all[i]);
            }
            return d.getName();
        } catch (IOException e) {
            note("checkpoint failed: " + e.getMessage());
            return null;
        }
    }

    // ---- test mode (simulator only): a fully built and tuned robot at one click, and back with nothing left over
    // Start: a checkpoint of everything (the robot code, every file on the robot, the copied results, the session),
    // the robot's own files emptied, then tools/test_mode.py puts the true values in the code (build). End: that
    // checkpoint restored, and every checkpoint and build made in between deleted. The marker file keeps it across a
    // restart of the simulator.
    private volatile String testModeState = null;

    private File testModeFile() {
        return new File(ws.root, "test-mode.json");
    }

    private JsonObject testModeMarker() {
        try {
            return testModeFile().isFile() ? Json.parse(new String(Files.readAllBytes(testModeFile().toPath()), StandardCharsets.UTF_8)) : null;
        } catch (Exception e) {
            return null;
        }
    }

    private String testModeCheckpoint() {
        JsonObject m = testModeMarker();
        return m == null ? null : m.get("checkpoint").getAsString();
    }

    // null = not in test mode, else {state: "setting up" / "ready" / "failed: ...", since}
    public Map<String, Object> testMode() {
        JsonObject m = testModeMarker();
        if (m == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("state", testModeState != null ? testModeState : "ready");
        out.put("since", m.get("startedMs").getAsLong());
        return out;
    }

    public synchronized void startTestMode() throws IOException {
        if (testModeFile().exists()) throw new IllegalStateException("already in test mode");
        long started = System.currentTimeMillis();
        stopRobot();
        String cp = checkpoint("before test mode");
        if (cp == null) throw new IOException("could not save the checkpoint: test mode not started");
        JsonObject m = new JsonObject();
        m.addProperty("checkpoint", cp);
        m.addProperty("startedMs", started);
        Files.write(testModeFile().toPath(), Json.write(m).getBytes(StandardCharsets.UTF_8));
        // a clean robot: none of your results, tables or logs (they come back at the end)
        File first = new File(ws.sdcard, "FIRST");
        File[] files = first.listFiles((dir, n) -> n.startsWith("biobuzz_"));
        if (files != null) for (File f : files) Files.deleteIfExists(f.toPath());
        testModeState = "setting up";
        note("TEST MODE: setting up a fully tuned robot (your work is saved in checkpoint \"" + cp + "\")");
        Thread t = new Thread(() -> {
            try {
                Process p = new ProcessBuilder("python3", new File(simDir, "tools/test_mode.py").getPath(),
                        "http://localhost:" + System.getProperty("sim.port", "8080")).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (p.waitFor() == 0) {
                    testModeState = "ready";
                    note("TEST MODE: ready (a fully built and tuned robot)");
                } else {
                    String last = out.trim().isEmpty() ? "" : out.trim().substring(Math.max(0, out.trim().lastIndexOf('\n') + 1));
                    testModeState = "failed: " + last;
                    note("TEST MODE: setting up failed: " + out.trim());
                }
            } catch (Exception e) {
                testModeState = "failed: " + e.getMessage();
            }
        }, "test-mode");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void endTestMode() throws Exception {
        JsonObject m = testModeMarker();
        if (m == null) throw new IllegalStateException("not in test mode");
        String cp = m.get("checkpoint").getAsString();
        long started = m.get("startedMs").getAsLong();
        long ended = System.currentTimeMillis();
        stopRobot();
        // back to the saved state: restore() replaces the robot code, the robot's files and the copied results
        File d = new File(checkpointDir(), cp);
        if (!new File(d, "session.json").isFile()) throw new IOException("the test mode checkpoint is missing: " + cp);
        JsonObject s = Json.parse(new String(Files.readAllBytes(new File(d, "session.json").toPath()), StandardCharsets.UTF_8));
        for (File f : new File[]{ws.sandbox, ws.sdcard, ws.outbox}) Workspace.deleteTree(f);
        Workspace.copyTree(new File(d, "sandbox"), ws.sandbox);
        Workspace.copyTree(new File(d, "robot-sdcard"), ws.sdcard);
        if (new File(d, "outbox").isDirectory()) Workspace.copyTree(new File(d, "outbox"), ws.outbox);
        Files.copy(new File(d, "session.json").toPath(), sessionFile().toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        // everything test mode made: its checkpoints (the saved one included: its contents are back in place) and builds
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        long from = started / 1000 * 1000;
        File[] cps = checkpointDir().listFiles(File::isDirectory);
        if (cps != null) {
            for (File c : cps) {
                try {
                    long t = f.parse(c.getName().substring(0, 19)).getTime();
                    if (t >= from && t <= ended) Workspace.deleteTree(c);
                } catch (Exception e) {
                    // not a checkpoint name: left alone
                }
            }
        }
        File[] builds = new File(ws.root, "build").listFiles(File::isDirectory);
        if (builds != null) {
            for (File b : builds) {
                String[] parts = b.getName().split("-");
                try {
                    long t = Long.parseLong(parts[1]);
                    if (t >= started && t <= ended) Workspace.deleteTree(b);
                } catch (RuntimeException e) {
                    // not a build folder: left alone
                }
            }
        }
        Files.deleteIfExists(testModeFile().toPath());
        testModeState = null;
        code = null;
        loadScenario(s.get("file").getAsString(), s.get("seed").getAsLong(), false);
        note("TEST MODE ended: everything back as it was, the test robot's numbers, files, builds and checkpoints deleted");
    }

    public List<Map<String, Object>> checkpoints() {
        List<Map<String, Object>> out = new ArrayList<>();
        File[] all = checkpointDir().listFiles(File::isDirectory);
        if (all == null) return out;
        Arrays.sort(all, Collections.reverseOrder());
        for (File d : all) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", d.getName());
            try {
                JsonObject s = Json.parse(new String(Files.readAllBytes(new File(d, "session.json").toPath()), StandardCharsets.UTF_8));
                m.put("scenario", s.get("file").getAsString());
                m.put("seed", s.get("seed").getAsLong());
            } catch (Exception e) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    // back to a checkpoint: its hidden robot, its sandbox, its robot files; then build and install
    public synchronized void restore(String name) throws Exception {
        File d = new File(checkpointDir(), name);
        if (name.contains("/") || !new File(d, "session.json").isFile()) throw new IllegalArgumentException("no checkpoint " + name);
        JsonObject s = Json.parse(new String(Files.readAllBytes(new File(d, "session.json").toPath()), StandardCharsets.UTF_8));
        stopRobot();
        for (File f : new File[]{ws.sandbox, ws.sdcard, ws.outbox}) Workspace.deleteTree(f);
        Workspace.copyTree(new File(d, "sandbox"), ws.sandbox);
        Workspace.copyTree(new File(d, "robot-sdcard"), ws.sdcard);
        if (new File(d, "outbox").isDirectory()) Workspace.copyTree(new File(d, "outbox"), ws.outbox);
        code = null;
        loadScenario(s.get("file").getAsString(), s.get("seed").getAsLong(), false);
        note("restored checkpoint " + name);
    }

    // "Build and install" from Android Studio: compile the sandbox TeamCode, restart the Robot Controller
    public synchronized void build() throws Exception {
        File out = ws.nextBuildDir();
        try {
            buildLog = TeamCode.compile(ws.teamCodeSource(), out);
            buildError = null;
            builtAt = System.currentTimeMillis();
            // what the robot code sees as the app's install time (PackageInfo.lastUpdateTime)
            System.setProperty("sim.installTimeMs", Long.toString(builtAt));
            code = new TeamCode(out);
            note("build installed");
            checkpoint("build installed");
        } catch (IOException e) {
            buildError = e.getMessage();
            note("build FAILED; the robot keeps the old build");
            throw e;
        }
        restartRobot();
    }

    // the repo's newer robot code into the sandbox, keeping every number entered there (tools/update_sandbox.py),
    // then build and install; a checkpoint first, so it can be undone
    public synchronized String updateSandbox() throws Exception {
        checkpoint("before updating the robot code");
        Process p = new ProcessBuilder("python3", new File(simDir, "tools/update_sandbox.py").getPath(), repo.getPath(), ws.sandbox.getPath())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IOException("Updating the robot code failed (restore the checkpoint \"before updating\"):\n" + out);
        note("robot code updated from the repo, entered numbers kept");
        build();
        return out;
    }

    // runs tools/set_tuning.py on the sandbox: returns render.sh output, throws if anything failed
    public synchronized String setTuning(String valuesJson) throws Exception {
        Process p = new ProcessBuilder("python3", new File(simDir, "tools/set_tuning.py").getPath(), ws.sandbox.getPath(), valuesJson)
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IOException("Entering values failed:\n" + out);
        note("values entered: " + valuesJson);
        return out;
    }

    public synchronized void restartRobot() throws Exception {
        stopRobot();
        startRobot();
    }

    private void startRobot() throws Exception {
        RobotController r = new RobotController(ds, world, code, activeConfig());
        rc = r;
        r.start();
        note("Robot Controller started" + (activeConfigName() == null ? " (no configuration)" : " with configuration " + activeConfigName()));
    }

    private void stopRobot() {
        RobotController r = rc;
        rc = null;
        if (r != null) r.stop();
    }

    // ---- configurations, stored on the robot like the real app does

    public File configFile(String name) {
        return new File(ws.sdcard, "FIRST/" + name + ".xml");
    }

    private File activeFile() {
        return new File(ws.sdcard, "FIRST/.sim-active-config");
    }

    public String activeConfigName() {
        try {
            File f = activeFile();
            if (!f.isFile()) return null;
            String n = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).trim();
            return configFile(n).isFile() ? n : null;
        } catch (IOException e) {
            return null;
        }
    }

    public void setActiveConfig(String name) throws IOException {
        activeFile().getParentFile().mkdirs();
        Files.write(activeFile().toPath(), name.getBytes(StandardCharsets.UTF_8));
    }

    public RobotConfig activeConfig() {
        String n = activeConfigName();
        if (n == null) return null;
        try {
            return readConfig(n);
        } catch (Exception e) {
            note("configuration " + n + " could not be read: " + e.getMessage());
            return null;
        }
    }

    public RobotConfig readConfig(String name) throws Exception {
        return RobotConfig.read(configFile(name), Devices.tags(ConfigurationType.DeviceFlavor.I2C), Devices.tags(ConfigurationType.DeviceFlavor.SERVO),
                Devices.tags(ConfigurationType.DeviceFlavor.DIGITAL_IO));
    }

    public List<String> configNames() {
        List<String> out = new ArrayList<>();
        File[] fs = new File(ws.sdcard, "FIRST").listFiles((d, n) -> n.endsWith(".xml"));
        if (fs != null) for (File f : fs) out.add(f.getName().replaceAll("\\.xml$", ""));
        out.sort(String::compareTo);
        return out;
    }

    // what "Scan" finds: the hubs that are attached
    public RobotConfig scanned(String name) {
        RobotConfig c = new RobotConfig(name);
        for (org.biobuzz.sim.hw.Hub h : world.robot.hubs) c.modules.add(new RobotConfig.Module(h.name, h.address));
        return c;
    }

    public static String goBildaMotorTag() {
        for (Map<String, String> m : Devices.list(ConfigurationType.DeviceFlavor.MOTOR))
            if (m.get("name").contains("goBILDA") && m.get("name").contains("5202")) return m.get("tag");
        return "goBILDA5202SeriesMotor";
    }

    // the device names the setup guide asks for (D-1), by physical part
    public static final Map<String, String> NAMES = new LinkedHashMap<>();
    static {
        String[] pairs = {"drive.leftFront", "lf", "drive.leftBack", "lb", "drive.rightFront", "rf", "drive.rightBack", "rb",
                "intake", "intake", "pollenFeed", "pfeed", "flywheel.front", "fs", "flywheel.back", "bs", "ramp.left", "rampl",
                "ramp.right", "rampr", "gate.front", "fg", "gate.back", "bg", "gate.back2", "bg2", "yaw.front", "fyaw", "yaw.back", "byaw",
                "pinpoint", "pp"};
        for (int i = 0; i < pairs.length; i += 2) NAMES.put(pairs[i], pairs[i + 1]);
    }

    // the configuration the setup guide asks for, on the default wiring
    public RobotConfig presetConfig(String name) {
        Map<String, String> names = NAMES;
        RobotConfig c = scanned(name);
        String motor = goBildaMotorTag();
        String[][] motors = {Robot.DEFAULT_CH_MOTORS, Robot.DEFAULT_EH_MOTORS};
        String[][] servos = {Robot.DEFAULT_CH_SERVOS, Robot.DEFAULT_EH_SERVOS};
        for (int h = 0; h < 2; h++) {
            RobotConfig.Module m = c.modules.get(h);
            for (int i = 0; i < 4; i++) if (motors[h][i] != null) m.motors.add(new RobotConfig.Device(motor, names.get(motors[h][i]), i, -1));
            // the ramp servos are continuous rotation ones
            for (int i = 0; i < 6; i++) if (servos[h][i] != null) m.servos.add(new RobotConfig.Device(servos[h][i].startsWith("ramp.")
                    ? "ContinuousRotationServo" : "Servo", names.get(servos[h][i]), i, -1));
        }
        c.modules.get(0).i2c.add(new RobotConfig.Device(Devices.PINPOINT_TAG, "pp", 0, 1));
        return c;
    }
}
