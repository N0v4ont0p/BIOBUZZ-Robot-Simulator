package org.biobuzz.sim.phys;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.hw.MotorPort;
import org.biobuzz.sim.hw.ServoPort;
import org.biobuzz.sim.truth.Truth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// The whole hidden robot: battery, hubs with their cables, and every mechanism. Part ids (physical, not the
// configuration names): drive.leftFront/leftBack/rightFront/rightBack, intake, yaw, flywheel.front/back (motors);
// hood.front/back, ramp, gate.front/back (servos); pinpoint (I2C).
public final class Robot {
    public final Truth truth;
    public final Battery battery;
    public final Map<String, Motor> motors = new LinkedHashMap<>();
    public final Map<String, ServoModel> servos = new LinkedHashMap<>();
    public final List<Part> parts = new ArrayList<>();
    public final List<Hub> hubs = new ArrayList<>();
    public final Hub controlHub, expansionHub;
    public boolean onBox = true;

    public final Drive drive;
    public final Intake intake;
    public final Turret turret;
    public final Flywheels flywheels;
    public final ServoMechs servoMechs;
    public final Pinpoint pinpoint;
    public final Shots shots;

    public static final String[] DEFAULT_CH_MOTORS = {"drive.leftFront", "drive.leftBack", "drive.rightFront", "drive.rightBack"};
    public static final String[] DEFAULT_EH_MOTORS = {"intake", "yaw", "flywheel.front", "flywheel.back"};
    public static final String[] DEFAULT_CH_SERVOS = {"hood.front", "hood.back", "ramp", "gate.front", "gate.back", null};
    public static final String[] DEFAULT_EH_SERVOS = {null, null, null, null, null, null};

    public Robot(Truth t) {
        truth = t;
        battery = new Battery(t);
        drive = add(new Drive(this, t));
        intake = add(new Intake(this, t));
        turret = add(new Turret(this, t));
        flywheels = add(new Flywheels(this, t));
        servoMechs = add(new ServoMechs(this, t));
        pinpoint = add(new Pinpoint(this, t));
        shots = add(new Shots(this, t));
        onBox = t.bool("robot.startsOnBox", true);

        controlHub = new Hub("Control Hub", 173, true, t);
        expansionHub = new Hub("Expansion Hub 2", 2, false, t);
        hubs.add(controlHub);
        hubs.add(expansionHub);
        wire(controlHub, "wiring.controlHub", DEFAULT_CH_MOTORS, DEFAULT_CH_SERVOS);
        wire(expansionHub, "wiring.expansionHub", DEFAULT_EH_MOTORS, DEFAULT_EH_SERVOS);
        controlHub.i2c[(int) t.num("wiring.pinpointBus", 1)] = "pinpoint";
        // optional: a magnetic limit switch that sees a magnet on the front turret at forward (only if the robot has one)
        if (t.bool("turret.indexSensor", false)) controlHub.digital[(int) t.num("wiring.yawIndexPort", 0)] = "yawIndex";
        applyWiringFaults(t);
    }

    private <P extends Part> P add(P p) {
        parts.add(p);
        return p;
    }

    private void wire(Hub hub, String path, String[] defMotors, String[] defServos) {
        JsonObject w = truth.section(path);
        for (int i = 0; i < 4; i++) {
            String part = listEntry(w, "motors", i, defMotors[i]);
            MotorPort port = hub.motors[i];
            port.motor = part == null ? null : motors.get(part);
            port.encoder = port.motor == null ? null : new Encoder(port.motor, 1);
        }
        for (int i = 0; i < 6; i++) {
            String part = listEntry(w, "servos", i, defServos[i]);
            hub.servos[i].servo = part == null ? null : servos.get(part);
        }
    }

    private static String listEntry(JsonObject w, String key, int i, String def) {
        if (!w.has(key) || !w.get(key).isJsonArray() || w.getAsJsonArray(key).size() <= i) return def;
        JsonElement e = w.getAsJsonArray(key).get(i);
        return e.isJsonNull() ? null : e.getAsString();
    }

    // wiring faults: {"type":"swapMotorCables","a":"CH.0","b":"CH.1"}, {"type":"encoderUnplugged","port":"EH.2"},
    // {"type":"encoderFrom","port":"EH.1","part":"intake"}, {"type":"encoderReversed","port":"EH.2"},
    // {"type":"swapServoCables","a":"CH.0","b":"CH.1"}
    private void applyWiringFaults(Truth t) {
        for (JsonObject f : t.faults()) {
            String type = f.has("type") ? f.get("type").getAsString() : "";
            switch (type) {
                case "swapMotorCables": {
                    MotorPort a = motorPort(f.get("a").getAsString()), b = motorPort(f.get("b").getAsString());
                    Motor m = a.motor;
                    Encoder e = a.encoder;
                    a.motor = b.motor;
                    a.encoder = b.encoder;
                    b.motor = m;
                    b.encoder = e;
                    break;
                }
                case "encoderUnplugged":
                    motorPort(f.get("port").getAsString()).encoder = null;
                    break;
                case "encoderFrom":
                    motorPort(f.get("port").getAsString()).encoder = new Encoder(motors.get(f.get("part").getAsString()), 1);
                    break;
                case "encoderReversed": {
                    MotorPort p = motorPort(f.get("port").getAsString());
                    if (p.encoder != null) p.encoder = new Encoder(p.encoder.source, -p.encoder.polarity);
                    break;
                }
                case "swapServoCables": {
                    ServoPort a = servoPort(f.get("a").getAsString()), b = servoPort(f.get("b").getAsString());
                    ServoModel s = a.servo;
                    a.servo = b.servo;
                    b.servo = s;
                    break;
                }
                default:
                    break;
            }
        }
    }

    public MotorPort motorPort(String ref) {
        String[] p = ref.split("\\.");
        return (p[0].equals("CH") ? controlHub : expansionHub).motors[Integer.parseInt(p[1])];
    }

    public ServoPort servoPort(String ref) {
        String[] p = ref.split("\\.");
        return (p[0].equals("CH") ? controlHub : expansionHub).servos[Integer.parseInt(p[1])];
    }

    public void addMotor(Motor m) {
        motors.put(m.id, m);
    }

    public void addServo(ServoModel s) {
        servos.put(s.id, s);
    }

    public List<Map<String, Object>> hubState() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Hub h : hubs) out.add(h.state());
        return out;
    }

    public boolean hand(String action, JsonObject args, World w) {
        switch (action) {
            case "onBox":
                onBox = true;
                return true;
            case "onFloor":
                onBox = false;
                return true;
            case "swapBattery":
                battery.swap();
                return true;
            default:
                return false;
        }
    }
}
