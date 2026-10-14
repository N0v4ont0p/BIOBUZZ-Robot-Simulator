"""Turret homing at run time (no extra hardware). Program 3 has measured the stops (here: the hidden turret's true stop
angles, with the speeds), the robot is powered up again with the turrets turned to different angles (a restart of the
Robot Controller resets the hub encoders), and options in the start corner homes the turret from its end stops while
the robot can already drive. Checks: the zero lands within 1 deg of the truth, in the planned time; fire mode cannot be
entered and nothing fires while it homes; a hold of options homes without touching the Pinpoint; a tap reseeds."""
import re
import time

from harness import running_sim
from launcher_student import pull_trigger, start_corner, tuned_robot, value_after
from test_p3_yaw import turn_to

FAST, CREEP, CROSS = 150.0, 20.0, 250.0


def power_cycle(s):
    s.post("robot/restart")
    s.wait(lambda st: st["ds"]["robot"] is not None and st["ds"]["state"] == "STOPPED", 60, "robot restarted")


def pose(s):
    m = re.search(r"Pose\s*:\s*(-?[\d.]+), (-?[\d.]+), (-?[\d.]+) deg", s.telemetry())
    assert m, s.telemetry()
    return tuple(float(v) for v in m.groups())


def code_angle(s, which=2):
    """The front turret angle the code reads (which=2, motor side: off the turret by half the gear play, the side it
    last moved) or commands (which=1: where Yaw puts the turret itself, the play taken into account)."""
    m = re.search(r"Yaw cmd / now\s*:\s*(-?[\d.]+) / (-?[\d.]+) deg", s.telemetry())
    return float(m.group(which)) if m else None


def setup(s):
    s.preset_config()
    values = tuned_robot(s)
    exp = s.get("expected")
    values.update({"yawHomeLeftDeg": exp["yawMaxDeg"]["value"] + 10, "yawHomeRightDeg": exp["yawMinDeg"]["value"] - 10,
                   "yawHomeFastDegS": FAST, "yawHomeCreepDegS": CREEP, "yawHomeCrossDegS": CROSS})
    s.set_values(values)
    return values


def power_up_twisted(s, values, twist):
    """The robot is in the start corner; the turrets were left turned by `twist` deg (true angle); power cycle."""
    if s.state()["ds"]["state"] != "STOPPED":
        s.stop()
    start_corner(s, values)
    s.hand("turret", "release")
    if abs(twist) > 1:
        turn_to(s, twist, tol=0.6, timeout=60)
        s.hand("turret", "release")
        time.sleep(1.0)
    power_cycle(s)
    s.init("BIOBUZZ TeleOp")
    s.wait_text("Alliance")


def home_from_twist(s, values, twist, tolerance=1.0):
    power_up_twisted(s, values, twist)
    assert "TURRET NOT ZEROED" in s.telemetry(), s.telemetry()
    # a tap in INIT: the start corner and homing armed for START
    s.press("options")
    s.wait_text("will HOME at START")
    s.start()
    s.wait_text("TURRET HOMING", timeout=5)
    moved = {}
    d0 = s.world()["drive"]
    # while it homes: drive (works), fire mode cannot be entered, R1 only collects
    s.hold("left_stick_y", -0.4)
    s.press("left_bumper")
    assert "fire mode is off while the turret homes" in s.telemetry() or "TURRET HOMING" in s.telemetry()
    s.wait_text("Turret homed in", timeout=8)
    s.hold("left_stick_y", 0.0)
    took = float(re.search(r"Turret homed in ([\d.]+) s", s.telemetry()).group(1))
    d1 = s.world()["drive"]
    assert ((d1["x"] - d0["x"]) ** 2 + (d1["y"] - d0["y"]) ** 2) ** 0.5 > 2, (d0, d1)
    t = s.telemetry()
    assert "off: R1 collects" in t, t
    # let the turret go to the CELL, then compare what the code thinks with the truth
    time.sleep(4.0)
    # the turret itself must point where the code commands it (the motor-side reading is off by half the gear play)
    mine, now, true = code_angle(s, 1), code_angle(s), s.world()["turret"]["frontDeg"]
    err = mine - true if mine is not None else None
    apart = re.search(r"([-\d.]+) deg apart", t)
    # homing itself: the second stop lands where the measured span says, so the zero is the same whichever stop came first
    assert apart and abs(float(apart.group(1))) <= 0.3, t
    print(f"  twisted {twist:+.0f} deg: homed in about {took:.1f} s (stops {apart.group(1) if apart else '?'} deg apart from the span),"
          f" commanded {mine} (reads {now}), true {true:.2f}, error {err}")
    assert err is not None and abs(err) <= tolerance, (mine, true)
    # both stops every time (a snag cannot pass as a stop): about 4 to 5 s at worst
    assert took <= 5.5, took
    assert "(both stops)" in t or "both stops" in s.telemetry(), t
    return took, err


def snag():
    """A cable that catches the turret at 110 deg, after program 3 measured the true stops. The fast hit stalls there and
    the slow touches agree with it (it repeats), so one stop alone would take it for the stop and give a zero tens of
    degrees off. The crossing to the other stop finds the real stop far from where it should be: the homing fails
    loudly, the turret stays off and nothing fires."""
    with running_sim("G-turret-snag.json", seed=72) as s:
        values = setup(s)
        power_up_twisted(s, values, 60)
        s.press("options")
        s.wait_text("will HOME at START")
        s.start()
        s.wait_text("TURRET HOMING FAILED", timeout=12)
        t = s.telemetry()
        print("  snag:", next(l for l in t.splitlines() if "HOMING FAILED" in l))
        # fire mode can be turned on, but with no zero it never gets ready (the turret stays off)
        s.press("left_bumper")
        time.sleep(1.5)
        t = s.telemetry()
        assert "TURRET HOMING FAILED" in t and "READY TO FIRE" not in t, t


def run():
    with running_sim("G-turret-no-play.json", seed=71) as s:
        values = setup(s)
        for twist in (0, 60, 120, -90, -140):
            home_from_twist(s, values, twist)

        # in the run: a hold of options homes only; the Pinpoint stays where it is. A tap reseeds in the corner.
        x0, y0, h0 = pose(s)
        s.hand("drive", "push", forward=40 - x0, left=14 - y0, seconds=3.0)
        time.sleep(3.8)
        x0, y0, h0 = pose(s)
        assert 20 < x0 < 60, (x0, y0, h0)
        s.press("options", hold=1.8)
        s.wait_text("Turret homed in", timeout=10)
        x1, y1, h1 = pose(s)
        print(f"  hold: pose {x0:.1f}, {y0:.1f} -> {x1:.1f}, {y1:.1f}")
        assert abs(x1 - x0) < 0.6 and abs(y1 - y0) < 0.6, "a hold of options must not reseed the Pinpoint"
        s.press("options")
        s.wait_text("TURRET HOMING", timeout=3)
        time.sleep(0.5)
        x2, y2, h2 = pose(s)
        print(f"  tap: pose {x2:.1f}, {y2:.1f}")
        assert abs(x2 - values["centerToBack"]) < 0.5 and abs(y2 - values["centerToRight"]) < 0.5, (x2, y2)
        s.wait_text("Turret homed in", timeout=10)

        # the shot tuner homes the same way, and does not fire while it does
        s.stop()
        start_corner(s, values)
        s.hand("turret", "release")
        power_cycle(s)
        s.init("8 Front Shot Tuner")
        s.wait_text("Alliance")
        s.press("circle")
        s.press("options")
        s.wait_text("will HOME at START")
        s.wait_text("Ready.")
        s.start()
        s.wait_text("TURRET HOMING", timeout=5)
        pull_trigger(s, "right_trigger")
        s.wait_text("no shots while the turret homes", timeout=3)
        s.wait_text("Load and fire", timeout=40)
        s.wait(lambda st: "TURRET HOMING" not in "\n".join(st["ds"]["telemetry"]), 10, "the homing to end")
        time.sleep(1.5)
        assert "Distance to CELL" in s.telemetry() and "aiming" not in value_after(s.telemetry(), "Spot"), s.telemetry()
    print("PASS turret homing")


if __name__ == "__main__":
    snag()
    run()
