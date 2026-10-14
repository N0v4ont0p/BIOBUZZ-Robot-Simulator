package org.firstinspires.ftc.teamcode.config;

import android.content.Context;

import org.firstinspires.ftc.robotcore.internal.system.AppUtil;
import org.firstinspires.ftc.teamcode.pedro.Constants;
import org.firstinspires.ftc.teamcode.subsystems.Intake;
import org.firstinspires.ftc.teamcode.subsystems.Launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Setup programs only. The numbers earlier setup programs saved in the robot's results file
// (/sdcard/FIRST/biobuzz_results.csv) that the code does not have yet, so the next program can use them
// without a new build. A number the code still has as NaN takes the file's newest value; a number saved
// after the running build was installed replaces the code's value (that build cannot contain it).
// Directions and reversed flags have no "not set" value, so the code's one may be a placeholder whatever the
// install time: the file's newest value wins whenever it differs (a rebuild or restart used to put the
// placeholder directions back). To change one, run its program again.
// TeleOp and the autos call restore() first: match code runs only on what is in the code.
public final class LiveResults {
    public static final String FILE = "biobuzz_results.csv";
    // program 2 and program 7 answers that make other answers unusable (same rules as the programmer's step)
    private static final String[] SIDES = {"front", "back"};
    private static final String[] FLYWHEEL = {"FlywheelP", "FlywheelI", "FlywheelD", "FlywheelF", "ReadyBand", "IdleVelocity", "FlywheelVoltage",
            "ReadyBandLow", "ReadyBandHigh", "ReadyBandLowSpeed", "ReadyBandHighSpeed"};

    private static final Map<Field, Object> codeValues = new HashMap<>();
    private static final List<String> used = new ArrayList<>();
    private static String problem = "";

    private LiveResults() {}

    // Puts the file's numbers in place (after putting the code's back). Returns how many came from the file.
    public static synchronized int apply() {
        restore();
        Map<String, String[]> rows;
        try {
            rows = newest(file());
        } catch (IOException | RuntimeException e) {
            problem = "results file unreadable: " + e.getMessage();
            return 0;
        }
        problem = "";
        long installed = installTimeMs();
        for (String side : SIDES) {
            String[] opposite = rows.get(side + "FlywheelEncoderOpposite");
            if (opposite != null && opposite[0].equals("true")) rows.remove(side + "FlywheelReversed");
            String[] throwsOut = rows.get(side + "FlywheelThrowsOut"), f = rows.get(side + "FlywheelF");
            if (throwsOut != null && throwsOut[0].equals("false") && (f == null || Long.parseLong(f[1]) <= Long.parseLong(throwsOut[1]))) {
                for (String k : FLYWHEEL) rows.remove(side + k);
            }
        }
        String[] frame = rows.get("velocityFrame");
        if (frame != null && !rows.containsKey("velocityFrameField")) {
            rows.put("velocityFrameField", new String[]{frame[0].equals("field") ? "1" : "0", frame[1]});
        }
        for (Map.Entry<String, String[]> e : rows.entrySet()) {
            Field target = target(e.getKey());
            if (target == null) continue;
            String value = e.getValue()[0];
            boolean newer = Long.parseLong(e.getValue()[1]) > installed;
            try {
                Object code = target.get(null);
                Object fromFile = parse(target.getType(), value);
                if (fromFile == null || fromFile.equals(code)) continue;
                boolean missing = code instanceof Double && Double.isNaN((Double) code);
                boolean number = code instanceof Double;
                if (number && !missing && !newer) continue;
                if (!codeValues.containsKey(target)) codeValues.put(target, code);
                target.set(null, fromFile);
                used.add(e.getKey());
            } catch (IllegalAccessException | IllegalArgumentException ex) {
                problem = e.getKey() + ": " + ex.getMessage();
            }
        }
        return used.size();
    }

    // Back to the numbers in the code (TeleOp and autos call this before reading anything).
    public static synchronized void restore() {
        for (Map.Entry<Field, Object> e : codeValues.entrySet()) {
            try {
                e.getKey().set(null, e.getValue());
            } catch (IllegalAccessException ignored) {
                // public static fields: cannot happen
            }
        }
        codeValues.clear();
        used.clear();
    }

    // keys of the numbers in use from the file, not from the code
    public static synchronized List<String> used() {
        return new ArrayList<>(used);
    }

    public static synchronized String problem() {
        return problem;
    }

    // One line per result: program, key, value, time. Returns "" or the error.
    public static synchronized String record(String program, String key, String value) {
        File file = file();
        boolean newFile = !file.exists();
        try (FileWriter w = new FileWriter(file, true)) {
            if (newFile) w.write("program,key,value,time_ms\n");
            w.write(String.format(Locale.US, "%s,%s,%s,%d\n", program, key, value, System.currentTimeMillis()));
            return "";
        } catch (IOException e) {
            return "could not save: " + e.getMessage();
        }
    }

    // Pedro's tuners (program 5) save their results here too, under the names the code uses
    public static void pedro(String key, Object value) {
        record("5 Drive Tuning", key, String.valueOf(value));
    }

    // The value a key has now: its code field (after apply(), so the results file's newest when that is in use), else
    // the results file's newest for keys with no field (information lines). null = none (NaN, or never saved).
    public static synchronized String current(String key) {
        Field f = target(key);
        if (f != null) {
            try {
                Object v = f.get(null);
                if (v instanceof Double && ((Double) v).isNaN()) return null;
                return String.valueOf(v);
            } catch (IllegalAccessException e) {
                return null;
            }
        }
        String[] row = newestAll().get(key);
        return row == null ? null : row[0];
    }

    // the value is built into the code (not NaN there before any results file number was put in place)
    public static synchronized boolean inCode(String key) {
        Field f = target(key);
        if (f == null) return false;
        try {
            Object v = codeValues.containsKey(f) ? codeValues.get(f) : f.get(null);
            return !(v instanceof Double && ((Double) v).isNaN());
        } catch (IllegalAccessException e) {
            return false;
        }
    }

    // key -> {value, time_ms}, the newest line per key (empty when there is no readable file)
    public static synchronized Map<String, String[]> newestAll() {
        try {
            return newest(file());
        } catch (IOException | RuntimeException e) {
            return new LinkedHashMap<>();
        }
    }

    // every result line in file order: {program, key, value, time_ms}
    public static synchronized List<String[]> lines() {
        List<String[]> out = new ArrayList<>();
        File file = file();
        if (!file.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                String[] c = line.split(",", -1);
                if (c.length == 4 && c[3].matches("\\d+")) out.add(c);
            }
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    // the keys the results file has a value for (any program, any time)
    public static synchronized java.util.Set<String> saved() {
        try {
            return new java.util.HashSet<>(newest(file()).keySet());
        } catch (IOException | RuntimeException e) {
            return new java.util.HashSet<>();
        }
    }

    public static File file() {
        return new File(AppUtil.FIRST_FOLDER, FILE);
    }

    // key -> {value, time_ms}: the newest line per key (a redone step adds a newer line)
    private static Map<String, String[]> newest(File file) throws IOException {
        Map<String, String[]> out = new LinkedHashMap<>();
        if (!file.exists()) return out;
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                String[] c = line.split(",", -1);
                if (c.length != 4 || !c[3].matches("\\d+")) continue;
                String[] old = out.get(c[1]);
                if (old == null || Long.parseLong(c[3]) >= Long.parseLong(old[1])) out.put(c[1], new String[]{c[2], c[3]});
            }
        }
        return out;
    }

    // Where a result key goes in the code: a Tuned or Pedro Constants field of the same name, or a reversed flag.
    private static Field target(String key) {
        switch (key) {
            case "intakeReversed": return field(Intake.class, "REVERSED");
            case "frontFlywheelReversed": return field(Launcher.class, "FRONT_REVERSED");
            case "backFlywheelReversed": return field(Launcher.class, "BACK_REVERSED");
            default:
                Field f = field(Tuned.class, key);
                return f != null ? f : field(Constants.class, key);
        }
    }

    private static Field field(Class<?> c, String name) {
        try {
            Field f = c.getField(name);
            int m = f.getModifiers();
            return Modifier.isStatic(m) && !Modifier.isFinal(m) ? f : null;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object parse(Class<?> type, String value) {
        try {
            if (type == double.class) {
                double d = Double.parseDouble(value);
                return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
            }
            if (type == boolean.class) return value.equalsIgnoreCase("true") ? Boolean.TRUE : value.equalsIgnoreCase("false") ? Boolean.FALSE : null;
            if (type.isEnum()) return Enum.valueOf((Class<Enum>) type, value.toUpperCase(Locale.US));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return null;
    }

    // when the running build was installed; MAX = unknown, so only the "still NaN in the code" rule applies
    private static long installTimeMs() {
        try {
            Context c = AppUtil.getInstance().getApplication();
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).lastUpdateTime;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }
}
