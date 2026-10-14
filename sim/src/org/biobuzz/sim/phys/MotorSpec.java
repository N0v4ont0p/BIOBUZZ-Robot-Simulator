package org.biobuzz.sim.phys;

// goBILDA 5203 Yellow Jacket motors (datasheet values at 12 V). The motor itself is the same 6000 rpm motor in
// every version; the gearbox changes speed, torque and encoder counts per output turn.
public final class MotorSpec {
    public final String name;
    public final double gear;          // gearbox ratio (motor turns per output turn)
    public final double freeRadS;      // motor shaft, 12 V, no load
    public final double stallTorque;   // motor shaft, N*m
    public final double stallAmps, freeAmps;
    public final double countsPerMotorRev = 28;
    public final double rotorInertia = 2.5e-6;  // kg*m^2 at the motor shaft
    public final double gearEfficiency;
    public final double resistance, kT, kE;

    public MotorSpec(String name, double gear, double gearEfficiency) {
        this.name = name;
        this.gear = gear;
        this.gearEfficiency = gearEfficiency;
        freeRadS = 6000 * 2 * Math.PI / 60;
        stallTorque = 0.1442;
        stallAmps = 9.2;
        freeAmps = 0.25;
        resistance = 12.0 / stallAmps;
        kT = stallTorque / stallAmps;
        kE = (12.0 - freeAmps * resistance) / freeRadS;
    }

    public double countsPerOutputRev() {
        return countsPerMotorRev * gear;
    }

    public static MotorSpec of(String name) {
        switch (name) {
            case "6000": return new MotorSpec("goBILDA 5203 6000 rpm (1:1)", 1.0, 1.0);
            case "1620": return new MotorSpec("goBILDA 5203 1620 rpm (3.7:1)", 3.7, 0.9);
            case "1150": return new MotorSpec("goBILDA 5203 1150 rpm (5.2:1)", 5.2, 0.9);
            case "435": return new MotorSpec("goBILDA 5203 435 rpm (13.7:1)", 13.7, 0.85);
            case "312": return new MotorSpec("goBILDA 5203 312 rpm (19.2:1)", 19.2, 0.85);
            case "223": return new MotorSpec("goBILDA 5203 223 rpm (26.9:1)", 26.9, 0.8);
            default: throw new IllegalArgumentException("unknown motor " + name);
        }
    }
}
