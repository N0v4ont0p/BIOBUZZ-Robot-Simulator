"""Once every setup program is done, the AutoTool autos run with nothing more to enter: the robot gets the values the
programs and the CAD / field sheets give (test mode's set, the same keys apply_results.py enters), one build, then every
AutoTool auto (both alliances, from its own start) must drive every leg, fire at every SHOOT leg and score (one element per
launcher loaded when the shot starts: the sim has no field elements), never report a value as not set or skip a shot,
and finish before the abort time."""
import os
import re
import sys
import time

from harness import running_sim
from launcher_student import straighten_turrets

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "tools"))
from test_mode import values  # noqa: E402

OPMODES = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "TeamCode", "src", "main", "java", "org",
                       "firstinspires", "ftc", "teamcode", "opmodes")


def shoot_legs(name):
    src = open(os.path.join(OPMODES, name + ".java")).read()
    legs = src[src.index("List<Leg> legs()"):]
    return len(re.findall(r"End\.SHOOT|Action\.SHOOT", legs[:legs.index(");")]))


def run(s, alliance, auto):
    x, y, h = auto["x"], auto["y"], auto["headingDeg"]
    if alliance == "Red":
        x, y, h = 144 - x, 144 - y, (h + 360) % 360 - 180
    s.hand("drive", "place", x=x, y=y, headingDeg=h, exact=True)
    straighten_turrets(s)
    shots_before = len(s.get("shots")["shots"])
    name = f"{alliance}_{auto['name']}"
    s.init(name)
    s.wait_text("Alliance", timeout=20)
    assert "WILL NOT DRIVE" not in s.telemetry(), (name, s.telemetry())
    s.start()
    t0, statuses, loaded, status = time.time(), [], False, "?"
    while time.time() - t0 < 45:
        tel = s.telemetry()
        m = re.search(r"Status\s*:\s*(.+)", tel)
        status = m.group(1).strip() if m else "?"
        if not statuses or statuses[-1] != status:
            statuses.append(status)
        if status.startswith("shoot: parked") and not loaded:
            s.hand("intake", "loadLauncher", side=0)
            s.hand("intake", "loadLauncher", side=1)
            loaded = True
        elif not status.startswith("shoot"):
            loaded = False
        if re.match(r"(parked|done|abort)", status) and s.world()["drive"]["speed"] < 1:
            break
        time.sleep(0.2)
    took = time.time() - t0
    s.stop()
    shots = s.get("shots")["shots"][shots_before:]
    fired, made = len(shots), sum(1 for x in shots if x.get("result") == "made")
    bad = [st for st in statuses if "not set" in st or "skipped" in st]
    print(f"{name}: {status} after {took:.1f} s, {fired} shots ({made} scored) for {shoot_legs(auto['name'])} SHOOT legs")
    assert not bad, (name, bad)
    assert re.match(r"(parked|done)", status), (name, statuses)
    assert fired >= 2 * shoot_legs(auto["name"]), (name, fired, statuses)
    assert made >= 0.75 * fired, (name, [x.get("result") for x in shots])


if __name__ == "__main__":
    with running_sim(seed=7) as s:
        s.preset_config()
        s.set_values(values(s))
        s.hand("robot", "onFloor")
        autos = s.get("autos/starts")
        if not autos:
            print("SKIP no AutoTool autos in TeamCode/.../opmodes yet: write one with AutoTool, then run this again")
            sys.exit(0)
        for auto in autos:
            for alliance in ("Blue", "Red"):
                run(s, alliance, auto)
    print("PASS every AutoTool auto runs on the tuned robot")
