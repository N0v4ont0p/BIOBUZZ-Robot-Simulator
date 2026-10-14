package org.biobuzz.sim.rc;

import com.qualcomm.robotcore.hardware.CRServoImplEx;
import com.qualcomm.robotcore.hardware.DcMotorImplEx;
import com.qualcomm.robotcore.hardware.DcMotorSimple;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.ServoImplEx;
import com.qualcomm.robotcore.hardware.configuration.ConfigurationType;
import com.qualcomm.robotcore.hardware.configuration.ConfigurationTypeManager;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.MotorConfigurationType;
import com.qualcomm.robotcore.hardware.configuration.typecontainers.ServoConfigurationType;

import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.hw.SimMotorController;
import org.biobuzz.sim.hw.SimPinpointDriver;
import org.biobuzz.sim.hw.SimServoController;
import org.biobuzz.sim.hw.SimVoltageSensor;
import org.biobuzz.sim.phys.Pinpoint;
import org.biobuzz.sim.phys.Robot;
import org.biobuzz.sim.truth.Truth;

import java.util.ArrayList;
import java.util.List;

// Builds the HardwareMap from the active configuration, the way the Robot Controller's HardwareFactory does:
// a device exists under its configured name and type, on the configured hub and port, whatever is physically
// plugged in there. Problems the real app would report go into warnings.
public final class HardwareBuilder {
    public final List<String> warnings = new ArrayList<>();

    public HardwareMap build(RobotConfig config, Robot robot, android.content.Context context, org.biobuzz.sim.hw.Lease lease) {
        HardwareMap map = new HardwareMap(context, null);
        if (config == null) return map;
        ConfigurationTypeManager types = ConfigurationTypeManager.getInstance();
        for (RobotConfig.Module m : config.modules) {
            Hub hub = null;
            for (Hub h : robot.hubs) if (h.address == m.address) hub = h;
            if (hub == null) {
                warnings.add("\"" + m.name + "\" (address " + m.address + ") is in the configuration but not attached");
                continue;
            }
            SimMotorController mc = new SimMotorController(hub, lease);
            SimServoController sc = new SimServoController(hub, lease);
            org.biobuzz.sim.hw.SimDigitalController dc = new org.biobuzz.sim.hw.SimDigitalController(hub, robot, lease);
            map.dcMotorController.put(m.name, mc);
            map.servoController.put(m.name, sc);
            map.voltageSensor.put(m.name, new SimVoltageSensor(hub, lease));
            for (RobotConfig.Device d : m.motors) {
                ConfigurationType t = types.configurationTypeFromTag(d.type);
                if (!(t instanceof MotorConfigurationType)) {
                    warnings.add("motor \"" + d.name + "\": unknown type " + d.type);
                    continue;
                }
                MotorConfigurationType mt = (MotorConfigurationType) t;
                mc.setMotorType(d.port, mt);
                map.dcMotor.put(d.name, new DcMotorImplEx(mc, d.port, DcMotorSimple.Direction.FORWARD, mt));
            }
            for (RobotConfig.Device d : m.servos) {
                ConfigurationType t = types.configurationTypeFromTag(d.type);
                if (d.type.equals("ContinuousRotationServo")) {
                    map.crservo.put(d.name, new CRServoImplEx(sc, d.port, (ServoConfigurationType) t));
                } else if (t instanceof ServoConfigurationType) {
                    map.servo.put(d.name, new ServoImplEx(sc, d.port, (ServoConfigurationType) t));
                } else {
                    warnings.add("servo \"" + d.name + "\": unknown type " + d.type);
                }
            }
            for (RobotConfig.Device d : m.digital) {
                // the real app makes a plain DigitalChannel only for "Digital Device"; other digital types are other classes
                if (d.type.equals("DigitalDevice")) map.digitalChannel.put(d.name, new com.qualcomm.robotcore.hardware.DigitalChannelImpl(dc, d.port));
                else warnings.add("digital device \"" + d.name + "\" (" + d.type + ") is not simulated");
            }
            for (RobotConfig.Device d : m.i2c) {
                String there = d.bus >= 0 && d.bus < 4 ? hub.i2c[d.bus] : null;
                if (d.type.equals(Devices.PINPOINT_TAG)) {
                    Pinpoint pp = "pinpoint".equals(there) ? robot.pinpoint : disconnectedPinpoint();
                    if (pp != robot.pinpoint) warnings.add("I2C device \"" + d.name + "\" on " + m.name + " bus " + d.bus + " is not responding");
                    map.put(d.name, new SimPinpointDriver(pp));
                } else {
                    warnings.add("I2C device \"" + d.name + "\" (" + d.type + ") is not simulated");
                }
            }
        }
        return map;
    }

    private static Pinpoint disconnectedPinpoint() {
        Pinpoint p = new Pinpoint(null, Truth.empty());
        p.connected = false;
        return p;
    }
}
