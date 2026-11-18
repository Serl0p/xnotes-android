#!/usr/bin/env bash
# CI-only Android emulator smoke check: verifies the debug APK actually opens.
set -euo pipefail

mkdir -p startup-smoke
APK="app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.xnotes.debug"
COMPONENT="com.xnotes.debug/com.xnotes.EditorActivity"

if [[ ! -f "$APK" ]]; then
  echo "Missing APK: $APK" >&2
  exit 1
fi

adb install -r "$APK"
for iteration in 1 2; do
  echo "=== Startup attempt $iteration ==="
  adb shell am force-stop "$PACKAGE" || true
  adb logcat -c
  adb shell am start -W -n "$COMPONENT" | tee "startup-smoke/activity-$iteration.txt"

  # A successful ActivityManager response does NOT mean the app survived startup.
  sleep 15
  adb logcat -d -v time -s AndroidRuntime:E > "startup-smoke/logcat-$iteration.txt" || true
  adb shell dumpsys activity activities > "startup-smoke/activities-$iteration.txt" || true
  adb exec-out screencap -p > "startup-smoke/screenshot-$iteration.png" || true

  # Check both process survival and the actual Android fatal-exception stream.
  PID="$(adb shell pidof "$PACKAGE" | tr -d '\r' || true)"
  if [[ -z "$PID" ]]; then
    echo "FAIL: xNotes process did not survive startup." >&2
    cat "startup-smoke/logcat-$iteration.txt" >&2
    exit 1
  fi
  if grep -A 8 'FATAL EXCEPTION' "startup-smoke/logcat-$iteration.txt" | grep -q "Process: $PACKAGE"; then
    echo "FAIL: AndroidRuntime reported a fatal xNotes exception." >&2
    cat "startup-smoke/logcat-$iteration.txt" >&2
    exit 1
  fi
  echo "PASS: $PACKAGE survived startup attempt $iteration as process $PID"
done
