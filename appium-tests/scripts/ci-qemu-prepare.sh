#!/usr/bin/env bash
set -euo pipefail

ADB_HOST="localhost:5556"
adb=(adb -s "$ADB_HOST")

adb disconnect "$ADB_HOST" || true

echo "Connecting to ADB on $ADB_HOST"

# Wait for ADB to show up
for _ in {1..60}; do
    if ! adb connect "$ADB_HOST"; then
        sleep 3
        continue
    fi

    if "${adb[@]}" shell true; then
        break
    fi

    sleep 3
done
echo "Connected to ADB on $ADB_HOST"

if ! "${adb[@]}" shell true; then
    echo "Failed to connect to ADB on $ADB_HOST"
    exit 1
fi

# Fix launcher and go home
"${adb[@]}" shell pm set-home-activity com.android.launcher3
"${adb[@]}" shell input keyevent KEYCODE_HOME
"${adb[@]}" shell settings put system pointer_location 1
