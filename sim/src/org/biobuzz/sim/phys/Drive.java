package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.hw.MotorPort;
import org.biobuzz.sim.truth.Truth;

import org.firstinspires.ftc.robotcore.external.navigation.Rotation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

// Mecanum drive. On the box the wheels spin freely. On the floor each wheel pushes the chassis through its
// rollers: the wheel's surface speed is compared with the speed the chassis motion asks of that wheel, and the
// difference (slip) makes a traction force, limited by grip. Rolling resistance, motor drag (coast or brake),
// a student's hands and the field walls act on the chassis too. Pose is the centre of the four wheel contacts.
public class Drive implements Part {
    public static final String[] IDS = {"drive.leftFront", "drive.leftBack", "drive.rightFront", "drive.rightBack"};
    public static final String[] DIRECTION_KEYS = {"frontLeftDirection", "backLeftDirection", "frontRightDirection", "backRightDirection"};
    private static final double IN = 0.0254, G = 9.81;
    private static final int SUBSTEPS = 10;
    private static final double SLIP_STIFFNESS = 800;          // N per m/s of slip
    private static final double HAND_K = 2500, HAND_C = 120, HAND_MAX = 220;          // N/m, N per m/s, N
    private static final double HAND_KT = 300, HAND_CT = 25, HAND_MAX_T = 60;         // N*m/rad, N*m per rad/s, N*m
    private static final double WALL_K = 40000, WALL_C = 1500;   // stiff and dead: a field wall does not bounce the robot back

    public final Motor[] motors = new Motor[4];
    public final Joint[] wheels = new Joint[4];
    public final int[] mount = new int[4];      // +1: positive motor rotation rolls the wheel forward
    public final double wheelRadiusIn;
    public final double massKg, inertia, halfWheelbaseIn, halfTrackIn, grip, strafeEfficiency, rolling;
    // robot body edges from the centre of the wheel contacts (the CAD sheet's C-2), inches
    public final double centerToFront, centerToBack, centerToLeft, centerToRight;
    public double x, y, heading;                // field pose (in, in, rad)
    public double fwdVel, leftVel, turnVel;     // robot frame (in/s, in/s, rad/s)
    private double vf, vl, w;                   // the same in m/s, rad/s
    private final Random rng;

    // hands: a target the hands pull the robot toward (field frame), and how it moves while pushing
    private boolean handOn, handFree;
    private double hx, hy, hh, hvx, hvy, hw, handTime, holdTime;

    public Drive(Robot r, Truth t) {
        rng = new Random(t.seed * 31 + 7);
        MotorSpec spec = MotorSpec.of(t.str("drive.motor", "312"));
        wheelRadiusIn = t.num("drive.wheelDiameterMm", 104) / 25.4 / 2;
        massKg = t.num("drive.massKg", range(13.0, 16.5));
        halfWheelbaseIn = t.num("drive.halfWheelbaseIn", range(6.2, 6.9));
        halfTrackIn = t.num("drive.halfTrackIn", range(6.6, 7.3));
        grip = t.num("drive.grip", range(0.85, 1.0));
        strafeEfficiency = t.num("drive.strafeEfficiency", range(0.78, 0.9));
        rolling = t.num("drive.rollingResistance", range(0.015, 0.03));
        inertia = massKg * (Math.pow(18 * IN, 2) * 2) / 12 * 1.15;
        centerToFront = t.num("robot.centerToFront", range(8.6, 9.0));
        centerToBack = t.num("robot.centerToBack", range(8.6, 9.0));
        centerToLeft = t.num("robot.centerToLeft", range(8.6, 9.0));
        centerToRight = t.num("robot.centerToRight", range(8.6, 9.0));
        for (int i = 0; i < 4; i++) {
            motors[i] = new Motor(IDS[i], spec);
            r.addMotor(motors[i]);
            Joint j = new Joint();
            j.inertia = 2.7e-4;
            j.viscous = 1e-4;
            j.motor = motors[i];
            wheels[i] = j;
            // the usual goBILDA layout: a left motor's positive rotation rolls its wheel forward, a right one's backward
            mount[i] = (int) t.num(IDS[i] + ".mount", i < 2 ? 1 : -1);
        }
        x = t.num("drive.startX", 72);
        y = t.num("drive.startY", 72);
        heading = Math.toRadians(t.num("drive.startHeadingDeg", 90));
    }

    private double range(double lo, double hi) {
        return lo + rng.nextDouble() * (hi - lo);
    }

    @Override
    public String id() {
        return "drive";
    }

    // the forward-equivalent speed (m/s) each wheel needs for this chassis motion: FL, BL, FR, BR. Sideways the
    // rollers slip, so a strafe needs more wheel turning (and gets less force) than the same speed forward.
    private double[] wheelNeeds(double f, double l, double om) {
        double k = (halfWheelbaseIn + halfTrackIn) * IN, ls = l / strafeEfficiency;
        return new double[]{f - ls - k * om, f + ls - k * om, f + ls + k * om, f - ls + k * om};
    }

    @Override
    public void step(double dt, World world) {
        double volts = world.robot.battery.volts;
        if (world.robot.onBox) {
            for (Joint j : wheels) {
                j.externalTorque = 0;
                j.step(dt, volts);
            }
            vf = vl = w = 0;
            fwdVel = leftVel = turnVel = 0;
            handOn = false;
            return;
        }
        double h = dt / SUBSTEPS, r = wheelRadiusIn * IN, k = (halfWheelbaseIn + halfTrackIn) * IN;
        double normal = massKg * G / 4, fMax = grip * normal, roll = rolling * normal;
        for (int s = 0; s < SUBSTEPS; s++) {
            double[] need = wheelNeeds(vf, vl, w);
            double[] f = new double[4];
            for (int i = 0; i < 4; i++) {
                double surface = mount[i] * wheels[i].rate * r;
                double slip = surface - need[i];
                f[i] = Math.max(-fMax, Math.min(fMax, SLIP_STIFFNESS * slip));
                // rolling resistance of the wheel and its rollers, opposing the way the wheel rolls on the ground
                f[i] -= roll * Math.tanh(need[i] / 0.02);
                wheels[i].externalTorque = -mount[i] * (f[i] + roll * Math.tanh(need[i] / 0.02)) * r;
                wheels[i].step(h, volts);
            }
            // generalized forces on the chassis: J^T f (mecanum rollers lose some force sideways)
            double qf = f[0] + f[1] + f[2] + f[3];
            double ql = (-f[0] + f[1] + f[2] - f[3]) * strafeEfficiency;
            double qw = (-f[0] - f[1] + f[2] + f[3]) * k;
            double c = Math.cos(heading), sn = Math.sin(heading);
            double[] ext = external(h, c, sn);
            qf += ext[0];
            ql += ext[1];
            qw += ext[2];
            vf += qf / massKg * h;
            vl += ql / massKg * h;
            w += qw / inertia * h;
            double vx = vf * c - vl * sn, vy = vf * sn + vl * c;
            x += vx / IN * h;
            y += vy / IN * h;
            heading = normalize(heading + w * h);
        }
        fwdVel = vf / IN;
        leftVel = vl / IN;
        turnVel = w;
        if (handOn) {
            handTime -= dt;
            if (handTime <= 0) {
                hvx = hvy = hw = 0;
                holdTime -= dt;
                if (holdTime <= 0) handOn = false;
            }
        }
    }

    // hand and wall forces in the robot frame (forward N, left N, turn N*m); advances the hand target
    private double[] external(double h, double c, double sn) {
        double fx = 0, fy = 0, tz = 0;
        double vx = (vf * c - vl * sn), vy = (vf * sn + vl * c);
        if (handOn) {
            hx += hvx * h / IN;
            hy += hvy * h / IN;
            hh += hw * h;
            fx = clamp(HAND_K * (hx - x) * IN - HAND_C * (vx - hvx), HAND_MAX);
            fy = clamp(HAND_K * (hy - y) * IN - HAND_C * (vy - hvy), HAND_MAX);
            // pressing into a wall with flat hands lets the wall square the robot up
            tz = handFree ? 0 : clamp(HAND_KT * normalize(hh - heading) - HAND_CT * (w - hw), HAND_MAX_T);
        }
        // walls: each body corner that is outside the 144 in field is pushed back in
        double[][] corners = corners();
        for (double[] p : corners) {
            double px = p[0], py = p[1], nx = 0, ny = 0, depth = 0;
            if (px < 0) { nx = 1; depth = -px; }
            else if (px > 144) { nx = -1; depth = px - 144; }
            if (py < 0) { ny = 1; depth = Math.max(depth, -py); }
            else if (py > 144) { ny = -1; depth = Math.max(depth, py - 144); }
            if (depth <= 0) continue;
            double rx = (px - x) * IN, ry = (py - y) * IN;
            double pvx = vx - w * ry, pvy = vy + w * rx;
            double out = pvx * nx + pvy * ny;
            double fn = WALL_K * depth * IN - WALL_C * out;
            fn = Math.max(0, fn);
            double wfx = fn * nx, wfy = fn * ny;
            fx += wfx;
            fy += wfy;
            tz += rx * wfy - ry * wfx;
        }
        // field frame to robot frame
        return new double[]{fx * c + fy * sn, -fx * sn + fy * c, tz};
    }

    // body corners in field inches: front-left, front-right, back-right, back-left
    public double[][] corners() {
        double c = Math.cos(heading), s = Math.sin(heading);
        double[][] local = {{centerToFront, centerToLeft}, {centerToFront, -centerToRight}, {-centerToBack, -centerToRight}, {-centerToBack, centerToLeft}};
        double[][] out = new double[4][2];
        for (int i = 0; i < 4; i++) {
            out[i][0] = x + local[i][0] * c - local[i][1] * s;
            out[i][1] = y + local[i][0] * s + local[i][1] * c;
        }
        return out;
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    private static double normalize(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }

    @Override
    public void state(Map<String, Object> out) {
        double[] deg = new double[4], rpm = new double[4];
        for (int i = 0; i < 4; i++) {
            deg[i] = Math.toDegrees(wheels[i].angle * mount[i]);
            rpm[i] = wheels[i].rate * mount[i] * 60 / (2 * Math.PI);
        }
        out.put("wheelDeg", deg);
        out.put("wheelRpm", rpm);
        out.put("x", x);
        out.put("y", y);
        out.put("headingDeg", Math.toDegrees(heading));
        double c = Math.cos(heading), s = Math.sin(heading);
        out.put("vx", fwdVel * c - leftVel * s);
        out.put("vy", fwdVel * s + leftVel * c);
        out.put("turnDegS", Math.toDegrees(turnVel));
        out.put("speed", Math.hypot(fwdVel, leftVel));
        out.put("corners", corners());
        Map<String, Object> cad = new LinkedHashMap<>();
        cad.put("centerToFront", round2(centerToFront));
        cad.put("centerToBack", round2(centerToBack));
        cad.put("centerToLeft", round2(centerToLeft));
        cad.put("centerToRight", round2(centerToRight));
        out.put("cad", cad);
        out.put("handOn", handOn);
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }

    @Override
    public boolean hand(String action, JsonObject a, World w) {
        switch (action) {
            case "turnWheel":
                wheels[a.get("wheel").getAsInt()].turnBy(Math.toRadians(a.get("deg").getAsDouble()));
                return true;
            case "releaseWheel":
                wheels[a.get("wheel").getAsInt()].release();
                return true;
            case "push": {
                // move the robot by hand: forward / left inches (robot frame) and turnDeg, over seconds, then hold still a moment
                if (w.robot.onBox) return false;
                double secs = Math.max(0.2, a.has("seconds") ? a.get("seconds").getAsDouble() : 1.0);
                double fwd = num(a, "forward"), left = num(a, "left"), turn = Math.toRadians(num(a, "turnDeg"));
                if (!handOn) {
                    hx = x;
                    hy = y;
                    hh = heading;
                }
                double c = Math.cos(hh), s = Math.sin(hh);
                hvx = (fwd * c - left * s) / secs * IN;
                hvy = (fwd * s + left * c) / secs * IN;
                hw = turn / secs;
                handTime = secs;
                holdTime = 0.4;
                handOn = true;
                handFree = a.has("square") && a.get("square").getAsBoolean();
                if (handFree) hh = heading;
                return true;
            }
            case "letGo":
                handOn = false;
                return true;
            case "place": {
                // lift the robot and set it down: a careful placement lands closer to where it was meant to
                boolean careful = a.has("careful") && a.get("careful").getAsBoolean();
                double posErr = careful ? 0.1 : 0.5, angErr = careful ? 0.3 : 1.5;
                x = a.get("x").getAsDouble() + (rng.nextDouble() * 2 - 1) * posErr * (a.has("exact") ? 0 : 1);
                y = a.get("y").getAsDouble() + (rng.nextDouble() * 2 - 1) * posErr * (a.has("exact") ? 0 : 1);
                heading = normalize(Math.toRadians(a.get("headingDeg").getAsDouble() + (rng.nextDouble() * 2 - 1) * angErr * (a.has("exact") ? 0 : 1)));
                // it cannot be set down through a wall: a placement error into one leaves it against the wall
                double minX = 144, maxX = 0, minY = 144, maxY = 0;
                for (double[] p : corners()) {
                    minX = Math.min(minX, p[0]);
                    maxX = Math.max(maxX, p[0]);
                    minY = Math.min(minY, p[1]);
                    maxY = Math.max(maxY, p[1]);
                }
                x += Math.max(0, -minX) - Math.max(0, maxX - 144);
                y += Math.max(0, -minY) - Math.max(0, maxY - 144);
                vf = vl = this.w = 0;
                fwdVel = leftVel = turnVel = 0;
                handOn = false;
                for (Joint j : wheels) j.rate = 0;
                return true;
            }
            default:
                return false;
        }
    }

    private static double num(JsonObject a, String k) {
        return a.has(k) ? a.get(k).getAsDouble() : 0;
    }

    // which config direction (FORWARD +1 / REVERSE -1) makes positive power roll each wheel forward; the SDK turns
    // a CCW motor type (goBILDA) around, so that counts as well
    public int directionFor(int i, World w) {
        int sdk = -1;
        for (Hub hub : w.robot.hubs)
            for (MotorPort p : hub.motors)
                if (p.motor == motors[i] && p.type != null) sdk = p.type.getOrientation() == Rotation.CCW ? -1 : 1;
        return mount[i] * sdk;
    }

    // top speed and coasting deceleration the Foresight tuner should find (in/s, in/s^2)
    public double topSpeedInS(double volts) {
        MotorSpec m = motors[0].spec;
        double r = wheelRadiusIn * IN, roll = rolling * massKg * G / 4;
        // steady state: motor torque at the wheel balances rolling resistance and gearbox friction
        double tLoad = roll * r / m.gearEfficiency / m.gear + m.kT * m.freeAmps;
        double current = tLoad / m.kT;
        double motorRate = (volts - current * m.resistance) / m.kE;
        return motorRate / m.gear * r / IN;
    }

    @Override
    public void expected(Map<String, Expected> out, World w) {
        String[] names = {"left-front", "left-back", "right-front", "right-back"};
        for (int i = 0; i < 4; i++)
            out.put(DIRECTION_KEYS[i], new Expected(directionFor(i, w), 0.5, "FORWARD=+1 / REVERSE=-1",
                    "the " + names[i] + " wheel rolls forward with positive power in this direction (Pedro Mecanum Tuner)"));
        out.put("centerToFront", new Expected(centerToFront, 0.1, "in", "CAD: centre of the wheel contacts to the front edge"));
        out.put("centerToBack", new Expected(centerToBack, 0.1, "in", "CAD: centre of the wheel contacts to the back edge"));
        out.put("centerToLeft", new Expected(centerToLeft, 0.1, "in", "CAD: centre of the wheel contacts to the left edge"));
        out.put("centerToRight", new Expected(centerToRight, 0.1, "in", "CAD: centre of the wheel contacts to the right edge"));
        double top = topSpeedInS(12.5);
        out.put("maxAchievableForwardVelocity", new Expected(top, 0.12 * top, "in/s", "top forward speed at full power (Foresight)"));
        out.put("maxAchievableStrafeVelocity", new Expected(top * strafeEfficiency, 0.15 * top, "in/s", "top strafe speed: rollers lose some sideways"));
    }
}
