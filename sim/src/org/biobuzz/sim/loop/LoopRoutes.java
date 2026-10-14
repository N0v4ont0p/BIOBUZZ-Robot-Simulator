package org.biobuzz.sim.loop;

import com.google.gson.JsonObject;

import org.biobuzz.sim.Sim;
import org.biobuzz.sim.web.Json;
import org.biobuzz.sim.web.Server;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The results loop: copy the results off the robot ("adb pull"), the programmer step (coach mode does it with
// tools/apply_results.py; real mode shows what the programmer will do), and the report card.
public final class LoopRoutes {
    private static final String[] PROGRAMS = {"1 Check Devices", "2 Motor Directions", "3 Yaw Tuner", "4 Servo Positions",
            "5 Drive Tuning", "6 Position Check", "7 Flywheel Tuner", "8 Front Shot Tuner", "9 Back Shot Tuner"};

    private LoopRoutes() {
    }

    public static void register(Sim sim) {
        Server.route("loop/status", (b, q) -> status(sim));
        Server.route("loop/pull", (b, q) -> {
            File dir = sim.ws.pull();
            List<String> files = new ArrayList<>(Arrays.asList(dir.list()));
            files.sort(null);
            sim.note(files.isEmpty() ? "copy results: no results on the robot yet" : "copied " + String.join(", ", files) + " to outbox/" + dir.getName());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dir", dir.getPath());
            m.put("files", files);
            return m;
        });
        // the photo of the Driver Station's RESULTS block: a new outbox folder with the last copy plus these rows
        Server.route("loop/paste", (b, q) -> {
            List<String> rows = new ArrayList<>(), programs = new ArrayList<>();
            String program = null;
            long now = System.currentTimeMillis();
            for (String line : Json.str(b, "text", "").split("\\R")) {
                java.util.regex.Matcher h = java.util.regex.Pattern.compile("^\\s*RESULTS P(\\d)\\b").matcher(line);
                java.util.regex.Matcher kv = java.util.regex.Pattern.compile("^\\s*([\\w.]+)\\s*[=:]\\s*(\\S+)\\s*$").matcher(line);
                if (h.find()) {
                    int n = Integer.parseInt(h.group(1));
                    program = n >= 1 && n <= PROGRAMS.length ? PROGRAMS[n - 1] : null;
                    if (program != null && !programs.contains(program)) programs.add(program);
                } else if (line.trim().startsWith("END P")) {
                    program = null;
                } else if (program != null && kv.find()) {
                    rows.add(program + "," + kv.group(1) + "," + kv.group(2) + "," + now);
                }
            }
            if (rows.isEmpty()) throw new IllegalArgumentException("No results found. It needs a RESULTS P<n> line, then key = value lines.");
            File last = sim.ws.newestPull();
            File dir = sim.ws.pull();
            for (File f : dir.listFiles()) f.delete();
            if (last != null) for (File f : last.listFiles()) java.nio.file.Files.copy(f.toPath(), new File(dir, f.getName()).toPath());
            File csv = new File(dir, "biobuzz_results.csv");
            StringBuilder sb = new StringBuilder(csv.isFile() ? "" : "program,key,value,time_ms\n");
            for (String r : rows) sb.append(r).append('\n');
            java.nio.file.Files.write(csv.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            sim.note("results from the Driver Station photo: " + rows.size() + " value(s) from " + String.join(", ", programs) + " in outbox/" + dir.getName());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("count", rows.size());
            m.put("programs", programs);
            m.put("dir", dir.getName());
            return m;
        });
        Server.route("loop/plan", (b, q) -> apply(sim, false));
        Server.route("loop/coach", (b, q) -> {
            synchronized (sim) {
                JsonObject r = apply(sim, true);
                if (!r.get("ok").getAsBoolean()) {
                    sim.note("coach: entering the results failed, nothing installed");
                    return r;
                }
                sim.note("coach: results entered in the sandbox");
                try {
                    sim.build();
                    r.addProperty("installed", true);
                } catch (Exception e) {
                    r.addProperty("installed", false);
                    r.addProperty("buildError", e.getMessage());
                }
                return r;
            }
        });
        Server.route("loop/report", (b, q) -> ReportCard.build(sim, "1".equals(q.get("reveal"))));
    }

    private static JsonObject apply(Sim sim, boolean write) throws Exception {
        File dir = sim.ws.newestPull();
        if (dir == null) throw new IllegalStateException("Copy the results off the robot first (Copy results).");
        List<String> cmd = new ArrayList<>(Arrays.asList("python3", new File(sim.simDir, "tools/apply_results.py").getPath(),
                dir.getPath(), "--sandbox", sim.ws.sandbox.getPath()));
        if (write) cmd.add("--apply");
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.INHERIT).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.waitFor() != 0) throw new IOException("apply_results.py failed (see the simulator's terminal):\n" + out);
        JsonObject r = Json.parse(out);
        r.addProperty("pulled", dir.getName());
        return r;
    }

    private static Map<String, Object> status(Sim sim) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, String> paths = new LinkedHashMap<>();
        paths.put("robot", new File(sim.ws.sdcard, "FIRST").getPath());
        paths.put("outbox", sim.ws.outbox.getPath());
        paths.put("sandbox", sim.ws.sandbox.getPath());
        m.put("paths", paths);
        List<String> robotFiles = new ArrayList<>();
        for (File f : sim.ws.robotResultFiles()) robotFiles.add(f.getName());
        m.put("robotFiles", robotFiles);
        m.put("programs", programTimes(new File(sim.ws.sdcard, "FIRST/biobuzz_results.csv")));
        m.put("results", ReportCard.newest(ReportCard.rows(new File(sim.ws.sdcard, "FIRST/biobuzz_results.csv"))));
        File pull = sim.ws.newestPull();
        if (pull != null) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("dir", pull.getName());
            pm.put("time", pull.lastModified());
            pm.put("programs", programTimes(new File(pull, "biobuzz_results.csv")));
            m.put("pull", pm);
        }
        m.put("builtAt", sim.builtAt);
        m.put("buildError", sim.buildError);
        m.put("yaml", ReportCard.yaml(new File(sim.ws.sandbox, "team-config.yaml")));
        return m;
    }

    // program name -> time of its newest result line
    private static Map<String, Long> programTimes(File csv) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String[] r : ReportCard.rows(csv)) out.merge(r[0], Long.parseLong(r[3]), Math::max);
        return out;
    }
}
