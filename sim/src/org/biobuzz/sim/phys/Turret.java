package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.truth.Truth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.SplittableRandom;

// Two turrets, one yaw servo each (team 2026-10-17; the part is not chosen yet, so a position servo like the gates:
// ServoModel). Each servo turns its turret through a gear: turret degrees = gain x horn degrees, and which way depends
// on how it is mounted (dir). Turret angle ("out"): 0 = barrel straight forward, + = left. The horn sits on the spline
// a little off (refDeg), so straight forward is not exactly the servo's middle. Each turret has a hard stop each way
// and a cable that pulls tight somewhere before it; turned past the tight point the cable stretches and is damaged.
// Without a signal the servo is limp and the turret turns by hand.
public class Turret implements Part {
    public static final String[] IDS = {"yaw.front", "yaw.back"};
    // program 3 keeps the limits this far inside the far ends
    public static final double LIMIT_MARGIN_DEG = 5;

    public final ServoModel[] servos = new ServoModel[2];
    final double[] stopMax = new double[2], stopMin = new double[2];
    final double[] cableTightMax = new double[2], cableTightMin = new double[2];
    final double cableStretch;
    final boolean[] cableDamaged = new boolean[2];
    final String[] contact = new String[2];

    public Turret(Robot r, Truth t) {
        Random rng = new Random(new SplittableRandom(t.seed * 31 + 7).nextLong());
        cableStretch = t.num("turret.cableStretchDeg", 20);
        for (int i = 0; i < 2; i++) {
            String p = i == 0 ? "turret.front" : "turret.back";
            stopMax[i] = pick(t, rng, p + "StopMaxDeg", "turret.stopMaxDeg", 150, 170);
            stopMin[i] = pick(t, rng, p + "StopMinDeg", "turret.stopMinDeg", -170, -150);
            cableTightMax[i] = pick(t, rng, p + "CableTightMaxDeg", i == 0 ? "turret.cableTightMaxDeg" : null, 175, 200);
            cableTightMin[i] = pick(t, rng, p + "CableTightMinDeg", null, -200, -175);
            // CAD v1 gear trains: front 3 x 36/95 = 1.137, back 3 x 25/68 = 1.103 turret deg per servo deg
            double gain = i == 0 ? pick(t, rng, p + "Gain", null, 1.12, 1.15) : pick(t, rng, p + "Gain", null, 1.09, 1.12);
            double dir = t.num(p + "Dir", rng.nextBoolean() ? 1 : -1);
            double ref = pick(t, rng, p + "RefDeg", null, -8, 8);
            // goBILDA / Axon class servo under a turret's load, horn seconds per 60 deg
            double speed = pick(t, rng, p + "SpeedS60", "turret.speedS60", 0.11, 0.14);
            servos[i] = new ServoModel(IDS[i], t, rng, speed, dir, gain, ref, stopMin[i], stopMax[i], t.num("turret.startDeg", rng.nextDouble() * 12 - 6));
            r.addServo(servos[i]);
        }
    }

    // scenario value for this turret, else one for both turrets, else random in [lo, hi]
    private static double pick(Truth t, Random rng, String own, String both, double lo, double hi) {
        double v = lo + rng.nextDouble() * (hi - lo);
        return t.num(own, both == null ? v : t.num(both, v));
    }

    // the slow-motion camera films both turrets (program 3 times their swing)
    public void film(SlowMoCamera camera) {
        for (ServoModel s : servos) camera.addChannel(s.id, s::out);
    }

    @Override
    public String id() {
        return "turret";
    }

    public double frontDeg() {
        return servos[0].out();
    }

    public double backDeg() {
        return servos[1].out();
    }

    @Override
    public void step(double dt, World w) {
        for (int i = 0; i < 2; i++) {
            ServoModel s = servos[i];
            // a cable pulled past its slack holds the turret like a stop (and is damaged)
            s.outMin = Math.max(s.outStopLow, cableTightMin[i] - cableStretch);
            s.outMax = Math.min(s.outStopHigh, cableTightMax[i] + cableStretch);
            s.step(dt, w.time, w.robot.battery);
            double a = s.out();
            contact[i] = a >= s.outMax - 1e-6 ? "left" : a <= s.outMin + 1e-6 ? "right" : null;
            if (a > cableTightMax[i] + 1 || a < cableTightMin[i] - 1) cableDamaged[i] = true;
        }
    }

    // where the turret can really get to: the hard stop, the tight cable, or the end of the servo's range
    double leftLimit(int i) {
        return Math.min(Math.min(stopMax[i], cableTightMax[i]), servoEnd(i, true));
    }

    double rightLimit(int i) {
        return Math.max(Math.max(stopMin[i], cableTightMin[i]), servoEnd(i, false));
    }

    // the turret angle at servo position 0 or 1 (the SDK's default 600-2400 us), whichever is that way
    private double servoEnd(int i, boolean left) {
        ServoModel s = servos[i];
        double a = s.outOf(hornAt(s, 600)), b = s.outOf(hornAt(s, 2400));
        return left ? Math.max(a, b) : Math.min(a, b);
    }

    private static double hornAt(ServoModel s, double us) {
        return (us - 1500) * s.degPerUs + s.offsetDeg;
    }

    double cableTension(int i) {
        double a = servos[i].out(), past = Math.max(a - cableTightMax[i], cableTightMin[i] - a);
        return Math.max(0, Math.min(1, past / cableStretch));
    }

    @Override
    public void state(Map<String, Object> out) {
        out.put("frontDeg", frontDeg());
        out.put("backDeg", backDeg());
        for (int i = 0; i < 2; i++) {
            ServoModel s = servos[i];
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("deg", s.out());
            m.put("powered", s.powered());
            m.put("rateDegS", s.velocity * s.dir * s.gain);
            m.put("atStop", contact[i]);
            m.put("cableTension", cableTension(i));
            m.put("cableSide", s.out() > 0 ? "left" : "right");
            m.put("cableDamaged", cableDamaged[i]);
            m.put("buzz", s.load);
            m.put("feel", s.feel());
            m.put("leftLimitDeg", leftLimit(i));
            m.put("rightLimitDeg", rightLimit(i));
            m.put("leftIs", state(i, true));
            m.put("rightIs", state(i, false));
            out.put(i == 0 ? "front" : "back", m);
        }
    }

    // turn {deg, back}: turn that turret by hand (only while its servo has no signal: a powered servo holds it);
    // release: nothing to let go of; fixCable {back}: re-route a tight cable past the hard stops
    @Override
    public boolean hand(String action, JsonObject a, World w) {
        int i = a.has("back") && a.get("back").getAsBoolean() ? 1 : 0;
        ServoModel s = servos[i];
        switch (action) {
            case "turn":
                if (s.powered()) return false;
                s.angleDeg = s.horn(Math.max(s.outStopLow, Math.min(s.outStopHigh, s.out() + a.get("deg").getAsDouble())));
                return true;
            case "release":
                return true;
            case "fixCable":
                cableTightMax[i] = Math.max(cableTightMax[i], stopMax[i] + 20);
                cableTightMin[i] = Math.min(cableTightMin[i], stopMin[i] - 20);
                cableDamaged[i] = false;
                return true;
            default:
                return false;
        }
    }

    // program 3's answer key, per turret: forward position, position per degree, the far ends LIMIT_MARGIN_DEG inside
    // what it can reach, and the slower turret's speed over a straight right -> straight left swing
    @Override
    public void expected(Map<String, Expected> out, World w) {
        double slowest = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 2; i++) {
            ServoModel s = servos[i];
            String side = i == 0 ? "front" : "back";
            double fwd = s.position(0), perDeg = s.position(1) - s.position(0);
            out.put(side + "YawForward", new Expected(fwd, 0.006, "position", "servo position that points the " + side + " turret straight forward"));
            out.put(side + "YawPerDeg", new Expected(perDeg, 0.02 * Math.abs(perDeg), "position/deg",
                    "servo position per turret degree (+ = left), from straight left minus straight right over 180"));
            out.put(side + "YawMaxDeg", new Expected(leftLimit(i) - LIMIT_MARGIN_DEG, 4, "deg",
                    "left " + state(i, true) + " minus " + (int) LIMIT_MARGIN_DEG + " deg"));
            out.put(side + "YawMinDeg", new Expected(rightLimit(i) + LIMIT_MARGIN_DEG, 4, "deg",
                    "right " + state(i, false) + " plus " + (int) LIMIT_MARGIN_DEG + " deg"));
            slowest = Math.min(slowest, 180 / s.travelSeconds(-90, 90));
        }
        out.put("yawDegPerS", new Expected(slowest, 0.12 * slowest, "deg/s", "the slower turret's straight right to straight left swing: 180 deg over its time"));
    }

    private String state(int i, boolean left) {
        double lim = left ? leftLimit(i) : rightLimit(i);
        return lim == (left ? stopMax[i] : stopMin[i]) ? "hard stop" : lim == (left ? cableTightMax[i] : cableTightMin[i]) ? "cable tight" : "servo end";
    }
}
