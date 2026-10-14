#!/bin/sh
# Regenerates the three generated files from team-config.yaml. Needs the refract-suite plugin installed.
# Run after editing device_map or tuning_constants (a measured value: origin: measured, confirmed: true).
set -e
cd "$(dirname "$0")/../.."
E=$(ls -d "$HOME"/.claude/plugins/cache/refract/refract-suite/*/skills/ftc-construct/scripts/emit_tuning.py | sort -V | tail -1)
T=TeamCode/src/main/java/org/firstinspires/ftc/teamcode
python3 "$E" render tools/codegen/DeviceNames.java.tpl --config team-config.yaml -o $T/config/DeviceNames.java > /dev/null
python3 "$E" render tools/codegen/Tuned.java.tpl --config team-config.yaml -o $T/config/Tuned.java > /dev/null
python3 "$E" render tools/codegen/Constants.java.tpl --config team-config.yaml -o $T/pedro/Constants.java > /dev/null
# hits inside Pedro's untouched vendor tuners (pedro/procedures) are expected; any other hit fails
python3 "$E" verify $T --config team-config.yaml | python3 -c '
import json, sys
bad = [v for v in json.load(sys.stdin)["violations"] if not v.get("file", "").startswith(sys.argv[1])]
for v in bad:
    print("verify:", v.get("file", ""), v.get("line", ""), v.get("field", ""), v.get("literal", ""), "-", v["reason"])
sys.exit(1 if bad else 0)' "$T/pedro/procedures/"
echo "rendered"
