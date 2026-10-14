"""Test mode (simulator only): one click gives a fully built and tuned robot; ending it puts back exactly what was there
and leaves no test data anywhere. Some real work is done first (a value built into the code, a result and a points file
on the robot, one copy of the results), its files fingerprinted; in test mode the robot's own files are empty and the
auto can drive; after ending, every file in the robot code, on the robot and in the copied results is byte for byte the
same as before, and no checkpoint or build from test mode is left."""
import hashlib
import os
import time

from harness import running_sim


def fingerprint(root, skip=()):
    out = {}
    for d, _, files in os.walk(root):
        for f in files:
            p = os.path.join(d, f)
            rel = os.path.relpath(p, root)
            if any(rel.startswith(x) for x in skip):
                continue
            with open(p, "rb") as fh:
                out[rel] = hashlib.sha1(fh.read()).hexdigest()
    return out


def state(s):
    paths = s.get("loop/status")["paths"]
    work = os.path.dirname(paths["robot"].rstrip("/").rsplit("/FIRST", 1)[0])
    return work


def snapshot(work):
    return {
        "sandbox": fingerprint(os.path.join(work, "sandbox")),
        "robot": fingerprint(os.path.join(work, "robot-sdcard")),
        "outbox": fingerprint(os.path.join(work, "outbox")) if os.path.isdir(os.path.join(work, "outbox")) else {},
        "session": open(os.path.join(work, "session.json")).read(),
    }


def wait_mode(s, want, timeout=240):
    end = time.time() + timeout
    while time.time() < end:
        t = s.state()["sim"].get("testMode")
        if want is None and t is None:
            return
        if want is not None and t is not None and t["state"].startswith(want):
            return t
        if t is not None and t["state"].startswith("failed"):
            raise AssertionError(t["state"])
        time.sleep(1)
    raise AssertionError(f"test mode never got to {want}: {s.state()['sim'].get('testMode')}")


if __name__ == "__main__":
    with running_sim(seed=5) as s:
        # some real work: a CAD value built in, a result and a points file on the robot, one copy of the results
        s.preset_config()
        s.set_values({"centerToFront": 8.5, "centerToBack": 8.5})
        robot = s.get("loop/status")["paths"]["robot"]
        with open(os.path.join(robot, "biobuzz_results.csv"), "a") as f:
            f.write("program,key,value,time_ms\n2 Motor Directions,intakeReversed,true,1\n")
        with open(os.path.join(robot, "biobuzz_front_points.csv"), "w") as f:
            f.write("distance_in,speed,hood,feed,battery_v\n40.00,1500,0.600,0.50,12.50\n")
        s.post("loop/pull")
        work = state(s)
        before = snapshot(work)
        cps_before = sorted(c["name"] for c in s.get("checkpoints"))
        builds_before = sorted(os.listdir(os.path.join(work, "build")))
        assert before["robot"] and before["outbox"], before

        s.post("testmode/start", {})
        t = wait_mode(s, "ready")
        print("test mode:", t)
        assert not [f for f in os.listdir(robot) if f.startswith("biobuzz_")], os.listdir(robot)
        s.init("SAMPLE Auto Blue")
        time.sleep(3)
        tele = s.telemetry()
        assert "WILL NOT DRIVE" not in tele, tele
        print("in test mode the auto is ready to drive")
        s.stop()

        s.post("testmode/end", {})
        wait_mode(s, None)
        s.wait(lambda st: st["ds"]["robot"] is not None and st["ds"]["state"] == "STOPPED", 120, "robot restarted")
        after = snapshot(work)
        for part in ("sandbox", "robot", "outbox"):
            added = sorted(set(after[part]) - set(before[part]))
            gone = sorted(set(before[part]) - set(after[part]))
            changed = sorted(k for k in set(before[part]) & set(after[part]) if before[part][k] != after[part][k])
            assert not (added or gone or changed), (part, "added", added, "gone", gone, "changed", changed)
        assert after["session"] == before["session"]
        cps_after = sorted(c["name"] for c in s.get("checkpoints"))
        assert not [c for c in cps_after if c not in cps_before and "before test mode" in c], cps_after
        leftover = [c for c in cps_after if c not in cps_before and not c.endswith("build installed")]
        assert not leftover, leftover
        # only the one build made on the way back may be new
        new_builds = sorted(set(os.listdir(os.path.join(work, "build"))) - set(builds_before))
        assert len(new_builds) <= 1, new_builds
        assert not os.path.exists(os.path.join(work, "test-mode.json"))
        print(f"after ending: {sum(len(before[p]) for p in ('sandbox', 'robot', 'outbox'))} files byte for byte as before, "
              f"no test checkpoints, {len(new_builds)} new build (the one back to your code)")
    print("PASS test mode")
