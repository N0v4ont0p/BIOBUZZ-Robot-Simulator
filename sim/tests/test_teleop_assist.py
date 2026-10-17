"""TeleOp accuracy helpers on a tuned robot whose flywheels were tuned on a fuller battery (1 V more):
1. the hub's flywheel gains are the tuned ones times tuning voltage / battery now;
2. sideways to the CELL, the turrets lock on by themselves (DECODE lock-on) and the robot does not turn on its own;
3. no shots on the move: R1 held while driving does not fire ("Stop to fire"); let go of the stick and it fires."""
import math
import re
import time

from harness import running_sim
from launcher_student import SIDES, last_shot, push_to, spot, start_corner, tuned_robot
from test_teleop_fire import shot_table


def flywheel_port(s, side):
    for hub in s.world()["hubs"]:
        for m in hub["motors"]:
            if m["part"] == "flywheel." + side:
                return m
    raise AssertionError("no flywheel " + side)


if __name__ == "__main__":
    with running_sim(seed=21) as s:
        s.preset_config()
        values = tuned_robot(s)
        battery = s.state()["ds"]["battery"]
        for side in SIDES:
            values[side + "FlywheelVoltage"] = round(battery + 1.0, 3)
        distances = (40, 50, 60, 70, 80, 90)
        values.update(shot_table(s, 0, distances))
        values.update(shot_table(s, 1, distances))
        s.set_values(values)
        start_corner(s, values)
        s.init("BIOBUZZ TeleOp")
        s.wait_text("Alliance")
        time.sleep(0.5)
        s.press("circle")
        for _ in range(3):
            time.sleep(0.5)
            s.press("options")
            if "start position set" in s.telemetry():
                break
        s.start()
        cell = (values["redAudienceCellX"], values["redAudienceCellY"])
        push_to(s, *spot(cell, (0, 0), 60, (values["frontPivotX"], values["frontPivotY"])))
        s.press("left_bumper")
        time.sleep(1.5)

        # 1. battery
        now = s.state()["ds"]["battery"]
        for side in SIDES:
            f = flywheel_port(s, side)["velocityPidf"][3]
            want = values[side + "FlywheelF"] * values[side + "FlywheelVoltage"] / now
            print(f"{side} F on the hub {f:.3f}, tuned {values[side + 'FlywheelF']:.3f} x {values[side + 'FlywheelVoltage']:.2f} / {now:.2f} V = {want:.3f}")
            assert abs(f - want) <= 0.015 * want, (side, f, want)

        # 2. lock-on (DECODE): turned 80 deg away by hand, the turrets follow the CELL on their own and lock;
        # the robot itself does not turn (no auto-face), even with R1 held
        s.hand("drive", "push", turnDeg=80, seconds=1.0)
        time.sleep(1.6)
        h0 = s.world()["drive"]["headingDeg"]
        s.hold("right_bumper", True)
        s.wait(lambda st: re.search(r"Locked\s*:\s*true", "\n".join(st["ds"]["telemetry"])), 6, "turret locked on")
        time.sleep(1.0)
        h1 = s.world()["drive"]["headingDeg"]
        print(f"heading before {h0:.1f}, after {h1:.1f}")
        assert abs((h1 - h0 + 540) % 360 - 180) < 3, (h0, h1)
        assert "Facing the CELL" not in s.telemetry()
        s.hold("right_bumper", False)
        time.sleep(0.5)

        # 3. driving with R1 held: no shot until the robot stops
        assert s.hand("intake", "loadLauncher", side=0)["ok"]
        before = (last_shot(s) or {"id": 0})["id"]
        # field-centric for red: stick up drives away from the red wall, into open field
        s.hold("left_stick_y", -0.25)
        time.sleep(0.4)
        s.hold("right_bumper", True)
        time.sleep(0.8)
        assert "Stop to fire" in s.telemetry(), s.telemetry()
        assert not [x for x in s.get("shots")["shots"] if x["id"] > before], "fired while moving without a lead"
        s.hold("left_stick_y", 0.0)
        s.wait(lambda st: st["world"]["intake"]["held"] == 0, 8, "fired once stopped")
        s.hold("right_bumper", False)
    print("PASS TeleOp assist")
