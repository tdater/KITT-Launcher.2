#!/usr/bin/env bash
# Runs MediaCaptureTest on the booted emulator (page-media.yml): stills for both editions first,
# each pulled straight away, then the clips, so a recorder crash can't lose the stills.
set -u
mkdir -p raw/kitt2 raw/kitt
adb logcat -v time > raw/logcat.txt &
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0930
adb shell am broadcast -a com.android.systemui.demo -e command notifications -e visible false
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true

T=com.aiconsole.kittlauncher2.MediaCaptureTest
run() { # flavor size method outdir
  adb shell wm size "$2" && adb shell wm density 160 && sleep 5
  ./gradlew "connected${1}DebugAndroidTest" "-Pandroid.testInstrumentationRunnerArguments.class=$T#$3" ||
    echo "::warning::$1 $3 failed; keeping what was captured"
  adb pull /data/local/tmp/media/. "raw/$4/" || true
  adb shell rm -rf /data/local/tmp/media || true
}
run Kitt2 1024x600 screens kitt2
run Kitt 800x1151 screens kitt
run Kitt2 1024x600 videos kitt2
run Kitt 800x1151 videos kitt
ls -la raw/kitt2 raw/kitt
