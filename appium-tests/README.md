# EtchDroid end-to-end tests

This directory contains code to test EtchDroid on real and emulated devices.

The tests are written in Python using `pytest`, making use of [Appium](https://appium.io/) for interactive UI testing.

## Test modules

The tests are divided into two modules:

- `test_generic.py`: tests that run on any device, including real devices, provided that a USB drive of at least 2GB is
  connected (it will be erased).
- `test_qemu.py`: tests that must be run using QEMU (not the Android emulator!), since they interact with the QEMU
  monitor console to connect and disconnect emulated USB drives.

## Requirements

- The Android SDK must be installed. It should either be stored under `~/Android/Sdk` (Android Studio default) or
  specified using the `ANDROID_HOME` environment variable.
- [`uv`](https://docs.astral.sh/uv/) must be installed

### On a real device

1. Connect the device to the computer using wireless ADB (for older devices: connect via USB and run
   `adb tcpip 5555 && adb connect <device-ip>:5555`).
2. Connect a USB drive of at least 2GB to the device.
3. Run the tests using `pytest`:
   ```bash
   uv run pytest -m "not qemu" -sv
   ```

### On QEMU

Make sure to have `qemu-system-x86_64` installed.

1. Download a recent version of Bliss OS x86_64 **without GApps** (the GApps version has a setup wizard on boot) such
   as [this build](https://sourceforge.net/projects/blissos-x86/files/Official/BlissOS16/FOSS/Generic/Bliss-v16.9.7-x86_64-OFFICIAL-foss-20241011.iso/download)
2. Mount the ISO image
    ```bash
    mkdir -p /tmp/bliss
    losetup -P /dev/loop10 /path/to/Bliss.iso
    mount /dev/loop10p1 /tmp/bliss
    ```
3. Create a USB drive image
    ```bash
    qemu-img create -f qcow2 /tmp/usb-storage.qcow2 2G
    ```
4. Launch QEMU
    ```bash
    cd /tmp/bliss
    qemu-system-x86_64 \
     -enable-kvm \
     -cpu host \
     -smp 2 \
     -m 4096 \
     -kernel kernel \
     -initrd initrd.img \
     -append 'root=/dev/ram0 androidboot.selinux=permissive console=tty1 FFMPEG_CODEC=1 FFMPEG_PREFER_C2=1' \
     -audiodev pa,id=snd0 -device AC97,audiodev=snd0 \
     -netdev user,id=network,hostfwd=tcp::5556-:5555 \
     -device virtio-net-pci,netdev=network \
     -device virtio-vga-gl -display sdl,gl=on \
     -drive index=0,if=virtio,id=system,file=system.efs,format=raw,readonly=on \
     -usb \
     -device usb-tablet,bus=usb-bus.0 \
     -device nec-usb-xhci,id=xhci \
     -device ich9-usb-uhci1,id=uhci \
     -drive if=none,id=usbstick,file=/tmp/usb-storage.qcow2,format=qcow2 \
     -device usb-storage,id=usbstick,bus=xhci.0,drive=usbstick,removable=on \
     -qmp unix:/tmp/qmp.sock,server=on,wait=off \
     -monitor unix:/tmp/qemu-monitor.sock,server=on,wait=off
    ```
    - Notes:
      - The `audiodev` can be removed if you don't need audio.
      - The two USB controllers are both necessary for the tests
      - The tests expect a USB drive to be connected at boot time so the generic tests can work both on real devices
        and QEMU.
      - Both the QMP and monitor sockets are necessary for the tests to work.
5. Connect ADB to QEMU
    ```bash
    adb connect localhost:5556
    ```
6. Set the default launcher
   ```bash
    adb shell pm set-home-activity com.android.launcher3 && adb shell input keyevent KEYCODE_HOME
    ```
7. Install EtchDroid
    ```bash
    ./gradlew installFossDebug
    ```
8. Run the tests using `pytest`:
   ```bash
   uv run pytest -sv
   ```
