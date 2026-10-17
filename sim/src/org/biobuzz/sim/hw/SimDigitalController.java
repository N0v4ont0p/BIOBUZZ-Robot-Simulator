package org.biobuzz.sim.hw;

import com.qualcomm.robotcore.hardware.DigitalChannel;
import com.qualcomm.robotcore.hardware.DigitalChannelController;
import com.qualcomm.robotcore.util.SerialNumber;

import org.biobuzz.sim.phys.Robot;

// A hub's 8 digital ports. Inputs read what is plugged in (Hub.digital): a REV magnetic limit switch reads
// false while its magnet is in front of it, true otherwise (nothing plugged in also reads true: pull-up).
// A read is one hub command, like the real one.
public final class SimDigitalController implements DigitalChannelController {
    private final Hub hub;
    private final Robot robot;
    private final Lease lease;
    private final DigitalChannel.Mode[] modes = new DigitalChannel.Mode[8];
    private final boolean[] outputs = new boolean[8];

    public SimDigitalController(Hub hub, Robot robot, Lease lease) {
        this.hub = hub;
        this.robot = robot;
        this.lease = lease;
        java.util.Arrays.fill(modes, DigitalChannel.Mode.INPUT);
    }

    @Override
    public SerialNumber getSerialNumber() {
        return SerialNumber.createFake();
    }

    @Override
    public DigitalChannel.Mode getDigitalChannelMode(int port) {
        return modes[port];
    }

    @Override
    public void setDigitalChannelMode(int port, DigitalChannel.Mode mode) {
        lease.check();
        hub.writeDelay();
        modes[port] = mode;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void setDigitalChannelMode(int port, DigitalChannelController.Mode mode) {
        setDigitalChannelMode(port, mode == DigitalChannelController.Mode.OUTPUT ? DigitalChannel.Mode.OUTPUT : DigitalChannel.Mode.INPUT);
    }

    @Override
    public boolean getDigitalChannelState(int port) {
        lease.check();
        if (modes[port] == DigitalChannel.Mode.OUTPUT) return outputs[port];
        hub.readDelay();
        // nothing on this robot pulls a digital input low (an open input reads high)
        return true;
    }

    @Override
    public void setDigitalChannelState(int port, boolean state) {
        lease.check();
        hub.writeDelay();
        outputs[port] = state;
    }

    @Override
    public Manufacturer getManufacturer() {
        return Manufacturer.Lynx;
    }

    @Override
    public String getDeviceName() {
        return hub.name + " digital";
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
