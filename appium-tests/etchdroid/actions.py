from appium.webdriver import Remote
from selenium.common import (
    TimeoutException,
)

from etchdroid import package_name
from etchdroid.utils import wait_for_element, run_adb_command


def basic_flow(driver: Remote, image_filename: str):
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


def select_first_usb_device_if_multiple(driver: Remote):
    try:
        usb_device = wait_for_element(driver, '//*[@content-desc="USB drive"]', timeout=1)
        usb_device.click()
    except TimeoutException:
        pass


def grant_usb_permission(driver: Remote):
    grant_btn = wait_for_element(driver, '//*[@resource-id="grantUsbPermissionButton"]')
    grant_btn.click()

    try:
        accept_usb_permission(driver)
    except TimeoutException:
        pass


def accept_usb_permission(driver: Remote):
    ok_btn = wait_for_element(driver, '//*[@text="OK"]', timeout=1)
    ok_btn.click()


def confirm_write_image(driver: Remote):
    write_image_btn = wait_for_element(driver, '//*[@resource-id="writeImageButton"]')
    write_image_btn.click()


def skip_lay_flat_sheet(driver: Remote):
    try:
        lay_flat_skip_btn = wait_for_element(driver, '//android.widget.TextView[@resource-id="layFlatSkipButton"]')
        lay_flat_skip_btn.click()
    except TimeoutException:
        pass


def wait_for_success(driver: Remote, timeout: int = 60):
    wait_for_element(driver, '//android.widget.TextView[@resource-id="success_write_title"]', timeout=timeout)


def wait_for_fatal_error(driver: Remote, timeout: int = 60):
    wait_for_element(driver, '//android.widget.TextView[@resource-id="fatal_error_title"]', timeout=timeout)


def wait_for_write_progress(driver: Remote, timeout: int = 60):
    wait_for_element(driver, '//android.widget.TextView[@resource-id="write_progress_title"]', timeout=timeout)


def get_skip_verify_button(driver: Remote, timeout: int = 60):
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
