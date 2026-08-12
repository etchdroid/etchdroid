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

# The VM is ephemeral by design: throw away last run's overlays and branch fresh ones off the
# pristine bases. Guest state never persists, so every run re-does the trade-in-mode bypass and
# no test can leak into the next one.
rm -rf "$VM_RUN_DIR"
mkdir -p "$VM_RUN_DIR"
for pair in "$VM_BASE_VDA:$VM_VDA" "$VM_BASE_VDB:$VM_VDB" "$VM_BASE_EFI_VARS:$VM_EFI_VARS"; do
    qemu-img create -q -f "$VM_IMAGE_FORMAT" \
        -b "${pair%%:*}" -F "$VM_IMAGE_FORMAT" "${pair##*:}"
done
# The virtual USB drive is scratch space the tests write to, so it needs no backing file.
qemu-img create -q -f qcow2 "$VM_USB_IMAGE" "$VM_USB_SIZE"

# VM_DISPLAY=none (default) | vnc | cocoa
#
# Not cocoa on macOS unless VM_QEMU_BIN is a GL-capable build: this image's gralloc is
# minigbm_upstream, and without OpenGL SurfaceFlinger SIGABRTs in a loop and nothing ever draws.
# Homebrew's QEMU has no OpenGL at all. none and vnc are 2D-scanout only and always work.
DISPLAY_BACKEND="${VM_DISPLAY:-none}"
VNC_DISPLAY="${VNC_DISPLAY:-:1}"

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

exec "$VM_QEMU_BIN" \
    "${VM_QEMU_FLAGS[@]}" \
    "${DISPLAY_ARGS[@]}" \
    -serial "file:$SERIAL_LOG"
