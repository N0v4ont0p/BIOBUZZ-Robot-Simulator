package android.os;

// The SDK watches folders for Blocks / OnBot Java changes; nothing to watch in the simulator.
public abstract class FileObserver {
    public static final int ACCESS = 1, MODIFY = 2, ATTRIB = 4, CLOSE_WRITE = 8, CLOSE_NOWRITE = 16, OPEN = 32,
            MOVED_FROM = 64, MOVED_TO = 128, CREATE = 256, DELETE = 512, DELETE_SELF = 1024, MOVE_SELF = 2048, ALL_EVENTS = 4095;

    public FileObserver(String path) {}
    public FileObserver(String path, int mask) {}
    public FileObserver(java.io.File file) {}
    public FileObserver(java.io.File file, int mask) {}
    public FileObserver(java.util.List<java.io.File> files) {}
    public FileObserver(java.util.List<java.io.File> files, int mask) {}
    public void startWatching() {}
    public void stopWatching() {}
    public abstract void onEvent(int event, String path);
}
