package org.biobuzz.sim.hw;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.DcMotorControllerEx;
import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.PIDCoefficients;
import com.qualcomm.robotcore.hardware.PIDFCoefficients;
import com.qualcomm.robotcore.hardware.configuration.ExpansionHubMotorControllerParamsState;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType;
import com.qualcomm.robotcore.util.Range;

import org.biobuzz.sim.phys.World;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.CurrentUnit;

// The hub's motor controller as the SDK sees it (stands in for LynxDcMotorController). Same rules as the real
// one: power in RUN_USING_ENCODER is a fraction of the motor type's achievable top speed, setVelocity switches to
// RUN_USING_ENCODER, the motor type sets the default velocity PIDF.
public final class SimMotorController implements DcMotorControllerEx {
    private final Hub hub;
    private final Lease lease;
    private final MotorConfigurationType[] configured = new MotorConfigurationType[4];

    public SimMotorController(Hub hub, Lease lease) {
        this.hub = hub;
        this.lease = lease;
    }

    private MotorPort port(int p) {
        if (p < 0 || p > 3) throw new IllegalArgumentException(String.format("motor %d is invalid; valid motors are 0..3", p));
        return hub.motors[p];
    }

    @Override
    public void setMotorType(int p, MotorConfigurationType type) {
        lease.check();
        hub.writeDelay();
        synchronized (World.LOCK) {
            if (configured[p] == null) configured[p] = type;
            applyType(port(p), type);
        }
    }

    private static void applyType(MotorPort m, MotorConfigurationType type) {
        m.type = type;
        if (type.hasExpansionHubVelocityParams()) {
            ExpansionHubMotorControllerParamsState v = type.getHubVelocityParams();
            m.velocityPidf = new PIDFCoefficients(v.p, v.i, v.d, v.f);
        }
        if (type.hasExpansionHubPositionParams()) {
            ExpansionHubMotorControllerParamsState v = type.getHubPositionParams();
            m.positionPidf = new PIDFCoefficients(v.p, v.i, v.d, v.f);
        }
    }

    @Override
    public MotorConfigurationType getMotorType(int p) {
        lease.check();
        synchronized (World.LOCK) {
            MotorConfigurationType t = port(p).type;
            return t == null ? MotorConfigurationType.getUnspecifiedMotorType() : t;
        }
    }

    @Override
    public void setMotorMode(int p, DcMotor.RunMode mode) {
        lease.check();
        // like the real controller, a command that changes nothing is not sent (no hub time)
        synchronized (World.LOCK) {
            if (port(p).mode == mode && mode != DcMotor.RunMode.STOP_AND_RESET_ENCODER) return;
        }
        hub.writeDelay();
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            if (mode == DcMotor.RunMode.STOP_AND_RESET_ENCODER) {
                m.resetEncoder();
                m.power = 0;
                m.targetVelocity = 0;
            } else if (m.mode != mode) {
                // the hub keeps the same output when switching modes: carry power over to the new meaning
                double power = m.power;
                m.mode = mode;
                applyPower(m, power);
            }
            m.mode = mode;
            m.resetController();
        }
    }

    @Override
    public DcMotor.RunMode getMotorMode(int p) {
        lease.check();
        synchronized (World.LOCK) {
            return port(p).mode;
        }
    }

    @Override
    public void setMotorPower(int p, double power) {
        lease.check();
        boolean changed;
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            double v = Range.clip(power, -1, 1);
            changed = !m.enabled || m.power != v;
            applyPower(m, v);
            m.enabled = true;
        }
        if (changed) hub.writeDelay();
    }

    private static void applyPower(MotorPort m, double power) {
        m.power = power;
        if (m.mode == DcMotor.RunMode.RUN_USING_ENCODER) m.targetVelocity = Math.round(power * m.maxTicksPerSecond());
    }

    @Override
    public double getMotorPower(int p) {
        lease.check();
        hub.readDelay();
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            if (m.mode == DcMotor.RunMode.RUN_USING_ENCODER) return Range.clip(m.targetVelocity / m.maxTicksPerSecond(), -1, 1);
            return m.power;
        }
    }

    @Override
    public boolean isBusy(int p) {
        lease.check();
        hub.bulkRead("busy" + p);
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            return m.mode == DcMotor.RunMode.RUN_TO_POSITION && Math.abs(m.targetPosition - m.position()) > m.tolerance;
        }
    }

    @Override
    public void setMotorZeroPowerBehavior(int p, DcMotor.ZeroPowerBehavior z) {
        lease.check();
        if (z == DcMotor.ZeroPowerBehavior.UNKNOWN) throw new IllegalArgumentException("zeroPowerBehavior may not be UNKNOWN");
        synchronized (World.LOCK) {
            if (port(p).zeroPower == z) return;
        }
        hub.writeDelay();
        synchronized (World.LOCK) {
            port(p).zeroPower = z;
        }
    }

    @Override
    public DcMotor.ZeroPowerBehavior getMotorZeroPowerBehavior(int p) {
        lease.check();
        synchronized (World.LOCK) {
            return port(p).zeroPower;
        }
    }

    @Override
    public boolean getMotorPowerFloat(int p) {
        lease.check();
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            return m.zeroPower == DcMotor.ZeroPowerBehavior.FLOAT && m.power == 0;
        }
    }

    @Override
    public void setMotorTargetPosition(int p, int position) {
        lease.check();
        setMotorTargetPosition(p, position, 5);
    }

    @Override
    public void setMotorTargetPosition(int p, int position, int tolerance) {
        lease.check();
        hub.writeDelay();
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            m.targetPosition = position;
            m.tolerance = tolerance;
            m.targetPositionSet = true;
        }
    }

    @Override
    public int getMotorTargetPosition(int p) {
        lease.check();
        synchronized (World.LOCK) {
            return port(p).targetPosition;
        }
    }

    @Override
    public int getMotorCurrentPosition(int p) {
        lease.check();
        hub.bulkRead("position" + p);
        synchronized (World.LOCK) {
            return port(p).position();
        }
    }

    @Override
    public void resetDeviceConfigurationForOpMode(int p) {
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            // back to the configured motor type and its default PIDF, like the real hub between OpModes
            if (configured[p] != null) applyType(m, configured[p]);
            m.resetController();
        }
    }

    @Override
    public void setMotorEnable(int p) {
        lease.check();
        synchronized (World.LOCK) {
            port(p).enabled = true;
        }
    }

    @Override
    public void setMotorDisable(int p) {
        lease.check();
        synchronized (World.LOCK) {
            port(p).enabled = false;
        }
    }

    @Override
    public boolean isMotorEnabled(int p) {
        lease.check();
        synchronized (World.LOCK) {
            return port(p).enabled;
        }
    }

    @Override
    public void setMotorVelocity(int p, double ticksPerSecond) {
        lease.check();
        boolean changed;
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            changed = !m.enabled || m.targetVelocity != Math.round(ticksPerSecond);
            if (m.mode != DcMotor.RunMode.RUN_USING_ENCODER && m.mode != DcMotor.RunMode.RUN_TO_POSITION) {
                m.mode = DcMotor.RunMode.RUN_USING_ENCODER;
                m.resetController();
                changed = true;
            }
            m.targetVelocity = Math.round(ticksPerSecond);
            m.power = Range.clip(ticksPerSecond / m.maxTicksPerSecond(), -1, 1);
            m.enabled = true;
        }
        if (changed) hub.writeDelay();
    }

    @Override
    public void setMotorVelocity(int p, double angularRate, AngleUnit unit) {
        lease.check();
        double degPerS = unit.toDegrees(angularRate);
        double ticksPerRev;
        synchronized (World.LOCK) {
            ticksPerRev = getMotorType(p).getTicksPerRev();
        }
        setMotorVelocity(p, degPerS / 360.0 * ticksPerRev);
    }

    @Override
    public double getMotorVelocity(int p) {
        lease.check();
        hub.bulkRead("velocity" + p);
        synchronized (World.LOCK) {
            return port(p).measuredVelocity;
        }
    }

    @Override
    public double getMotorVelocity(int p, AngleUnit unit) {
        lease.check();
        double ticksPerS = getMotorVelocity(p);
        double ticksPerRev;
        synchronized (World.LOCK) {
            ticksPerRev = getMotorType(p).getTicksPerRev();
        }
        return unit.fromDegrees(ticksPerS / ticksPerRev * 360.0);
    }

    @Override
    public void setPIDCoefficients(int p, DcMotor.RunMode mode, PIDCoefficients c) {
        lease.check();
        setPIDFCoefficients(p, mode, new PIDFCoefficients(c.p, c.i, c.d, 0));
    }

    @Override
    public void setPIDFCoefficients(int p, DcMotor.RunMode mode, PIDFCoefficients c) {
        lease.check();
        hub.writeDelay();
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            if (mode == DcMotor.RunMode.RUN_TO_POSITION) m.positionPidf = new PIDFCoefficients(c.p, c.i, c.d, c.f);
            else if (mode == DcMotor.RunMode.RUN_USING_ENCODER) m.velocityPidf = new PIDFCoefficients(c.p, c.i, c.d, c.f);
            else throw new UnsupportedOperationException("PIDF coefficients only apply to RUN_USING_ENCODER and RUN_TO_POSITION");
        }
    }

    @Override
    public PIDCoefficients getPIDCoefficients(int p, DcMotor.RunMode mode) {
        lease.check();
        PIDFCoefficients f = getPIDFCoefficients(p, mode);
        return new PIDCoefficients(f.p, f.i, f.d);
    }

    @Override
    public PIDFCoefficients getPIDFCoefficients(int p, DcMotor.RunMode mode) {
        lease.check();
        hub.readDelay();
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            PIDFCoefficients c = mode == DcMotor.RunMode.RUN_TO_POSITION ? m.positionPidf : m.velocityPidf;
            return new PIDFCoefficients(c.p, c.i, c.d, c.f);
        }
    }

    @Override
    public double getMotorCurrent(int p, CurrentUnit unit) {
        lease.check();
        hub.readDelay();
        synchronized (World.LOCK) {
            return unit.convert(port(p).amps(), CurrentUnit.AMPS);
        }
    }

    @Override
    public double getMotorCurrentAlert(int p, CurrentUnit unit) {
        lease.check();
        synchronized (World.LOCK) {
            return unit.convert(port(p).currentAlertAmps, CurrentUnit.AMPS);
        }
    }

    @Override
    public void setMotorCurrentAlert(int p, double current, CurrentUnit unit) {
        lease.check();
        synchronized (World.LOCK) {
            port(p).currentAlertAmps = CurrentUnit.AMPS.convert(current, unit);
        }
    }

    @Override
    public boolean isMotorOverCurrent(int p) {
        lease.check();
        synchronized (World.LOCK) {
            MotorPort m = port(p);
            return m.amps() > m.currentAlertAmps;
        }
    }

    // stop of an OpMode (and failsafe): motors off, the hub keeps modes and settings
    @Override
    public void resetDeviceConfigurationForOpMode() {
        synchronized (World.LOCK) {
            for (int p = 0; p < 4; p++) {
                resetDeviceConfigurationForOpMode(p);
                MotorPort m = hub.motors[p];
                m.power = 0;
                m.targetVelocity = 0;
                m.enabled = false;
            }
        }
    }

    @Override
    public HardwareDevice.Manufacturer getManufacturer() {
        return HardwareDevice.Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return "REV Expansion Hub Motor Controller";
    }

    @Override
    public String getConnectionInfo() {
        return hub.name + "; module " + hub.address;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void close() {
    }

    public Hub hub() {
        return hub;
    }
}
