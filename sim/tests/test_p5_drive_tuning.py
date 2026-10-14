"""Program 5 (Drive Tuning) on the Driver Station only: Pedro's Mecanum, Pinpoint and Foresight tuners and a Line
test, with nothing but gamepad presses and the student's hands. No web page, no laptop, no build in between: each
part must use what the part before saved in the robot's results file. Every number must land near the truth, and
the Line test must drive 48 in straight on the Foresight numbers from the file."""
import os
import re
import time

from harness import running_sim

WHEEL = {"Front Left": 0, "Back Left": 1, "Front Right": 2, "Back Right": 3}
MEASURED = ["frontLeftDirection", "backLeftDirection", "frontRightDirection", "backRightDirection",
            "xPodOffset", "yPodOffset", "xPodDirection", "yPodDirection"]


def close(name, got, want, tol):
    ok = abs(got - want) <= tol
    print(f"  {name:32s} measured {got:9.4g}  truth {want:9.4g} +/- {tol:.3g}  {'ok' if ok else 'OFF'}")
    return ok


def screen(s):
    return s.telemetry()


def field_lines(t):
    """The question lines of an inputs screen: [(selected, name, value)]."""
    out = []
    for line in t.splitlines():
        m = re.match(r"^(> |  )(.+?): (.*)$", line)
        if m and not line.startswith("> Press"):
            out.append((m.group(1) == "> ", m.group(2), m.group(3)))
    return out


class Student:
    """Reads the Driver Station and answers like a careful student. on_step(name, phase) does the physical part:
    phase "before" (on the start screen) and "during" (while it runs, for steps that end on cross)."""

    def __init__(self, s, answers=None, on_step=None):
        self.s, self.answers, self.on_step = s, answers or {}, on_step or (lambda n, p: None)

    def run_part(self, number, timeout=600):
        s = self.s
        s.wait_text("pick a part", timeout=30)
        for _ in range(8):
            sel = re.search(r"^> (\d) ", screen(s), re.M)
            if sel and int(sel.group(1)) == number:
                break
            s.press("dpad_down" if not sel or int(sel.group(1)) < number else "dpad_up", hold=0.05)
            time.sleep(0.2)
        s.press("cross")
        end = time.time() + timeout
        while time.time() < end:
            t = screen(s)
            if "PART %d DONE" % number in t or "STOPPED:" in t:
                done = "PART %d DONE" % number in t
                s.press("cross")
                return done, t
            m = re.search(r"^NEXT: (.+)$", t, re.M)
            if m and "cross = start" in t:
                self.on_step(m.group(1), "before")
                s.press("cross")
                time.sleep(0.6)
                continue
            m = re.search(r"^NOW: (.+)$", t, re.M)
            if m and "cross = done" in t:
                self.on_step(m.group(1), "during")
                s.press("cross")
                time.sleep(0.6)
                continue
            if "cross = OK" in t:
                self.answer(t)
                continue
            if "cross = continue" in t:
                time.sleep(0.4)
                s.press("cross")
                time.sleep(0.4)
                continue
            time.sleep(0.2)
        raise TimeoutError(screen(s))

    def answer(self, t):
        """Fill the open questions: answers[name] is the wanted value text, else keep what is shown."""
        s = self.s
        fields = field_lines(t)
        for i, (_, name, value) in enumerate(fields):
            want = None
            for key, fn in self.answers.items():
                if key in name:
                    want = fn() if callable(fn) else fn
            if want is None and not value.startswith("?"):
                continue
            # select the line, then step right until it shows the wanted answer
            for _ in range(10):
                cur = field_lines(screen(s))
                sel = [j for j, f in enumerate(cur) if f[0]]
                if sel and sel[0] == i:
                    break
                s.press("dpad_down" if not sel or sel[0] < i else "dpad_up", hold=0.05)
                time.sleep(0.15)
            for _ in range(12):
                cur = field_lines(screen(s))
                if i < len(cur) and want is not None and cur[i][2].lower() == str(want).lower():
                    break
                if i < len(cur) and want is None and not cur[i][2].startswith("?"):
                    break
                s.press("dpad_right", hold=0.05)
                time.sleep(0.15)
        time.sleep(0.4)
        s.press("cross")
        time.sleep(0.6)


def run(s):
    s.preset_config()
    expected = s.get("expected")
    builds = s.get("loop/status")["builtAt"]
    s.hand("robot", "onBox")
    s.init("5 Drive Tuning")
    s.wait_text("Press START")
    s.start()

    # part 1: each wheel spins; watch it, answer which way
    seen = {}

    def wheels(name, phase):
        if phase == "during":
            time.sleep(1.2)
            seen["wheel"] = s.world()["drive"]["wheelRpm"][WHEEL[name]]

    st = Student(s, {"Which way": lambda: "forward" if seen["wheel"] > 0 else "reverse"}, wheels)
    done, t = st.run_part(1)
    assert done, t

    # part 2: on the floor; push forward, push left, spin half a turn
    s.hand("robot", "onFloor")
    s.hand("drive", "place", x=72, y=72, headingDeg=0, exact=True)

    def pods(name, phase):
        if phase != "during":
            return
        time.sleep(1.5)
        if name.startswith("Forward"):
            s.hand("drive", "push", forward=24, seconds=2)
        elif name.startswith("Strafe"):
            s.hand("drive", "push", left=24, seconds=2)
        else:
            s.hand("drive", "push", turnDeg=180, seconds=3)
        time.sleep(4)

    done, t = Student(s, on_step=pods).run_part(2)
    assert done, t
    saved = s.get("loop/status")["results"]
    good = True
    for k in MEASURED:
        v = saved[k]
        got = float(v) if k.endswith("Offset") else (1 if v.upper() == "FORWARD" else -1)
        good &= close(k, got, expected[k]["value"], expected[k]["tolerance"] if k.endswith("Offset") else 0.5)
    assert good, "Mecanum / Pinpoint results off truth"

    # part 3: Foresight drives itself; the student puts the robot where each run has room
    def room(name, phase):
        if phase == "before":
            low = name in ("Max Forward Velocity", "Max Strafe Velocity", "Forward Deceleration", "Strafe Deceleration")
            s.hand("drive", "place", x=30 if low else 72, y=30 if low else 72, headingDeg=0, careful=True)
            time.sleep(0.5)

    done, t = Student(s, on_step=room).run_part(3, timeout=900)
    assert done, t
    saved = s.get("loop/status")["results"]
    for k in ("maxAchievableForwardVelocity", "maxAchievableStrafeVelocity"):
        good &= close(k, float(saved[k]), expected[k]["value"], expected[k]["tolerance"])
    assert good, "Foresight velocities off truth"
    assert "brakeKV" in saved and "headingKP" in saved, saved

    # part 4: Line test on the numbers from the file
    moved = {}

    def line(name, phase):
        if phase == "before":
            s.hand("drive", "place", x=40, y=72, headingDeg=0, careful=True)
            time.sleep(0.5)
            moved["start"] = s.world()["drive"]
        else:
            start = moved["start"]
            moved["dx"], moved["dy"] = 0, 0
            end = time.time() + 7
            while time.time() < end:
                d = s.world()["drive"]
                moved["dx"] = max(moved["dx"], d["x"] - start["x"])
                moved["dy"] = max(moved["dy"], abs(d["y"] - start["y"]), key=abs)
                time.sleep(0.1)

    done, t = Student(s, {"Test": "line"}, line).run_part(4)
    assert done, t
    print("  line test moved", {k: v for k, v in moved.items() if k != "start"})
    assert 40 <= moved["dx"] <= 56 and abs(moved["dy"]) < 4, moved
    assert s.get("loop/status")["builtAt"] == builds, "a build happened"
    s.wait_text("Parts 1 to 3 done")
    s.stop()
    s.wait(lambda st: st["ds"]["active"] == "6 Position Check", 10, "program 6 auto-loaded")
    s.stop()

    # a wrong part 1 answer (one wheel flipped): part 3's drive check refuses before Foresight drives fast
    flipped = "FORWARD" if saved["frontLeftDirection"].upper() == "REVERSE" else "REVERSE"
    with open(os.path.join(s.get("loop/status")["paths"]["robot"], "biobuzz_results.csv"), "a") as f:
        f.write(f"5 Drive Tuning,frontLeftDirection,{flipped},{int(time.time() * 1000)}\n")
    s.hand("drive", "place", x=40, y=40, headingDeg=0, careful=True)
    s.init("5 Drive Tuning")
    s.wait_text("Press START")
    s.start()
    done, t = Student(s).run_part(3, timeout=60)
    assert not done and "Drive check" in t, t
    print("  one wheel flipped: " + next(l for l in t.splitlines() if "Drive check" in l))
    s.stop()


if __name__ == "__main__":
    with running_sim(seed=3) as s:
        run(s)
    print("PASS program 5 (Drive Tuning on the Driver Station)")
