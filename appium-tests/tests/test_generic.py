from time import sleep

import appium.webdriver

from etchdroid import actions as app
from etchdroid.fixtures import appium_service, driver
from etchdroid.utils import device_temp_sparse_file, used, wait_for_element

used(appium_service)


def test_regular_flow(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_regular_flow_", ".iso", "1800M") as image:
        app.basic_flow(driver, image.filename)
        app.wait_for_success(driver)


def test_skip_verification(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_skip_verification_", ".iso", "1000M") as image:
        app.basic_flow(driver, image.filename)
        app.skip_lay_flat_sheet(driver)

        skip_btn = app.get_skip_verify_button(driver)
        skip_btn.click()

        app.wait_for_success(driver)


def test_accept_notifications(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_accept_notifications_", ".iso", "1800M") as image:
        app.basic_flow(driver, image.filename)
        sure_btn = wait_for_element(
            driver,
            '//*[@resource-id="notifications_enable_button"]',
            timeout=5,
        )
        sure_btn.click()
        allow_btn = wait_for_element(
            driver,
            '//android.widget.Button[@resource-id="com.android.permissioncontroller:id/permission_allow_button"]',
            timeout=5,
        )
        allow_btn.click()

        skip_btn = app.get_skip_verify_button(driver)
        skip_btn.click()

        app.wait_for_success(driver)


def test_accept_then_deny_notifications(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_accept_then_deny_notifications_", ".iso", "1800M") as image:
        app.basic_flow(driver, image.filename)
        sure_btn = wait_for_element(
            driver,
            '//*[@resource-id="notifications_enable_button"]',
            timeout=5,
        )
        sure_btn.click()
        deny_btn = wait_for_element(
            driver,
            '//android.widget.Button[@resource-id="com.android.permissioncontroller:id/permission_deny_button"]',
            timeout=5,
        )
        deny_btn.click()
        sleep(0.5)
        sure_btn.click()

        enable_switch = wait_for_element(
            driver,
            '//android.widget.TextView[@text="All EtchDroid notifications"]',
            timeout=5,
        )
        enable_switch.click()
        driver.back()

        skip_btn = app.get_skip_verify_button(driver)
        skip_btn.click()

        app.wait_for_success(driver)


def test_windows_image_warning(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_windows_image_warning_", ".iso", "1M") as image:
        app.open_file(driver, image.path)
        wait_for_element(driver, '//android.view.View[@resource-id="confirmWindowsAlert"]')


def test_image_too_large(driver: appium.webdriver.Remote):
    with device_temp_sparse_file(driver, "etchdroid_test_image_too_large_", ".iso", "100G") as image:
        app.open_file(driver, image.path)
        app.select_first_usb_device_if_multiple(driver, timeout=5)
        app.grant_usb_permission(driver)
        app.confirm_write_image(driver)
        app.skip_lay_flat_sheet(driver)
        app.wait_for_fatal_error(driver)
