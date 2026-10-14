package dev.frozenmilk.sinister.sdk.opmodes;

import com.qualcomm.robotcore.eventloop.opmode.OpMode;

import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta;
import org.firstinspires.ftc.robotcore.internal.opmode.RegisteredOpModes;

// Simulator stand-in for Sinister's OpMode registry, used by Pedro tuning to run its procedure OpModes.
// Registers straight into the SDK's own RegisteredOpModes, which is what the real one does in the end.
public final class SinisterRegisteredOpModes {
    public static final SinisterRegisteredOpModes INSTANCE = new SinisterRegisteredOpModes();

    public void register(OpModeMeta meta, OpMode opMode) {
        unregister(meta);
        RegisteredOpModes.getInstance().register(meta, opMode);
    }

    public void unregister(OpModeMeta meta) {
        try {
            java.lang.reflect.Method m = RegisteredOpModes.class.getDeclaredMethod("unregister", OpModeMeta.class);
            m.setAccessible(true);
            m.invoke(RegisteredOpModes.getInstance(), meta);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
