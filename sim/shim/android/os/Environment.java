package android.os;

public class Environment {
    public static java.io.File getExternalStorageDirectory() { return new java.io.File(System.getProperty("sim.sdcard", "work/robot-sdcard")); }
    public static String getExternalStorageState() { return "mounted"; }
}
