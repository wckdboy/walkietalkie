#!/usr/bin/env bash
set -euo pipefail
mkdir -p emulator-results
trap 'adb logcat -d > emulator-results/logcat-final.txt || true' EXIT
adb shell getprop ro.product.cpu.abilist > emulator-results/abis.txt
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell settings put system font_scale 1.0
adb shell cmd uimode night no
adb logcat -c
timeout 8m adb shell am instrument -w -r -e class cc.wckd.walkietalkie.UiSmokeTest cc.wckd.walkietalkie.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-results/instrumentation.txt
adb pull /sdcard/Android/data/cc.wckd.walkietalkie/files/screenshots emulator-results/ || true
adb logcat -d > emulator-results/logcat.txt
grep -q 'OK (1 test)' emulator-results/instrumentation.txt
timeout 8m adb shell am instrument -w -r -e class cc.wckd.walkietalkie.RecordingTest cc.wckd.walkietalkie.test/androidx.test.runner.AndroidJUnitRunner | tee emulator-results/recording-tests.txt
adb logcat -d >> emulator-results/logcat.txt
grep -q 'OK (4 tests)' emulator-results/recording-tests.txt
adb shell am force-stop cc.wckd.walkietalkie
adb shell cmd uimode night yes
adb shell am start -W -n cc.wckd.walkietalkie/.MainActivity
sleep 3
adb exec-out screencap -p > emulator-results/08-dark-mode.png
adb shell am force-stop cc.wckd.walkietalkie
adb shell settings put system font_scale 1.3
adb shell am start -W -n cc.wckd.walkietalkie/.MainActivity
sleep 3
adb exec-out screencap -p > emulator-results/09-large-text.png
adb logcat -d >> emulator-results/logcat.txt
