"""Results by photo: program 2 ends with a RESULTS block on the Driver Station; pasting that text gives the same
values as the results file (adb pull), and coach mode enters them. Also the CAD sheet entry that program 6 needs."""
import re

import test_p2_directions
from harness import running_sim


def block(s):
    lines = [re.sub(r"<[^>]*>", "", l).replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&") for l in s.telemetry().split("\n")]
    assert any(l.startswith("RESULTS P2") for l in lines) and "END P2" in lines, lines
    return "\n".join(lines)


if __name__ == "__main__":
    with running_sim(seed=2) as s:
        exp = test_p2_directions.run(s)
        got = test_p2_directions.check(s, exp)
        text = block(s)
        for k, v in got.items():
            assert f"{k} = {v}" in text, (k, v, text)
        r = s.post("loop/paste", {"text": text})
        assert r["count"] == len(got) and r["programs"] == ["2 Motor Directions"], r
        plan = s.get("loop/plan")
        assert plan["values"] == {"Intake.REVERSED": True, "Launcher.FRONT_REVERSED": True, "Launcher.BACK_REVERSED": True}, plan
        print("photo plan:", plan["values"])
        s.stop()
        r = s.post("loop/coach")
        assert r["ok"] and r["installed"], r

        cad = s.get("expected")
        s.set_values({k: round(cad[k]["value"], 2) for k in ("centerToFront", "centerToBack", "centerToLeft", "centerToRight")})
        yaml = s.get("loop/status")["yaml"]
        assert all(k in yaml for k in ("centerToFront", "centerToRight")), yaml
    print("PASS results by photo")
