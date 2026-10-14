"""Program 1 (Check Devices), done the way a careful student does it, on the perfect robot."""
import time

from harness import running_sim


def run(s):
    s.preset_config()
    s.init("1 Check Devices")
    s.wait_text("Press START")
    s.start()
    s.wait_text("Every device name matches.")
    s.press("cross")
    s.wait_text("Charged enough.")
    s.press("cross")
    # one part at a time, holding it still afterwards so nothing coasts into the next part's check
    parts = [("left-front wheel", "drive", "turnWheel", "releaseWheel", {"wheel": 0}, 90),
             ("left-back wheel", "drive", "turnWheel", "releaseWheel", {"wheel": 1}, 90),
             ("right-front wheel", "drive", "turnWheel", "releaseWheel", {"wheel": 2}, 90),
             ("right-back wheel", "drive", "turnWheel", "releaseWheel", {"wheel": 3}, 90),
             ("intake", "intake", "turn", "release", {}, 90),
             ("turrets (yaw)", "turret", "turn", "release", {}, 10),
             ("front flywheel", "flywheels", "turn", "release", {"wheel": 0}, 720),
             ("back flywheel", "flywheels", "turn", "release", {"wheel": 1}, 720)]
    for name, part, turn, release, args, deg in parts:
        s.wait_text("Encoders: " + name)
        s.hand(part, turn, deg=deg, **args)
        s.wait_text("OK: its encoder counts, and no other one did.", timeout=10)
        time.sleep(1.0)
        s.hand(part, release, **args)
        s.press("cross")
    s.wait_text("STEP 4 of 4")
    s.hand("robot", "onFloor")
    s.hand("drive", "push", forward=12, seconds=1)
    time.sleep(1.3)
    s.hand("drive", "push", left=12, seconds=1)
    s.wait_text("OK   strafe pod counts", timeout=5)
    s.wait_text("OK   forward pod counts", timeout=5)
    s.press("cross")
    s.wait_text("PROGRAM 1 COMPLETE")
    s.stop()
    st = s.wait(lambda st: st["ds"]["active"] == "2 Motor Directions" and st["ds"]["state"] == "INIT", 10, "program 2 auto-loaded")
    results = s.file("biobuzz_results.csv")
    assert "devicesFound,14/14" in results, results
    assert "encodersNotCounting,none" in results, results
    assert "encodersCrossed,none" in results, results


if __name__ == "__main__":
    with running_sim(seed=1) as s:
        run(s)
    print("PASS program 1")
