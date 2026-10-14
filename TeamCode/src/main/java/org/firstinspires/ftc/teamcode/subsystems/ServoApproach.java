package org.firstinspires.ftc.teamcode.subsystems;

import com.qualcomm.robotcore.hardware.Servo;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

// A hobby servo stops anywhere inside its deadband, so where it ends up depends on the side it came from. This
// makes every last move go up: a lower position is first passed by BACKOFF, then approached from below. The shot
// tuners (programs 8 and 9) and the match both set the hood through Launcher, so a table number means one angle.
public final class ServoApproach {
    private static final double BACKOFF = 0.01, BACKOFF_S = 0.12;
    // a drop smaller than about half a servo's deadband leaves it where it is, still on the low side
    private static final double SMALL_DROP = 0.002;

    private final Servo servo;
    private final ElapsedTime timer = new ElapsedTime();
    private double target = Double.NaN, sent = Double.NaN, base = Double.NaN;
    private boolean backingOff = false;

    public ServoApproach(Servo servo) {
        this.servo = servo;
    }

    // call every loop with the wanted position
    public void set(double position) {
        set(position, 0);
    }

    // lowest: never back off below this (a stop it must not be driven into)
    public void set(double position, double lowest) {
        if (Double.isNaN(position)) return;
        target = position;
        if (!backingOff && !Double.isNaN(base) && position < base - SMALL_DROP) {
            backingOff = true;
            timer.reset();
        }
        if (backingOff && timer.seconds() >= BACKOFF_S) backingOff = false;
        if (!backingOff && (Double.isNaN(base) || position > base || position < base - SMALL_DROP)) base = position;
        send(backingOff ? Range.clip(position - BACKOFF, lowest, 1) : position);
    }

    public double target() {
        return target;
    }

    private void send(double position) {
        if (position == sent) return;
        servo.setPosition(position);
        sent = position;
    }
}
