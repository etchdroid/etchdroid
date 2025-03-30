import appium.webdriver
import pytest

from etchdroid.fixtures import driver


@pytest.mark.qemu
def test_basic_flow(driver: appium.webdriver.Remote):
    pass
