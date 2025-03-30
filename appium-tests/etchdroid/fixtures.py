import os
import traceback
from typing import Generator

import appium
import pytest
from appium.options.android import UiAutomator2Options
from appium.webdriver.client_config import AppiumClientConfig

from etchdroid import package_name
from etchdroid.utils import execute_script


@pytest.fixture(scope="function")
def driver() -> Generator[appium.webdriver.Remote, None, None]:
    options = UiAutomator2Options()
    options.app_package = package_name
    client_config = AppiumClientConfig(remote_server_addr=os.environ.get("APPIUM_HOST", "http://127.0.0.1:4723"))
    _driver = appium.webdriver.Remote(
        options=UiAutomator2Options(),
        client_config=client_config,
    )

    # noinspection PyBroadException
    try:
        execute_script(_driver, "mobile: clearApp", {"appId": package_name})
    except Exception:
        traceback.print_exc()

    execute_script(
        _driver,
        "mobile: startActivity",
        {
            "component": f"{package_name}/.ui.MainActivity",
        },
    )

    yield _driver

    # noinspection PyBroadException
    try:
        _driver.terminate_app(package_name)
    except Exception:
        pass
    finally:
        _driver.quit()
