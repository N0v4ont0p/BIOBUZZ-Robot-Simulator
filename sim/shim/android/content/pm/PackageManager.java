package android.content.pm;

// The simulator's "app": lastUpdateTime is when the simulator last installed a build (Sim.build sets it).
public class PackageManager {
    public static class NameNotFoundException extends Exception {
        public NameNotFoundException(String name) {
            super(name);
        }
    }

    public PackageInfo getPackageInfo(String packageName, int flags) throws NameNotFoundException {
        PackageInfo p = new PackageInfo();
        p.packageName = packageName;
        p.lastUpdateTime = Long.getLong("sim.installTimeMs", 0L);
        p.firstInstallTime = p.lastUpdateTime;
        return p;
    }
}
