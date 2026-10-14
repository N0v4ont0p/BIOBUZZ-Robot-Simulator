package org.biobuzz.sim.phys;

// A rotating part (wheel, roller, flywheel, turret): inertia, friction, optional hard stops, optional motor
// through an extra gear stage, and a "hand" that can hold or turn it like a student does.
public class Joint {
    public double angle, rate;               // rad, rad/s
    public double inertia = 1e-4;            // kg*m^2, without the motor
    public double staticFriction, kineticFriction, viscous;  // N*m, N*m, N*m per rad/s
    public double min = Double.NEGATIVE_INFINITY, max = Double.POSITIVE_INFINITY;
    public double restitution = 0.0;
    public Motor motor;
    public double gear = 1;                  // motor output turns per joint turn (negative = reversed mesh)
    public double externalTorque;            // set by the owner each step (loads, springs)
    public boolean hitStop;                  // pressed against a stop during the last step
    public double stopTorque;                // how hard it pushed into the stop (N*m)

    // hand: while held, a stiff spring pulls the joint to handTarget; release lets it coast
    public Double handTarget;
    public double handStiffness = 4, handDamping = 0.15, handMaxTorque = 3;

    public void step(double dt, double busVolts) {
        double motorTorque = 0, effInertia = inertia, friction = kineticFriction, stat = staticFriction;
        if (motor != null) {
            motor.outRate = rate * gear;
            motor.outAngle = angle * gear;
            motorTorque = motor.torque(busVolts) * gear;
            effInertia += motor.reflectedInertia() * gear * gear;
            friction += motor.frictionTorque() * Math.abs(gear);
            stat += 1.3 * motor.frictionTorque() * Math.abs(gear);
        }
        double hand = 0;
        if (handTarget != null) {
            hand = handStiffness * (handTarget - angle) - handDamping * rate;
            hand = Math.max(-handMaxTorque, Math.min(handMaxTorque, hand));
        }
        double drive = motorTorque + hand + externalTorque - viscous * rate;
        double net;
        if (Math.abs(rate) < 1e-4) {
            if (Math.abs(drive) <= stat) {
                rate = 0;
                net = 0;
            } else {
                net = drive - Math.signum(drive) * friction;
            }
        } else {
            net = drive - Math.signum(rate) * friction;
        }
        double before = rate;
        rate += net / effInertia * dt;
        if (before != 0 && Math.signum(before) != Math.signum(rate) && Math.abs(drive) <= stat) rate = 0;
        angle += rate * dt;

        hitStop = false;
        stopTorque = 0;
        if (angle < min) {
            angle = min;
            if (rate < 0) rate = -rate * restitution;
            hitStop = true;
            stopTorque = Math.max(0, -drive);
        } else if (angle > max) {
            angle = max;
            if (rate > 0) rate = -rate * restitution;
            hitStop = true;
            stopTorque = Math.max(0, drive);
        }
        if (motor != null) {
            motor.outRate = rate * gear;
            motor.outAngle = angle * gear;
        }
    }

    public void grab() {
        handTarget = angle;
    }

    public void turnBy(double radians) {
        if (handTarget == null) handTarget = angle;
        handTarget += radians;
    }

    public void release() {
        handTarget = null;
    }
}
