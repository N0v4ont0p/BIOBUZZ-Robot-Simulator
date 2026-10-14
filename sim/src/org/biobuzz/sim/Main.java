package org.biobuzz.sim;

import org.biobuzz.sim.rc.Devices;
import org.biobuzz.sim.web.Server;
import org.firstinspires.ftc.robotcore.internal.system.AppUtil;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

// java ... org.biobuzz.sim.Main [--port 8080] [--scenario A-perfect-robot.json] [--seed 1] [--keep] [--fresh] [--no-browser]
//   with no --scenario/--seed it picks up the last robot where it was left; --fresh starts a new one
//   [--pedro-page] (also serve Pedro's own tuning web page on :10158; the robot app no longer has it: program 5 runs
//   Pedro's tuners on the Driver Station) [--no-pedro] (accepted, the default)
public final class Main {
    public static void main(String[] args) throws Exception {
        int port = 8080;
        String scenario = "A-perfect-robot.json";
        long seed = System.currentTimeMillis() % 100000;
        boolean keep = false, browser = true, picked = false, fresh = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": port = Integer.parseInt(args[++i]); break;
                case "--scenario": scenario = args[++i]; picked = true; break;
                case "--seed": seed = Long.parseLong(args[++i]); picked = true; break;
                case "--fresh": fresh = true; break;
                case "--keep": keep = true; break;
                case "--no-browser": browser = false; break;
                case "--no-pedro": break;
                case "--pedro-page": System.setProperty("sim.pedro", "true"); break;
                default: throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        File simDir = new File(System.getProperty("sim.dir", ".")).getAbsoluteFile();
        String w = System.getenv("SIM_WORK");
        File work = w == null || w.isEmpty() ? new File(simDir, "work") : new File(w).getAbsoluteFile();
        // before any SDK class looks at storage: the robot's /sdcard is <work>/robot-sdcard
        System.setProperty("sim.sdcard", new File(work, "robot-sdcard").getPath());
        // test mode's tool talks to this simulator over its own API
        System.setProperty("sim.port", Integer.toString(port));
        System.setProperty("sim.pedroWeb", new File(simDir, "build/pedro-web").getPath());
        new File(work, "robot-sdcard/FIRST").mkdirs();

        Field app = AppUtil.class.getDeclaredField("application");
        app.setAccessible(true);
        app.set(AppUtil.getInstance(), new android.app.Application());

        List<File> jars = new ArrayList<>();
        for (String p : System.getProperty("java.class.path").split(File.pathSeparator)) {
            String n = new File(p).getName();
            if (n.equals("RobotCore.jar") || n.equals("Hardware.jar") || n.equals("FtcCommon.jar") || n.equals("pedro-revhub.jar")) jars.add(new File(p));
        }
        Devices.scan(jars);

        Sim sim = new Sim(simDir, work);
        android.os.Process.onKill = () -> {
            sim.note("the Robot Controller app restarted");
            new Thread(() -> {
                try {
                    sim.restartRobot();
                } catch (Exception e) {
                    sim.note("restart failed: " + e);
                }
            }).start();
        };
        Server server = new Server(sim, new File(simDir, "web"));
        server.start(port);
        com.google.gson.JsonObject last = picked || fresh ? null : sim.lastSession();
        if (last != null) {
            scenario = last.get("file").getAsString();
            seed = last.get("seed").getAsLong();
            keep = true;
            sim.note("picking up where you left off (./sim.sh --fresh for a new robot)");
        }
        sim.loadScenario(scenario, seed, !keep);
        String url = "http://localhost:" + port + "/";
        System.out.println("BIOBUZZ simulator: " + url);
        if (browser) {
            try {
                new ProcessBuilder("open", url).start();
            } catch (Exception ignored) {
            }
        }
        Thread.currentThread().join();
    }
}
