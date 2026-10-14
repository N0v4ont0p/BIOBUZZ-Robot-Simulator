package org.biobuzz.sim.phys;

import org.biobuzz.sim.truth.Truth;

import java.util.Map;

// goBILDA Pinpoint odometry computer with two pods. Hidden truth: where the pods really are and which way they
// count. The firmware part integrates exactly like the real one, using whatever offsets, directions and
// resolution the robot code sets, so wrong settings give wrong positions.
public class Pinpoint implements Part {
    public static final double FOUR_BAR_TICKS_PER_MM = 19.89436789;
    public static final double SWINGARM_TICKS_PER_MM = 13.26291192;
    // truth (inches, robot frame: x forward, y left)
    public final double xPodLeftIn, xPodFwdIn, yPodFwdIn, yPodLeftIn;
    public final int xPodSign, yPodSign;
    public final double ticksPerMm;
    // the IMU's heading scale: real ones read a few tenths of a percent off (program 6 measures it over 10 turns)
    public final double imuScale;
    public final double imuDriftRadPerS;
    public final boolean fieldFrameVelocity;
    public final int firmwareVersion;
    public boolean xPodTouching = true, yPodTouching = true;
    public boolean connected = true;

    // raw counts, IMU
    public double encX, encY, imu;
    // firmware settings (what the driver sent)
    public double offXmm, offYmm, resolution;
    public int dirX = 1, dirY = 1;
    public double yawScalar = 1;
    // firmware estimate (mm, rad) and velocities
    public double posX, posY, posH, velX, velY, velH;
    public double calibrating;
    private double lastX, lastY, lastImu;
    private boolean faultNoPods;

    public Pinpoint(Robot r, Truth t) {
        xPodLeftIn = t.num("pinpoint.xPodLeftIn", 3.5);
        xPodFwdIn = t.num("pinpoint.xPodFwdIn", -1.0);
        yPodFwdIn = t.num("pinpoint.yPodFwdIn", -4.25);
        yPodLeftIn = t.num("pinpoint.yPodLeftIn", 0.5);
        xPodSign = (int) t.num("pinpoint.xPodSign", 1);
        yPodSign = (int) t.num("pinpoint.yPodSign", -1);
        // pod wheels are never exactly the catalog size (program 6 measures it over a 96 in push)
        ticksPerMm = t.range("pinpoint.ticksPerMm", FOUR_BAR_TICKS_PER_MM * 0.994, FOUR_BAR_TICKS_PER_MM * 1.006);
        imuScale = t.range("pinpoint.imuScale", 0.997, 1.003);
        imuDriftRadPerS = Math.toRadians(t.num("pinpoint.imuDriftDegPerMin", 0.15)) / 60;
        fieldFrameVelocity = !"robot".equals(t.str("pinpoint.velocityFrame", "field"));
        firmwareVersion = (int) t.num("pinpoint.firmwareVersion", 3);
        xPodTouching = !t.bool("pinpoint.xPodLifted", false);
        yPodTouching = !t.bool("pinpoint.yPodLifted", false);
        resolution = 0;
    }

    @Override
    public String id() {
        return "pinpoint";
    }

    @Override
    public void step(double dt, World w) {
        Drive d = w.robot.drive;
        if (!w.robot.onBox) {
            double vx = d.fwdVel - d.turnVel * xPodLeftIn;
            double vy = d.leftVel + d.turnVel * yPodFwdIn;
            if (xPodTouching) encX += xPodSign * vx * dt * 25.4 * ticksPerMm;
            if (yPodTouching) encY += yPodSign * vy * dt * 25.4 * ticksPerMm;
            imu += d.turnVel * dt * imuScale;
        }
        imu += imuDriftRadPerS * dt;
        if (calibrating > 0) {
            calibrating -= dt;
            lastX = encX;
            lastY = encY;
            lastImu = imu;
            return;
        }
        double res = resolution > 0 ? resolution : FOUR_BAR_TICKS_PER_MM;
        double dx = (encX - lastX) * dirX / res, dy = (encY - lastY) * dirY / res, dh = (imu - lastImu) * yawScalar;
        lastX = encX;
        lastY = encY;
        lastImu = imu;
        double fwd = dx + dh * offXmm, left = dy - dh * offYmm;
        double mid = posH + dh / 2, c = Math.cos(mid), s = Math.sin(mid);
        double fx = fwd * c - left * s, fy = fwd * s + left * c;
        posX += fx;
        posY += fy;
        posH = normalize(posH + dh);
        double a = Math.min(1, dt / 0.02);
        double nvx = fieldFrameVelocity ? fx / dt : fwd / dt, nvy = fieldFrameVelocity ? fy / dt : left / dt;
        velX += (nvx - velX) * a;
        velY += (nvy - velY) * a;
        velH += (dh / dt - velH) * a;
    }

    public String status() {
        if (!connected) return "NOT_READY";
        if (calibrating > 0) return "CALIBRATING";
        if (!xPodTouching && !yPodTouching) return "FAULT_NO_PODS_DETECTED";
        return "READY";
    }

    public void resetPosAndImu() {
        posX = posY = posH = velX = velY = velH = 0;
        calibrating = 0.25;
    }

    public void recalibrateImu() {
        calibrating = 0.25;
    }

    private static double normalize(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }

    @Override
    public void expected(Map<String, Expected> out, World w) {
        out.put("xPodOffset", new Expected(xPodLeftIn, 0.25, "in", "how far LEFT of the robot centre the forward (X) pod is (Pedro Pinpoint Tuner)"));
        out.put("yPodOffset", new Expected(yPodFwdIn, 0.25, "in", "how far FORWARD of the robot centre the strafe (Y) pod is (Pedro Pinpoint Tuner)"));
        out.put("xPodDirection", new Expected(xPodSign, 0.5, "FORWARD=+1 / REVERSED=-1", "the forward pod counts up when the robot moves forward"));
        out.put("yPodDirection", new Expected(yPodSign, 0.5, "FORWARD=+1 / REVERSED=-1", "the strafe pod counts up when the robot moves left"));
        out.put("velocityFrame", new Expected(fieldFrameVelocity ? 1 : 0, 0.5, "field=1 / robot=0", "which way the Pinpoint reports velocity (program 6 step 1)"));
        out.put("podTicksPerMm", new Expected(ticksPerMm, 0.03, "ticks/mm", "pod encoder ticks per mm of travel (program 6: 96 in push)"));
        out.put("imuYawScalar", new Expected(1 / imuScale, 0.0015, "", "Pinpoint yaw scalar that makes its heading right (program 6: 10 turns)"));
        out.put("loopErrorInches", new Expected(0, 1.0, "in", "a good loop comes back within 1 in"));
        out.put("loopErrorDegrees", new Expected(0, 2.0, "deg", "a good loop comes back within 2 deg"));
    }

    @Override
    public void state(Map<String, Object> out) {
        out.put("encX", (long) encX);
        out.put("encY", (long) encY);
        out.put("status", status());
        out.put("posXIn", posX / 25.4);
        out.put("posYIn", posY / 25.4);
        out.put("headingDeg", Math.toDegrees(posH));
    }
}
