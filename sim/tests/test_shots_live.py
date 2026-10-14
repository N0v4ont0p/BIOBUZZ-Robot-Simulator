"""Programs 8 and 9 with nothing but the CAD sheet built in: yaw, servos, flywheels, directions, Pedro and the pod /
gyro scales are only in the robot's results file (as after programs 2-7 with no build). Both shot tuners must run,
drive themselves with Pedro, and save points that score, with no build during either."""
from harness import running_sim
from test_p8_front_shots import run

if __name__ == "__main__":
    with running_sim(seed=11) as s:
        run(s, side=0, points=4, live=True)
    print("PASS program 8 from the results file")
    with running_sim(seed=12) as s:
        run(s, side=1, points=4, live=True)
    print("PASS program 9 from the results file")
