package org.biobuzz.sim.phys;

// What a setup program should find for one results-file key on this hidden robot: the true value, how close
// counts as good, and a sentence for the report card. Parts fill these in (Part.expected).
public final class Expected {
    public final double value, tolerance;
    public final String unit, how;

    public Expected(double value, double tolerance, String unit, String how) {
        this.value = value;
        this.tolerance = tolerance;
        this.unit = unit;
        this.how = how;
    }
}
