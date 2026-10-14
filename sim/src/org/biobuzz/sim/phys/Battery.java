package org.biobuzz.sim.phys;

import org.biobuzz.sim.truth.Truth;

// 12 V NiMH robot battery: open-circuit voltage falls with charge, terminal voltage sags under load.
public final class Battery {
    private static final double CAPACITY_AH = 3.0, ELECTRONICS_AMPS = 0.6;
    public double charge;
    public final double resistance;
    public double volts, lastAmps;
    private double amps;

    public Battery(Truth t) {
        charge = t.num("battery.charge", 0.95);
        resistance = t.num("battery.resistance", 0.08);
        volts = openCircuit();
    }

    public double openCircuit() {
        return 11.75 + 1.55 * Math.max(0, Math.min(1, charge));
    }

    public void draw(double a) {
        amps += a;
    }

    public void step(double dt) {
        double total = amps + ELECTRONICS_AMPS;
        lastAmps = total;
        volts = Math.max(6, openCircuit() - total * resistance);
        charge = Math.max(0, charge - total * dt / (CAPACITY_AH * 3600));
        amps = 0;
    }

    public void swap() {
        charge = 1.0;
    }
}
