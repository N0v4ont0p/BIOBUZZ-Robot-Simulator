package org.biobuzz.sim.rc;

import com.qualcomm.robotcore.hardware.ControlSystem;
import com.qualcomm.robotcore.hardware.configuration.ConfigurationType;
import com.qualcomm.robotcore.hardware.configuration.ConfigurationTypeManager;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Device types, straight from the SDK's own ConfigurationTypeManager (the list the real Configure Robot screen
// offers). The manager learns them by scanning classes at app start; the simulator does the same scan once.
public final class Devices {
    public static final String PINPOINT_TAG = "goBILDAPinpoint";
    private static boolean scanned;

    public static synchronized void scan(List<File> jars) {
        if (scanned) return;
        ConfigurationTypeManager m = ConfigurationTypeManager.getInstance();
        m.filterAllClassesStart();
        ClassLoader cl = Devices.class.getClassLoader();
        for (File jar : jars) {
            try (ZipFile z = new ZipFile(jar)) {
                for (Enumeration<? extends ZipEntry> e = z.entries(); e.hasMoreElements(); ) {
                    String n = e.nextElement().getName();
                    if (!n.endsWith(".class") || n.contains("$")) continue;
                    try {
                        m.filterClass(Class.forName(n.substring(0, n.length() - 6).replace('/', '.'), false, cl));
                    } catch (Throwable ignored) {
                        // classes that need Android parts the simulator does not have are not device types we use
                    }
                }
            } catch (java.io.IOException ignored) {
            }
        }
        m.filterAllClassesComplete();
        scanned = true;
    }

    // every non-deprecated type of this flavor that works on a REV hub, sorted by name
    @SuppressWarnings("unchecked")
    public static List<Map<String, String>> list(ConfigurationType.DeviceFlavor flavor) {
        List<Map<String, String>> out = new ArrayList<>();
        Map<String, ConfigurationType> all;
        try {
            java.lang.reflect.Field f = ConfigurationTypeManager.class.getDeclaredField("mapTagToConfigurationType");
            f.setAccessible(true);
            all = (Map<String, ConfigurationType>) f.get(ConfigurationTypeManager.getInstance());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        for (ConfigurationType t : all.values()) {
            if (t.getDeviceFlavor() != flavor || t.isDeprecated() || !t.isCompatibleWith(ControlSystem.REV_HUB)) continue;
            Map<String, String> m = new LinkedHashMap<>();
            m.put("tag", t.getXmlTag());
            m.put("name", t.getName());
            out.add(m);
        }
        out.sort((a, b) -> a.get("name").compareToIgnoreCase(b.get("name")));
        return out;
    }

    public static Set<String> tags(ConfigurationType.DeviceFlavor flavor) {
        Set<String> s = new LinkedHashSet<>();
        for (Map<String, String> m : list(flavor)) s.add(m.get("tag"));
        return s;
    }
}
