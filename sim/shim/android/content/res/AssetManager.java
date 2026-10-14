package android.content.res;

// serves the Pedro tuning page that ships inside the Pedro tuning library (extracted by build.sh)
public class AssetManager {
    public java.io.InputStream open(String path) throws java.io.IOException {
        java.io.File root = new java.io.File(System.getProperty("sim.pedroWeb", "build/pedro-web")).getCanonicalFile();
        String rel = path.startsWith("pedro/") ? path.substring(6) : path;
        java.io.File f = new java.io.File(root, rel).getCanonicalFile();
        if (!f.getPath().startsWith(root.getPath()) || !f.isFile()) throw new java.io.FileNotFoundException(path);
        return new java.io.FileInputStream(f);
    }
    public String[] list(String path) { return new String[0]; }
}
