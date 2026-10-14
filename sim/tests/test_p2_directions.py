"""Program 2 (Motor Directions) done by a careful student: an element at the intake mouth to see if it goes in, then the
flywheel surface direction at the contact point. Several seeds (random motor directions), then the front flywheel with
its power wires swapped (C-launcher-reversed-flywheel.json), which must stop with "encoder counts the opposite way"."""
import time

from harness import running_sim
from launcher_student import expected, results


def watch_intake(s):
    """Hold R2 with a POLLEN at the mouth: did it go in?"""
    s.hand("intake", "loadIntake", type="POLLEN")
    before = s.world()["intake"]
    s.hold("right_trigger", 1.0)
    time.sleep(1.0)
    after = s.world()["intake"]
    s.hold("right_trigger", 0.0)
    time.sleep(0.3)
    went_in = after["held"] > before["held"]
    spat = after["spatOut"] > before["spatOut"]
    assert went_in != spat, (before, after)
    return went_in


def watch_flywheel(s, i):
    """Hold R2 and look at the wheel surface at the contact point: the way that throws out?"""
    s.hold("right_trigger", 1.0)
    time.sleep(1.2)
    rpm = s.world()["flywheels"]["rpmOut"][i]
    s.hold("right_trigger", 0.0)
    time.sleep(0.3)
    assert abs(rpm) > 300, rpm
    return rpm > 0


def answer(s, yes):
    s.press("cross" if yes else "circle")


def run(s):
    s.preset_config()
    s.init("2 Motor Directions")
    s.wait_text("Press START")
    s.start()
    s.wait_text("Intake direction")
    answer(s, watch_intake(s))
    s.wait_text("Front flywheel direction")
    answer(s, watch_flywheel(s, 0))
    exp = expected(s)
    if exp["frontFlywheelEncoderOpposite"] == 1:
        s.wait_text("encoder counts the opposite way")
        return exp
    s.wait_text("Back flywheel direction")
    answer(s, watch_flywheel(s, 1))
    if exp["backFlywheelEncoderOpposite"] == 1:
        s.wait_text("encoder counts the opposite way")
        return exp
    s.wait_text("PROGRAM 2 COMPLETE")
    s.hand("intake", "unload")
    return exp


def check(s, exp):
    got = results(s)
    for key in ["intakeReversed", "frontFlywheelReversed", "backFlywheelReversed",
                "frontFlywheelEncoderOpposite", "backFlywheelEncoderOpposite"]:
        if key not in got:
            continue
        assert (got[key] == "true") == (exp[key] == 1), (key, got[key], exp[key])
    return got


if __name__ == "__main__":
    seen = set()
    for seed in (1, 2, 3, 4):
        with running_sim(seed=seed) as s:
            exp = run(s)
            got = check(s, exp)
            assert len(got) == 5, got
            seen.add((got["intakeReversed"], got["frontFlywheelReversed"], got["backFlywheelReversed"]))
            print("seed", seed, got)
    assert len(seen) > 1, "directions should differ between seeds"
    with running_sim("C-launcher-reversed-flywheel.json", seed=5) as s:
        exp = run(s)
        assert exp["frontFlywheelEncoderOpposite"] == 1
        got = check(s, exp)
        assert got["frontFlywheelEncoderOpposite"] == "true", got
        print("reversed flywheel caught:", got)
    print("PASS program 2")
