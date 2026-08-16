import os


class Config:
    ANDROID_HOME = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk"))
    APPIUM_HOST = os.environ.get("APPIUM_HOST", "127.0.0.1")
    APPIUM_PORT = os.environ.get("APPIUM_PORT", "14723")

    QEMU_QMP_PATH = os.environ.get("QEMU_QMP_PATH", "/tmp/qmp.sock")
    QEMU_MONITOR_PATH = os.environ.get("QEMU_MONITOR_PATH", "/tmp/qemu-monitor.sock")

    # Must match the bus the default stick is attached to in scripts/vm-config.sh, or
    # replug tests re-add the device on a different controller than they removed it from.
    QEMU_USB_BUS = os.environ.get("QEMU_USB_BUS", "ehci.0")
    QEMU_USB_SLOW_BUS = os.environ.get("QEMU_USB_SLOW_BUS", "ehci.0")
    QEMU_USB_DEV_ID = os.environ.get("QEMU_USB_DEV_ID", "usbstick")
    # How long a test image's write phase should last, so there is time to interact with
    # the UI (skip button, mid-write unplug) regardless of how fast the host emulates USB.
    # Image sizes are derived from this and the measured drive speed, see
    # usb_write_speed_mbps in fixtures.py.
    TARGET_WRITE_SECONDS = float(os.environ.get("TARGET_WRITE_SECONDS", "15"))

    # Every Appium element wait is multiplied by this. CI has no host GPU, so the guest
    # software-renders (see appium-tests/scripts/vm-config.sh) and first-frame latency
    # regularly exceeds the short timeouts the flow helpers use. Locally the guest is
    # GPU-accelerated, so 1x keeps failures quick to report while iterating.
    WAIT_TIMEOUT_SCALE = float(os.environ.get("WAIT_TIMEOUT_SCALE", "5" if os.environ.get("CI") else "1"))

    DISABLE_SETUP = os.environ.get("DISABLE_SETUP", "0") == "1"
    DISABLE_SHUTDOWN = os.environ.get("DISABLE_SHUTDOWN", "0") == "1"

    LOGCAT_DIR = os.environ.get("LOGCAT_DIR", None)

    # Where utils.mark() records timestamped test events, for turning the CI screen recording
    # into something navigable afterwards (see appium-tests/scripts/annotate-recording.py).
    # Unset outside CI, which makes marking a no-op.
    MARKERS_FILE = os.environ.get("VM_MARKERS_FILE", None)
