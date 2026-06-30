import appium
import os
import pytest
import signal
import subprocess
import traceback
from appium.options.android import UiAutomator2Options
from appium.webdriver.appium_service import AppiumService
from appium.webdriver.client_config import AppiumClientConfig
from etchdroid import package_name
from etchdroid.config import Config
from etchdroid.qemu import QEMUController
from etchdroid.utils import denoise_logcat, execute_script, get_adb_udid, write_app_filtered_logcat
from pathlib import Path
from typing import Generator

adb = f"{Config.ANDROID_HOME}/platform-tools/adb"


@pytest.fixture(scope="session", autouse=True)
def appium_service():
    if not os.path.exists(Config.ANDROID_HOME):
        raise RuntimeError(f"ANDROID_HOME environment variable is not set or points to an invalid directory")
    os.environ["ANDROID_HOME"] = Config.ANDROID_HOME

    # Try to find the local Appium main script to avoid requiring it in the PATH
    # The file is expected to be in appium-tests/node_modules/appium/build/lib/main.js
    # but since this file is in appium-tests/etchdroid/fixtures.py, we go up two levels.
    project_root = Path(__file__).parent.parent
    local_appium = project_root / "node_modules" / "appium" / "build" / "lib" / "main.js"

    service = AppiumService()
    kwargs = {
        "args": ["--address", Config.APPIUM_HOST, "-p", Config.APPIUM_PORT, "--allow-insecure=uiautomator2:adb_shell"],
        "timeout_ms": 20000,
    }
    if local_appium.exists():
        kwargs["main_script"] = str(local_appium)

    service.start(**kwargs)
    yield service
    service.stop()


@pytest.fixture(scope="function")
def driver(appium_service, request) -> Generator[appium.webdriver.Remote, None, None]:
    print(f"\n[DEBUG] Starting driver fixture for {request.node.name}")
    options = UiAutomator2Options()
    options.app_package = package_name
    options.app_activity = ".ui.MainActivity"
    client_config = AppiumClientConfig(remote_server_addr=f"http://{Config.APPIUM_HOST}:{Config.APPIUM_PORT}")
    print("[DEBUG] Connecting to Appium...")
    _driver = appium.webdriver.Remote(
        options=options,
        client_config=client_config,
    )
    # noinspection PyBroadException
    try:
        status = _driver.get_status()
        build_info = status.get("build", {})
        appium_version = build_info.get("version", "unknown")
        print(f"[DEBUG] Connected to Appium: {appium_version}")
    except Exception:
        print("[DEBUG] Connected (failed to retrieve version info).")

    logcat = None
    logcat_file = None
    try:
        if Config.LOGCAT_DIR:
            print(f"[DEBUG] Starting logcat to {Config.LOGCAT_DIR}")
            logcat_dir = Path(Config.LOGCAT_DIR)
            logcat_dir.mkdir(parents=True, exist_ok=True)
            logcat_file = open(logcat_dir / f"{request.node.name}.log", "wb")
            logcat = subprocess.Popen(
                [adb, "-s", get_adb_udid(_driver), "logcat", "-v", "threadtime", "-T", "1"],
                stdout=logcat_file,
                stderr=subprocess.STDOUT,
            )

        if not Config.DISABLE_SETUP:
            print("[DEBUG] Performing setup (clearApp, startActivity)...")
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

        print("[DEBUG] Yielding driver.")
        yield _driver

        # noinspection PyBroadException
        try:
            if not Config.DISABLE_SHUTDOWN:
                print("[DEBUG] Terminating app...")
                _driver.terminate_app(package_name)
        except Exception:
            pass
    finally:
        print("[DEBUG] Cleaning up...")
        if Config.LOGCAT_DIR:
            if logcat is not None:
                logcat.send_signal(signal.SIGINT)
                try:
                    logcat.wait(timeout=5)
                except subprocess.TimeoutExpired:
                    logcat.terminate()
                    try:
                        logcat.wait(timeout=5)
                    except subprocess.TimeoutExpired:
                        logcat.kill()
            if logcat_file is not None:
                logcat_file.close()
                # noinspection PyBroadException
                try:
                    logcat_dir = Path(Config.LOGCAT_DIR)
                    app_log = logcat_dir / f"{request.node.name}.app.log"
                    write_app_filtered_logcat(
                        logcat_dir / f"{request.node.name}.log",
                        app_log,
                        package_name,
                    )
                    # Lossy, LLM-sized denoised second pass over the app-scoped log.
                    (logcat_dir / f"{request.node.name}.app.denoised.log").write_text(
                        denoise_logcat(app_log.read_text(errors="replace"))
                    )
                except Exception:
                    traceback.print_exc()

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
