"""0 Setup Report: every value per program with where it is, sanity warnings and the values a later run changed a lot.
A tuned robot (programs 1-7 and the CAD sheet in the code, no shot tables), plus a results file in which program 7 ran
twice and the front P moved from 2.0 to 30.0: the report says which programs are done, that the shot tables are empty,
that frontFlywheelP is only in the results file, and lists the big change. It also writes biobuzz_setup_report.txt."""
import os
import time

from harness import running_sim
from launcher_student import tuned_robot


def run():
    with running_sim(seed=31) as s:
        s.preset_config()
        values = tuned_robot(s)
        s.set_values(values)
        path = os.path.join(s.get("loop/status")["paths"]["robot"], "biobuzz_results.csv")
        now = int(time.time() * 1000)
        with open(path, "a") as f:
            if os.path.getsize(path) == 0:
                f.write("program,key,value,time_ms\n")
            f.write(f"7 Flywheel Tuner,frontFlywheelP,2.0,{now}\n")
            f.write(f"7 Flywheel Tuner,frontFlywheelP,30.0,{now + 1000}\n")
        s.init("0 Setup Report")
        s.wait_text("SETUP REPORT", timeout=20)
        time.sleep(1.0)
        t = s.telemetry()
        print("\n".join(t.splitlines()[:12]))
        for want in ("CAD sheet: done", "7 Flywheel Tuner: done", "front shot table: 0 points",
                     "frontFlywheelP = 30.0  [results file only (not built in yet)]", "Changed a lot by a later run: 1",
                     "frontFlywheelP 2.0 -> 30.0"):
            assert want in t, (want, t)
        report = s.file("biobuzz_setup_report.txt")
        assert report and "Changed a lot by a later run: 1" in report, report
        s.stop()


if __name__ == "__main__":
    run()
    print("PASS setup report")
