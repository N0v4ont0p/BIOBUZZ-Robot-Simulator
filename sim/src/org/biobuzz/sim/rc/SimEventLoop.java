package org.biobuzz.sim.rc;

import android.hardware.usb.UsbDevice;

import com.qualcomm.robotcore.eventloop.EventLoop;
import com.qualcomm.robotcore.eventloop.EventLoopManager;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.usb.RobotUsbModule;
import com.qualcomm.robotcore.robocol.Command;
import com.qualcomm.robotcore.robocol.TelemetryMessage;
import com.qualcomm.robotcore.util.SerialNumber;

import org.firstinspires.ftc.robotcore.internal.network.CallbackResult;
import org.firstinspires.ftc.robotcore.internal.network.NetworkConnectionHandler;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

// What FtcEventLoop does on the robot, minus USB: run commands from the Driver Station, hand the latest gamepad
// data to the OpMode manager every iteration, send telemetry and gamepad effects back.
final class SimEventLoop implements EventLoop {
    private final RobotController rc;
    final Gamepad[] opModeGamepads = {new SimGamepad(), new SimGamepad()};
    volatile Gamepad latest1 = new Gamepad(), latest2 = new Gamepad();
    private final ConcurrentLinkedQueue<Command> commands = new ConcurrentLinkedQueue<>();
    private final AtomicReference<OpMode> stopRequested = new AtomicReference<>();
    private long lastTelemetryNs;

    SimEventLoop(RobotController rc) {
        this.rc = rc;
    }

    void command(Command c) {
        commands.add(c);
    }

    @Override
    public void init(EventLoopManager m) {
    }

    @Override
    public void loop() {
        OpModeManagerImpl manager = rc.manager;
        Command c;
        while ((c = commands.poll()) != null) {
            if (c.getName().equals("CMD_INIT_OP_MODE")) {
                manager.initOpMode(c.getExtra());
            } else if (c.getName().equals("CMD_RUN_OP_MODE")) {
                if (manager.getActiveOpModeName().equals(c.getExtra())) manager.startActiveOpMode();
            }
        }
        OpMode stop = stopRequested.getAndSet(null);
        if (stop != null && stop == manager.getActiveOpMode()) manager.stopActiveOpMode();
        manager.runActiveOpMode(opModeGamepads, latest1, latest2);
        effects();
    }

    private void effects() {
        for (int i = 0; i < 2; i++) {
            Gamepad g = opModeGamepads[i];
            Gamepad.RumbleEffect r;
            while ((r = g.rumbleQueue.poll()) != null) rc.ds.event("rumble", Effects.rumble(i + 1, r));
            Gamepad.LedEffect l;
            while ((l = g.ledQueue.poll()) != null) rc.ds.event("led", Effects.led(i + 1, l));
        }
    }

    @Override
    public void refreshUserTelemetry(TelemetryMessage msg, double sInterval) {
        long now = System.nanoTime();
        double interval = sInterval < 0 ? TELEMETRY_DEFAULT_INTERVAL : sInterval;
        if (interval <= 0 || (now - lastTelemetryNs) / 1e9 >= interval) {
            lastTelemetryNs = now;
            NetworkConnectionHandler.getInstance().sendDataToPeer(msg);
            msg.clearData();
        }
    }

    @Override
    public void teardown() {
    }

    @Override
    public void onUsbDeviceAttached(UsbDevice d) {
    }

    @Override
    public void pendUsbDeviceAttachment(SerialNumber s, long t, TimeUnit u) {
    }

    @Override
    public void processedRecentlyAttachedUsbDevices() {
    }

    @Override
    public void handleUsbModuleDetach(RobotUsbModule m) {
    }

    @Override
    public void handleUsbModuleAttach(RobotUsbModule m) {
    }

    @Override
    public CallbackResult processCommand(Command c) {
        command(c);
        return CallbackResult.HANDLED;
    }

    @Override
    public OpModeManagerImpl getOpModeManager() {
        return rc.manager;
    }

    @Override
    public void requestOpModeStop(OpMode opMode) {
        stopRequested.set(opMode);
    }
}
