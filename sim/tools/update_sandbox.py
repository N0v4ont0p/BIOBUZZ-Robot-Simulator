"""Bring a sandbox up to the repo's robot code, keeping every number entered in it: the measured team-config.yaml
values, the reversed flags in code, and the Pedro directions. Then render.sh (the caller builds and installs).

    update_sandbox.py <repo-root> <sandbox-root>

Prints JSON: kept (key -> value), dropped (keys the new code no longer has).
"""
import json
import os
import re
import shutil
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import set_tuning  # noqa: E402

TC = "TeamCode/src/main/java"


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def measured_yaml(root):
    """team-config.yaml entries with origin: measured, as numbers."""
    out = {}
    for m in re.finditer(r"^\s+(\w+): \{value: ([^,]*), origin: measured", read(os.path.join(root, "team-config.yaml")), re.M):
        try:
            out[m.group(1)] = float(m.group(2))
        except ValueError:
            pass
    return out


def code_settings(root):
    """The reversed flags and the Pedro directions, in the old or the new Constants template layout."""
    out = {}
    for key, (path, pattern) in set_tuning.CODE_FLAGS.items():
        m = re.search(pattern, read(os.path.join(root, path)))
        if m:
            out[key] = m.group(2) == "true"
    tpl = read(os.path.join(root, "tools/codegen/Constants.java.tpl"))
    for key, enum in set_tuning.DIRECTIONS.items():
        m = re.search(rf"{key} = {re.escape(enum)}\.(\w+);", tpl) or re.search(rf"c\.{key}\.set\({re.escape(enum)}\.(\w+)\)", tpl)
        if m:
            out[key] = m.group(1)
    return out


def main():
    repo, sandbox = sys.argv[1], sys.argv[2]
    values = measured_yaml(sandbox)
    values.update(code_settings(sandbox))
    shutil.rmtree(os.path.join(sandbox, TC), ignore_errors=True)
    shutil.copytree(os.path.join(repo, TC), os.path.join(sandbox, TC))
    shutil.rmtree(os.path.join(sandbox, "tools/codegen"), ignore_errors=True)
    shutil.copytree(os.path.join(repo, "tools/codegen"), os.path.join(sandbox, "tools/codegen"))
    shutil.copy(os.path.join(repo, "team-config.yaml"), os.path.join(sandbox, "team-config.yaml"))
    kept, dropped = {}, []
    for k, v in values.items():
        try:
            set_tuning.set_values(sandbox, {k: v})
            kept[k] = v
        except SystemExit:
            dropped.append(k)
    r = subprocess.run(["sh", os.path.join(sandbox, "tools/codegen/render.sh")], capture_output=True, text=True)
    print(json.dumps({"kept": kept, "dropped": dropped, "render": r.stdout + r.stderr, "ok": r.returncode == 0}))
    sys.exit(r.returncode)


if __name__ == "__main__":
    main()
