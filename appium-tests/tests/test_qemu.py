import appium.webdriver
import pytest

from etchdroid.fixtures import appium_service, driver, qemu
from etchdroid.qemu import QEMUController
from etchdroid.utils import used

used(appium_service)


@pytest.mark.qemu
def test_basic_flow(driver: appium.webdriver.Remote, qemu: QEMUController):
    pass
