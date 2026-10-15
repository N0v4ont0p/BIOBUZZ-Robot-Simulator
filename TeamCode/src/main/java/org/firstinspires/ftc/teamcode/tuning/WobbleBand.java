package org.firstinspires.ftc.teamcode.tuning;

import java.util.TreeSet;

// Program 7 and its shot-speed check: the ready band from a hold at a steady speed. The hub reads speed in steps
// (20 ticks/s on a Control Hub: encoder counts over 50 ms) and the wheel wobbles a few ticks/s, so the readings sit
// on two neighbouring steps and now and then reach a third. The largest swing in a few seconds is then nearly
// all-or-nothing (one step or two): the same wheel read a band half or twice as wide from run to run. The spread of
// all the readings (their standard deviation) settles, so:
//   band = 1.5 x (the larger of one reading step and 2.5 standard deviations) + how far the average is off the target
final class WobbleBand {
    static final double FACTOR = 1.5, SIGMAS = 2.5;

    private final TreeSet<Double> seen = new TreeSet<>();
    private double sum, sumSq;
    private int n;

    void add(double velocity) {
        sum += velocity;
        sumSq += velocity * velocity;
        n++;
        seen.add(velocity);
    }

    int count() {
        return n;
    }

    double mean() {
        return n > 0 ? sum / n : 0;
    }

    // standard deviation of the readings
    double spread() {
        if (n < 2) return 0;
        double m = mean();
        return Math.sqrt(Math.max(0, (sumSq - n * m * m) / (n - 1)));
    }

    // the smallest difference between two different readings: the hub's reading step (0 with fewer than two)
    double step() {
        double best = Double.POSITIVE_INFINITY;
        Double prev = null;
        for (double v : seen) {
            if (prev != null) best = Math.min(best, v - prev);
            prev = v;
        }
        return Double.isInfinite(best) ? 0 : best;
    }

    double band(double target) {
        return Math.max(1, Math.ceil(FACTOR * Math.max(step(), SIGMAS * spread()) + Math.abs(mean() - target)));
    }
}
