#!/usr/bin/env python3
"""Film cold starts of 3-Stone AI and check each frame for the wrong appearance (roadmap P6-3b A3, D224).

On API 31+ the app hands its resolved night mode to the system (UiModeManager.setApplicationNightMode), which draws
the next starting window with it. For each combination, system dark + app light and system light + app dark, the
script

1. sets the app's appearance with ApplicationNightModeDeviceTest#holdAppearanceForColdStart, which also records the
   resolved mode with the system (the user's settings wait in a file of the app until the end),
2. switches the system night mode with `cmd uimode night`,
3. films --runs cold starts of the launcher activity with screenrecord (force-stop, home screen, start),
4. classifies every recorded frame: a frame whose area between the status and navigation bars is at least
   SOLID_SHARE one brightness (the starting window, an empty chat) is dark or light; a solid frame of the
   other appearance than the app's is an inverted frame.

The home screen is filmed before each start; when it is itself solid (a single-colour wallpaper) the run is reported
as ambiguous instead of passed. Afterwards the system night mode and the app's settings are restored and the test
APK is uninstalled. --instrumentation first runs the other ApplicationNightModeDeviceTest cases (the recorded mode for
dark, light, following the system and following AutoJs6). Recordings and contact sheets go to
build/verification/splash/<serial>/. Needs ffmpeg (--ffmpeg) and Pillow.
"""

import argparse
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import tempfile
import time

from PIL import Image, ImageChops, ImageStat

from device_speech_check import PACKAGE, RUNNER, apk_from_metadata, summarize

ROOT = Path(__file__).resolve().parents[1]
TEST_CLASS = PACKAGE + ".ApplicationNightModeDeviceTest"
COMBINATIONS = (
    ("system-dark-app-light", "yes", "LIGHT"),
    ("system-light-app-dark", "no", "DARK"),
)
SOLID_SHARE = 0.8
DARK_BELOW = 110
LIGHT_ABOVE = 150
# A frame this close to the first one still shows the home screen.
UNCHANGED_DIFFERENCE = 3.0
RECORD_SECONDS = 6
REMOTE = "/data/local/tmp/stone-splash.mp4"


def classify(image):
    """Returns (kind, level, share): kind is dark, light, mid or mixed for the area between the bars."""
    gray = image.convert("L")
    width, height = gray.size
    area = gray.crop((0, int(height * 0.08), width, int(height * 0.92)))
    histogram = area.histogram()
    total = sum(histogram)
    start, count = max(((index, sum(histogram[index:index + 16])) for index in range(0, 241)), key=lambda item: item[1])
    share = count / total
    level = start + 8
    if share < SOLID_SHARE:
        return "mixed", level, share
    if level < DARK_BELOW:
        return "dark", level, share
    if level > LIGHT_ABOVE:
        return "light", level, share
    return "mid", level, share


def analyze(frames, expected):
    """Counts the frames by kind and the inverted ones; frames identical to the first are the home screen."""
    first = frames[0].convert("L")
    other = "light" if expected == "dark" else "dark"
    result = {"frames": len(frames), "home": classify(frames[0])[0], "inverted": 0, "expected": 0,
              "mixed": 0, "firstExpected": None, "invertedFrames": []}
    for index, frame in enumerate(frames[1:], start=1):
        gray = frame.convert("L")
        if gray.size == first.size and \
                ImageStat.Stat(ImageChops.difference(gray, first)).mean[0] < UNCHANGED_DIFFERENCE:
            continue
        kind, _, _ = classify(frame)
        if kind == expected:
            result["expected"] += 1
            if result["firstExpected"] is None:
                result["firstExpected"] = index
        elif kind == other:
            result["inverted"] += 1
            result["invertedFrames"].append(index)
        elif kind == "mixed":
            result["mixed"] += 1
    return result


def contact_sheet(frames, path, columns=10, width=96):
    if not frames:
        return
    height = round(frames[0].size[1] * width / frames[0].size[0])
    shown = frames[:columns * 6]
    rows = (len(shown) + columns - 1) // columns
    sheet = Image.new("RGB", (columns * width, rows * height), "gray")
    for index, frame in enumerate(shown):
        sheet.paste(frame.convert("RGB").resize((width, height)), ((index % columns) * width, (index // columns) * height))
    sheet.save(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--runs", type=int, default=2, help="cold starts filmed per combination")
    parser.add_argument("--no-install", action="store_true")
    parser.add_argument("--instrumentation", action="store_true",
                        help="also run the recorded-mode cases of ApplicationNightModeDeviceTest")
    parser.add_argument("--keep-test-apk", action="store_true")
    parser.add_argument("--force", action="store_true", help="run even when another app is in the foreground")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/splash")
    args = parser.parse_args()
    if shutil.which(args.ffmpeg) is None and not Path(args.ffmpeg).is_file():
        parser.error(f"ffmpeg not found: {args.ffmpeg}")
    output = args.output / args.serial
    output.mkdir(parents=True, exist_ok=True)

    def adb(*command, check=True, timeout=600):
        result = subprocess.run([args.adb, "-s", args.serial, *map(str, command)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and result.returncode != 0:
            detail = (result.stderr or result.stdout).decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"adb {command[0]} failed: {detail[-400:]}")
        return result.stdout.decode("utf-8", errors="replace")

    def shell(*command, **kwargs):
        return adb("shell", shlex.join(map(str, command)), **kwargs)

    def instrument(*extra):
        result = shell("am", "instrument", "-w", "-r", *extra, RUNNER, timeout=900)
        passed, tests, failures = summarize(result)
        return passed, tests, failures, result

    sdk = int(shell("getprop", "ro.build.version.sdk").strip())
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    print(f"device {args.serial} model={model} api={sdk} abi={abi}")
    if sdk < 31:
        print("SKIP the system records an application night mode from API 31 on")
        return 0
    activities = shell("dumpsys", "activity", "activities", check=False)
    front = re.search(r"(?:topResumedActivity|mResumedActivity)[=:] ?ActivityRecord\{\S+ u\d+ ([^/ ]+)/", activities)
    home = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                 "-c", "android.intent.category.HOME", check=False).strip().splitlines()
    home_package = home[-1].split("/")[0] if home else ""
    front_package = front.group(1) if front else ""
    if front_package not in {home_package, PACKAGE, PACKAGE + ".test", ""} and \
            not front_package.endswith(".launcher") and not args.force:
        print(f"BUSY {front_package} is in the foreground; rerun with --force when the phone is free")
        return 2
    original = re.search(r"Night mode: (\S+)", shell("cmd", "uimode", "night", check=False))
    original_mode = original.group(1) if original else ""
    if original_mode not in ("yes", "no", "auto"):
        print(f"REFUSED the system night mode is '{original_mode}', which this script cannot restore")
        return 2
    launcher = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                     "-c", "android.intent.category.LAUNCHER", PACKAGE).strip().splitlines()[-1]
    print(f"  system night mode {original_mode}; launcher activity {launcher.split('/')[-1]}")
    if not args.no_install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))

    all_passed = True
    held = False
    try:
        if args.instrumentation:
            passed, tests, failures, result = instrument("-e", "class", TEST_CLASS)
            (output / "recorded-mode-instrumentation.txt").write_text(result, encoding="utf-8")
            all_passed &= passed
            print(f"{'PASS' if passed else 'FAIL'} recorded mode: {tests} tests, {failures} failures")
        for name, system, app in COMBINATIONS:
            held = True
            passed, _, _, result = instrument("-e", "coldStart", app, "-e", "class", TEST_CLASS + "#holdAppearanceForColdStart")
            if not passed:
                (output / f"{name}-hold-instrumentation.txt").write_text(result, encoding="utf-8")
                print(f"FAIL {name}: the app's appearance could not be set")
                all_passed = False
                continue
            shell("cmd", "uimode", "night", system)
            time.sleep(2.0)
            expected = app.lower()
            for run in range(1, args.runs + 1):
                shell("am", "force-stop", PACKAGE)
                shell("am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
                time.sleep(2.0)
                shell("rm", "-f", REMOTE, check=False)
                recorder = subprocess.Popen([args.adb, "-s", args.serial, "shell",
                                             f"screenrecord --time-limit {RECORD_SECONDS} {REMOTE}"],
                                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                time.sleep(1.2)
                shell("am", "start", "-W", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER",
                      "-n", launcher)
                recorder.wait(timeout=RECORD_SECONDS + 20)
                time.sleep(0.8)
                video = output / f"{name}-{run}.mp4"
                adb("pull", REMOTE, video)
                shell("rm", "-f", REMOTE, check=False)
                with tempfile.TemporaryDirectory() as directory:
                    subprocess.run([args.ffmpeg, "-v", "error", "-i", str(video), "-vsync", "0",
                                    "-vf", "scale=180:-2", str(Path(directory) / "%04d.png")], check=True)
                    frames = [Image.open(path).copy() for path in sorted(Path(directory).glob("*.png"))]
                result = analyze(frames, expected)
                contact_sheet(frames, output / f"{name}-{run}.png")
                ambiguous = result["home"] in ("dark", "light")
                verdict = "AMBIGUOUS" if ambiguous else "PASS" if result["inverted"] == 0 and result["expected"] else "FAIL"
                all_passed &= verdict == "PASS"
                print(f"{verdict} {name} run {run}: frames={result['frames']} expected={result['expected']} "
                      f"inverted={result['inverted']} mixed={result['mixed']} firstExpected={result['firstExpected']} "
                      f"home={result['home']}" + (f" invertedFrames={result['invertedFrames'][:10]}" if result["inverted"] else ""))
    finally:
        shell("cmd", "uimode", "night", original_mode, check=False)
        if held:
            passed, _, _, result = instrument("-e", "coldStart", "restore", "-e", "class", TEST_CLASS + "#holdAppearanceForColdStart")
            if not passed:
                (output / "restore-instrumentation.txt").write_text(result, encoding="utf-8")
                print("FAIL the app's appearance settings were not restored; see restore-instrumentation.txt")
                all_passed = False
        restored = re.search(r"Night mode: (\S+)", shell("cmd", "uimode", "night", check=False))
        print(f"  system night mode restored to {restored.group(1) if restored else 'unknown'}")
        if not args.keep_test_apk:
            # The test APK carries an exported fake recognizer; never leave it in system choosers.
            adb("uninstall", PACKAGE + ".test", check=False)
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
