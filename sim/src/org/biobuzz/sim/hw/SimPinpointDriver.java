package org.biobuzz.sim.hw;

import com.qualcomm.hardware.gobilda.GoBildaPinpointDriver;
import com.qualcomm.robotcore.hardware.I2cDeviceSynchSimple;

import org.biobuzz.sim.phys.Pinpoint;
import org.biobuzz.sim.phys.World;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;
import org.firstinspires.ftc.robotcore.external.navigation.Pose2D;
import org.firstinspires.ftc.robotcore.external.navigation.UnnormalizedAngleUnit;

import java.lang.reflect.Proxy;

// The SDK's goBILDA Pinpoint driver, talking to the simulated Pinpoint instead of I2C. Like the real driver,
// getters return what the last update() read.
public class SimPinpointDriver extends GoBildaPinpointDriver {
    private final Pinpoint pp;
    private int encX, encY, loopTime = 666;
    private double posX, posY, posH, velX, velY, velH;
    private DeviceStatus status = DeviceStatus.NOT_READY;

    public SimPinpointDriver(Pinpoint pp) {
        super(fakeI2c(), true);
        this.pp = pp;
    }

    private static I2cDeviceSynchSimple fakeI2c() {
        return (I2cDeviceSynchSimple) Proxy.newProxyInstance(SimPinpointDriver.class.getClassLoader(),
                new Class<?>[]{I2cDeviceSynchSimple.class}, (proxy, m, a) -> {
                    Class<?> r = m.getReturnType();
                    switch (m.getName()) {
                        case "toString": return "Pinpoint I2C";
                        case "hashCode": return System.identityHashCode(proxy);
                        case "equals": return proxy == a[0];
                        case "read": return new byte[a != null && a.length == 2 ? (Integer) a[1] : 1];
                        default: break;
                    }
                    if (r == boolean.class) return true;
                    if (r == int.class) return 0;
                    if (r == long.class) return 0L;
                    if (r == double.class) return 0.0;
                    if (r == byte.class) return (byte) 0;
                    if (r == String.class) return "sim";
                    return null;
                });
    }

    @Override
    protected synchronized boolean doInitialize() {
        return true;
    }

    @Override
    public String getDeviceName() {
        return "goBILDA® Pinpoint Odometry Computer";
    }

    // one bulk read over I2C from the Control Hub takes about 1.5 ms
    @Override
    public void update() {
        java.util.concurrent.locks.LockSupport.parkNanos(1_500_000L);
        synchronized (World.LOCK) {
            encX = (int) Math.floor(pp.encX);
            encY = (int) Math.floor(pp.encY);
            posX = pp.posX;
            posY = pp.posY;
            posH = pp.posH;
            velX = pp.velX;
            velY = pp.velY;
            velH = pp.velH;
            status = DeviceStatus.valueOf(pp.status());
        }
    }

    @Override
    public void update(ReadData data) {
        update();
    }

    @Override
    public void setErrorDetectionType(ErrorDetectionType type) {
    }

    @Override
    public void setOffsets(double xOffset, double yOffset, DistanceUnit unit) {
        synchronized (World.LOCK) {
            pp.offXmm = unit.toMm(xOffset);
            pp.offYmm = unit.toMm(yOffset);
        }
    }

    @Override
    public void recalibrateIMU() {
        synchronized (World.LOCK) {
            pp.recalibrateImu();
        }
    }

    @Override
    public void resetPosAndIMU() {
        synchronized (World.LOCK) {
            pp.resetPosAndImu();
        }
    }

    @Override
    public void setEncoderDirections(EncoderDirection x, EncoderDirection y) {
        synchronized (World.LOCK) {
            pp.dirX = x == EncoderDirection.FORWARD ? 1 : -1;
            pp.dirY = y == EncoderDirection.FORWARD ? 1 : -1;
        }
    }

    @Override
    public void setEncoderResolution(GoBildaOdometryPods pods) {
        synchronized (World.LOCK) {
            pp.resolution = pods == GoBildaOdometryPods.goBILDA_SWINGARM_POD ? Pinpoint.SWINGARM_TICKS_PER_MM : Pinpoint.FOUR_BAR_TICKS_PER_MM;
        }
    }

    @Override
    public void setEncoderResolution(double ticksPerUnit, DistanceUnit unit) {
        synchronized (World.LOCK) {
            pp.resolution = ticksPerUnit / unit.toMm(1);
        }
    }

    @Override
    public void setYawScalar(double yawScalar) {
        synchronized (World.LOCK) {
            pp.yawScalar = yawScalar;
        }
    }

    @Override
    public void setPosition(Pose2D pos) {
        synchronized (World.LOCK) {
            pp.posX = pos.getX(DistanceUnit.MM);
            pp.posY = pos.getY(DistanceUnit.MM);
            pp.posH = pos.getHeading(AngleUnit.RADIANS);
        }
    }

    @Override
    public void setPosX(double x, DistanceUnit unit) {
        synchronized (World.LOCK) {
            pp.posX = unit.toMm(x);
        }
    }

    @Override
    public void setPosY(double y, DistanceUnit unit) {
        synchronized (World.LOCK) {
            pp.posY = unit.toMm(y);
        }
    }

    @Override
    public void setHeading(double heading, AngleUnit unit) {
        synchronized (World.LOCK) {
            pp.posH = unit.toRadians(heading);
        }
    }

    @Override
    public int getDeviceID() {
        return 2;
    }

    @Override
    public int getDeviceVersion() {
        return pp.firmwareVersion;
    }

    @Override
    public float getYawScalar() {
        return (float) pp.yawScalar;
    }

    @Override
    public DeviceStatus getDeviceStatus() {
        return status;
    }

    @Override
    public int getLoopTime() {
        return loopTime;
    }

    @Override
    public double getFrequency() {
        return 1e6 / loopTime;
    }

    @Override
    public int getEncoderX() {
        return encX;
    }

    @Override
    public int getEncoderY() {
        return encY;
    }

    @Override
    public double getPosX(DistanceUnit unit) {
        return unit.fromMm(posX);
    }

    @Override
    public double getPosY(DistanceUnit unit) {
        return unit.fromMm(posY);
    }

    @Override
    public double getHeading(AngleUnit unit) {
        return unit.fromRadians(posH);
    }

    @Override
    public double getHeading(UnnormalizedAngleUnit unit) {
        return unit.fromRadians(posH);
    }

    @Override
    public double getVelX(DistanceUnit unit) {
        return unit.fromMm(velX);
    }

    @Override
    public double getVelY(DistanceUnit unit) {
        return unit.fromMm(velY);
    }

    @Override
    public double getHeadingVelocity(UnnormalizedAngleUnit unit) {
        return unit.fromRadians(velH);
    }

    @Override
    public float getXOffset(DistanceUnit unit) {
        return (float) unit.fromMm(pp.offXmm);
    }

    @Override
    public float getYOffset(DistanceUnit unit) {
        return (float) unit.fromMm(pp.offYmm);
    }

    @Override
    public Pose2D getPosition() {
        return new Pose2D(DistanceUnit.MM, posX, posY, AngleUnit.RADIANS, posH);
    }
}
