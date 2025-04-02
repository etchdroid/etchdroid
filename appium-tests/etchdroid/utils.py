import tempfile
from collections import namedtuple
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Generator

import appium.webdriver
from appium.webdriver.common.appiumby import AppiumBy
from selenium.common import ElementNotVisibleException, NoSuchElementException
from selenium.webdriver.support.wait import WebDriverWait


def used(*a, **k):
    """
    Used to mark a function as used, so that it is not removed by the linter.
    """
    pass


def execute_script(driver: appium.webdriver.Remote, script: str, *args: Any) -> Any:
    return driver.execute_script(script, *args)


def get_wait(driver: appium.webdriver.Remote, timeout: float = 3) -> WebDriverWait:
    """

    :rtype: object
    """
    return WebDriverWait(
        driver,
        timeout,
        ignored_exceptions=(ElementNotVisibleException, NoSuchElementException),
    )


def wait_for_element(
    driver: appium.webdriver.Remote,
    xpath: str,
    timeout: float = 3,
) -> appium.webdriver.WebElement:
    wait = get_wait(driver, timeout)
    return wait.until(
        lambda x: x.find_element(
            by=AppiumBy.XPATH,
            value=xpath,
        )
    )


def find_element(
    driver: appium.webdriver.Remote,
    xpath: str,
) -> appium.webdriver.WebElement:
    return wait_for_element(driver, xpath, timeout=0.5)


def run_adb_command(
    driver: appium.webdriver.Remote,
    command: str,
    *args: str,
    include_stderr: bool = True,
    timeout: float = 5,
) -> dict:
    """
    Run an adb command on the device.

    :param driver: The Appium driver instance.
    :param command: The adb command to run.
    :param args: Additional arguments for the adb command.
    :param include_stderr: Whether to include stderr in the output.
    :param timeout: Timeout for the adb command.
    :return: The result of the adb command.
    """

    result = execute_script(
        driver,
        "mobile: shell",
        {
            "command": command,
            "args": args,
            "includeStderr": include_stderr,
            "timeout": timeout * 1000,
        },
    )
    if not result:
        raise RuntimeError("Failed to run adb command")
    return result


PathFilenamePair = namedtuple("PathFilenamePair", ["path", "filename"])


@contextmanager
def device_temp_sparse_file(
    driver: appium.webdriver.Remote,
    name_prefix: str,
    name_suffix: str,
    size: int | str,
    path: str = "/sdcard/Download/",
) -> Generator[PathFilenamePair, None, None]:
    temp_file_name = tempfile.mktemp(prefix=name_prefix, suffix=name_suffix, dir=path)
    run_adb_command(
        driver,
        "truncate",
        "-s",
        str(size),
        temp_file_name,
    )
    try:
        yield PathFilenamePair(temp_file_name, Path(temp_file_name).name)
    finally:
        run_adb_command(driver, "rm", "-f", temp_file_name)
