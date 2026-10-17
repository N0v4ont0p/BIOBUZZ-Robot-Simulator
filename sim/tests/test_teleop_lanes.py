"""BIOBUZZ TeleOp, each launcher on its own (team 2026-10-17: two turrets, each with its own servo; the main intake
motor feeds both lanes, the POLLEN feeder only the back one), on a fully tuned robot:
1. First move at START: the turrets were left crooked by hand (40 deg off). Nothing reads where a servo-turned turret is,
   so the aim may only say Locked once the turret has really arrived.
2. One turret out of reach: the front turret's travel cut to +/-30 deg (as a short cable would), the CELL 45 deg to the
   left. Holding R1 fires the back launcher only; the front gate stays shut and the NECTAR stays in.
3. Per-lane pause: fire starts with both launchers ready, then a hand stops the front flywheel before the feed starts.
   The main motor (it pushes both lanes) waits, but the POLLEN feeder still feeds the back launcher: POLLEN flies, NECTAR
   stays until the front wheel is let go and back at speed."""
import re
import time

from harness import running_sim
from launcher_student import push_to, spot, start_corner, tuned_robot, value_after, last_shot
from test_teleop_fire import shot_table


def setup(s, extra=None):
    s.preset_config()
    values = tuned_robot(s)
    distances = (40, 50, 60, 70, 80, 90)
    values.update(shot_table(s, 0, distances))
    values.update(shot_table(s, 1, distances))
    values.update(extra or {})
    s.set_values(values)
    start_corner(s, values)
    return values


def teleop(s):
    s.init("BIOBUZZ TeleOp")
    s.wait_text("Alliance")
    time.sleep(0.5)
    s.press("circle")
    s.wait_text("start position set")


def to_spot(s, values, d=60):
    cell = (values["redAudienceCellX"], values["redAudienceCellY"])
    push_to(s, *spot(cell, (0, 0), d, (values["frontPivotX"], values["frontPivotY"])))


def first_move():
    with running_sim(seed=41) as s:
        values = setup(s)
        # crooked by hand while the servo has no signal (INIT)
        s.hand("turret", "turn", deg=40, back=False)
        s.hand("turret", "turn", deg=-40, back=True)
        teleop(s)
        to_spot(s, values)
        s.start()
        # watch the screen and the real turret together: never Locked while a turret is still far off
        end, worst, locked_at = time.time() + 6, 0.0, None
        while time.time() < end:
            st = s.state()
            text = "\n".join(st["ds"]["telemetry"])
            t = st["world"]["turret"]
            m = [re.search(r"Turret %s\s*:\s*(-?[\d.]+) -> (-?[\d.]+)" % k, text) for k in "FB"]
            if re.search(r"Locked\s*:\s*true", text) and all(m):
                off = max(abs(t["front"]["deg"] - float(m[0].group(2))), abs(t["back"]["deg"] - float(m[1].group(2))))
                worst = max(worst, off)
                locked_at = locked_at or time.time()
            time.sleep(0.05)
        assert locked_at, "never locked"
        print(f"first move: Locked only with both turrets on target (worst {worst:.1f} deg off while Locked)")
        assert worst < 3, worst
        s.stop()


def out_of_reach():
    with running_sim(seed=42) as s:
        values = setup(s, {"frontYawMinDeg": -30, "frontYawMaxDeg": 30})
        teleop(s)
        s.start()
        to_spot(s, values)
        s.press("left_bumper")
        s.wait(lambda st: "READY" in value_after("\n".join(st["ds"]["telemetry"]), "Flywheel B") or False, 15, "back flywheel ready")
        t = s.telemetry()
        print("Turret F", value_after(t, "Turret F"), "| Turret B", value_after(t, "Turret B"))
        assert s.hand("intake", "loadLauncher", side=0)["ok"]
        assert s.hand("intake", "loadLauncher", side=1)["ok"]
        before = (last_shot(s) or {"id": 0})["id"]
        front_open = 0.0
        s.hold("right_bumper", True)
        end = time.time() + 6
        while time.time() < end:
            front_open = max(front_open, s.world()["servos"]["gate.front"]["open"])
            shots = [x for x in s.get("shots")["shots"] if x["id"] > before]
            if shots and shots[-1]["result"] != "flying":
                break
            time.sleep(0.1)
        time.sleep(1.0)
        s.hold("right_bumper", False)
        shots = [x for x in s.get("shots")["shots"] if x["id"] > before]
        print("shots:", [(x["side"], x["result"]) for x in shots], f"front gate opened at most {front_open:.2f}")
        assert [x["side"] for x in shots] == ["back"], shots
        assert front_open < 0.05, front_open
        assert s.world()["intake"]["held"] == 1, "the NECTAR stays in"
        s.stop()


def lane_pause():
    with running_sim(seed=43) as s:
        values = setup(s)
        teleop(s)
        s.start()
        to_spot(s, values)
        s.press("left_bumper")
        s.wait(lambda st: re.search(r"Locked\s*:\s*true", "\n".join(st["ds"]["telemetry"])) and "\n".join(st["ds"]["telemetry"]).count("READY") >= 2,
               15, "locked and both flywheels ready")
        assert s.hand("intake", "loadLauncher", side=0)["ok"]
        assert s.hand("intake", "loadLauncher", side=1)["ok"]
        # fire starts with both ready (the gates open), then, before the feed starts (gate travel), a hand stops the
        # front wheel: the front lane must wait while the back one goes on
        before = (last_shot(s) or {"id": 0})["id"]
        closed = s.world()["servos"]["gate.front"]["out"]
        s.hold("right_bumper", True)
        s.wait(lambda st: abs(st["world"]["servos"]["gate.front"]["out"] - closed) > 3, 3, "the gates start to open")
        s.hand("flywheels", "turn", wheel=0, deg=0)
        time.sleep(3)
        shots = [x for x in s.get("shots")["shots"] if x["id"] > before]
        print("front held:", [(x["side"], x["result"]) for x in shots])
        assert [x["side"] for x in shots] == ["back"], shots
        assert s.world()["intake"]["held"] == 1, "the NECTAR waits"
        s.hand("flywheels", "release", wheel=0)
        s.wait(lambda st: st["world"]["intake"]["held"] == 0, 10, "the NECTAR fires once the front wheel is back at speed")
        s.hold("right_bumper", False)
        time.sleep(1.5)
        shots = [x for x in s.get("shots")["shots"] if x["id"] > before]
        print("after release:", [(x["side"], x["result"]) for x in shots])
        assert sorted(x["side"] for x in shots) == ["back", "front"], shots
        s.stop()


if __name__ == "__main__":
    first_move()
    print("PASS first turret move at START")
    out_of_reach()
    print("PASS one turret out of reach: the other fires")
    lane_pause()
    print("PASS per-lane pause")
