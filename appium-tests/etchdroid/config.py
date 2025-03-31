import os


class Config:
    APPIUM_HOST = os.environ.get("APPIUM_HOST", "http://127.0.0.1:4723")
    QEMU_QMP_PATH = os.environ.get("QEMU_QMP_PATH")
    QEMU_MONITOR_PATH = os.environ.get("QEMU_MONITOR_PATH")
