package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.truth.Truth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

// Intake roller and the element path behind it, two motors (team 2026-10-17). The main motor's roller pulls an
// element in from the mouth, the channel carries it to a passive size sorter (NECTAR up the ramp to the front lane,
// POLLEN under it to the back lane) and pushes the front lane against its gate. In the back lane the main drive only
// pushes POLLEN as far as the 2 POLLEN feeder wheels (second motor); from there the feeder wheels carry it to the back
// gate and, open, into the back flywheel. inSign / pollenInSign: +1 means positive rotation moves elements in.
public class Intake implements Part {
    public static final int CAPACITY = 4;
    // path positions (inches from the roller): mouth, sorter, each lane's gate, gate to flywheel contact
    public static final double MOUTH_S = -2.2, SORT_S = 5;
    public static final double[] GATE_S = {13, 17};
    public static final double GAP_IN = 1.2;
    // how open a gate must be before each element squeezes through (fraction of ServoMechs.gateOpen)
    private static final double NECTAR_PASS = 0.8, POLLEN_PASS = 0.65;
    private static final double SLIP_TORQUE = 0.04;

    // where the POLLEN feeder wheels take over in the back lane (inches before the back gate)
    public static final double POLLEN_WHEELS_BEFORE_GATE_IN = 4.5;

    public final Motor motor, pollenMotor;
    public final Joint roller = new Joint(), pollenWheels = new Joint();
    public final int inSign, pollenInSign;
    public final double rollerRadiusIn, grip, pollenRadiusIn;
    public final double pollenMassKg, nectarMassKg;
    public final List<Element> elements = new ArrayList<>();
    public int spatOut, blocked, blockedPollen;
    private final double[] gateOpen = new double[2];
    private final boolean[] gateRubs = new boolean[2];
    private final Random rng;

    public Intake(Robot r, Truth t) {
        motor = new Motor("intake", MotorSpec.of(t.str("intake.motor", "1150")));
        r.addMotor(motor);
        roller.motor = motor;
        roller.inertia = 6e-4;
        roller.viscous = 2e-4;
        pollenMotor = new Motor("pollenFeed", MotorSpec.of(t.str("intake.pollenMotor", "1150")));
        r.addMotor(pollenMotor);
        pollenWheels.motor = pollenMotor;
        pollenWheels.inertia = 4e-4;
        pollenWheels.viscous = 2e-4;
        inSign = Shots.sign(t, "intake.inSign");
        pollenInSign = Shots.sign(t, "intake.pollenInSign");
        pollenRadiusIn = Shots.pick(t, "intake.pollenRadiusIn", 0.95, 1.1);
        rollerRadiusIn = Shots.pick(t, "intake.rollerRadiusIn", 0.95, 1.1);
        grip = Shots.pick(t, "intake.grip", 0.55, 0.7);
        pollenMassKg = Shots.pick(t, "element.pollenMassG", 21, 25) / 1000;
        nectarMassKg = Shots.pick(t, "element.nectarMassG", 36, 42) / 1000;
        rng = new Random(t.seed * 7919 + 11);
        for (int side = 0; side < 2; side++) {
            int n = (int) t.num(side == 0 ? "launcher.preloadFront" : "launcher.preloadBack", 0);
            for (int k = 0; k < n; k++) loadLane(side, side == 0 ? Element.Type.NECTAR : Element.Type.POLLEN);
        }
    }

    @Override
    public String id() {
        return "intake";
    }

    public Element newElement(Element.Type type) {
        return new Element(type, type == Element.Type.NECTAR ? nectarMassKg : pollenMassKg, rng);
    }

    // inches per second the drive moves elements at full power (the feed share of a speed is speed / this)
    public double maxFeedIn() {
        return motor.spec.freeRadS / motor.spec.gear * rollerRadiusIn * grip;
    }

    // inches per second the drive moves elements; + = toward the launchers
    public double feedSpeedIn() {
        return roller.rate * inSign * rollerRadiusIn * grip;
    }

    // the POLLEN feeder wheels, same units
    public double pollenSpeedIn() {
        return pollenWheels.rate * pollenInSign * pollenRadiusIn * grip;
    }

    // what moves this element: the feeder wheels once it is in the back lane past where they start, else the main drive
    private double speedFor(Element e, int lane) {
        return lane == 1 && e.s >= GATE_S[1] - POLLEN_WHEELS_BEFORE_GATE_IN ? pollenSpeedIn() : feedSpeedIn();
    }

    public int held() {
        int n = 0;
        for (Element e : elements) if (e.s > MOUTH_S + 1e-6) n++;
        return n;
    }

    public Element atMouth() {
        for (Element e : elements) if (e.s <= MOUTH_S + 1e-6) return e;
        return null;
    }

    public boolean gatePasses(int lane, Element e, World w) {
        return w.robot.servoMechs.gateOpen(lane) >= (e.type == Element.Type.NECTAR ? NECTAR_PASS : POLLEN_PASS);
    }

    // the element held against this lane's closed gate (the one a rubbing gate pushes into the flywheel)
    public Element atGate(int lane, World w) {
        for (Element e : elements) {
            if (e.lane == lane && Math.abs(e.s - (GATE_S[lane] - e.radiusIn())) < 0.05 && !gatePasses(lane, e, w)) return e;
        }
        return null;
    }

    @Override
    public void step(double dt, World w) {
        roller.externalTorque = blocked == 0 ? 0 : -Math.signum(roller.rate) * Math.min(4, blocked) * SLIP_TORQUE;
        roller.step(dt, w.robot.battery.volts);
        pollenWheels.externalTorque = blockedPollen == 0 ? 0 : -Math.signum(pollenWheels.rate) * Math.min(4, blockedPollen) * SLIP_TORQUE;
        pollenWheels.step(dt, w.robot.battery.volts);
        for (int i = 0; i < 2; i++) {
            gateOpen[i] = w.robot.servoMechs.gateOpen(i);
            gateRubs[i] = w.robot.servoMechs.gateRubs(i);
        }
        blocked = 0;
        blockedPollen = 0;
        pushIn(dt, w);
        pushOut(dt);
    }

    // every element its own drive moves forward (+), others stay; ahead of it in the same lane blocks it
    private void pushIn(double dt, World w) {
        elements.sort(Comparator.comparingDouble((Element e) -> e.s).reversed());
        boolean full = held() >= CAPACITY;
        List<Element> done = new ArrayList<>();
        for (int k = 0; k < elements.size(); k++) {
            Element e = elements.get(k);
            int lane = e.lane >= 0 ? e.lane : e.sortLane();
            double v = speedFor(e, lane);
            if (v <= 0.3) continue;
            if (e.s <= MOUTH_S + 1e-6 && full) {
                blocked++;
                continue;
            }
            double target = e.s + v * e.grip * dt;
            for (int a = k - 1; a >= 0; a--) {
                Element ahead = elements.get(a);
                int aheadLane = ahead.lane >= 0 ? ahead.lane : ahead.sortLane();
                if (ahead.s < SORT_S || aheadLane == lane) {
                    double limit = ahead.s - ahead.radiusIn() - e.radiusIn();
                    if (target > limit) {
                        target = Math.max(e.s, limit);
                        block(e, lane);
                    }
                    break;
                }
            }
            if (target >= SORT_S) e.lane = lane;
            // a closed gate holds an element whose centre has not passed it (and shoves back one it closes on)
            double gateStop = GATE_S[lane] - e.radiusIn();
            if (e.s < GATE_S[lane] && target > gateStop && !gatePasses(lane, e, w)) {
                target = gateStop;
                block(e, lane);
            }
            double contact = GATE_S[lane] + GAP_IN;
            if (target >= contact) {
                e.s = contact;
                double before = e.s;
                if (w.robot.flywheels.startContact(lane, e, v, 1.0, "feed", w)) {
                    done.add(e);
                } else if (e.s == before) {
                    block(e, lane);
                }
                continue;
            }
            e.s = target;
        }
        elements.removeAll(done);
    }

    // the element slips on the drive that moves it
    private void block(Element e, int lane) {
        if (lane == 1 && e.s >= GATE_S[1] - POLLEN_WHEELS_BEFORE_GATE_IN - 1e-9) blockedPollen++;
        else blocked++;
    }

    // every element its own drive moves backward (-) goes back toward the mouth and out
    private void pushOut(double dt) {
        List<Element> gone = new ArrayList<>();
        for (Element e : elements) {
            double v = speedFor(e, e.lane >= 0 ? e.lane : e.sortLane());
            if (v >= -0.3) continue;
            e.s += v * e.grip * dt;
            if (e.s < SORT_S) e.lane = -1;
            if (e.s < MOUTH_S - 0.5) gone.add(e);
        }
        spatOut += gone.size();
        elements.removeAll(gone);
    }

    // a student drops one element into a launcher lane, rolled up against the gate (or the element already there)
    public boolean loadLane(int side, Element.Type type) {
        if (held() >= CAPACITY) return false;
        Element e = newElement(type);
        double front = GATE_S[side];
        for (Element o : elements) if (o.lane == side) front = Math.min(front, o.s - o.radiusIn());
        e.lane = side;
        e.s = front - e.radiusIn();
        if (e.s < SORT_S) return false;
        elements.add(e);
        return true;
    }

    @Override
    public void state(Map<String, Object> out) {
        out.put("deg", Math.toDegrees(roller.angle * inSign));
        out.put("rpmIn", roller.rate * inSign * 60 / (2 * Math.PI));
        out.put("surfaceIn", feedSpeedIn());
        out.put("pollenSurfaceIn", pollenSpeedIn());
        out.put("pollenRpmIn", pollenWheels.rate * pollenInSign * 60 / (2 * Math.PI));
        out.put("held", held());
        out.put("capacity", CAPACITY);
        out.put("spatOut", spatOut);
        out.put("blocked", blocked);
        out.put("blockedPollen", blockedPollen);
        out.put("gateOpen", gateOpen);
        out.put("gateRubs", gateRubs);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Element e : elements) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.id);
            m.put("type", e.type.name());
            m.put("lane", e.lane);
            m.put("s", e.s);
            m.put("d", e.diameterIn);
            m.put("bounces", e.bounces);
            list.add(m);
        }
        out.put("elements", list);
        Map<String, Object> geo = new LinkedHashMap<>();
        geo.put("mouthS", MOUTH_S);
        geo.put("sortS", SORT_S);
        geo.put("gateS", GATE_S);
        geo.put("gapIn", GAP_IN);
        geo.put("pollenWheelsS", GATE_S[1] - POLLEN_WHEELS_BEFORE_GATE_IN);
        out.put("path", geo);
    }

    @Override
    public void expected(Map<String, Expected> out, World w) {
        boolean reversed = Flywheels.forwardDuty(w.robot, motor) * inSign < 0;
        out.put("intakeReversed", new Expected(reversed ? 1 : 0, 0, "0/1",
                reversed ? "+power pushes elements OUT: Intake.REVERSED = true" : "+power pulls elements IN: Intake.REVERSED = false"));
        boolean pollenReversed = Flywheels.forwardDuty(w.robot, pollenMotor) * pollenInSign < 0;
        out.put("pollenFeedReversed", new Expected(pollenReversed ? 1 : 0, 0, "0/1",
                pollenReversed ? "+power moves POLLEN away from the back launcher: Intake.POLLEN_REVERSED = true"
                        : "+power moves POLLEN toward the back launcher: Intake.POLLEN_REVERSED = false"));
    }

    @Override
    public boolean hand(String action, JsonObject a, World w) {
        switch (action) {
            case "turn":
                // {deg, pollen}: the roller, or with pollen = true the POLLEN feeder wheels
                if (a.has("pollen") && a.get("pollen").getAsBoolean()) pollenWheels.turnBy(Math.toRadians(a.get("deg").getAsDouble()) * pollenInSign);
                else roller.turnBy(Math.toRadians(a.get("deg").getAsDouble()) * inSign);
                return true;
            case "release":
                roller.release();
                pollenWheels.release();
                return true;
            case "loadIntake": {
                if (atMouth() != null) return false;
                Element e = newElement(Element.parse(a.has("type") ? a.get("type").getAsString() : null, Element.Type.NECTAR));
                e.s = MOUTH_S;
                elements.add(e);
                return true;
            }
            case "loadLauncher": {
                int side = a.has("side") ? a.get("side").getAsInt() : 0;
                Element.Type def = side == 0 ? Element.Type.NECTAR : Element.Type.POLLEN;
                return loadLane(side, Element.parse(a.has("type") ? a.get("type").getAsString() : null, def));
            }
            case "unload":
                elements.clear();
                return true;
            default:
                return false;
        }
    }
}
