"""Program 7's optional shot-speed check (after programs 8 and 9). The ready band per speed is saved only when every
check passes; Launcher.ready() then uses it. Part 1: a robot whose flywheel wobble grows with speed: on the front (the wider speed
range) the band at the longest shot speed is clearly wider, both ends are saved, and TeleOp still fires and scores
with them. Part 2: a normal
robot: the two bands are within 25 % of each other, so one band is enough (both ends saved as program 7's band, nothing
changes). Part 3: program 7's band is wrong (three times too wide): it does not fit between the two, nothing is saved.
Every part also re-picks the idle speed against both shot speeds."""
import time

from harness import running_sim
from launcher_student import SIDES, pull_trigger, tuned_robot, value_after
from test_teleop_fire import shot_table

DISTANCES = (40, 50, 60, 70, 80, 90, 100)


def robot(s, band_scale=1.0):
    s.preset_config()
    values = tuned_robot(s)
    values.update(shot_table(s, 0, DISTANCES))
    values.update(shot_table(s, 1, DISTANCES))
    for side in SIDES:
        values[side + "ReadyBand"] = round(values[side + "ReadyBand"] * band_scale)
    s.set_values(values)
    return values


def check(s):
    """Runs the shot-speed check for both flywheels; returns the result line of each."""
    s.init("7 Flywheel Tuner: shot-speed check")
    s.wait_text("Press START")
    s.start()
    verdicts = []
    for side in (0, 1):
        for k in range(4):
            s.wait_text(f"Shot {k + 1} of 4", timeout=30)
            # a shot the program did not count ("hardly slowed": nothing reached the wheel): load ONE and fire again,
            # as the screen says
            for attempt in range(3):
                if s.world()["intake"]["held"] == 0:
                    assert s.hand("intake", "loadLauncher", side=side)["ok"]
                for _ in range(30):
                    pull_trigger(s)
                    try:
                        s.wait_text("Firing and watching", timeout=1.0)
                        break
                    except TimeoutError:
                        continue
                s.wait(lambda st: "Firing and watching" not in "\n".join(st["ds"]["telemetry"]), 10, "the shot to end")
                if f"Shot {k + 1} of 4" not in s.telemetry() or "hardly slowed" not in s.telemetry():
                    break
        s.wait_text("Idle", timeout=10)
        end = time.time() + 90
        while time.time() < end and "SAVED" not in s.telemetry() and "NOT saved" not in s.telemetry() and "one band is enough" not in s.telemetry():
            time.sleep(0.5)
        t = s.telemetry()
        line = next(l for l in t.splitlines() if "saved" in l.lower() or "one band is enough" in l)
        measured = next((l for l in t.splitlines() if l.startswith("Band at")), "")
        print(f"  {SIDES[side]}: {line}  [{measured}]")
        verdicts.append(line)
        s.press("cross")
    s.wait_text("Shot-speed check done")
    return verdicts


def results(s):
    return s.get("loop/status")["results"]


def part1():
    with running_sim("G-flywheel-speed-wobble.json", seed=21) as s:
        values = robot(s)
        verdicts = check(s)
        got = results(s)
        for side, v in zip(SIDES, verdicts):
            assert "NOT saved" not in v, v
            lo, hi = float(got[side + "ReadyBandLow"]), float(got[side + "ReadyBandHigh"])
            # the front's table spans 1350 to 1660 ticks/s, where this wobble grows clearly; the back's only 1200 to
            # 1480, where one band can be enough
            if side == "front":
                assert "Band per speed SAVED" in v and hi > 1.25 * lo, (side, lo, hi, v)
            assert min(lo, hi) >= float(values[side + "ReadyBand"]), (side, lo, hi)
            assert float(got[side + "ReadyBandLowSpeed"]) < float(got[side + "ReadyBandHighSpeed"])
            assert float(got[side + "IdleVelocity"]) <= 0.65 * float(got[side + "ReadyBandLowSpeed"]) + 10
        s.stop()
        import test_teleop_fire
        extra = {k: float(got[k]) for k in got if "ReadyBand" in k and ("Low" in k or "High" in k)}
        test_teleop_fire.run(s, extra=extra)
        print("TeleOp fires and scores with the band per speed")


def part2():
    with running_sim(seed=7) as s:
        values = robot(s)
        verdicts = check(s)
        got = results(s)
        for side, v in zip(SIDES, verdicts):
            assert "NOT saved" not in v, ("a normal robot passes every check", v)
            lo, hi = float(got[side + "ReadyBandLow"]), float(got[side + "ReadyBandHigh"])
            if "one band is enough" in v:
                assert lo == hi == float(values[side + "ReadyBand"]), (lo, hi, values[side + "ReadyBand"])
            else:
                assert abs(hi - lo) > 0.25 * min(lo, hi), (lo, hi)
            # never narrower than program 7's band
            assert min(lo, hi) >= float(values[side + "ReadyBand"]), (lo, hi, values[side + "ReadyBand"])


def part3():
    with running_sim(seed=8) as s:
        robot(s, band_scale=3.0)
        verdicts = check(s)
        got = results(s)
        for side, v in zip(SIDES, verdicts):
            assert "NOT saved" in v and "does not fit" in v, v
            assert side + "ReadyBandLow" not in got, got


if __name__ == "__main__":
    part1()
    part2()
    part3()
    print("PASS program 7 shot-speed check")
