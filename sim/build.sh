#!/bin/sh
# Builds the simulator. The robot code (TeamCode) is not compiled here: the simulator compiles it itself when it
# starts and on every "install new build", from its sandbox copy of the repo.
# Needs: the robot app built once in Android Studio (so the FTC SDK and Pedro are in the Gradle cache), JDK 17+,
# the Android SDK (android.jar), python3, cc.
set -e
cd "$(dirname "$0")"
B=build
G="$HOME/.gradle/caches/modules-2/files-2.1"

JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)}
[ -n "$JAVA_HOME" ] && PATH="$JAVA_HOME/bin:$PATH"
javac -version 2>&1 | grep -qE 'javac (1[7-9]|2[0-9])' || { echo "Need JDK 17 or newer (javac)"; exit 1; }

SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
ANDROID_JAR=$(ls -d "$SDK"/platforms/android-3[0-9]/android.jar 2>/dev/null | sort -V | tail -1)
[ -f "$ANDROID_JAR" ] || { echo "Need the Android SDK (android.jar under $SDK/platforms)"; exit 1; }

find_one() {
    f=$(find "$G/$1" -name "$2" 2>/dev/null | head -1)
    [ -n "$f" ] || { echo "Missing $2 in the Gradle cache. Build the robot app once in Android Studio."; exit 1; }
    echo "$f"
}

rm -rf $B/jars $B/aar $B/gen $B/classes $B/native $B/pedro-web
mkdir -p $B/jars $B/aar $B/gen $B/classes $B/native $B/pedro-web

for a in RobotCore Hardware FtcCommon RobotServer Inspection Vision; do
    f=$(find_one "org.firstinspires.ftc/$a/12.0.0" "$a-12.0.0.aar")
    mkdir -p $B/aar/$a
    (cd $B/aar/$a && unzip -q -o "$f" classes.jar R.txt AndroidManifest.xml 'res/values/*' 'libs/*' 2>/dev/null || true)
    mv $B/aar/$a/classes.jar $B/jars/$a.jar
    [ -d $B/aar/$a/libs ] && cp $B/aar/$a/libs/*.jar $B/jars/
done
for a in revhub:3.0.1 tuning:1.0.1; do
    n=${a%%:*}; v=${a##*:}
    f=$(find_one "com.pedropathing/$n/$v" "$n-$v.aar")
    mkdir -p $B/aar/pedro-$n
    (cd $B/aar/pedro-$n && unzip -q -o "$f")
    mv $B/aar/pedro-$n/classes.jar $B/jars/pedro-$n.jar
done
cp -R $B/aar/pedro-tuning/assets/pedro/. $B/pedro-web/
cp "$(find_one com.pedropathing/core/3.0.1 core-3.0.1.jar)" $B/jars/pedro-core.jar
cp "$(find_one org.nanohttpd/nanohttpd-websocket/2.3.1 nanohttpd-websocket-2.3.1.jar)" $B/jars/
cp "$(find_one org.jetbrains.kotlin/kotlin-stdlib/2.2.0 kotlin-stdlib-2.2.0.jar)" $B/jars/
cp "$(find_one org.threeten/threetenbp/1.5.1 threetenbp-1.5.1-no-tzdb.jar)" $B/jars/
cp "$(find_one com.aventrix.jnanoid/jnanoid/2.0.0 jnanoid-2.0.0.jar)" $B/jars/
rm -f $B/jars/slf4j-handroid-*.jar

python3 tools/genR.py $B/aar $B/gen $B/classes/res

CP="$B/jars/*:$ANDROID_JAR"
find shim src $B/gen -name '*.java' > $B/sources.txt
javac -nowarn -encoding UTF-8 --release 17 -d $B/classes -cp "$CP" @$B/sources.txt 2>&1 | grep -v '^Note:' || true
[ -f $B/classes/org/biobuzz/sim/Main.class ] || { echo "Simulator compile failed"; exit 1; }

case "$(uname)" in
    Darwin) cc -dynamiclib -o $B/native/libRobotCore.dylib tools/empty.c ;;
    *) cc -shared -fPIC -o $B/native/libRobotCore.so tools/empty.c ;;
esac
echo "$ANDROID_JAR" > $B/android-jar.txt
echo "built"
