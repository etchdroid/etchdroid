#!/usr/bin/env python3
"""Make a CI screen recording navigable, without shortening it.

The recording covers the whole job including early boot, which is exactly what you want when
triaging and exactly what makes it tedious to scrub. This adds:

  - chapters, one per test, titled with the test's outcome
  - a soft subtitle track, one cue per marked moment

Matroska carries both natively and mpv/VLC navigate chapters out of the box, so this is a single
`-c copy` remux: no re-encode, nothing burned into the picture, and the original stays playable
in anything that ignores the extras.

Timestamps come from the markers file written by etchdroid.utils.mark during the test run, which
records wall-clock epochs. The recording's own t=0 is whenever ffmpeg started, which
qemu-kvm-action reports; subtracting gives offsets into the video.

    annotate-recording.py <recording.mkv> <markers.tsv> <ffmpeg-start-epoch> [-o out.mkv]

Missing inputs are not an error: the recording is a debugging aid, so a job that produced no
markers should still upload its video.
"""

import argparse
import os
import subprocess
import sys
import tempfile
from pathlib import Path

# Outcome words pytest reports, used to fold "PASSED test_foo" events into the chapter title for
# test_foo so a failure is findable from the chapter list alone.
OUTCOMES = ("PASSED", "FAILED", "SKIPPED", "ERROR", "XFAILED", "XPASSED")


def parse_markers(text: str) -> list[tuple[float, str, str]]:
    """Parse `<epoch>\t<kind>\t<label>` lines, skipping anything malformed."""
    marks = []
    for line in text.splitlines():
        parts = line.split("\t", 2)
        if len(parts) != 3:
            continue
        try:
            marks.append((float(parts[0]), parts[1], parts[2]))
        except ValueError:
            continue
    return sorted(marks, key=lambda m: m[0])


def to_offsets(marks, start: float, duration: float) -> list[tuple[float, str, str]]:
    """Epochs to offsets into the video, clamped to it."""
    return [(min(max(t - start, 0.0), duration), kind, label) for t, kind, label in marks]


def build_chapters(marks, duration: float) -> list[tuple[float, float, str]]:
    """One chapter per test marker, running until the next one."""
    outcomes = {}
    for _, kind, label in marks:
        head, _, name = label.partition(" ")
        if kind != "test" and head in OUTCOMES and name:
            outcomes[name] = head

    tests = [(t, label) for t, kind, label in marks if kind == "test"]
    chapters = []
    for i, (start, name) in enumerate(tests):
        end = tests[i + 1][0] if i + 1 < len(tests) else duration
        if end - start < 0.001:
            continue
        title = f"{name} \u2014 {outcomes[name]}" if name in outcomes else name
        chapters.append((start, end, title))
    return chapters


def build_cues(marks, duration: float) -> list[tuple[float, float, str]]:
    """One subtitle cue per marker, held until the next one."""
    cues = []
    for i, (start, _, label) in enumerate(marks):
        end = marks[i + 1][0] if i + 1 < len(marks) else duration
        if end - start < 0.001:
            continue
        cues.append((start, end, label))
    return cues


def srt_timecode(seconds: float) -> str:
    ms = round(seconds * 1000)
    h, ms = divmod(ms, 3600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def render_srt(cues) -> str:
    out = []
    for i, (start, end, text) in enumerate(cues, 1):
        out.append(f"{i}\n{srt_timecode(start)} --> {srt_timecode(end)}\n{text}\n")
    return "\n".join(out)


def render_ffmetadata(chapters) -> str:
    def esc(value: str) -> str:
        for ch in "\\=;#\n":
            value = value.replace(ch, "\\" + ch)
        return value

    out = [";FFMETADATA1"]
    for start, end, title in chapters:
        out.append("[CHAPTER]")
        out.append("TIMEBASE=1/1000")
        out.append(f"START={round(start * 1000)}")
        out.append(f"END={round(end * 1000)}")
        out.append(f"title={esc(title)}")
    return "\n".join(out) + "\n"


def video_duration(path: Path) -> float:
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(path)],
        capture_output=True,
        text=True,
    )
    return float(probe.stdout.strip())


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("recording", nargs="?", type=Path)
    parser.add_argument("markers", nargs="?", type=Path)
    parser.add_argument("start", nargs="?", type=float, help="epoch at which the recording started")
    parser.add_argument("-o", "--output", type=Path, help="defaults to replacing the recording in place")
    parser.add_argument("--self-test", action="store_true", help="check the timecode and chapter logic")
    args = parser.parse_args()

    if args.self_test:
        return self_test()

    if not all((args.recording, args.markers, args.start)):
        parser.error("recording, markers and start are required")

    for path in (args.recording, args.markers):
        if not path.is_file():
            print(f"No {path}; leaving the recording as it is.")
            return 0

    marks = parse_markers(args.markers.read_text(errors="replace"))
    if not marks:
        print(f"No usable markers in {args.markers}; leaving the recording as it is.")
        return 0

    duration = video_duration(args.recording)
    marks = to_offsets(marks, args.start, duration)
    chapters = build_chapters(marks, duration)
    cues = build_cues(marks, duration)
    print(f"{len(marks)} markers over {duration:.1f}s: {len(chapters)} chapters, {len(cues)} subtitle cues")

    output = args.output or args.recording
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        (tmp / "meta.txt").write_text(render_ffmetadata(chapters))
        (tmp / "cues.srt").write_text(render_srt(cues))
        # Written beside the output so the final move stays on one filesystem.
        staged = output.with_suffix(".annotated.mkv")

        cmd = ["ffmpeg", "-y", "-v", "error", "-i", str(args.recording)]
        cmd += ["-f", "ffmetadata", "-i", str(tmp / "meta.txt")]
        maps = ["-map", "0:v", "-map_metadata", "1"]
        if cues:
            cmd += ["-f", "srt", "-i", str(tmp / "cues.srt")]
            maps += ["-map", "2:s"]
        subprocess.run(cmd + maps + ["-c", "copy", "-c:s", "srt", str(staged)], check=True)
        os.replace(staged, output)

    print(f"Annotated {output}")
    return 0


def self_test() -> int:
    assert srt_timecode(0) == "00:00:00,000"
    assert srt_timecode(1.5) == "00:00:01,500"
    assert srt_timecode(3661.007) == "01:01:01,007"

    marks = to_offsets(
        [
            (1000.0, "test", "test_a"),
            (1002.0, "event", "Unplugging USB device"),
            (1005.0, "event", "PASSED test_a"),
            (1010.0, "test", "test_b"),
            (1099.0, "event", "FAILED test_b"),
        ],
        start=1000.0,
        duration=20.0,
    )
    # The last two markers are past the end of the video and clamp onto it.
    assert [round(t, 3) for t, _, _ in marks] == [0.0, 2.0, 5.0, 10.0, 20.0], marks

    chapters = build_chapters(marks, 20.0)
    assert chapters == [(0.0, 10.0, "test_a \u2014 PASSED"), (10.0, 20.0, "test_b \u2014 FAILED")], chapters

    cues = build_cues(marks, 20.0)
    assert len(cues) == 4, cues  # the clamped final marker has no room left and is dropped
    assert cues[0] == (0.0, 2.0, "test_a"), cues[0]

    # An outcome for a test that never started must not invent a chapter.
    assert build_chapters([(0.0, "event", "PASSED test_ghost")], 20.0) == []

    assert render_ffmetadata([(0.0, 1.5, "a=b;c")]).endswith("title=a\\=b\\;c\n")

    print("self-test OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
