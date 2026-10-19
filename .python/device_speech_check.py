#!/usr/bin/env python3
"""Run the 3-Stone AI speech device tests (roadmap section 10, P5) on one device.

Installs the debug APK for the device ABI and the androidTest APK, runs the selected
instrumentation classes one after another, pulls receipts and screenshots into
build/verification/speech/<serial>/ and prints one line per class plus the key values
of each receipt. Recognized and spoken text is never printed or stored.

Dialogs that need a person (system chooser, permissions) are left to the maintainer;
these tests use fake recognizers and engines unless a class says otherwise.
"""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.supermonster003.autojs6.plugin.threestoneai"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
REMOTE = f"/sdcard/Android/data/{PACKAGE}/files/speech-check"
CLASSES = {
    "settings": PACKAGE + ".speech.SpeechSettingsDeviceTest",
}


def apk_from_metadata(directory, abi=None):
    metadata = json.loads((directory / "output-metadata.json").read_text(encoding="utf-8"))
    elements = metadata["elements"]
    matching = [item for item in elements if any(
        entry.get("filterType") == "ABI" and entry.get("value") == abi
        for entry in item["filters"]
    )] if abi else []
    candidates = matching or [item for item in elements if not item["filters"]]
    if len(candidates) != 1:
        raise ValueError(f"Expected one compatible APK in {directory}")
    return directory / candidates[0]["outputFile"]


def summarize(output):
    """Returns (passed, tests, failures) from `am instrument -r` output."""
    tests = len(re.findall(r"^INSTRUMENTATION_STATUS_CODE: 1\s*$", output, re.MULTILINE))
    failed = len(re.findall(r"^INSTRUMENTATION_STATUS_CODE: -2\s*$", output, re.MULTILINE))
    errors = len(re.findall(r"^INSTRUMENTATION_STATUS_CODE: -1\s*$", output, re.MULTILINE))
    crashed = bool(re.search(r"INSTRUMENTATION_FAILED|Process crashed", output))
    passed = tests > 0 and failed == 0 and errors == 0 and not crashed
    return passed, tests, failed + errors + int(crashed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--only", default=",".join(CLASSES), help="comma-separated: " + ", ".join(CLASSES))
    parser.add_argument("--no-install", action="store_true")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/speech")
    args = parser.parse_args()
    selected = [name.strip() for name in args.only.split(",") if name.strip()]
    unknown = [name for name in selected if name not in CLASSES]
    if unknown:
        parser.error("unknown test: " + ", ".join(unknown))
    output = args.output / args.serial
    output.mkdir(parents=True, exist_ok=True)

    def adb(*command, check=True, timeout=900):
        result = subprocess.run([args.adb, "-s", args.serial, *map(str, command)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and result.returncode != 0:
            detail = (result.stderr or result.stdout).decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"adb {command[0]} failed: {detail[-400:]}")
        return result.stdout.decode("utf-8", errors="replace")

    def shell(*command, **kwargs):
        return adb("shell", shlex.join(map(str, command)), **kwargs)

    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    print(f"device {args.serial} model={model} api={sdk} abi={abi}")
    if not args.no_install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
    shell("rm", "-rf", REMOTE, check=False)

    all_passed = True
    for name in selected:
        result = shell("am", "instrument", "-w", "-r", "-e", "class", CLASSES[name], RUNNER, timeout=1800)
        (output / f"{name}-instrumentation.txt").write_text(result, encoding="utf-8")
        passed, tests, failures = summarize(result)
        all_passed &= passed
        print(f"{'PASS' if passed else 'FAIL'} {name}: {tests} tests, {failures} failures")
        if not passed:
            failure = re.search(r"^(?:INSTRUMENTATION_STATUS: stack=)(.*)$", result, re.MULTILINE)
            if failure:
                print("  " + failure.group(1)[:300])

    listing = shell("ls", REMOTE, check=False).split()
    for file in listing:
        adb("pull", f"{REMOTE}/{file}", output / file, check=False)
        if file.endswith(".json"):
            receipt = json.loads((output / file).read_text(encoding="utf-8"))
            values = " ".join(f"{key}={value}" for key, value in receipt.items())
            print(f"  {file[:-5]}: {values}")
    shots = sorted(file for file in listing if file.endswith(".png"))
    if shots:
        print(f"  screenshots: {', '.join(shots)} -> {output}")
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
