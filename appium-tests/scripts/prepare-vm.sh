#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

BLISSOS_FILE="${BLISSOS_FILE:-Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso}"
BLISSOS_URL="${BLISSOS_URL:-https://deac-riga.dl.sourceforge.net/project/blissos-x86/Official/BlissOS16/FOSS/Generic/}"
VM_DIR="${VM_DIR:-$PROJECT_ROOT/.appium-vm}"

# Check for required tools
EXIT=0
for tool in curl 7z qemu-img sha256sum; do
    if ! command -v "$tool" &> /dev/null; then
        echo "Error: $tool is not installed."
        EXIT=1
    fi
done
[[ "$EXIT" == 1 ]] && exit 1

mkdir -p "$VM_DIR"

if [[ -f "$VM_DIR/kernel" && -f "$VM_DIR/initrd.img" && -f "$VM_DIR/system.efs" ]]; then
    echo "Kernel, initrd, and system.efs already extracted to '$VM_DIR'. Skipping download and extraction."
    exit 0
fi

# Download BlissOS ISO
ISO_PATH="$VM_DIR/$BLISSOS_FILE"
SHA_PATH="$ISO_PATH.sha256"

if [[ ! -f "$SHA_PATH" ]]; then
    echo "Downloading SHA256 checksum..."
    curl -L -o "$SHA_PATH" "$BLISSOS_URL/$BLISSOS_FILE.sha256"
fi

if [[ ! -f "$ISO_PATH" ]] || ! (cd "$VM_DIR" && sha256sum -c "$(basename "$SHA_PATH")"); then
    echo "Downloading BlissOS ISO (this may take a while)..."
    curl -L -o "$ISO_PATH" "$BLISSOS_URL/$BLISSOS_FILE"
    (cd "$VM_DIR" && sha256sum -c "$(basename "$SHA_PATH")")
else
    echo "BlissOS ISO is already present and valid."
fi

# Extract files from ISO
echo "Extracting files from ISO..."
# 7z e (extract) -y (yes to all) -o (output dir)
7z e -y -o"$VM_DIR" "$ISO_PATH" kernel initrd.img system.efs

# Create virtual USB drive image
USB_IMAGE="$VM_DIR/usb-storage.qcow2"
if [[ ! -f "$USB_IMAGE" ]]; then
    echo "Creating virtual USB drive image..."
    qemu-img create -f qcow2 "$USB_IMAGE" 2G
else
    echo "Virtual USB drive image already exists."
fi

echo "VM preparation complete. Files are in $VM_DIR"
# For CI to know where files are
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "vm_dir=$VM_DIR" >> "$GITHUB_OUTPUT"
fi
