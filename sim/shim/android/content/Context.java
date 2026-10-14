package android.content;

public abstract class Context {
    static final java.util.Properties STR = new java.util.Properties();
    static {
        try (java.io.InputStream in = Context.class.getResourceAsStream("/res/strings.properties")) {
            if (in != null) STR.load(new java.io.InputStreamReader(in, "UTF-8"));
        } catch (Exception e) {
        }
    }
    public String getString(int id) { return STR.getProperty(Integer.toString(id), "res#" + id); }
    public String getString(int id, Object... a) { return String.format(getString(id), a); }
    public Object getSystemService(String name) { return null; }
    public String getPackageName() { return "com.qualcomm.ftcrobotcontroller"; }
    public android.content.pm.PackageManager getPackageManager() { return new android.content.pm.PackageManager(); }
    public java.io.File getFilesDir() { return new java.io.File(android.os.Environment.getExternalStorageDirectory(), "app-files"); }
    public java.io.File getCacheDir() { return new java.io.File(android.os.Environment.getExternalStorageDirectory(), "app-cache"); }
    public Context getApplicationContext() { return this; }
    public Context getBaseContext() { return this; }
    public android.content.res.AssetManager getAssets() { return new android.content.res.AssetManager(); }
    public android.content.res.Resources getResources() { return new android.content.res.Resources(this); }
}
