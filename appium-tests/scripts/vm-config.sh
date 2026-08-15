# shellcheck shell=bash
#
# Single source of truth for the Appium e2e VM. Sourced, not executed:
#
#     source "$(dirname "${BASH_SOURCE[0]}")/vm-config.sh"
#
# Used by prepare-vm.sh, run-vm.sh and the "Load VM config" step in
# .github/workflows/build-test-debug.yml, which re-exports these through $GITHUB_ENV.
# Every value can be overridden from the environment.
#
# The guest is LineageOS 23.2 (Android 16) packaged for upstream QEMU by
# jqssun/android-lineage-qemu. Same image at two architectures, so CI and local macOS run the
# same guest. It boots from disk over UEFI -- there is no kernel/initrd/cmdline.

LINEAGE_REPO="${LINEAGE_REPO:-jqssun/android-lineage-qemu}"
LINEAGE_RELEASE="${LINEAGE_RELEASE:-v2026.08.04}"

vm_host_arch() {
    case "$(uname -m)" in
        arm64 | aarch64) echo arm64 ;;
        x86_64 | amd64) echo x86_64 ;;
        *) echo "Unsupported host architecture: $(uname -m)" >&2; return 1 ;;
    esac
}

# Guest architecture: arm64 | x86_64. Defaults to the host's, the only combination that gets
# hardware acceleration.
VM_GUEST_ARCH="${VM_GUEST_ARCH:-$(vm_host_arch)}"

case "$VM_GUEST_ARCH" in
    arm64)
        VM_ASSET_ARCH='arm64only'
        VM_QEMU_BIN="${VM_QEMU_BIN:-qemu-system-aarch64}"
        VM_MACHINE="${VM_MACHINE:-virt}"
        # Debian/Ubuntu ship AAVMF; Homebrew and Fedora use the edk2-* names.
        VM_UEFI_NAMES=(edk2-aarch64-code.fd AAVMF_CODE.fd QEMU_EFI-pflash.raw)
        ;;
    x86_64)
        VM_ASSET_ARCH='x86_64'
        VM_QEMU_BIN="${VM_QEMU_BIN:-qemu-system-x86_64}"
        VM_MACHINE="${VM_MACHINE:-q35}"
        VM_UEFI_NAMES=(edk2-x86_64-code.fd OVMF_CODE_4M.fd OVMF_CODE.fd)
        ;;
    *)
        echo "Unsupported VM_GUEST_ARCH: $VM_GUEST_ARCH (expected arm64 or x86_64)" >&2
        return 1
        ;;
esac

# Hardware acceleration only when the guest matches the host. Emulating x86_64 on arm64 is
# especially bad: QEMU disables MTTCG for a strongly-ordered guest on a weakly-ordered host, so
# it degrades to single-threaded TCG no matter what -smp says.
if [[ -z "${VM_ACCEL:-}" ]]; then
    if [[ "$VM_GUEST_ARCH" == "$(vm_host_arch)" ]]; then
        case "$(uname -s)" in
            Darwin) VM_ACCEL=hvf ;;
            Linux) VM_ACCEL=kvm ;;
            *) VM_ACCEL=tcg ;;
        esac
    else
        VM_ACCEL=tcg
    fi
fi

if [[ "$VM_ACCEL" == tcg ]]; then
    # `host` is only valid under kvm/hvf.
    VM_CPU="${VM_CPU:-max}"
else
    VM_CPU="${VM_CPU:-host}"
fi

VM_SMP="${VM_SMP:-4}"
VM_MEMORY="${VM_MEMORY:-4096}"

# Pristine images from prepare-vm.sh, kept per-arch so both can coexist in one cache.
VM_DIR="${VM_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)/.appium-vm}"
VM_BASE_DIR="$VM_DIR/$VM_GUEST_ARCH"
VM_BASE_VDA="$VM_BASE_DIR/vda.qcow2"
VM_BASE_VDB="$VM_BASE_DIR/vdb.qcow2"
VM_BASE_EFI_VARS="$VM_BASE_DIR/efi_vars.fd"

# Throwaway per-run overlays. The VM is deliberately ephemeral: every boot starts from the
# pristine bases, so guest state (including the trade-in-mode bypass) never persists and tests
# can't leak into each other. run-vm.sh recreates these on every start.
VM_RUN_DIR="$VM_DIR/run-$VM_GUEST_ARCH"
VM_VDA="$VM_RUN_DIR/vda.qcow2"
VM_VDB="$VM_RUN_DIR/vdb.qcow2"
VM_EFI_VARS="$VM_RUN_DIR/efi_vars.fd"
VM_USB_IMAGE="$VM_RUN_DIR/usb-storage.qcow2"
VM_USB_SIZE="${VM_USB_SIZE:-2G}"

# All three shipped images are qcow2 -- including efi_vars.fd, which is a *sparse* qcow2 whose
# virtual size is exactly the 64 MiB the pflash unit expects. Attaching it as raw fails.
VM_IMAGE_FORMAT='qcow2'

# Throw away last run's overlays and branch fresh ones off the pristine bases, so guest state
# never persists between runs and no test can leak into the next one.
#
# This lives here rather than in run-vm.sh because both entry points need it: locally run-vm.sh
# starts QEMU, but in CI qemu-kvm-action does, and it would otherwise be handed disk paths that
# nothing had created.
#
# Must run *after* any cache save of VM_DIR: these overlays are throwaway, and caching them
# would restore stale guest state on the next run, quietly defeating the whole point.
vm_reset_disks() {
    local pair
    rm -rf "$VM_RUN_DIR"
    mkdir -p "$VM_RUN_DIR"
    for pair in "$VM_BASE_VDA:$VM_VDA" "$VM_BASE_VDB:$VM_VDB" "$VM_BASE_EFI_VARS:$VM_EFI_VARS"; do
        qemu-img create -q -f "$VM_IMAGE_FORMAT" \
            -b "${pair%%:*}" -F "$VM_IMAGE_FORMAT" "${pair##*:}"
    done
    # The virtual USB drive is scratch space the tests write to, so it needs no backing file.
    qemu-img create -q -f qcow2 "$VM_USB_IMAGE" "$VM_USB_SIZE"
}

vm_find_uefi_code() {
    local dirs=() dir name
    if [[ -n "${VM_UEFI_CODE:-}" ]]; then
        echo "$VM_UEFI_CODE"
        return 0
    fi
    if command -v brew > /dev/null 2>&1; then
        dirs+=("$(brew --prefix qemu 2> /dev/null)/share/qemu")
    fi
    dirs+=(/usr/share/qemu /usr/share/edk2/aarch64 /usr/share/AAVMF /usr/share/OVMF /usr/share/edk2/x64 /usr/share/edk2/ovmf)
    for dir in "${dirs[@]}"; do
        for name in "${VM_UEFI_NAMES[@]}"; do
            if [[ -f "$dir/$name" ]]; then
                echo "$dir/$name"
                return 0
            fi
        done
    done
    echo "Could not find UEFI firmware (looked for ${VM_UEFI_NAMES[*]}). Set VM_UEFI_CODE." >&2
    return 1
}

# GPU passthrough (virgl) needs both a GL-capable QEMU and a display backend that can hand it an
# EGL context -- in practice a visible window. That means local Linux only:
#
#   - Linux has the `sdl` backend and Mesa, so a local session gets an accelerated window.
#   - CI renders offscreen under Xvfb with no GPU, so GL buys nothing there.
#   - macOS QEMU from Homebrew has no OpenGL at all (and no sdl/gtk backend), so it uses VNC.
#
# Override with VM_GL=1/0 if the guess is wrong for your setup.
if [[ -z "${VM_GL:-}" ]]; then
    if [[ "$(uname -s)" == Linux && -z "${CI:-}" ]]; then
        VM_GL=1
    else
        VM_GL=0
    fi
fi

# virtio-vga is virtio-gpu plus VGA compatibility, and only exists on x86: arm's `virt` machine
# has no VGA at all, so it must use the plain PCI device. The distinction matters -- with
# virtio-gpu-pci on x86 the display stops updating the moment the kernel takes over from UEFI
# and switches scanout, which leaves the CI recording frozen on the GRUB handoff even though the
# guest boots fine. The pre-LineageOS setup used virtio-vga for exactly this reason.
if [[ "$VM_GUEST_ARCH" == x86_64 ]]; then
    VM_VGA_DEVICE="virtio-vga$([[ "$VM_GL" == 1 ]] && echo -gl)"
else
    VM_VGA_DEVICE="virtio-gpu$([[ "$VM_GL" == 1 ]] && echo -gl)-pci"
fi

if [[ "$VM_GL" == 1 ]]; then
    VM_DISPLAY_GL=',gl=on'
else
    VM_DISPLAY_GL=''
fi

# QEMU flags, split into two arrays because CI and local invoke QEMU differently.
#
# VM_ACCEL_FLAGS holds exactly what qemu-kvm-action already builds from its own inputs
# (`-cpu`, `-m`, `-smp`, plus `-enable-kvm`), so CI must NOT pass these again -- it supplies
# them as action inputs instead, and duplicating them just makes the final command line
# ambiguous. run-vm.sh passes both arrays.
#
# Everything else is shared. Deliberately excluded from both because they genuinely differ:
# the display backend (see VM_DISPLAY_GL above) and the serial routing.
#
# Both USB controllers are intentional: xhci is USB 3 (fast path), uhci is USB 1.1 (slow path).
# The tests pick between them via QEMU_USB_BUS / QEMU_USB_SLOW_BUS, see
# appium-tests/etchdroid/config.py. Input devices share the xhci bus because machine `virt` has
# no default USB controller.
vm_qemu_flags() {
    VM_ACCEL_FLAGS=(
        -accel "$VM_ACCEL"
        -cpu "$VM_CPU"
        -smp "$VM_SMP"
        -m "$VM_MEMORY"
    )

    VM_QEMU_FLAGS=(
        -machine "$VM_MACHINE"
        -drive "if=pflash,unit=0,format=raw,readonly=on,file=$(vm_find_uefi_code)"
        -drive "if=pflash,unit=1,format=$VM_IMAGE_FORMAT,file=$VM_EFI_VARS"
        -drive "file=$VM_VDA,if=none,id=vda,format=$VM_IMAGE_FORMAT,discard=unmap,detect-zeroes=unmap"
        -device virtio-blk-pci,drive=vda,bootindex=0
        -drive "file=$VM_VDB,if=none,id=vdb,format=$VM_IMAGE_FORMAT,discard=unmap,detect-zeroes=unmap"
        -device virtio-blk-pci,drive=vdb,bootindex=1
        -netdev user,id=network,hostfwd=tcp::5556-:5555
        -device virtio-net-pci,netdev=network
        -device "$VM_VGA_DEVICE"
        -device virtio-rng-pci
        -device nec-usb-xhci,id=xhci
        -device ich9-usb-uhci1,id=uhci
        -device usb-kbd,bus=xhci.0
        -device usb-tablet,bus=xhci.0
        -drive "if=none,id=usbstick,file=$VM_USB_IMAGE,format=qcow2"
        -device usb-storage,id=usbstick,bus=xhci.0,drive=usbstick,removable=on
        -qmp unix:/tmp/qmp.sock,server=on,wait=off
        -monitor unix:/tmp/qemu-monitor.sock,server=on,wait=off
    )
}
