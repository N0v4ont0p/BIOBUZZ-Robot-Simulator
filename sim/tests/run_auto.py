"""Run one OpMode of an auto in the simulator on a fully tuned robot (shot tables and ramp positions set), load one
element per launcher at every shot, and print the status line each time it changes and where each shot landed.   python3 run_auto.py Blue Blue_MyAuto 81 9.5 90   (alliance, OpMode, blue start x y heading)
Not part of run_all.sh."""
import re, time, sys
from harness import running_sim
from launcher_student import tuned_robot, expected, guide, pick
from test_teleop_fire import shot_table

def run(alliance, name, start, seed):
    with running_sim(seed=seed) as s:
        s.preset_config()
        values = tuned_robot(s)
        exp, gd = expected(s), guide(s)
        for k in ("rampStowed", "rampDeployed"):
            values[k] = round(float(pick(exp, gd, k)), 4)
        dist = (30, 40, 50, 60, 70, 80, 90, 100, 110)
        values.update(shot_table(s, 0, dist)); values.update(shot_table(s, 1, dist))
        s.set_values(values)
        sx, sy, sh = start if alliance == "Blue" else (144 - start[0], 144 - start[1], start[2] + 180)
        s.hand("robot", "onFloor")
        s.hand("drive", "place", x=sx, y=sy, headingDeg=sh, exact=True)
        s.init(name)
        s.wait_text("Alliance", timeout=20)
        print("INIT:", s.telemetry().replace("\n", " | ")[:300])
        assert "WILL NOT DRIVE" not in s.telemetry(), s.telemetry()
        s.start()
        t0 = time.time(); last = None; loaded_at = None
        while time.time() - t0 < 50:
            tel = s.telemetry()
            m = re.search(r"Status\s*:\s*(.+)", tel)
            d = s.world()["drive"]
            st = m.group(1) if m else "?"
            if st.startswith("shoot: parked") and loaded_at is None:
                loaded_at = time.time()
                # the sim has no field elements: put one element in each launcher when a shot starts
                s.hand("intake", "loadLauncher", side=0); s.hand("intake", "loadLauncher", side=1)
            if not st.startswith("shoot") and loaded_at is not None:
                loaded_at = None
            if st != last:
                print(f"  {time.time()-t0:5.1f}s {st}  pose {d['x']:.1f},{d['y']:.1f} h{d.get('headingDeg', d.get('heading', 0)):.0f}", flush=True)
                last = st
            if re.search(r"Status\s*:\s*(parked|done|abort)", tel) and d["speed"] < 1:
                break
            time.sleep(0.2)
        print(alliance, "final:", s.telemetry().replace("\n", " | "))
        sh = s.get("shots")["shots"]
        print("SHOTS", len(sh))
        for x in sh: print("  ", {k: (round(v, 1) if isinstance(v, float) else v) for k, v in x.items()})
if __name__ == "__main__":
    x, y, h = (float(v) for v in sys.argv[3:6])
    run(sys.argv[1], sys.argv[2], (x, y, h), 81)
