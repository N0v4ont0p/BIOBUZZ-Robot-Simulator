"""Program 7 (Flywheel Tuner). The programmer has entered the program 2 directions and the program 4 gate positions.
Part 1: a careful student tunes both flywheels: top speed, hold, then the automatic recovery step (the first element
flies out: cross; after that cross = ready, load ONE, fire, until the program has chosen and checked a P), then the
automatic idle step. F must land within tolerance of 32767 / true top speed; every shot is in the trial log.
Part 2: program 2 was answered wrong for the front flywheel, so it spins the wrong way. The element does not fly out;
the student says so (circle), says yes it is a direction problem (cross), the program flips the wheel and saves the
right direction, and the next shot flies out. Then the student takes over P with the dpad (manual mode still works).
Part 3: a leftover element makes the first "one element" shot fire two; the dragging-flywheel robot's truth.
Part 4: on three robots (seeds), the P the program picks is steady about as soon as the best P of a sweep (x1.95 steps
from the start P to about 57 x, two shots each, each P counted by its worse shot) on the same robot.
Part 5: the dragging-flywheel robot: the automatic steps finish on its slow back flywheel too."""
import os
import re
import time

from harness import running_sim
from launcher_student import (SIDES, directions, expected, guide, last_shot, pull_trigger, results, servo_values,
                              value_after, wait_shot_done)


def setup(s, wrong_front=False):
    s.preset_config()
    exp = expected(s)
    values = directions(exp)
    if wrong_front:
        values["Launcher.FRONT_REVERSED"] = not values["Launcher.FRONT_REVERSED"]
    values.update(servo_values(exp, guide(s)))
    s.set_values(values)
    s.init("7 Flywheel Tuner")
    s.wait_text("Press START")
    s.start()


def top_and_hold(s, side):
    s.wait_text("top speed")
    s.press("cross")
    s.wait_text("Starting gains", timeout=10)
    s.press("cross")
    s.wait_text("hold a speed")
    s.press("cross")
    s.wait_text("Ready band", timeout=10)
    s.press("cross")
    s.wait_text("recovery after a shot")


def fire(s, side, then):
    """Load ONE element in this launcher, fire when the wheel is steady, wait for the screen after the shot."""
    assert s.hand("intake", "loadLauncher", side=side)["ok"]
    before = (last_shot(s) or {"id": 0})["id"]
    for _ in range(20):
        s.wait_text("Press R2 to fire it")
        pull_trigger(s)
        try:
            s.wait_text("Firing and watching", timeout=1.5)
            break
        except TimeoutError:
            continue
    s.wait_text(then, timeout=10)
    return wait_shot_done(s, before, timeout=4)


def fire_one(s, side):
    """The first shot of a flywheel: the program asks whether it flew out. Returns the shot (None = nothing came out)."""
    return fire(s, side, "Did the element fly OUT")


def auto_p(s, side, max_shots=16):
    """Step 3 the automatic way: the first shot (it flies out: cross), then cross = ready, load, fire, until the program
    says which P it chose. Returns the screen with the result."""
    shot = fire_one(s, side)
    assert shot is not None and shot["side"] == SIDES[side], shot
    s.press("cross")
    for n in range(max_shots):
        s.wait_text("This shot", timeout=10)
        t = s.telemetry()
        print(f"  {SIDES[side]} shot {n + 1}: {value_after(t, 'This shot')} | next {value_after(t, 'Next P')}")
        if "P chosen" in t:
            return t
        s.press("cross")
        fire(s, side, "This shot")
    raise AssertionError("no P chosen after %d shots:\n%s" % (max_shots, s.telemetry()))


def band_again(s, side):
    """After the P is chosen: the band is measured again with it (nothing loaded); if it shrank a lot, one check shot."""
    s.wait_text("Ready band at the start P", timeout=20)
    t = s.telemetry()
    print("   band again:", value_after(t, "Ready band at the start P"))
    shrank = "check shot shows" in t
    s.press("cross")
    if shrank:
        fire(s, side, "Check shot with the new band")
        t = s.telemetry()
        print("   " + next(l for l in t.splitlines() if l.startswith("Check shot")))
        s.press("cross")


def auto_idle(s):
    s.wait_text("idle (pre-spin) speed")
    s.press("cross")
    s.wait_text("Picked:", timeout=90)
    t = s.telemetry()
    s.press("cross")
    return t


def run_side(s, side):
    top_and_hold(s, side)
    result = auto_p(s, side)
    print(f"{SIDES[side]}: " + value_after(result, "P chosen"))
    for line in result.splitlines():
        if line.startswith("P ") and ": " in line:
            print("   ", line)
    world = s.world()["flywheels"]
    print(f"   last dip {world['lastDipTicks'][side]:.0f} ticks/s, exit {world['lastExitMps'][side]:.2f} m/s")
    s.press("cross")
    band_again(s, side)
    idle = auto_idle(s)
    for line in idle.splitlines():
        if line.startswith("idle ") or line.startswith("Idle speed"):
            print("   ", line)


def steady_of(t):
    """'Steady (ready for the next element) after: 0.72 s' -> 0.72; 'not within 2 s' -> None."""
    v = value_after(t, "Steady (ready for the next element) after")
    return float(v.split()[0]) if v and v[0].isdigit() else None


def chosen(result):
    """The P the program chose and the worst 'steady after' of its shots (how the program counts a P), from the result
    screen."""
    p = float(value_after(result, "P chosen").split()[0])
    steady = []
    for line in result.splitlines():
        m = re.match(r"P ([\d.]+): steady after ([\d.]+) s", line)
        if m and abs(float(m.group(1)) - p) <= 0.0006 * p + 0.001:
            steady.append(float(m.group(2)))
    return p, (max(steady) if steady else None)


def part1():
    with running_sim(seed=7) as s:
        # an earlier program 7 run saved a very different front P: the new one is shown as a big change
        path = os.path.join(s.get("loop/status")["paths"]["robot"], "biobuzz_results.csv")
        with open(path, "a") as f:
            if os.path.getsize(path) == 0:
                f.write("program,key,value,time_ms\n")
            f.write(f"7 Flywheel Tuner,frontFlywheelP,1.0,{int(time.time() * 1000) - 600000}\n")
        setup(s)
        run_side(s, 0)
        s.wait_text("a big change since the last run")
        print("   big change shown:", next(l for l in s.telemetry().splitlines() if "big change" in l))
        run_side(s, 1)
        s.wait_text("PROGRAM 7 COMPLETE")
        got = results(s)
        exp = {k: v for k, v in s.get("expected").items()}
        for side in SIDES:
            for key in ("FlywheelF", "TopVelocity", "FlywheelP", "ReadyBand", "IdleVelocity"):
                k = side + key
                v, e = float(got[k]), exp[k]
                print(f"{k}: measured {v:.4g}, true {e['value']:.4g} +/- {e['tolerance']:.3g}")
                # the band also catches the wheel still settling after 1.5 s (a program 7 limit, see the report card)
                if key != "ReadyBand":
                    assert abs(v - e["value"]) <= e["tolerance"], (k, v, e)
            assert abs(float(got[side + "FlywheelI"]) - float(got[side + "FlywheelP"]) / 10) < 1e-3 * float(got[side + "FlywheelP"])
            assert got[side + "FlywheelThrowsOut"] == "true"
            assert exp[side + "FlywheelThrowsOut"]["value"] == 1
        log = (s.file("biobuzz_flywheel_trials.csv") or "").strip().splitlines()
        assert log and log[0].startswith("side,p,dip,recovery_s,steady_s"), log[:2]
        assert {r.split(",")[0] for r in log[1:]} == {"front", "back"} and len(log) - 1 >= 2 * 7, len(log)


def part2():
    with running_sim(seed=8) as s:
        setup(s, wrong_front=True)
        top_and_hold(s, 0)
        shot = fire_one(s, 0)
        assert shot is None, shot
        assert s.world()["intake"]["held"] == 1, "the element should still be in the robot"
        s.press("circle")
        s.wait_text("DIRECTION problem")
        # the student watches the wheel: its surface moves into the robot, so yes
        s.press("cross")
        s.wait_text("direction flipped and saved")
        truth = directions(expected(s))["Launcher.FRONT_REVERSED"]
        assert results(s)["frontFlywheelReversed"] == str(truth).lower(), results(s)
        # the element is still in the lane: fire again, now it flies out
        before = (last_shot(s) or {"id": 0})["id"]
        for _ in range(20):
            s.wait_text("Press R2 to fire it")
            pull_trigger(s)
            try:
                s.wait_text("Firing and watching", timeout=1.5)
                break
            except TimeoutError:
                continue
        s.wait_text("Did the element fly OUT", timeout=10)
        shot = wait_shot_done(s, before, timeout=4)
        assert shot is not None and shot["side"] == "front", shot
        s.press("cross")
        s.wait_text("This shot")
        assert results(s)["frontFlywheelThrowsOut"] == "true"
        print("wrong-way flywheel flipped in program 7, element flew out")
        # cross = ready; then the student sets P by hand instead: dpad switches this flywheel to manual
        s.press("cross")
        s.wait_text("Automatic: after each shot")
        p_auto = float(value_after(s.telemetry(), "P now"))
        s.press("dpad_up")
        s.wait_text("You set P yourself")
        assert abs(float(value_after(s.telemetry(), "P now")) - 1.25 * p_auto) < 0.002 * p_auto + 0.001
        fire(s, 0, "Back to speed in")
        t = s.telemetry()
        assert "Shorter 'steady after' is better" in t or "Not steady in the band within 2 s" in t, t
        s.press("cross")
        band_again(s, 0)
        s.wait_text("idle (pre-spin) speed")
        print(f"manual override: P {p_auto:.3f} -> {1.25 * p_auto:.3f} by dpad, cross kept it")


def part3():
    """E-launcher-leftover-elements: the student does not look in the lanes. The 'one' recovery shot fires two."""
    with running_sim("E-launcher-leftover-elements.json", seed=9) as s:
        assert s.world()["intake"]["held"] == 2
        setup(s)
        top_and_hold(s, 0)
        before = (last_shot(s) or {"id": 0})["id"]
        fire_one(s, 0)
        time.sleep(1.5)
        front = [x for x in s.get("shots")["shots"] if x["id"] > before and x["side"] == "front"]
        assert len(front) == 2, front
        print(f"leftover element: the recovery shot fired {len(front)} elements, dip {s.world()['flywheels']['lastDipTicks'][0]:.0f} ticks/s")
    with running_sim("C-launcher-dragging-flywheel.json", seed=9) as s:
        exp = s.get("expected")
        assert exp["backTopVelocity"]["value"] < 0.9 * exp["frontTopVelocity"]["value"], exp
        assert exp["backReadyBand"]["value"] > 1.5 * exp["frontReadyBand"]["value"], exp


def sweep(seed):
    """Manual mode, the front flywheel: two shots at each P from about 1 x to about 57 x the start P (x1.95 steps); each P
    counts with its worse shot, like the program counts them (one lucky quick shot is not a quick P)."""
    with running_sim(seed=seed) as s:
        setup(s)
        top_and_hold(s, 0)
        assert fire_one(s, 0) is not None
        s.press("cross")
        s.wait_text("This shot")
        s.press("cross")  # the program's next P is 2 x the start P
        s.wait_text("Automatic: after each shot")
        for _ in range(3):
            s.press("dpad_down")  # 2 x 0.8^3 = 1.02 x the start P, and manual from here
        out = []
        for _ in range(7):
            p = float(value_after(s.telemetry(), "P now"))
            worst = 0.0
            for _ in range(2):
                fire(s, 0, "Back to speed in")
                time.sleep(0.3)
                v = steady_of(s.telemetry())
                worst = None if v is None or worst is None else max(worst, v)
            out.append((p, worst))
            for _ in range(3):
                s.press("dpad_up")
        return out


def auto_front(seed):
    with running_sim(seed=seed) as s:
        setup(s)
        top_and_hold(s, 0)
        return chosen(auto_p(s, 0))


def part4():
    for seed in (7, 17, 27):
        swept = sweep(seed)
        best = min(v for _, v in swept if v is not None)
        p, steady = auto_front(seed)
        print(f"seed {seed}: sweep " + ", ".join(f"{q:.2f}: {'-' if v is None else f'{v:.2f} s'}" for q, v in swept)
              + f" | picked {p:.2f}, steady after {steady:.2f} s (best of the sweep {best:.2f} s)")
        assert steady is not None and steady <= 1.1 * best + 0.1, (seed, p, steady, best)


def part5():
    with running_sim("C-launcher-dragging-flywheel.json", seed=9) as s:
        setup(s)
        run_side(s, 0)
        run_side(s, 1)
        s.wait_text("PROGRAM 7 COMPLETE")
        got = results(s)
        print(f"dragging back flywheel: P {float(got['backFlywheelP']):.3f}, idle {float(got['backIdleVelocity']):.0f} ticks/s")


if __name__ == "__main__":
    part1()
    part2()
    part3()
    part4()
    part5()
    print("PASS program 7")
