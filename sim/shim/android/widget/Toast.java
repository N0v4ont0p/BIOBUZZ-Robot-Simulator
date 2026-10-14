package android.widget;

public class Toast {
    public static final int LENGTH_SHORT = 0, LENGTH_LONG = 1;
    public static Toast makeText(android.content.Context c, CharSequence text, int duration) { return new Toast(); }
    public void show() {}
    public void cancel() {}
    public void setGravity(int gravity, int x, int y) {}
}
