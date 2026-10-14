"""The optional turret index switch (F-turret-index-switch): the turrets start 7 deg crooked. TeleOp INIT first
says to turn them left through forward; turning right through forward does nothing (only a left turn counts),
then a slow left turn through it says INDEXED, and from then on the code's turret angle matches the real one
to a fraction of a degree, instead of the 7 deg a by-eye zero would have kept."""
import re
import time

from harness import running_sim
from launcher_student import tuned_robot
from test_p3_yaw import turn_to, turret


def reading(s):
    m = re.search(r"front turret at (-?[\d.]+) deg", s.telemetry())
    return float(m.group(1)) if m else None


if __name__ == "__main__":
    with running_sim("F-turret-index-switch.json", seed=3) as s:
        s.preset_config()
        values = tuned_robot(s)
        assert "yawIndexDeg" in values, values
        s.set_values(values)
        assert abs(turret(s)["frontDeg"] - 7) < 1, turret(s)
        s.init("BIOBUZZ TeleOp")
        s.wait_text("turn the turrets slowly LEFT")
        turn_to(s, -8)
        time.sleep(0.5)
        assert "Turret zero: INDEXED" not in s.telemetry(), "a right turn must not index:\n" + s.telemetry()
        # what the screen asks: slowly left through forward (about 3 deg/s)
        for _ in range(200):
            if "Turret zero: INDEXED" in s.telemetry():
                break
            s.hand("turret", "turn", deg=0.3)
            time.sleep(0.1)
        s.hand("turret", "release")
        s.wait_text("Turret zero: INDEXED")
        time.sleep(0.5)
        t = turret(s)
        # the encoder is on the motor: where the gears rest inside their play is not seen, so "right" means within
        # half the play (the aim is off by at most that much either way)
        got, real, half = reading(s), t["frontDeg"], t["coach"]["playFrontDeg"] / 2
        print(f"after indexing: code {got:.2f} deg, real {real:.2f} deg (half the gear play {half:.2f})")
        assert abs(got - real) <= half + 0.15, (got, real, half)
        s.stop()
    print("PASS turret index switch")
