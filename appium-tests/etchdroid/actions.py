from appium.webdriver import Remote
from selenium.common import (
    TimeoutException,
)

from etchdroid.utils import wait_for_element, find_element


def tap_write_image(driver: Remote):
    btn = wait_for_element(driver, '//*[@resource-id="writeImageCTA"]')
    btn.click()


def find_and_open_file(driver: Remote, prefix: str, suffix: str):
    search_btn = wait_for_element(driver, '//*[@content-desc="Search"]')
    search_btn.click()
    search_field = wait_for_element(driver, "//android.widget.AutoCompleteTextView")
    search_field.send_keys(f"{prefix}\n")
    arch_iso = find_element(
        driver,
        f'//android.widget.TextView[starts-with(@text, "{prefix}") and ends-with(@text, "{suffix}")]',
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
        ok_btn = wait_for_element(driver, '//*[@text="OK"]', timeout=1)
        ok_btn.click()
    except TimeoutException:
        pass


def confirm_write_image(driver: Remote):
    write_image_btn = find_element(driver, '//*[@resource-id="writeImageButton"]')
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
