import pytest
import traceback as _tb
from pathlib import Path
from selenium.common.exceptions import NoSuchElementException, TimeoutException

from etchdroid.config import Config

# UI-wait failures (element never appeared in time) are test/app-state signals, not
# bugs in the harness. For these we replace the firehose pytest/pluggy/selenium
# traceback with a concise message plus the denoised app logcat, which is what's
# actually useful when triaging a failure.
_EXPECTED_EXC = (TimeoutException, NoSuchElementException)
_CLEAN_REPORT = pytest.StashKey()


def _repo_frames(exc_tb) -> str:
    """The traceback entries that live in this repo (drops venv/pytest internals).

    Their source lines carry the awaited xpath, so this is the "what was it waiting
    for" context."""
    out = []
    for f in _tb.extract_tb(exc_tb):
        if "/site-packages/" in f.filename or "/.venv/" in f.filename:
            continue
        if "appium-tests" not in f.filename:
            continue
        loc = f.filename.split("appium-tests/", 1)[-1]
        out.append(f"  {loc}:{f.lineno} in {f.name}\n      {(f.line or '').strip()}")
    return "\n".join(out) or "  (no in-repo frames)"


def _selenium_message(exc) -> str:
    # str(TimeoutException) is multi-line and ends with a JS "at ..." stack; keep the
    # meaningful lines (e.g. the NoSuchElementError reason) and drop the JS frames.
    lines = [
        ln.strip()
        for ln in str(exc).splitlines()
        if ln.strip() and not ln.strip().startswith("at ") and ln.strip() not in ("Message:", "Stacktrace:")
    ]
    return " ".join(lines).strip() or exc.__class__.__name__


def _denoised_logcat(name: str) -> str:
    if not Config.LOGCAT_DIR:
        return "(LOGCAT_DIR not configured; no logcat captured)"
    path = Path(Config.LOGCAT_DIR) / f"{name}.app.denoised.log"
    try:
        text = path.read_text(errors="replace").strip()
    except OSError as e:
        # Denoising failed (or never ran) in the fixture — say so rather than dumping
        # the raw, un-denoised log.
        return f"(denoised logcat unavailable: {e})"
    return text or "(denoised logcat is empty)"


@pytest.hookimpl(wrapper=True)
def pytest_runtest_makereport(item, call):
    report = yield

    if (
        report.when == "call"
        and report.failed
        and call.excinfo is not None
        and call.excinfo.errisinstance(_EXPECTED_EXC)
    ):
        report.longrepr = (
            f"{call.excinfo.typename}: {_selenium_message(call.excinfo.value)}\n\n"
            f"{_repo_frames(call.excinfo.tb)}"
        )
        # The denoised logcat is written during teardown, so append it then.
        item.stash[_CLEAN_REPORT] = report
    elif report.when == "teardown":
        clean = item.stash.get(_CLEAN_REPORT, None)
        if clean is not None:
            clean.longrepr = (
                f"{clean.longrepr}\n\n"
                f"===== denoised app logcat: {item.name} =====\n"
                f"{_denoised_logcat(item.name)}"
            )

    return report
