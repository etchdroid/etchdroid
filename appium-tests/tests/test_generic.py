import appium.webdriver

from etchdroid import actions as app
from etchdroid.fixtures import driver
from etchdroid.utils import device_temp_sparse_file, wait_for_element


def test_regular_flow(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_image_", ".iso", "1800M"):
        app.tap_write_image(driver)
        app.find_and_open_file(driver, "etchdroid_test_image_", ".iso")
        app.select_first_usb_device_if_multiple(driver)
        app.grant_usb_permission(driver)
        app.confirm_write_image(driver)
        app.skip_lay_flat_sheet(driver)
        app.wait_for_success(driver)


def test_windows_image_warning(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_image_windows_", ".iso", "1800M"):
        app.tap_write_image(driver)
        app.find_and_open_file(driver, "etchdroid_test_image_windows_", ".iso")

        wait_for_element(driver, '//android.view.View[@resource-id="confirmWindowsAlert"]')
