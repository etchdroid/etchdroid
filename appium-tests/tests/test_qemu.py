import hashlib
import tempfile
from pathlib import Path
from time import sleep
from typing import Generator

import appium.webdriver
import pytest

from etchdroid import actions as app
from etchdroid.config import Config
from etchdroid.fixtures import appium_service, driver, qemu, usb_write_speed_mbps
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

used(appium_service, usb_write_speed_mbps)


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
    wait_for_element(driver, '//android.widget.TextView[@resource-id="reconnect_usb_drive_title"]', 15)

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
    app.accept_usb_permission(driver)


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


@pytest.fixture(scope="function")
def raw_usb_drive(qemu: QEMUController, raw_disk_image: Path) -> Generator[tuple[str, Path], None, None]:
    # Disconnect existing USB device first
    device = qemu.get_block_device(Config.QEMU_USB_DEV_ID)
    qemu.device_del(Config.QEMU_USB_DEV_ID)
    sleep(0.5)

    raw_dev_id = f"{Config.QEMU_USB_DEV_ID}-raw"
    qemu.add_usb_drive(
        raw_dev_id,
        bus=Config.QEMU_USB_SLOW_BUS,
        file=raw_disk_image,
        format="raw",
    )

    sleep(2)

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

        app.accept_usb_permission(driver)

        skip_btn = app.get_skip_verify_button(driver)
        skip_btn.click()

        app.wait_for_success(driver)
