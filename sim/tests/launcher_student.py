"""What a careful student (and the programmer) does on the launcher side, shared by the launcher tests.

The student judges by eye, like on the real robot: which way the wheel surface moves, whether the element went in
or flew out, whether the shot went in. Here "by eye" = reading the Workbench state the views draw.
"""
import time

SIDES = ["front", "back"]


def expected(s):
    return {k: v["value"] for k, v in s.get("expected").items()}


def guide(s):
    return s.get("launcher/guide")


def pick(exp, gd, key):
    """The true value for a team-config key: another part's expected() if it has one, else the launcher guide."""
    return exp[key] if key in exp and exp[key] is not None else gd[key]


def directions(exp):
    return {"Intake.REVERSED": exp["intakeReversed"] == 1,
            "Launcher.FRONT_REVERSED": exp["frontFlywheelReversed"] == 1,
            "Launcher.BACK_REVERSED": exp["backFlywheelReversed"] == 1}


def servo_values(exp, gd):
    keys = ["frontGateClosed", "frontGateOpen", "backGateClosed", "backGateOpen", "gateTravelS", "rampTravelS",
            "frontHoodMin", "frontHoodMax", "backHoodMin", "backHoodMax"]
    return {k: round(float(pick(exp, gd, k)), 4) for k in keys}


def flywheel_values(exp, volts=None):
    """Program 7's results; the true F is the one at the battery's present charge, so that is its voltage too."""
    out = {}
    for side in SIDES:
        if volts:
            out[side + "FlywheelVoltage"] = round(volts, 3)
        f = exp[side + "FlywheelF"]
        # P as program 7 picks it on the sim's flywheels (about 4 x F; its search starts at F / 10), I = P / 10
        out.update({side + "FlywheelF": round(f, 4), side + "FlywheelP": round(4 * f, 4), side + "FlywheelI": round(0.4 * f, 5),
                    side + "FlywheelD": 0, side + "ReadyBand": round(exp[side + "ReadyBand"]), side + "IdleVelocity": 0})
    return out


def yaw_values(s, exp):
    """Yaw Tuner results. The turret's own expected() when it has them, else worked out from the turret truth:
    the SDK flips a goBILDA motor's power and encoder together, so +power counts up while the turret turns CW."""
    keys = ["yawTicksPerDegFront", "yawTicksPerDegBack", "yawMinDeg", "yawMaxDeg", "yawKs", "yawKv", "yawPowerSign",
            "yawTuningVoltage", "yawKp"]
    if all(k in exp for k in keys):
        # gear play (E-6) and, on a robot with the index switch, where it turns on
        return {k: exp[k] for k in keys + ["yawPlayDeg", "yawIndexDeg"] if k in exp}
    t = s.get("truth")
    per_deg = -383.6 * t.get("turret.motorTurnsPerFrontTurn", 5.0) * t.get("turret.motorSign", 1.0) / 360
    return {"yawTicksPerDegFront": round(per_deg, 4), "yawTicksPerDegBack": round(per_deg / t.get("turret.backRatio", 1.0), 4),
            "yawMinDeg": t.get("turret.stopMinDeg", -175) + 5, "yawMaxDeg": t.get("turret.stopMaxDeg", 175) - 5,
            "yawKs": 0.06, "yawKv": 0.0019, "yawPowerSign": 1 if per_deg > 0 else -1, "yawTuningVoltage": 12.5, "yawKp": 8}


def geometry_values(exp, gd):
    out = {k: exp.get(k, 9.0) for k in ("centerToFront", "centerToBack", "centerToLeft", "centerToRight")}
    for k in ("frontPivotX", "frontPivotY", "backPivotX", "backPivotY",
              "redFarCellX", "redFarCellY", "redAudienceCellX", "redAudienceCellY"):
        out[k] = round(float(pick(exp, gd, k)), 3)
    out["xPodOffset"] = exp.get("xPodOffset", 3.5)
    out["yPodOffset"] = exp.get("yPodOffset", -4.25)
    for k in ("frontLeftDirection", "backLeftDirection", "frontRightDirection", "backRightDirection"):
        if k in exp:
            out[k] = "FORWARD" if exp[k] == 1 else "REVERSE"
    for k in ("xPodDirection", "yPodDirection"):
        if k in exp:
            out[k] = "FORWARD" if exp[k] == 1 else "REVERSED"
    out.update(FORESIGHT)
    return out


# what program 5's Foresight Tuner measured on a sim robot (the shot tuners drive themselves with Pedro)
FORESIGHT = {"maxAchievableForwardVelocity": 69.33, "maxAchievableStrafeVelocity": 57.85, "naturalForwardDeceleration": 19.71,
             "naturalStrafeDeceleration": 16.73, "headingBrakingLinearCoefficient": 0.0399, "headingBrakingQuadraticCoefficient": 0.0023,
             "headingKP": 5.014, "forwardBrakingLinearCoefficient": 0.0877, "forwardBrakingQuadraticCoefficient": 0.00056,
             "strafeBrakingLinearCoefficient": 0.0865, "strafeBrakingQuadraticCoefficient": 0.00067,
             "forwardTranslationalPrimaryKP": 0.2665, "forwardTranslationalSecondaryKP": 0.0985,
             "strafeTranslationalPrimaryKP": 0.322, "strafeTranslationalSecondaryKP": 0.119, "coastKV": 0.0152, "brakeKV": 0.0129}


def tuned_robot(s):
    """Everything programs 1 to 7 and the CAD / field sheets put into the code, with true values."""
    exp, gd = expected(s), guide(s)
    values = directions(exp)
    values.update(servo_values(exp, gd))
    values.update(flywheel_values(exp, s.state()["ds"]["battery"]))
    values.update(yaw_values(s, exp))
    values.update(geometry_values(exp, gd))
    return values


def start_corner(s, values):
    """Robot on the floor in the red start corner, pushed flush: back on the alliance wall, right side on the audience
    wall (a careful placement; a sloppy one is off by up to 1.5 deg, which the robot then aims off by all session)."""
    s.hand("robot", "onFloor")
    s.hand("drive", "place", x=values["centerToBack"], y=values["centerToRight"], headingDeg=0, careful=True)


def straighten_turrets(s, tol=0.3, timeout=20):
    """By hand, turns the turrets straight forward (the sim starts them up to 6 deg off), then lets go: what the screen
    asks before cross zeroes them."""
    end = time.time() + timeout
    while time.time() < end:
        err = -s.world()["turret"]["frontDeg"]
        if abs(err) < tol:
            break
        s.hand("turret", "turn", deg=max(-5, min(5, err)))
        time.sleep(0.2)
    s.hand("turret", "release")
    time.sleep(0.3)
    return s.world()["turret"]


def push_to(s, x, y, speed=30.0):
    """Push the robot (heading 0) so its centre ends at (x, y); the Pinpoint counts the push like a real one."""
    d = s.world()["drive"]
    dx, dy = x - d["x"], y - d["y"]
    secs = max(0.8, (dx * dx + dy * dy) ** 0.5 / speed)
    s.hand("drive", "push", forward=dx, left=dy, seconds=secs)
    time.sleep(secs + 0.6)


def results(s):
    text = s.file("biobuzz_results.csv") or ""
    out = {}
    for line in text.splitlines()[1:]:
        parts = line.split(",")
        if len(parts) >= 3:
            out[parts[1]] = parts[2]
    return out


def last_shot(s, side=None):
    shots = s.get("shots")["shots"]
    if side is not None:
        shots = [x for x in shots if x["side"] == side]
    return shots[-1] if shots else None


def wait_shot_done(s, after_id, timeout=8.0):
    """The shot launched after after_id, once it is in a CELL or on the floor (the student watches it land)."""
    end = time.time() + timeout
    while time.time() < end:
        shots = [x for x in s.get("shots")["shots"] if x["id"] > after_id]
        if shots and shots[-1]["result"] != "flying":
            return shots[-1]
        time.sleep(0.1)
    return None


def pull_trigger(s, axis="right_trigger", seconds=0.25):
    s.hold(axis, 1.0)
    time.sleep(seconds)
    s.hold(axis, 0.0)
    time.sleep(0.15)


def tele(s):
    return s.telemetry()


def value_after(text, caption):
    """The value of a telemetry line 'caption: value'."""
    for line in text.splitlines():
        if caption in line:
            return line.split(caption, 1)[1].lstrip(" :")
    return None


def spot(cell, toward, d, pivot):
    """Robot centre (heading 0) with this turret axis d inches from the CELL, on the line toward a point."""
    ux, uy = toward[0] - cell[0], toward[1] - cell[1]
    n = (ux * ux + uy * uy) ** 0.5
    return cell[0] + ux / n * d - pivot[0], cell[1] + uy / n * d - pivot[1]
