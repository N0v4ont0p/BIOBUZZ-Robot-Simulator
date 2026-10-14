"""Program 9 (Back Shot Tuner): the program 8 student on the back launcher with POLLEN, a short session (4 points,
then share twice to finish anyway), skipping its feed check with L1 (the robot drives on). Every saved point must score
on this robot (backShotPointsMakeable)."""
from harness import running_sim
import test_p8_front_shots
from test_p8_front_shots import run

if __name__ == "__main__":
    with running_sim(seed=12) as s:
        # this session skips its feed check with L1: the robot must drive on by itself
        test_p8_front_shots.SKIP_FEED_CHECK.append(True)
        run(s, side=1, points=4)
    print("PASS program 9")
