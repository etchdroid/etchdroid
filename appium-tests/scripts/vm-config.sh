# shellcheck shell=bash
#
# Single source of truth for the Appium e2e VM. Sourced, not executed:
#
#     source "$(dirname "${BASH_SOURCE[0]}")/vm-config.sh"
#
# Used by prepare-vm.sh, run-vm.sh and the "Load VM config" step in
# .github/workflows/build-test-debug.yml, which re-exports these through $GITHUB_ENV.
# Every value can be overridden from the environment.

BLISSOS_FILE="${BLISSOS_FILE:-Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso}"
BLISSOS_URL="${BLISSOS_URL:-https://deac-riga.dl.sourceforge.net/project/blissos-x86/Official/BlissOS16/FOSS/Generic/}"

VM_DIR="${VM_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.appium-vm}"

# Extracted from the ISO by prepare-vm.sh, plus the virtual USB drive it creates.
VM_KERNEL="$VM_DIR/kernel"
VM_INITRD="$VM_DIR/initrd.img"
VM_SYSTEM_EFS="$VM_DIR/system.efs"
VM_USB_IMAGE="$VM_DIR/usb-storage.qcow2"

VM_CMDLINE='root=/dev/ram0 androidboot.selinux=permissive console=ttyS0 FFMPEG_CODEC=1 FFMPEG_PREFER_C2=1'

# QEMU flags shared by the local and CI invocations, one argument per array element.
# Deliberately excluded because the two differ on purpose: the display backend, the serial
# routing, and -cpu/-smp/-m/-enable-kvm/-kernel/-initrd/-append (CI passes the last four as
# qemu-kvm-action inputs rather than raw flags).
#
# Both USB controllers are intentional: xhci is USB 3 (fast path), uhci is USB 1.1 (slow
# path). The tests pick between them via QEMU_USB_BUS / QEMU_USB_SLOW_BUS, see
# appium-tests/etchdroid/config.py.
VM_QEMU_FLAGS=(
    -netdev user,id=network,hostfwd=tcp::5556-:5555
    -device virtio-net-pci,netdev=network
    -drive "index=0,if=virtio,id=system,file=$VM_SYSTEM_EFS,format=raw,readonly=on"
    -usb
    -device usb-tablet,bus=usb-bus.0
    -device nec-usb-xhci,id=xhci
    -device ich9-usb-uhci1,id=uhci
    -drive "if=none,id=usbstick,file=$VM_USB_IMAGE,format=qcow2"
    -device usb-storage,id=usbstick,bus=xhci.0,drive=usbstick,removable=on
    -qmp unix:/tmp/qmp.sock,server=on,wait=off
    -monitor unix:/tmp/qemu-monitor.sock,server=on,wait=off
)
