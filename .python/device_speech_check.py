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
    "dictation": PACKAGE + ".speech.SpeechDictationDeviceTest",
    "reading": PACKAGE + ".speech.SpeechReadingDeviceTest",
    "conversation": PACKAGE + ".speech.SpeechConversationDeviceTest",
    "voice-command": PACKAGE + ".speech.SpeechVoiceCommandDeviceTest",
    "engine": PACKAGE + ".speech.SpeechEngineLatencyDeviceTest",
    "conversation-local": PACKAGE + ".speech.SpeechConversationRealDeviceTest#localTargetTenRounds",
    "conversation-online": PACKAGE + ".speech.SpeechConversationRealDeviceTest#onlineTargetTenRounds",
}
# These play sound through the speaker at the device's media volume: never on adjacent phones at once.
ACOUSTIC = {"engine", "conversation-local", "conversation-online"}
# These wait for the maintainer on the phone (roadmap 10.5 E4); run one at a time on request.
MANUAL = {
    "manual-dictation": "tap the microphone once and say a sentence",
    "manual-conversation": "long press the microphone, talk a few rounds, then cancel the recognizer",
    "manual-assistant": "hold the power button, speak, then tap \"3-Stone AI\"",
}
CLASSES.update({
    "manual-dictation": PACKAGE + ".speech.SpeechManualDeviceTest#dictateOnce",
    "manual-conversation": PACKAGE + ".speech.SpeechManualDeviceTest#voiceConversation",
    "manual-assistant": PACKAGE + ".speech.SpeechManualDeviceTest#voiceCommandFromTheAssistant",
})


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
    parser.add_argument("--only", default=",".join(name for name in CLASSES if name not in ACOUSTIC | set(MANUAL)),
                        help="comma-separated: " + ", ".join(CLASSES) + "; acoustic and manual ones only on request")
    parser.add_argument("--no-install", action="store_true")
    parser.add_argument("--keep-test-apk", action="store_true",
                        help="leave the test APK (and its fake recognizer) installed")
    parser.add_argument("--force", action="store_true",
                        help="run even when another app is in the foreground")
    parser.add_argument("--prefer-macaw", choices=["true", "false"],
                        help="manual tests: set \"Prefer 3-Macaw Speech\" for this run only")
    parser.add_argument("--target-model", help="real-target conversations: the exact model ID to use")
    parser.add_argument("--list-targets", action="store_true",
                        help="real-target conversations: only list the ready targets (model and provider)")
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
    # The tests open the chat in front; do not take the screen from someone using the phone.
    home = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                 "-c", "android.intent.category.HOME", check=False).strip().splitlines()
    home_package = home[-1].split("/")[0] if home else ""
    activities = shell("dumpsys", "activity", "activities", check=False)
    front = re.search(r"(?:topResumedActivity|mResumedActivity)[=:] ?ActivityRecord\{\S+ u\d+ ([^/ ]+)/", activities)
    front_package = front.group(1) if front else ""
    allowed = {home_package, PACKAGE, PACKAGE + ".test", ""}
    if front_package not in allowed and not front_package.endswith(".launcher") and not args.force:
        print(f"BUSY {front_package} is in the foreground; rerun with --force when the phone is free")
        return 2
    if not args.no_install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
    shell("rm", "-rf", REMOTE, check=False)

    all_passed = True
    try:
        for name in selected:
            if name in MANUAL:
                print(f"  {name}: waiting for the maintainer to {MANUAL[name]}", flush=True)
            if name in ACOUSTIC:
                volume = shell("cmd", "media_session", "volume", "--stream", "3", "--get", check=False)
                if "volume is" not in volume:
                    # API 28 has no media_session shell command.
                    volume = shell("media", "volume", "--stream", "3", "--get", check=False)
                level = re.search(r"volume is (\d+) in range \[(\d+)\.\.(\d+)\]", volume)
                print(f"  {name} plays sound; media volume {level.group(1) + '/' + level.group(3) if level else 'unknown'}")
            extra = ["-e", "preferMacaw", args.prefer_macaw] if args.prefer_macaw else []
            if args.target_model:
                extra += ["-e", "targetModel", args.target_model]
            if args.list_targets:
                extra += ["-e", "listTargets", "true"]
            result = shell("am", "instrument", "-w", "-r", *extra, "-e", "class", CLASSES[name], RUNNER, timeout=1800)
            (output / f"{name}-instrumentation.txt").write_text(result, encoding="utf-8")
            passed, tests, failures = summarize(result)
            all_passed &= passed
            print(f"{'PASS' if passed else 'FAIL'} {name}: {tests} tests, {failures} failures")
            for failure in re.findall(r"^INSTRUMENTATION_STATUS: stack=(.*)$", result, re.MULTILINE):
                print("  " + failure[:300])
    finally:
        if not args.keep_test_apk:
            # The test APK carries an exported fake recognizer; never leave it in system choosers.
            adb("uninstall", PACKAGE + ".test", check=False)

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
