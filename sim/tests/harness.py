"""Starts a headless simulator for a test and gives back a simctl client. Use a context manager:

    from harness import running_sim
    with running_sim("A-perfect-robot.json", seed=3) as s:
        s.preset_config()
        ...
"""
import contextlib
import os
import socket
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SIM = os.path.dirname(HERE)
sys.path.insert(0, os.path.join(SIM, "tools"))
from simctl import Sim  # noqa: E402


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


@contextlib.contextmanager
def running_sim(scenario="A-perfect-robot.json", seed=1, pedro=False, log=None):
    if not os.path.exists(os.path.join(SIM, "build/classes/org/biobuzz/sim/Main.class")):
        subprocess.check_call(["sh", os.path.join(SIM, "build.sh")])
    port = free_port()
    args = ["sh", os.path.join(SIM, "run.sh"), "--no-browser", "--port", str(port), "--scenario", scenario, "--seed", str(seed)]
    if not pedro:
        args.append("--no-pedro")
    # every test gets its own work dir so tests can run side by side
    os.makedirs(os.path.join(SIM, "work"), exist_ok=True)
    env = dict(os.environ, SIM_WORK=os.path.join(SIM, "work", f"test-{port}"))
    os.makedirs(os.path.join(SIM, "work"), exist_ok=True)
    out = open(log or os.path.join(SIM, "work", f"test-{port}.log"), "w")
    p = subprocess.Popen(args, stdout=out, stderr=subprocess.STDOUT, env=env)
    s = Sim(f"http://127.0.0.1:{port}")
    try:
        end = time.time() + 90
        while True:
            try:
                st = s.state()
                if st["ds"]["robot"] is not None:
                    break
            except Exception:
                pass
            if time.time() > end or p.poll() is not None:
                raise RuntimeError("simulator did not start; see " + out.name)
            time.sleep(0.5)
        yield s
    finally:
        p.terminate()
        try:
            p.wait(10)
        except subprocess.TimeoutExpired:
            p.kill()
        out.close()
