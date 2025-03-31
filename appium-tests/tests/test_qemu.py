import appium.webdriver
import pytest

from etchdroid.fixtures import driver, qemu
from etchdroid.qemu import QEMUController


@pytest.mark.qemu
def test_basic_flow(driver: appium.webdriver.Remote, qemu: QEMUController):
    pass
