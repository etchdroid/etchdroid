import os


class Config:
    ANDROID_HOME = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk"))
    APPIUM_HOST = os.environ.get("APPIUM_HOST", "127.0.0.10")  # Avoid collisions with manually started Appium servers
    APPIUM_PORT = os.environ.get("APPIUM_PORT", "14723")

    QEMU_QMP_PATH = os.environ.get("QEMU_QMP_PATH", "/tmp/qmp.sock")
    QEMU_MONITOR_PATH = os.environ.get("QEMU_MONITOR_PATH", "/tmp/qemu-monitor.sock")

    QEMU_USB_BUS = os.environ.get("QEMU_USB_BUS", "xhci.0")
    QEMU_USB_SLOW_BUS = os.environ.get("QEMU_USB_BUS", "uhci.0")
    QEMU_USB_DEV_ID = os.environ.get("QEMU_USB_DEV_ID", "usbstick")

    DISABLE_SETUP = os.environ.get("DISABLE_SETUP", "0") == "1"
    DISABLE_SHUTDOWN = os.environ.get("DISABLE_SHUTDOWN", "0") == "1"

    LOGCAT_DIR = os.environ.get("LOGCAT_DIR", None)
