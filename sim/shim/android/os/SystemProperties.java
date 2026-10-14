package android.os;
public class SystemProperties {
    private static final java.util.Map<String, String> P = new java.util.concurrent.ConcurrentHashMap<>();
    public static String get(String k) { return P.getOrDefault(k, ""); }
    public static String get(String k, String d) { return P.getOrDefault(k, d); }
    public static boolean getBoolean(String k, boolean d) { String v = P.get(k); return v == null ? d : v.equals("1") || v.equalsIgnoreCase("true"); }
    public static int getInt(String k, int d) { try { return Integer.parseInt(P.get(k)); } catch (Exception e) { return d; } }
    public static long getLong(String k, long d) { try { return Long.parseLong(P.get(k)); } catch (Exception e) { return d; } }
    public static void set(String k, String v) { P.put(k, v); }
}
