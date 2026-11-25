#!/usr/bin/env bash
# Instrumented tests on a running emulator: APKs are installed with adb directly
# (connectedDebugAndroidTest could fail with "Cannot install split APKs with API level < 21"
# when the emulator API level was not detected yet).
set -u
OUT="$1"; mkdir -p "$OUT"
PKG=org.ivanivorontsov.stroka
adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = "1" ]; do sleep 2; done
adb shell getprop ro.build.version.sdk
adb install -r -t app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb logcat -c || true
adb shell am instrument -w -r "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tee "$OUT/instrument.txt"
adb logcat -d > "$OUT/logcat.txt" || true
adb pull "/sdcard/Android/data/$PKG/files/build-out" "$OUT/" || true
grep -q "INSTRUMENTATION_CODE: -1" "$OUT/instrument.txt" && ! grep -Eq "FAILURES!!!|INSTRUMENTATION_STATUS_CODE: -2|Process crashed" "$OUT/instrument.txt"
