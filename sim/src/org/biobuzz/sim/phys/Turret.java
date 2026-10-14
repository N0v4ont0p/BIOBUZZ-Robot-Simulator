package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.hw.MotorPort;
import org.biobuzz.sim.truth.Truth;
import org.firstinspires.ftc.robotcore.external.navigation.Rotation;

import java.util.LinkedHashMap;
import java.util.Map;

// One yaw motor geared to both turrets. Three bodies: the drive side (motor, gearbox, pinions; angle in front-turret
// radians), the front turret and the back turret (each in its own radians, 0 = barrel straight forward, CCW +).
// Each turret meshes with the drive side through a gear with play (backlash); the back pinion turns backRatio times
// the front one. Hard stops and the cable act on the front turret. The yaw encoder is on the motor, so the robot code
// only ever sees the drive side.
public class Turret implements Part {
    private static final double MESH_STIFFNESS = 2000, MESH_DAMPING = 2;  // N*m/rad, N*m per rad/s
    private static final double HAND_STIFFNESS = 60, HAND_DAMPING = 1.5, HAND_MAX = 8;
    private static final double WIGGLE_HZ = 2.5, LIMIT_MARGIN_DEG = 10;
    private static final int SUBSTEPS = 4;
    // what the robot code adds to the reaction time it measures: ~2 loops at ~9 ms (hub calls) plus its speed filter
    private static final double SENSE_DELAY_S = 0.035;

    public final Motor motor;
    public final double gear;        // motor output turns per front turret turn, sign = mesh direction
    public final double backRatio;   // back turret turns per front turret turn, sign = same / opposite way
    final Body drive = new Body(), front = new Body(), back = new Body();
    final double playFront, playBack;
    final double stopMax, stopMin, cableStretch, cableStiffness;
    double cableTightMax, cableTightMin;
    // a snag (scenario G-turret-snag): within SNAG_HALF_DEG of snagDeg the front turret is held while it is pushed left
    // with less than snagTorque (a cable that catches when turning left; turning right frees it); NaN = none
    final double snag, snagTorque;
    private static final double SNAG_HALF_DEG = 0.6;
    boolean cableDamaged;
    String contact;                  // "left" / "right" when pressed against a stop or the end of the cable
    // optional magnetic switch at forward (Robot wires it to a digital port): the magnet on the front turret is
    // mounted a little off true forward, and the switch sees it over a band either side
    final boolean hasIndex;
    final double indexDeg, indexHalfWidthDeg;

    private Body handBody;
    private double handTarget, wiggleLeft, wiggleT;
    private Body wiggleBody;
    private double voltSum, voltTime;

    public Turret(Robot r, Truth t) {
        motor = new Motor("yaw", MotorSpec.of(t.str("turret.motor", "435")));
        r.addMotor(motor);
        t.random().nextLong();   // java.util.Random's first number hardly changes between nearby seeds
        double turns = pick(t, "motorTurnsPerFrontTurn", 4.2, 6.0);
        gear = turns * Math.signum(t.num("turret.motorSign", t.random().nextDouble() < 0.5 ? 1 : -1));
        backRatio = pick(t, "backRatio", 0.9, 1.15);
        playFront = Math.toRadians(pick(t, "playFrontDeg", 0.3, 0.9));
        playBack = Math.toRadians(pick(t, "playBackDeg", 0.3, 0.9));
        stopMax = Math.toRadians(pick(t, "stopMaxDeg", 150, 175));
        stopMin = Math.toRadians(pick(t, "stopMinDeg", -175, -150));
        cableTightMax = Math.toRadians(pick(t, "cableTightMaxDeg", 185, 210));
        cableTightMin = Math.toRadians(pick(t, "cableTightMinDeg", -210, -185));
        cableStretch = Math.toRadians(t.num("turret.cableStretchDeg", 20));
        cableStiffness = t.num("turret.cableStiffness", 3.0);
        snag = Math.toRadians(t.num("turret.snagDeg", Double.NaN));
        snagTorque = t.num("turret.snagTorque", 1.0);
        hasIndex = t.bool("turret.indexSensor", false);
        indexDeg = t.range("turret.indexDeg", -1.5, 1.5);
        indexHalfWidthDeg = t.range("turret.indexHalfWidthDeg", 1.5, 2.5);

        double fs = pick(t, "staticFriction", 0.03, 0.08);   // each turret's bearing and cable drag, N*m
        double fk = t.num("turret.kineticFriction", 0.75 * fs);
        double bias = pick(t, "frictionBias", -0.15, 0.15);  // extra fraction when turning left (CCW)
        double visc = pick(t, "viscous", 0.01, 0.03);
        front.inertia = pick(t, "frontInertia", 0.006, 0.012);
        back.inertia = pick(t, "backInertia", 0.006, 0.012);
        for (Body b : new Body[]{front, back}) {
            b.staticPos = fs * (1 + bias);
            b.staticNeg = fs * (1 - bias);
            b.kineticPos = fk * (1 + bias);
            b.kineticNeg = fk * (1 - bias);
            b.viscous = visc;
        }
        double g = motor.frictionTorque() * Math.abs(gear);
        drive.kineticPos = drive.kineticNeg = g;
        drive.staticPos = drive.staticNeg = 1.3 * g;
        drive.inertia = motor.reflectedInertia() * gear * gear + t.num("turret.driveInertia", 0.002);

        front.angle = Math.toRadians(pick(t, "startDeg", -6, 6));
        drive.angle = front.angle + (t.random().nextDouble() - 0.5) * playFront;
        back.angle = backRatio * drive.angle + (t.random().nextDouble() - 0.5) * playBack;
        syncMotor();
    }

    private static double pick(Truth t, String key, double lo, double hi) {
        return t.num("turret." + key, lo + t.random().nextDouble() * (hi - lo));
    }

    @Override
    public String id() {
        return "turret";
    }

    public double frontDeg() {
        return Math.toDegrees(front.angle);
    }

    public double backDeg() {
        return Math.toDegrees(back.angle);
    }

    @Override
    public void step(double dt, World w) {
        double volts = w.robot.battery.volts;
        double h = dt / SUBSTEPS;
        for (int i = 0; i < SUBSTEPS; i++) substep(h, volts);
        if (motor.driven && motor.duty != 0) {
            voltSum += volts * dt;
            voltTime += dt;
        }
        if (wiggleLeft > 0) {
            wiggleLeft -= dt;
            wiggleT += dt;
            if (wiggleLeft <= 0) wiggleBody = null;
        }
    }

    private void substep(double h, double volts) {
        syncMotor();
        double onDrive = motor.torque(volts) * gear;
        double cf = mesh(front.angle - drive.angle, front.rate - drive.rate, playFront);
        double cb = mesh(back.angle - backRatio * drive.angle, back.rate - backRatio * drive.rate, playBack);
        onDrive -= cf + backRatio * cb;
        double onFront = cf + cable() + hand(front), onBack = cb + hand(back);
        if (wiggleBody != null) {
            double push = (wiggleBody.staticPos + 0.4) * Math.signum(Math.sin(2 * Math.PI * WIGGLE_HZ * wiggleT));
            if (wiggleBody == front) onFront += push;
            else onBack += push;
            drive.rate = 0;   // E-6: the yaw motor is held still
        } else {
            drive.step(onDrive, h);
        }
        double frontBefore = front.angle;
        front.step(onFront, h);
        // it catches only while the turret is pushed left into it (backing off right is free, like a cable that catches)
        if (!Double.isNaN(snag) && Math.abs(frontBefore - snag) < Math.toRadians(SNAG_HALF_DEG) && onFront > 0 && onFront < snagTorque) {
            front.angle = frontBefore;
            front.rate = 0;
        }
        back.step(onBack, h);
        stops();
        syncMotor();
    }

    // the magnetic switch sees the magnet (its digital input reads false)
    public boolean indexActive() {
        return hasIndex && Math.abs(Math.toDegrees(front.angle) - indexDeg) <= indexHalfWidthDeg;
    }

    // torque on the turret from its mesh; d = turret angle minus where the pinion holds it
    private static double mesh(double d, double rate, double play) {
        double half = play / 2;
        if (d > half) return Math.min(0, -MESH_STIFFNESS * (d - half) - MESH_DAMPING * rate);
        if (d < -half) return Math.max(0, -MESH_STIFFNESS * (d + half) - MESH_DAMPING * rate);
        return 0;
    }

    private double cable() {
        if (front.angle > cableTightMax) return -cableStiffness * (front.angle - cableTightMax);
        if (front.angle < cableTightMin) return -cableStiffness * (front.angle - cableTightMin);
        return 0;
    }

    private double hand(Body b) {
        if (handBody != b) return 0;
        double t = HAND_STIFFNESS * (handTarget - b.angle) - HAND_DAMPING * b.rate;
        return Math.max(-HAND_MAX, Math.min(HAND_MAX, t));
    }

    private void stops() {
        double hi = Math.min(stopMax, cableTightMax + cableStretch), lo = Math.max(stopMin, cableTightMin - cableStretch);
        contact = null;
        if (front.angle >= hi) {
            front.angle = hi;
            front.rate = Math.min(0, front.rate);
            contact = "left";
            if (hi < stopMax) cableDamaged = true;
        } else if (front.angle <= lo) {
            front.angle = lo;
            front.rate = Math.max(0, front.rate);
            contact = "right";
            if (lo > stopMin) cableDamaged = true;
        }
    }

    private void syncMotor() {
        motor.outAngle = drive.angle * gear;
        motor.outRate = drive.rate * gear;
    }

    double cableTension() {
        double past = Math.max(front.angle - cableTightMax, cableTightMin - front.angle);
        return Math.max(0, Math.min(1, past / cableStretch));
    }

    @Override
    public void state(Map<String, Object> out) {
        out.put("frontDeg", frontDeg());
        out.put("backDeg", backDeg());
        out.put("rateDegS", Math.toDegrees(front.rate));
        out.put("atStop", contact);
        out.put("cableTension", cableTension());
        out.put("cableSide", front.angle > 0 ? "left" : "right");
        out.put("cableDamaged", cableDamaged);
        out.put("hand", handBody == null ? null : handBody == front ? "front" : "back");
        out.put("wiggle", wiggleBody == null ? null : wiggleBody == front ? "front" : "back");
        Map<String, Object> coach = new LinkedHashMap<>();
        coach.put("leftLimitDeg", Math.toDegrees(Math.min(stopMax, cableTightMax)));
        coach.put("rightLimitDeg", Math.toDegrees(Math.max(stopMin, cableTightMin)));
        coach.put("leftIs", cableTightMax < stopMax ? "cable tight" : "hard stop");
        coach.put("rightIs", cableTightMin > stopMin ? "cable tight" : "hard stop");
        coach.put("playFrontDeg", Math.toDegrees(playFront));
        coach.put("playBackDeg", Math.toDegrees(playBack));
        coach.put("encoderDeg", Math.toDegrees(drive.angle));
        out.put("coach", coach);
    }

    // turn {deg, back}: move your hand by deg (that turret's own degrees) and keep holding; release; wiggle {back}:
    // hold the yaw motor and rock that turret for 2 s to feel the play (E-6); fixCable: re-route a tight cable
    @Override
    public boolean hand(String action, JsonObject a, World w) {
        boolean isBack = a.has("back") && a.get("back").getAsBoolean();
        Body b = isBack ? back : front;
        switch (action) {
            case "turn":
                if (handBody != b) {
                    handBody = b;
                    handTarget = b.angle;
                }
                handTarget += Math.toRadians(a.get("deg").getAsDouble());
                return true;
            case "release":
                handBody = null;
                return true;
            case "wiggle":
                handBody = null;
                wiggleBody = b;
                wiggleT = 0;
                wiggleLeft = a.has("seconds") ? a.get("seconds").getAsDouble() : 2;
                return true;
            case "fixCable":
                cableTightMax = Math.max(cableTightMax, stopMax + Math.toRadians(20));
                cableTightMin = Math.min(cableTightMin, stopMin - Math.toRadians(20));
                cableDamaged = false;
                return true;
            default:
                return false;
        }
    }

    @Override
    public void expected(Map<String, Expected> out, World w) {
        MotorSpec s = motor.spec;
        double volts = voltTime > 0 ? voltSum / voltTime : w.robot.battery.openCircuit();
        // the SDK turns a motor type with CCW orientation (goBILDA) around: power and counts both flip
        MotorPort port = yawPort(w);
        int sdk = port != null && port.type != null && port.type.getOrientation() == Rotation.CCW ? -1 : 1;
        int polarity = port != null && port.encoder != null && port.encoder.source == motor ? port.encoder.polarity : 1;
        double ticksFront = sdk * polarity * gear * s.countsPerOutputRev() / 360;
        double motorTurns = s.gear * Math.abs(gear);            // motor shaft turns per front turret turn
        double stallPerPower = s.kT * motorTurns * volts / s.resistance;
        double ksLeft = (drive.staticPos + front.staticPos + Math.abs(backRatio) * (backRatio > 0 ? back.staticPos : back.staticNeg)) / stallPerPower;
        double ksRight = (drive.staticNeg + front.staticNeg + Math.abs(backRatio) * (backRatio > 0 ? back.staticNeg : back.staticPos)) / stallPerPower;
        double viscous = front.viscous + backRatio * backRatio * back.viscous;
        double emfDamping = s.kT * s.kE * motorTurns * motorTurns * s.gearEfficiency / s.resistance;
        double kv = (s.kE * motorTurns + viscous * s.resistance / (s.kT * motorTurns * s.gearEfficiency)) / volts;
        double tau = (drive.inertia + front.inertia + backRatio * backRatio * back.inertia) / (emfDamping + viscous);
        double left = Math.toDegrees(Math.min(stopMax, cableTightMax)), right = Math.toDegrees(Math.max(stopMin, cableTightMin));
        double ks = (ksLeft + ksRight) / 2;
        out.put("yawTicksPerDegFront", new Expected(ticksFront, 0.03 * Math.abs(ticksFront), "ticks/deg",
                "yaw encoder counts per degree of the front turret (left minus right over 180)"));
        out.put("yawTicksPerDegBack", new Expected(ticksFront / backRatio, 0.03 * Math.abs(ticksFront / backRatio), "ticks/deg",
                "yaw encoder counts per degree of the back turret"));
        out.put("yawMaxDeg", new Expected(left - LIMIT_MARGIN_DEG, 4, "deg",
                "left " + (cableTightMax < stopMax ? "cable tight" : "hard stop") + " minus 10 deg"));
        out.put("yawMinDeg", new Expected(right + LIMIT_MARGIN_DEG, 4, "deg",
                "right " + (cableTightMin > stopMin ? "cable tight" : "hard stop") + " plus 10 deg"));
        out.put("yawPowerSign", new Expected(sdk * Math.signum(gear), 0.5, "", "+ power turns the turret left (+1) or right (-1)"));
        out.put("yawTuningVoltage", new Expected(volts, 0.3, "V", "battery voltage while the yaw motor ran"));
        out.put("yawKs", new Expected(ks, Math.max(0.02, 0.4 * ks), "power", "power that just starts the whole yaw drive moving (average of both ways)"));
        out.put("yawKv", new Expected(Math.toRadians(kv), 0.12 * Math.toRadians(kv), "power per deg/s", "power per deg/s of steady turret speed"));
        out.put("yawReactionTimeS", new Expected(tau, 0.02, "s",
                "time constant: inertia over back-EMF and grease damping (the program times how far the turn lags its steady speed)"));
        double kp = 1 / (2 * (tau + SENSE_DELAY_S / 2));
        // the switch's edge met turning LEFT by hand, read by the motor encoder: a turret pushed by hand drives the
        // motor through the gear play, so the motor (and the reading) trails the turret by half the play
        if (hasIndex) out.put("yawIndexDeg", new Expected(indexDeg - indexHalfWidthDeg - Math.toDegrees(playFront) / 2, 0.3, "deg",
                "front-turret angle where the index switch turns on when the turret turns left (program 3)"));
        out.put("yawPlayDeg", new Expected(Math.toDegrees(playFront), 0.2, "deg", "free play of the front turret with the yaw motor held (E-6, program 3)"));
        out.put("yawKp", new Expected(kp, 0.4 * kp, "deg/s per deg", "fastest kP that stops within 1 deg (time constant plus loop delay)"));
    }

    private MotorPort yawPort(World w) {
        for (Hub hub : w.robot.hubs)
            for (MotorPort p : hub.motors)
                if (p.motor == motor) return p;
        return null;
    }

    // one rotating body: inertia, grease (viscous), static and kinetic friction that can differ by direction
    static final class Body {
        double angle, rate, inertia = 0.01, viscous;
        double staticPos, staticNeg, kineticPos, kineticNeg;

        void step(double torque, double h) {
            double drive = torque - viscous * rate;
            double net;
            if (rate == 0) {
                if (drive >= 0 ? drive <= staticPos : -drive <= staticNeg) return;
                net = drive - (drive > 0 ? kineticPos : -kineticNeg);
            } else {
                net = drive - (rate > 0 ? kineticPos : -kineticNeg);
            }
            double before = rate;
            rate += net / inertia * h;
            if (before * rate < 0) rate = 0;
            angle += rate * h;
        }
    }
}
