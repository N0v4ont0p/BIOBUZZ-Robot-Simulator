package org.firstinspires.ftc.teamcode.pedro.procedures;

// Stands in for Pedro's com.pedropathing.tuning.autotune.Procedure (same methods, same meaning), but talks to a
// Screen: the tuners in this package run on the Driver Station, no laptop or web page needed.
public abstract class Procedure {
    public enum Language { JAVA, KOTLIN }

    // abort(): the tuner found something it cannot tune around (wrong motor spun, ...)
    public static final class Aborted extends RuntimeException {
        public Aborted(String message) {
            super(message);
        }
    }

    public final String name;
    public final String description;
    private Screen screen;
    private Display display;

    public Procedure(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public abstract void run() throws InterruptedException;

    public final void run(Screen screen) throws InterruptedException {
        this.screen = screen;
        run();
    }

    protected Inputs inputs(String title, String description) {
        return new Inputs(title, description);
    }

    protected void awaitInputs(Inputs inputs) throws InterruptedException {
        screen.ask(inputs, display);
    }

    protected void confirmation(String title, String message) throws InterruptedException {
        screen.confirm(title, message, display);
    }

    protected void withDisplay(Display display, InterruptibleBlock block) throws InterruptedException {
        Display before = this.display;
        this.display = display;
        try {
            block.run();
        } finally {
            this.display = before;
        }
    }

    protected final <Result> Result runOpMode(TuningOpMode<Result> step) throws InterruptedException {
        return step.run(screen, display);
    }

    protected final void result(String key, Object value) {
        screen.result(key, value);
    }

    // Pedro's page shows code to paste; here the results go to the robot's results file instead
    protected final void code(Language language, String code) {
    }

    protected final void abort(String message) {
        throw new Aborted(message);
    }
}
