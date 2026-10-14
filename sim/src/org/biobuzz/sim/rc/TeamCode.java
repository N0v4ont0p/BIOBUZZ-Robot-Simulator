package org.biobuzz.sim.rc;

import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.Disabled;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;

import org.firstinspires.ftc.robotcore.internal.opmode.OpModeMeta;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

// The robot code: compiled from the sandbox copy of TeamCode (what "build and install" does on the real robot)
// and loaded in its own class loader, so a Robot Controller restart starts with fresh static state.
public final class TeamCode {
    public final File classes;

    public TeamCode(File classes) {
        this.classes = classes;
    }

    // returns the compiler output; throws if it did not compile
    public static String compile(File sourceRoot, File out) throws IOException {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) throw new IOException("No Java compiler: run the simulator with a JDK, not a JRE");
        List<String> sources;
        try (Stream<Path> s = Files.walk(sourceRoot.toPath())) {
            sources = s.filter(p -> p.toString().endsWith(".java")).map(Path::toString).collect(Collectors.toList());
        }
        deleteTree(out);
        out.mkdirs();
        List<String> args = new ArrayList<>(Arrays.asList("-nowarn", "-encoding", "UTF-8", "--release", "17",
                "-proc:none", "-cp", System.getProperty("java.class.path"), "-d", out.getPath()));
        args.addAll(sources);
        StringWriter log = new StringWriter();
        java.io.ByteArrayOutputStream err = new java.io.ByteArrayOutputStream();
        int rc = javac.run(null, null, err, args.toArray(new String[0]));
        log.write(err.toString());
        if (rc != 0) throw new IOException("TeamCode did not compile:\n" + log);
        return log.toString();
    }

    static void deleteTree(File f) throws IOException {
        if (!f.exists()) return;
        try (Stream<Path> s = Files.walk(f.toPath())) {
            for (Path p : s.sorted((a, b) -> b.compareTo(a)).collect(Collectors.toList())) Files.delete(p);
        }
    }

    public URLClassLoader newLoader() throws IOException {
        return new URLClassLoader(new URL[]{classes.toURI().toURL()}, TeamCode.class.getClassLoader());
    }

    public List<String> classNames() throws IOException {
        Path root = classes.toPath();
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(p -> p.toString().endsWith(".class"))
                    .map(p -> root.relativize(p).toString().replace(File.separatorChar, '.').replaceAll("\\.class$", ""))
                    .sorted().collect(Collectors.toList());
        }
    }

    public static final class Found {
        public final OpModeMeta meta;
        public final Class<? extends OpMode> cls;

        Found(OpModeMeta meta, Class<? extends OpMode> cls) {
            this.meta = meta;
            this.cls = cls;
        }
    }

    // the same rules the SDK's annotation scanner uses: concrete, public no-argument constructor, not @Disabled
    @SuppressWarnings("unchecked")
    public List<Found> opModes(ClassLoader loader) throws IOException {
        List<Found> out = new ArrayList<>();
        for (String n : classNames()) {
            Class<?> c;
            try {
                c = Class.forName(n, false, loader);
            } catch (Throwable t) {
                continue;
            }
            if (!OpMode.class.isAssignableFrom(c) || Modifier.isAbstract(c.getModifiers()) || !Modifier.isPublic(c.getModifiers()))
                continue;
            if (c.isAnnotationPresent(Disabled.class)) continue;
            try {
                c.getConstructor();
            } catch (NoSuchMethodException e) {
                continue;
            }
            TeleOp t = c.getAnnotation(TeleOp.class);
            Autonomous a = c.getAnnotation(Autonomous.class);
            if (t == null && a == null) continue;
            OpModeMeta.Builder b = new OpModeMeta.Builder()
                    .setFlavor(t != null ? OpModeMeta.Flavor.TELEOP : OpModeMeta.Flavor.AUTONOMOUS)
                    .setName(nameOf(t != null ? t.name() : a.name(), c))
                    .setGroup(groupOf(t != null ? t.group() : a.group()))
                    .setSource(OpModeMeta.Source.ANDROID_STUDIO);
            if (a != null && !a.preselectTeleOp().isEmpty()) b.setTransitionTarget(a.preselectTeleOp());
            out.add(new Found(b.build(), (Class<? extends OpMode>) c));
        }
        return out;
    }

    private static String nameOf(String n, Class<?> c) {
        return n == null || n.trim().isEmpty() ? c.getSimpleName() : n.trim();
    }

    private static String groupOf(String g) {
        return g == null || g.trim().isEmpty() ? OpModeMeta.DefaultGroup : g.trim();
    }

    // Pedro tuning: static, no-argument methods marked @Tuner that return a Procedure
    public List<Object[]> tuners(ClassLoader loader) throws IOException {
        List<Object[]> out = new ArrayList<>();
        Class<? extends java.lang.annotation.Annotation> tuner;
        try {
            tuner = Class.forName("com.pedropathing.tuning.autotune.Tuner").asSubclass(java.lang.annotation.Annotation.class);
        } catch (ClassNotFoundException e) {
            return out;
        }
        for (String n : classNames()) {
            Class<?> c;
            try {
                c = Class.forName(n, false, loader);
            } catch (Throwable t) {
                continue;
            }
            for (Method m : c.getDeclaredMethods()) {
                if (!m.isAnnotationPresent(tuner) || !Modifier.isStatic(m.getModifiers()) || m.getParameterCount() != 0) continue;
                try {
                    Object ann = m.getAnnotation(tuner);
                    String name = (String) tuner.getMethod("name").invoke(ann);
                    m.setAccessible(true);
                    out.add(new Object[]{name, m});
                } catch (ReflectiveOperationException ignored) {
                }
            }
        }
        out.sort((x, y) -> ((String) x[0]).compareTo((String) y[0]));
        return out;
    }
}
