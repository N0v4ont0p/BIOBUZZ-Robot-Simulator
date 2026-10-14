package org.biobuzz.sim.hw;

import org.biobuzz.sim.truth.Truth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

// A REV Control Hub or Expansion Hub: 4 motor ports, 6 servo ports, 4 I2C buses, 8 digital ports, a battery voltage reading.
// Commands from robot code cost a little time, like the real hub (the Expansion Hub is further away, over RS-485).
public final class Hub {
    public final String name;
    public final int address;
    public final boolean isControlHub;
    public final MotorPort[] motors = new MotorPort[4];
    public final ServoPort[] servos = new ServoPort[6];
    public final String[] i2c = new String[4];   // part id on each bus (only the Pinpoint here)
    public final String[] digital = new String[8];   // what is plugged into each digital port ("yawIndex": the turret's magnetic switch)
    private final long readNanos, writeNanos;
    public final double voltageDrop;
    public volatile double volts;

    public Hub(String name, int address, boolean isControlHub, Truth t) {
        this.name = name;
        this.address = address;
        this.isControlHub = isControlHub;
        double window = t.num("hub.velocityWindowS", 0.05), pid = t.num("hub.pidPeriodS", 0.01);
        for (int i = 0; i < 4; i++) motors[i] = new MotorPort(this, i, window, pid);
        for (int i = 0; i < 6; i++) servos[i] = new ServoPort(this, i);
        // a REV hub command takes ~1.3 ms on the Control Hub, ~2.5 ms on the Expansion Hub (RS-485)
        readNanos = (long) (t.num(isControlHub ? "hub.chReadMs" : "hub.ehReadMs", isControlHub ? 1.3 : 2.5) * 1e6);
        writeNanos = (long) (t.num(isControlHub ? "hub.chWriteMs" : "hub.ehWriteMs", isControlHub ? 1.3 : 2.5) * 1e6);
        voltageDrop = isControlHub ? 0 : 0.05;
    }

    public void firmwareStep(double dt, double batteryVolts) {
        volts = batteryVolts - voltageDrop;
        for (MotorPort p : motors) p.firmware(dt);
        for (ServoPort s : servos) s.firmware();
    }

    public void sample(double dt) {
        for (MotorPort p : motors) p.sample();
    }

    // Bulk caching (the team's OpModes use LynxModule AUTO): one bulk read fetches every encoder position, velocity
    // and busy flag of the hub; reading the same value a second time starts a new bulk read.
    private final java.util.Set<String> readSinceBulk = new java.util.HashSet<>();
    private boolean bulkValid;

    public void bulkRead(String item) {
        boolean fetch;
        synchronized (readSinceBulk) {
            fetch = !bulkValid || readSinceBulk.contains(item);
            if (fetch) {
                readSinceBulk.clear();
                bulkValid = true;
            }
            readSinceBulk.add(item);
        }
        if (fetch) readDelay();
    }

    public void readDelay() {
        if (readNanos > 0) LockSupport.parkNanos(readNanos);
    }

    public void writeDelay() {
        if (writeNanos > 0) LockSupport.parkNanos(writeNanos);
    }

    public Map<String, Object> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("address", address);
        List<Object> ms = new ArrayList<>(), ss = new ArrayList<>();
        for (MotorPort p : motors) ms.add(p.state());
        for (ServoPort s : servos) ss.add(s.state());
        m.put("motors", ms);
        m.put("servos", ss);
        m.put("i2c", i2c);
        m.put("digital", digital);
        m.put("volts", volts);
        return m;
    }
}
