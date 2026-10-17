"""Program 3 (Turret Servos) on the hidden robot, done by a careful student: for each turret, nudge the servo until the
barrel looks straight forward, straight left and straight right (here: the world state the Turret view draws), then
as far each way as the cable and stops allow (stop when the cable goes tight or the servo buzzes, ease back), then
film both turrets swinging and count the slower one's frames. Every recorded number must land near the truth.
Then a tight front cable (re-routed when the student sees it pull) and a slow back servo (the speed comes from it)."""
import csv
import io
import sys
import time

from harness import running_sim
from test_p4_servos import BIG, SMALL, frames_to_open, tap

SIDES = ("front", "back")


def turret(s, side):
    return s.world()["turret"][SIDES[side]]


def step_sign(s, side):
    """+1 when R1 (servo position up) turns this turret left: watch it move."""
    before = turret(s, side)["deg"]
    tap(s, "right_bumper")
    time.sleep(0.3)
    after = turret(s, side)["deg"]
    if abs(after - before) < 0.5:
        tap(s, "left_bumper")
        time.sleep(0.3)
        return -1 if turret(s, side)["deg"] > after else 1
    return 1 if after > before else -1


def nudge_to(s, side, target, sign, tol=0.45):
    """Big steps while far, small ones close, like a student looking at the barrel."""
    for _ in range(400):
        t = turret(s, side)
        err = target - t["deg"]
        if abs(err) < tol:
            return t
        d = 1 if err * sign > 0 else -1
        tap(s, BIG[d] if abs(err) > 4 else SMALL[d])
        time.sleep(0.05)
        if t["cableTension"] > 0:
            return t
    raise AssertionError(f"{SIDES[side]} turret never reached {target}: {turret(s, side)}")


def to_far_end(s, side, way, sign):
    """Turn that way until it stops moving, the cable goes tight or the servo buzzes, then ease back until quiet."""
    d = way * sign
    last = turret(s, side)["deg"]
    for _ in range(200):
        tap(s, BIG[d])
        time.sleep(0.1)
        t = turret(s, side)
        if t["cableTension"] > 0 or t["buzz"] > 0.12 or t.get("atStop") or abs(t["deg"] - last) < 0.2:
            break
        last = t["deg"]
    for _ in range(60):
        t = turret(s, side)
        if t["cableTension"] <= 0 and t["buzz"] <= 0.12 and not t.get("atStop"):
            break
        tap(s, SMALL[-d])
        time.sleep(0.1)
    t = turret(s, side)
    assert not t["cableDamaged"], f"pulled the {SIDES[side]} cable too far"
    return t


def results(s):
    latest = {}
    for row in csv.DictReader(io.StringIO(s.file("biobuzz_results.csv"))):
        latest[row["key"]] = float(row["value"])
    return latest


def run(s, fix_cable=False):
    s.preset_config()
    s.init("3 Turret Servos")
    s.wait_text("Press START")
    s.start()
    for side in (0, 1):
        name = "Front" if side == 0 else "Back"
        s.wait_text(f"{name} turret: straight forward")
        tap(s, "square")
        time.sleep(0.6)
        sign = step_sign(s, side)
        nudge_to(s, side, 0, sign)
        tap(s, "cross")
        for target, title in ((90, "straight LEFT"), (-90, "straight RIGHT")):
            s.wait_text(f"{name} turret: {title}")
            t = nudge_to(s, side, target, sign)
            if t["cableTension"] > 0:
                assert fix_cable, f"{SIDES[side]} cable tight at {t['deg']:.0f} deg"
                # the student sees the cable pull (Turret view) and re-routes it, then carries on
                s.hand("turret", "fixCable", back=side == 1)
                nudge_to(s, side, target, sign)
            tap(s, "cross")
        for way, title in ((1, "as far LEFT"), (-1, "as far RIGHT")):
            s.wait_text(f"{name} turret: {title}")
            to_far_end(s, side, way, sign)
            tap(s, "cross")
    s.wait_text("Turret turn speed")
    time.sleep(0.6)
    s.hand("servos", "record", on=True)
    time.sleep(0.3)
    tap(s, "square")
    time.sleep(1.5)
    s.hand("servos", "record", on=False)
    clip = s.get("slowmo")
    nf, vf = frames_to_open(clip, "yaw.front")
    nb, vb = frames_to_open(clip, "yaw.back")
    assert vf[-1] > vf[0] + 150 and vb[-1] > vb[0] + 150, "both turrets should swing right -> left"
    n = max(nf, nb)
    for _ in range(n // 10):
        tap(s, "right_bumper")
    for _ in range(n % 10):
        tap(s, "dpad_up")
    s.wait_text(f"Frames: {n} ")
    tap(s, "cross")
    s.wait_text("PROGRAM 3 COMPLETE")
    print(f"  slow-mo: front {nf} frames, back {nb} frames")
    return check(s)


def check(s):
    got, exp = results(s), s.get("expected")
    bad = []
    keys = [side + k for side in SIDES for k in ("YawForward", "YawPerDeg", "YawMinDeg", "YawMaxDeg")] + ["yawDegPerS"]
    for key in keys:
        e = exp[key]
        ok = key in got and abs(got[key] - e["value"]) <= e["tolerance"] + 1e-6
        print(f"  {'ok ' if ok else 'BAD'} {key:16} {got.get(key)!s:10} expected {e['value']:.5f} +- {e['tolerance']:.5f}")
        if not ok:
            bad.append(f"{key}: {got.get(key)} vs {e['value']:.5f} +- {e['tolerance']:.5f} ({e['how']})")
    assert not bad, "\n".join(bad)
    return got


if __name__ == "__main__":
    seeds = [int(a) for a in sys.argv[1:]] or [1, 2, 3]
    for seed in seeds:
        with running_sim(seed=seed) as s:
            print("seed", seed)
            run(s)
            s.stop()
            s.wait(lambda st: st["ds"]["active"] == "4 Servo Positions" and st["ds"]["state"] == "INIT", 10, "program 4 auto-loaded")
    with running_sim("C-turret-tight-cable.json", seed=4) as s:
        print("tight cable")
        run(s, fix_cable=True)
    with running_sim("C-turret-slow-servo.json", seed=5) as s:
        print("slow back servo")
        got = run(s)
        exp = s.get("expected")
        assert got["yawDegPerS"] < 0.6 * 180 / 0.2, got["yawDegPerS"]
    print("PASS program 3")
