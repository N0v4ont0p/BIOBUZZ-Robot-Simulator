package org.biobuzz.sim.rc;

import com.qualcomm.robotcore.hardware.Gamepad;

// The gamepad an OpMode reads. The SDK's button edge flags ("wasPressed") are plain fields written by the event
// loop thread and cleared by the OpMode thread; in a very fast OpMode loop a press can be lost between the two.
// Here the copy-in and every edge read take the same lock. Everything else is the SDK's own Gamepad.
final class SimGamepad extends Gamepad {
    @Override
    public synchronized void fromByteArray(byte[] bytes) {
        super.fromByteArray(bytes);
    }

    @Override
    public synchronized boolean aWasPressed() {
        return super.aWasPressed();
    }

    @Override
    public synchronized boolean aWasReleased() {
        return super.aWasReleased();
    }

    @Override
    public synchronized boolean bWasPressed() {
        return super.bWasPressed();
    }

    @Override
    public synchronized boolean bWasReleased() {
        return super.bWasReleased();
    }

    @Override
    public synchronized boolean backWasPressed() {
        return super.backWasPressed();
    }

    @Override
    public synchronized boolean backWasReleased() {
        return super.backWasReleased();
    }

    @Override
    public synchronized boolean circleWasPressed() {
        return super.circleWasPressed();
    }

    @Override
    public synchronized boolean circleWasReleased() {
        return super.circleWasReleased();
    }

    @Override
    public synchronized boolean crossWasPressed() {
        return super.crossWasPressed();
    }

    @Override
    public synchronized boolean crossWasReleased() {
        return super.crossWasReleased();
    }

    @Override
    public synchronized boolean dpadDownWasPressed() {
        return super.dpadDownWasPressed();
    }

    @Override
    public synchronized boolean dpadDownWasReleased() {
        return super.dpadDownWasReleased();
    }

    @Override
    public synchronized boolean dpadLeftWasPressed() {
        return super.dpadLeftWasPressed();
    }

    @Override
    public synchronized boolean dpadLeftWasReleased() {
        return super.dpadLeftWasReleased();
    }

    @Override
    public synchronized boolean dpadRightWasPressed() {
        return super.dpadRightWasPressed();
    }

    @Override
    public synchronized boolean dpadRightWasReleased() {
        return super.dpadRightWasReleased();
    }

    @Override
    public synchronized boolean dpadUpWasPressed() {
        return super.dpadUpWasPressed();
    }

    @Override
    public synchronized boolean dpadUpWasReleased() {
        return super.dpadUpWasReleased();
    }

    @Override
    public synchronized boolean guideWasPressed() {
        return super.guideWasPressed();
    }

    @Override
    public synchronized boolean guideWasReleased() {
        return super.guideWasReleased();
    }

    @Override
    public synchronized boolean leftBumperWasPressed() {
        return super.leftBumperWasPressed();
    }

    @Override
    public synchronized boolean leftBumperWasReleased() {
        return super.leftBumperWasReleased();
    }

    @Override
    public synchronized boolean leftStickButtonWasPressed() {
        return super.leftStickButtonWasPressed();
    }

    @Override
    public synchronized boolean leftStickButtonWasReleased() {
        return super.leftStickButtonWasReleased();
    }

    @Override
    public synchronized boolean leftTriggerWasPressed() {
        return super.leftTriggerWasPressed();
    }

    @Override
    public synchronized boolean leftTriggerWasReleased() {
        return super.leftTriggerWasReleased();
    }

    @Override
    public synchronized boolean optionsWasPressed() {
        return super.optionsWasPressed();
    }

    @Override
    public synchronized boolean optionsWasReleased() {
        return super.optionsWasReleased();
    }

    @Override
    public synchronized boolean psWasPressed() {
        return super.psWasPressed();
    }

    @Override
    public synchronized boolean psWasReleased() {
        return super.psWasReleased();
    }

    @Override
    public synchronized boolean rightBumperWasPressed() {
        return super.rightBumperWasPressed();
    }

    @Override
    public synchronized boolean rightBumperWasReleased() {
        return super.rightBumperWasReleased();
    }

    @Override
    public synchronized boolean rightStickButtonWasPressed() {
        return super.rightStickButtonWasPressed();
    }

    @Override
    public synchronized boolean rightStickButtonWasReleased() {
        return super.rightStickButtonWasReleased();
    }

    @Override
    public synchronized boolean rightTriggerWasPressed() {
        return super.rightTriggerWasPressed();
    }

    @Override
    public synchronized boolean rightTriggerWasReleased() {
        return super.rightTriggerWasReleased();
    }

    @Override
    public synchronized boolean shareWasPressed() {
        return super.shareWasPressed();
    }

    @Override
    public synchronized boolean shareWasReleased() {
        return super.shareWasReleased();
    }

    @Override
    public synchronized boolean squareWasPressed() {
        return super.squareWasPressed();
    }

    @Override
    public synchronized boolean squareWasReleased() {
        return super.squareWasReleased();
    }

    @Override
    public synchronized boolean startWasPressed() {
        return super.startWasPressed();
    }

    @Override
    public synchronized boolean startWasReleased() {
        return super.startWasReleased();
    }

    @Override
    public synchronized boolean touchpadWasPressed() {
        return super.touchpadWasPressed();
    }

    @Override
    public synchronized boolean touchpadWasReleased() {
        return super.touchpadWasReleased();
    }

    @Override
    public synchronized boolean triangleWasPressed() {
        return super.triangleWasPressed();
    }

    @Override
    public synchronized boolean triangleWasReleased() {
        return super.triangleWasReleased();
    }

    @Override
    public synchronized boolean xWasPressed() {
        return super.xWasPressed();
    }

    @Override
    public synchronized boolean xWasReleased() {
        return super.xWasReleased();
    }

    @Override
    public synchronized boolean yWasPressed() {
        return super.yWasPressed();
    }

    @Override
    public synchronized boolean yWasReleased() {
        return super.yWasReleased();
    }
}
