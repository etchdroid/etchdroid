#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=appium-tests/scripts/vm-config.sh
source "$SCRIPT_DIR/vm-config.sh"

# Check for required tools
EXIT=0
for tool in curl unzip qemu-img python3; do
    if ! command -v "$tool" &> /dev/null; then
        echo "Error: $tool is not installed."
        EXIT=1
    fi
done
[[ "$EXIT" == 1 ]] && exit 1

echo "Guest: LineageOS $LINEAGE_RELEASE ($VM_GUEST_ARCH)"

if [[ -f "$VM_BASE_VDA" && -f "$VM_BASE_VDB" && -f "$VM_BASE_EFI_VARS" ]]; then
    echo "Base images already present in '$VM_BASE_DIR'. Skipping download."
else
    mkdir -p "$VM_BASE_DIR"

    # Resolve the asset URL from the release: the file name embeds both a version and a build
    # date (UTM-VM-lineage-23.2-20260804-jqssun-virtio_arm64only.zip), neither of which is
    # derivable from the tag. Authenticated when GITHUB_TOKEN is set, to dodge CI rate limits.
    echo "Resolving download URL..."
    AUTH_ARGS=()
    [[ -n "${GITHUB_TOKEN:-}" ]] && AUTH_ARGS=(-H "Authorization: Bearer $GITHUB_TOKEN")
    ASSET_URL="$(
        curl -fsSL "${AUTH_ARGS[@]}" \
            "https://api.github.com/repos/$LINEAGE_REPO/releases/tags/$LINEAGE_RELEASE" \
            | python3 -c "
import json, re, sys
pattern = re.compile(r'UTM-VM-.*virtio_${VM_ASSET_ARCH}\.zip$')
for asset in json.load(sys.stdin)['assets']:
    if pattern.search(asset['name']):
        print(asset['browser_download_url'])
        break
else:
    sys.exit('No UTM-VM asset matching virtio_${VM_ASSET_ARCH} in $LINEAGE_RELEASE')
"
    )"
    echo "  $ASSET_URL"

    ZIP_PATH="$VM_DIR/$(basename "$ASSET_URL")"
    if [[ ! -f "$ZIP_PATH" ]]; then
        echo "Downloading VM image (this may take a while)..."
        curl -fL --retry 3 -o "$ZIP_PATH.part" "$ASSET_URL"
        mv "$ZIP_PATH.part" "$ZIP_PATH"
    fi

    # The archive is a UTM bundle: LineageOS_on_<arch>.utm/Data/{vda,vdb,efi_vars}. We only
    # want the three images; UTM's config.plist is irrelevant since we drive QEMU directly.
    echo "Extracting..."
    EXTRACT_DIR="$(mktemp -d "$VM_DIR/extract.XXXXXX")"
    trap 'rm -rf "$EXTRACT_DIR"' EXIT
    unzip -q -o "$ZIP_PATH" -d "$EXTRACT_DIR"

    DATA_DIR="$(find "$EXTRACT_DIR" -maxdepth 3 -type d -name Data | head -1)"
    [[ -n "$DATA_DIR" ]] || { echo "No .utm bundle Data/ directory in $ZIP_PATH"; exit 1; }

    for image in vda.qcow2 vdb.qcow2 efi_vars.fd; do
        [[ -f "$DATA_DIR/$image" ]] || { echo "Missing $image in the bundle"; exit 1; }
        mv "$DATA_DIR/$image" "$VM_BASE_DIR/$image"
    done
    rm -rf "$EXTRACT_DIR"
    trap - EXIT

    # These are overlay backing files from here on: never boot them directly, never write them.
    chmod a-w "$VM_BASE_VDA" "$VM_BASE_VDB" "$VM_BASE_EFI_VARS"

    echo "Removing the archive to save cache space..."
    rm -f "$ZIP_PATH"
fi

echo
echo "Base images (read-only, used as overlay backing files):"
for image in "$VM_BASE_VDA" "$VM_BASE_VDB" "$VM_BASE_EFI_VARS"; do
    printf '  %s (%s virtual)\n' "$image" \
        "$(qemu-img info --output=json "$image" | python3 -c 'import json,sys; print(json.load(sys.stdin)["virtual-size"] // 1048576, "MiB")')"
done
echo
echo "VM preparation complete. Run appium-tests/scripts/run-vm.sh next."

# For CI to know where files are
if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    echo "vm_dir=$VM_DIR" >> "$GITHUB_OUTPUT"
fi
