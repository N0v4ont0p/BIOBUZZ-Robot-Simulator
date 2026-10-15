"""The programmer step, done the way docs/BIOBUZZ_PREREQUISITES.md Part 2 says: read the files copied off the robot
(biobuzz_results.csv, biobuzz_<side>_points.csv) and work out what goes where.

    apply_results.py <outbox-dir> [--sandbox <root>] [--apply]

Prints JSON: values (for set_tuning.py: team-config.yaml keys and the code settings), info (info-only keys, not
entered), stop (red STOP results: nothing entered for that part), notes, unknown keys, byHand (blank YAML keys no
file fills: Pedro page, CAD sheet, E-1). With --apply it also enters the values in the sandbox (set_tuning.py),
runs render.sh and adds the diff it made.
"""
import csv
import difflib
import json
import math
import os
import re
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import set_tuning  # noqa: E402

SIDES = ("front", "back")
INTO_YAML = ["yawTicksPerDegFront", "yawTicksPerDegBack", "yawMinDeg", "yawMaxDeg", "yawPowerSign", "yawKs", "yawKv",
             "yawTuningVoltage", "yawKp",
             "frontHoodMin", "frontHoodMax", "backHoodMin", "backHoodMax", "rampStowed", "rampDeployed",
             "frontGateClosed", "frontGateOpen", "backGateClosed", "backGateOpen", "gateTravelS", "rampTravelS"]
FLYWHEEL = ["FlywheelP", "FlywheelI", "FlywheelD", "FlywheelF", "ReadyBand", "IdleVelocity", "FlywheelVoltage",
            "ReadyBandLow", "ReadyBandHigh", "ReadyBandLowSpeed", "ReadyBandHighSpeed"]
INTO_YAML += [s + k for s in SIDES for k in FLYWHEEL]
# Pedro's tuners (program 5) save their results to the same file
PEDRO = ["xPodOffset", "yPodOffset", "maxAchievableForwardVelocity", "maxAchievableStrafeVelocity", "naturalForwardDeceleration",
         "naturalStrafeDeceleration", "headingBrakingLinearCoefficient", "headingBrakingQuadraticCoefficient", "headingKP",
         "forwardBrakingLinearCoefficient", "forwardBrakingQuadraticCoefficient", "strafeBrakingLinearCoefficient",
         "strafeBrakingQuadraticCoefficient", "forwardTranslationalPrimaryKP", "forwardTranslationalSecondaryKP",
         "strafeTranslationalPrimaryKP", "strafeTranslationalSecondaryKP", "coastKV", "brakeKV"]
INTO_YAML += PEDRO + ["podTicksPerMm", "imuYawScalar", "velocityFrameField", "yawPlayDeg", "yawIndexDeg"]
# programs 8 / 9: the yaw trim each launcher scored with; past MAX_TRIM_DEG it is a bad turret zero, not aim (AutoAim)
TRIMS = ["frontYawTrimDeg", "backYawTrimDeg"]
MAX_TRIM_DEG = 10
INTO_YAML += TRIMS
# programs 8 / 9: the hood direction check (+1 / -1)
# program 3's turret homing (NaN = that stop or the whole homing is not usable, nothing entered)
INTO_YAML += ["yawHomeLeftDeg", "yawHomeRightDeg", "yawHomeFastDegS", "yawHomeCreepDegS", "yawHomeCrossDegS"]
INTO_CODE = {"intakeReversed": "Intake.REVERSED", "frontFlywheelReversed": "Launcher.FRONT_REVERSED",
             "backFlywheelReversed": "Launcher.BACK_REVERSED"}
DIRECTION_KEYS = list(set_tuning.DIRECTIONS)
INFO = ["devicesFound", "batteryVolts", "encodersNotCounting", "encodersCrossed", "yawReactionTimeS", "yawSettleS", "yawPastTargetDeg",
        "yawSpeedPoints", "velocityFrame", "loopErrorInches", "loopErrorDegrees", "yawHomeSpreadDeg", "yawHomeTypicalS", "yawHomeWorstS"]
INFO += [s + k for s in SIDES for k in ("FlywheelEncoderOpposite", "TopVelocity", "FlywheelThrowsOut", "ShotPoints")]
MAX_POINTS = 16
WATCHED = ["team-config.yaml", set_tuning.CODE_FLAGS["Intake.REVERSED"][0], set_tuning.CODE_FLAGS["Launcher.FRONT_REVERSED"][0],
           "tools/codegen/Constants.java.tpl"]


def newest(path):
    """key -> (value, time_ms, program): the newest line per key wins (a redone step adds a newer line)."""
    out = {}
    if not os.path.isfile(path):
        return out
    with open(path, encoding="utf-8") as f:
        for row in csv.DictReader(f):
            try:
                t = int(row["time_ms"])
            except (KeyError, TypeError, ValueError):
                continue
            k = row.get("key")
            if k and (k not in out or t >= out[k][1]):
                out[k] = (row.get("value", ""), t, row.get("program", ""))
    return out


def points(path):
    """Saved shot table points, in file order, minus the ones a later '# removed <distance>' line cancels.
    Other '#' lines (session starts, check shots) are not points."""
    pts = []
    if not os.path.isfile(path):
        return pts
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("distance_in"):
                continue
            if line.startswith("# removed"):
                d = float(line.split()[-1])
                for i in range(len(pts) - 1, -1, -1):
                    if abs(pts[i][0] - d) < 0.006:
                        del pts[i]
                        break
                continue
            # "# session" and "# check" lines are notes, not table points
            if line.startswith("#"):
                continue
            c = line.split(",")
            pts.append([float(c[0]), float(c[1]), float(c[2]), float(c[3])])
    return pts


def number(s):
    try:
        v = float(s)
    except (TypeError, ValueError):
        return None
    return None if math.isnan(v) or math.isinf(v) else v


def yaml_values(root):
    """tuning_constants keys in the sandbox team-config.yaml: key -> (value or None, tuning_procedure_ref)."""
    out = {}
    path = os.path.join(root, "team-config.yaml")
    if not os.path.isfile(path):
        return out
    inside = False
    with open(path, encoding="utf-8") as f:
        for line in f:
            if re.match(r"^\w", line):
                inside = line.startswith("tuning_constants:")
            m = re.match(r"^\s+(\w+): \{value: ([^,]*),", line)
            if inside and m and (m.group(2) == "null" or number(m.group(2)) is not None):
                ref = re.search(r'tuning_procedure_ref: "?([^"}]*)', line)
                out[m.group(1)] = (number(m.group(2)), ref.group(1).strip() if ref else "")
    return out


def plan(outbox, sandbox=None):
    res = newest(os.path.join(outbox, "biobuzz_results.csv"))
    p = {"dir": outbox, "files": sorted(f for f in os.listdir(outbox) if not f.startswith(".")) if os.path.isdir(outbox) else [],
         "values": {}, "info": {}, "stop": [], "notes": [], "unknown": [], "byHand": [], "shots": {}}
    skip = set()

    for s in SIDES:
        opp = res.get(s + "FlywheelEncoderOpposite")
        if opp and opp[0] == "true":
            skip.add(s + "FlywheelReversed")
            p["stop"].append(f"{s} flywheel: program 2 stopped, \"encoder counts the opposite way\". Nothing entered for it. "
                             f"Put its encoder cable in the same port number as its motor; if it already is, swap the motor's "
                             f"red/black wires. Then run program 2 again and send the new file.")
        out = res.get(s + "FlywheelThrowsOut")
        f_line = res.get(s + "FlywheelF")
        if out and out[0] == "false" and (not f_line or f_line[1] <= out[1]):
            skip.update(s + k for k in FLYWHEEL)
            p["stop"].append(f"{s} flywheel: program 7 says the element did NOT fly out, so program 2's answer for it was "
                             f"wrong. Nothing from program 7 entered for it. Run program 2 again for this flywheel, send the "
                             f"file, then program 7 after the new build.")

    for key, (value, t, prog) in sorted(res.items(), key=lambda kv: kv[1][1]):
        if key in skip:
            continue
        if key in INTO_CODE:
            p["values"][INTO_CODE[key]] = value == "true"
        elif key in DIRECTION_KEYS:
            p["values"][key] = value.upper()
        elif key in INTO_YAML:
            v = number(value)
            if v is None:
                p["notes"].append(f"{key} = {value}: not a number, not entered ({prog} did not finish it)")
            elif key in TRIMS and abs(v) > MAX_TRIM_DEG:
                p["notes"].append(f"{key} = {value}: over {MAX_TRIM_DEG} deg, not entered. The turret was zeroed while turned; "
                                  f"zero it straight forward (cross in INIT) and run {prog} again.")
            else:
                p["values"][key] = v
        elif key in INFO:
            p["info"][key] = value
        else:
            p["unknown"].append(key)

    info = p["info"]
    # older files: program 6 step 1 saved only "velocityFrame"
    if "velocityFrame" in info and "velocityFrameField" not in p["values"]:
        p["values"]["velocityFrameField"] = 1 if info["velocityFrame"] == "field" else 0
    if info.get("encodersNotCounting", "none") != "none":
        p["notes"].append(f"Program 1: encoders not counting: {info['encodersNotCounting']}. Fix those cables before program 2.")
    if info.get("encodersCrossed", "none") != "none":
        p["notes"].append(f"Program 1: encoder cables crossed: {info['encodersCrossed']} (turning the first part counted on the "
                          f"second port). Swap those encoder cables, or turn one part at a time, before program 2.")
    volts = number(info.get("batteryVolts"))
    if volts is not None and volts < 12.5:
        p["notes"].append(f"Program 1: battery was {volts:.2f} V (needs 12.5 V or more). Swap in a charged battery for tuning runs.")
    found = info.get("devicesFound", "")
    if "/" in found and found.split("/")[0] != found.split("/")[1]:
        p["notes"].append(f"Program 1: only {found} device names matched. Fix Configure Robot.")
    if info.get("velocityFrame") == "robot":
        p["notes"].append("Program 6 step 1 FAILED: velocity is in robot directions. The programmer has to look at this.")
    li, ld = number(info.get("loopErrorInches")), number(info.get("loopErrorDegrees"))
    if li is not None and (li > 1 or (ld is not None and ld > 2)):
        p["notes"].append(f"Program 6 loop error {li:.2f} in / {ld or 0:.2f} deg (good: within 1 in and 2 deg). Redo the Pinpoint tuning.")

    for s in SIDES:
        pts = sorted(points(os.path.join(outbox, f"biobuzz_{s}_points.csv")))
        if not pts:
            continue
        if len(pts) > MAX_POINTS:
            p["notes"].append(f"{s} shot table: {len(pts)} points, the code holds {MAX_POINTS}. Only the first {MAX_POINTS} by distance are entered.")
            pts = pts[:MAX_POINTS]
        for i, (d, speed, hood, feed) in enumerate(pts, 1):
            for name, v in (("Distance", d), ("Speed", speed), ("Hood", hood), ("Feed", feed)):
                p["values"][f"{s}Shot{i:02d}{name}"] = v
        p["shots"][s] = len(pts)
        if sandbox:
            stale = [k for k, (v, _) in yaml_values(sandbox).items()
                     if v is not None and re.fullmatch(rf"{s}Shot(\d\d)Distance", k) and int(k[len(s) + 4:len(s) + 6]) > len(pts)]
            if stale:
                for k in stale:
                    for name in ("Distance", "Speed", "Hood", "Feed"):
                        p["values"][k[:-len("Distance")] + name] = None
                p["notes"].append(f"{s} shot table: {', '.join(sorted(stale))} held points from an older, longer table. "
                                  f"They are cleared (value: null) so the robot does not use them.")

    if sandbox:
        for k, (v, ref) in yaml_values(sandbox).items():
            if v is None and k not in INTO_YAML and not re.search(r"Shot\d\d", k):
                p["byHand"].append({"key": k, "ref": ref})
    return p


def apply(p, sandbox):
    before = {f: read(os.path.join(sandbox, f)) for f in WATCHED}
    p["ok"] = True
    p["render"] = ""
    if p["values"]:
        try:
            set_tuning.set_values(sandbox, p["values"])
        except SystemExit as e:
            p["ok"] = False
            p["render"] = f"Entering values failed: {e}"
        if p["ok"]:
            r = subprocess.run(["sh", os.path.join(sandbox, "tools/codegen/render.sh")], capture_output=True, text=True)
            p["render"] = r.stdout + r.stderr
            p["ok"] = r.returncode == 0
    p["diff"] = "".join("".join(difflib.unified_diff(before[f].splitlines(True), read(os.path.join(sandbox, f)).splitlines(True),
                                                     "a/" + f, "b/" + f, n=0)) for f in WATCHED)
    return p


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def main():
    args = sys.argv[1:]
    sandbox = args[args.index("--sandbox") + 1] if "--sandbox" in args else None
    p = plan(args[0], sandbox)
    if "--apply" in args:
        apply(p, sandbox)
    print(json.dumps(p, indent=1))


if __name__ == "__main__":
    main()
