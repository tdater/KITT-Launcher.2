#!/usr/bin/env bash
# Diagnostic run of the smoke test for one edition: streams logcat, caps the run, and on a hang
# dumps the app's thread stacks, the activity state and a screenshot. Not part of the build.
set -u
pkg="$1"; flavor="$2"; size="$3"
mkdir -p diag
adb shell wm size "$size"
adb shell wm density 160
adb logcat -c
adb logcat -v threadtime > diag/logcat.txt 2>&1 &
logpid=$!
chmod +x gradlew
timeout 1200 ./gradlew "connected${flavor}DebugAndroidTest" -Pandroid.testInstrumentationRunnerArguments.class=com.aiconsole.kittlauncher2.LauncherSmokeTest
status=$?
echo "gradle exit: $status" | tee diag/status.txt
pid=$(adb shell pidof "$pkg" | tr -d '\r')
echo "app pid: $pid" | tee -a diag/status.txt
adb exec-out screencap -p > diag/screen.png || true
adb shell dumpsys activity activities > diag/activities.txt 2>&1 || true
adb shell dumpsys window windows > diag/windows.txt 2>&1 || true
if [ -n "$pid" ]; then
  adb shell kill -3 "$pid" || true
  sleep 8
  adb root >/dev/null 2>&1 || true
  sleep 3
  adb pull /data/anr diag/anr >/dev/null 2>&1 || true
fi
kill $logpid 2>/dev/null || true
exit $status
