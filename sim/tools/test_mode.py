"""Test mode (simulator only): puts the true values of this hidden robot in the sandbox code, as if programs 1 to 9
and the CAD / field sheets had all been done perfectly, then builds and installs. Run by the simulator itself
(Sim.startTestMode) after it has saved a checkpoint and emptied the robot's files; ending test mode restores that
checkpoint and deletes everything made in between.

    test_mode.py <simulator url>
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "tests"))
from simctl import Sim  # noqa: E402
from launcher_student import SIDES, expected, guide, pick, tuned_robot  # noqa: E402

SPOTS = [36 + 5 * k for k in range(15)]


def shot_table(s, side):
    """The sim's solver at every 5 in from 36 in out, the reachable ones (up to 16 per launcher)."""
    out, n = {}, 0
    for d in SPOTS:
        sol = s.get(f"shots/solve?side={side}&distance={d}")
        if not sol.get("reachable") or n >= 16:
            continue
        n += 1
        key = f"{SIDES[side]}Shot{n:02d}"
        out.update({key + "Distance": d, key + "Speed": round(sol["speed"]), key + "Feed": 0.5})
    return out


def values(s):
    v = tuned_robot(s)
    exp = expected(s)
    # program 6 (position check)
    for k in ("podTicksPerMm", "imuYawScalar"):
        if exp.get(k) is not None:
            v[k] = round(exp[k], 6)
    if exp.get("velocityFrame") is not None:
        v["velocityFrameField"] = 1 if exp["velocityFrame"] >= 0.5 else 0
    # the auto draft (H-2): the sample auto's start and park poses (the ones sim/tests/test_auto_park.py drives)
    v.update({"blueStartX": 30.0, "blueStartY": 120.0, "blueStartHeading": 0.0, "blueParkX": 60.0, "blueParkY": 100.0, "blueParkHeading": -60.0})
    # programs 8 / 9
    for side in (0, 1):
        v.update(shot_table(s, side))
        v[SIDES[side] + "YawTrimDeg"] = 0
    return v


def main():
    s = Sim(sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080")
    s.preset_config()
    v = values(s)
    s.set_values(v)
    print(f"test mode: {len(v)} values in the code, both shot tables ({sum(1 for k in v if k.endswith('Distance'))} points)")


if __name__ == "__main__":
    main()
