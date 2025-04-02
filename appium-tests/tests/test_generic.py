import appium.webdriver

from etchdroid import actions as app
from etchdroid.fixtures import appium_service, driver
from etchdroid.utils import device_temp_sparse_file, used, wait_for_element

used(appium_service)


def test_regular_flow(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_image_", ".iso", "1800M"):
        app.basic_flow(driver, "etchdroid_test_image_", ".iso")
        app.wait_for_success(driver)


def test_skip_verification(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_image_", ".iso", "1000M"):
        app.basic_flow(driver, "etchdroid_test_image_", ".iso")
        app.skip_lay_flat_sheet(driver)

        skip_btn = app.get_skip_verify_button(driver)
        skip_btn.click()

        app.wait_for_success(driver)


def test_windows_image_warning(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_image_windows_", ".iso", "1M") as image:
        app.open_file(driver, image)
        wait_for_element(driver, '//android.view.View[@resource-id="confirmWindowsAlert"]')


def test_image_too_large(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_image_too_large_", ".iso", "100G") as image:
        app.open_file(driver, image)
        app.select_first_usb_device_if_multiple(driver)
        app.grant_usb_permission(driver)
        app.confirm_write_image(driver)
        app.skip_lay_flat_sheet(driver)
        app.wait_for_fatal_error(driver)
