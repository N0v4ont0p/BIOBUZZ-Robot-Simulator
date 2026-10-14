package org.firstinspires.ftc.teamcode.pedro.procedures;

// Stands in for Pedro's Display: the picture next to a question. Only the one the tuners here use: the robot from
// above with one wheel marked (Mecanum Tuner).
public abstract class Display {
    private Display() {}

    public static FourWheelBot fourWheelBot(FourWheelBot.Wheel wheel, boolean reversed) {
        return new FourWheelBot(wheel, reversed);
    }

    public static final class FourWheelBot extends Display {
        public enum Wheel { FRONT_LEFT, FRONT_RIGHT, BACK_LEFT, BACK_RIGHT }

        public final Wheel wheel;
        public final boolean reversed;

        private FourWheelBot(Wheel wheel, boolean reversed) {
            this.wheel = wheel;
            this.reversed = reversed;
        }
    }
}
