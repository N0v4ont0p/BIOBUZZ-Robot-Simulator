package org.biobuzz.sim.phys;

import org.biobuzz.sim.truth.Truth;

import java.util.Random;

// A continuous rotation servo: the pulse sets a power, not a position. 1500 us = stop; the SDK's full power is
// +/-900 us (CRServo power p -> position (p + 1) / 2 over the 600-2400 us range); a few microseconds round the
// middle do nothing (deadband). The mechanism it drives (ServoMechs' ramp winch) reads power() and sets load when it
// strains (pulling against a stop, or against the other servo).
public class CrServo extends ServoModel {
    public static final double DEADBAND_US = 12, FULL_US = 900;
    // how it is mounted: +1 = a + pulse drives its mechanism the + way (can be turned round by hand)
    public double mount;

    public CrServo(String id, Truth t, Random rng, double mount) {
        super(id, t, rng, 0.2, 1, 1, 0, -1e9, 1e9, 0);
        this.mount = mount < 0 ? -1 : 1;
    }

    // -1..1, 0 with no signal or inside the deadband; + = the way its mechanism calls +
    public double power() {
        if (!powered()) return 0;
        double d = latchedUs - 1500;
        return Math.abs(d) < DEADBAND_US ? 0 : mount * Math.max(-1, Math.min(1, d / FULL_US));
    }

    @Override
    public void step(double dt, double now, Battery battery) {
        frameT += dt;
        if (frameT >= FRAME_S) {
            frameT -= FRAME_S;
            latchedUs = pulseUs;
        }
        load = powered() ? extraLoad : 0;
        amps = powered() ? 0.02 + Math.abs(power()) * 0.6 + load * stallAmps : 0;
        battery.draw(amps * 5 / 12 / 0.9);
        heat = Math.max(0, heat + dt * (load * load / burnoutSeconds - heat / 90));
        if (heat >= 1) burnedOut = true;
    }

    @Override
    public double out() {
        return power();
    }
}
