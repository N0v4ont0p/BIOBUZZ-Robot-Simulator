"""apply_results.py without a simulator: newest line per key, code settings from program 2, info keys ignored,
STOP results, the points file to <side>ShotNN keys (with '# removed' lines), nothing written without --apply."""
import json
import os
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(os.path.dirname(HERE), "tools", "apply_results.py")
REPO = os.path.dirname(os.path.dirname(HERE))

RESULTS = """program,key,value,time_ms
1 Check Devices,encodersNotCounting,fs,100
1 Check Devices,devicesFound,14/14,101
1 Check Devices,batteryVolts,12.2,102
2 Motor Directions,intakeReversed,false,200
2 Motor Directions,intakeReversed,true,210
2 Motor Directions,frontFlywheelReversed,false,201
2 Motor Directions,frontFlywheelEncoderOpposite,false,202
2 Motor Directions,backFlywheelReversed,true,203
2 Motor Directions,backFlywheelEncoderOpposite,true,204
3 Yaw Tuner,yawKs,0.0800000,300
3 Yaw Tuner,yawKs,0.0900000,301
3 Yaw Tuner,yawSettleS,nan,302
6 Servo Positions,frontHoodMin,0.120,400
7 Flywheel Tuner,frontFlywheelF,11.5,500
7 Flywheel Tuner,frontFlywheelThrowsOut,false,600
"""
POINTS = """distance_in,speed,hood,feed,battery_v
40.00,1500,0.300,0.80,12.6
50.00,1600,0.320,0.80,12.6
# removed 50.00
51.20,1650,0.330,0.78,12.5
30.00,1400,0.280,0.85,12.6
"""


def run():
    with tempfile.TemporaryDirectory() as d:
        with open(os.path.join(d, "biobuzz_results.csv"), "w") as f:
            f.write(RESULTS)
        with open(os.path.join(d, "biobuzz_front_points.csv"), "w") as f:
            f.write(POINTS)
        before = open(os.path.join(REPO, "team-config.yaml")).read()
        p = json.loads(subprocess.check_output(["python3", TOOL, d, "--sandbox", REPO]))
        assert open(os.path.join(REPO, "team-config.yaml")).read() == before, "plan must not write"
    v = p["values"]
    assert v["Intake.REVERSED"] is True, v
    assert v["Launcher.FRONT_REVERSED"] is False, v
    assert "Launcher.BACK_REVERSED" not in v, "encoder opposite: STOP, not entered"
    assert v["yawKs"] == 0.09, v
    assert v["frontHoodMin"] == 0.12, v
    assert "frontFlywheelF" not in v, "throws out = false after F: program 7 front values dropped"
    assert "yawSettleS" not in v and p["info"]["yawSettleS"] == "nan"
    assert "batteryVolts" in p["info"] and "devicesFound" in p["info"]
    assert [v[f"frontShot{i:02d}Distance"] for i in (1, 2, 3)] == [30.0, 40.0, 51.2], v
    assert v["frontShot03Speed"] == 1650 and "frontShot04Distance" not in v
    assert p["shots"] == {"front": 3}
    assert len(p["stop"]) == 2, p["stop"]
    assert any("12.20 V" in n for n in p["notes"]) and any("fs" in n for n in p["notes"]), p["notes"]
    keys = {b["key"] for b in p["byHand"]}
    # Pedro's tuners and program 6 save to the results file now: only CAD, E-1 and poses are typed by hand
    assert {"robotMass", "centerToFront"} <= keys, keys
    assert not keys & {"yawKv", "xPodOffset", "headingKP", "podTicksPerMm", "velocityFrameField"}, keys


if __name__ == "__main__":
    run()
    print("PASS apply_results")
