package android.os;

public class Process {
    // the simulator restarts the Robot Controller instead of exiting the whole program
    public static volatile Runnable onKill;

    public static int myTid() { return (int) Thread.currentThread().getId(); }
    public static int myPid() { return (int) ProcessHandle.current().pid(); }
    public static int myUid() { return 0; }
    public static void setThreadPriority(int p) {}
    public static void setThreadPriority(int t, int p) {}
    public static int getThreadPriority(int t) { return 0; }
    public static void killProcess(int pid) {
        Runnable r = onKill;
        if (r != null) r.run();
        else System.exit(1);
    }
}
