package org.biobuzz.sim.rc;

import android.app.Activity;

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.util.RobotLog;

import org.biobuzz.sim.hw.Lease;
import org.firstinspires.ftc.robotcore.internal.system.AppUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// The SDK's OpModeManagerImpl with one part replaced: the stuck-OpMode monitor. The real one ends by restarting
// the Android app (System.exit); here the same steps end by restarting the simulated Robot Controller.
// Same timing and messages: after the timeout every hardware call throws; if that ends the OpMode within
// 100 ms it was "force stopped", otherwise the app restarts.
final class SimOpModeManager extends OpModeManagerImpl {
    private final Lease lease;
    private final DriverStation ds;

    SimOpModeManager(Activity activity, HardwareMap map, Lease lease, DriverStation ds) {
        super(activity, map);
        this.lease = lease;
        this.ds = ds;
    }

    @Override
    protected void detectStuck(int msTimeout, String method, Runnable runnable, boolean resetDebuggerCheck) {
        CountDownLatch done = new CountDownLatch(1);
        String opMode = getActiveOpModeName();
        boolean linear = getActiveOpMode() instanceof LinearOpMode;
        Thread monitor = new Thread(() -> {
            try {
                if (done.await(msTimeout, TimeUnit.MILLISECONDS)) return;
                lease.throwing = true;
                if (done.await(100, TimeUnit.MILLISECONDS)) {
                    lease.throwing = false;
                    ds.event("dialog", "OpMode Force-Stopped: " + String.format(linear
                            ? "User OpMode was stuck in %s, but was able to be force stopped without restarting the app. It appears this was a linear OpMode; make sure you are calling opModeIsActive() in any loops"
                            : "User OpMode was stuck in %s, but was able to be force stopped without restarting the app. It appears this was an iterative OpMode; make sure you aren't using your own loops.", method));
                    return;
                }
                android.content.Context c = AppUtil.getInstance().getApplication();
                RobotLog.setGlobalErrorMsg(c.getString(com.qualcomm.robotcore.R.string.errorOpModeStuck, opMode, method));
                ds.event("toast", c.getString(com.qualcomm.robotcore.R.string.toastOpModeStuck, method));
                Thread.sleep(1000);
                Runnable kill = android.os.Process.onKill;
                if (kill != null) kill.run();
            } catch (InterruptedException ignored) {
            }
        }, "OpModeStuckCodeMonitor");
        monitor.setDaemon(true);
        monitor.start();
        try {
            runnable.run();
        } finally {
            done.countDown();
        }
    }
}
