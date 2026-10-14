package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.truth.Truth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;
import java.util.function.DoublePredicate;

// Hoods, ramp and gates: one servo each, driving a linkage between two mechanical stops (ServoModel), plus a
// slow-motion camera that films them. Mechanism angles ("out"):
//   hood: launch angle above horizontal; the hood rotates about the flywheel axle between a low and a high stop.
//   ramp: degrees out from straight up (stowed leans back a little, deployed points down at the floor).
//   gate: arm angle from hanging straight down; + swings it up, away from the flywheel (opening).
// Side views in inches. Ramp: x forward from the frame's front face, y up from the floor. Gate: x along the feed
// lane toward the flywheel (wheel centre at x = 0), y up from the lane floor.
public class ServoMechs implements Part {
    public static final String[] IDS = {"hood.front", "hood.back", "ramp", "gate.front", "gate.back"};
    public static final double FRAME_IN = 18, LIMIT_IN = 24, FLOWER_FACE_IN = 0.25, FLOWER_OPENING_IN = 3.55;
    static final double HOOD_MARGIN = 2.5, ARM_HALF = 0.06, SQUEEZE = 0.3, FREE = 0.08, ROLL_IN_PER_S = 14;
    static final double TRAVEL_TOLERANCE_FRAMES = 2;

    public final ServoModel[] servos = new ServoModel[IDS.length];
    public final SlowMoCamera camera = new SlowMoCamera();
    private final Random rng;

    final double rampPivotX, rampPivotY, rampLength;
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
        for (int i = 0; i < 2; i++) {
            String id = IDS[i];
            double low = range(t, id + ".lowStopDeg", 22, 27), high = range(t, id + ".highStopDeg", 53, 58);
            servos[i] = new ServoModel(id, t, rng, rand(0.13, 0.17), 1,
                    rand(0.24, 0.30), rand(38, 44), low, high, low);
        }

        rampPivotX = range(t, "ramp.pivotXIn", -1.8, -1.3);
        rampPivotY = range(t, "ramp.pivotYIn", 3.9, 4.5);
        rampLength = range(t, "ramp.lengthIn", 7.0, 7.8);
        double stow = range(t, "ramp.stowStopDeg", -18, -8), deploy = range(t, "ramp.deployStopDeg", 132, 138);
        servos[2] = new ServoModel("ramp", t, rng, rand(0.22, 0.28), 1, 1,
                rand(50, 70), stow, deploy, stow);

        elementR = t.num("element.diameterIn", 3.0) / 2;
        wheelR = t.num("gate.wheelDiameterIn", 96 / 25.4) / 2;
        wheelY = 2 * elementR + wheelR - range(t, "gate.compressionIn", 0.35, 0.45);
        // one gate is slower; which one changes with the robot
        double base = range(t, "gate.speedS60.base", 0.10, 0.12), slow = base * range(t, "gate.slowerFactor", 1.2, 1.45);
        boolean backSlower = t.bool("gate.backIsSlower", rng.nextBoolean());
        for (int i = 0; i < 2; i++) {
            String id = IDS[3 + i];
            Gate g = new Gate();
            g.pivotX = range(t, id + ".pivotXIn", -0.3, -0.1);
            g.pivotY = range(t, id + ".pivotYIn", 4.5, 4.7);
            g.arm = range(t, id + ".armIn", 4.1, 4.3);
            gates[i] = g;
            double closed = range(t, id + ".closedStopDeg", -14, -10), open = range(t, id + ".openStopDeg", 80, 86);
            // the two gate servos are mounted mirrored
            servos[3 + i] = new ServoModel(id, t, rng, (i == 1) == backSlower ? slow : base, i == 0 ? -1 : 1, 1,
                    rand(25, 45), closed, open, 10);
        }

        for (ServoModel s : servos) {
            r.addServo(s);
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

    public double hoodLaunchDeg(int side) {
        return servos[side].out();
    }

    // 0 = closed (holds an element), 1 = open (an element passes freely); between: it squeezes past, rubbing
    public double gateOpen(int side) {
        double gap = tipY(side, servos[3 + side].out());
        return Math.max(0, Math.min(1, (gap - (2 * elementR - SQUEEZE)) / (SQUEEZE + FREE)));
    }

    // the closed gate holds an element so far forward that it presses into the wheel
    public boolean gateRubs(int side) {
        double th = servos[3 + side].out();
        return held(side, th) && clearance(heldX(side, th)) <= 0;
    }

    public double rampDeg() {
        return servos[2].out() - servos[2].outStopLow;
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
        double lo = servos[3 + side].outStopLow - 5, hi = 45, xt = wheelTouchX();
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
        for (ServoModel s : servos) {
            s.outMin = s.outStopLow;
            s.outMax = s.outStopHigh;
        }
        ServoModel ramp = servos[2];
        if (!w.robot.onBox) ramp.outMax = Math.min(ramp.outMax, 90 - Math.toDegrees(Math.asin(-rampPivotY / rampLength)));
        for (int i = 0; i < 2; i++) {
            Gate g = gates[i];
            ServoModel s = servos[3 + i];
            // an element squeezed against the wheel stops the arm (it gives a little)
            if (g.element && !g.passing && held(i, s.out())) s.outMin = Math.max(s.outMin, touchDeg(i) - 0.6);
        }
        for (ServoModel s : servos) s.step(dt, w.time, w.robot.battery);
        for (int i = 0; i < 2; i++) elementStep(i, dt);
        camera.sample(w.time + dt);
    }

    private void elementStep(int i, double dt) {
        Gate g = gates[i];
        if (!g.element) return;
        double th = servos[3 + i].out();
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
                double th = servos[3 + side].out();
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
            m.put("deg", s.angleDeg);
            m.put("powered", s.powered());
            m.put("stalled", s.stalled());
            m.put("heat", s.heat);
            m.put("burnedOut", s.burnedOut);
            m.put("stopLowDeg", s.stopLowDeg());
            m.put("stopHighDeg", s.stopHighDeg());
            m.put("out", s.out());
            m.put("outStopLow", s.outStopLow);
            m.put("outStopHigh", s.outStopHigh);
            m.put("buzz", s.load);
            m.put("amps", s.amps);
            m.put("feel", s.feel());
            m.put("replaced", s.replaced);
            if (i == 2) {
                m.put("pivotX", rampPivotX);
                m.put("pivotY", rampPivotY);
                m.put("length", rampLength);
                m.put("tipX", rampTipX(s.out()));
                m.put("tipY", rampTipY(s.out()));
                m.put("inside", rampInside(s.out()));
                m.put("atFlower", rampAtFlower(s.out()));
            } else if (i >= 3) {
                int side = i - 3;
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

    // ---- report card: program 4 keys, in servo positions (0-1)

    @Override
    public void expected(Map<String, Expected> out, World w) {
        String[] side = {"front", "back"};
        double[] closed = new double[2], open = new double[2];
        for (int i = 0; i < 2; i++) {
            ServoModel s = servos[i];
            double db = ServoModel.QUIET_DEG * s.gain;
            out.put(side[i] + "HoodMin", band(s, o -> o >= s.outStopLow - db && o <= s.outStopLow + HOOD_MARGIN, String.format(Locale.US,
                    "%s hood low stop at position %.3f (launch angle %.1f deg). Good: resting on it, not buzzing, at most %.1f deg above it.",
                    cap(side[i]), s.position(s.outStopLow), s.outStopLow, HOOD_MARGIN)));
            out.put(side[i] + "HoodMax", band(s, o -> o <= s.outStopHigh + db && o >= s.outStopHigh - HOOD_MARGIN, String.format(Locale.US,
                    "%s hood high stop at position %.3f (launch angle %.1f deg). Good: on it, not buzzing, at most %.1f deg below it.",
                    cap(side[i]), s.position(s.outStopHigh), s.outStopHigh, HOOD_MARGIN)));
        }
        ServoModel ramp = servos[2];
        double db = ServoModel.QUIET_DEG;
        out.put("rampStowed", band(ramp, o -> o >= ramp.outStopLow - db && o <= ramp.outStopLow + 6 && rampInside(o), String.format(Locale.US,
                "Ramp stowed stop at position %.3f (%.1f deg past upright). Good: on or near the stop, not buzzing, tip inside the 18 in frame.",
                ramp.position(ramp.outStopLow), -ramp.outStopLow)));
        out.put("rampDeployed", band(ramp, o -> o <= ramp.outStopHigh + db && rampAtFlower(o), String.format(Locale.US,
                "Ramp deploy stop at position %.3f. Good: tip 0.1-1.5 in off the floor, inside the FLOWER opening and inside 18 x 24 in.",
                ramp.position(ramp.outStopHigh))));
        for (int i = 0; i < 2; i++) {
            ServoModel s = servos[3 + i];
            int k = i;
            Expected c = band(s, o -> o >= s.outStopLow - db && tipY(k, o) <= 2 * elementR - SQUEEZE - 0.25
                            && clearance(heldX(k, o)) >= 0.06 && clearance(heldX(k, o)) <= 0.75, String.format(Locale.US,
                    "%s gate: a held element touches the flywheel at position %.3f. Good: holds an element 0.06-0.75 in clear of the flywheel.",
                    cap(side[i]), s.position(touchDeg(i))));
            Expected o2 = band(s, o -> free(k, o) && o <= s.outStopHigh + db, String.format(Locale.US,
                    "%s gate open stop at position %.3f. Good: an element passes under freely, not buzzing on the stop.",
                    cap(side[i]), s.position(s.outStopHigh)));
            out.put(side[i] + "GateClosed", c);
            out.put(side[i] + "GateOpen", o2);
            closed[i] = outAt(s, c.value);
            open[i] = outAt(s, o2.value);
        }
        double travel = 0;
        int slower = 0;
        for (int i = 0; i < 2; i++) {
            ServoModel s = servos[3 + i];
            double tt = !Double.isNaN(s.lastOpenSeconds) ? s.lastOpenSeconds : s.travelSeconds(closed[i], open[i]);
            if (tt > travel) {
                travel = tt;
                slower = i;
            }
        }
        out.put("gateTravelS", new Expected(travel, TRAVEL_TOLERANCE_FRAMES / SlowMoCamera.FPS, "s", String.format(Locale.US,
                "The %s gate is slower: %.1f frames at 240 fps from its first movement to fully open (the last time it opened).",
                side[slower], travel * SlowMoCamera.FPS)));
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
