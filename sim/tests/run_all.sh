#!/bin/sh
# Runs every sim/tests/test_*.py (each starts its own headless simulator) and prints a summary.
# Logs: sim/work/tests/<name>.log
cd "$(dirname "$0")"
mkdir -p ../work/tests
pass=0; fail=0; failed=""
for t in test_*.py; do
    n=${t%.py}
    start=$(date +%s)
    if python3 "$t" > "../work/tests/$n.log" 2>&1; then
        pass=$((pass + 1)); r=PASS
    else
        fail=$((fail + 1)); r=FAIL; failed="$failed $n"
    fi
    echo "$r  $n  ($(( $(date +%s) - start )) s)"
done
echo "----"
echo "$pass passed, $fail failed${failed:+:$failed}"
[ "$fail" -eq 0 ]
