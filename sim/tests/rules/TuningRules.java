import org.firstinspires.ftc.teamcode.subsystems.ShotCurve;
import org.firstinspires.ftc.teamcode.tuning.FeedCheck;
import org.firstinspires.ftc.teamcode.tuning.FlywheelAutoTune;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// The decision rules of the setup programs' automatic parts, as tables of cases (no robot, no simulator): the shot curve
// (programs 8 / 9 and the match), the feed check (programs 8 / 9), program 7's P search and idle pick.
// Run by sim/tests/test_tuning_rules.py.
public class TuningRules {
    static int checks = 0;

    static void check(boolean ok, String what) {
        checks++;
        if (!ok) throw new AssertionError(what);
    }

    // ---------- the shot curve ----------
    interface Truth {
        double at(double d);
    }

    static double[][] table(Truth speed, Truth hood, double from, double to, double step, Random noise, double speedW, double hoodW) {
        List<double[]> rows = new ArrayList<>();
        for (double d = from; d <= to + 1e-9; d += step) {
            double sw = noise == null ? 0 : (noise.nextDouble() * 2 - 1) * speedW, hw = noise == null ? 0 : (noise.nextDouble() * 2 - 1) * hoodW;
            rows.add(new double[]{d, speed.at(d) + sw, hood.at(d) + hw, 0.5});
        }
        return rows.toArray(new double[0][]);
    }

    static void curveExact() {
        double[][] line = table(d -> 1000 + 10 * d, d -> 0.3 + 0.004 * d, 36, 81, 5, null, 0, 0);
        ShotCurve c = new ShotCurve(line, true);
        for (double d = 36; d <= 81; d += 2.5) {
            check(Math.abs(c.speed(d) - (1000 + 10 * d)) < 1e-6 && Math.abs(c.hood(d) - (0.3 + 0.004 * d)) < 1e-9, "a straight table stays straight at " + d);
        }
        // past the ends: the end value is held
        check(Math.abs(c.speed(95) - 1810) < 1e-6 && Math.abs(c.speed(30) - 1360) < 1e-6, "past the ends the end value holds: " + c.speed(95) + ", " + c.speed(30));
        double[][] para = table(d -> 900 + 0.2 * d * d, d -> 0.6, 36, 81, 5, null, 0, 0);
        c = new ShotCurve(para, true);
        check(c.method(ShotCurve.SPEED) != ShotCurve.LINES, "a curved table is fitted, not joined with lines: " + c.describe());
        for (double d = 36; d <= 81; d += 2.5) check(Math.abs(c.speed(d) - (900 + 0.2 * d * d)) < 1e-6, "a parabola comes back exactly at " + d);
        check(c.pulled().isEmpty(), "no point pulled on clean data");
        // the switch off: exactly the old straight lines
        ShotCurve off = new ShotCurve(para, false);
        check(off.method(ShotCurve.SPEED) == ShotCurve.LINES && Math.abs(off.speed(38.5) - (para[0][1] + 0.5 * (para[1][1] - para[0][1]))) < 1e-9
                && off.speed(90) == para[para.length - 1][1], "smooth off = straight lines, ends held");
        double[][] few = table(d -> 900 + 0.2 * d * d, d -> 0.6, 36, 51, 5, null, 0, 0);
        check(new ShotCurve(few, true).method(ShotCurve.SPEED) == ShotCurve.LINES, "fewer than " + ShotCurve.MIN_POINTS + " points: straight lines");
    }

    // points that scored somewhere inside their window: the curve lands nearer the true middle than straight lines
    static void curveAccuracy() {
        Random r = new Random(4);
        Truth speed = d -> 1100 + 8 * d + 0.05 * d * d, hood = d -> 0.75 - 0.004 * d;
        double linesErr = 0, curveErr = 0, linesHood = 0, curveHood = 0, worseCases = 0;
        int cases = 500, n = 0;
        for (int k = 0; k < cases; k++) {
            double[][] t = table(speed, hood, 36, 81, 5, r, 35, 0.015);
            ShotCurve smooth = new ShotCurve(t, true), lines = new ShotCurve(t, false);
            double le = 0, ce = 0;
            for (double d = 36; d <= 81; d += 2.5) {
                le += Math.abs(lines.speed(d) - speed.at(d));
                ce += Math.abs(smooth.speed(d) - speed.at(d));
                linesHood += Math.abs(lines.hood(d) - hood.at(d));
                curveHood += Math.abs(smooth.hood(d) - hood.at(d));
                n++;
            }
            linesErr += le;
            curveErr += ce;
            if (ce > le * 1.1) worseCases++;
        }
        System.out.printf("  curve vs straight lines over %d noisy tables: speed off the middle %.1f vs %.1f ticks/s, hood %.4f vs %.4f; curve clearly worse in %.0f cases%n",
                cases, curveErr / n, linesErr / n, curveHood / n, linesHood / n, worseCases);
        check(curveErr < 0.85 * linesErr, "the curve is nearer the middle than straight lines (speed)");
        check(curveHood < 0.85 * linesHood, "the curve is nearer the middle than straight lines (hood)");
        check(worseCases < 0.1 * cases, "the curve is rarely clearly worse than straight lines: " + worseCases);
    }

    static void curveGuards() {
        Truth speed = d -> 1100 + 8 * d + 0.05 * d * d;
        double[][] t = table(speed, d -> 0.5, 36, 81, 5, null, 0, 0);
        t[4][1] += 150;   // one wild point (56 in)
        ShotCurve c = new ShotCurve(t, true), lines = new ShotCurve(t, false);
        double off = Math.abs(c.speed(56) - speed.at(56));
        check(c.method(ShotCurve.SPEED) == ShotCurve.LINES || off < Math.abs(lines.speed(56) - speed.at(56)), "a wild point moves the curve less than straight lines");
        check(c.method(ShotCurve.SPEED) == ShotCurve.LINES || !c.pulled().isEmpty(), "and it is named: " + c.pulled());
        // within 2 steps of every saved point, and no overshoot between two points
        Random r = new Random(8);
        for (int k = 0; k < 200; k++) {
            double[][] u = table(speed, d -> 0.6, 36, 81, 5, r, 60, 0);
            ShotCurve s = new ShotCurve(u, true);
            for (int i = 0; i < u.length; i++) check(Math.abs(s.speed(u[i][0]) - u[i][1]) <= 40 + 1e-6, "within 2 steps of every point");
            for (int i = 1; i < u.length; i++) {
                double lo = Math.min(u[i - 1][1], u[i][1]) - 40, hi = Math.max(u[i - 1][1], u[i][1]) + 40;
                for (double d = u[i - 1][0]; d <= u[i][0]; d += 0.5) check(s.speed(d) >= lo - 1e-6 && s.speed(d) <= hi + 1e-6, "no overshoot");
            }
        }
        // speed that rises with distance never drops on the curve, and past the last point never below it
        for (int k = 0; k < 200; k++) {
            double[][] u = table(d -> 1100 + 12 * d, d -> 0.6, 36, 81, 5, r, 25, 0);
            for (int i = 1; i < u.length; i++) u[i][1] = Math.max(u[i][1], u[i - 1][1]);
            ShotCurve s = new ShotCurve(u, true);
            double prev = s.speed(36);
            for (double d = 36.5; d <= 81; d += 0.5) {
                check(s.speed(d) >= prev - 10 - 1e-6, "speed does not drop as the distance grows");
                prev = s.speed(d);
            }
            check(s.speed(95) == s.speed(81) && s.speed(25) == s.speed(36), "past the ends the curve's end value holds");
        }
    }

    // ---------- the feed check ----------
    // a dip as the hub reads it: a share of the speed, rounded to the reading's step
    static double dip(double feed, double best, double effect, double step, Random r) {
        double spread = 0.002 + effect * Math.abs(feed - best);
        return Math.round((0.08 + spread * r.nextGaussian()) / step) * step;
    }

    static double runCheck(double best, double effect, double step, Random r) {
        FeedCheck c = new FeedCheck(0.5, step);
        while (!c.done()) c.shot(dip(c.feed(), best, effect, step, r), false);
        return c.chosen();
    }

    static void feedCheck() {
        Random r = new Random(6);
        int found = 0, kept = 0, cases = 300;
        double step = 20 / 1500.0;
        for (int k = 0; k < cases; k++) {
            double best = 0.3 + 0.55 * r.nextDouble();
            // a feed effect well above the reading's step: the steadiest feed is found
            if (Math.abs(runCheck(best, 0.3, step, r) - best) <= 0.15) found++;
            // an effect smaller than the step: the feed in use (0.5) stays
            if (runCheck(best, 0.01, step, r) == 0.5) kept++;
        }
        System.out.printf("  feed check: strong effect, steadiest feed found within 0.15 in %d of %d; effect below the reading's step, feed kept in %d of %d%n",
                found, cases, kept, cases);
        check(found >= 0.8 * cases, "a strong feed effect: the steadiest feed is found");
        check(kept >= 0.9 * cases, "an effect the reading cannot show: the feed in use stays");
        FeedCheck c = new FeedCheck(0.5, step);
        int shots = 0;
        while (!c.done()) {
            c.shot(dip(c.feed(), 0.7, 0.3, step, r), false);
            shots++;
        }
        check(shots <= 15, "at most 15 shots: " + shots);
        // a feed that sent two at once is out, whatever its spread
        c = new FeedCheck(0.8, step);
        while (!c.done()) c.shot(0.08, c.feed() >= 0.79);
        check(c.chosen() < 0.79, "a double feed rules a feed out, even the one in use");
        // equal spreads: the feed in use stays
        c = new FeedCheck(0.5, step);
        while (!c.done()) c.shot(0.08, false);
        check(c.chosen() == 0.5, "no clear winner: the feed in use stays: " + c.chosen());
        // the dip watcher: one dip, two dips, nothing
        FeedCheck.Dip one = new FeedCheck.Dip(1500, 40);
        for (double v : new double[]{1500, 1450, 1300, 1350, 1450, 1490, 1500}) one.add(v, true);
        check(one.reachedWheel() && !one.twice() && one.depth() == 200, "one dip");
        FeedCheck.Dip two = new FeedCheck.Dip(1500, 40);
        for (double v : new double[]{1500, 1320, 1450, 1480, 1330, 1460}) two.add(v, true);
        check(two.twice(), "two dips");
        FeedCheck.Dip wobble = new FeedCheck.Dip(1500, 40);
        for (double v : new double[]{1500, 1300, 1420, 1540, 1450, 1510, 1490, 1500}) wobble.add(v, true);
        check(wobble.reachedWheel() && !wobble.twice(), "the recovery's wobble is not a second element");
        FeedCheck.Dip late = new FeedCheck.Dip(1500, 40);
        double[] lateTrace = {1500, 1320, 1450, 1480, 1330, 1460};
        for (int i = 0; i < lateTrace.length; i++) late.add(lateTrace[i], i < 3);
        check(!late.twice(), "a dip after the feed stopped is not a second element");
        FeedCheck.Dip none = new FeedCheck.Dip(1500, 40);
        for (double v : new double[]{1500, 1480, 1490, 1500}) none.add(v, true);
        check(!none.reachedWheel(), "no element: no dip");
    }

    // ---------- program 7: idle pick ----------
    static void idle() {
        double[] f = {0, 0.2, 0.35, 0.5, 0.65};
        check(FlywheelAutoTune.chooseIdle(f, new double[]{1.47, 1.40, 1.25, 1.11, 0.96}, 0.65) == 4, "steady gain: the highest allowed");
        check(FlywheelAutoTune.chooseIdle(f, new double[]{0.80, 0.78, 0.77, 0.76, 0.75}, 0.65) == 0, "flat curve: 0");
        check(FlywheelAutoTune.chooseIdle(f, new double[]{1.8, 1.0, 0.95, 0.93, 0.92}, 0.65) == 1, "knee at 20 %");
        check(FlywheelAutoTune.chooseIdle(f, new double[]{1.8, 1.7, 1.6, 1.0, 0.2}, 0.5) == 3, "never above the cap");
    }

    // ---------- program 7: the P search on a made-up wheel ----------
    interface Wheel {
        double steady(double p);
    }

    static double runSearch(Wheel w, double p0, Random noise, double spread, List<String> log) {
        FlywheelAutoTune.PSearch ps = new FlywheelAutoTune.PSearch(p0);
        for (int k = 0; k < 30 && !ps.done(); k++) {
            double st = w.steady(ps.p()) + (noise == null ? 0 : noise.nextGaussian() * spread);
            ps.offer(200, Math.max(0.1, st - 0.1), Math.max(0.05, st), 10, 50);
            ps.accept();
            log.add(String.format("%.2f->%.2f", ps.trials().get(ps.trials().size() - 1).p, st));
        }
        check(ps.done(), "the search ends");
        check(ps.counted() <= FlywheelAutoTune.PSearch.MAX_SHOTS, "at most 12 shots");
        return ps.finalP();
    }

    static void pSearch() {
        // best near x8 (a valley): picks inside x5.7 .. x11.3
        List<String> log = new ArrayList<>();
        double p = runSearch(x -> 0.5 + 0.3 * Math.abs(Math.log(x / 8.0) / Math.log(2)), 1, null, 0, log);
        check(p > 5.6 && p < 11.4, "valley at x8: picked " + p + " " + log);
        // better and better up to x64 (the simulator's shape): extends to x64
        log.clear();
        p = runSearch(x -> 1.5 / Math.pow(x, 0.25), 1, null, 0, log);
        check(p >= 45, "keeps getting quicker: tries past x32, picked " + p + " " + log);
        // best at the start P: tries x0.5 too
        log.clear();
        p = runSearch(x -> 0.4 + 0.2 * Math.abs(Math.log(x) / Math.log(2)), 1, null, 0, log);
        check(p > 0.69 && p < 1.42, "best at the start P: picked " + p + " " + log);
        // nothing ever steady: keeps the start P and says so
        FlywheelAutoTune.PSearch ps = new FlywheelAutoTune.PSearch(1.1);
        for (int k = 0; k < 30 && !ps.done(); k++) {
            ps.offer(200, -1, -1, 10, 50);
            ps.accept();
        }
        check(ps.done() && ps.finalP() == 1.1 && ps.note().contains("limited"), "never steady: start P, said why");
        // the tie follows the measured spread
        ps = new FlywheelAutoTune.PSearch(1);
        check(ps.tie() == FlywheelAutoTune.PSearch.TIE_MIN_S, "no repeats: the smallest tie");
        // noisy shots on the valley: the pick is still near the valley
        Random r = new Random(9);
        int good = 0;
        for (int k = 0; k < 100; k++) {
            log.clear();
            double q = runSearch(x -> 0.5 + 0.3 * Math.abs(Math.log(x / 8.0) / Math.log(2)), 1, r, 0.05, log);
            if (q > 3.9 && q < 16.1) good++;
        }
        check(good >= 95, "noisy valley: within one grid step of x8 in " + good + " of 100");
        System.out.println("  P search on a noisy valley: within one grid step " + good + " / 100");
    }

    public static void main(String[] args) {
        curveExact();
        curveAccuracy();
        curveGuards();
        feedCheck();
        idle();
        pSearch();
        System.out.println("PASS tuning rules: " + checks + " checks");
    }
}
