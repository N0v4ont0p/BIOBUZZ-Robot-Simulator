package org.biobuzz.sim.phys;

import com.google.gson.JsonObject;
import com.qualcomm.robotcore.hardware.PwmControl;

import org.biobuzz.sim.Sim;
import org.biobuzz.sim.hw.Hub;
import org.biobuzz.sim.hw.ServoPort;
import org.biobuzz.sim.truth.Truth;
import org.biobuzz.sim.web.ShotRoutes;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

// Elements in flight and the CELL targets. A launched element flies in 3-D with gravity and air drag from the turret's
// release point; it is MADE when it passes into a CELL opening (20 x 14 in, manual 9.6.2) and missed otherwise (rim,
// short, long, wide, or out over the wall). Every shot is recorded with what really happened, so the report card can
// compare it with the made / missed button the student pressed.
// Field frame as the robot code: origin = red alliance wall x audience wall corner, +x toward blue, +y toward the far
// wall, inches. The HIVE crossbar runs along x at y = 72; each HIVE has an audience CELL and a far CELL; the one that is
// up faces up and tilts toward its own half of the field. Blue CELLs are the 180-degree turn of the red ones.
public class Shots implements Part {
    public static final double G = 386.09;      // in/s^2
    private static final double RHO = 1.2, BOX_IN = 4, WALL_IN = 12, FIELD = 144;
    private static final int MAX_RECORDS = 300, MAX_LANDED = 80;

    static {
        ShotRoutes.register();
    }

    public static final class Cell {
        public static final double HALF_W = 10, HALF_H = 7;
        public final String id;
        public final double x, y, z, tiltDeg;
        public final int facing;                 // -1: opening faces the audience half (-y), +1: the far half
        final double ny, nz, vy, vz;             // opening normal and its 14 in axis (the 20 in axis is +x)

        Cell(String id, double x, double y, double z, double tiltDeg, int facing) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tiltDeg = tiltDeg;
            this.facing = facing;
            double t = Math.toRadians(tiltDeg);
            ny = facing * Math.sin(t);
            nz = Math.cos(t);
            vy = -facing * Math.cos(t);
            vz = Math.sin(t);
        }

        double side(double px, double py, double pz) {
            return ny * (py - y) + nz * (pz - z);
        }

        double v(double py, double pz) {
            return vy * (py - y) + vz * (pz - z);
        }

        Map<String, Object> map() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("x", x);
            m.put("y", y);
            m.put("z", z);
            m.put("tiltDeg", tiltDeg);
            m.put("facing", facing);
            m.put("halfW", HALF_W);
            m.put("halfH", HALF_H);
            return m;
        }
    }

    // a ball in the air; also used for the "what if" shots of the solver and the report card
    static final class Flight {
        double x, y, z, vx, vy, vz, r, kd, t;
        Cell in, target;
        boolean rim, outOfField, landed;
        double ox, oy, dirX, dirY, alongCell, latCell, zAt, latAt, crossV = Double.NaN, crossLeft = Double.NaN;
        boolean passed;

        Flight(double x, double y, double z, double vx, double vy, double vz, double r, double kd) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.r = r;
            this.kd = kd;
        }

        void aimAt(Cell c, double fromX, double fromY, double yaw) {
            target = c;
            ox = fromX;
            oy = fromY;
            dirX = Math.cos(yaw);
            dirY = Math.sin(yaw);
            alongCell = (c.x - ox) * dirX + (c.y - oy) * dirY;
            latCell = -(c.x - ox) * dirY + (c.y - oy) * dirX;
        }

        // false once it is in a CELL or on the floor
        boolean step(double dt, List<Cell> cells) {
            double sp = Math.sqrt(vx * vx + vy * vy + vz * vz);
            vx -= kd * sp * vx * dt;
            vy -= kd * sp * vy * dt;
            vz -= (G + kd * sp * vz) * dt;
            double px = x, py = y, pz = z;
            x += vx * dt;
            y += vy * dt;
            z += vz * dt;
            t += dt;
            for (Cell c : cells) {
                double d0 = c.side(px, py, pz), d1 = c.side(x, y, z);
                if ((d0 > 0) == (d1 > 0)) continue;
                double f = d0 / (d0 - d1);
                double ix = px + f * (x - px), iy = py + f * (y - py), iz = pz + f * (z - pz);
                double u = ix - c.x, v = c.v(iy, iz);
                boolean inward = d0 > 0;
                if (inward && c == target && Double.isNaN(crossV)) {
                    crossV = v;
                    // left (+) of the CELL centre as the shooter sees it: the angle off the line to it, in inches there
                    crossLeft = Math.hypot(c.x - ox, c.y - oy) * Math.toRadians(wrapDeg(Math.toDegrees(
                            Math.atan2(iy - oy, ix - ox) - Math.atan2(c.y - oy, c.x - ox))));
                }
                if (inward && Math.abs(u) <= Cell.HALF_W - 0.6 * r && Math.abs(v) <= Cell.HALF_H - 0.6 * r) {
                    in = c;
                    x = ix;
                    y = iy;
                    z = iz;
                    return false;
                }
                if (Math.abs(u) <= Cell.HALF_W + 3 && Math.abs(v) <= Cell.HALF_H + 3) {
                    double vn = vy * c.ny + vz * c.nz;
                    vy = 0.35 * (vy - 2 * vn * c.ny);
                    vz = 0.35 * (vz - 2 * vn * c.nz);
                    vx *= 0.35;
                    x = px;
                    y = py;
                    z = pz;
                    rim = true;
                }
            }
            if (!outOfField) {
                if (x < r || x > FIELD - r || y < r || y > FIELD - r) {
                    if (z > WALL_IN) {
                        outOfField = true;
                    } else {
                        if (x < r || x > FIELD - r) vx = -0.3 * vx;
                        if (y < r || y > FIELD - r) vy = -0.3 * vy;
                        x = Math.max(r, Math.min(FIELD - r, x));
                        y = Math.max(r, Math.min(FIELD - r, y));
                    }
                }
            }
            if (target != null && !passed && (x - ox) * dirX + (y - oy) * dirY >= alongCell) {
                passed = true;
                zAt = z;
                latAt = -(x - ox) * dirY + (y - oy) * dirX - latCell;
            }
            if (z <= r || t > 6) {
                z = r;
                landed = true;
                return false;
            }
            return true;
        }

        // for the speed search: how far above (+) or below (-) the opening centre it arrives
        double arrival() {
            if (!Double.isNaN(crossV)) return crossV;
            if (passed) return zAt > target.z ? 200 : -200;
            return -200;
        }

        String miss() {
            if (rim) return "rim";
            if (outOfField) return "over the wall";
            if (!passed) return "short";
            if (Math.abs(latAt) > Cell.HALF_W) return latAt > 0 ? "left" : "right";
            return zAt < target.z ? "short" : "long";
        }
    }

    static final class Record {
        int id, side;
        double time;
        long wallMs;     // computer clock at launch: matches the time_ms column of biobuzz_<side>_shots.csv
        String type, cause, result = "flying", detail = "";
        double robotX, robotY, headingDeg, turretDeg, launchDeg, yawDeg, exitMps, wheelTicks;
        double fromX, fromY, fromZ, distanceIn, aimErrorDeg, endX, endY, endZ;
        double arrivalAbove = Double.NaN, arrivalLeft = Double.NaN;
        String target;
        final List<double[]> samples = new ArrayList<>();
        Flight flight;

        Map<String, Object> map(boolean withSamples) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id);
            m.put("time", time);
            m.put("wallMs", wallMs);
            m.put("side", side == 0 ? "front" : "back");
            m.put("type", type);
            m.put("cause", cause);
            m.put("result", result);
            m.put("detail", detail);
            m.put("target", target);
            m.put("distanceIn", distanceIn);
            m.put("aimErrorDeg", aimErrorDeg);
            m.put("robot", new double[]{robotX, robotY, headingDeg});
            m.put("turretDeg", turretDeg);
            m.put("launchDeg", launchDeg);
            m.put("yawDeg", yawDeg);
            m.put("exitMps", exitMps);
            m.put("wheelTicks", wheelTicks);
            m.put("from", new double[]{fromX, fromY, fromZ});
            m.put("end", new double[]{endX, endY, endZ});
            // where it arrived at the CELL's plane, as a student sees it: inches above (+) / below the opening centre and
            // left (+) / right of it (NaN = it never got there); lets a test answer a "rim" shot the way a human would
            m.put("arrivalAboveIn", arrivalAbove);
            m.put("arrivalLeftIn", arrivalLeft);
            if (withSamples) m.put("samples", samples);
            return m;
        }
    }

    private final Robot robot;
    public final List<Cell> cells = new ArrayList<>();
    public final double[] pivotX = new double[2], pivotY = new double[2], releaseZ = new double[2], exitOffsetIn = new double[2];
    // no hood (team 2026-10-17): each launcher's angle is fixed by the build (CAD v1: 58.7 POLLEN, 58.5 NECTAR, about
    // 65 if the element leaves the last roller at once; the robot may differ: check with a side video)
    public final double[] launchDeg = new double[2];
    public final double yawSpreadDeg, pitchSpreadDeg, dragCd;
    private final List<Record> records = new ArrayList<>();
    private final List<double[]> landed = new ArrayList<>();
    private final Map<String, Integer> inCells = new LinkedHashMap<>();
    private final Random rng;
    private int nextId = 1;

    public Shots(Robot r, Truth t) {
        robot = r;
        rng = new Random(t.seed * 15485863L + 5);
        // turret rotation axes on the robot (x forward, y left), release height above the tiles, release past the axis:
        // CAD v1 (4.32 / -4.63 in, 12.2 / 12.0 in, within 0.2 in of the axis) give or take what a build changes
        pivotX[0] = pick(t, "turret.frontPivotX", 4.1, 4.5);
        pivotY[0] = pick(t, "turret.frontPivotY", -0.15, 0.15);
        pivotX[1] = pick(t, "turret.backPivotX", -4.85, -4.4);
        pivotY[1] = pick(t, "turret.backPivotY", -0.15, 0.15);
        releaseZ[0] = pick(t, "launcher.front.releaseHeightIn", 11.8, 12.6);
        releaseZ[1] = pick(t, "launcher.back.releaseHeightIn", 11.6, 12.4);
        exitOffsetIn[0] = pick(t, "launcher.front.exitOffsetIn", -0.3, 0.1);
        exitOffsetIn[1] = pick(t, "launcher.back.exitOffsetIn", -0.2, 0.1);
        launchDeg[0] = pick(t, "launcher.front.launchDeg", 57.5, 60.5);
        launchDeg[1] = pick(t, "launcher.back.launchDeg", 57.5, 60.5);
        yawSpreadDeg = t.num("shots.yawSpreadDeg", 0.6);
        pitchSpreadDeg = t.num("shots.pitchSpreadDeg", 0.5);
        dragCd = pick(t, "shots.dragCd", 0.45, 0.55);
        double hiveX = pick(t, "field.redHiveX", 58.5, 60.5);
        double cellDy = pick(t, "field.cellDy", 7.5, 9.0);
        double cellZ = pick(t, "field.cellZ", 44, 48);
        double tilt = pick(t, "field.cellTiltDeg", 40, 55);
        cells.add(new Cell("redAudience", hiveX, 72 - cellDy, cellZ, tilt, -1));
        cells.add(new Cell("redFar", hiveX, 72 + cellDy, cellZ, tilt, 1));
        cells.add(new Cell("blueAudience", FIELD - hiveX, 72 - cellDy, cellZ, tilt, -1));
        cells.add(new Cell("blueFar", FIELD - hiveX, 72 + cellDy, cellZ, tilt, 1));
    }

    // a hidden value: the scenario's if it sets one, otherwise random in [lo, hi] for this seed and path
    public static double pick(Truth t, String path, double lo, double hi) {
        return t.num(path, lo + draw(t, path) * (hi - lo));
    }

    public static int sign(Truth t, String path) {
        return t.num(path, draw(t, path) < 0.5 ? 1 : -1) < 0 ? -1 : 1;
    }

    // splitmix64 of (seed, path): java.util.Random's first draw barely changes between nearby seeds
    private static double draw(Truth t, String path) {
        long z = t.seed * 0x9E3779B97F4A7C15L + path.hashCode();
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        z ^= z >>> 31;
        return (z >>> 11) * 0x1.0p-53;
    }

    @Override
    public String id() {
        return "shots";
    }

    static double dragPerIn(double cd, double diameterIn, double massKg) {
        double dm = diameterIn * 0.0254;
        return 0.5 * RHO * cd * Math.PI * dm * dm / 4 / massKg * 0.0254;
    }

    // the element leaves the wheel: release point and direction from the robot pose, turret and launch angle right now
    public void launch(int side, Element e, double exitMps, double wheelTicks, String cause, World w) {
        Drive d = w.robot.drive;
        double h = d.heading;
        double turretDeg = side == 0 ? w.robot.turret.frontDeg() : w.robot.turret.backDeg();
        double yaw = h + Math.toRadians(turretDeg + rng.nextGaussian() * yawSpreadDeg);
        double pitch = Math.toRadians(launchDeg[side] + rng.nextGaussian() * pitchSpreadDeg);
        double c = Math.cos(h), s = Math.sin(h);
        double rx = c * pivotX[side] - s * pivotY[side], ry = s * pivotX[side] + c * pivotY[side];
        double px = d.x + rx, py = d.y + ry;
        double v = exitMps / 0.0254;
        double vx = v * Math.cos(pitch) * Math.cos(yaw) + d.fwdVel * c - d.leftVel * s - d.turnVel * ry;
        double vy = v * Math.cos(pitch) * Math.sin(yaw) + d.fwdVel * s + d.leftVel * c + d.turnVel * rx;
        Flight f = new Flight(px + Math.cos(yaw) * exitOffsetIn[side], py + Math.sin(yaw) * exitOffsetIn[side],
                releaseZ[side] + (w.robot.onBox ? BOX_IN : 0), vx, vy, v * Math.sin(pitch),
                e.radiusIn(), dragPerIn(dragCd, e.diameterIn, e.massKg));
        Cell target = aimedCell(px, py, yaw);
        f.aimAt(target, px, py, yaw);

        Record rec = new Record();
        rec.id = nextId++;
        rec.time = w.time;
        rec.wallMs = System.currentTimeMillis();
        rec.side = side;
        rec.type = e.type.name();
        rec.cause = cause;
        rec.robotX = d.x;
        rec.robotY = d.y;
        rec.headingDeg = Math.toDegrees(h);
        rec.turretDeg = turretDeg;
        rec.launchDeg = Math.toDegrees(pitch);
        rec.yawDeg = Math.toDegrees(yaw);
        rec.exitMps = exitMps;
        rec.wheelTicks = wheelTicks;
        rec.fromX = f.x;
        rec.fromY = f.y;
        rec.fromZ = f.z;
        rec.target = target.id;
        rec.distanceIn = Math.hypot(target.x - px, target.y - py);
        rec.aimErrorDeg = wrapDeg(rec.yawDeg - Math.toDegrees(Math.atan2(target.y - py, target.x - px)));
        rec.flight = f;
        rec.samples.add(new double[]{f.x, f.y, f.z});
        records.add(rec);
        while (records.size() > MAX_RECORDS) records.remove(0);
    }

    // the CELL the shot heads for: the one the smallest angle off the shot line (the nearest one "along the line" took
    // the other alliance's CELL behind ours from some spots, and judged the miss against it)
    private Cell aimedCell(double px, double py, double yaw) {
        Cell best = cells.get(0);
        double bestErr = Double.MAX_VALUE;
        for (Cell c : cells) {
            double err = Math.abs(wrapDeg(Math.toDegrees(yaw - Math.atan2(c.y - py, c.x - px))));
            if (err < bestErr) {
                bestErr = err;
                best = c;
            }
        }
        return best;
    }

    static double wrapDeg(double d) {
        d %= 360;
        if (d > 180) d -= 360;
        if (d <= -180) d += 360;
        return d;
    }

    @Override
    public void step(double dt, World w) {
        for (Record rec : records) {
            Flight f = rec.flight;
            if (f == null) continue;
            boolean flying = f.step(dt, cells);
            if (Math.round(f.t * 1000) % 10 == 0 || !flying) rec.samples.add(new double[]{f.x, f.y, f.z});
            if (flying) continue;
            rec.flight = null;
            if (f.target != null && f.passed) {
                rec.arrivalAbove = f.zAt - f.target.z;
                rec.arrivalLeft = f.latAt;
            }
            // where it met the opening's plane, as a person watching sees it (a rim hit included)
            if (!Double.isNaN(f.crossV)) {
                rec.arrivalAbove = f.crossV;
                rec.arrivalLeft = f.crossLeft;
            }
            rec.endX = f.x;
            rec.endY = f.y;
            rec.endZ = f.z;
            if (f.in != null) {
                rec.result = "made";
                rec.detail = "in the " + f.in.id + " CELL";
                inCells.merge(f.in.id, 1, Integer::sum);
            } else {
                rec.result = "missed";
                rec.detail = f.miss();
                if (!f.outOfField) landed.add(new double[]{f.x, f.y, rec.type.equals("NECTAR") ? 1 : 0});
                while (landed.size() > MAX_LANDED) landed.remove(0);
            }
        }
    }

    @Override
    public void state(Map<String, Object> out) {
        List<double[]> flying = new ArrayList<>();
        for (Record rec : records) {
            if (rec.flight != null) flying.add(new double[]{rec.flight.x, rec.flight.y, rec.flight.z, rec.type.equals("NECTAR") ? 1 : 0, rec.id});
        }
        out.put("inFlight", flying);
        out.put("landed", landed);
        out.put("inCells", inCells);
        List<Map<String, Object>> cs = new ArrayList<>();
        for (Cell c : cells) cs.add(c.map());
        out.put("cells", cs);
        out.put("lastId", nextId - 1);
        out.put("pivots", new double[][]{{pivotX[0], pivotY[0]}, {pivotX[1], pivotY[1]}});
    }

    @Override
    public boolean hand(String action, JsonObject a, World w) {
        if (action.equals("retrieve")) {
            landed.clear();
            inCells.clear();
            return true;
        }
        return false;
    }

    // ---- what the web page, the report card and the tests read (called with the world lock held)

    public List<Map<String, Object>> history(int since, boolean withSamples) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Record r : records) if (r.id > since) out.add(r.map(withSamples));
        return out;
    }

    private Cell cell(String id) {
        for (Cell c : cells) if (c.id.equals(id)) return c;
        return cells.get(0);
    }

    // practice direction: from the red audience CELL toward the red start corner
    private double[] approach(Cell c) {
        double n = Math.hypot(c.x, c.y);
        return new double[]{-c.x / n, -c.y / n};
    }

    private PwmControl.PwmRange range(ServoModel s) {
        for (Hub h : robot.hubs) for (ServoPort p : h.servos) if (p.servo == s) return p.range;
        return PwmControl.PwmRange.defaultRange;
    }

    private double servoDeg(ServoModel s, double position) {
        PwmControl.PwmRange r = range(s);
        return (r.usPulseLower + position * (r.usPulseUpper - r.usPulseLower) - 1500) * s.degPerUs + s.offsetDeg;
    }

    private double servoPosition(ServoModel s, double deg) {
        PwmControl.PwmRange r = range(s);
        return ((deg - s.offsetDeg) / s.degPerUs + 1500 - r.usPulseLower) / (r.usPulseUpper - r.usPulseLower);
    }

    // what the robot code needs to know about the launcher before shooting, from its true parts
    public Map<String, Object> guide() {
        Map<String, Object> m = new LinkedHashMap<>();
        String[] sides = {"front", "back"};
        double travel = 0;
        for (int i = 0; i < 2; i++) {
            ServoModel gate = robot.servoMechs.servo(i == 0 ? "gate.front" : "gate.back");
            double keep = gate.angleDeg;
            List<Double> closed = new ArrayList<>(), open = new ArrayList<>();
            for (int k = 0; k <= 200; k++) {
                double pos = k / 200.0, cmd = servoDeg(gate, pos);
                if (cmd < gate.stopLowDeg() + 2 || cmd > gate.stopHighDeg() - 2) continue;
                gate.angleDeg = cmd;
                double o = robot.servoMechs.gateOpen(i);
                if (o <= 0.02 && !robot.servoMechs.gateRubs(i)) closed.add(pos);
                if (o >= 0.98) open.add(pos);
            }
            gate.angleDeg = keep;
            double c = closed.isEmpty() ? Double.NaN : closed.get(closed.size() / 2);
            double o = open.isEmpty() ? Double.NaN : open.get(open.size() / 2);
            m.put(sides[i] + "GateClosed", round3(c));
            m.put(sides[i] + "GateOpen", round3(o));
            travel = Math.max(travel, Math.abs(servoDeg(gate, o) - servoDeg(gate, c)) / gate.speedDegPerS);
            if (i == 1) {
                // the second back-gate servo at the arm angles the first one gives
                ServoModel g2 = robot.servoMechs.servo("gate.back2");
                double c2 = servoPosition(g2, g2.horn(gate.outOf(servoDeg(gate, c))));
                double o2 = servoPosition(g2, g2.horn(gate.outOf(servoDeg(gate, o))));
                m.put("backGate2Closed", round3(c2));
                m.put("backGate2Open", round3(o2));
                travel = Math.max(travel, Math.abs(servoDeg(g2, o2) - servoDeg(g2, c2)) / g2.speedDegPerS);
            }
        }
        m.put("gateTravelS", Math.round(travel * 1.15 * 1000) / 1000.0);
        m.put("frontPivotX", pivotX[0]);
        m.put("frontPivotY", pivotY[0]);
        m.put("backPivotX", pivotX[1]);
        m.put("backPivotY", pivotY[1]);
        m.put("redFarCellX", cell("redFar").x);
        m.put("redFarCellY", cell("redFar").y);
        m.put("redAudienceCellX", cell("redAudience").x);
        m.put("redAudienceCellY", cell("redAudience").y);
        return m;
    }

    private static double round3(double v) {
        return Math.round(v * 1000) / 1000.0;
    }

    // inputs of a "what if" shot, copied under the world lock so the flights can run without it
    private final class Plan {
        final int side;
        final Cell cell;
        final double ax, ay, maxTicks;
        final Element.Type type;
        final double mass, feedPerPower;

        Plan(int side, Cell cell) {
            this.side = side;
            this.cell = cell;
            double[] a = approach(cell);
            ax = a[0];
            ay = a[1];
            maxTicks = 0.95 * robot.flywheels.trueTopTicks(side, robot.battery);
            type = side == 0 ? Element.Type.NECTAR : Element.Type.POLLEN;
            Intake in = robot.intake;
            mass = type == Element.Type.NECTAR ? in.nectarMassKg : in.pollenMassKg;
            feedPerPower = in.motor.spec.freeRadS / in.motor.spec.gear * in.rollerRadiusIn * in.grip;
        }

        Flight fly(double distance, double ticks, double feed, Random jit) {
            double m = mass, grip = 1, diam = type == Element.Type.NECTAR ? 3.6 : 2.8, dYaw = 0, dPitch = 0;
            if (jit != null) {
                m *= 1 + (jit.nextDouble() * 2 - 1) * 0.04;
                grip *= 1 + (jit.nextDouble() * 2 - 1) * 0.02;
                grip *= 1 + jit.nextGaussian() * robot.flywheels.feedSpread(side, feed);
                diam += (jit.nextDouble() * 2 - 1) * 0.06;
                dYaw = jit.nextGaussian() * yawSpreadDeg;
                dPitch = jit.nextGaussian() * pitchSpreadDeg;
            }
            double px = cell.x + ax * distance, py = cell.y + ay * distance;
            double yaw = Math.atan2(cell.y - py, cell.x - px) + Math.toRadians(dYaw);
            double pitch = Math.toRadians(launchDeg[side] + dPitch);
            double v = robot.flywheels.exitMps(side, type, m, grip, ticks, feed * feedPerPower) / 0.0254;
            Flight f = new Flight(px + Math.cos(yaw) * exitOffsetIn[side], py + Math.sin(yaw) * exitOffsetIn[side], releaseZ[side],
                    v * Math.cos(pitch) * Math.cos(yaw), v * Math.cos(pitch) * Math.sin(yaw), v * Math.sin(pitch),
                    diam / 2, dragPerIn(dragCd, diam, m));
            f.aimAt(cell, px, py, yaw);
            while (f.step(0.002, cells)) {
                // fly
            }
            return f;
        }

        double makeChance(double distance, double ticks, double feed) {
            return makeChance(distance, ticks, feed, 24);
        }

        double makeChance(double distance, double ticks, double feed, int n) {
            Random jit = new Random(1234567L + side);
            int made = 0;
            for (int k = 0; k < n; k++) if (fly(distance, ticks, feed, jit).in == cell) made++;
            return made / (double) n;
        }

        // the flywheel speed whose shot arrives at the opening's centre from this distance (NaN = it cannot get there)
        double speedFor(double distance) {
            double a = 100, b = maxTicks;
            if (fly(distance, b, 0.5, null).arrival() < 0) return Double.NaN;
            for (int it = 0; it < 22; it++) {
                double mid = (a + b) / 2;
                if (fly(distance, mid, 0.5, null).arrival() < 0) a = mid;
                else b = mid;
            }
            return (a + b) / 2;
        }
    }

    // the flywheel speed (ticks/s) that scores from this distance (pivot to CELL) at this launcher's fixed angle, and how
    // often; reachable = false when no speed up to 95 % of top gets it in at least half the time
    public Map<String, Object> solve(int side, double distance) {
        Plan plan;
        synchronized (World.LOCK) {
            plan = new Plan(side, cell("redAudience"));
        }
        double speed = plan.speedFor(distance);
        double chance = Double.isNaN(speed) ? 0 : plan.makeChance(distance, speed, 0.5);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("side", side == 0 ? "front" : "back");
        m.put("distance", distance);
        m.put("launchDeg", launchDeg[side]);
        if (Double.isNaN(speed) || chance < 0.5) {
            m.put("reachable", false);
            return m;
        }
        m.put("reachable", true);
        m.put("speed", speed);
        m.put("makeChance", chance);
        m.put("exitMps", robot.flywheels.exitMps(side, plan.type, plan.mass, 1, speed, 0.5 * plan.feedPerPower));
        return m;
    }

    // the distances (pivot to CELL, every 2 in from 20 to 140) this launcher scores from at its fixed angle: a fixed
    // angle only reaches a band (too close it flies over, too far the wheel is not fast enough)
    public Map<String, Object> band(int side) {
        Plan plan;
        synchronized (World.LOCK) {
            plan = new Plan(side, cell("redAudience"));
        }
        double lo = Double.NaN, hi = Double.NaN;
        for (double d = 20; d <= 140; d += 2) {
            double sp = plan.speedFor(d);
            if (Double.isNaN(sp) || plan.makeChance(d, sp, 0.5, 12) < 0.5) continue;
            if (Double.isNaN(lo)) lo = d;
            hi = d;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("side", side == 0 ? "front" : "back");
        m.put("launchDeg", launchDeg[side]);
        m.put("minDistance", lo);
        m.put("maxDistance", hi);
        return m;
    }

    // how often a setting scores from this distance on this robot (n shots with the true spread)
    public double chance(int side, double distance, double ticks, double feed, int n) {
        Plan plan;
        synchronized (World.LOCK) {
            plan = new Plan(side, cell("redAudience"));
        }
        return plan.makeChance(distance, ticks, feed, Math.max(1, n));
    }

    // programs 8 / 9: would each saved point score on this robot? (each point re-shot 24 times with the true spread)
    public Map<String, Object> makeable(int side) {
        List<double[]> points = savedPoints(side);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("side", side == 0 ? "front" : "back");
        if (points == null) return m;
        Plan plan;
        synchronized (World.LOCK) {
            plan = new Plan(side, cell("redAudience"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int ok = 0;
        for (double[] p : points) {
            double chance = plan.makeChance(p[0], p[1], p[2]);
            if (chance >= 0.5) ok++;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("distance", p[0]);
            row.put("speed", p[1]);
            row.put("feed", p[2]);
            row.put("makeChance", chance);
            rows.add(row);
        }
        m.put("points", rows);
        m.put("fraction", points.isEmpty() ? Double.NaN : ok / (double) points.size());
        return m;
    }

    // the rows the shot tuner kept, {distance, speed, feed}: biobuzz_<side>_points.csv minus its "# removed" lines; the
    // header and "# columns" lines say where the feed is (files from before 2026-10-17 have a hood column); null = no file
    private static List<double[]> savedPoints(int side) {
        Sim sim = Sim.get();
        if (sim == null) return null;
        File f = new File(sim.ws.sdcard, "FIRST/biobuzz_" + (side == 0 ? "front" : "back") + "_points.csv");
        if (!f.isFile()) return null;
        List<double[]> rows = new ArrayList<>();
        int feed = 2;
        try {
            for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.startsWith("distance_in") || line.startsWith("# columns ")) {
                    feed = java.util.Arrays.asList(line.replace("# columns ", "").split(",")).indexOf("feed");
                } else if (line.startsWith("# removed")) {
                    double d = Double.parseDouble(line.substring(9).trim());
                    for (int k = rows.size() - 1; k >= 0; k--) {
                        if (Math.abs(rows.get(k)[0] - d) < 0.006) {
                            rows.remove(k);
                            break;
                        }
                    }
                } else if (!line.isEmpty() && Character.isDigit(line.charAt(0))) {
                    String[] c = line.split(",");
                    rows.add(new double[]{Double.parseDouble(c[0]), Double.parseDouble(c[1]), Double.parseDouble(c[feed])});
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        return rows;
    }

    @Override
    public void expected(Map<String, Expected> out, World w) {
        String[] sides = {"front", "back"};
        for (int i = 0; i < 2; i++) {
            out.putIfAbsent(sides[i] + "PivotX", new Expected(pivotX[i], 0.25, "in", "turret axis, forward of the robot centre (CAD sheet C-3)"));
            out.putIfAbsent(sides[i] + "PivotY", new Expected(pivotY[i], 0.25, "in", "turret axis, left of the robot centre (CAD sheet C-3)"));
        }
        Cell far = cell("redFar"), aud = cell("redAudience");
        out.putIfAbsent("redFarCellX", new Expected(far.x, 0.5, "in", "red far CELL opening centre (field drawing H-1)"));
        out.putIfAbsent("redFarCellY", new Expected(far.y, 0.5, "in", "red far CELL opening centre (field drawing H-1)"));
        out.putIfAbsent("redAudienceCellX", new Expected(aud.x, 0.5, "in", "red audience CELL opening centre (field drawing H-1)"));
        out.putIfAbsent("redAudienceCellY", new Expected(aud.y, 0.5, "in", "red audience CELL opening centre (field drawing H-1)"));
        // ponytail: re-shoots every saved point under the world lock (tens of ms, only when the report card asks)
        for (int i = 0; i < 2; i++) {
            Map<String, Object> m = makeable(i);
            Object f = m.get("fraction");
            if (!(f instanceof Double) || ((Double) f).isNaN()) continue;
            out.put(sides[i] + "ShotPointsMakeable", new Expected((Double) f, 0, "share", String.format(Locale.US,
                    "share of the %d saved %s points whose speed scores at their distance on this robot (1.0 = all)",
                    ((List<?>) m.get("points")).size(), sides[i])));
        }
    }
}
