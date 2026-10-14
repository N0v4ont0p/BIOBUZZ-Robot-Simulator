package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;

import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.truth.Truth;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

// Physics thread: 1 kHz, real time. Everything physical (hubs' firmware, motors, parts, battery) steps here under
// the world lock; hub controllers called from OpMode threads take the same lock.
public final class World {
    public static final double DT = 0.001;
    // one lock for all physical state: the physics thread, hub controllers (OpMode threads) and the web server
    public static final Object LOCK = new Object();
    public final Truth truth;
    public final Robot robot;
    public double time;
    private volatile boolean running;
    private Thread thread;

    public World(Truth truth) {
        this.truth = truth;
        this.robot = new Robot(truth);
    }

    public void step(double dt) {
        synchronized (LOCK) {
            Battery b = robot.battery;
            for (Hub h : robot.hubs) h.firmwareStep(dt, b.volts);
            for (Part p : robot.parts) p.step(dt, this);
            for (Motor m : robot.motors.values()) b.draw(m.batteryAmps());
            b.step(dt);
            for (Hub h : robot.hubs) h.sample(dt);
            time += dt;
        }
    }

    public void start() {
        running = true;
        thread = new Thread(() -> {
            long next = System.nanoTime();
            while (running) {
                long now = System.nanoTime();
                int steps = 0;
                while (now >= next && steps < 20) {
                    step(DT);
                    next += (long) (DT * 1e9);
                    steps++;
                }
                if (now - next > 50_000_000L) next = now;
                LockSupport.parkNanos(Math.max(100_000L, next - System.nanoTime()));
            }
        }, "physics");
        thread.setDaemon(true);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public Map<String, Object> snapshot() {
        synchronized (LOCK) {
            return snapshotLocked();
        }
    }

    private Map<String, Object> snapshotLocked() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("time", time);
        Map<String, Object> bat = new LinkedHashMap<>();
        bat.put("volts", robot.battery.volts);
        bat.put("charge", robot.battery.charge);
        bat.put("amps", robot.battery.lastAmps);
        out.put("battery", bat);
        out.put("onBox", robot.onBox);
        for (Part p : robot.parts) {
            Map<String, Object> m = new LinkedHashMap<>();
            p.state(m);
            out.put(p.id(), m);
        }
        out.put("hubs", robot.hubState());
        return out;
    }

    public Map<String, Expected> expected() {
        synchronized (LOCK) {
            Map<String, Expected> out = new LinkedHashMap<>();
            for (Part p : robot.parts) p.expected(out, this);
            return out;
        }
    }

    public boolean hand(String part, String action, JsonObject args) {
        synchronized (LOCK) {
            if (part.equals("robot")) return robot.hand(action, args, this);
            for (Part p : robot.parts) if (p.id().equals(part)) return p.hand(action, args, this);
            return false;
        }
    }
}
