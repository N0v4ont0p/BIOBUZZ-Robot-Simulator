"""Program 6 (Position Check): velocity direction at heading 90, start corner, a 96 in push along the wall (pod
scale), 10 turns by itself and lined up again (gyro scale), start corner again, a 15 s loop pushed by hand and back
into the same corner against the walls. The hidden robot's pods and gyro are a little off their catalog values:
the two scale steps must measure them, and with them the loop passes. Wrong pod offsets fail."""
import math
import time

from harness import running_sim


def prerequisites(s, offsets=None):
    x = s.get("expected")
    v = {k: round(x[k]["value"], 3) for k in ("centerToFront", "centerToBack", "centerToLeft", "centerToRight", "xPodOffset", "yPodOffset")}
    for k in ("frontLeftDirection", "backLeftDirection", "frontRightDirection", "backRightDirection"):
        v[k] = "FORWARD" if x[k]["value"] > 0 else "REVERSE"
    for k in ("xPodDirection", "yPodDirection"):
        v[k] = "FORWARD" if x[k]["value"] > 0 else "REVERSED"
    if offsets:
        v.update(offsets)
    s.set_values(v)
    return x


def push(s, wait=None, **kw):
    s.hand("drive", "push", **kw)
    time.sleep(wait if wait is not None else kw.get("seconds", 1.2) + 0.6)


def into_corner(s, x):
    """Bring the robot back into the red corner by hand turned a quarter turn left (heading 90: left side on the
    alliance wall, back on the side wall), then press it back and left against the two walls."""
    for _ in range(6):
        d = s.world()["drive"]
        dh = (90 - d["headingDeg"] + 540) % 360 - 180
        if abs(dh) > 1:
            push(s, turnDeg=dh, seconds=max(0.6, abs(dh) / 90))
        d = s.world()["drive"]
        tx, ty = x["centerToLeft"]["value"] + 2, x["centerToBack"]["value"] + 2
        ex, ey = tx - d["x"], ty - d["y"]
        if math.hypot(ex, ey) < 0.5:
            break
        # robot faces +y here: forward = +y, left = -x
        push(s, forward=ey, left=-ex, seconds=max(0.6, math.hypot(ex, ey) / 25))
    push(s, forward=-5, left=5, seconds=1.0, wait=1.8, square=True)


def push_to(s, tx, ty, tol=0.05, speed=25):
    """Push the robot until its centre is at (tx, ty), reading a tape measure (the true position)."""
    for _ in range(10):
        d = s.world()["drive"]
        ex, ey = tx - d["x"], ty - d["y"]
        if math.hypot(ex, ey) < tol:
            return
        h = math.radians(d["headingDeg"])
        push(s, forward=ex * math.cos(h) + ey * math.sin(h), left=-ex * math.sin(h) + ey * math.cos(h),
             seconds=max(0.6, math.hypot(ex, ey) / speed))
    raise AssertionError(f"could not push to {tx}, {ty}: {s.world()['drive']}")


def turn_to(s, heading, tol=0.05):
    """Turn the robot by hand until its edge lines up with a tile seam at this heading."""
    for _ in range(10):
        dh = (heading - s.world()["drive"]["headingDeg"] + 540) % 360 - 180
        if abs(dh) < tol:
            return
        push(s, turnDeg=dh, seconds=max(0.6, abs(dh) / 90))
    raise AssertionError(f"could not turn to {heading}: {s.world()['drive']}")


def scales(s, x):
    """Steps 3 and 4: 96 in along the side wall, then 10 turns in open space and lined up with the same seam."""
    s.wait_text("STEP 3 of 6")
    d = s.world()["drive"]
    push_to(s, d["x"] + 96, d["y"])
    s.press("cross")
    s.wait_text("STEP 4 of 6")
    push_to(s, d["x"] + 96, 40)
    turn_to(s, 0)
    s.press("cross")
    s.wait_text("Pinpoint says", timeout=60)
    time.sleep(0.5)
    turn_to(s, 0)
    s.press("cross")
    s.wait_text("STEP 5 of 6")


def run(s, offsets=None):
    s.preset_config()
    x = prerequisites(s, offsets)
    s.hand("robot", "onFloor")
    s.hand("drive", "place", x=72, y=72, headingDeg=90, exact=True)
    s.init("6 Position Check")
    s.press("circle")
    s.wait_text("Alliance: RED")
    s.start()
    s.wait_text("STEP 1 of 6")
    # the Pinpoint starts at heading 0: turn the robot until the screen reads about 90, then push it straight
    push(s, turnDeg=90, seconds=1.5)
    push(s, forward=24, seconds=1.5, wait=0.3)
    s.wait_text("Buttons: cross = next", timeout=8)
    assert "FAIL" not in s.telemetry(), s.telemetry()
    time.sleep(1.5)
    s.press("cross")
    for step in (2, 5):
        s.wait_text(f"STEP {step} of 6")
        s.hand("drive", "place", x=x["centerToBack"]["value"], y=x["centerToRight"]["value"], headingDeg=0, exact=True)
        push(s, forward=-3, left=-3, seconds=0.8, wait=1.5, square=True)
        s.press("cross")
        if step == 2:
            scales(s, x)
    s.wait_text("STEP 6 of 6")
    t0 = time.time()
    push(s, forward=36, seconds=2)
    push(s, turnDeg=90, seconds=1.5)
    push(s, forward=30, seconds=2)
    push(s, turnDeg=-135, seconds=2)
    push(s, forward=20, left=10, seconds=2)
    push(s, turnDeg=45, seconds=1)
    while time.time() - t0 < 15.5:
        time.sleep(0.5)
    into_corner(s, x)
    s.press("cross")
    st = s.wait_text("Off by")
    return s.telemetry()


if __name__ == "__main__":
    with running_sim(seed=4) as s:
        tele = run(s)
        assert "PASS: the robot keeps track of where it is." in tele, tele
        res = s.file("biobuzz_results.csv")
        assert "velocityFrame,field" in res, res
        x = s.get("expected")
        got = {l.split(",")[1]: float(l.split(",")[2]) for l in res.splitlines()[1:] if l.split(",")[1] in ("podTicksPerMm", "imuYawScalar")}
        for k, v in got.items():
            e = x[k]
            print(f"  {k}: measured {v:.5f}, truth {e['value']:.5f} +/- {e['tolerance']}")
            assert abs(v - e["value"]) <= e["tolerance"], (k, v, e)
        assert len(got) == 2, res
        s.press("cross")
        s.wait_text("PROGRAM 6 COMPLETE")
        s.stop()
        s.wait(lambda st: st["ds"]["active"] == "7 Flywheel Tuner", 10, "program 7 auto-loaded")
    print("PASS program 6, good Pinpoint numbers")
    with running_sim(seed=4) as s:
        tele = run(s, offsets={"xPodOffset": 0.0, "yPodOffset": 0.0})
        assert "Too far off" in tele, tele
    print("PASS program 6, wrong pod offsets are caught")
    with running_sim(seed=4) as s:
        x = s.get("expected")
        tele = run(s, offsets={"centerToLeft": round(x["centerToLeft"]["value"] + 1.5, 2)})
        assert "Too far off" in tele, tele
    print("PASS program 6, a wrong robot edge size is caught")
