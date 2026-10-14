package android.app;

public class Activity extends android.content.ContextWrapper {
    public Activity() { super(null); }
    public void runOnUiThread(Runnable r) { r.run(); }
    public boolean isFinishing() { return false; }
    public boolean isDestroyed() { return false; }
    public Application getApplication() { return null; }
}
