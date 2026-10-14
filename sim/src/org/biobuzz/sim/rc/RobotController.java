package org.biobuzz.sim.rc;

import android.app.Activity;

import com.google.gson.JsonObject;
import com.qualcomm.robotcore.eventloop.EventLoopManager;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.OpModeManagerImpl;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.hardware.VoltageSensor;
import com.qualcomm.robotcore.robocol.Command;
import com.qualcomm.robotcore.robot.RobotState;
import com.qualcomm.robotcore.util.RobotLog;

import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.hw.MotorPort;
import org.biobuzz.sim.phys.World;
import org.firstinspires.ftc.robotcore.internal.network.NetworkConnectionHandler;
import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta;
import org.firstinspires.ftc.robotcore.internal.opmode.RegisteredOpModes;
import org.firstinspires.ftc.robotcore.internal.system.AppUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.LockSupport;

// One run of the Robot Controller app: from start (or "Restart Robot", or installing a new build) to the next
// restart. Uses the SDK's real OpModeManagerImpl, RegisteredOpModes and EventLoopManager; only the event loop
// (what FtcEventLoop does) and the network link are the simulator's.
public final class RobotController {
    final DriverStation ds;
    private final World world;
    private final TeamCode code;
    private final RobotConfig config;
    URLClassLoader loader;
    OpModeManagerImpl manager;
    private SimEventLoop loop;
    private HardwareMap map;
    private Thread thread;
    private volatile boolean running;
    private volatile String status = "Robot Controller starting";
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, OpModeMeta> metas = new LinkedHashMap<>();
    private final Map<String, Object> tuners = new LinkedHashMap<>();
    private final org.biobuzz.sim.hw.Lease lease = new org.biobuzz.sim.hw.Lease();

    public RobotController(DriverStation ds, World world, TeamCode code, RobotConfig config) {
        this.ds = ds;
        this.world = world;
        this.code = code;
        this.config = config;
    }

    public synchronized void start() throws Exception {
        Activity activity = new Activity();
        setField(AppUtil.class, AppUtil.getInstance(), "currentActivity", activity);
        setField(AppUtil.class, AppUtil.getInstance(), "rootActivity", activity);
        RobotLog.clearGlobalErrorMsg();
        RobotLog.clearGlobalWarningMsg();
        resetHubs();

        loader = code == null ? null : code.newLoader();
        registerOpModes();

        HardwareBuilder hb = new HardwareBuilder();
        map = hb.build(config, world.robot, AppUtil.getInstance().getApplication(), lease);
        warnings.addAll(hb.warnings);
        if (config == null) warnings.add("No active configuration: Configure Robot");

        NetworkConnectionHandler.toDriverStation = ds::onCommand;
        NetworkConnectionHandler.dataToDriverStation = ds::onData;
        ds.attach(this);
        loop = new SimEventLoop(this);
        manager = new SimOpModeManager(activity, map, lease, ds);
        EventLoopManager elm = allocate(EventLoopManager.class);
        setField(EventLoopManager.class, elm, "state", RobotState.RUNNING);
        setField(EventLoopManager.class, elm, "networkConnectionHandler", NetworkConnectionHandler.getInstance());
        setField(EventLoopManager.class, elm, "eventLoop", loop);
        manager.init(elm);
        startPedro();

        running = true;
        thread = new Thread(this::run, "EventLoop");
        thread.setDaemon(true);
        thread.start();
        status = warnings.isEmpty() ? "running" : "running, with warnings";
    }

    // what the hubs do when the Robot Controller app (re)starts: motors stopped and floating, no encoder mode
    private void resetHubs() {
        synchronized (World.LOCK) {
            for (Hub h : world.robot.hubs) {
                for (MotorPort p : h.motors) {
                    p.mode = DcMotor.RunMode.RUN_WITHOUT_ENCODER;
                    p.zeroPower = DcMotor.ZeroPowerBehavior.FLOAT;
                    p.power = 0;
                    p.targetVelocity = 0;
                    p.enabled = false;
                    p.resetController();
                }
                for (org.biobuzz.sim.hw.ServoPort s : h.servos) {
                    s.pwmEnabled = false;
                    s.position = Double.NaN;
                }
            }
        }
    }

    private void registerOpModes() throws Exception {
        RegisteredOpModes reg = RegisteredOpModes.getInstance();
        ((Map<?, ?>) getField(RegisteredOpModes.class, reg, "opModeClasses")).clear();
        ((Map<?, ?>) getField(RegisteredOpModes.class, reg, "opModeInstances")).clear();
        metas.clear();
        reg.register(RegisteredOpModes.DEFAULT_OP_MODE_METADATA, OpModeManagerImpl.DefaultOpMode.class);
        if (code != null) {
            for (TeamCode.Found f : code.opModes(loader)) {
                reg.register(f.meta, f.cls);
                metas.put(f.meta.name, f.meta);
            }
        }
        setField(RegisteredOpModes.class, reg, "opmodesAreRegistered", true);
    }

    private void startPedro() {
        try {
            Class<?> hooks = Class.forName("com.pedropathing.tuning.autotune.Hooks");
            Class<?> registrar = Class.forName("com.pedropathing.tuning.autotune.TunerRegistrar");
            Class<?> procedure = Class.forName("com.pedropathing.tuning.autotune.Procedure");
            hooks.getMethod("simAbortSession").invoke(null);
            registrar.getMethod("deregisterAll").invoke(null);
            tuners.clear();
            if (code != null) {
                for (Object[] t : code.tuners(loader)) {
                    Object p = ((Method) t[1]).invoke(null);
                    registrar.getMethod("register", String.class, procedure).invoke(null, t[0], p);
                    tuners.put((String) t[0], p);
                }
            }
            hooks.getMethod("simSetOpModeManager", OpModeManagerImpl.class).invoke(null, manager);
            if ("true".equals(System.getProperty("sim.pedro"))) hooks.getMethod("simStartServers").invoke(null);
        } catch (Throwable t) {
            Throwable c = t instanceof java.lang.reflect.InvocationTargetException ? t.getCause() : t;
            warnings.add("Pedro tuning page not available: " + c);
        }
    }

    private void run() {
        try {
            while (running) {
                try {
                    loop.loop();
                } catch (Throwable t) {
                    RobotLog.setGlobalErrorMsg("Robot Controller event loop: " + t);
                    RobotLog.ee("Sim", t, "event loop");
                }
                ds.tick();
                LockSupport.parkNanos(1_000_000L);
            }
        } finally {
            ds.tick();
        }
    }

    public void command(Command c) {
        SimEventLoop l = loop;
        if (l != null) l.command(c);
    }

    public void gamepads(JsonObject g1, JsonObject g2) {
        SimEventLoop l = loop;
        if (l == null) return;
        l.latest1 = Effects.fromBrowser(g1);
        l.latest2 = Effects.fromBrowser(g2);
    }

    // stops the active OpMode the way the app does on restart, then the event loop
    public synchronized void stop() {
        if (thread == null) return;
        command(new Command("CMD_INIT_OP_MODE", DriverStation.STOP));
        long until = System.nanoTime() + 2_000_000_000L;
        while (System.nanoTime() < until && manager != null && !DriverStation.STOP.equals(manager.getActiveOpModeName()))
            LockSupport.parkNanos(5_000_000L);
        // one more pass so the stop OpMode's init runs (motors to zero, servos off)
        LockSupport.parkNanos(30_000_000L);
        running = false;
        try {
            thread.join(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // the app is going away: its devices die, and the last OpMode's thread is told to stop. A thread stuck
        // in its own loop cannot be killed inside one JVM; with dead devices it can no longer move anything.
        lease.revoked = true;
        Thread cleanup = new Thread(() -> {
            try {
                OpMode last = manager.getActiveOpMode();
                if (last != null) {
                    Method stop = Class.forName("com.qualcomm.robotcore.eventloop.opmode.OpModeInternal").getDeclaredMethod("internalStop");
                    stop.setAccessible(true);
                    stop.invoke(last);
                }
                manager.teardown();
            } catch (Throwable ignored) {
            }
        }, "RobotControllerShutdown");
        cleanup.setDaemon(true);
        cleanup.start();
        try {
            cleanup.join(1500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            loader.close();
        } catch (Throwable ignored) {
        }
        status = "stopped";
        thread = null;
    }

    public String autoTransition(String opMode) {
        OpModeMeta m = metas.get(opMode);
        return m == null ? null : m.autoTransition;
    }

    public String flavor(String opMode) {
        OpModeMeta m = metas.get(opMode);
        return m == null ? "" : m.flavor.name();
    }

    public List<OpModeMeta> opModes() {
        return new ArrayList<>(metas.values());
    }

    public HardwareMap hardwareMap() {
        return map;
    }

    public OpMode activeOpMode() {
        return manager == null ? null : manager.getActiveOpMode();
    }

    public double batteryVolts() {
        double v = Double.NaN;
        if (map == null) return v;
        if (lease.revoked) return v;
        try {
            for (VoltageSensor s : map.voltageSensor) {
                double x = s.getVoltage();
                if (Double.isNaN(v) || x < v) v = x;
            }
        } catch (RuntimeException e) {
            return Double.NaN;
        }
        return v;
    }

    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status);
        m.put("config", config == null ? null : config.name);
        m.put("warnings", new ArrayList<>(warnings));
        List<Map<String, String>> list = new ArrayList<>();
        for (OpModeMeta o : metas.values()) {
            Map<String, String> e = new LinkedHashMap<>();
            e.put("name", o.name);
            e.put("group", OpModeMeta.DefaultGroup.equals(o.group) ? "" : o.group);
            e.put("flavor", o.flavor.name());
            e.put("transition", o.autoTransition);
            list.add(e);
        }
        m.put("opModes", list);
        m.put("tuners", new ArrayList<>(tuners.keySet()));
        return m;
    }

    private static Object getField(Class<?> c, Object o, String name) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
    }

    private static void setField(Class<?> c, Object o, String name, Object v) throws ReflectiveOperationException {
        Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(o, v);
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> c) throws ReflectiveOperationException {
        Field f = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Object unsafe = f.get(null);
        return (T) unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, c);
    }
}
