package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.truth.Truth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.function.DoublePredicate;

// Ramp and gates (team 2026-10-17; no hoods), plus a slow-motion camera that films them.
//   ramp: two continuous rotation servos (CrServo) wind strings on a spool that hold the ramp up. Its place is a share
//     from 0 (folded up, stowed) to 1 (out on its stop); its angle out from straight up follows the share. Gravity
//     pulls it out like a constant power (holdPower): a servo power the other way at least that big holds it up, less
//     lets it creep out (droop). With no power (no signal, or inside the deadband) the gears hold part of it, so it
//     creeps more slowly; the team says it does need a little power. Running out past the stop pays out slack string, which must be wound in again before
//     the ramp lifts. Pulling in hard against the up stop strains both servos. The second servo is mounted mirrored;
//     mounted the same way, the two fight (both buzz, the ramp hardly moves).
//   gate: arm angle from hanging straight down; + swings it up, away from the flywheel (opening). The back gate is one
//     arm driven by two servos, one each side (mirrored): where they disagree, the arm sits between and both buzz.
// Side views in inches. Ramp: x forward from the frame's front face, y up from the floor. Gate: x along the feed
// lane toward the flywheel (wheel centre at x = 0), y up from the lane floor.
public class ServoMechs implements Part {
    public static final String[] IDS = {"ramp.left", "ramp.right", "gate.front", "gate.back", "gate.back2"};
    static final int RAMP_L = 0, RAMP_R = 1, GATE = 2, BACK2 = 4;
    public static final double FRAME_IN = 18, LIMIT_IN = 24, FLOWER_FACE_IN = 0.25, FLOWER_OPENING_IN = 3.55;
    static final double ARM_HALF = 0.06, SQUEEZE = 0.3, FREE = 0.08, ROLL_IN_PER_S = 14;
    static final double TRAVEL_TOLERANCE_FRAMES = 2;
    // ramp: slack it can pay out before the string runs off the spool; power against the up stop that fully strains
    // the servos; how far out it may sag while the robot drives before it counts as drooping into the field
    static final double MAX_SLACK = 0.6, STRAIN_POWER = 0.4, DROOP_SHARE = 0.15, DROOP_SPEED_IN_S = 2;

    public final ServoModel[] servos = new ServoModel[IDS.length];
    public final SlowMoCamera camera = new SlowMoCamera();
    private final Random rng;

    final double rampPivotX, rampPivotY, rampLength, rampStow, rampDeploy;
    final double rampHold, rampSpeed, rampLimpHold;
    double rampShare, rampSlack, droopSeconds;
    // the back gate's arm as a slow-motion video sees it: its last opening, first movement to standing still
    double armRest = Double.NaN, armPrev = Double.NaN, armStart = Double.NaN, armLastMove, armOpenS = Double.NaN;
    final double elementR, wheelR, wheelY;
    final Gate[] gates = new Gate[2];

    static final class Gate {
        double pivotX, pivotY, arm, elementX;
        boolean element, passing, rubbed;
        String result = "";
    }

    public ServoMechs(Robot r, Truth t) {
        // java.util.Random gives nearly the same first numbers for seeds 1, 2, 3: scramble the seed first
        rng = new Random(new SplittableRandom(t.seed).nextLong());

        rampPivotX = range(t, "ramp.pivotXIn", -1.8, -1.3);
        rampPivotY = range(t, "ramp.pivotYIn", 3.9, 4.5);
        rampLength = range(t, "ramp.lengthIn", 7.0, 7.8);
        rampStow = range(t, "ramp.stowStopDeg", -18, -8);
        rampDeploy = range(t, "ramp.deployStopDeg", 132, 138);
        // gravity as a servo power, full-power speed (share per s, no gravity), and how much of gravity an unpowered
        // servo's gears hold by themselves (not in the CAD: model guesses; program 4 measures the first two)
        // the team's ramp rests up by itself (string tension): no sag unless a scenario sets one
        rampHold = t.num("ramp.holdPower", 0);
        rampSpeed = range(t, "ramp.fullSpeed", 0.9, 1.3);
        rampLimpHold = rampHold * range(t, "ramp.limpHoldShare", 0.4, 0.7);
        rampShare = t.num("ramp.startShare", 0);
        double dirL = rng.nextBoolean() ? 1 : -1;
        servos[RAMP_L] = new CrServo("ramp.left", t, rng, t.num("ramp.left.dir", dirL));
        servos[RAMP_R] = new CrServo("ramp.right", t, rng, t.num("ramp.right.dir", -((CrServo) servos[RAMP_L]).mount));

        elementR = t.num("element.diameterIn", 3.0) / 2;
        wheelR = t.num("gate.wheelDiameterIn", 72 / 25.4) / 2;
        wheelY = 2 * elementR + wheelR - range(t, "gate.compressionIn", 0.35, 0.45);
        // one gate is slower; which one changes with the robot
        double base = range(t, "gate.speedS60.base", 0.10, 0.12), slow = base * range(t, "gate.slowerFactor", 1.2, 1.45);
        boolean backSlower = t.bool("gate.backIsSlower", rng.nextBoolean());
        for (int i = 0; i < 2; i++) {
            String id = IDS[GATE + i];
            Gate g = new Gate();
            g.pivotX = range(t, id + ".pivotXIn", -0.3, -0.1);
            g.pivotY = range(t, id + ".pivotYIn", 4.5, 4.7);
            g.arm = range(t, id + ".armIn", 4.1, 4.3);
            gates[i] = g;
            double closed = range(t, id + ".closedStopDeg", -14, -10), open = range(t, id + ".openStopDeg", 80, 86);
            // the front and back gate servos are mounted mirrored
            servos[GATE + i] = new ServoModel(id, t, rng, (i == 1) == backSlower ? slow : base, i == 0 ? -1 : 1, 1,
                    rand(25, 45), closed, open, 10);
            if (i == 1) {
                // the back gate's second servo: the other side of the same arm, so mirrored again, its horn on the
                // spline wherever it went (never exactly 1 - p of the first)
                servos[BACK2] = new ServoModel("gate.back2", t, rng, base * range(t, "gate.back2.speedFactor", 0.95, 1.1), -1, 1,
                        rand(25, 45), closed, open, 10);
            }
        }

        for (ServoModel s : servos) r.addServo(s);
        camera.addChannel("ramp", this::rampOutDeg);
        for (int i = GATE; i < servos.length; i++) {
            ServoModel s = servos[i];
            camera.addChannel(s.id, s::out);
        }
    }

    private double rand(double lo, double hi) {
        return lo + rng.nextDouble() * (hi - lo);
    }

    private double range(Truth t, String path, double lo, double hi) {
        return t.num(path, rand(lo, hi));
    }

    @Override
    public String id() {
        return "servos";
    }

    // ---- what the launcher model uses (side 0 = front, 1 = back)

    public ServoModel servo(String id) {
        for (ServoModel s : servos) if (s.id.equals(id)) return s;
        throw new IllegalArgumentException(id);
    }

    ServoModel gate(int side) {
        return servos[GATE + side];
    }

    // 0 = closed (holds an element), 1 = open (an element passes freely); between: it squeezes past, rubbing
    public double gateOpen(int side) {
        double gap = tipY(side, gate(side).out());
        return Math.max(0, Math.min(1, (gap - (2 * elementR - SQUEEZE)) / (SQUEEZE + FREE)));
    }

    // the closed gate holds an element so far forward that it presses into the wheel
    public boolean gateRubs(int side) {
        double th = gate(side).out();
        return held(side, th) && clearance(heldX(side, th)) <= 0;
    }

    // 0 = folded up, 1 = out on its stop
    public double rampShare() {
        return rampShare;
    }

    // the two ramp servos mounted mirrored (as the code drives them)
    public boolean rampMirrored() {
        return ((CrServo) servos[RAMP_L]).mount != ((CrServo) servos[RAMP_R]).mount;
    }

    public double rampOutDeg() {
        return rampStow + rampShare * (rampDeploy - rampStow);
    }

    // ---- geometry

    double rampTipX(double out) {
        return rampPivotX + rampLength * Math.sin(Math.toRadians(out));
    }

    double rampTipY(double out) {
        return rampPivotY + rampLength * Math.cos(Math.toRadians(out));
    }

    boolean rampInside(double out) {
        return rampTipX(out) <= -0.25 && rampTipY(out) <= FRAME_IN;
    }

    // tip low in the FLOWER's retrieval opening, under its top edge, and inside 18 x 24 in
    boolean rampAtFlower(double out) {
        double x = rampTipX(out), y = rampTipY(out), a = Math.toRadians(90 - out);
        double yAtFace = rampPivotY + (FLOWER_FACE_IN - rampPivotX) * Math.tan(a);
        return y >= 0.1 && y <= 1.5 && x >= FLOWER_FACE_IN + 1.25 && x <= LIMIT_IN - FRAME_IN && yAtFace <= FLOWER_OPENING_IN - 0.1;
    }

    // how far out it can get: its stop on a box, the floor on the field
    double rampShareMax(World w) {
        if (w.robot.onBox) return 1;
        double floorDeg = 90 - Math.toDegrees(Math.asin(-rampPivotY / rampLength));
        return Math.max(0, Math.min(1, (floorDeg - rampStow) / (rampDeploy - rampStow)));
    }

    double tipX(int side, double th) {
        return gates[side].pivotX - gates[side].arm * Math.sin(Math.toRadians(th));
    }

    double tipY(int side, double th) {
        return gates[side].pivotY - gates[side].arm * Math.cos(Math.toRadians(th));
    }

    boolean held(int side, double th) {
        return tipY(side, th) < 2 * elementR - SQUEEZE;
    }

    boolean free(int side, double th) {
        return tipY(side, th) >= 2 * elementR + FREE;
    }

    // centre of an element pushed down the lane against the arm (its face, or its tip once the face is too short)
    double heldX(int side, double th) {
        Gate g = gates[side];
        double a = Math.toRadians(th), r = elementR;
        double x = g.pivotX - (r + ARM_HALF + (g.pivotY - r) * Math.sin(a)) / Math.cos(a);
        double along = -(x - g.pivotX) * Math.sin(a) + (g.pivotY - r) * Math.cos(a);
        if (along >= 0 && along <= g.arm) return x;
        double h = tipY(side, th);
        return tipX(side, th) - Math.sqrt(Math.max(0, r * r - (h - r) * (h - r))) - ARM_HALF;
    }

    double wheelTouchX() {
        double s = elementR + wheelR, dy = wheelY - elementR;
        return -Math.sqrt(s * s - dy * dy);
    }

    double clearance(double x) {
        return Math.hypot(x, wheelY - elementR) - elementR - wheelR;
    }

    // arm angle where a held element first touches the wheel
    double touchDeg(int side) {
        double lo = gate(side).outStopLow - 5, hi = 45, xt = wheelTouchX();
        for (int k = 0; k < 50; k++) {
            double m = (lo + hi) / 2;
            if (heldX(side, m) > xt) lo = m;
            else hi = m;
        }
        return hi;
    }

    // ---- physics

    @Override
    public void step(double dt, World w) {
        for (int i = GATE; i < servos.length; i++) {
            ServoModel s = servos[i];
            s.outMin = s.outStopLow;
            s.outMax = s.outStopHigh;
        }
        for (int i = 0; i < 2; i++) {
            Gate g = gates[i];
            // an element squeezed against the wheel stops the arm (it gives a little)
            if (g.element && !g.passing && held(i, gate(i).out())) {
                double m = touchDeg(i) - 0.6;
                gate(i).outMin = Math.max(gate(i).outMin, m);
                if (i == 1) servos[BACK2].outMin = Math.max(servos[BACK2].outMin, m);
            }
        }
        // the back gate's two servos: each pushed by how far its command is from the other's
        ServoModel a = servos[GATE + 1], b = servos[BACK2];
        double apart = a.powered() && b.powered() ? Math.abs(a.outOf(a.targetDeg) - b.outOf(b.targetDeg)) : 0;
        double push = Math.max(0, Math.min(1, (apart / 2 / Math.min(a.gain, b.gain) - ServoModel.DEADBAND_DEG) / ServoModel.P_BAND_DEG));
        a.extraLoad = push;
        b.extraLoad = push;
        rampStep(dt, w);
        for (ServoModel s : servos) s.step(dt, w.time, w.robot.battery);
        // one arm: where both drive it, between them; a limp one is carried along
        if (a.powered() && b.powered()) {
            double arm = (a.out() + b.out()) / 2;
            a.angleDeg = a.horn(arm);
            b.angleDeg = b.horn(arm);
        } else if (a.powered()) {
            b.angleDeg = b.horn(a.out());
        } else if (b.powered()) {
            a.angleDeg = a.horn(b.out());
        }
        armTrack(a.out(), w.time + dt);
        for (int i = 0; i < 2; i++) elementStep(i, dt);
        camera.sample(w.time + dt);
    }

    private void armTrack(double arm, double now) {
        if (Double.isNaN(armPrev)) armRest = armPrev = arm;
        if (Double.isNaN(armStart)) {
            if (Math.abs(arm - armRest) > 0.02) {
                armStart = now;
                armLastMove = now;
            } else {
                armRest = arm;
            }
        } else if (Math.abs(arm - armPrev) > 0.005) {
            armLastMove = now;
        } else if (now - armLastMove > 0.05) {
            if (arm > armRest + 5) armOpenS = armLastMove - armStart;
            armStart = Double.NaN;
            armRest = arm;
        }
        armPrev = arm;
    }

    private void rampStep(double dt, World w) {
        CrServo l = (CrServo) servos[RAMP_L], r = (CrServo) servos[RAMP_R];
        double pl = l.power(), pr = r.power();
        double u = (pl + pr) / 2;
        double fight = pl * pr < 0 ? Math.min(Math.abs(pl), Math.abs(pr)) : 0;
        double g = u != 0 ? rampHold : Math.max(0, rampHold - rampLimpHold);
        double net = u + g, max = rampShareMax(w), strain = 0;
        if (rampSlack > 0) {
            rampSlack = Math.max(0, Math.min(MAX_SLACK, rampSlack + rampSpeed * u * dt));
        } else if (rampShare >= max - 1e-9 && net >= 0) {
            rampShare = max;
            if (u > 0) rampSlack = Math.min(MAX_SLACK, rampSlack + rampSpeed * u * dt);
        } else {
            rampShare = Math.max(0, Math.min(max, rampShare + rampSpeed * net * dt));
            if (rampShare <= 0 && net < 0) strain = Math.min(1, -net / STRAIN_POWER);
        }
        l.extraLoad = Math.max(strain, fight);
        r.extraLoad = Math.max(strain, fight);
        Drive d = w.robot.drive;
        if (!w.robot.onBox && rampShare > DROOP_SHARE && u <= 0 && Math.hypot(d.fwdVel, d.leftVel) > DROOP_SPEED_IN_S) droopSeconds += dt;
    }

    private void elementStep(int i, double dt) {
        Gate g = gates[i];
        if (!g.element) return;
        double th = gate(i).out();
        if (g.passing) {
            g.elementX += ROLL_IN_PER_S * dt;
            if (!free(i, th) && Math.abs(g.elementX - tipX(i, th)) < elementR) g.rubbed = true;
            if (g.elementX > 0) {
                g.element = false;
                g.passing = false;
                g.result = g.rubbed ? "The element squeezed under the gate, rubbing it, and went into the flywheel."
                        : "The element rolled under the gate freely and went into the flywheel.";
            }
        } else if (!held(i, th)) {
            g.passing = true;
            g.rubbed = !free(i, th);
        } else {
            g.elementX = Math.min(heldX(i, th), wheelTouchX());
        }
    }

    // ---- the student's hands

    @Override
    public boolean hand(String action, JsonObject a, World w) {
        int side = a.has("gate") ? a.get("gate").getAsInt() : 0;
        switch (action) {
            case "placeElement": {
                Gate g = gates[side];
                g.element = true;
                g.passing = false;
                g.rubbed = false;
                g.result = "";
                double th = gate(side).out();
                g.elementX = held(side, th) ? Math.min(heldX(side, th), wheelTouchX()) : tipX(side, th) - elementR - 1;
                return true;
            }
            case "removeElement":
                gates[side].element = false;
                gates[side].passing = false;
                gates[side].result = "";
                return true;
            case "replaceServo":
                servo(a.get("id").getAsString()).replace(rng);
                return true;
            case "record":
                camera.record(a.has("on") && a.get("on").getAsBoolean(), w.time);
                return true;
            case "stowRamp":
                // folded up by hand (the ramp must be stowed when the robot is placed)
                rampShare = 0;
                rampSlack = 0;
                return true;
            case "remountRampRight":
                // the second ramp servo turned round on its mount: now mirrored to the first
                ((CrServo) servos[RAMP_R]).mount = -((CrServo) servos[RAMP_L]).mount;
                return true;
            default:
                return false;
        }
    }

    // ---- Workbench

    @Override
    public void state(Map<String, Object> out) {
        for (int i = 0; i < servos.length; i++) {
            ServoModel s = servos[i];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("powered", s.powered());
            m.put("heat", s.heat);
            m.put("burnedOut", s.burnedOut);
            m.put("buzz", s.load);
            m.put("amps", s.amps);
            m.put("feel", s.feel());
            m.put("replaced", s.replaced);
            if (s instanceof CrServo) {
                m.put("power", ((CrServo) s).power());
            } else {
                m.put("deg", s.angleDeg);
                m.put("stalled", s.stalled());
                m.put("stopLowDeg", s.stopLowDeg());
                m.put("stopHighDeg", s.stopHighDeg());
                m.put("out", s.out());
                m.put("outStopLow", s.outStopLow);
                m.put("outStopHigh", s.outStopHigh);
            }
            if (i >= GATE) {
                int side = i == BACK2 ? 1 : i - GATE;
                Gate g = gates[side];
                double th = s.out();
                m.put("pivotX", g.pivotX);
                m.put("pivotY", g.pivotY);
                m.put("arm", g.arm);
                m.put("tipX", tipX(side, th));
                m.put("tipY", tipY(side, th));
                m.put("element", g.element);
                m.put("elementX", g.elementX);
                m.put("passing", g.passing);
                m.put("pressed", g.element && !g.passing && g.elementX >= wheelTouchX() - 1e-6);
                m.put("result", g.result);
                m.put("open", gateOpen(side));
                m.put("clearanceIn", clearance(Math.min(heldX(side, th), wheelTouchX())));
                m.put("gapIn", tipY(side, th) - 2 * elementR);
                m.put("holds", held(side, th));
                m.put("passesFreely", free(side, th));
                m.put("lastOpenS", s.lastOpenSeconds);
            }
            out.put(s.id, m);
        }
        Map<String, Object> ramp = new LinkedHashMap<>();
        double o = rampOutDeg();
        ramp.put("share", rampShare);
        ramp.put("outDeg", o);
        ramp.put("slack", rampSlack);
        ramp.put("pivotX", rampPivotX);
        ramp.put("pivotY", rampPivotY);
        ramp.put("length", rampLength);
        ramp.put("tipX", rampTipX(o));
        ramp.put("tipY", rampTipY(o));
        ramp.put("inside", rampInside(o));
        ramp.put("atFlower", rampAtFlower(o));
        ramp.put("droopSeconds", droopSeconds);
        double pl = ((CrServo) servos[RAMP_L]).power(), pr = ((CrServo) servos[RAMP_R]).power();
        ramp.put("fighting", pl * pr < 0);
        out.put("ramp", ramp);
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("elementR", elementR);
        geo.put("wheelR", wheelR);
        geo.put("wheelY", wheelY);
        geo.put("frameIn", FRAME_IN);
        geo.put("limitIn", LIMIT_IN);
        geo.put("flowerFaceIn", FLOWER_FACE_IN);
        geo.put("flowerOpeningIn", FLOWER_OPENING_IN);
        out.put("geometry", geo);
        out.put("cameraRecording", camera.recording());
    }

    // ---- report card: programs 2 and 4 keys

    @Override
    public void expected(Map<String, Expected> out, World w) {
        String[] side = {"front", "back"};
        double db = ServoModel.QUIET_DEG;

        boolean reversed = ((CrServo) servos[RAMP_L]).mount < 0;
        out.put("rampReversed", new Expected(reversed ? 1 : 0, 0, "0/1", reversed
                ? "+power on the left ramp servo pulls the ramp in: Ramp.RAMP_REVERSED = true"
                : "+power on the left ramp servo lets the ramp out: Ramp.RAMP_REVERSED = false"));
        double outS = 1 / (rampSpeed * (1 + rampHold)), inS = 1 / (rampSpeed * (1 - rampHold));
        double tol = (TRAVEL_TOLERANCE_FRAMES + 1) / SlowMoCamera.FPS;
        out.put("rampOutS", new Expected(outS, tol, "s", String.format(Locale.US,
                "%.1f frames at 240 fps from folded up to fully out at full power (gravity helps)", outS * SlowMoCamera.FPS)));
        out.put("rampInS", new Expected(inS, tol, "s", String.format(Locale.US,
                "%.1f frames at 240 fps from fully out to folded up at full power (against gravity)", inS * SlowMoCamera.FPS)));

        double[] closed = new double[3], open = new double[3];
        for (int i = 0; i < 2; i++) {
            ServoModel s = gate(i);
            int k = i;
            Expected c = band(s, o -> o >= s.outStopLow - db && tipY(k, o) <= 2 * elementR - SQUEEZE - 0.25
                            && clearance(heldX(k, o)) >= 0.06 && clearance(heldX(k, o)) <= 0.75, String.format(Locale.US,
                    "%s gate%s: a held element touches the flywheel at position %.3f. Good: holds an element 0.06-0.75 in clear of the flywheel.",
                    cap(side[i]), i == 1 ? " servo 1" : "", s.position(touchDeg(i))));
            Expected o2 = band(s, o -> free(k, o) && o <= s.outStopHigh + db, String.format(Locale.US,
                    "%s gate%s open stop at position %.3f. Good: an element passes under freely, not buzzing on the stop.",
                    cap(side[i]), i == 1 ? " servo 1" : "", s.position(s.outStopHigh)));
            out.put(side[i] + "GateClosed", c);
            out.put(side[i] + "GateOpen", o2);
            closed[i] = outAt(s, c.value);
            open[i] = outAt(s, o2.value);
        }
        // servo 2 matched to servo 1 at its (expected) closed and open angles: anything that does not buzz, plus the room
        // servo 1's own band leaves
        ServoModel s1 = gate(1), s2 = servos[BACK2];
        String[] key = {"Closed", "Open"};
        for (int k = 0; k < 2; k++) {
            double arm = k == 0 ? closed[1] : open[1];
            Expected e1 = out.get("backGate" + key[k]);
            double spread1 = Math.abs(outAt(s1, e1.value + e1.tolerance) - arm) + (ServoModel.DEADBAND_DEG + ServoModel.BUZZ * ServoModel.P_BAND_DEG) * s2.gain;
            double c2 = s2.position(arm);
            double tol2 = Math.abs(s2.position(arm + spread1) - c2);
            out.put("backGate2" + key[k], new Expected(c2, tol2, "position", String.format(Locale.US,
                    "back gate servo 2 at the arm angle servo 1 holds %s (%.1f deg): position %.3f. Good: neither servo buzzes.",
                    key[k].toLowerCase(Locale.US), arm, c2)));
            if (k == 0) closed[2] = arm;
            else open[2] = arm;
        }
        double travel = 0;
        String slower = "";
        for (int i = GATE; i < servos.length; i++) {
            ServoModel s = servos[i];
            int k = i - GATE;
            // the back gate's two servos move one arm: time what the arm did
            double own = i == GATE ? s.lastOpenSeconds : armOpenS;
            double tt = !Double.isNaN(own) ? own : s.travelSeconds(closed[k], open[k]);
            if (tt > travel) {
                travel = tt;
                slower = s.id;
            }
        }
        out.put("gateTravelS", new Expected(travel, TRAVEL_TOLERANCE_FRAMES / SlowMoCamera.FPS, "s", String.format(Locale.US,
                "%s is the slowest: %.1f frames at 240 fps from its first movement to fully open (the last time it opened).",
                slower, travel * SlowMoCamera.FPS)));
    }

    private double outAt(ServoModel s, double position) {
        return s.outOf((position * 1800 + 600 - 1500) * s.degPerUs + s.offsetDeg);
    }

    // the widest run of mechanism angles that are good, as a band of servo positions
    private Expected band(ServoModel s, DoublePredicate ok, String how) {
        double bestLo = Double.NaN, bestHi = Double.NaN, runLo = Double.NaN;
        double from = s.outStopLow - 3, to = s.outStopHigh + 3, step = 0.02;
        for (double o = from; o <= to + step; o += step) {
            if (o <= to && ok.test(o)) {
                if (Double.isNaN(runLo)) runLo = o;
            } else if (!Double.isNaN(runLo)) {
                if (Double.isNaN(bestLo) || o - step - runLo > bestHi - bestLo) {
                    bestLo = runLo;
                    bestHi = o - step;
                }
                runLo = Double.NaN;
            }
        }
        if (Double.isNaN(bestLo)) return new Expected(Double.NaN, 0, "position", how + " No position is good on this robot: fix the mechanism.");
        double a = s.position(bestLo), b = s.position(bestHi);
        return new Expected((a + b) / 2, Math.abs(a - b) / 2, "position", how);
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
