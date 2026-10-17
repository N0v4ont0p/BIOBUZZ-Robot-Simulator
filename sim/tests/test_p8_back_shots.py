"""Program 8 (Back Shot Tuner: POLLEN, the main launcher, first): the full session, 10 points with feed checks, on a
robot whose programs 1-7 and CAD / field sheets are in the code. Every saved point must score on this robot
(backShotPointsMakeable). The student and run() are program 9's (test_p9_front_shots.py)."""
from harness import running_sim
from test_p9_front_shots import run

if __name__ == "__main__":
    with running_sim(seed=11) as s:
        run(s, side=1, points=10)
    print("PASS program 8")
