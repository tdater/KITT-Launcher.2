#!/usr/bin/env bash
# One MediaCaptureTest group on the booted emulator (page-media.yml): $1 flavor (Kitt2/Kitt),
# $2 screen size, $3 test method, $4 output folder. Pulls whatever was captured, even on failure.
set -u
mkdir -p "raw/$4"
adb logcat -v time > "raw/$4/logcat-$3.txt" &
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true
adb shell wm size "$2" && adb shell wm density 160 && sleep 5
./gradlew "connected${1}DebugAndroidTest" "-Pandroid.testInstrumentationRunnerArguments.class=com.aiconsole.kittlauncher2.MediaCaptureTest#$3" ||
  echo "::warning::$1 $3 failed; keeping what was captured"
adb pull /data/local/tmp/media/. "raw/$4/" || true
ls -la "raw/$4"
