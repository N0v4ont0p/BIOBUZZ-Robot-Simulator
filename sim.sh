#!/bin/bash
# Builds and runs the simulator at http://localhost:8080. Runs while this terminal stays open; Ctrl+C stops it.
# Picks up where you left off; ./sim.sh --fresh for a new robot. Options pass through to sim/run.sh (--scenario --seed --port).
cd "$(dirname "$0")/sim" || exit 1
old=$(lsof -ti tcp:8080 -sTCP:LISTEN 2>/dev/null)
if [ -n "$old" ]; then echo "stopping the simulator already on port 8080"; kill $old; sleep 1; fi
./build.sh && exec ./run.sh --no-browser "$@"
