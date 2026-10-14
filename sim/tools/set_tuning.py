"""Enter measured values into a sandbox copy of the repo, the way the programmer does (prerequisites doc):
team-config.yaml entries get the value with origin: measured; the few settings that are code, not YAML
(motor/pod directions, reversed flags) are edited in place. Then render.sh regenerates the Java files.

usage: set_tuning.py <sandbox-root> '{"yawKs": 0.08, "Intake.REVERSED": true, "frontLeftDirection": "REVERSE"}'
"""
import json
import re
import subprocess
import sys

TC = "TeamCode/src/main/java/org/firstinspires/ftc/teamcode"
CODE_FLAGS = {
    "Intake.REVERSED": (f"{TC}/subsystems/Intake.java", r"(public static boolean REVERSED = )(true|false);"),
    "Launcher.FRONT_REVERSED": (f"{TC}/subsystems/Launcher.java", r"(public static boolean FRONT_REVERSED = )(true|false);"),
    "Launcher.BACK_REVERSED": (f"{TC}/subsystems/Launcher.java", r"(public static boolean BACK_REVERSED = )(true|false);"),
}
DIRECTIONS = {
    "frontLeftDirection": "DcMotorSimple.Direction", "backLeftDirection": "DcMotorSimple.Direction",
    "frontRightDirection": "DcMotorSimple.Direction", "backRightDirection": "DcMotorSimple.Direction",
    "xPodDirection": "GoBildaPinpointDriver.EncoderDirection", "yPodDirection": "GoBildaPinpointDriver.EncoderDirection",
}


def edit(path, fn):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    new = fn(text)
    with open(path, "w", encoding="utf-8") as f:
        f.write(new)


def set_values(root, values):
    for key, value in values.items():
        if key in CODE_FLAGS:
            path, pattern = CODE_FLAGS[key]
            v = "true" if value in (True, "true", "True", 1) else "false"
            def sub(t, pattern=pattern, v=v, key=key):
                t2, n = re.subn(pattern, lambda m: m.group(1) + v + ";", t)
                if n != 1:
                    raise SystemExit(f"{key}: expected one match in {path}")
                return t2
            edit(f"{root}/{path}", sub)
        elif key in DIRECTIONS:
            enum = DIRECTIONS[key]
            v = str(value).upper()
            def sub(t, key=key, enum=enum, v=v):
                t2, n = re.subn(rf"(public static {re.escape(enum)} {key} = {re.escape(enum)}\.)\w+(;)", lambda m: m.group(1) + v + m.group(2), t)
                if n != 1:
                    raise SystemExit(f"{key}: expected one match in Constants.java.tpl")
                return t2
            edit(f"{root}/tools/codegen/Constants.java.tpl", sub)
        else:
            if isinstance(value, bool):
                raise SystemExit(f"{key}: needs a number")
            # None clears an entry (value: null, back to untuned), e.g. shot table slots a shorter table no longer uses
            if value is None:
                num, origin = "null", "untuned"
            else:
                num = repr(float(value)) if not float(value).is_integer() or isinstance(value, float) else str(int(value))
                origin = "measured"
            def sub(t, key=key, num=num, origin=origin):
                pat = rf"(^\s+{re.escape(key)}: \{{value: )[^,]*(, origin: )\w+"
                t2, n = re.subn(pat, lambda m: m.group(1) + num + m.group(2) + origin, t, flags=re.M)
                if n != 1:
                    raise SystemExit(f"{key}: expected exactly one entry in team-config.yaml, found {n}")
                return t2
            edit(f"{root}/team-config.yaml", sub)


def main():
    root, values = sys.argv[1], json.loads(sys.argv[2])
    set_values(root, values)
    r = subprocess.run(["sh", f"{root}/tools/codegen/render.sh"], capture_output=True, text=True)
    sys.stdout.write(r.stdout + r.stderr)
    sys.exit(r.returncode)


if __name__ == "__main__":
    main()
