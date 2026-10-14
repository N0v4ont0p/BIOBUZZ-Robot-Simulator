package org.biobuzz.sim.hw;

import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.PwmControl;
import com.qualcomm.robotcore.hardware.ServoControllerEx;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.ServoConfigurationType;
import com.qualcomm.robotcore.util.Range;

import org.biobuzz.sim.phys.World;

// The hub's servo controller as the SDK sees it (stands in for LynxServoController). Setting a position turns
// that port's PWM on; pwmDisable (done by the SDK when an OpMode stops) makes every servo go limp.
public final class SimServoController implements ServoControllerEx {
    private final Hub hub;
    private final Lease lease;

    public SimServoController(Hub hub, Lease lease) {
        this.hub = hub;
        this.lease = lease;
    }

    private ServoPort port(int p) {
        if (p < 0 || p > 5) throw new IllegalArgumentException(String.format("servo %d is invalid; valid servos are 0..5", p));
        return hub.servos[p];
    }

    @Override
    public void pwmEnable() {
        lease.check();
        synchronized (World.LOCK) {
            for (ServoPort s : hub.servos) if (!Double.isNaN(s.position)) s.pwmEnabled = true;
        }
    }

    @Override
    public void pwmDisable() {
        lease.check();
        synchronized (World.LOCK) {
            for (ServoPort s : hub.servos) s.pwmEnabled = false;
        }
    }

    @Override
    public PwmStatus getPwmStatus() {
        lease.check();
        synchronized (World.LOCK) {
            boolean any = false, all = true;
            for (ServoPort s : hub.servos) {
                any |= s.pwmEnabled;
                all &= s.pwmEnabled;
            }
            return all ? PwmStatus.ENABLED : any ? PwmStatus.MIXED : PwmStatus.DISABLED;
        }
    }

    @Override
    public void setServoPosition(int p, double position) {
        lease.check();
        boolean changed;
        synchronized (World.LOCK) {
            ServoPort s = port(p);
            double v = Range.clip(position, 0, 1);
            changed = !s.pwmEnabled || s.position != v;
            s.position = v;
            s.pwmEnabled = true;
        }
        if (changed) hub.writeDelay();
    }

    @Override
    public double getServoPosition(int p) {
        lease.check();
        synchronized (World.LOCK) {
            return port(p).position;
        }
    }

    @Override
    public void forgetLastKnownPosition(int p) {
        lease.check();
    }

    @Override
    public void setServoPwmRange(int p, PwmControl.PwmRange range) {
        lease.check();
        synchronized (World.LOCK) {
            port(p).range = range;
        }
    }

    @Override
    public PwmControl.PwmRange getServoPwmRange(int p) {
        lease.check();
        synchronized (World.LOCK) {
            return port(p).range;
        }
    }

    @Override
    public void setServoPwmEnable(int p) {
        lease.check();
        synchronized (World.LOCK) {
            ServoPort s = port(p);
            if (!Double.isNaN(s.position)) s.pwmEnabled = true;
        }
    }

    @Override
    public void setServoPwmDisable(int p) {
        lease.check();
        synchronized (World.LOCK) {
            port(p).pwmEnabled = false;
        }
    }

    @Override
    public boolean isServoPwmEnabled(int p) {
        lease.check();
        synchronized (World.LOCK) {
            return port(p).pwmEnabled;
        }
    }

    @Override
    public void setServoType(int p, ServoConfigurationType type) {
        lease.check();
    }

    @Override
    public void setPulseWidth(int p, double usPulse) {
        lease.check();
        synchronized (World.LOCK) {
            ServoPort s = port(p);
            s.position = Range.clip((usPulse - s.range.usPulseLower) / (s.range.usPulseUpper - s.range.usPulseLower), 0, 1);
            s.pwmEnabled = true;
        }
    }

    @Override
    public double getPulseWidth(int p) {
        lease.check();
        synchronized (World.LOCK) {
            ServoPort s = port(p);
            return Double.isNaN(s.position) ? 0 : s.pulseUs();
        }
    }

    @Override
    public HardwareDevice.Manufacturer getManufacturer() {
        return HardwareDevice.Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return "REV Expansion Hub Servo Controller";
    }

    @Override
    public String getConnectionInfo() {
        return hub.name + "; module " + hub.address;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    // between OpModes: default pulse ranges, signal off
    @Override
    public void resetDeviceConfigurationForOpMode() {
        synchronized (World.LOCK) {
            for (ServoPort s : hub.servos) {
                s.range = PwmControl.PwmRange.defaultRange;
                s.pwmEnabled = false;
            }
        }
    }

    @Override
    public void close() {
    }
}
