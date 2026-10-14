package org.firstinspires.ftc.teamcode.pedro.procedures;

import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;

import java.util.List;

// Where Pedro's tuners talk to the people: the Driver Station (setup program 5) instead of Pedro's web page.
// Procedure, Inputs, TuningOpMode and Display in this package stand in for Pedro's tuning framework, so Pedro's
// own tuner code runs unchanged inside an ordinary OpMode.
public interface Screen {
    HardwareMap hardwareMap();

    Gamepad gamepad1();

    Gamepad gamepad2();

    // shows a message until cross
    void confirm(String title, String message, Display display) throws InterruptedException;

    // shows the fields until cross (fields the robot already knows are filled in without asking)
    void ask(Inputs inputs, Display display) throws InterruptedException;

    // a driving step: shows what it will do, returns once cross is pressed
    void stepStart(TuningOpMode<?> step, Display display) throws InterruptedException;

    // polled by a running step: true once the student pressed cross (steps that end on the student's word)
    boolean stepDone(TuningOpMode<?> step);

    // STOP on the Driver Station
    boolean stopped();

    // what the step itself printed
    void stepTelemetry(TuningOpMode<?> step, List<String> lines);

    void result(String key, Object value);
}
