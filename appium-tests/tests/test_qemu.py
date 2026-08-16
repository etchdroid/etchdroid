import hashlib
import tempfile
from pathlib import Path
from time import sleep, time
from typing import Generator

import appium.webdriver
import pytest
from selenium.common import TimeoutException

from etchdroid import actions as app
from etchdroid.config import Config
from etchdroid.fixtures import appium_service, driver, qemu
from etchdroid.qemu import QEMUController
from etchdroid.utils import (
    used,
    device_temp_sparse_file,
    mark,
    scaled_image_mb,
    wait_for_element,
    run_adb_command,
    grant_permissions,
)

used(appium_service)


def unplug_and_reconnect_usb(
    driver: appium.webdriver.Remote,
    qemu: QEMUController,
    device_id: str = Config.QEMU_USB_DEV_ID,
    bus: str = Config.QEMU_USB_BUS,
):
    mark("Unplugging USB device...")
    device = qemu.get_block_device(device_id)
    qemu.device_del(device_id)

    mark("Waiting for reconnect dialog...")
    try:
        wait_for_element(driver, '//android.widget.TextView[@resource-id="reconnect_usb_drive_title"]', 15)
    except TimeoutException:
        # Put the device back before failing, or every later test finds no drive at all.
        qemu.add_usb_drive(
            device_id,
            bus=bus,
            file=device["inserted"]["image"]["filename"],
            format=device["inserted"]["image"]["format"],
        )
        raise

    sleep(0.5)

    mark("Plugging USB device back in...")
    qemu.add_usb_drive(
        device_id,
        bus=bus,
        file=device["inserted"]["image"]["filename"],
        format=device["inserted"]["image"]["format"],
    )

    # Wait 3 seconds to ensure the emulated device doesn't spit out Unit Attention sense codes on init.
    # A patch should be submitted to libaums to handle this.
    sleep(3)

    mark("Accepting permission...")
    # Generous timeout: re-enumeration and the permission dialog can lag several seconds
    # while the guest is under heavy write I/O.
    app.accept_usb_permission(driver, timeout=15)


@pytest.fixture(scope="function")
def random_image_file(
    driver: appium.webdriver.Remote, usb_write_speed_mbps: float, request
) -> Generator[tuple[str, str, int], None, None]:
    """
    A random-data image in the guest's Downloads, sized so the write phase lasts
    ~TARGET_WRITE_SECONDS. Generated on-device (pushing this much data over Appium would
    take longer than the test); yields (remote path, sha256, size in bytes).
    """
    size_mb = scaled_image_mb(usb_write_speed_mbps)

    remote_path = tempfile.mktemp(prefix=f"etchdroid_{request.node.name}_", suffix=".iso", dir="/sdcard/Download/")
    run_adb_command(
        driver, "dd", "if=/dev/urandom", f"of={remote_path}", "bs=1M", f"count={size_mb}", timeout=600
    )
    sha256 = run_adb_command(driver, "sha256sum", remote_path, timeout=600)["stdout"].split()[0]

    yield remote_path, sha256, size_mb * 1024 * 1024

    run_adb_command(driver, "rm", "-f", remote_path)


@pytest.fixture(scope="function")
def raw_disk_image(qemu: QEMUController, usb_write_speed_mbps: float, request):
    with tempfile.TemporaryDirectory("etchdroid_qemu_test") as tmp_path:
        tmp_path = Path(tmp_path)

        # Room for the speed-scaled image (see random_image_file) plus margin. Sparse:
        # since the source image is random data, a zeroed target can't false-match it.
        size_bytes = (scaled_image_mb(usb_write_speed_mbps) + 64) * 1024 * 1024
        filename = tmp_path / f"etchdroid_{request.node.name}.img"
        with open(filename, "wb") as f:
            f.truncate(size_bytes)

        yield filename

        filename.unlink(missing_ok=True)


def wait_for_usb_enumeration(driver: appium.webdriver.Remote, timeout: float = 30):
    """
    Wait until the Android USB service reports the QEMU drive. A fixed sleep is not
    enough on slow CI hosts: if the flow starts while the framework has processed the
    detach but not yet the attach, the app closes the confirmation screen.
    """
    deadline = time() + timeout
    while time() < deadline:
        # dumpsys reflects the framework's view, which is what the app will see.
        out = run_adb_command(driver, "dumpsys", "usb", timeout=15)
        if "QEMU USB HARDDRIVE" in str(out):
            return
        sleep(1)
    raise TimeoutError("Guest did not enumerate the USB drive in time")


@pytest.fixture(scope="function")
def raw_usb_drive(
    driver: appium.webdriver.Remote, qemu: QEMUController, raw_disk_image: Path
) -> Generator[tuple[str, Path], None, None]:
    # Disconnect existing USB device first
    device = qemu.get_block_device(Config.QEMU_USB_DEV_ID)
    qemu.device_del(Config.QEMU_USB_DEV_ID)
    sleep(0.5)

    raw_dev_id = f"{Config.QEMU_USB_DEV_ID}-raw"
    try:
        qemu.add_usb_drive(
            raw_dev_id,
            bus=Config.QEMU_USB_SLOW_BUS,
            file=raw_disk_image,
            format="raw",
        )

        wait_for_usb_enumeration(driver)
        # Give Android a moment to broadcast the attach to the app on top of enumeration.
        sleep(2)
    except Exception:
        # A fixture that fails during setup never runs its teardown; put the original
        # stick back or every later test finds no USB drive at all.
        # noinspection PyBroadException
        try:
            qemu.device_del(raw_dev_id)
        except Exception:
            pass
        qemu.add_usb_drive(
            Config.QEMU_USB_DEV_ID,
            bus=Config.QEMU_USB_BUS,
            file=device["inserted"]["image"]["filename"],
            format=device["inserted"]["image"]["format"],
        )
        raise

    yield raw_dev_id, raw_disk_image

    # Restore the original USB device
    qemu.device_del(raw_dev_id)
    qemu.add_usb_drive(
        Config.QEMU_USB_DEV_ID,
        bus=Config.QEMU_USB_BUS,
        file=device["inserted"]["image"]["filename"],
        format=device["inserted"]["image"]["format"],
    )
    sleep(0.5)


def verify_written_image(sha256: str, size_bytes: int, raw_blockdev: Path):
    digest = hashlib.sha256()
    with open(raw_blockdev, "rb") as f:
        remaining = size_bytes
        while remaining > 0:
            chunk = f.read(min(remaining, 16 * 1024 * 1024))
            if not chunk:
                break
            digest.update(chunk)
            remaining -= len(chunk)
    assert digest.hexdigest() == sha256, "Written data does not match expected data"


@pytest.mark.qemu
def test_unplug_xhci(driver: appium.webdriver.Remote, qemu: QEMUController, usb_write_speed_mbps: float):
    # One window is enough for both unplugs: the first happens during the write, the
    # second during verification. A bigger image only pushes the job past the 120s cap
    # on wait_for_success, since each resume replays work.
    size = f"{scaled_image_mb(usb_write_speed_mbps)}M"
    with device_temp_sparse_file(driver, "etchdroid_test_unplug_xhci_", ".iso", size) as image:
        app.basic_flow(driver, image.filename)

        mark("Waiting for write progress...")
        app.wait_for_write_progress(driver)

        unplug_and_reconnect_usb(driver, qemu)
        app.get_skip_verify_button(driver)
        unplug_and_reconnect_usb(driver, qemu)
        app.wait_for_success(driver)


@pytest.mark.qemu
def test_regular_flow_with_random_data_uhci(
    driver: appium.webdriver.Remote,
    random_image_file: tuple[str, str, int],
    raw_usb_drive: tuple[str, Path],
):
    remote_image_path, image_sha256, image_size = random_image_file
    _, raw_disk_image_path = raw_usb_drive
    remote_fname = Path(remote_image_path).name

    app.basic_flow(driver, remote_fname)
    app.wait_for_success(driver)

    verify_written_image(image_sha256, image_size, raw_disk_image_path)


@pytest.mark.qemu
def test_unplug_with_random_data_uhci(
    driver: appium.webdriver.Remote,
    random_image_file: tuple[str, str, int],
    raw_usb_drive: tuple[str, Path],
    qemu: QEMUController,
):
    remote_image_path, image_sha256, image_size = random_image_file
    raw_device_id, raw_disk_image_path = raw_usb_drive
    remote_fname = Path(remote_image_path).name

    app.basic_flow(driver, remote_fname)

    mark("Waiting for write progress...")
    app.wait_for_write_progress(driver)

    unplug_and_reconnect_usb(driver, qemu, raw_device_id, Config.QEMU_USB_SLOW_BUS)
    app.get_skip_verify_button(driver)
    unplug_and_reconnect_usb(driver, qemu, raw_device_id, Config.QEMU_USB_SLOW_BUS)
    app.wait_for_success(driver)

    verify_written_image(image_sha256, image_size, raw_disk_image_path)


@pytest.mark.qemu
def test_unplug_resume_from_notification(
    driver: appium.webdriver.Remote, qemu: QEMUController, usb_write_speed_mbps: float
):
    grant_permissions(driver, ["android.permission.POST_NOTIFICATIONS"])

    size = f"{scaled_image_mb(usb_write_speed_mbps)}M"
    with device_temp_sparse_file(driver, "etchdroid_test_unplug_resume_from_notification_", ".iso", size) as image:
        app.basic_flow(driver, image.filename)
        app.wait_for_write_progress(driver)

        # Unplug USB device
        device = qemu.get_block_device(Config.QEMU_USB_DEV_ID)
        qemu.device_del(Config.QEMU_USB_DEV_ID)

        # Wait for reconnect dialog
        wait_for_element(driver, '//android.widget.TextView[@resource-id="reconnect_usb_drive_title"]', 15)

        # Close app from recents
        driver.keyevent(187)  # KEYCODE_APP_SWITCH
        sleep(0.5)
        driver.keyevent(67)  # KEYCODE_DEL
        sleep(0.5)
        driver.keyevent(3)  # KEYCODE_HOME

        driver.open_notifications()

        notification = wait_for_element(
            driver,
            f'//android.widget.TextView[@resource-id="android:id/title" and @text="Action required"]',
            timeout=5,
        )
        notification.click()

        sleep(0.5)

        # Reconnect USB device
        qemu.add_usb_drive(
            Config.QEMU_USB_DEV_ID,
            bus=Config.QEMU_USB_BUS,
            file=device["inserted"]["image"]["filename"],
            format=device["inserted"]["image"]["format"],
        )

        # Wait 3 seconds to ensure the emulated device doesn't spit out Unit Attention sense codes on init.
        # A patch should be submitted to libaums to handle this.
        sleep(3)

        app.accept_usb_permission(driver, timeout=15)

        skip_btn = app.get_skip_verify_button(driver)
        skip_btn.click()

        app.wait_for_success(driver)
