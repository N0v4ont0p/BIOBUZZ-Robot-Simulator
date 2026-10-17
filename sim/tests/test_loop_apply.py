"""apply_results.py without a simulator: newest line per key, code settings from program 2, info keys ignored,
STOP results, the points files to <side>ShotNN keys (with '# removed' lines; a file from before 2026-10-17 still has a
hood column, which is skipped), nothing written without --apply."""
import json
import os
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
TOOL = os.path.join(os.path.dirname(HERE), "tools", "apply_results.py")
REPO = os.path.dirname(os.path.dirname(HERE))

RESULTS = """program,key,value,time_ms
1 Check Devices,encodersNotCounting,fs,100
1 Check Devices,devicesFound,16/16,101
1 Check Devices,batteryVolts,12.2,102
2 Motor Directions,intakeReversed,false,200
2 Motor Directions,intakeReversed,true,210
2 Motor Directions,frontFlywheelReversed,false,201
2 Motor Directions,frontFlywheelEncoderOpposite,false,202
2 Motor Directions,backFlywheelReversed,true,203
2 Motor Directions,backFlywheelEncoderOpposite,true,204
2 Motor Directions,rampReversed,true,205
3 Turret Servos,frontYawForward,0.4800,300
3 Turret Servos,frontYawForward,0.4900,301
3 Turret Servos,yawDegPerS,nan,302
4 Servo Positions,backGate2Closed,0.620,400
4 Servo Positions,rampOutS,0.75,401
7 Flywheel Tuner,frontFlywheelF,11.5,500
7 Flywheel Tuner,frontFlywheelThrowsOut,false,600
"""
# an old front points file (hood column), then a session from after the update appended to it
POINTS = """distance_in,speed,hood,feed,battery_v
40.00,1500,0.300,0.80,12.6
50.00,1600,0.320,0.80,12.6
# removed 50.00
# columns distance_in,speed,feed,battery_v
# session 2026-10-18 10:00
51.20,1650,0.78,12.5
30.00,1400,0.85,12.6
"""
BACK_POINTS = """distance_in,speed,feed,battery_v
# columns distance_in,speed,feed,battery_v
# session 2026-10-18 11:00
45.00,1900,0.60,12.6
70.00,2150,0.55,12.5
"""


def run():
    with tempfile.TemporaryDirectory() as d:
        with open(os.path.join(d, "biobuzz_results.csv"), "w") as f:
            f.write(RESULTS)
        with open(os.path.join(d, "biobuzz_front_points.csv"), "w") as f:
            f.write(POINTS)
        with open(os.path.join(d, "biobuzz_back_points.csv"), "w") as f:
            f.write(BACK_POINTS)
        before = open(os.path.join(REPO, "team-config.yaml")).read()
        p = json.loads(subprocess.check_output(["python3", TOOL, d, "--sandbox", REPO]))
        assert open(os.path.join(REPO, "team-config.yaml")).read() == before, "plan must not write"
    v = p["values"]
    assert v["Intake.REVERSED"] is True, v
    assert v["Launcher.FRONT_REVERSED"] is False, v
    assert "Launcher.BACK_REVERSED" not in v, "encoder opposite: STOP, not entered"
    assert v["Ramp.RAMP_REVERSED"] is True, v
    assert v["frontYawForward"] == 0.49, v
    assert v["backGate2Closed"] == 0.62 and v["rampOutS"] == 0.75, v
    assert not any("Hood" in k for k in v), [k for k in v if "Hood" in k]
    assert "frontFlywheelF" not in v, "throws out = false after F: program 7 front values dropped"
    assert "yawDegPerS" not in v
    assert "batteryVolts" in p["info"] and "devicesFound" in p["info"]
    assert [v[f"frontShot{i:02d}Distance"] for i in (1, 2, 3)] == [30.0, 40.0, 51.2], v
    assert v["frontShot03Speed"] == 1650 and "frontShot04Distance" not in v
    # the old file's feed is its 4th column, the new rows' the 3rd
    assert [v[f"frontShot{i:02d}Feed"] for i in (1, 2, 3)] == [0.85, 0.80, 0.78], v
    assert [v[f"backShot{i:02d}Feed"] for i in (1, 2)] == [0.60, 0.55] and v["backShot02Speed"] == 2150, v
    assert p["shots"] == {"front": 3, "back": 2}
    assert len(p["stop"]) == 2, p["stop"]
    assert any("12.20 V" in n for n in p["notes"]) and any("fs" in n for n in p["notes"]), p["notes"]
    keys = {b["key"] for b in p["byHand"]}
    # Pedro's tuners and program 6 save to the results file now: only CAD, E-1 and poses are typed by hand
    assert {"robotMass", "redFarCellX"} <= keys, keys
    # the CAD sheet came from the Fusion CAD (2026-10-17): no longer typed by hand
    assert "centerToFront" not in keys and "frontPivotX" not in keys, keys
    assert not keys & {"yawKv", "xPodOffset", "headingKP", "podTicksPerMm", "velocityFrameField"}, keys


if __name__ == "__main__":
    run()
    print("PASS apply_results")
