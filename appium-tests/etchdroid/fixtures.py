import os
import traceback
from typing import Generator

import appium
import pytest
from appium.options.android import UiAutomator2Options
from appium.webdriver.appium_service import AppiumService
from appium.webdriver.client_config import AppiumClientConfig

from etchdroid import package_name
from etchdroid.config import Config
from etchdroid.qemu import QEMUController
from etchdroid.utils import execute_script


@pytest.fixture(scope="session", autouse=True)
def appium_service():
    if not os.path.exists(Config.ANDROID_HOME):
        raise RuntimeError(f"ANDROID_HOME environment variable is not set or points to an invalid directory")
    os.environ["ANDROID_HOME"] = Config.ANDROID_HOME

    service = AppiumService()
    service.start(
        # Check the output of `appium server --help` for the complete list of
        # server command line arguments
        args=["--address", Config.APPIUM_HOST, "-p", Config.APPIUM_PORT, "--allow-insecure=adb_shell"],
        timeout_ms=20000,
    )
    yield service
    service.stop()


@pytest.fixture(scope="function")
def driver(appium_service) -> Generator[appium.webdriver.Remote, None, None]:
    options = UiAutomator2Options()
    options.app_package = package_name
    client_config = AppiumClientConfig(remote_server_addr=f"http://{Config.APPIUM_HOST}:{Config.APPIUM_PORT}")
    _driver = appium.webdriver.Remote(
        options=UiAutomator2Options(),
        client_config=client_config,
    )

    if not Config.DISABLE_SETUP:
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
        if not Config.DISABLE_SHUTDOWN:
            _driver.terminate_app(package_name)
    except Exception:
        pass
    finally:
        _driver.quit()


@pytest.fixture(scope="session")
def qemu() -> Generator[QEMUController, None, None]:
    if not os.path.exists(Config.QEMU_QMP_PATH) or not os.path.exists(Config.QEMU_MONITOR_PATH):
        pytest.skip(
            "QEMU sockets do not exist, make sure you specify QEMU_QMP_PATH and QEMU_MONITOR_PATH in your "
            "environment variables."
        )

    with QEMUController(qmp_path=Config.QEMU_QMP_PATH, monitor_path=Config.QEMU_MONITOR_PATH) as qemu:
        yield qemu
