package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.hw.MotorPort;
import org.biobuzz.sim.truth.Truth;
import org.firstinspires.ftc.robotcore.external.navigation.Rotation;

import java.util.Map;
import java.util.Random;

// Front (NECTAR) and back (POLLEN) flywheels, one 6000 rpm motor each, a hood shooter: the element rolls between the
// wheel and the hood. outSign: +1 means positive motor rotation moves the wheel surface the way that throws an
// element out. A shot takes energy from the wheel (the element leaves at eff x surface speed, spinning, plus slip
// losses), so the wheel dips and the hub's speed control pulls it back. Speed wobble: bearings and wheel runout,
// on top of the hub's own counting.
public class Flywheels implements Part {
    public static final String[] IDS = {"flywheel.front", "flywheel.back"};
    public static final double TICKS_PER_RAD = 28 / (2 * Math.PI);
    // the hub's velocity reading: encoder counts over this window (MotorPort), so it reads in 1 / window steps
    private static final double READ_WINDOW_S = 0.05;
    private static final double WOBBLE_TAU = 0.03, CONTACT_PATH_M = 0.09, MIN_RAD_S = 20;
    private static final double ELECTRONICS_AMPS = 0.6;

    public final Motor[] motors = new Motor[2];
    public final Joint[] wheels = new Joint[2];
    public final int[] outSign = new int[2];
    public final double[] radiusM = new double[2], effNectar = new double[2], effPollen = new double[2];
    public final double[] loss = new double[2], wobbleTicks = new double[2], feedCarry = new double[2];
    // extra wobble per 1000 ticks/s above 1000 ticks/s (0 = the same wobble at every speed); scenario G-flywheel-speed-wobble
    public final double[] wobbleGrowth = new double[2];
    // feed (intake power share while firing): the element enters most squarely at bestFeed; away from it each shot's
    // grip scatters by feedScatter per unit of feed share (a guess about real launchers: programs 8 / 9's feed check
    // must find bestFeed from the flywheel's dips alone)
    public final double[] bestFeed = new double[2], feedScatter = new double[2];
    private final double[] wobble = new double[2];
    private final Random rng;

    // element in contact with each wheel
    private final Element[] contact = new Element[2];
    private final double[] contactLeft = new double[2], brake = new double[2], contactFeed = new double[2], contactEff = new double[2];
    private final String[] contactCause = new String[2];
    // what the last shot did to each wheel, for the views and the report card
    public final double[] lastDipTicks = new double[2], lastExitMps = new double[2], beforeTicks = new double[2];
    private final double[] dipWatch = new double[2], dipMin = new double[2];
    public final int[] launched = new int[2], bouncedBack = new int[2];
    public final int[] lastOutcome = {-1, -1};   // 1 = flew out, 0 = pushed back in, -1 = nothing fed yet
    private final double[] fullPowerS = new double[2];
    public final double[] topAtRun = {Double.NaN, Double.NaN};

    public Flywheels(Robot r, Truth t) {
        rng = new Random(t.seed * 104729 + 3);
        for (int i = 0; i < 2; i++) {
            String p = IDS[i];
            motors[i] = new Motor(p, MotorSpec.of(t.str(p + ".motor", "6000")));
            r.addMotor(motors[i]);
            Joint j = new Joint();
            j.motor = motors[i];
            // two 96 mm wheels on a shaft, no extra flywheel mass: spins up in about half a second
            j.inertia = Shots.pick(t, p + ".inertia", 0.9e-4, 1.6e-4);
            j.viscous = Shots.pick(t, p + ".viscous", 1.0e-5, 2.5e-5);
            wheels[i] = j;
            outSign[i] = Shots.sign(t, p + ".outSign");
            radiusM[i] = Shots.pick(t, p + ".wheelDiameterMm", 95, 97) / 2000;
            effNectar[i] = Shots.pick(t, p + ".effNectar", 0.40, 0.46);
            effPollen[i] = Shots.pick(t, p + ".effPollen", 0.43, 0.50);
            loss[i] = Shots.pick(t, p + ".loss", 1.2, 1.6);
            wobbleTicks[i] = Shots.pick(t, p + ".wobbleTicks", 3, 10);
            feedCarry[i] = t.num(p + ".feedCarry", 0.1);
            wobbleGrowth[i] = t.num(p + ".wobbleGrowth", 0);
            bestFeed[i] = Shots.pick(t, p + ".bestFeed", 0.4, 0.75);
            feedScatter[i] = Shots.pick(t, p + ".feedScatter", 0.03, 0.06);
        }
    }

    @Override
    public String id() {
        return "flywheels";
    }

    public double inertia(int i) {
        return wheels[i].inertia + motors[i].reflectedInertia();
    }

    // wheel speed the throwing way, in encoder ticks per second (what a correctly set up hub would read)
    public double ticksOut(int i) {
        return wheels[i].rate * outSign[i] * TICKS_PER_RAD;
    }

    @Override
    public void step(double dt, World w) {
        for (int i = 0; i < 2; i++) {
            Joint j = wheels[i];
            double sigma = wobbleTicks[i] / TICKS_PER_RAD * Math.min(1, Math.abs(j.rate) / 300)
                    * (1 + wobbleGrowth[i] * Math.max(0, Math.abs(j.rate) * TICKS_PER_RAD / 1000 - 1));
            double dx = -wobble[i] / WOBBLE_TAU * dt + sigma * Math.sqrt(2 * dt / WOBBLE_TAU) * rng.nextGaussian();
            wobble[i] += dx;
            double torque = inertia(i) * dx / dt;
            if (contact[i] != null) {
                torque -= outSign[i] * brake[i];
                contactLeft[i] -= dt;
            }
            Element rub = w.robot.servoMechs.gateRubs(i) ? w.robot.intake.atGate(i, w) : null;
            if (rub != null && Math.abs(j.rate) > MIN_RAD_S) torque -= Math.signum(j.rate) * 0.01;
            j.externalTorque = torque;
            j.step(dt, w.robot.battery.volts);

            if (contact[i] != null && contactLeft[i] <= 0) release(i, w);
            // a gate set too close lets the spinning wheel grab the element it holds: a weak, unplanned shot
            if (rub != null && contact[i] == null && ticksOut(i) > 200 && rng.nextDouble() < dt * 0.4) {
                w.robot.intake.elements.remove(rub);
                rub.s = Intake.GATE_S[i] + Intake.GAP_IN;
                if (!startContact(i, rub, 0, 0.55, "gate rubs", w)) w.robot.intake.elements.add(rub);
            }
            if (dipWatch[i] > 0) {
                dipWatch[i] -= dt;
                dipMin[i] = Math.min(dipMin[i], ticksOut(i));
                lastDipTicks[i] = beforeTicks[i] - dipMin[i];
            }
            Motor m = motors[i];
            fullPowerS[i] = m.driven && Math.abs(m.duty) > 0.98 && contact[i] == null ? fullPowerS[i] + dt : 0;
            if (fullPowerS[i] >= 2.0) topAtRun[i] = trueTopTicks(i, w.robot.battery);
        }
    }

    // an element reaches the wheel; false = it stays where it is (wheel still, busy, or spinning the wrong way)
    public boolean startContact(int i, Element e, double feedIn, double effScale, String cause, World w) {
        if (contact[i] != null) return false;
        double om = wheels[i].rate * outSign[i];
        if (Math.abs(om) < MIN_RAD_S) return false;
        lastOutcome[i] = om > 0 ? 1 : 0;
        if (om < 0) {
            // surface moves into the robot at the contact point: the element is shoved back down the lane
            e.s -= 2.5;
            e.bounces++;
            bouncedBack[i]++;
            wheels[i].rate *= 0.985;
            return false;
        }
        if (cause.equals("feed")) effScale *= 1 + rng.nextGaussian() * feedSpread(i, feedIn / w.robot.intake.maxFeedIn());
        double a = eff(i, e.type) * e.grip * effScale * radiusM[i];
        double om1 = om / Math.sqrt(1 + 2 * energyK(i) * e.massKg * a * a / inertia(i));
        double tc = Math.max(0.008, Math.min(0.15, CONTACT_PATH_M / (0.5 * a * om)));
        contact[i] = e;
        contactLeft[i] = tc;
        brake[i] = inertia(i) * (om - om1) / tc;
        contactFeed[i] = feedIn;
        contactEff[i] = a;
        contactCause[i] = cause;
        beforeTicks[i] = om * TICKS_PER_RAD;
        dipMin[i] = beforeTicks[i];
        dipWatch[i] = 0.4;
        return true;
    }

    private void release(int i, World w) {
        Element e = contact[i];
        contact[i] = null;
        double om = Math.max(0, wheels[i].rate * outSign[i]);
        double v = contactEff[i] * om + feedCarry[i] * contactFeed[i] * 0.0254;
        lastExitMps[i] = v;
        launched[i]++;
        w.robot.shots.launch(i, e, v, beforeTicks[i], contactCause[i], w);
    }

    // the grip scatter (a share) of a shot fed at this feed share
    public double feedSpread(int i, double feedShare) {
        return feedScatter[i] * Math.abs(feedShare - bestFeed[i]);
    }

    public double eff(int i, Element.Type type) {
        return type == Element.Type.NECTAR ? effNectar[i] : effPollen[i];
    }

    // energy the wheel gives per unit of m v^2 at the exit: rolling hollow ball (1/2 + 1/3) times slip losses
    private double energyK(int i) {
        return loss[i] * 5.0 / 6.0;
    }

    // exit speed (m/s) for a wheel held at this speed: the same physics as a live shot, without the motor's help
    // during the few milliseconds of contact
    public double exitMps(int i, Element.Type type, double massKg, double grip, double ticks, double feedIn) {
        double a = eff(i, type) * grip * radiusM[i];
        double om = ticks / TICKS_PER_RAD;
        return a * om / Math.sqrt(1 + 2 * energyK(i) * massKg * a * a / inertia(i)) + feedCarry[i] * feedIn * 0.0254;
    }

    // steady speed at full power on this battery, ticks/s: motor torque = friction, battery sagging under the
    // motor's own current (everything else off)
    public double trueTopTicks(int i, Battery b) {
        MotorSpec s = motors[i].spec;
        double tf = motors[i].frictionTorque() + wheels[i].kineticFriction;
        double rr = s.resistance + b.resistance;
        double om = (b.openCircuit() - b.resistance * ELECTRONICS_AMPS - rr * tf / s.kT) / (s.kE + rr * wheels[i].viscous / s.kT);
        return om * TICKS_PER_RAD;
    }

    @Override
    public void state(Map<String, Object> out) {
        double[] rpm = new double[2], deg = new double[2], ticks = new double[2];
        boolean[] touching = new boolean[2];
        for (int i = 0; i < 2; i++) {
            rpm[i] = wheels[i].rate * outSign[i] * 60 / (2 * Math.PI);
            deg[i] = Math.toDegrees(wheels[i].angle * outSign[i]);
            ticks[i] = ticksOut(i);
            touching[i] = contact[i] != null;
        }
        out.put("rpmOut", rpm);
        out.put("deg", deg);
        out.put("ticksOut", ticks);
        out.put("contact", touching);
        out.put("lastDipTicks", lastDipTicks);
        out.put("lastExitMps", lastExitMps);
        out.put("launched", launched);
        out.put("bouncedBack", bouncedBack);
    }

    @Override
    public void expected(Map<String, Expected> out, World w) {
        String[] sides = {"front", "back"};
        for (int i = 0; i < 2; i++) {
            String s = sides[i];
            boolean reversed = forwardDuty(w.robot, motors[i]) * outSign[i] < 0;
            out.put(s + "FlywheelReversed", new Expected(reversed ? 1 : 0, 0, "0/1", reversed
                    ? "+power spins the " + s + " wheel against the launch direction: Launcher." + s.toUpperCase() + "_REVERSED = true"
                    : "+power spins the " + s + " wheel the launch way: Launcher." + s.toUpperCase() + "_REVERSED = false"));
            boolean opposite = encoderOpposite(i, w);
            out.put(s + "FlywheelEncoderOpposite", new Expected(opposite ? 1 : 0, 0, "0/1", opposite
                    ? "the " + s + " motor's power wires are swapped: its encoder counts against its power (program 2 must STOP)"
                    : "encoder counts with the power"));
            double top = Double.isNaN(topAtRun[i]) ? trueTopTicks(i, w.robot.battery) : topAtRun[i];
            double f = 32767 / top;
            String when = Double.isNaN(topAtRun[i]) ? "at the battery's present charge" : "at the battery voltage of the full-power run";
            out.put(s + "TopVelocity", new Expected(top, 0.04 * top, "ticks/s", "true steady full-power speed " + when));
            out.put(s + "FlywheelF", new Expected(f, 0.04 * f, "", "32767 / true top speed"));
            out.put(s + "BestFeed", new Expected(bestFeed[i], 0.15, "share", "the feed the element enters most squarely at (programs 8 / 9 feed check)"));
            // program 7 picks P by shots between F/20 and 6.4 F (the start P F/10 x 0.5 ... x 32, x 64 when x 32 still
            // beats x 16 clearly: FlywheelAutoTune.PSearch); test_p7_flywheel checks the pick against a sweep of P on the
            // same robot (the range has room for F itself being measured a few % off)
            out.put(s + "FlywheelP", new Expected(f * 3.3, f * 3.3, "", "picked by program 7's shots: anything from F/20 to 6.4 x F"));
            out.put(s + "FlywheelI", new Expected(f * 0.33, f * 0.33, "", "P / 10"));
            out.put(s + "FlywheelD", new Expected(0, 0.001, "", "0"));
            // what program 7 measures (WobbleBand): 1.5 x the larger of one reading step and 2.5 x the spread of the
            // readings at 70 % of top speed. The spread: the wobble as the hub's 50 ms counting window averages it (an
            // Ornstein-Uhlenbeck wobble with time constant WOBBLE_TAU keeps this share of its variance), plus the
            // readings landing on two neighbouring 20 ticks/s steps (a sixth of a step squared on average)
            double w70 = wobbleTicks[i] * (1 + wobbleGrowth[i] * Math.max(0, 0.7 * top / 1000 - 1));
            double r = WOBBLE_TAU / READ_WINDOW_S, kept = 2 * r * (1 - r * (1 - Math.exp(-1 / r)));
            double step = 1 / READ_WINDOW_S, spread = Math.sqrt(kept * w70 * w70 + step * step / 6);
            double band = 1.5 * Math.max(step, 2.5 * spread);
            out.put(s + "ReadyBand", new Expected(band, 0.5 * band, "ticks/s", String.format(java.util.Locale.US,
                    "1.5 x the larger of one %.0f ticks/s reading step and 2.5 x the reading spread (%.1f ticks/s: %.1f ticks/s wheel wobble at 70%% of top speed, seen through the hub's %.0f ms window, plus the reading steps)",
                    step, spread, w70, READ_WINDOW_S * 1000)));
            out.put(s + "IdleVelocity", new Expected(0.35 * top, 0.35 * top, "ticks/s", "anything from 0 to about 70% of top speed"));
            if (lastOutcome[i] >= 0) {
                out.put(s + "FlywheelThrowsOut", new Expected(lastOutcome[i], 0, "0/1", lastOutcome[i] == 1
                        ? "the last element fed into the " + s + " wheel flew out" : "the last element fed into the " + s + " wheel was pushed back in"));
            }
        }
    }

    // sign of the hub's duty when robot code sets +power with direction FORWARD: the SDK flips it for motor types
    // that turn CCW (goBILDA). Port not configured yet: goBILDA, as the setup guide asks.
    public static int forwardDuty(Robot r, Motor m) {
        for (Hub h : r.hubs) {
            for (MotorPort p : h.motors) {
                if (p.motor == m) return p.type == null || p.type.getOrientation() == Rotation.CCW ? -1 : 1;
            }
        }
        return -1;
    }

    private boolean encoderOpposite(int i, World w) {
        for (Hub h : w.robot.hubs) {
            for (MotorPort p : h.motors) {
                if (p.motor == motors[i]) return p.encoder != null && p.encoder.source == motors[i] && p.encoder.polarity < 0;
            }
        }
        return false;
    }

    @Override
    public boolean hand(String action, JsonObject a, World w) {
        int i = a.has("wheel") ? a.get("wheel").getAsInt() : 0;
        if (action.equals("turn")) {
            wheels[i].turnBy(Math.toRadians(a.get("deg").getAsDouble()) * outSign[i]);
            return true;
        }
        if (action.equals("release")) {
            wheels[i].release();
            return true;
        }
        return false;
    }
}
