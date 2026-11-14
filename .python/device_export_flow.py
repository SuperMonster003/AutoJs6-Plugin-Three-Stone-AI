#!/usr/bin/env python3
"""Markdown export and image sharing on one device (roadmap P7.2, D149 - D156).

--run installs the test APK, runs ChatExportDeviceTest (conversations written straight into
history, no AI request), prints the receipts (Markdown sizes and MIME type, image time, share sheet
time, image and layout size), copies them, the share sheet screenshot and the sample exports (the
Markdown files and the PNG made from the test's own conversations) to the output directory,
deletes them on the device, checks that the test left no file in Download and no image in
cache/share, and uninstalls the test APK again. Nothing on the phone is tapped: the test hands the
picker's answer back itself and closes the share sheet with Back.

--system-picker (emulator only) also runs the opt-in test that presses Save in the real documents
UI, then reads and deletes the saved file.

--install installs the debug APK for the device ABI over the existing app first (data kept; never
uninstalls the app). --snapshot NAME and --compare NAME record and compare, through run-as on the
debug build, the state the run must leave alone (as `device_attachments_flow.py` does); only same /
changed per item is printed. Files stay under build/verification/export/<serial>/.

A remote adb server is used through the ADB_SERVER_SOCKET environment variable, for example
ADB_SERVER_SOCKET=tcp:100.107.160.16:5037.
"""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys

from device_attachments_flow import DOCUMENTS, compare_states, read_state, report
from device_speech_check import PACKAGE, REMOTE, RUNNER, apk_from_metadata

ROOT = Path(__file__).resolve().parents[1]
TEST = PACKAGE + ".ChatExportDeviceTest"
RECEIPTS = ("chat-export-markdown", "chat-export-image", "chat-export-cancel", "chat-export-picker")
SCREENSHOT = "chat-export-share-sheet"
DOWNLOAD = "/sdcard/Download"
TEST_FILE_PREFIX = "3-Stone-P7-test-"
TIME_LIMIT_MILLIS = 3000


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--snapshot", metavar="NAME", help="record the state under NAME")
    parser.add_argument("--install", action="store_true", help="install the debug APK over the app (data kept)")
    parser.add_argument("--run", action="store_true", help="run ChatExportDeviceTest and collect its receipts")
    parser.add_argument("--test", metavar="METHOD", help="run only this test method")
    parser.add_argument("--system-picker", action="store_true",
                        help="also press Save in the real documents UI (emulator only)")
    parser.add_argument("--time-limit-millis", type=int, default=TIME_LIMIT_MILLIS,
                        help="limit for the image and the share sheet (3000, P7.2; looser only for a loaded emulator)")
    parser.add_argument("--compare", metavar="NAME", help="compare the state with the one recorded as NAME")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/export")
    args = parser.parse_args()
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

    def run_as(*command):
        return shell("run-as", PACKAGE, *command, check=False)

    def version_code():
        found = re.search(r"versionCode=(\d+)", shell("dumpsys", "package", PACKAGE, check=False))
        return found.group(1) if found else "none"

    def names(listing):
        return set() if "No such file" in listing else {line.strip() for line in listing.splitlines() if line.strip()}

    def remote_names(directory):
        return names(shell("ls", "-1", directory, check=False))

    def shared_images():
        return names(run_as("ls", "-1", "cache/share"))

    def test_downloads():
        return {name for name in remote_names(DOWNLOAD) if name.startswith(TEST_FILE_PREFIX)}

    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    print(f"device {args.serial} model={model} api={sdk} abi={abi} versionCode={version_code()}")

    if args.snapshot:
        state = read_state(run_as)
        (output / f"state-{args.snapshot}.json").write_text(json.dumps(state, indent=1), encoding="utf-8")
        present = sum(1 for key, value in state.items() if key in DOCUMENTS and value)
        prefs = sum(1 for key in state if key.startswith("prefs "))
        print(f"recorded {args.snapshot}: {present}/{len(DOCUMENTS)} documents, {prefs} preference files, "
              f"{len(state['files/models (names and sizes)'])} entries in files/models, "
              f"{state['stored attachment files']} attachment files")

    if args.install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
        print(f"installed the debug APK over the app (data kept), versionCode={version_code()}")

    passed = True
    if args.run:
        if args.system_picker and not args.serial.startswith("emulator-"):
            raise SystemExit("--system-picker taps the documents UI and runs on an emulator only")
        reports_before = remote_names(REMOTE)
        shares_before = shared_images()
        downloads_before = test_downloads()
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
        try:
            target = TEST + (f"#{args.test}" if args.test else "")
            picker = ["-e", "systemPickers", "true"] if args.system_picker else []
            result = shell("am", "instrument", "-w", "-r", "-e", "class", target, *picker,
                           "-e", "exportTimeLimitMillis", args.time_limit_millis, RUNNER, timeout=1200)
            (output / "run-export.txt").write_text(result, encoding="utf-8")
            passed &= report("export", result)
        finally:
            # The test APK carries an exported fake recognizer; never leave it in system choosers.
            adb("uninstall", PACKAGE + ".test", check=False)

        created = remote_names(REMOTE) - reports_before
        for name in sorted(created):
            if not name.startswith("chat-export-"):
                continue
            adb("pull", f"{REMOTE}/{name}", str(output / name), check=False)
            shell("rm", "-f", f"{REMOTE}/{name}", check=False)
        if not reports_before and not remote_names(REMOTE):
            shell("rmdir", REMOTE, check=False)
        passed &= print_receipts(output, args.time_limit_millis)

        leftovers = [f"{DOWNLOAD}/{name}" for name in sorted(test_downloads() - downloads_before)]
        leftovers += [f"cache/share/{name}" for name in sorted(shared_images() - shares_before)]
        left = {name for name in remote_names(REMOTE) if name.startswith("chat-export-")}
        leftovers += [f"{REMOTE}/{name}" for name in sorted(left)]
        print(f"{'PASS' if not leftovers else 'FAIL'} leftovers: {len(leftovers)}" +
              (f" ({', '.join(leftovers)})" if leftovers else ""))
        passed &= not leftovers

    if args.compare:
        saved = json.loads((output / f"state-{args.compare}.json").read_text(encoding="utf-8"))
        passed &= compare_states(saved, read_state(run_as), args.compare)
    print(f"{'PASS' if passed else 'FAIL'} export flow")
    return 0 if passed else 1


def print_receipts(output, limit):
    """Prints the numbers the evidence needs; False when the image or the share sheet took too long."""
    passed = True
    image = output / "chat-export-image.json"
    if image.exists():
        values = json.loads(image.read_text(encoding="utf-8"))
        print(f"  image: {values['messages']} messages, image {values['imageMillis']} ms (thumbnails "
              f"{values['prepareMillis']}, layout and draw {values['drawMillis']}, PNG {values['encodeMillis']}), share sheet "
              f"{values['shareSheetMillis']} ms ({values['shareSheetPackage']}), {values['width']} x {values['height']} px "
              f"from a {values['layoutWidth']} x {values['layoutHeight']} px layout, {values['bytes']} bytes, "
              f"other app read {values['otherAppRead']}")
        print(f"  limit: {limit} ms" + ("" if limit == TIME_LIMIT_MILLIS else f" (P7.2 limit is {TIME_LIMIT_MILLIS} ms)"))
        passed &= values["imageMillis"] <= limit and values["shareSheetMillis"] <= limit
    markdown = output / "chat-export-markdown.json"
    if markdown.exists():
        values = json.loads(markdown.read_text(encoding="utf-8"))
        print(f"  markdown: {values['bytesWithoutReasoning']} bytes without reasoning, {values['bytesWithReasoning']} with, "
              f"MIME {values['mimeType']}, read by shell {values['readByShell']}")
    picker = output / "chat-export-picker.json"
    if picker.exists():
        values = json.loads(picker.read_text(encoding="utf-8"))
        print(f"  system picker: {values['picker']} saved {values['bytes']} bytes to {values['folder']}, "
              f"MIME {values['mimeType']}, read by shell {values['readByShell']}, deleted {values['deleted']}")
    if (output / f"{SCREENSHOT}.png").exists():
        print(f"  screenshot: {SCREENSHOT}.png")
    samples = sorted(path.name for path in output.glob("chat-export-sample*"))
    if samples:
        print(f"  samples: {', '.join(samples)}")
    return passed


if __name__ == "__main__":
    sys.exit(main())
