"""Program 1 on scenario B (wiring day): swapped left drive motor cables, unplugged front flywheel encoder, low battery.
A careful student turns one part at a time; the program must point at each problem."""
import time

from harness import running_sim


def run(s):
    s.preset_config()
    s.init("1 Check Devices")
    s.start()
    s.wait_text("Every device name matches.")
    s.press("cross")
    s.wait_text("Below 12.5 V")
    s.press("cross")
    # left-front wheel: its cables are in port 1 (named lb), so turning it counts on lb
    s.wait_text("Encoders: left-front wheel")
    s.hand("drive", "turnWheel", wheel=0, deg=90)
    s.wait_text("MIXED UP: lf and lb", timeout=10)
    time.sleep(1)
    s.hand("drive", "releaseWheel", wheel=0)
    s.press("cross")
    s.wait_text("Encoders: left-back wheel")
    s.hand("drive", "turnWheel", wheel=1, deg=90)
    s.wait_text("MIXED UP: lb and lf", timeout=10)
    time.sleep(1)
    s.hand("drive", "releaseWheel", wheel=1)
    s.press("cross")
    parts = [("right-front wheel", "drive", "turnWheel", "releaseWheel", {"wheel": 2}, 90),
             ("right-back wheel", "drive", "turnWheel", "releaseWheel", {"wheel": 3}, 90),
             ("intake", "intake", "turn", "release", {}, 90),
             ("turrets (yaw)", "turret", "turn", "release", {}, 10)]
    for name, part, turn, release, args, deg in parts:
        s.wait_text("Encoders: " + name)
        s.hand(part, turn, deg=deg, **args)
        s.wait_text("OK: its encoder counts, and no other one did.", timeout=10)
        time.sleep(1)
        s.hand(part, release, **args)
        s.press("cross")
    # front flywheel: encoder cable unplugged, it never counts
    s.wait_text("Encoders: front flywheel")
    s.hand("flywheels", "turn", wheel=0, deg=720)
    time.sleep(2)
    assert "OK: its encoder counts" not in s.telemetry(), s.telemetry()
    s.hand("flywheels", "release", wheel=0)
    s.press("cross")
    s.wait_text("Encoders: back flywheel")
    s.hand("flywheels", "turn", wheel=1, deg=720)
    s.wait_text("OK: its encoder counts, and no other one did.", timeout=10)
    time.sleep(1)
    s.hand("flywheels", "release", wheel=1)
    s.press("cross")
    s.wait_text("STEP 4 of 4")
    res = s.file("biobuzz_results.csv")
    assert "encodersNotCounting,lf lb fs" in res, res
    assert "lf-lb" in res and "lb-lf" in res, res


if __name__ == "__main__":
    with running_sim("B-wiring-day.json", seed=3) as s:
        run(s)
    print("PASS program 1 catches the wiring-day faults")
