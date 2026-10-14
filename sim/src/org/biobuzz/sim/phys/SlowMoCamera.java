package org.biobuzz.sim.phys;

import org.biobuzz.sim.Sim;
import org.biobuzz.sim.web.Server;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.DoubleSupplier;

// A phone in 240 fps slow motion pointed at the robot. While it records, every 1/240 s of physics time it takes
// one frame: the value of each named channel at that instant (interpolated between physics steps). It keeps the
// last 10 s. Any part can add channels; the Workbench plays the clip back frame by frame (GET /api/slowmo).
public final class SlowMoCamera {
    public static final double FPS = 240, SECONDS = 10;
    private static final int CAPACITY = (int) (FPS * SECONDS);

    private final List<String> names = new ArrayList<>();
    private final List<DoubleSupplier> sources = new ArrayList<>();
    private double[][] frames = new double[CAPACITY][0];
    private double[] prev = new double[0];
    private double prevT = Double.NaN, nextFrame, clipStart;
    private int head, count;
    private boolean recording;

    static {
        Server.route("slowmo", (b, q) -> {
            World w = Sim.get().world;
            return w == null ? null : w.robot.servoMechs.camera.clip();
        });
    }

    public void addChannel(String name, DoubleSupplier source) {
        synchronized (World.LOCK) {
            names.add(name);
            sources.add(source);
            frames = new double[CAPACITY][names.size()];
            prev = new double[names.size()];
            prevT = Double.NaN;
            count = 0;
        }
    }

    public boolean recording() {
        return recording;
    }

    // starting throws the old clip away
    public void record(boolean on, double now) {
        if (on && !recording) {
            count = 0;
            head = 0;
            nextFrame = now;
            clipStart = now;
        }
        recording = on;
    }

    // physics thread, once per step
    public void sample(double now) {
        int n = sources.size();
        double[] cur = new double[n];
        for (int i = 0; i < n; i++) cur[i] = sources.get(i).getAsDouble();
        if (recording && !Double.isNaN(prevT)) {
            while (nextFrame <= now) {
                double f = now > prevT ? Math.max(0, (nextFrame - prevT) / (now - prevT)) : 1;
                double[] fr = frames[head];
                for (int i = 0; i < n; i++) fr[i] = prev[i] + (cur[i] - prev[i]) * Math.min(1, f);
                head = (head + 1) % CAPACITY;
                count = Math.min(CAPACITY, count + 1);
                nextFrame += 1 / FPS;
            }
        }
        prev = cur;
        prevT = now;
    }

    public Map<String, Object> clip() {
        synchronized (World.LOCK) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("fps", FPS);
            m.put("recording", recording);
            m.put("channels", new ArrayList<>(names));
            List<double[]> out = new ArrayList<>(count);
            for (int k = 0; k < count; k++) {
                double[] fr = frames[(head - count + k + CAPACITY) % CAPACITY];
                double[] r = new double[fr.length];
                for (int i = 0; i < fr.length; i++) r[i] = Math.round(fr[i] * 1000) / 1000.0;
                out.add(r);
            }
            m.put("frames", out);
            m.put("startTime", clipStart + Math.max(0, frameCountSinceStart() - count) / FPS);
            return m;
        }
    }

    private long frameCountSinceStart() {
        return Math.round((nextFrame - clipStart) * FPS);
    }
}
