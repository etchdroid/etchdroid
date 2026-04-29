#!/usr/bin/env bash
set -euo pipefail

# Go to project root from appium-tests/scripts
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
VM_DIR="$PROJECT_ROOT/.appium-vm"

USB_IMAGE="$VM_DIR/usb-storage.qcow2"
SYSTEM_EFS="$VM_DIR/system.efs"
KERNEL="$VM_DIR/kernel"
INITRD="$VM_DIR/initrd.img"

# Check if files exist
for file in "$USB_IMAGE" "$SYSTEM_EFS" "$KERNEL" "$INITRD"; do
    if [[ ! -f "$file" ]]; then
        echo "Error: $file not found. Please run appium-tests/scripts/prepare-vm.sh first."
        exit 1
    fi
done

echo "Starting Bliss OS VM..."
echo "ADB will be available on localhost:5556"

qemu-system-x86_64 \
    -enable-kvm \
    -cpu host \
    -smp 2 \
    -m 4096 \
    -kernel "$KERNEL" \
    -initrd "$INITRD" \
    -append 'root=/dev/ram0 androidboot.selinux=permissive console=ttyS0 FFMPEG_CODEC=1 FFMPEG_PREFER_C2=1' \
    -netdev user,id=network,hostfwd=tcp::5556-:5555 \
    -device virtio-net-pci,netdev=network \
    -device virtio-vga-gl \
    -display gtk,gl=on \
    -serial stdio \
    -drive index=0,if=virtio,id=system,file="$SYSTEM_EFS",format=raw,readonly=on \
    -usb \
    -device usb-tablet,bus=usb-bus.0 \
    -device nec-usb-xhci,id=xhci \
    -device ich9-usb-uhci1,id=uhci \
    -drive if=none,id=usbstick,file="$USB_IMAGE",format=qcow2 \
    -device usb-storage,id=usbstick,bus=xhci.0,drive=usbstick,removable=on \
    -qmp unix:/tmp/qmp.sock,server=on,wait=off \
    -monitor unix:/tmp/qemu-monitor.sock,server=on,wait=off
