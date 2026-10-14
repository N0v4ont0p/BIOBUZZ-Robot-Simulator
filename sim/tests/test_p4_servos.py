"""Program 4 (Servo Positions), done the way a careful student does it, on the perfect robot.

The student only uses what they could see or hear: which way a servo moves, when it buzzes, where the ramp tip
is against the drawn outlines and the FLOWER, the gap between a held element and the flywheel, whether an element
rolls through a gate freely, and the slow-motion video of the gates. Then every recorded value must be inside the
good band the hidden robot allows.
"""
import csv
import io
import sys
import time

from harness import running_sim

TRIGGERS = ("left_trigger", "right_trigger")
BIG = {+1: "right_bumper", -1: "left_bumper"}
SMALL = {+1: "right_trigger", -1: "left_trigger"}


def tap(s, button):
    if button in TRIGGERS:
        s.hold(button, 1.0)
        time.sleep(0.08)
        s.hold(button, 0.0)
    else:
        s.hold(button, True)
        time.sleep(0.08)
        s.hold(button, False)
    time.sleep(0.12)


def servo(s, sid):
    return s.world()["servos"][sid]


def step_until(s, sid, button, done, limit=120, what=""):
    for _ in range(limit):
        st = servo(s, sid)
        if done(st):
            return st
        tap(s, button)
        time.sleep(0.05)
    raise AssertionError(f"{sid}: never reached {what}: {st}")


def start_step(s, sid, title):
    s.wait_text(title)
    tap(s, "square")
    time.sleep(0.4)


def direction(s, sid):
    """+1 when R1 moves the mechanism up / out / open (watch it move)."""
    before = servo(s, sid)["out"]
    tap(s, "right_bumper")
    time.sleep(0.2)
    after = servo(s, sid)["out"]
    if abs(after - before) < 0.5:
        # already against a stop that way: try the other way
        tap(s, "left_bumper")
        tap(s, "left_bumper")
        time.sleep(0.2)
        back = servo(s, sid)["out"]
        assert abs(back - after) > 0.5, f"{sid} does not move either way: {before} -> {after} -> {back}"
        return 1 if back < after else -1
    return 1 if after > before else -1


def to_stop(s, sid, toward, d):
    """Move toward one end until it buzzes, then back off until it is quiet, and a little more."""
    step_until(s, sid, BIG[toward * d], lambda st: st["stalled"], what="its stop")
    step_until(s, sid, SMALL[-toward * d], lambda st: not st["stalled"], limit=30, what="quiet")
    for _ in range(4):
        tap(s, SMALL[-toward * d])
    time.sleep(0.3)
    st = servo(s, sid)
    assert not st["stalled"] and st["heat"] < 0.3, st
    return st


def hood(s, sid, first):
    start_step(s, sid, first)
    d = direction(s, sid)
    to_stop(s, sid, -1, d)
    tap(s, "cross")
    s.wait_text("highest")
    to_stop(s, sid, +1, d)
    tap(s, "cross")


def ramp(s):
    sid = "ramp"
    start_step(s, sid, "Ramp: stowed")
    d = direction(s, sid)
    st = to_stop(s, sid, -1, d)
    assert st["inside"], st
    tap(s, "cross")
    s.wait_text("Ramp: deployed")
    step_until(s, sid, BIG[d], lambda st: st["tipY"] < 1.3 and st["tipX"] > 1, what="the FLOWER opening")
    st = step_until(s, sid, SMALL[d], lambda st: st["tipY"] <= 0.8, limit=40, what="low in the opening")
    assert st["atFlower"] and not st["stalled"], st
    tap(s, "cross")


def gate(s, side, title_closed, title_open):
    sid = ("gate.front", "gate.back")[side]
    start_step(s, sid, title_closed)
    d = direction(s, sid)
    step_until(s, sid, BIG[-d], lambda st: st["holds"], what="closed")
    s.hand("servos", "placeElement", gate=side)
    time.sleep(0.3)
    step_until(s, sid, BIG[-d], lambda st: st["clearanceIn"] < 0.55 or st["pressed"], limit=30, what="near the wheel")
    st = servo(s, sid)
    if st["pressed"] or st["clearanceIn"] < 0.15:
        st = step_until(s, sid, SMALL[d], lambda st: not st["pressed"] and st["clearanceIn"] >= 0.2, limit=60, what="clear")
    else:
        st = step_until(s, sid, SMALL[-d], lambda st: st["clearanceIn"] <= 0.4 or st["pressed"], limit=60, what="close")
        if st["pressed"]:
            st = step_until(s, sid, SMALL[d], lambda st: not st["pressed"] and st["clearanceIn"] >= 0.2, limit=20, what="clear")
    assert st["element"] and st["holds"] and not st["pressed"] and not st["stalled"], st
    tap(s, "cross")

    s.wait_text(title_open)
    step_until(s, sid, BIG[d], lambda st: st["passesFreely"], limit=40, what="open")
    tap(s, BIG[d])
    time.sleep(0.3)
    if servo(s, sid)["stalled"]:
        step_until(s, sid, SMALL[-d], lambda st: not st["stalled"], limit=20, what="quiet")
        for _ in range(4):
            tap(s, SMALL[-d])
    s.hand("servos", "placeElement", gate=side)
    st = s.wait(lambda x: not x["world"]["servos"][sid]["element"], 5, "element through the gate")["world"]["servos"][sid]
    assert "freely" in st["result"] and st["passesFreely"] and not st["stalled"], st
    tap(s, "cross")


def frames_to_open(clip, channel):
    k = clip["channels"].index(channel)
    v = [f[k] for f in clip["frames"]]
    start = next(i for i, x in enumerate(v) if abs(x - v[0]) > 0.02)
    end = next(i for i, x in enumerate(v) if abs(x - v[-1]) < 0.005)
    return end - start, v


def gate_travel(s):
    s.wait_text("Gate travel time")
    time.sleep(0.5)
    s.hand("servos", "record", on=True)
    time.sleep(0.3)
    tap(s, "square")
    time.sleep(0.8)
    s.hand("servos", "record", on=False)
    clip = s.get("slowmo")
    assert clip["fps"] == 240 and len(clip["frames"]) > 200, (clip["fps"], len(clip["frames"]))
    front, vf = frames_to_open(clip, "gate.front")
    back, vb = frames_to_open(clip, "gate.back")
    assert vf[-1] > vf[0] + 20 and vb[-1] > vb[0] + 20, "both gates should open (arm swings up)"
    n = max(front, back)
    for _ in range(n // 10):
        tap(s, "right_bumper")
    for _ in range(n % 10):
        tap(s, "dpad_up")
    s.wait_text(f"Frames: {n} ")
    tap(s, "cross")
    return front, back


def results(s):
    latest = {}
    for row in csv.DictReader(io.StringIO(s.file("biobuzz_results.csv"))):
        latest[row["key"]] = float(row["value"])
    return latest


def check(s):
    got, exp = results(s), s.get("expected")
    bad = []
    for key in ("frontHoodMin", "frontHoodMax", "backHoodMin", "backHoodMax", "rampStowed", "rampDeployed",
                "frontGateClosed", "frontGateOpen", "backGateClosed", "backGateOpen", "gateTravelS"):
        e = exp[key]
        slack = 0.0006 if key != "gateTravelS" else 0.0001
        ok = key in got and abs(got[key] - e["value"]) <= e["tolerance"] + slack
        print(f"  {'ok ' if ok else 'BAD'} {key:16} {got.get(key)!s:8} expected {e['value']:.4f} +- {e['tolerance']:.4f}")
        if not ok:
            bad.append(f"{key}: {got.get(key)} vs {e['value']:.4f} +- {e['tolerance']:.4f} ({e['how']})")
    assert not bad, "\n".join(bad)


def run(s):
    s.preset_config()
    s.init("4 Servo Positions")
    s.wait_text("Press START")
    s.start()
    hood(s, "hood.front", "Front hood: lowest")
    hood(s, "hood.back", "Back hood: lowest")
    ramp(s)
    gate(s, 0, "Front gate: closed", "Front gate: open")
    gate(s, 1, "Back gate: closed", "Back gate: open")
    front, back = gate_travel(s)
    s.wait_text("PROGRAM 4 COMPLETE")
    print(f"  slow-mo: front gate {front} frames, back gate {back} frames")
    check(s)
    s.stop()
    s.wait(lambda st: st["ds"]["active"] == "5 Drive Tuning" and st["ds"]["state"] == "INIT", 10, "program 5 auto-loaded")


if __name__ == "__main__":
    seeds = [int(a) for a in sys.argv[1:]] or [1, 2]
    for seed in seeds:
        with running_sim(seed=seed) as s:
            run(s)
        print(f"PASS program 4 (seed {seed})")
