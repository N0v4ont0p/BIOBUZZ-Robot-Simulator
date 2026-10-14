"""The results loop in coach mode: program 1 for real, copy results, coach apply, install; then program 2-style
results (written straight into the robot's results file) reach the code settings and team-config.yaml; a broken
sandbox edit fails the build and the robot keeps the old one; the report card answers."""
import os

import test_p1_check_devices
from harness import running_sim

TC = "TeamCode/src/main/java/org/firstinspires/ftc/teamcode"


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def wait_robot(s):
    s.wait(lambda st: st["ds"]["robot"] is not None and st["ds"]["state"] == "STOPPED", 60, "robot restarted")


def run(s):
    paths = s.get("loop/status")["paths"]
    sandbox, robot = paths["sandbox"], paths["robot"]
    yaml = os.path.join(sandbox, "team-config.yaml")

    test_p1_check_devices.run(s)
    before = read(yaml)
    pulled = s.post("loop/pull")
    assert pulled["files"] == ["biobuzz_results.csv"], pulled
    r = s.post("loop/coach")
    assert r["ok"] and r["installed"], r
    assert r["values"] == {} and r["info"]["devicesFound"] == "14/14", r
    assert read(yaml) == before, "program 1 is info only: team-config.yaml must not change"
    wait_robot(s)

    with open(os.path.join(robot, "biobuzz_results.csv"), "a") as f:
        f.write("2 Motor Directions,intakeReversed,true,9000000000000\n"
                "2 Motor Directions,frontFlywheelReversed,true,9000000000001\n"
                "2 Motor Directions,frontFlywheelEncoderOpposite,false,9000000000002\n"
                "2 Motor Directions,backFlywheelReversed,true,9000000000003\n"
                "2 Motor Directions,backFlywheelEncoderOpposite,true,9000000000004\n"
                "4 Servo Positions,frontHoodMin,0.120,9000000000005\n")
    s.post("loop/pull")
    plan = s.get("loop/plan")
    assert plan["values"] == {"Intake.REVERSED": True, "Launcher.FRONT_REVERSED": True, "frontHoodMin": 0.12}, plan["values"]
    r = s.post("loop/coach")
    assert r["ok"] and r["installed"], r
    assert "public static boolean REVERSED = true;" in read(os.path.join(sandbox, TC, "subsystems/Intake.java"))
    launcher = read(os.path.join(sandbox, TC, "subsystems/Launcher.java"))
    assert "FRONT_REVERSED = true;" in launcher and "BACK_REVERSED = false;" in launcher, "back flywheel had a STOP"
    assert "frontHoodMin: {value: 0.12, origin: measured" in read(yaml)
    assert "+    frontHoodMin: {value: 0.12, origin: measured" in r["diff"], r["diff"]
    assert len(r["stop"]) == 1 and "back flywheel" in r["stop"][0], r["stop"]
    wait_robot(s)

    intake = os.path.join(sandbox, TC, "subsystems/Intake.java")
    good = read(intake)
    with open(intake, "a") as f:
        f.write("\nthis is not java\n")
    try:
        s.post("build")
        raise AssertionError("a broken sandbox must not build")
    except RuntimeError as e:
        assert "did not compile" in str(e), e
    st = s.state()
    assert st["sim"]["buildError"] and st["ds"]["robot"] is not None, "the robot keeps running the old build"
    s.init("1 Check Devices")
    s.stop()
    with open(intake, "w") as f:
        f.write(good)
    s.post("build")
    wait_robot(s)
    assert s.state()["sim"].get("buildError") is None

    rep = s.get("loop/report")
    assert isinstance(rep["entries"], list) and isinstance(rep["faults"], list), rep
    assert all(e.get("truth") is None for e in rep["entries"]), "truth stays hidden until reveal"
    rev = s.get("loop/report?reveal=1")
    assert rev["revealed"] and all(e.get("truth") is not None for e in rev["entries"])
    assert set(rep["count"]) == {"green", "yellow", "red", "unmeasured", "other"}


if __name__ == "__main__":
    with running_sim("A2-perfect-preset-config.json", seed=2) as s:
        run(s)
    print("PASS coach loop")
