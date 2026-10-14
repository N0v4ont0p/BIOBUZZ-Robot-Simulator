"""Drive a running simulator from Python: the scripted "student" used by the program tests (sim/tests/*.py).

    from simctl import Sim
    s = Sim()                      # http://localhost:8080
    s.scenario("A-perfect-robot.json", seed=1)
    s.preset_config()
    s.run_program("1 Check Devices")
    s.wait_text("STEP 1 of 4")
    s.press("cross")
"""
import json
import re
import time
import urllib.request

BUTTONS = ["cross", "circle", "square", "triangle", "share", "options", "ps", "touchpad", "dpad_up", "dpad_down",
           "dpad_left", "dpad_right", "left_bumper", "right_bumper", "left_stick_button", "right_stick_button"]
AXES = ["left_stick_x", "left_stick_y", "right_stick_x", "right_stick_y", "left_trigger", "right_trigger"]


def strip_html(s):
    s = re.sub(r"<[^>]+>", "", s or "")
    return s.replace("&gt;", ">").replace("&lt;", "<").replace("&amp;", "&")


class Sim:
    def __init__(self, url="http://localhost:8080"):
        self.url = url.rstrip("/")
        self.pad = {k: False for k in BUTTONS}
        self.pad.update({k: 0.0 for k in AXES})

    def post(self, path, body=None):
        req = urllib.request.Request(self.url + "/api/" + path, data=json.dumps(body or {}).encode(),
                                     headers={"Content-Type": "application/json"}, method="POST")
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.loads(r.read().decode() or "null")
        except urllib.error.HTTPError as e:
            raise RuntimeError(f"{path}: {e.read().decode()}")

    def get(self, path):
        with urllib.request.urlopen(self.url + "/api/" + path, timeout=60) as r:
            return json.loads(r.read().decode())

    def state(self):
        """One frame of the event stream: {"ds": ..., "world": ..., "sim": ...}."""
        with urllib.request.urlopen(self.url + "/api/stream", timeout=10) as r:
            line = r.readline().decode()
            while not line.startswith("data: "):
                if not line:
                    raise ConnectionError("event stream closed")
                line = r.readline().decode()
            return json.loads(line[6:])

    def telemetry(self):
        return "\n".join(strip_html(l) for l in self.state()["ds"]["telemetry"])

    def wait(self, pred, timeout=20.0, what="condition"):
        end = time.time() + timeout
        last = None
        while time.time() < end:
            try:
                last = self.state()
            except (ConnectionError, OSError):
                time.sleep(0.3)
                continue
            if pred(last):
                return last
            time.sleep(0.1)
        tele = "\n".join(strip_html(l) for l in (last or {}).get("ds", {}).get("telemetry", []))
        raise TimeoutError(f"timed out waiting for {what}\n--- telemetry ---\n{tele}\n--- ds ---\n"
                           f"{json.dumps({k: v for k, v in (last or {}).get('ds', {}).items() if k != 'telemetry'})[:1500]}")

    def wait_text(self, text, timeout=20.0):
        return self.wait(lambda s: text in "\n".join(strip_html(l) for l in s["ds"]["telemetry"]), timeout, repr(text))

    # ---- setup

    def scenario(self, file, seed=1, fresh=True):
        self.post("scenario", {"file": file, "seed": seed, "fresh": fresh})
        self.wait(lambda s: s["ds"]["robot"] is not None, 60, "robot controller")

    def preset_config(self, name="BIOBUZZ"):
        self.post("config/preset", {"name": name})
        self.post("config/activate", {"name": name})
        self.wait(lambda s: s["sim"]["activeConfig"] == name, 30, "config active")

    def set_values(self, values, build=True):
        """Programmer step on the sandbox: values into team-config.yaml / code settings, render, build, install."""
        r = self.post("sandbox/set", {"values": values, "build": build})
        self.wait(lambda s: s["ds"]["robot"] is not None and s["ds"]["state"] == "STOPPED", 60, "robot restarted")
        return r

    # ---- driver station

    def send_pad(self):
        self.post("gamepad", {"user1": self.pad, "user2": None})

    def press(self, button, hold=0.15):
        self.pad[button] = True
        self.send_pad()
        time.sleep(hold)
        self.pad[button] = False
        self.send_pad()
        time.sleep(0.15)

    def hold(self, button, value=True):
        if button in AXES:
            self.pad[button] = float(value)
        else:
            self.pad[button] = bool(value)
        self.send_pad()

    def select(self, name):
        self.post("ds/select", {"name": name})

    def init(self, name=None):
        if name:
            self.select(name)
        self.post("ds/init")
        self.wait(lambda s: s["ds"]["state"] == "INIT" and (name is None or s["ds"]["active"] == name), 20, f"INIT {name}")

    def start(self):
        self.post("ds/start")
        self.wait(lambda s: s["ds"]["state"] == "RUNNING", 20, "RUNNING")

    def stop(self):
        self.post("ds/stop")
        self.wait(lambda s: s["ds"]["state"] == "STOPPED", 20, "STOPPED")

    def run_program(self, name):
        self.init(name)
        self.start()

    # ---- hands

    def hand(self, part, action, **args):
        return self.post("hand", {"part": part, "action": action, "args": args})

    def world(self):
        return self.state()["world"]

    def file(self, name):
        return self.get("file?name=" + urllib.parse.quote(name))["text"]


import urllib.parse  # noqa: E402
