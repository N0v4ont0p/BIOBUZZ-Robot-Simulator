package org.biobuzz.sim;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

// The sandbox: a private copy of the parts of the repo the robot code is built from (TeamCode sources,
// team-config.yaml, tools/codegen). Practice results are entered here, never in the real repo.
public final class Workspace {
    public final File repo;      // the real repo (read only for the simulator)
    public final File root;      // sim/work
    public final File sandbox;   // sim/work/sandbox
    public final File sdcard;    // sim/work/robot-sdcard
    public final File outbox;    // sim/work/outbox ("adb pull" lands here)
    private int build;

    public Workspace(File repo, File work) {
        this.repo = repo;
        this.root = work;
        sandbox = new File(work, "sandbox");
        sdcard = new File(work, "robot-sdcard");
        outbox = new File(work, "outbox");
    }

    public File teamCodeSource() {
        return new File(sandbox, "TeamCode/src/main/java");
    }

    public boolean exists() {
        return teamCodeSource().isDirectory() && new File(sandbox, "team-config.yaml").isFile();
    }

    // fresh copy from the real repo (and an empty outbox: old results belong to another robot)
    public void reset() throws IOException {
        deleteTree(sandbox);
        deleteTree(outbox);
        copyTree(new File(repo, "TeamCode/src/main/java"), teamCodeSource());
        copyTree(new File(repo, "tools/codegen"), new File(sandbox, "tools/codegen"));
        Files.copy(new File(repo, "team-config.yaml").toPath(), new File(sandbox, "team-config.yaml").toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public void clearRobot() throws IOException {
        deleteTree(sdcard);
        new File(sdcard, "FIRST").mkdirs();
    }

    // "adb pull": the results file and the shot tables, off the robot into a new outbox/<time>/ folder
    public File pull() throws IOException {
        String name = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        File dir = new File(outbox, name);
        for (int i = 2; dir.exists(); i++) dir = new File(outbox, name + "-" + i);
        dir.mkdirs();
        for (File f : robotResultFiles()) Files.copy(f.toPath(), new File(dir, f.getName()).toPath());
        return dir;
    }

    public List<File> robotResultFiles() {
        List<File> out = new ArrayList<>();
        File[] fs = new File(sdcard, "FIRST").listFiles((d, n) -> n.equals("biobuzz_results.csv") || n.matches("biobuzz_\\w+_(points|shots)\\.csv"));
        if (fs != null) out.addAll(Arrays.asList(fs));
        out.sort(null);
        return out;
    }

    public File newestPull() {
        File[] ds = outbox.listFiles(File::isDirectory);
        if (ds == null || ds.length == 0) return null;
        Arrays.sort(ds);
        return ds[ds.length - 1];
    }

    public File nextBuildDir() {
        build++;
        return new File(root, "build/teamcode-" + System.currentTimeMillis() + "-" + build);
    }

    public static void copyTree(File from, File to) throws IOException {
        Path src = from.toPath(), dst = to.toPath();
        try (Stream<Path> s = Files.walk(src)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path t = dst.resolve(src.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(t);
                else Files.copy(p, t, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static void deleteTree(File f) throws IOException {
        if (!f.exists()) return;
        try (Stream<Path> s = Files.walk(f.toPath())) {
            for (Path p : (Iterable<Path>) s.sorted((a, b) -> b.compareTo(a))::iterator) Files.delete(p);
        }
    }
}
