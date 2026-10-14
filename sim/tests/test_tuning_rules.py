"""The decision rules of the setup programs' automatic parts, without the simulator: ShotCurve (the shot table as a
curve), FeedCheck (programs 8 / 9 feed pick) and FlywheelAutoTune (program 7's P search and idle pick). Plain Java with
no hardware, so they compile on their own; sim/tests/rules/TuningRules.java holds the cases."""
import os
import subprocess
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
TUNING = os.path.join(REPO, "TeamCode/src/main/java/org/firstinspires/ftc/teamcode/tuning")


def java_home():
    for v in ("21", "17"):
        try:
            return subprocess.check_output(["/usr/libexec/java_home", "-v", v], stderr=subprocess.DEVNULL).decode().strip()
        except (OSError, subprocess.CalledProcessError):
            continue
    return os.environ.get("JAVA_HOME", "")


def run():
    home = java_home()
    javac = os.path.join(home, "bin", "javac") if home else "javac"
    java = os.path.join(home, "bin", "java") if home else "java"
    sources = [os.path.join(TUNING, f) for f in ("FlywheelAutoTune.java", "FeedCheck.java")]
    sources.append(os.path.join(TUNING, "..", "subsystems", "ShotCurve.java"))
    sources.append(os.path.join(HERE, "rules", "TuningRules.java"))
    with tempfile.TemporaryDirectory() as out:
        subprocess.check_call([javac, "-nowarn", "-encoding", "UTF-8", "-d", out] + sources)
        result = subprocess.run([java, "-ea", "-cp", out, "TuningRules"], capture_output=True, text=True)
        print(result.stdout.strip())
        if result.returncode != 0:
            print(result.stderr.strip())
            raise SystemExit("FAIL tuning rules")


if __name__ == "__main__":
    run()
    print("PASS tuning rules")
