package android.content.res;

// Resource lookups the SDK makes for its own strings (device type names, messages), served from the string
// table genR.py builds out of the SDK's AAR resources.
public class Resources {
    private static final java.util.Properties IDS = new java.util.Properties();
    static {
        try (java.io.InputStream in = Resources.class.getResourceAsStream("/res/ids.properties")) {
            if (in != null) IDS.load(new java.io.InputStreamReader(in, "UTF-8"));
        } catch (Exception e) {
        }
    }
    private final android.content.Context context;

    public Resources(android.content.Context context) {
        this.context = context;
    }

    public int getIdentifier(String name, String defType, String defPackage) {
        String n = name.startsWith("@") ? name.substring(1) : name;
        int colon = n.indexOf(':');
        if (colon >= 0) n = n.substring(colon + 1);
        if (!n.contains("/")) n = defType + "/" + n;
        String v = IDS.getProperty(n);
        return v == null ? 0 : Integer.parseInt(v);
    }

    public String getString(int id) { return context.getString(id); }
    public String getString(int id, Object... args) { return context.getString(id, args); }
    public CharSequence getText(int id) { return context.getString(id); }
    public String[] getStringArray(int id) { return new String[0]; }
    public boolean getBoolean(int id) { return false; }
    public int getInteger(int id) { return 0; }
}
