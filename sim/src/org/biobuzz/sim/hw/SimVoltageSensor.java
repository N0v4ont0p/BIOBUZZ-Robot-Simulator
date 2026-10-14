package org.biobuzz.sim.hw;

import com.qualcomm.robotcore.hardware.HardwareDevice;
import com.qualcomm.robotcore.hardware.VoltageSensor;

import org.biobuzz.sim.phys.World;

// Each hub measures the battery voltage at its own input.
public final class SimVoltageSensor implements VoltageSensor {
    private final Hub hub;
    private final Lease lease;

    public SimVoltageSensor(Hub hub, Lease lease) {
        this.hub = hub;
        this.lease = lease;
    }

    @Override
    public double getVoltage() {
        lease.check();
        hub.readDelay();
        synchronized (World.LOCK) {
            return Math.round(hub.volts * 1000) / 1000.0;
        }
    }

    @Override
    public HardwareDevice.Manufacturer getManufacturer() {
        return HardwareDevice.Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return "REV Expansion Hub Voltage Sensor";
    }

    @Override
    public String getConnectionInfo() {
        return hub.name;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public void resetDeviceConfigurationForOpMode() {
    }

    @Override
    public void close() {
    }
}
