"""BIOBUZZ TeleOp on a fully tuned robot (programs 1-9 done, shot tables from what scores on this robot): start in the
red corner, drive out, launchers on (L1), wait for Locked + both READY, load one NECTAR (front) and one POLLEN (back),
hold R1 to fire. Both must fly and the elements must reach the CELL."""
import re
import time

from harness import running_sim
from launcher_student import push_to, start_corner, tuned_robot, value_after, wait_shot_done, last_shot, SIDES
from launcher_student import spot


def shot_table(s, side, distances):
    out = {}
    n = 0
    for d in distances:
        sol = s.get(f"shots/solve?side={side}&distance={d}")
        if not sol["reachable"]:
            continue
        n += 1
        key = f"{SIDES[side]}Shot{n:02d}"
        out.update({key + "Distance": d, key + "Speed": round(sol["speed"]), key + "Feed": 0.5})
    assert n >= 2, out
    return out


def run(s, extra=None):
    s.preset_config()
    values = tuned_robot(s)
    distances = (40, 50, 60, 70, 80, 90)
    values.update(shot_table(s, 0, distances))
    values.update(shot_table(s, 1, distances))
    values.update(extra or {})
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
    s.wait_text("start position set")
    s.start()
    cell = (values["redAudienceCellX"], values["redAudienceCellY"])
    push_to(s, *spot(cell, (0, 0), 60, (values["frontPivotX"], values["frontPivotY"])))
    s.press("left_bumper")
    def ready(st):
        text = "\n".join(st["ds"]["telemetry"])
        return re.search(r"Locked\s*:\s*true", text) and text.count("READY") >= 2
    s.wait(ready, 15, "locked and both flywheels ready")
    t = s.telemetry()
    print("Distance F/B", value_after(t, "Distance F/B"), "| Residual F/B", value_after(t, "Residual F/B"))
    assert s.hand("intake", "loadLauncher", side=0)["ok"]
    assert s.hand("intake", "loadLauncher", side=1)["ok"]
    before = (last_shot(s) or {"id": 0})["id"]
    s.hold("right_bumper", True)
    s.wait(lambda st: st["world"]["intake"]["held"] == 0, 8, "both elements fired")
    s.hold("right_bumper", False)
    # watch both land (a 59 deg shot is in the air about a second)
    s.wait(lambda st: all(x["result"] != "flying" for x in s.get("shots")["shots"] if x["id"] > before), 10, "both shots landed")
    shots = [x for x in s.get("shots")["shots"] if x["id"] > before]
    for x in shots:
        print(f"{x['side']} {x['type']}: {x['result']} ({x['detail']}), {x['distanceIn']:.1f} in, exit {x['exitMps']:.2f} m/s,"
              f" aim error {x['aimErrorDeg']:.1f} deg")
    assert {x["side"] for x in shots} == {"front", "back"}, shots
    assert all(x["result"] == "made" for x in shots), shots
    assert s.world()["intake"]["held"] == 0


if __name__ == "__main__":
    with running_sim(seed=21) as s:
        run(s)
    print("PASS TeleOp aim and fire")
