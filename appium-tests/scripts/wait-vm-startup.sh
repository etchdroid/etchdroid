#!/usr/bin/env bash
set -euo pipefail

ADB_HOST="${ADB_HOST:-localhost:5556}"
adb=(adb -s "$ADB_HOST")

adb disconnect "$ADB_HOST" || true

echo "Connecting to ADB on $ADB_HOST"

# Wait for the transport. The guest reports as `device` and advertises shell_v2 well before a
# shell actually works, so `adb connect` succeeding is not enough to proceed on.
for _ in {1..100}; do
    if adb connect "$ADB_HOST" | grep -q connected; then
        [[ "$("${adb[@]}" get-state 2>/dev/null)" == device ]] && break
    fi
    sleep 3
done

if [[ "$("${adb[@]}" get-state 2>/dev/null)" != device ]]; then
    echo "Failed to connect to ADB on $ADB_HOST"
    exit 1
fi
echo "Connected to ADB on $ADB_HOST"

# The VM is ephemeral, so it always boots unprovisioned. On an unprovisioned Android 16 device
# adbd runs in trade-in mode (SELinux u:r:adbd_tradeinmode:s0), where the transport looks
# healthy but every command except `tradeinmode` is refused with "error: closed".
#
# Trade-in mode permits exactly one escape: `tradeinmode evaluate` bypasses setup. We then mark
# the device provisioned, which is what actually turns trade-in mode off for good, per
# https://android.googlesource.com/platform/packages/modules/adb/+/HEAD/docs/dev/adb_tradeinmode.md
#
# (`evaluate` forces a factory reset on the *next* boot. That never matters here: overlays are
# discarded on every start, so there is no next boot for this disk state.)
if ! "${adb[@]}" shell true 2> /dev/null; then
    echo "No adb shell -- assuming trade-in mode, bypassing setup"
    "${adb[@]}" shell tradeinmode wait-until-ready evaluate || true

    for _ in {1..40}; do
        "${adb[@]}" shell true 2> /dev/null && break
        sleep 3
    done

    if ! "${adb[@]}" shell true 2> /dev/null; then
        echo "Still no adb shell after 'tradeinmode evaluate'."
        echo "Check whether the guest is unprovisioned and adbd is in trade-in mode:"
        echo "  adb -s $ADB_HOST shell tradeinmode getstatus"
        exit 1
    fi
    echo "Setup bypassed"
fi

# Wait for the framework, otherwise `settings` and `pm` race against system_server.
for _ in {1..60}; do
    [[ "$("${adb[@]}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]] && break
    sleep 3
done

# Provisioning is what keeps trade-in mode off; disabling the wizard keeps it from stealing
# focus from the app under test. Both are idempotent.
"${adb[@]}" shell 'settings put global device_provisioned 1'
"${adb[@]}" shell 'settings put secure user_setup_complete 1'
"${adb[@]}" shell pm disable-user --user 0 org.lineageos.setupwizard || true

# Fix launcher and go home. LineageOS 23 ships Launcher3/Quickstep, not Trebuchet.
"${adb[@]}" shell pm set-home-activity com.android.launcher3
"${adb[@]}" shell input keyevent KEYCODE_HOME

# Enable pointer location to see what the appium tests are doing
"${adb[@]}" shell settings put system pointer_location 1

echo "Guest ready: $("${adb[@]}" shell getprop ro.build.version.release | tr -d '\r')" \
    "($("${adb[@]}" shell getprop ro.product.cpu.abi | tr -d '\r'))"
