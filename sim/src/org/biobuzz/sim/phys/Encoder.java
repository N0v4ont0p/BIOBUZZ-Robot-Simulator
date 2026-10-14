package org.biobuzz.sim.phys;

// The encoder cable plugged into a hub motor port. Normally it comes from the same motor; a wiring fault can
// unplug it or bring it from another motor.
public final class Encoder {
    public final Motor source;
    public final int polarity;

    public Encoder(Motor source, int polarity) {
        this.source = source;
        this.polarity = polarity;
    }

    public double counts() {
        return polarity * source.outAngle * source.spec.countsPerOutputRev() / (2 * Math.PI);
    }
}
