#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=appium-tests/scripts/vm-config.sh
source "$SCRIPT_DIR/vm-config.sh"

for image in "$VM_BASE_VDA" "$VM_BASE_VDB" "$VM_BASE_EFI_VARS"; do
    if [[ ! -f "$image" ]]; then
        echo "Error: $image not found. Please run appium-tests/scripts/prepare-vm.sh first."
        exit 1
    fi
done

if ! command -v "$VM_QEMU_BIN" &> /dev/null; then
    echo "Error: $VM_QEMU_BIN is not installed."
    exit 1
fi

# The VM is ephemeral by design; see vm_reset_disks in vm-config.sh. CI calls the same function
# from its own step, because there qemu-kvm-action starts QEMU rather than this script.
vm_reset_disks

# VM_DISPLAY=sdl | vnc | none | ...
#
# Defaults per platform, because the options genuinely differ:
#
#   - Linux has `sdl`, which opens its own window and takes gl=on (see VM_GL in vm-config.sh).
#   - macOS QEMU from Homebrew has neither sdl nor gtk, and `cocoa` needs OpenGL this build
#     lacks -- under it SurfaceFlinger SIGABRTs in a loop and nothing ever draws. So: vnc,
#     which is 2D-scanout only and always works, with a viewer opened automatically below.
#
# `none` stays available for headless runs.
if [[ -z "${VM_DISPLAY:-}" ]]; then
    case "$(uname -s)" in
        Darwin) VM_DISPLAY=vnc ;;
        *) VM_DISPLAY=sdl ;;
    esac
fi
DISPLAY_BACKEND="$VM_DISPLAY"
VNC_DISPLAY="${VNC_DISPLAY:-:1}"
VNC_PORT=$((5900 + ${VNC_DISPLAY#:}))

case "$DISPLAY_BACKEND" in
    none)
        DISPLAY_ARGS=(-display none)
        ;;
    vnc)
        # macOS Screen Sharing refuses "None" auth, and QEMU rejects set_password unless the
        # server started with a secret, so wire one up front.
        VNC_PASSWORD="${VNC_PASSWORD:-etchdroid}"
        DISPLAY_ARGS=(
            -object "secret,id=vncpw,data=$VNC_PASSWORD"
            -vnc "$VNC_DISPLAY,password-secret=vncpw"
        )
        ;;
    *)
        DISPLAY_ARGS=(-display "$DISPLAY_BACKEND$VM_DISPLAY_GL")
        ;;
esac

# On macOS the VNC display is only useful with something to view it in, and Screen Sharing is
# built in. The password is embedded in the URL so it does not prompt. VM_VIEWER=0 opts out.
#
# The viewer is a convenience: never let it fail the run.
VIEWER_STARTED_BY_US=0

start_viewer() {
    [[ "$(uname -s)" == Darwin && "$DISPLAY_BACKEND" == vnc ]] || return 0
    [[ -z "${CI:-}" && "${VM_VIEWER:-1}" == 1 ]] || return 0

    # Only tidy up afterwards if this run is what launched it; see stop_viewer.
    pgrep -qf 'Screen Sharing.app' || VIEWER_STARTED_BY_US=1

    for _ in $(seq 60); do
        kill -0 "$QEMU_PID" 2> /dev/null || return 0
        nc -z localhost "$VNC_PORT" 2> /dev/null && break
        sleep 0.5
    done

    if open "vnc://:$VNC_PASSWORD@localhost:$VNC_PORT" 2> /dev/null; then
        echo "Opened Screen Sharing on localhost:$VNC_PORT"
    else
        echo "Note: could not open a VNC viewer; connect manually to localhost:$VNC_PORT"
    fi
}

# Screen Sharing is one shared app for every session, and it exposes no scriptable window list
# (`get every window` fails with -1728; only System Events can see it, and that needs
# Accessibility permission we should not demand). So the rule is ownership rather than window
# matching: quit it only if it was not already running when we started, otherwise leave the
# user's own sessions alone.
stop_viewer() {
    [[ "$VIEWER_STARTED_BY_US" == 1 ]] || return 0
    osascript -e 'quit app "Screen Sharing"' > /dev/null 2>&1 \
        || echo "Note: could not close Screen Sharing automatically"
}

vm_qemu_flags

SERIAL_LOG="$VM_RUN_DIR/serial.log"

echo "Starting LineageOS $LINEAGE_RELEASE ($VM_GUEST_ARCH) VM..."
echo "  Acceleration: $VM_ACCEL (cpu $VM_CPU, $VM_SMP cores, ${VM_MEMORY}M)"
echo "  Display:      $DISPLAY_BACKEND$([[ "$DISPLAY_BACKEND" == vnc ]] && echo " on $VNC_DISPLAY (password: $VNC_PASSWORD)")"
echo "  Serial log:   $SERIAL_LOG"
echo "  ADB:          localhost:5556"
if [[ "$VM_ACCEL" == tcg ]]; then
    echo
    echo "  WARNING: no hardware acceleration for a $VM_GUEST_ARCH guest on a $(vm_host_arch) host."
    echo "           This will be extremely slow; expect test timeouts."
fi
echo
echo "Run appium-tests/scripts/wait-vm-startup.sh once it boots."
echo

# Deliberately not `exec`: that would replace this shell with QEMU, so nothing could tear the
# viewer down afterwards. Running QEMU as a child and waiting on it means the EXIT trap fires on
# a normal exit and on Ctrl-C alike, while still propagating QEMU's exit status.
trap stop_viewer EXIT

"$VM_QEMU_BIN" \
    "${VM_ACCEL_FLAGS[@]}" \
    "${VM_QEMU_FLAGS[@]}" \
    "${DISPLAY_ARGS[@]}" \
    -serial "file:$SERIAL_LOG" &
QEMU_PID=$!

start_viewer

wait "$QEMU_PID"
