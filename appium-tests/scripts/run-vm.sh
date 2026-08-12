#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=appium-tests/scripts/vm-config.sh
source "$SCRIPT_DIR/vm-config.sh"

# Check if files exist
for file in "$VM_USB_IMAGE" "$VM_SYSTEM_EFS" "$VM_KERNEL" "$VM_INITRD"; do
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
    -kernel "$VM_KERNEL" \
    -initrd "$VM_INITRD" \
    -append "$VM_CMDLINE" \
    -device virtio-vga-gl \
    -display gtk,gl=on \
    -serial stdio \
    "${VM_QEMU_FLAGS[@]}"
