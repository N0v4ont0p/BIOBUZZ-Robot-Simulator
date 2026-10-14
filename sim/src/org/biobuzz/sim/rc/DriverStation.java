package org.biobuzz.sim.rc;

import com.qualcomm.robotcore.robocol.Command;
import com.qualcomm.robotcore.robocol.RobocolParsable;
import com.qualcomm.robotcore.robocol.TelemetryMessage;
import com.qualcomm.robotcore.util.RobotLog;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// The Driver Station side of the link. It only knows what the real Driver Station knows: the commands and
// telemetry the Robot Controller sends, and what the drivers press. It behaves like the real app: OpMode lists,
// INIT / START / STOP, the 30 s autonomous timer, loading the autonomous' TeleOp when it stops.
public final class DriverStation {
    public static final String STOP = "$Stop$Robot$";
    public static final double AUTO_SECONDS = 30;

    private volatile RobotController rc;
    public volatile String active = STOP;        // what the Robot Controller says is loaded
    public volatile String state = "STOPPED";    // STOPPED, INIT, RUNNING
    public volatile String selected;             // chosen in the Driver Station list (not loaded yet)
    public volatile String displayFormat = "CLASSIC";
    public volatile boolean autoTimer = true;
    private volatile long runStartedNs;
    private volatile String lastStartedFlavor = "";
    private final List<String> telemetry = new ArrayList<>();
    private long telemetrySeq;
    private final List<Map<String, Object>> events = new ArrayList<>();
    private long eventSeq;

    public void attach(RobotController rc) {
        this.rc = rc;
        active = STOP;
        state = "STOPPED";
        synchronized (telemetry) {
            telemetry.clear();
            telemetrySeq++;
        }
    }

    // ---- from the Robot Controller

    public void onCommand(Command c) {
        String name = c.getName(), extra = c.getExtra();
        switch (name) {
            case "CMD_NOTIFY_INIT_OP_MODE":
                boolean wasRunningAuto = "RUNNING".equals(state) && "AUTONOMOUS".equals(lastStartedFlavor);
                String previous = active;
                active = extra;
                state = STOP.equals(extra) ? "STOPPED" : "INIT";
                if (STOP.equals(extra) && wasRunningAuto) {
                    String next = rc == null ? null : rc.autoTransition(previous);
                    if (next != null && !next.isEmpty()) {
                        selected = next;
                        event("toast", "Loaded TeleOp \"" + next + "\". Press INIT when ready.");
                    }
                } else if (!STOP.equals(extra)) {
                    selected = extra;
                }
                break;
            case "CMD_NOTIFY_RUN_OP_MODE":
                active = extra;
                if (STOP.equals(extra)) break;
                state = "RUNNING";
                runStartedNs = System.nanoTime();
                lastStartedFlavor = rc == null ? "" : rc.flavor(extra);
                break;
            case "CMD_SET_TELEM_DISPL_FORMAT":
                displayFormat = extra;
                break;
            case "CMD_SHOW_TOAST":
                event("toast", toastText(extra));
                break;
            case "CMD_SHOW_DIALOG":
            case "CMD_SHOW_PROGRESS":
                event("dialog", extra);
                break;
            case "CMD_TEXT_TO_SPEECH":
                event("speech", extra);
                break;
            default:
                break;
        }
    }

    private static String toastText(String extra) {
        try {
            com.google.gson.JsonObject o = new com.google.gson.JsonParser().parse(extra).getAsJsonObject();
            if (o.has("message")) return o.get("message").getAsString();
        } catch (RuntimeException ignored) {
        }
        return extra;
    }

    public void onData(RobocolParsable p) {
        if (!(p instanceof TelemetryMessage)) return;
        TelemetryMessage m = (TelemetryMessage) p;
        String tag = m.getTag();
        if (tag != null && !tag.isEmpty() && !tag.equals(TelemetryMessage.DEFAULT_TAG)) return;
        Map<String, String> data = m.isSorted() ? new TreeMap<>(m.getDataStrings()) : new LinkedHashMap<>(m.getDataStrings());
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> e : data.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("\u0000")) lines.add(e.getValue());
            else lines.add(k + " : " + e.getValue());
        }
        // the SDK's "clear telemetry" message is one empty hidden line
        if (lines.size() == 1 && lines.get(0).isEmpty()) lines.clear();
        synchronized (telemetry) {
            telemetry.clear();
            telemetry.addAll(lines);
            telemetrySeq++;
        }
    }

    public void event(String type, Object data) {
        synchronized (events) {
            Map<String, Object> e = new LinkedHashMap<>();
            e.put("seq", ++eventSeq);
            e.put("type", type);
            e.put("data", data);
            events.add(e);
            while (events.size() > 200) events.remove(0);
        }
    }

    // ---- from the drivers

    public void select(String name) {
        selected = name;
    }

    public void init() {
        String s = selected;
        if (rc != null && s != null) rc.command(new Command("CMD_INIT_OP_MODE", s));
    }

    public void start() {
        if (rc != null && "INIT".equals(state)) rc.command(new Command("CMD_RUN_OP_MODE", active));
    }

    public void stop() {
        if (rc != null) rc.command(new Command("CMD_INIT_OP_MODE", STOP));
    }

    // the Driver Station stops an autonomous after 30 s when its timer is on
    public void tick() {
        if (autoTimer && "RUNNING".equals(state) && "AUTONOMOUS".equals(lastStartedFlavor)
                && (System.nanoTime() - runStartedNs) / 1e9 >= AUTO_SECONDS) {
            stop();
        }
    }

    public Map<String, Object> state(long sinceEvent) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", active);
        m.put("state", state);
        m.put("selected", selected);
        m.put("displayFormat", displayFormat);
        m.put("autoTimer", autoTimer);
        m.put("runSeconds", "RUNNING".equals(state) ? (System.nanoTime() - runStartedNs) / 1e9 : 0);
        m.put("runningFlavor", "RUNNING".equals(state) ? lastStartedFlavor : "");
        m.put("error", RobotLog.getGlobalErrorMsg());
        m.put("warning", RobotLog.getGlobalWarningMessage().message);
        synchronized (telemetry) {
            m.put("telemetry", new ArrayList<>(telemetry));
            m.put("telemetrySeq", telemetrySeq);
        }
        List<Map<String, Object>> ev = new ArrayList<>();
        synchronized (events) {
            for (Map<String, Object> e : events) if ((Long) e.get("seq") > sinceEvent) ev.add(e);
        }
        m.put("events", ev);
        RobotController r = rc;
        m.put("robot", r == null ? null : r.status());
        return m;
    }
}
