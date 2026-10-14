package org.biobuzz.sim.loop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.biobuzz.sim.Sim;
import org.biobuzz.sim.hw.MotorPort;
import org.biobuzz.sim.hw.ServoPort;
import org.biobuzz.sim.phys.Expected;
import org.biobuzz.sim.phys.Robot;
import org.biobuzz.sim.phys.World;
import org.biobuzz.sim.rc.RobotConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// What the student measured vs the hidden robot: every key a part knows the true answer for (World.expected), and
// every scenario fault with the program step that should have caught it.
public final class ReportCard {
    private static final Pattern YAML = Pattern.compile("^\\s+(\\w+): \\{value: ([^,]*),", Pattern.MULTILINE);
    private static final Map<String, String> PARTS = new LinkedHashMap<>();
    static {
        String[] p = {"drive.leftFront", "left-front wheel", "drive.leftBack", "left-back wheel", "drive.rightFront", "right-front wheel",
                "drive.rightBack", "right-back wheel", "intake", "intake", "yaw", "turret (yaw)", "flywheel.front", "front flywheel",
                "flywheel.back", "back flywheel", "hood.front", "front hood", "hood.back", "back hood", "ramp", "ramp",
                "gate.front", "front gate", "gate.back", "back gate"};
        for (int i = 0; i < p.length; i += 2) PARTS.put(p[i], p[i + 1]);
    }

    private ReportCard() {
    }

    // biobuzz_results.csv rows: program, key, value, time_ms (oldest first)
    public static List<String[]> rows(File csv) {
        List<String[]> out = new ArrayList<>();
        if (!csv.isFile()) return out;
        try {
            for (String line : Files.readAllLines(csv.toPath(), StandardCharsets.UTF_8)) {
                String[] r = line.split(",", -1);
                if (r.length == 4 && r[3].matches("\\d+")) out.add(r);
            }
        } catch (IOException ignored) {
            // unreadable = nothing measured yet
        }
        return out;
    }

    public static Map<String, String> newest(List<String[]> rows) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String[] r : rows) out.put(r[1], r[2]);
        return out;
    }

    public static Map<String, String> yaml(File f) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Matcher m = YAML.matcher(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            while (m.find()) if (!m.group(2).equals("null")) out.put(m.group(1), m.group(2));
        } catch (IOException ignored) {
            // no sandbox yet
        }
        return out;
    }

    public static Map<String, Object> build(Sim sim, boolean reveal) {
        World w = sim.world;
        List<String[]> rows = rows(new File(sim.ws.sdcard, "FIRST/biobuzz_results.csv"));
        Map<String, String> results = newest(rows);
        Map<String, String> yaml = yaml(new File(sim.ws.sandbox, "team-config.yaml"));
        yaml.putAll(directions(new File(sim.ws.sandbox, "tools/codegen/Constants.java.tpl")));

        List<Map<String, Object>> entries = new ArrayList<>();
        Map<String, Integer> count = new LinkedHashMap<>();
        for (String s : new String[]{"green", "yellow", "red", "unmeasured", "other"}) count.put(s, 0);
        for (Map.Entry<String, Expected> e : w.expected().entrySet()) {
            Expected x = e.getValue();
            String raw = results.containsKey(e.getKey()) ? results.get(e.getKey()) : yaml.get(e.getKey());
            String from = results.containsKey(e.getKey()) ? "results file" : raw != null ? "team-config.yaml" : null;
            Double v = number(raw);
            String status = raw == null ? "unmeasured" : v == null ? "other" : grade(Math.abs(v - x.value), x.tolerance);
            count.merge(status, 1, Integer::sum);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.getKey());
            m.put("measured", raw);
            m.put("from", from);
            m.put("status", status);
            m.put("unit", x.unit);
            m.put("tolerance", x.tolerance);
            m.put("how", x.how);
            m.put("truth", reveal ? x.value : null);
            entries.add(m);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("scenario", w.truth.name());
        out.put("seed", sim.seed);
        out.put("revealed", reveal);
        out.put("count", count);
        out.put("entries", entries);
        out.put("faults", faults(sim, w, rows));
        return out;
    }

    // drive and pod directions are code (Constants.java.tpl), not YAML: c.frontLeftDirection.set(...REVERSE)
    static Map<String, String> directions(File tpl) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("c\\.(\\w+Direction)\\.set\\([\\w.]+\\.(\\w+)\\)")
                    .matcher(new String(java.nio.file.Files.readAllBytes(tpl.toPath()), java.nio.charset.StandardCharsets.UTF_8));
            while (m.find()) out.put(m.group(1), m.group(2));
        } catch (java.io.IOException ignored) {
        }
        return out;
    }

    static String grade(double err, double tol) {
        if (err <= tol) return "green";
        return err <= 2 * tol ? "yellow" : "red";
    }

    static Double number(String s) {
        if (s == null) return null;
        if (s.equals("true") || s.equals("FORWARD") || s.equals("field")) return 1.0;
        if (s.equals("false") || s.equals("robot")) return 0.0;
        if (s.equals("REVERSE") || s.equals("REVERSED")) return -1.0;
        try {
            double v = Double.parseDouble(s);
            return Double.isNaN(v) ? null : v;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Map<String, Object>> faults(Sim sim, World w, List<String[]> rows) {
        List<Map<String, Object>> out = new ArrayList<>();
        RobotConfig cfg = sim.activeConfig();
        Robot r = w.robot;
        for (JsonObject f : w.truth.faults()) {
            String type = str(f, "type");
            String text, step;
            Boolean caught = null;
            switch (type) {
                case "swapMotorCables":
                case "swapServoCables": {
                    boolean motor = type.equals("swapMotorCables");
                    String a = str(f, "a"), b = str(f, "b");
                    text = (motor ? "Motor" : "Servo") + " cables swapped: port " + a + " has the " + part(r, a, motor) + ", port " + b + " has the " + part(r, b, motor) + ".";
                    step = "Configure Robot (D-1): name each port by following its cable (Wiring tab). Missed there: "
                            + (motor ? "program 1 step 3 lights the wrong line when you turn a part, and program 5's Mecanum Tuner spins the wrong wheel."
                            : "program 4 moves the wrong servo when you press square.");
                    caught = cfg != null && named(cfg, r, a, motor) && named(cfg, r, b, motor);
                    break;
                }
                case "encoderUnplugged":
                case "encoderFrom": {
                    String port = str(f, "port");
                    text = type.equals("encoderUnplugged") ? "The " + part(r, port, true) + " encoder cable (port " + port + ") is unplugged."
                            : "Port " + port + "'s encoder cable comes from the " + PARTS.getOrDefault(str(f, "part"), str(f, "part")) + ", not its own motor.";
                    step = type.equals("encoderUnplugged") ? "Program 1 step 3: its line stays \"...\" when you turn it by hand (recorded as encodersNotCounting)."
                            : "Program 1 step 3, if you turn ONE part at a time: turning the " + PARTS.getOrDefault(str(f, "part"), str(f, "part"))
                            + " lights two lines, and the part on port " + port + " lights none (recorded as encodersNotCounting).";
                    String name = configName(cfg, r, port);
                    caught = false;
                    for (String[] row : rows) if (row[1].equals("encodersNotCounting") && name != null && (" " + row[2] + " ").contains(" " + name + " ")) caught = true;
                    break;
                }
                case "encoderReversed": {
                    String port = str(f, "port");
                    MotorPort mp = r.motorPort(port);
                    String id = mp.motor == null ? "" : mp.motor.id;
                    text = "The " + part(r, port, true) + " encoder counts backwards (port " + port + ").";
                    if (id.startsWith("flywheel.")) {
                        String side = id.substring(9);
                        step = "Program 2 step " + (side.equals("front") ? 2 : 3) + ": red STOP \"encoder counts the opposite way\" (" + side + "FlywheelEncoderOpposite = true).";
                        caught = any(rows, side + "FlywheelEncoderOpposite", "true");
                    } else {
                        step = "Nothing to fix by hand: program 3 measures the turret's sign itself, and Pedro drives from the Pinpoint pods, not the wheel encoders.";
                    }
                    break;
                }
                case "lowBattery":
                    text = String.format(Locale.US, "The battery was low (%.2f V at rest).", 11.75 + 1.55 * w.truth.num("battery.charge", 0.95));
                    step = "Program 1 step 2: below 12.5 V means swap the battery (Robot tab: Swap in a charged battery).";
                    caught = r.battery.charge > w.truth.num("battery.charge", 0.95) + 0.01;
                    break;
                case "turretNotForward":
                    text = String.format(Locale.US, "The turrets were %.0f deg off straight forward when INIT set the zero.", w.truth.num("turret.startDeg", 0));
                    step = "Before INIT of program 3 (and 8, 9, TeleOp): point both barrels straight forward. No program checks this by itself (a gap): "
                            + "in program 3 Part A the left/right readings come out uneven, e.g. +100 / -80 instead of +90 / -90.";
                    break;
                case "intakeReversed":
                    text = "The intake motor turns the other way (+ power pushes elements out).";
                    step = "Program 2 step 1: elements don't go in, answer NO (intakeReversed = true).";
                    caught = "true".equals(newest(rows).get("intakeReversed"));
                    break;
                default:
                    text = type;
                    step = "";
            }
            out.add(fault(f.has("text") ? str(f, "text") : text, f.has("caughtBy") ? str(f, "caughtBy") : step, caught));
        }
        JsonObject rename = w.truth.section("config").has("rename") ? w.truth.section("config").getAsJsonObject("rename") : new JsonObject();
        for (Map.Entry<String, JsonElement> e : rename.entrySet()) {
            String right = e.getKey(), typed = e.getValue().getAsString();
            out.add(fault("A teammate typed \"" + typed + "\" instead of \"" + right + "\" in the configuration.",
                    "Program 1 step 1 lists the missing name \"" + right + "\". Fix it in Configure Robot and activate the configuration again.",
                    cfg != null && hasName(cfg, right) && !hasName(cfg, typed)));
        }
        return out;
    }

    private static Map<String, Object> fault(String text, String step, Boolean caught) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        m.put("caughtBy", step);
        m.put("caught", caught);
        return m;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    private static boolean any(List<String[]> rows, String key, String value) {
        for (String[] r : rows) if (r[1].equals(key) && r[2].equals(value)) return true;
        return false;
    }

    private static String partId(Robot r, String port, boolean motor) {
        if (motor) {
            MotorPort p = r.motorPort(port);
            return p.motor == null ? null : p.motor.id;
        }
        ServoPort p = r.servoPort(port);
        return p.servo == null ? null : p.servo.id;
    }

    private static String part(Robot r, String port, boolean motor) {
        String id = partId(r, port, motor);
        return id == null ? "nothing" : PARTS.getOrDefault(id, id) + (motor ? " motor" : " servo");
    }

    // the configured name on that port is the name of the part really plugged in there
    private static boolean named(RobotConfig cfg, Robot r, String port, boolean motor) {
        String id = partId(r, port, motor);
        String[] p = port.split("\\.");
        RobotConfig.Module m = cfg.module(p[0].equals("CH") ? r.controlHub.address : r.expansionHub.address);
        if (m == null || id == null) return false;
        for (RobotConfig.Device d : motor ? m.motors : m.servos)
            if (d.port == Integer.parseInt(p[1])) return d.name.equals(Sim.NAMES.get(id));
        return false;
    }

    private static String configName(RobotConfig cfg, Robot r, String port) {
        if (cfg == null) return null;
        String[] p = port.split("\\.");
        RobotConfig.Module m = cfg.module(p[0].equals("CH") ? r.controlHub.address : r.expansionHub.address);
        if (m == null) return null;
        for (RobotConfig.Device d : m.motors) if (d.port == Integer.parseInt(p[1])) return d.name;
        return null;
    }

    private static boolean hasName(RobotConfig cfg, String name) {
        for (RobotConfig.Module m : cfg.modules)
            for (List<RobotConfig.Device> ds : List.of(m.motors, m.servos, m.i2c))
                for (RobotConfig.Device d : ds) if (d.name.equals(name)) return true;
        return false;
    }
}
