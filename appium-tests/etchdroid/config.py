import os


class Config:
    ANDROID_HOME = os.environ.get("ANDROID_HOME", os.path.expanduser("~/Android/Sdk"))
    APPIUM_HOST = os.environ.get("APPIUM_HOST", "127.0.0.10")  # Avoid collisions with manually started Appium servers
    APPIUM_PORT = os.environ.get("APPIUM_PORT", "14723")
    QEMU_QMP_PATH = os.environ.get("QEMU_QMP_PATH")
    QEMU_MONITOR_PATH = os.environ.get("QEMU_MONITOR_PATH")
