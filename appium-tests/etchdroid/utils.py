import appium.webdriver
import etchdroid
import re
import tempfile
from appium.webdriver.common.appiumby import AppiumBy
from collections import namedtuple
from contextlib import contextmanager
from pathlib import Path
from selenium.common import ElementNotVisibleException, NoSuchElementException
from selenium.webdriver.support.wait import WebDriverWait
from typing import Any, Generator


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


def get_adb_udid(driver: appium.webdriver.Remote) -> str:
    return driver.capabilities["deviceUDID"]


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


def grant_permissions(driver: appium.webdriver.Remote, permissions: list[str]) -> None:
    run_adb_command(
        driver,
        "pm",
        "grant",
        etchdroid.package_name,
        *permissions,
    )


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


# threadtime format: "MM-DD HH:MM:SS.mmm  PID  TID LEVEL TAG: msg"
_LOGCAT_PID = re.compile(r"^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+(\d+)\s+\d+\s+\S")


def write_app_filtered_logcat(full_log: Path, out_log: Path, package: str) -> None:
    """
    Write a copy of a full threadtime logcat containing only lines relevant to the app:
    its own log lines (matched by PID, discovered from "Start proc <pid>:<package>"
    lines, which also covers clearApp/restarts) and any line naming the package.

    Native crash tombstones are logged by debuggerd under the crash_dump PID, not the
    app's, so they can't be matched by PID. Instead, each contiguous block of "F DEBUG"
    tombstone lines is kept as a whole iff the block references the package — this keeps
    the full backtrace (including non-package frames) while excluding unrelated system
    crashes (e.g. media.swcodec aborting on boot).
    """
    start_proc = re.compile(r"Start proc (\d+):" + re.escape(package))
    text = full_log.read_text(errors="replace")
    app_pids = set(start_proc.findall(text))

    out: list[str] = []
    block: list[str] = []

    def flush_block() -> None:
        if block and any(package in line for line in block):
            out.extend(block)
        block.clear()

    block_pid: str | None = None
    for line in text.splitlines(keepends=True):
        m = _LOGCAT_PID.match(line)
        if " F DEBUG " in line:  # native tombstone line (debuggerd, Fatal level, tag DEBUG)
            pid = m.group(1) if m else None
            if block and pid != block_pid:  # new tombstone (different process)
                flush_block()
            block_pid = pid
            block.append(line)
            continue
        flush_block()
        if (m and m.group(1) in app_pids) or package in line:
            out.append(line)
    flush_block()

    out_log.write_text("".join(out))


# logcat threadtime format: "MM-DD HH:MM:SS.mmm  PID  TID  P  TAG: message"
_LOGCAT_FULL = re.compile(r"^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+\d+\s+\d+\s+([VDIWEF])\s+([^:]*?):\s?(.*)$")

# Tags that are pure environment noise on these emulator/CI runs. Substring match on
# the tag, case-insensitive.
_NOISE_TAGS = (
    "audiosystem-jni", "audiomanager", "as.audioservice", "audioflinger",
    "c2store", "ccodec", "ccodecconfig", "ccodecbuffers", "codec2",
    "mediaswcodec", "mediacodec", "bufferpoolaccessor",
    "thermal", "compatibilityinfo", "compatibilitychangereporter",
    "providerscache", "carriersvcbindhelper", "openglrenderer",
    "connectivityservice", "ethernetnetworkfactory", "networkfactory",
    "phoneswitcher", "windowmanager", "inputmanager-jni", "bpbinder",
    "ringtoneplayer", "nlservice", "b/260135164",
    "mediaprovider", "parcel", "ashmem", "taskpersister",
    "bptransactioncompletedlistener", "activitytaskmanager", "activitymanager",
    "gms", "appsfilter",
    # graphics / vsync noise that survives app-scoped filtering
    "egl-main", "choreographer", "frameevents", "colorutils",
    "hidlservicemanagement", "trafficstats", "usagestatsservice",
)

# Lines this strong always survive filtering, even with a noisy tag, and are never
# dropped by the byte budget — this is the crash signal we care about.
_ALWAYS_KEEP = re.compile(
    r"sigsegv|fatal signal|libusb|libaums|jahnen|backtrace|app died|"
    r"androidruntime|has died|\bF DEBUG\b|tombstone",
    re.IGNORECASE,
)


def denoise_logcat(text: str, max_bytes: int = 20000) -> str:
    """
    Denoise a threadtime logcat so a small LLM can ingest it: keep the signal (errors,
    fatals, app crashes), drop the audio/codec/compat firehose, collapse consecutive
    duplicates and truncate to a byte budget (default ~6K tokens). Crash/backtrace
    lines are always emitted in full regardless of the budget.

    Intended as a thin second pass after write_app_filtered_logcat (the pid-scoped
    filter), but works on any logcat. Lossy on purpose — the full logs remain alongside.
    """
    # Pass 1: keep signal lines, collapse consecutive duplicates, tag each as "critical"
    # (the crash/backtrace) or "normal". The crash happens late in the file, so plain
    # top-down truncation would drop it — instead we guarantee criticals in pass 2.
    kept: list[tuple[str, bool]] = []
    prev_key = None
    dup = 0

    def flush_dup() -> None:
        nonlocal dup
        if dup > 1 and kept:
            kept.append((f"    ... (x{dup} repeated)\n", kept[-1][1]))
        dup = 0

    for line in text.splitlines(keepends=True):
        critical = bool(_ALWAYS_KEEP.search(line))
        m = _LOGCAT_FULL.match(line)
        if m:
            prio, tag, msg = m.group(1), m.group(2), m.group(3)
            if not critical:
                if any(n in tag.strip().lower() for n in _NOISE_TAGS):
                    continue
                if prio not in ("E", "F", "W"):
                    continue
            key = (tag.strip(), msg.strip())
        else:
            if not critical:  # non-threadtime banner lines: only if they carry signal
                continue
            key = ("_raw", line.strip())

        if key == prev_key:
            dup += 1
            continue
        flush_dup()
        prev_key = key
        kept.append((line, critical))
    flush_dup()

    # Pass 2: always emit criticals; fill the remaining budget with normals in order.
    budget = max_bytes - sum(len(t) for t, c in kept if c)
    out: list[str] = []
    truncated = False
    for line, critical in kept:
        if critical:
            out.append(line)
        elif budget - len(line) >= 0:
            out.append(line)
            budget -= len(line)
        else:
            truncated = True
    if truncated:
        out.append("\n[... non-critical lines truncated to byte budget; "
                   "crash/backtrace lines retained in full ...]\n")
    return "".join(out)
