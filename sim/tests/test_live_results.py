"""Programs use what earlier programs saved on the robot, with no build in between (LiveResults).
Program 2 is run for real: its directions go only into the robot's results file. Program 4's gate positions and ramp times
are added to the results file as if program 4 had run. Program 7 then starts with no NOT READY line, shows the yellow
"from the robot's results file" line, and both flywheels throw the element out (so it used program 2's directions).
A rebuild in between (as a restart does) must not put the code's placeholder directions back. At the end: one copy,
coach mode enters everything, one install; the yellow line is gone, and TeleOp, which only uses
the code, gets the same numbers."""
import os
import time

import test_p2_directions
from harness import running_sim
from launcher_student import expected, guide, results, servo_values
from test_p7_flywheel import run_side


def robot_file(s):
    return os.path.join(s.get("loop/status")["paths"]["robot"], "biobuzz_results.csv")


if __name__ == "__main__":
    with running_sim(seed=7) as s:
        exp = test_p2_directions.run(s)
        test_p2_directions.check(s, exp)
        s.stop()
        s.wait(lambda st: st["ds"]["active"] == "3 Turret Servos" and st["ds"]["state"] == "INIT", 10, "program 3 auto-loaded")
        builds = s.get("loop/status")["builtAt"]

        now = int(time.time() * 1000)
        with open(robot_file(s), "a") as f:
            for k, v in servo_values(expected(s), guide(s)).items():
                f.write(f"4 Servo Positions,{k},{v},{now}\n")

        s.init("7 Flywheel Tuner")
        s.wait_text("Press START")
        t = s.telemetry()
        assert "from the robot's results file" in t, t
        assert "NOT READY" not in t, t
        s.start()
        run_side(s, 0)
        run_side(s, 1)
        s.wait_text("PROGRAM 7 COMPLETE")
        got = results(s)
        assert got["frontFlywheelThrowsOut"] == "true" and got["backFlywheelThrowsOut"] == "true", got
        assert s.get("loop/status")["builtAt"] == builds, "no build should have happened"
        s.stop()

        # a rebuild / restart (the build installed after the directions were saved) must not put the code's placeholder
        # directions back: they have no "not set" value, so the results file wins
        s.post("build")
        s.wait(lambda st: st["ds"]["robot"] is not None and st["ds"]["state"] == "STOPPED" and s.get("loop/status")["builtAt"] != builds,
               180, "the robot rebuilt and restarted")
        s.init("0 Setup Report")
        s.wait_text("SETUP REPORT")
        t = s.telemetry()
        for k in ("intakeReversed", "frontFlywheelReversed", "backFlywheelReversed"):
            assert f"{k} = {got[k]}" in t, (k, got[k], t)
        print("after a rebuild the tuned directions are still used:", {k: got[k] for k in ("intakeReversed", "frontFlywheelReversed", "backFlywheelReversed")})
        s.stop()

        s.post("loop/pull")
        r = s.post("loop/coach")
        assert r["ok"] and r["installed"], r
        for k in ("Intake.REVERSED", "frontGateOpen", "frontFlywheelF", "backFlywheelF"):
            assert k in r["values"], (k, r["values"])
        s.wait(lambda st: st["ds"]["robot"] is not None and st["ds"]["state"] == "STOPPED", 60, "robot restarted")

        s.init("7 Flywheel Tuner")
        s.wait_text("Press START")
        t = s.telemetry()
        assert "from the robot's results file" not in t, "after the final build every number is in the code:\n" + t
        s.stop()
    print("PASS live results")
