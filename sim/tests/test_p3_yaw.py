"""Program 3 (Yaw Tuner) on the hidden turret: a careful student lines the barrels up by eye (here: from the world
state), finds the stops, lets the automatic parts run, and every recorded number must land near the truth.
Then two broken robots: a tight cable (limits refused) and a sticky turret (binding found)."""
import sys
import time

from harness import running_sim
from simctl import strip_html


def turret(s):
    return s.world()["turret"]


def turn_to(s, target, back=False, tol=0.3, timeout=30):
    key = "backDeg" if back else "frontDeg"
    end = time.time() + timeout
    while time.time() < end:
        err = target - turret(s)[key]
        if abs(err) < tol:
            return
        # a hand turns it smoothly, not in one jump
        s.hand("turret", "turn", deg=max(-10, min(10, err)), back=back)
        time.sleep(0.1 if abs(err) > 10 else 0.2)
    raise TimeoutError(f"could not turn the {'back' if back else 'front'} turret to {target}")


# turn slowly until it stops: a hard stop, or the cable just going tight (then ease off until it is just slack)
def turn_to_stop(s, direction):
    side = "left" if direction > 0 else "right"
    for _ in range(200):
        t = turret(s)
        if t.get("atStop") == side or (t["cableTension"] > 0 and t["cableSide"] == side):
            break
        s.hand("turret", "turn", deg=3 * direction)
        time.sleep(0.1)
    for _ in range(20):
        if turret(s)["cableTension"] <= 0:
            break
        s.hand("turret", "turn", deg=-0.5 * direction)
        time.sleep(0.15)
    t = turret(s)
    assert not t["cableDamaged"], "pulled the cable too far"
    return t


def part_a(s):
    turn_to(s, 0)
    s.hand("turret", "release")
    s.init("3 Yaw Tuner")
    s.wait_text("Press START")
    s.start()
    s.wait_text("STEP 1 of 14")
    for step, target, back in ((2, 90, False), (3, -90, False), (4, 90, True), (5, -90, True)):
        turn_to(s, target, back=back)
        s.press("cross")
        s.wait_text(f"STEP {step} of 14")
    s.hand("turret", "release")
    turn_to_stop(s, 1)
    s.press("cross")
    s.wait_text("STEP 6 of 14")
    turn_to_stop(s, -1)
    s.press("cross")


def measure_play(s):
    """E-6 by eye: rock the front turret with the yaw motor held and read the swing on the degree scale."""
    s.hand("turret", "wiggle", back=False, seconds=2)
    seen = []
    end = time.time() + 2.0
    while time.time() < end:
        seen.append(turret(s)["frontDeg"])
        time.sleep(0.03)
    time.sleep(0.3)
    return round(max(seen) - min(seen), 1)


def finish_part_a(s):
    s.wait_text("STEP 7 of 14")
    turn_to(s, 0)
    s.hand("turret", "release")
    s.press("cross")
    s.wait_text("STEP 8 of 14")
    play = measure_play(s)
    for _ in range(round(play / 0.1)):
        s.press("dpad_up", hold=0.05)
    s.wait_text(f"Play: {play:.1f} deg")
    s.press("cross")
    s.wait_text("STEP 9 of 14")
    s.press("cross")


def results(text):
    out = {}
    for line in text.strip().splitlines()[1:]:
        program, key, value, _ = line.split(",")
        if program == "3 Yaw Tuner":
            out[key] = value
    return out


def screen(st):
    return "\n".join(strip_html(l) for l in st["ds"]["telemetry"])


# The YawTuner sometimes rejects every speed run (its steady-speed check is tighter than its own speed noise, see
# the report); the screen then says to STOP and run it again, so the student does.
def perfect(seed, scenario="A-perfect-robot.json"):
    with running_sim(scenario, seed=seed) as s:
        s.preset_config()
        for attempt in range(3):
            part_a(s)
            finish_part_a(s)
            st = s.wait(lambda st: "STEP 12 of 14" in screen(st) or "STOPPED:" in screen(st), 150, "the homing step of program 3")
            if "STEP 12 of 14" in screen(st):
                # the last part: the turrets are driven into their stops to measure homing
                s.press("cross")
                st = s.wait(lambda st: "PROGRAM 3 COMPLETE" in screen(st) or "STOPPED:" in screen(st), 240, "the end of program 3")
            if "PROGRAM 3 COMPLETE" in screen(st):
                break
            assert "never reached a steady speed" in screen(st), screen(st)
            print(f"  attempt {attempt + 1}: YawTuner rejected every speed run, running it again")
            s.stop()
        got = results(s.file("biobuzz_results.csv"))
        expected = s.get("expected")
        bad = []
        for key in ("yawTicksPerDegFront", "yawTicksPerDegBack", "yawMaxDeg", "yawMinDeg", "yawPowerSign", "yawKs",
                    "yawKv", "yawTuningVoltage", "yawKp", "yawReactionTimeS", "yawPlayDeg"):
            e = expected[key]
            v = float(got[key])
            ok = abs(v - e["value"]) <= e["tolerance"]
            print(f"  {key:20s} measured {v:10.5g}  truth {e['value']:10.5g} +/- {e['tolerance']:.3g}  {'ok' if ok else 'OFF'}")
            if not ok:
                bad.append(key)
        print("  ", {k: got[k] for k in ("yawSettleS", "yawPastTargetDeg", "yawSpeedPoints")})
        assert not bad, f"seed {seed}: off truth: {bad}"
        homing(s, got, expected)
        s.stop()
        s.wait(lambda st: st["ds"]["active"] == "4 Servo Positions" and st["ds"]["state"] == "INIT", 10, "program 4 auto-loaded")
        runtime_homing(s, got)


def homing(s, got, expected):
    """Part D: both stops repeat, their angles are the true stops (the hard stops of the hidden turret), the speeds and
    the times for this travel are reported."""
    print("   homing:", {k: got.get(k) for k in ("yawHomeLeftDeg", "yawHomeRightDeg", "yawHomeFastDegS", "yawHomeCreepDegS",
                                                  "yawHomeCrossDegS", "yawHomeSpreadDeg", "yawHomeTypicalS", "yawHomeWorstS")})
    left, right = float(got["yawHomeLeftDeg"]), float(got["yawHomeRightDeg"])
    # the soft limits are 10 deg inside the stops found by hand; the hidden turret's stops are the truth
    left_truth = expected["yawMaxDeg"]["value"] + 10
    right_truth = expected["yawMinDeg"]["value"] - 10
    # the angles are measured in the tuner's own frame (the by-eye forward, the gear play): only roughly the wall angles
    assert abs(left - left_truth) <= 2.5 and abs(right - right_truth) <= 2.5, (left, left_truth, right, right_truth)
    assert float(got["yawHomeSpreadDeg"]) <= 0.5, got["yawHomeSpreadDeg"]
    assert float(got["yawHomeFastDegS"]) >= 60, got["yawHomeFastDegS"]
    # both stops on every homing: typical about 4 s, worst (turret starting at the far end) about 5 s
    assert float(got["yawHomeTypicalS"]) <= 4.6 and float(got["yawHomeWorstS"]) <= 5.6, (got["yawHomeTypicalS"], got["yawHomeWorstS"])


def runtime_homing(s, got):
    """The numbers program 3 measured (with the gear play of this hidden turret) put into the code: after a power-up
    with the turrets turned, options homes them, and the zero is right to a degree."""
    from launcher_student import tuned_robot
    import test_homing
    s.stop()
    values = tuned_robot(s)
    for key in ("yawTicksPerDegFront", "yawTicksPerDegBack", "yawMinDeg", "yawMaxDeg", "yawPowerSign", "yawKs", "yawKv",
                "yawTuningVoltage", "yawKp", "yawPlayDeg", "yawHomeLeftDeg", "yawHomeRightDeg", "yawHomeFastDegS",
                "yawHomeCreepDegS", "yawHomeCrossDegS"):
        values[key] = float(got[key])
    s.set_values(values)
    # against the true angle: program 3's "straight forward" is set by eye (a steady offset the yaw trim takes up later),
    # the turret rests up to half the gear play either side of the motor, and the motor settles within about 0.3 deg of
    # its target. Homing itself is checked in home_from_twist: both stops where the measured span says.
    errors = []
    for twist in (0, 100, -120):
        errors.append(test_homing.home_from_twist(s, values, twist, tolerance=1.5)[1])
        s.stop()
    spread = max(errors) - min(errors)
    print(f"  homing spread over the twists: {spread:.2f} deg (gear play {values['yawPlayDeg']:.2f})")
    # half the play either side of the motor, and the motor settling up to about 0.4 deg either side of its target
    assert spread <= values["yawPlayDeg"] + 2 * 0.4 + 0.05, (errors, values["yawPlayDeg"])


def tight_cable(seed):
    """Stopping at 'cable tight' 50 deg out: program 3 refuses a stop short of straight left; re-route, go on."""
    with running_sim("C-turret-tight-cable.json", seed=seed) as s:
        s.preset_config()
        turn_to(s, 0)
        s.hand("turret", "release")
        s.init("3 Yaw Tuner")
        s.wait_text("Press START")
        s.start()
        s.wait_text("STEP 1 of 14")
        for step, target, back in ((2, 90, False), (3, -90, False), (4, 90, True), (5, -90, True)):
            turn_to(s, target, back=back)
            s.press("cross")
            s.wait_text(f"STEP {step} of 14")
        s.hand("turret", "release")
        turn_to_stop(s, 1)
        s.press("cross")
        s.wait_text("must be past straight left")
        s.hand("turret", "fixCable")
        turn_to_stop(s, 1)
        s.press("cross")
        s.wait_text("STEP 6 of 14")
        turn_to_stop(s, -1)
        s.press("cross")
        s.wait_text("STEP 7 of 14")


def sticky(seed):
    with running_sim("C-turret-sticky.json", seed=seed) as s:
        s.preset_config()
        part_a(s)
        finish_part_a(s)
        # caught at the direction step (only the gear play moves) or at kS / the moves (check for binding)
        st = s.wait(lambda st: "STOPPED:" in screen(st), 90, "program 3 to stop")
        assert "did not move at 10% power" in screen(st) or "check for binding" in screen(st), screen(st)


if __name__ == "__main__":
    seeds = [int(a) for a in sys.argv[1:]] or [1, 11]
    for seed in seeds:
        perfect(seed)
        print(f"PASS program 3, perfect robot, seed {seed}")
    tight_cable(seeds[0])
    print("PASS program 3, tight cable refused")
    sticky(seeds[0])
    print("PASS program 3, sticky turret found")
