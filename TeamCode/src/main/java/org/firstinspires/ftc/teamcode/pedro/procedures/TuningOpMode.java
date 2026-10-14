package org.firstinspires.ftc.teamcode.pedro.procedures;

import com.pedropathing.drivetrain.Drivetrain;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Stands in for Pedro's TuningOpMode: one driving step of a tuner. Pedro ran each as its own OpMode, started and
// stopped from the web page; here it runs inside the setup program: waitForStart() waits for cross, and for a
// step that ends on the student's word (canStop) cross again is "stop".
public abstract class TuningOpMode<Result> {
    public final String name;
    public final String description;
    public final boolean canStop;

    protected HardwareMap hardwareMap;
    protected Gamepad gamepad1, gamepad2;
    protected final StepTelemetry telemetry = new StepTelemetry();

    private Screen screen;
    private Display display;
    private boolean started;

    public TuningOpMode(String name, String description, boolean canStop) {
        this.name = name;
        this.description = description;
        this.canStop = canStop;
    }

    protected abstract Result runTuningOpMode() throws InterruptedException;

    final Result run(Screen screen, Display display) throws InterruptedException {
        this.screen = screen;
        this.display = display;
        hardwareMap = screen.hardwareMap();
        gamepad1 = screen.gamepad1();
        gamepad2 = screen.gamepad2();
        Result result = runTuningOpMode();
        // STOP on the Driver Station during a step: its answer is not a measurement
        if (screen.stopped()) throw new InterruptedException("stopped");
        return result;
    }

    public Display display() {
        return display;
    }

    // STOP on the start screen must not start the step (some steps drive until a distance without checking)
    public static final class Stopped extends RuntimeException {
        Stopped() {
            super("stopped");
        }
    }

    protected void waitForStart() {
        if (started) return;
        try {
            screen.stepStart(this, display);
        } catch (InterruptedException e) {
            throw new Stopped();
        }
        started = true;
    }

    // a step that drives until the robot gets somewhere: STOP ends it, and so does not getting there in time (the robot
    // still on the blocks from part 1, a wheel unplugged), the wheels stopped first; Pedro's steps never checked
    protected void keepGoing(Drivetrain drivetrain, long startNanos, double limitS, String goal) throws InterruptedException {
        if (screen.stopped()) {
            drivetrain.stop();
            throw new InterruptedException("stopped");
        }
        if ((System.nanoTime() - startNanos) / 1e9 > limitS) {
            drivetrain.stop();
            throw new Procedure.Aborted(String.format(Locale.US, "the robot did not %s within %.0f s with the wheels driving."
                    + " Still on the blocks (part 1)? Put it on the floor. Moved but this says not? The Pinpoint is not counting (cable, pod"
                    + " direction): part 2 again. Then run part 3 again", goal, limitS));
        }
    }

    protected boolean isStopRequested() {
        return screen.stopped() || (canStop && started && screen.stepDone(this));
    }

    protected boolean opModeIsActive() {
        return started && !isStopRequested();
    }

    // the lines a step prints, shown under the step's own instructions
    public final class StepTelemetry {
        private final List<String> lines = new ArrayList<>();

        public void addData(String caption, Object value) {
            lines.add(caption + ": " + value);
        }

        public void addData(String caption, String format, Object... args) {
            lines.add(caption + ": " + String.format(Locale.US, format, args));
        }

        public void addLine(String line) {
            lines.add(line);
        }

        public void update() {
            screen.stepTelemetry(TuningOpMode.this, new ArrayList<>(lines));
            lines.clear();
        }
    }
}
