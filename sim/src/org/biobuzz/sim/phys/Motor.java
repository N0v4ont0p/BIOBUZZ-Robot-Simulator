package org.biobuzz.sim.phys;

// One DC motor with its gearbox. The hub port sets duty (-1..1) and brake; the mechanism that owns the motor
// keeps outAngle/outRate (output shaft) up to date and asks for the output torque every physics step.
public final class Motor {
    public final String id;
    public final MotorSpec spec;
    public volatile double duty;
    public volatile boolean brake = false;
    public volatile boolean driven = false;   // false = H-bridge off (motor coasts whatever brake says)
    public double outAngle, outRate;
    public double current;

    public Motor(String id, MotorSpec spec) {
        this.id = id;
        this.spec = spec;
    }

    // output shaft torque (N*m) at the present output speed and battery voltage
    public double torque(double busVolts) {
        double motorRate = outRate * spec.gear;
        double d = driven ? duty : 0;
        if (!driven || (d == 0 && !brake)) {
            current = 0;
        } else {
            double v = d * busVolts;
            current = (v - spec.kE * motorRate) / spec.resistance;
        }
        double t = spec.kT * current * spec.gear;
        return current * motorRate > 0 ? t * spec.gearEfficiency : t;
    }

    // gearbox and brush friction seen at the output shaft
    public double frictionTorque() {
        return spec.kT * spec.freeAmps * spec.gear;
    }

    public double reflectedInertia() {
        return spec.rotorInertia * spec.gear * spec.gear;
    }

    public double batteryAmps() {
        return driven ? Math.abs(duty * current) : 0;
    }
}
