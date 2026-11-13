#!/usr/bin/env bash
# Diagnostic run of the smoke test for one edition at one screen size. While the test runs, samples
# frame stats, CPU and the main and instrumentation thread stacks into the job log. Not part of the build.
set -u
pkg="$1"; flavor="$2"; size="$3"
adb root >/dev/null 2>&1; sleep 3
adb shell wm size "$size"
adb shell wm density 160
adb logcat -c
adb logcat -v threadtime > /tmp/logcat.txt 2>&1 &
logpid=$!

sample() {
  local n=0
  while true; do
    sleep 25
    n=$((n+1))
    pid=$(adb shell pidof "$pkg" 2>/dev/null | tr -d '\r')
    [ -z "$pid" ] && { echo "=== sample $n $(date -u +%T): app not running"; continue; }
    echo "=== sample $n $(date -u +%T) pid $pid"
    adb shell dumpsys gfxinfo "$pkg" | sed -n '/Total frames rendered/,/Number Frame deadline missed/p'
    adb shell top -b -n 1 -H -p "$pid" | head -14
    if [ $((n % 2)) -eq 1 ]; then
      adb shell 'rm -f /data/anr/*' >/dev/null 2>&1
      adb shell kill -3 "$pid"; sleep 4
      f=$(adb shell 'ls -t /data/anr/ 2>/dev/null | head -1' | tr -d '\r')
      if [ -n "$f" ]; then
        adb shell cat "/data/anr/$f" > /tmp/trace.txt
        echo "--- main thread"
        awk '/^"main"/{p=1} p&&/^$/{exit} p' /tmp/trace.txt | head -40
        echo "--- instrumentation thread"
        awk '/^"Instr: /{p=1} p&&/^$/{exit} p' /tmp/trace.txt | head -30
      fi
    fi
  done
}
sample &
samplepid=$!

chmod +x gradlew
start=$(date +%s)
timeout 1500 ./gradlew "connected${flavor}DebugAndroidTest" -Pandroid.testInstrumentationRunnerArguments.class=com.aiconsole.kittlauncher2.LauncherSmokeTest
status=$?
echo "=== gradle exit $status after $(( $(date +%s) - start ))s"
kill $samplepid 2>/dev/null
kill $logpid 2>/dev/null
echo "=== logcat (filtered)"
grep -E "TestRunner|Davey|Choreographer|Displayed|FATAL|AndroidRuntime|ActivityScenario|Skipped" /tmp/logcat.txt | head -300
echo "=== app log lines by tag (top 25)"
awk '{print $6}' /tmp/logcat.txt | sort | uniq -c | sort -rn | head -25
exit $status
