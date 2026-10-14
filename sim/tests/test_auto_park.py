"""SAMPLE Auto Red and Blue (the reference route, see SampleAutoPaths) with the sample route (NONE->SHOOT, INTAKE->WAIT,
INTAKE->SHOOT, RAMP->NONE, NONE->PARK along the line from the start pose to the park pose), on a fully tuned robot. The robot drives every leg with Pedro, the aim call runs every loop,
each action shows on the screen, and it parks on the pose. Red is the 180-degree rotation of what is drawn for blue,
so the red robot parks at the rotated pose."""
import re
import time

from harness import running_sim
from launcher_student import expected, guide, pick, tuned_robot
from test_teleop_fire import shot_table

START = (30.0, 120.0, 0.0)
PARK = (60.0, 100.0, -60.0)


def run(alliance, seed):
    with running_sim(seed=seed) as s:
        s.preset_config()
        values = tuned_robot(s)
        # ramp positions and a shot table, so the shots are ready shots (not the timeout fallback) and the RAMP leg deploys
        exp, gd = expected(s), guide(s)
        for k in ("rampStowed", "rampDeployed"):
            values[k] = round(float(pick(exp, gd, k)), 4)
        distances = (30, 40, 50, 60, 70, 80, 90, 100)
        values.update(shot_table(s, 0, distances))
        values.update(shot_table(s, 1, distances))
        values.update({"blueStartX": START[0], "blueStartY": START[1], "blueStartHeading": START[2],
                       "blueParkX": PARK[0], "blueParkY": PARK[1], "blueParkHeading": PARK[2]})
        s.set_values(values)
        # where the drawn (blue) start becomes for this alliance
        sx, sy, sh = (START if alliance == "Blue" else (144 - START[0], 144 - START[1], START[2] + 180))
        px, py = (PARK[0], PARK[1]) if alliance == "Blue" else (144 - PARK[0], 144 - PARK[1])
        s.hand("robot", "onFloor")
        s.hand("drive", "place", x=sx, y=sy, headingDeg=sh, exact=True)
        s.init(f"SAMPLE Auto {alliance}")
        s.wait_text("Alliance", timeout=20)
        assert "WILL NOT DRIVE" not in s.telemetry(), s.telemetry()
        s.start()
        s.wait_text("Aim", timeout=10)
        end = time.time() + 40
        d = s.world()["drive"]
        during, ends = set(), set()
        while time.time() < end:
            d = s.world()["drive"]
            m = re.search(r"Status\s*:\s*leg \d: (\w+) -> (\w+)", s.telemetry())
            if m:
                during.add(m.group(1))
                ends.add(m.group(2))
            if re.search(r"Status\s*:\s*leg \d: waiting", s.telemetry()):
                ends.add("waited")
            if re.search(r"Status\s*:\s*parked", s.telemetry()) and d["speed"] < 1:
                break
            time.sleep(0.2)
        assert {"NONE", "INTAKE", "RAMP"} <= during, during
        assert {"SHOOT", "WAIT", "NONE", "PARK", "waited"} <= ends, ends
        print(f"{alliance}: parked at {d['x']:.1f}, {d['y']:.1f} (wanted {px:.1f}, {py:.1f}); screen:\n  " + s.telemetry().replace("\n", "\n  "))
        assert ((d["x"] - px) ** 2 + (d["y"] - py) ** 2) ** 0.5 < 3, d
        assert re.search(r"Aim\s*:", s.telemetry()), s.telemetry()


if __name__ == "__main__":
    run("Blue", 51)
    run("Red", 52)
    print("PASS autos park")
