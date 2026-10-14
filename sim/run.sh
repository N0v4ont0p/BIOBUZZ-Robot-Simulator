#!/bin/sh
# Starts the simulator and opens it in the browser. Options: --scenario <file> --seed <n> --keep --port <n> --no-browser
set -e
cd "$(dirname "$0")"
[ -f build/classes/org/biobuzz/sim/Main.class ] || sh build.sh
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)}
[ -n "$JAVA_HOME" ] && PATH="$JAVA_HOME/bin:$PATH"
CP="build/classes:build/jars/*:$(cat build/android-jar.txt)"
exec java $SIM_JAVA_OPTS -Dsim.dir="$PWD" -Djava.library.path=build/native -Djava.awt.headless=true -cp "$CP" org.biobuzz.sim.Main "$@"
