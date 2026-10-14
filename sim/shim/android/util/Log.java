package android.util;
public final class Log {
    public static final int VERBOSE = 2, DEBUG = 3, INFO = 4, WARN = 5, ERROR = 6, ASSERT = 7;
    public static int println(int p, String tag, String msg) { if (p >= Integer.getInteger("sim.loglevel", WARN)) System.err.println("[" + tag + "] " + msg); return 0; }
    public static boolean isLoggable(String t, int l) { return false; }
    public static String getStackTraceString(Throwable t) { java.io.StringWriter w = new java.io.StringWriter(); if (t != null) t.printStackTrace(new java.io.PrintWriter(w)); return w.toString(); }
    public static int v(String t, String m) { return println(VERBOSE, t, m); }
    public static int d(String t, String m) { return println(DEBUG, t, m); }
    public static int i(String t, String m) { return println(INFO, t, m); }
    public static int w(String t, String m) { return println(WARN, t, m); }
    public static int e(String t, String m) { return println(ERROR, t, m); }
    public static int e(String t, String m, Throwable x) { return println(ERROR, t, m + "\n" + getStackTraceString(x)); }
}
