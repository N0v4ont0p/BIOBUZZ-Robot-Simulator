"""The shot table as a curve (ShotCurve, the default) against straight lines between the points, scored by the
simulator. Per robot and launcher a student-like table: at each spot the speed that went 4 for 4 is somewhere inside
the window that scores (at least 75 %) (no hood: the launch angle is fixed). Both ways of reading the table are then scored at
the saved distances, half way between them and past the last one (DECODE's way: the end fit carries on). The curve
must score more on average and never lose more than one shot in 24 on any robot."""
import os
import random
import subprocess
import sys
import tempfile

from harness import running_sim
from test_tuning_rules import java_home, REPO, HERE

SEEDS = range(1, 11)
SPOTS = [36 + 5 * k for k in range(10)]
N = 48


def compile_cli(out):
    home = java_home()
    javac = os.path.join(home, "bin", "javac") if home else "javac"
    src = [os.path.join(REPO, "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/subsystems/ShotCurve.java"),
           os.path.join(HERE, "rules", "CurveCli.java")]
    subprocess.check_call([javac, "-nowarn", "-encoding", "UTF-8", "-d", out] + src)
    return os.path.join(home, "bin", "java") if home else "java"


def curve(java, out, rows, ask):
    text = "\n".join(f"{d},{s},{f}" for d, s, f in rows) + "\n?\n" + "\n".join(str(d) for d in ask) + "\n"
    res = subprocess.run([java, "-cp", out, "CurveCli"], input=text, capture_output=True, text=True, check=True)
    lines = res.stdout.strip().splitlines()
    vals = {}
    for l in lines[1:]:
        c = [float(x) for x in l.split()]
        vals[round(c[0], 3)] = {"lines": (c[1], c[2]), "smooth": (c[3], c[4])}
    return lines[0], vals


def chance(s, side, d, speed, feed=0.5, n=N):
    return s.get(f"shots/chance?side={side}&distance={d}&speed={speed}&feed={feed}&n={n}")["makeChance"]


def student_table(s, side, rnd):
    rows = []
    for d in SPOTS:
        sol = s.get(f"shots/solve?side={side}&distance={d}")
        if not sol.get("reachable"):
            continue
        good = [sp for sp in range(int(sol["speed"]) - 200, int(sol["speed"]) + 201, 10) if chance(s, side, d, sp, n=24) >= 0.75]
        if not good:
            continue
        rows.append((d, float(rnd.choice(good)), 0.5))
    return rows


def main():
    out = tempfile.mkdtemp()
    java = compile_cli(out)
    totals = {"nodes": [0, 0], "between": [0, 0], "past": [0, 0]}
    worst = 0.0
    for seed in SEEDS:
        with running_sim(seed=seed) as s:
            for side in (0, 1):
                rnd = random.Random(seed * 10 + side)
                rows = student_table(s, side, rnd)
                if len(rows) < 5:
                    print(f"seed {seed} side {side}: only {len(rows)} reachable spots, skipped")
                    continue
                nodes = [r[0] for r in rows]
                between = [(a + b) / 2 for a, b in zip(nodes, nodes[1:])]
                past = [nodes[-1] + 5, nodes[-1] + 10]
                desc, vals = curve(java, out, rows, nodes + between + past)
                got = {}
                for name, ds in (("nodes", nodes), ("between", between), ("past", past)):
                    for way in ("lines", "smooth"):
                        c = [chance(s, side, d, *vals[round(d, 3)][way]) for d in ds]
                        got[(name, way)] = sum(c) / len(c)
                    totals[name][0] += got[(name, "lines")]
                    totals[name][1] += got[(name, "smooth")]
                inner = lambda way: (got[("nodes", way)] * len(nodes) + got[("between", way)] * len(between)) / (len(nodes) + len(between))
                diff = inner("smooth") - inner("lines")
                worst = min(worst, diff)
                print(f"seed {seed} {'front' if side == 0 else 'back'}: {len(rows)} points; lines {inner('lines'):.3f} smooth {inner('smooth'):.3f}"
                      f" (between {got[('between', 'lines')]:.3f} -> {got[('between', 'smooth')]:.3f}, past {got[('past', 'lines')]:.3f} -> {got[('past', 'smooth')]:.3f}) {desc}")
                sys.stdout.flush()
    for name, (l, c) in totals.items():
        print(f"total {name}: lines {l:.2f} smooth {c:.2f}")
    inner_l = totals["nodes"][0] + totals["between"][0]
    inner_c = totals["nodes"][1] + totals["between"][1]
    assert inner_c > inner_l, ("the curve must score more on average", inner_l, inner_c)
    assert worst >= -1 / 24, ("the curve lost more than one shot in 24 on a robot", worst)
    print(f"worst robot: {worst:+.3f}")


if __name__ == "__main__":
    main()
    print("PASS shot curve")
