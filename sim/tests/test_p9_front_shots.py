"""Program 9 (Front Shot Tuner: NECTAR, the backup launcher), semi-automatic, on a robot whose programs 1-7 and CAD /
field sheets are in the code (true values): a short session (4 points, then share twice to finish anyway), skipping
its feed check with L1 (the robot drives on). The student starts in the red corner and presses START; the robot drives
itself to each spot. At each spot the student sets the speed (square / circle; no hood, the launch angle is fixed),
loads ONE element, fires (R2), watches the shot and honestly answers in (cross) / not in (triangle). 4 in a row save
the point and the robot drives itself to the next spot. The speed a coach would suggest comes from /api/shots/solve.
At the end every saved point must still score on this robot (<side>ShotPointsMakeable). run() is shared with program
8 (test_p8_back_shots.py, the full session)."""
import os
import re
import time

from harness import running_sim
from launcher_student import pull_trigger, start_corner, straighten_turrets, tuned_robot, value_after, wait_shot_done, last_shot

SPEED_STEP = 20
# points each launcher aims for (ShotTuner.MIN_POINTS_BACK / MIN_POINTS_FRONT)
MIN_POINTS = (6, 10)


def read_settings(s):
    return float(value_after(s.telemetry(), "Flywheel set / now").split("/")[0])


def nudge(s, up, down, steps):
    for _ in range(abs(steps)):
        s.press(up if steps > 0 else down, hold=0.05)


def set_shot(s, speed):
    nudge(s, "circle", "square", round((speed - read_settings(s)) / SPEED_STEP))
    time.sleep(0.2)
    return read_settings(s)


def at_spot(s, timeout=20):
    """Waits for the robot to drive itself to the spot and the settings to load; returns the distance."""
    end = time.time() + timeout
    while time.time() < end:
        t = s.telemetry()
        v = value_after(t, "Distance to CELL")
        if "Load and fire" in t and v and v.endswith(" in") and "waiting" not in value_after(t, "Flywheel set / now"):
            return float(v[:-3])
        time.sleep(0.2)
    raise AssertionError("never reached a spot:\n" + s.telemetry())


def points_saved(s):
    t = s.telemetry()
    v = value_after(t, "Points saved")
    assert v is not None, t
    return int(v.split()[0])


def shoot(s, side):
    """Load one element in this launcher, fire, watch where it goes."""
    if s.world()["intake"]["held"] == 0:
        assert s.hand("intake", "loadLauncher", side=side)["ok"]
    before = (last_shot(s) or {"id": 0})["id"]
    pull_trigger(s)
    s.wait_text("Did it go in?", timeout=15)
    shot = wait_shot_done(s, before, timeout=6)
    assert shot is not None, "nothing flew:\n" + s.telemetry()
    s.press("cross" if shot["result"] == "made" else "triangle")
    return shot


FEEDS = []
SKIP_FEED_CHECK = []


def feed_check(s, side):
    """After a save the program may ask for a feed check: load ONE and fire until it picks a feed (nothing to answer).
    With SKIP_FEED_CHECK set, L1 instead: the check is skipped and the robot must drive on by itself."""
    if SKIP_FEED_CHECK:
        assert "stays at this spot" in s.telemetry(), s.telemetry()
        start = s.world()["drive"]
        s.press("left_bumper")
        s.wait_text("feed check skipped", timeout=5)
        s.wait_text("Driving itself", timeout=5)
        time.sleep(2)
        d = s.world()["drive"]
        moved = ((d["x"] - start["x"]) ** 2 + (d["y"] - start["y"]) ** 2) ** 0.5
        assert moved > 2, ("after skipping the feed check the robot drives on", moved)
        assert "Feed: 0.50" in s.telemetry(), s.telemetry()
        print(f"  feed check skipped with L1: drove on ({moved:.1f} in so far), feed back to 0.50")
        FEEDS.append(None)
        return
    end = time.time() + 240
    while "FEED CHECK:" in s.telemetry() and time.time() < end:
        shot_no = re.search(r"FEED CHECK: .*?Shot (\d+) of", s.telemetry()).group(1)
        if s.world()["intake"]["held"] == 0:
            assert s.hand("intake", "loadLauncher", side=side)["ok"]
        before = (last_shot(s) or {"id": 0})["id"]
        pull_trigger(s)
        wait_shot_done(s, before, timeout=10)
        # wait for the screen to move on (next shot number, or the check done) before firing again
        s.wait(lambda st: f"Shot {shot_no} of" not in "\n".join(st["ds"]["telemetry"]) or "no element reached" in "\n".join(st["ds"]["telemetry"]),
               8, "the feed check to take the shot")
        time.sleep(0.2)
    t = s.telemetry()
    m = re.search(r"FEED ([\d.]+) at (\d+) in \((.*)\)", t)
    assert m, "the feed check never finished:\n" + t
    FEEDS.append(float(m.group(1)))
    print(f"  feed check at {m.group(2)} in: {m.group(1)} ({m.group(3)})")


def save_point(s, side):
    d = at_spot(s)
    sol = s.get(f"shots/solve?side={side}&distance={d}")
    assert sol["reachable"], sol
    speed = set_shot(s, sol["speed"])
    start = points_saved(s)
    misses = 0
    for n in range(16):
        shot = shoot(s, side)
        time.sleep(0.3)
        if points_saved(s) > start:
            if "FEED CHECK:" in s.telemetry():
                feed_check(s, side)
            print(f"point {start + 1}: {d:.1f} in (off the spot {value_after(s.telemetry(), 'Off the spot')}), speed {speed:.0f}, {n + 1} shots")
            return True
        if shot["result"] == "made":
            misses = 0
            continue
        misses += 1
        print(f"  miss at {d:.1f} in: {shot['detail']}")
        if misses >= 2 and shot["detail"] in ("short", "long"):
            s.press("circle" if shot["detail"] == "short" else "square")
            speed = read_settings(s)
            misses = 0
    print(f"  gave up on {d:.1f} in")
    s.press("left_bumper")
    return False


def buttons_check(s, side):
    """Each button moves the shot the way the guide says: dpad left = aims further left, circle = faster wheel (and the
    opposite buttons put it back). dpad up / down do nothing: no hood, the launch angle stays the build's."""
    at_spot(s)
    base = shoot(s, side)
    nudge(s, "dpad_up", "dpad_down", 8)
    up = shoot(s, side)
    nudge(s, "dpad_up", "dpad_down", -8)
    assert abs(up["launchDeg"] - base["launchDeg"]) < 3, ("no hood: dpad up must not change the launch angle", base["launchDeg"], up["launchDeg"])
    nudge(s, "dpad_left", "dpad_right", 4)
    left = shoot(s, side)
    nudge(s, "dpad_left", "dpad_right", -4)
    assert left["yawDeg"] > base["yawDeg"] + 1, ("dpad left must aim further left", base["yawDeg"], left["yawDeg"])
    nudge(s, "circle", "square", 10)
    fast = shoot(s, side)
    nudge(s, "circle", "square", -10)
    assert fast["wheelTicks"] > base["wheelTicks"] + 100, ("circle must spin the wheel faster", base["wheelTicks"], fast["wheelTicks"])
    print(f"buttons: launch angle {base['launchDeg']:.1f} / {up['launchDeg']:.1f} deg (fixed), dpad left {base['yawDeg']:.1f} -> "
          f"{left['yawDeg']:.1f} deg aim, circle {base['wheelTicks']:.0f} -> {fast['wheelTicks']:.0f} ticks/s")


CAD = ("centerToFront", "centerToBack", "centerToLeft", "centerToRight", "frontPivotX", "frontPivotY", "backPivotX",
       "backPivotY", "redFarCellX", "redFarCellY", "redAudienceCellX", "redAudienceCellY")
# results keys for the settings that live in code
RESULT_KEYS = {"Intake.REVERSED": "intakeReversed", "Launcher.FRONT_REVERSED": "frontFlywheelReversed",
               "Launcher.BACK_REVERSED": "backFlywheelReversed"}


def install_live(s, values):
    """The real setup: only the CAD sheet is built in (build 1); everything programs 2-7 and Pedro measured is only
    in the robot's results file, saved after that build."""
    s.set_values({k: v for k, v in values.items() if k in CAD})
    path = os.path.join(s.get("loop/status")["paths"]["robot"], "biobuzz_results.csv")
    now = int(time.time() * 1000)
    with open(path, "a") as f:
        if os.path.getsize(path) == 0:
            f.write("program,key,value,time_ms\n")
        for k, v in values.items():
            if k in CAD:
                continue
            v = str(v).lower() if isinstance(v, bool) else v
            f.write(f"earlier program,{RESULT_KEYS.get(k, k)},{v},{now}\n")


def run(s, side=0, points=10, live=False):
    name = ["front", "back"][side]
    # POLLEN first (team 2026-10-17): the back launcher is program 8, the front program 9
    number = 9 if side == 0 else 8
    s.preset_config()
    values = tuned_robot(s)
    if live:
        install_live(s, values)
    else:
        s.set_values(values)
    builds = s.get("loop/status")["builtAt"]
    start_corner(s, values)
    s.init(f"{number} {name.capitalize()} Shot Tuner")
    s.wait_text("Alliance")
    s.press("circle")
    s.press("options")
    # turn the turrets straight forward by hand (the sim starts them up to 6 deg off): the servos take over at START
    straighten_turrets(s)
    s.wait_text("Ready.")
    if live:
        assert "from the robot's results file" in s.telemetry(), s.telemetry()
    s.start()
    s.wait_text("Points saved", timeout=10)
    buttons_check(s, side)
    spots = None
    for _ in range(points + 3):
        if points_saved(s) >= points:
            break
        save_point(s, side)
        spots = value_after(s.telemetry(), "Spot")
        if spots and spots.startswith("all planned"):
            break
    saved = points_saved(s)
    assert saved >= min(points, 5), s.telemetry()
    # the yaw trim is saved with every point (a STOP before share keeps it); this student never trims
    trim = s.get("loop/status")["results"].get(name + "YawTrimDeg")
    assert trim is not None and abs(float(trim)) < 0.01, trim
    s.press("share")
    if saved < MIN_POINTS[side]:
        s.wait_text("press share again")
        s.press("share")
    s.wait_text(f"PROGRAM {number} COMPLETE")
    m = s.get("expected")[name + "ShotPointsMakeable"]
    print(f"{name}ShotPointsMakeable", m["value"], "-", m["how"])
    assert m["value"] >= 0.8, s.get(f"shots/makeable?side={side}")
    points_file = s.file(f"biobuzz_{name}_points.csv")
    assert points_file and len([l for l in points_file.splitlines() if l[:1].isdigit()]) >= saved, points_file
    made = [x for x in s.get("shots")["shots"] if x["result"] == "made"]
    print(f"{len(made)} shots made in the session")
    best = s.get("expected")[name + "BestFeed"]["value"]
    print(f"feed checks picked {FEEDS}; this launcher's true best feed {best:.2f}")
    assert FEEDS, "a feed check runs in every session"
    # the sim's feed effect is mostly below the hub's speed step: a check may keep the feed in use (0.5 here); when it
    # changes the feed, the new one must be near the steadiest
    assert all(f is None or f == 0.5 or abs(f - best) <= 0.15 for f in FEEDS), ("a changed feed lands within 0.15 of the steadiest", FEEDS, best)
    FEEDS.clear()
    assert s.get("loop/status")["builtAt"] == builds, "no build should happen during the session"


if __name__ == "__main__":
    with running_sim(seed=12) as s:
        # this session skips its feed check with L1: the robot must drive on by itself
        SKIP_FEED_CHECK.append(True)
        run(s, side=0, points=4)
    print("PASS program 9")
