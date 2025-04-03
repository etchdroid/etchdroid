#!/usr/bin/env bash
set -euo pipefail

ADB_HOST="localhost:5556"
adb=(adb -s "$ADB_HOST")

adb disconnect "$ADB_HOST" || true

echo "Connecting to ADB on $ADB_HOST"

# Wait for ADB to show up
for _ in {1..20}; do
    if ! adb connect "$ADB_HOST"; then
        sleep 3
        continue
    fi

    if "${adb[@]}" shell true; then
        break
    fi

    sleep 1
done
echo "Connected to ADB on $ADB_HOST"

# Fix launcher and go home
"${adb[@]}" shell pm set-home-activity com.android.launcher3
"${adb[@]}" shell input keyevent KEYCODE_HOME
