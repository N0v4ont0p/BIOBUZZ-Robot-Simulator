"""Program 4 on the two servo fault robots.

C. Hood binds early: commanded past the bind, the front hood stops early, buzzes, heats up and finally burns out;
a replacement servo works again (and sits a part-tooth off on its spline).
C. One slow gate: with the positions already entered, the slow-motion video shows the back gate is the slower one,
and the frame count matches its true travel time.
"""
import time

from harness import running_sim
from test_p4_servos import BIG, direction, frames_to_open, ramp_travel, results, servo, step_until, tap

SERVO_KEYS = ("frontHoodMin", "frontHoodMax", "backHoodMin", "backHoodMax", "rampStowed", "rampDeployed",
              "frontGateClosed", "frontGateOpen", "backGateClosed", "backGateOpen")


def hood_binds(s):
    s.preset_config()
    exp = s.get("expected")
    truth = s.get("truth")
    assert truth["hood.front.highStopDeg"] < 43, truth["hood.front.highStopDeg"]
    s.init("4 Servo Positions")
    s.wait_text("Press START")
    s.start()
    s.wait_text("Front hood: lowest")
    tap(s, "square")
    time.sleep(0.4)
    d = direction(s, "hood.front")
    st = step_until(s, "hood.front", BIG[d], lambda st: st["stalled"], what="the bind")
    assert st["out"] < 43, st
    for _ in range(4):
        tap(s, BIG[d])
    time.sleep(0.5)
    a = servo(s, "hood.front")
    time.sleep(2)
    b = servo(s, "hood.front")
    assert a["stalled"] and b["stalled"] and b["buzz"] > 0.9 and b["amps"] > 1.5, b
    assert b["heat"] > a["heat"] + 0.05, (a["heat"], b["heat"])
    assert abs(b["out"] - st["out"]) < 0.5, "the hood must not move past the bind"
    s.wait(lambda x: x["world"]["servos"]["hood.front"]["burnedOut"], 40, "servo burns out")
    dead = servo(s, "hood.front")
    assert not dead["powered"] and dead["amps"] == 0 and dead["feel"].startswith("dead"), dead
    s.hand("servos", "replaceServo", id="hood.front")
    time.sleep(0.3)
    new = servo(s, "hood.front")
    assert not new["burnedOut"] and new["replaced"] == 1 and new["heat"] < 0.1, new
    # still told to go past the bind: the new servo buzzes too, until the student backs off
    s.wait(lambda x: x["world"]["servos"]["hood.front"]["stalled"], 3, "new servo buzzes")
    step_until(s, "hood.front", BIG[-d], lambda st: not st["stalled"], limit=20, what="quiet")
    for _ in range(2):
        tap(s, BIG[-d])
    time.sleep(0.3)
    assert not servo(s, "hood.front")["stalled"]
    after = s.get("expected")
    print(f"  bind at {st['out']:.1f} deg; frontHoodMax {exp['frontHoodMax']['value']:.3f} -> "
          f"{after['frontHoodMax']['value']:.3f} after the replacement (spline offset)")


def slow_gate(s):
    s.preset_config()
    exp = s.get("expected")
    s.set_values({k: round(exp[k]["value"], 3) for k in SERVO_KEYS})
    s.init("4 Servo Positions")
    s.wait_text("Press START")
    s.start()
    for i in range(len(SERVO_KEYS)):
        s.wait_text(f"STEP {i + 1} of 12")
        tap(s, "square")
        time.sleep(0.3)
        tap(s, "cross")
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
    assert back > front + 10, (front, back)
    for _ in range(back // 10):
        tap(s, "right_bumper")
    for _ in range(back % 10):
        tap(s, "dpad_up")
    s.wait_text(f"Frames: {back} ")
    tap(s, "cross")
    ramp_travel(s)
    s.wait_text("PROGRAM 4 COMPLETE")
    e = s.get("expected")["gateTravelS"]
    got = results(s)["gateTravelS"]
    print(f"  front {front} frames, back {back} frames; recorded {got} s, true {e['value']:.4f} +- {e['tolerance']:.4f}")
    assert "back gate is slower" in e["how"], e["how"]
    assert abs(got - e["value"]) <= e["tolerance"], (got, e)
    s.stop()


if __name__ == "__main__":
    with running_sim("C-servo-hood-binds.json", seed=3) as s:
        hood_binds(s)
    print("PASS hood binds early")
    with running_sim("C-servo-slow-gate.json", seed=4) as s:
        slow_gate(s)
    print("PASS one slow gate")
