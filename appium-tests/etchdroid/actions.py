from appium.webdriver import Remote
from selenium.common import (
    StaleElementReferenceException,
    TimeoutException,
)

from etchdroid import package_name
from etchdroid.utils import wait_for_element, run_adb_command


def basic_flow(driver: Remote, image_filename: str):
    tap_write_image(driver)
    # The tap is occasionally swallowed when the device list recomposes under it (e.g.
    # right after a test hot-plugged a USB device); if the picker didn't open, tap again.
    try:
        wait_for_element(driver, '//*[@content-desc="Search"]', timeout=5)
    except TimeoutException:
        tap_write_image(driver)
    find_and_open_file(driver, image_filename)
    select_first_usb_device_if_multiple(driver)
    grant_usb_permission(driver)
    confirm_write_image(driver)
    skip_lay_flat_sheet(driver)


def tap_write_image(driver: Remote):
    btn = wait_for_element(driver, '//*[@resource-id="writeImageCTA"]')
    btn.click()


def find_and_open_file(driver: Remote, filename: str):
    search_btn = wait_for_element(driver, '//*[@content-desc="Search"]')
    search_btn.click()
    search_field = wait_for_element(driver, "//android.widget.AutoCompleteTextView")
    search_field.send_keys(f"{filename}")
    arch_iso = wait_for_element(
        driver,
        f'//android.widget.TextView[@text="{filename}"]',
        5,
    )
    arch_iso.click()


def select_first_usb_device_if_multiple(driver: Remote, timeout: int = 1):
    try:
        usb_device = wait_for_element(driver, '//*[@content-desc="USB drive"]', timeout=timeout)
        usb_device.click()
    except TimeoutException:
        pass


def grant_usb_permission(driver: Remote):
    # The tap is swallowed if it lands while the confirmation activity is still settling,
    # and then nothing requests permission at all. Retry, but keep the dialog wait short:
    # Android often grants silently, and then no dialog is ever coming. The grant button
    # disappearing is the signal that permission landed, however it was granted.
    for _ in range(3):
        try:
            grant_btn = wait_for_element(driver, '//*[@resource-id="grantUsbPermissionButton"]', timeout=2)
        except TimeoutException:
            return
        try:
            grant_btn.click()
            accept_usb_permission(driver, timeout=2)
            return
        except (TimeoutException, StaleElementReferenceException):
            continue


def accept_usb_permission(driver: Remote, timeout: float = 1):
    ok_btn = wait_for_element(driver, '//*[@text="OK"]', timeout=timeout)
    ok_btn.click()


def confirm_write_image(driver: Remote):
    # Like the other taps on this screen, this one can be swallowed while the UI settles.
    # Confirm it took by waiting for what must follow: the lay-flat sheet, or the progress
    # screen when the sheet auto-proceeds (a device with a working gravity sensor).
    for _ in range(3):
        write_image_btn = wait_for_element(driver, '//*[@resource-id="writeImageButton"]')
        try:
            write_image_btn.click()
            wait_for_element(
                driver,
                '//android.widget.TextView[@resource-id="layFlatSkipButton"] | '
                '//android.widget.TextView[@resource-id="write_progress_title"]',
                timeout=5,
            )
            return
        except (TimeoutException, StaleElementReferenceException):
            continue


def skip_lay_flat_sheet(driver: Remote):
    # A missing button means the sheet auto-proceeded (the gravity sensor read flat) and
    # the flow has already moved on. A stale click, however, must be retried: it just
    # means the sheet moved under the tap while animating in, and if the sensor never
    # reads flat (CI), nobody else will ever fire the sheet's onReady.
    for _ in range(3):
        try:
            lay_flat_skip_btn = wait_for_element(driver, '//android.widget.TextView[@resource-id="layFlatSkipButton"]')
            lay_flat_skip_btn.click()
            return
        except TimeoutException:
            return
        except StaleElementReferenceException:
            continue


def wait_for_success(driver: Remote, timeout: int = 120):
    wait_for_element(driver, '//android.widget.TextView[@resource-id="success_write_title"]', timeout=timeout)


def wait_for_fatal_error(driver: Remote, timeout: int = 120):
    wait_for_element(driver, '//android.widget.TextView[@resource-id="fatal_error_title"]', timeout=timeout)


def wait_for_write_progress(driver: Remote, timeout: int = 120):
    wait_for_element(driver, '//android.widget.TextView[@resource-id="write_progress_title"]', timeout=timeout)


def get_skip_verify_button(driver: Remote, timeout: int = 120):
    return wait_for_element(driver, '//*[@resource-id="skip_verification_button"]', timeout=timeout)


def open_file(driver: Remote, file_name: str):
    """
    Open a file in the EtchDroid app. Unfortunately, since EtchDroid does not request storage permissions, the file
    won't be readable. It can still read the size and file name.

    :param driver: The Appium driver instance.
    :param file_name: The full path to the file to open.
    """
    run_adb_command(
        driver,
        "am",
        "start-activity",
        "-a",
        "android.intent.action.VIEW",
        f"-n{package_name}/.ui.MainActivity",
        "-d",
        f"file://{file_name}",
        "--grant-persistable-uri-permission",
        "--grant-read-uri-permission",
    )
