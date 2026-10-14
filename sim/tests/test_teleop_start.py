"""TeleOp after the setup programs, the way a team practises: program 8 drives the robot out to a shooting spot and is
stopped there, the robot is put back in the red start corner, TeleOp is INIT-ed with no auto. TeleOp must not take
the shot tuner's pose: with no buttons pressed it is red, in the start corner. Then field-centric (DECODE): stick up
drives away from the red alliance wall (+x) whichever way the robot faces. A TeleOp restart resumes in place."""
import re
import time

from harness import running_sim
from launcher_student import start_corner, tuned_robot


def pose(s):
    m = re.search(r"Pose\s*:\s*(-?[\d.]+), (-?[\d.]+), (-?[\d.]+) deg", s.telemetry())
    assert m, s.telemetry()
    return tuple(float(v) for v in m.groups())


def drive_up(s, seconds=0.8):
    before = s.world()["drive"]
    s.hold("left_stick_y", -0.6)
    time.sleep(seconds)
    s.hold("left_stick_y", 0.0)
    time.sleep(0.8)
    after = s.world()["drive"]
    return after["x"] - before["x"], after["y"] - before["y"]


if __name__ == "__main__":
    with running_sim(seed=31) as s:
        s.preset_config()
        values = tuned_robot(s)
        s.set_values(values)
        start_corner(s, values)
        s.init("8 Front Shot Tuner")
        s.wait_text("Alliance")
        s.press("circle")
        s.press("options")
        s.press("cross")
        s.wait_text("Ready.")
        s.start()
        s.wait_text("Driving itself", timeout=10)
        time.sleep(3)
        print("shot tuner left the robot at", s.world()["drive"])
        s.stop()
        time.sleep(1)

        start_corner(s, values)
        s.init("BIOBUZZ TeleOp")
        s.wait_text("Alliance")
        time.sleep(1)
        assert "RED" in s.telemetry(), s.telemetry()
        x, y, h = pose(s)
        print(f"TeleOp INIT pose {x:.1f}, {y:.1f}, {h:.1f} deg")
        assert abs(x - values["centerToBack"]) < 1 and abs(y - values["centerToRight"]) < 1 and abs(h) < 2, (x, y, h)
        s.start()
        time.sleep(0.5)

        dx, dy = drive_up(s)
        print(f"facing +x, stick up: moved {dx:.1f}, {dy:.1f}")
        assert dx > 6 and abs(dy) < 0.3 * dx, (dx, dy)
        s.hand("drive", "push", turnDeg=90, seconds=1.0)
        time.sleep(1.5)
        dx, dy = drive_up(s)
        print(f"facing +y, stick up: moved {dx:.1f}, {dy:.1f}")
        assert dx > 6 and abs(dy) < 0.3 * dx, (dx, dy)

        # square in a corner means the heading snaps to the nearest 90 deg: face +x again, near the start corner
        s.hand("drive", "push", turnDeg=-90, seconds=1.0)
        time.sleep(1.5)
        x, y, h = pose(s)
        s.hand("drive", "push", forward=30 - x, left=14 - y, seconds=2.5)
        time.sleep(3.2)
        # options (DECODE's start button) = reseed square in the nearest corner. Pushed 20 in along the wall: back to 9.0
        s.hand("drive", "push", forward=20, seconds=1.5)
        time.sleep(2.2)
        x, y, h = pose(s)
        assert x > values["centerToBack"] + 10, (x, y, h)
        s.press("options")
        time.sleep(0.6)
        x, y, h = pose(s)
        print(f"after options: {x:.1f}, {y:.1f}, {h:.1f}")
        assert abs(x - values["centerToBack"]) < 0.5 and abs(y - values["centerToRight"]) < 0.5 and abs(h) < 1, (x, y, h)
        # a little crooked next to the corner: still snaps square into it
        s.hand("drive", "push", forward=3, seconds=1.0)
        time.sleep(1.5)
        s.hand("drive", "push", turnDeg=4, seconds=1.0)
        time.sleep(1.5)
        x, y, h = pose(s)
        assert abs(x - values["centerToBack"]) > 1.5 and abs(h) > 2, (x, y, h)
        s.press("options")
        time.sleep(0.6)
        x, y, h = pose(s)
        print(f"after options again: {x:.1f}, {y:.1f}, {h:.1f}")
        assert abs(x - values["centerToBack"]) < 0.5 and abs(y - values["centerToRight"]) < 0.5 and abs(h) < 1, (x, y, h)
        s.hand("drive", "push", forward=15, left=10, seconds=1.5)
        time.sleep(2.2)

        # a TeleOp restarted right away (mid-match disconnect) resumes where the robot is, not in the corner
        end = pose(s)
        s.stop()
        time.sleep(1)
        s.init("BIOBUZZ TeleOp")
        s.wait_text("Alliance")
        time.sleep(1)
        x, y, h = pose(s)
        print(f"restart pose {x:.1f}, {y:.1f}, {h:.1f}; TeleOp had {end[0]:.1f}, {end[1]:.1f}, {end[2]:.1f}")
        assert "restart" in s.telemetry(), s.telemetry()
        assert abs(x - end[0]) < 2 and abs(y - end[1]) < 2, (x, y, end)
    print("PASS TeleOp start pose and field-centric")
