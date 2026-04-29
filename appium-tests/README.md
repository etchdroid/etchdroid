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
- `7z`, `qemu-img` and `qemu` for VM testing.

### On a real device

>[!WARNING]
> The tests will erase the content of the connected USB drive.
> Some wear is to be expected on the drive since random data will be written to it.

1. Connect the device to the computer using wireless ADB (for older devices: connect via USB and run
   `adb tcpip 5555 && adb connect <device-ip>:5555`).
2. Connect a USB drive of at least 2GB to the device.
3. Build and install EtchDroid: `./gradlew installFossDebug`
4. Run the tests using `pytest` from the `appium-tests/` directory, excluding those specifically designed to run in a VM:
   ```bash
   uv run pytest -m "not qemu" -sv
   ```

### In a virtual machine (recommended, Linux-only)

>[!NOTE]
> Running the tests in a VM is recommended over a physical device since it allows testing the USB hotplugging logic without risking damage to physical drives. The VM is configured to use a virtual USB drive that can be safely erased and re-created on each test run.

Make sure to have `qemu-system-x86_64` installed.

1. **Prepare the VM files**:
    ```bash
    ./appium-tests/scripts/prepare-vm.sh
    ```
    This will download the Bliss OS ISO, extract the required files, and create a virtual USB drive image in the `.appium-vm/` directory.

2. **Launch QEMU** (in a separate terminal):
    ```bash
    ./appium-tests/scripts/run-vm.sh
    ```
    This starts the VM with a GTK UI and redirects the serial console to stdio. ADB will be available on `localhost:5556`.

3. **Wait for startup and configure**:
    ```bash
    ./appium-tests/scripts/wait-vm-startup.sh
    ```
    This script waits for the VM to be reachable via ADB and applies necessary settings (like setting the default launcher) for the tests to run reliably.
    
    We recommend running this script at least once before running the tests to ensure the VM is properly configured, even if the ADB connection is working already.

4. **Build and install EtchDroid**:
    ```bash
    ./gradlew installFossDebug
    ```

5. **Run the tests** from the `appium-tests/` directory:
   ```bash
   uv run pytest -sv
   ```

## Notes

- The tests expect a USB drive to be connected before the tests start (the script handles this for QEMU).
- **Warning**: The tests will erase the content of the connected USB drive if run on a physical device.
- Both the QMP and monitor sockets are used by the tests to simulate USB hotplugging in QEMU.
