import org.firstinspires.ftc.teamcode.subsystems.ShotCurve;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The robot's ShotCurve from the command line, for sim/tests/test_shot_curve.py. stdin: one "distance,speed,hood,feed"
// row per line, then "?" and one distance per line. stdout: per distance "lines speed,hood,feed | smooth speed,hood,feed".
public class CurveCli {
    public static void main(String[] args) throws Exception {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        List<double[]> rows = new ArrayList<>();
        List<Double> ask = new ArrayList<>();
        boolean asking = false;
        for (String line = r.readLine(); line != null; line = r.readLine()) {
            line = line.trim();
            if (line.isEmpty()) continue;
            if (line.equals("?")) {
                asking = true;
                continue;
            }
            if (asking) {
                ask.add(Double.parseDouble(line));
            } else {
                String[] c = line.split(",");
                rows.add(new double[]{Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[2]), Double.parseDouble(c[3])});
            }
        }
        double[][] t = rows.toArray(new double[0][]);
        ShotCurve lines = new ShotCurve(t, false), smooth = new ShotCurve(t, true);
        System.out.println("# " + smooth.describe() + (smooth.pulled().isEmpty() ? "" : " pulled " + smooth.pulled()));
        for (double d : ask) {
            System.out.println(String.format(Locale.US, "%.3f %.4f %.5f %.4f %.4f %.5f %.4f", d, lines.speed(d), lines.hood(d), lines.feed(d),
                    smooth.speed(d), smooth.hood(d), smooth.feed(d)));
        }
    }
}
