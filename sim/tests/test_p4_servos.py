"""Program 4 (Servo Positions), done the way a careful student does it, on the perfect robot.

The student only uses what they could see or hear: which way a servo moves, when it buzzes (and how loud), the gap
between a held element and the flywheel, whether an element rolls through a gate freely, whether the folded ramp creeps
down, and slow-motion videos of the gates and the ramp. Then every recorded value must be inside the good band the
hidden robot allows.
"""
import csv
import re
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


def gate_closed(s, side, sid, title):
    start_step(s, sid, title)
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
    s.hand("servos", "removeElement", gate=side)
    tap(s, "cross")
    return d


def gate_open(s, side, sid, title, d):
    s.wait_text(title)
    tap(s, "square")
    time.sleep(0.4)
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


def loud(s):
    sv = s.world()["servos"]
    return max(sv["gate.back"]["buzz"], sv["gate.back2"]["buzz"])


def match_second(s, title):
    """Back gate servo 2 while servo 1 holds the arm, by ear: step it the way the buzz gets quieter until it stops, keep
    going until it starts again on the other side, come back halfway."""
    s.wait_text(title)
    tap(s, "square")
    time.sleep(0.5)
    way = +1
    if loud(s) > 0.12:
        # far off it buzzes flat out and one step changes nothing: try a few big steps one way, else the other way
        before, way = loud(s), None
        for _ in range(8):
            tap(s, BIG[+1])
            time.sleep(0.2)
            if loud(s) < before - 0.05:
                way = +1
                break
        if way is None:
            way = -1
            for _ in range(60):
                tap(s, BIG[-1])
                time.sleep(0.15)
                if loud(s) < before - 0.05:
                    break
        for _ in range(400):
            if loud(s) <= 0.12:
                break
            tap(s, SMALL[way] if loud(s) < 0.6 else BIG[way])
            time.sleep(0.05)
        assert loud(s) <= 0.12, s.world()["servos"]["gate.back2"]
    # the quiet window: walk to its far side, then back half the way
    n = 0
    for _ in range(200):
        tap(s, SMALL[way])
        time.sleep(0.05)
        if loud(s) > 0.12:
            break
        n += 1
    for _ in range((n + 1) // 2 + 1):
        tap(s, SMALL[-way])
    time.sleep(0.4)
    assert loud(s) <= 0.12, s.world()["servos"]["gate.back2"]
    tap(s, "cross")


def both_check(s):
    s.wait_text("Back gate: both servos together")
    for _ in range(4):
        tap(s, "square")
        time.sleep(0.6)
        sv = s.world()["servos"]
        assert sv["gate.back"]["buzz"] <= 0.12 and sv["gate.back2"]["buzz"] <= 0.12, (sv["gate.back"], sv["gate.back2"])
    tap(s, "cross")


def frames_to_open(clip, channel, moving=0.02):
    """Frames from the first clear movement (more than `moving` degrees from one frame to the next for the ramp, whose
    slow creep is not the run; any change from the first frame for a gate) to arrival at the end value."""
    k = clip["channels"].index(channel)
    v = [f[k] for f in clip["frames"]]
    if channel == "ramp":
        start = next(i for i in range(1, len(v)) if abs(v[i] - v[i - 1]) > moving) - 1
    else:
        start = next(i for i, x in enumerate(v) if abs(x - v[0]) > moving)
    end = next(i for i, x in enumerate(v) if abs(x - v[-1]) < 0.005)
    return end - start, v


def enter_frames(s, n):
    """Type the count with R1 (+10) and dpad (+/- 1), reading the screen back like a student checking the number."""
    for _ in range(n // 10):
        tap(s, "right_bumper")
    for _ in range(40):
        m = re.search(r"Frames: (\d+) ", s.telemetry())
        have = int(m.group(1)) if m else n
        if have == n:
            break
        tap(s, "right_bumper" if n - have >= 10 else "left_bumper" if have - n >= 10 else "dpad_up" if n > have else "dpad_down")
    s.wait_text(f"Frames: {n} ")
    tap(s, "cross")


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
    counts = {}
    for ch in ("gate.front", "gate.back", "gate.back2"):
        counts[ch], v = frames_to_open(clip, ch)
        assert v[-1] > v[0] + 20, f"{ch} should open (arm swings up)"
    enter_frames(s, max(counts.values()))
    return counts


def ramp(s):
    return s.world()["servos"]["ramp"]


def creeps(s, watch):
    """Fold the ramp up by hand, let go, watch: does it creep down?"""
    s.hand("servos", "stowRamp")
    time.sleep(watch)
    return ramp(s)["share"] > 0.004 * watch


def ramp_run(s, title, out):
    """Hold square while filming; let go once the ramp is all the way (a student reacts in about 0.1 s)."""
    s.wait_text(title)
    if out:
        s.hand("servos", "stowRamp")
    time.sleep(0.4)
    s.hand("servos", "record", on=True)
    time.sleep(0.3)
    s.hold("square", True)
    s.wait(lambda x: (x["world"]["servos"]["ramp"]["share"] >= 0.999) if out else (x["world"]["servos"]["ramp"]["share"] <= 0.0),
           6, "ramp all the way")
    time.sleep(0.1)
    s.hold("square", False)
    time.sleep(0.4)
    s.hand("servos", "record", on=False)
    clip = s.get("slowmo")
    n, v = frames_to_open(clip, "ramp", moving=0.15)
    assert abs(v[-1] - v[0]) > 90, "the ramp should swing all the way"
    enter_frames(s, n)
    return n


def results(s):
    latest = {}
    for row in csv.DictReader(io.StringIO(s.file("biobuzz_results.csv"))):
        latest[row["key"]] = float(row["value"])
    return latest


KEYS = ("frontGateClosed", "frontGateOpen", "backGateClosed", "backGate2Closed", "backGateOpen", "backGate2Open", "gateTravelS",
        "rampOutS", "rampInS")


def check(s):
    got, exp = results(s), s.get("expected")
    bad = []
    for key in KEYS:
        e = exp[key]
        slack = 0.0001 if key.endswith("S") else 0.0006
        ok = key in got and abs(got[key] - e["value"]) <= e["tolerance"] + slack
        print(f"  {'ok ' if ok else 'BAD'} {key:16} {got.get(key)!s:8} expected {e['value']:.4f} +- {e['tolerance']:.4f}")
        if not ok:
            bad.append(f"{key}: {got.get(key)} vs {e['value']:.4f} +- {e['tolerance']:.4f} ({e['how']})")
    assert not bad, "\n".join(bad)
    return got


def run(s):
    s.preset_config()
    # program 2 ran before: the ramp's direction is known (the ramp runs here)
    s.set_values({"Ramp.RAMP_REVERSED": s.get("expected")["rampReversed"]["value"] == 1})
    s.init("4 Servo Positions")
    s.wait_text("Press START")
    s.start()
    d = gate_closed(s, 0, "gate.front", "Front gate: closed")
    gate_open(s, 0, "gate.front", "Front gate: open", d)
    d = gate_closed(s, 1, "gate.back", "Back gate servo 1: closed")
    match_second(s, "Back gate servo 2: closed")
    gate_open(s, 1, "gate.back", "Back gate servo 1: open", d)
    match_second(s, "Back gate servo 2: open")
    both_check(s)
    counts = gate_travel(s)
    out = ramp_run(s, "Ramp: time out", True)
    back = ramp_run(s, "Ramp: time in", False)
    s.wait_text("PROGRAM 4 COMPLETE")
    print(f"  slow-mo: gates {counts}, ramp out {out} frames, in {back} frames")
    got = check(s)
    # the ramp rests up with the servos stopped
    assert not creeps(s, 4.0), ramp(s)
    s.stop()
    s.wait(lambda st: st["ds"]["active"] == "5 Drive Tuning" and st["ds"]["state"] == "INIT", 10, "program 5 auto-loaded")
    return got


if __name__ == "__main__":
    seeds = [int(a) for a in sys.argv[1:]] or [1, 2]
    for seed in seeds:
        with running_sim(seed=seed) as s:
            run(s)
        print(f"PASS program 4 (seed {seed})")
