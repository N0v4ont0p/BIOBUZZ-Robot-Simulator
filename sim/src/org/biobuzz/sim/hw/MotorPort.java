package org.biobuzz.sim.hw;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType;

import org.biobuzz.sim.phys.Encoder;
import org.biobuzz.sim.phys.Motor;

import java.util.LinkedHashMap;
import java.util.Map;

// One motor port on a hub, with its firmware: open-loop power, the hub's velocity PIDF (RUN_USING_ENCODER),
// RUN_TO_POSITION, and the encoder velocity the hub reports (counts over a sliding window).
public final class MotorPort {
    public final Hub hub;
    public final int index;
    public Motor motor;       // power cable: which physical motor is plugged in
    public Encoder encoder;   // encoder cable: whose encoder is plugged in

    // firmware state (as the SDK sets it)
    public DcMotor.RunMode mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
    public DcMotor.ZeroPowerBehavior zeroPower = DcMotor.ZeroPowerBehavior.FLOAT;
    public boolean enabled;
    public double power;
    public double targetVelocity;         // ticks/s
    public int targetPosition, tolerance = 5;
    public boolean targetPositionSet;
    public PIDFCoefficients velocityPidf = new PIDFCoefficients(10, 3, 0, 0);
    public PIDFCoefficients positionPidf = new PIDFCoefficients(5, 0, 0, 0);
    public MotorConfigurationType type;
    public double currentAlertAmps = 6.5;

    private long resetOffset;
    private double integral, lastError, pidTimer, pidOutput;
    private final double[] ring;
    private int ringPos;
    private final double pidPeriod;
    public double measuredVelocity;

    MotorPort(Hub hub, int index, double windowS, double pidPeriod) {
        this.hub = hub;
        this.index = index;
        ring = new double[Math.max(2, (int) Math.round(windowS / 0.001))];
        this.pidPeriod = pidPeriod;
    }

    // whole encoder counts, like the real quadrature counter
    public long rawCounts() {
        return encoder == null ? 0 : (long) Math.floor(encoder.counts());
    }

    public int position() {
        return (int) (rawCounts() - resetOffset);
    }

    public void resetEncoder() {
        resetOffset = rawCounts();
        for (int i = 0; i < ring.length; i++) ring[i] = 0;
        measuredVelocity = 0;
    }

    public void resetController() {
        integral = 0;
        lastError = 0;
        pidOutput = 0;
    }

    public double maxTicksPerSecond() {
        return type == null ? 2800 : type.getAchieveableMaxTicksPerSecondRounded();
    }

    // runs every physics step: turns the firmware state into motor duty
    void firmware(double dt) {
        if (motor == null) return;
        double duty;
        switch (mode) {
            case STOP_AND_RESET_ENCODER:
                duty = 0;
                break;
            case RUN_USING_ENCODER:
                duty = velocityLoop(dt, targetVelocity);
                break;
            case RUN_TO_POSITION: {
                if (!targetPositionSet) {
                    duty = 0;
                    break;
                }
                double err = targetPosition - position();
                double maxV = Math.abs(power) * maxTicksPerSecond();
                double v = Math.abs(err) <= tolerance ? 0 : Math.max(-maxV, Math.min(maxV, positionPidf.p * err));
                duty = velocityLoop(dt, v);
                break;
            }
            default:
                duty = power;
                break;
        }
        motor.duty = Math.max(-1, Math.min(1, duty));
        motor.driven = enabled;
        motor.brake = zeroPower == DcMotor.ZeroPowerBehavior.BRAKE;
    }

    private double velocityLoop(double dt, double target) {
        pidTimer += dt;
        if (pidTimer >= pidPeriod) {
            pidTimer = 0;
            double err = target - measuredVelocity;
            PIDFCoefficients k = velocityPidf;
            double iMax = k.i > 0 ? 32767 / k.i : 0;
            integral = Math.max(-iMax, Math.min(iMax, integral + err));
            double d = err - lastError;
            lastError = err;
            pidOutput = (k.f * target + k.p * err + k.i * integral + k.d * d) / 32767.0;
        }
        return pidOutput;
    }

    void sample() {
        double c = rawCounts();
        double old = ring[ringPos];
        ring[ringPos] = c;
        ringPos = (ringPos + 1) % ring.length;
        measuredVelocity = Math.round((c - old) / ((ring.length) * 0.001));
    }

    public double amps() {
        return motor == null ? 0 : Math.abs(motor.current);
    }

    Map<String, Object> state() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("port", index);
        m.put("part", motor == null ? null : motor.id);
        m.put("encoderFrom", encoder == null ? null : encoder.source.id);
        m.put("mode", mode.name());
        m.put("power", power);
        m.put("enabled", enabled);
        m.put("position", position());
        m.put("velocity", measuredVelocity);
        m.put("amps", amps());
        // the hub's velocity gains as the robot code last set them (coach readout)
        m.put("velocityPidf", new double[]{velocityPidf.p, velocityPidf.i, velocityPidf.d, velocityPidf.f});
        return m;
    }
}
