package org.biobuzz.sim.phys;

import org.biobuzz.sim.truth.Truth;

import java.util.Random;

// A hobby servo (goBILDA 2000 series style) driving a linkage between two mechanical stops. The hub sends a pulse
// every 20 ms; the servo picks up the new width at the next frame and drives its horn there at its loaded speed,
// with a short speed-up and slow-down. The linkage turns the horn angle into the mechanism's own angle
// ("out": gate arm angle up from hanging, turret angle):
//   out = refDeg + dir * gain * horn,   horn = (pulse - 1500 us) * degPerUs + offsetDeg
// dir +1: a higher servo position moves the mechanism up / out / open.
// Commanding past a stop (or into an obstacle) makes it press there: it buzzes, draws current, heats up, and
// after long enough at full stall it burns out (no torque, limp) until the servo is replaced.
public class ServoModel {
    public static final double FRAME_S = 0.02, DEADBAND_DEG = 0.8, P_BAND_DEG = 5, BUZZ = 0.12;
    // how far past a stop (horn degrees) it can be told to go before anyone hears it
    public static final double QUIET_DEG = DEADBAND_DEG + BUZZ * P_BAND_DEG;
    // it stops driving once inside half the deadband of the command, so it rests that far short of it on the side
    // it came from (where a servo ends up depends on which way it moved last)
    public static final double HOLD_DEG = DEADBAND_DEG / 2;
    private static final double COOL_S = 90;

    public final String id;
    public final double degPerUs, speedDegPerS, accel, stallAmps, burnoutSeconds;
    public final double refDeg, dir, gain;
    public final double outStopLow, outStopHigh;   // mechanical stops, mechanism degrees
    public double offsetDeg;                       // horn on the spline; changes when the servo is replaced
    public int replaced;

    public volatile double pulseUs = Double.NaN;   // NaN = no signal
    protected double latchedUs = Double.NaN, frameT;
    public double angleDeg, velocity, commandDeg, targetDeg;
    public double outMin, outMax;                  // stops plus obstacles this step (set by the mechanism)
    public double load, amps, heat;
    // pushed by something besides its stops this step, 0-1 (the back gate's other servo disagreeing): it buzzes as at a stop
    public double extraLoad;
    public boolean burnedOut;

    // the last finished move up / out / open of 5 deg or more, first movement to arrival (gate travel time)
    private double moveFrom, moveStart = Double.NaN, lastTarget = Double.NaN;
    private boolean moving;
    public double lastOpenFromOut = Double.NaN, lastOpenToOut = Double.NaN, lastOpenSeconds = Double.NaN;

    public ServoModel(String id, Truth t, Random rng, double speedS60, double dir, double gain, double refDeg, double stopLow, double stopHigh,
                      double startOut) {
        this.id = id;
        degPerUs = t.num(id + ".degPerUs", 0.147 + rng.nextDouble() * 0.006);
        speedDegPerS = 60 / t.num(id + ".speedS60", speedS60);
        accel = speedDegPerS / t.num(id + ".accelS", 0.015);
        stallAmps = t.num(id + ".stallAmps", 1.8 + rng.nextDouble() * 0.6);
        burnoutSeconds = t.num(id + ".burnoutSeconds", 25 + rng.nextDouble() * 10);
        this.dir = t.num(id + ".dir", dir) < 0 ? -1 : 1;
        this.gain = t.num(id + ".gain", gain);
        this.refDeg = t.num(id + ".refDeg", refDeg);
        outStopLow = stopLow;
        outStopHigh = stopHigh;
        frameT = rng.nextDouble() * FRAME_S;
        outMin = stopLow;
        outMax = stopHigh;
        angleDeg = horn(Math.max(stopLow, Math.min(stopHigh, t.num(id + ".startDeg", startOut))));
        targetDeg = angleDeg;
    }

    public boolean powered() {
        return !Double.isNaN(latchedUs) && !burnedOut;
    }

    public double out() {
        return outOf(angleDeg);
    }

    public double outOf(double hornDeg) {
        return refDeg + dir * gain * hornDeg;
    }

    public double horn(double out) {
        return (out - refDeg) / (dir * gain);
    }

    // servo position (SDK default 600-2400 us range) that puts the mechanism at this angle
    public double position(double out) {
        return ((horn(out) - offsetDeg) / degPerUs + 1500 - 600) / 1800;
    }

    public double stopLowDeg() {
        return Math.min(horn(outStopLow), horn(outStopHigh));
    }

    public double stopHighDeg() {
        return Math.max(horn(outStopLow), horn(outStopHigh));
    }

    public boolean stalled() {
        return load > BUZZ;
    }

    public void step(double dt, double now, Battery battery) {
        frameT += dt;
        if (frameT >= FRAME_S) {
            frameT -= FRAME_S;
            latchedUs = pulseUs;
        }
        double lo = Math.min(horn(outMin), horn(outMax)), hi = Math.max(horn(outMin), horn(outMax));
        load = 0;
        amps = 0;
        if (powered()) {
            commandDeg = (latchedUs - 1500) * degPerUs + offsetDeg;
            targetDeg = Math.max(lo, Math.min(hi, commandDeg));
            // a command past a stop drives all the way into it; otherwise it stops at the deadband's edge
            double gap = targetDeg - angleDeg;
            double goal = targetDeg != commandDeg ? targetDeg
                    : Math.abs(gap) <= HOLD_DEG && velocity == 0 ? angleDeg : targetDeg - Math.signum(gap) * HOLD_DEG;
            double err = goal - angleDeg;
            double want = Math.signum(err) * Math.min(speedDegPerS, Math.sqrt(2 * accel * Math.abs(err)));
            velocity += Math.max(-accel * dt, Math.min(accel * dt, want - velocity));
            double next = angleDeg + velocity * dt;
            if ((goal - next) * err <= 0 || (Math.abs(err) < 0.02 && Math.abs(velocity) < accel * dt)) {
                next = goal;
                velocity = 0;
            }
            angleDeg = next;
            if (angleDeg == goal) {
                double beyond = Math.max(commandDeg - hi, lo - commandDeg);
                load = Math.max(0, Math.min(1, (beyond - DEADBAND_DEG) / P_BAND_DEG));
            }
            load = Math.max(load, extraLoad);
            amps = 0.02 + (velocity != 0 ? 0.6 : 0) + load * stallAmps;
            // servo power comes from the hub's 5 V regulator
            battery.draw(amps * 5 / 12 / 0.9);
            track(now, dt);
        } else {
            velocity = 0;
        }
        heat = Math.max(0, heat + dt * (load * load / burnoutSeconds - heat / COOL_S));
        if (heat >= 1) {
            burnedOut = true;
            moving = false;
        }
        angleDeg = Math.max(lo, Math.min(hi, angleDeg));
    }

    private void track(double now, double dt) {
        if (Double.isNaN(lastTarget) || Math.abs(targetDeg - lastTarget) > 0.3) {
            lastTarget = targetDeg;
            moveFrom = angleDeg;
            moveStart = Double.NaN;
            moving = true;
        }
        if (!moving) return;
        if (Double.isNaN(moveStart) && velocity != 0) moveStart = now;
        // arrived: stopped at the target or at the edge of its deadband
        if (!Double.isNaN(moveStart) && velocity == 0 && Math.abs(angleDeg - targetDeg) <= HOLD_DEG + 1e-9) {
            moving = false;
            if (out() > outOf(moveFrom) + 5) {
                lastOpenFromOut = outOf(moveFrom);
                lastOpenToOut = out();
                lastOpenSeconds = now + dt - moveStart;
            }
        }
    }

    // trapezoid move time from one mechanism angle to another (speed-up, full speed, slow-down)
    public double travelSeconds(double fromOut, double toOut) {
        double d = Math.abs(horn(toOut) - horn(fromOut)), v = speedDegPerS, a = accel;
        return d >= v * v / a ? d / v + v / a : 2 * Math.sqrt(d / a);
    }

    // a new servo from the spares box: the horn goes back on the spline a part-tooth off, so positions shift
    public void replace(Random rng) {
        burnedOut = false;
        heat = 0;
        load = 0;
        replaced++;
        offsetDeg = (rng.nextDouble() * 2 - 1) * 7.2;
        lastTarget = Double.NaN;
    }

    public String feel() {
        return burnedOut ? "dead, smells burnt" : heat < 0.3 ? "cool" : heat < 0.6 ? "warm" : heat < 0.85 ? "hot" : "too hot to touch";
    }
}
