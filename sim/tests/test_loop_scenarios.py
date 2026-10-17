"""Scenario mechanics: B wiring faults on the report card (and a battery swap counts as caught), B2's teammate
config with wrong names, E's hidden-robot "set" values, F's random faults (same seed = same faults)."""
import json

from harness import running_sim


def faults(s):
    return s.get("loop/report")["faults"]


def run(s):
    f = faults(s)
    assert len(f) == 3 and not any(x.get("caught") for x in f), f
    assert "left-back wheel motor" in f[0]["text"], f[0]
    assert "front flywheel" in f[1]["text"] and "program 1 step 3" in f[1]["caughtBy"].lower(), f[1]
    s.hand("robot", "swapBattery")
    assert faults(s)[2]["caught"] is True, "a swapped battery counts as caught"

    s.scenario("B2-wrong-names.json", seed=1)
    assert s.state()["sim"]["activeConfig"] == "BIOBUZZ"
    cfg = json.dumps(s.get("config/get?name=BIOBUZZ"))
    assert '"pollen"' in cfg and '"bgate2"' in cfg and '"pfeed"' not in cfg, cfg
    f = faults(s)
    assert len(f) == 2 and all(x.get("caught") is False for x in f) and '"pfeed"' in f[0]["text"], f

    s.scenario("E-human-error.json", seed=1)
    truth = s.get("truth")
    assert truth["battery.charge"] == 0.35, truth
    assert [x.get("caught") for x in faults(s)] == [False]

    picks = []
    for seed in (1, 1, 2, 3):
        s.scenario("F-random.json", seed=seed)
        f = faults(s)
        assert len(f) == 2, f
        picks.append([x["text"] for x in f])
    assert picks[0] == picks[1], "same seed, same faults"
    assert len({tuple(p) for p in picks}) > 1, "different seeds should pick different faults"


if __name__ == "__main__":
    with running_sim("B-wiring-day.json", seed=1) as s:
        run(s)
    print("PASS scenarios")
