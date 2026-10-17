"""Program 4 on the servo fault robots.

C. One slow gate: with the positions already entered, the slow-motion video shows the back gate is the slower one,
and the frame count matches its true travel time.
C. Back gate servo 2 a spline off: servo 2's positions are nowhere near 1 - servo 1's, so copying servo 1 mirrored would
make them fight; matched by ear it agrees with servo 1 (neither buzzes) and lands in its own good band.
"""
import time

from harness import running_sim
from test_p4_servos import (KEYS, both_check, frames_to_open, gate_closed, gate_open, match_second, ramp,                             ramp_run, creeps, results, enter_frames, tap)

GATE_KEYS = KEYS[:6]


def program4(s, values):
    s.preset_config()
    values = dict(values)
    values["Ramp.RAMP_REVERSED"] = s.get("expected")["rampReversed"]["value"] == 1
    s.set_values(values)
    s.init("4 Servo Positions")
    s.wait_text("Press START")
    s.start()


def skip_gates(s):
    """The gate positions are already in: each step, square to see it, cross."""
    for i in range(len(GATE_KEYS)):
        s.wait_text(f"STEP {i + 1} of 10")
        tap(s, "square")
        time.sleep(0.3)
        tap(s, "cross")
    both_check(s)


def slow_gate(s):
    exp = s.get("expected")
    program4(s, {k: round(exp[k]["value"], 3) for k in GATE_KEYS})
    skip_gates(s)
    s.wait_text("Gate travel time")
    time.sleep(0.8)
    s.hand("servos", "record", on=True)
    time.sleep(0.3)
    tap(s, "square")
    time.sleep(0.8)
    s.hand("servos", "record", on=False)
    clip = s.get("slowmo")
    front, _ = frames_to_open(clip, "gate.front")
    back, _ = frames_to_open(clip, "gate.back")
    back2, _ = frames_to_open(clip, "gate.back2")
    assert back > front + 10, (front, back)
    enter_frames(s, max(front, back, back2))
    ramp_run(s, "Ramp: time out", True)
    ramp_run(s, "Ramp: time in", False)
    s.wait_text("PROGRAM 4 COMPLETE")
    e = s.get("expected")["gateTravelS"]
    got = results(s)["gateTravelS"]
    print(f"  front {front} frames, back {back} / {back2} frames; recorded {got} s, true {e['value']:.4f} +- {e['tolerance']:.4f}")
    assert "gate.back" in e["how"], e["how"]
    assert abs(got - e["value"]) <= e["tolerance"], (got, e)
    s.stop()


def spline_off(s):
    program4(s, {})
    d = gate_closed(s, 0, "gate.front", "Front gate: closed")
    gate_open(s, 0, "gate.front", "Front gate: open", d)
    d = gate_closed(s, 1, "gate.back", "Back gate servo 1: closed")
    # the program starts servo 2 at 1 - servo 1 (mirrored): on this robot that is far off, both buzz hard
    tap(s, "square")
    time.sleep(0.6)
    sv = s.world()["servos"]
    assert sv["gate.back2"]["buzz"] > 0.5 and sv["gate.back"]["buzz"] > 0.5, (sv["gate.back"], sv["gate.back2"])
    match_second(s, "Back gate servo 2: closed")
    gate_open(s, 1, "gate.back", "Back gate servo 1: open", d)
    match_second(s, "Back gate servo 2: open")
    both_check(s)
    got, exp = results(s), s.get("expected")
    for k in ("backGate2Closed", "backGate2Open"):
        e = exp[k]
        assert abs(got[k] - e["value"]) <= e["tolerance"] + 0.0006, (k, got[k], e)
    off = abs(got["backGate2Closed"] - (1 - got["backGateClosed"]))
    print(f"  servo 2 closed {got['backGate2Closed']:.3f}, 1 - servo 1 = {1 - got['backGateClosed']:.3f}: {off:.3f} apart; both quiet")
    assert off > 0.05, off


if __name__ == "__main__":
    with running_sim("C-servo-slow-gate.json", seed=4) as s:
        slow_gate(s)
    print("PASS one slow gate")
    with running_sim("C-backgate-spline-off.json", seed=3) as s:
        spline_off(s)
    print("PASS back gate servo 2 a spline off")
