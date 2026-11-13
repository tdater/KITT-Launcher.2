#!/usr/bin/env bash
# Times the smoke test for one edition at one screen size, with no sampling. Not part of the build.
set -u
flavor="$1"; size="$2"
adb shell wm size "$size"
adb shell wm density 160
chmod +x gradlew
./gradlew "assemble${flavor}Debug" "assemble${flavor}DebugAndroidTest" -q
start=$(date +%s)
timeout 1500 ./gradlew "connected${flavor}DebugAndroidTest" -Pandroid.testInstrumentationRunnerArguments.class=com.aiconsole.kittlauncher2.LauncherSmokeTest
status=$?
echo "=== SMOKE ${flavor} ${size}: exit ${status}, $(( $(date +%s) - start ))s for connected${flavor}DebugAndroidTest"
exit $status
