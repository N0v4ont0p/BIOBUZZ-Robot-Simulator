"""TeleOp pre-aims at the stop point. Sticks let go while the robot still moves: the aim uses where Foresight's braking
model says it will stop. The screen then shows "last stop: predicted X in off". It must be well below how far the
robot rolled after the release (what an aim at the release pose would have missed by), for forward, strafe and a
diagonal run, and the real stop must be a few inches from where the sticks were released."""
import re
import time

from harness import running_sim
from launcher_student import start_corner, tuned_robot


def last_stop(s):
    m = re.search(r"last stop: predicted ([\d.]+) in, ([\d.]+) deg off", s.telemetry())
    return (float(m.group(1)), float(m.group(2))) if m else None


def run_and_release(s, forward, left, seconds=1.2):
    s.hold("left_stick_y", -forward)
    s.hold("left_stick_x", -left)
    time.sleep(seconds)
    d0 = s.world()["drive"]
    s.hold("left_stick_y", 0.0)
    s.hold("left_stick_x", 0.0)
    # the note of an earlier run stays on screen, so read it only after this run has come to rest
    for _ in range(80):
        time.sleep(0.1)
        if s.world()["drive"]["speed"] < 0.3:
            break
    time.sleep(0.4)
    got = last_stop(s)
    time.sleep(0.3)
    d1 = s.world()["drive"]
    return got, ((d1["x"] - d0["x"]) ** 2 + (d1["y"] - d0["y"]) ** 2) ** 0.5, d0["speed"]


if __name__ == "__main__":
    with running_sim(seed=41) as s:
        s.preset_config()
        values = tuned_robot(s)
        s.set_values(values)
        start_corner(s, values)
        s.init("BIOBUZZ TeleOp")
        s.wait_text("Alliance")
        s.press("cross")
        s.wait_text("Turret zero: SET")
        s.start()
        time.sleep(0.5)
        s.hand("drive", "place", x=40, y=40, headingDeg=0, careful=True)
        time.sleep(1)
        # a fresh stop note each run: wait for the previous one to be replaced by a different text or none
        for name, fwd, lft in (("forward", 0.8, 0.0), ("strafe", 0.0, 0.8), ("diagonal", 0.6, 0.6)):
            s.hand("drive", "place", x=30, y=30, headingDeg=0, careful=True)
            time.sleep(1)
            got, rolled, speed = run_and_release(s, fwd, lft)
            assert got, (name, s.telemetry())
            print(f"{name}: released at {speed:.0f} in/s, rolled {rolled:.1f} in, prediction off by {got[0]:.1f} in, {got[1]:.1f} deg")
            assert rolled > 2, (name, rolled)
            assert got[0] <= max(1.5, 0.5 * rolled), (name, got, rolled)
    print("PASS TeleOp pre-aim at the stop point")
