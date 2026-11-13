#!/usr/bin/env python3
"""Light and dark screenshots of the "Models and services" page (roadmap 1.6.0 P5.2) on one device.

Installs the debug APK for the device ABI over the existing app (data is kept) and the androidTest
APK, runs ModelsAndServicesScreenshotTest with `-e screenshots true`, pulls the PNG files into
build/verification/settings/<serial>/, removes them from the device and uninstalls the test APK.
The test switches only the plugin's own dark mode and writes the appearance preferences back.
Prints one PASS / FAIL line and the screenshot names with their sizes.

With --migration-check (roadmap D114) it also reads, through run-as on the debug build, the stored
default target and the two documents the migration must leave alone (the online services document
and the local model catalog). It prints only the kind of the stored target, whether it agrees with
the previous defaultProfileId / selectedModelId, and whether the documents were written after the
install; no file content, ID or digest is printed.

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

from device_speech_check import PACKAGE, RUNNER, apk_from_metadata, summarize

ROOT = Path(__file__).resolve().parents[1]
REMOTE = f"/sdcard/Android/data/{PACKAGE}/files/settings-screenshots"
TEST = PACKAGE + ".ModelsAndServicesScreenshotTest"
TARGET_FILE = "files/ai-default-target/default-target"
DOCUMENTS = {"profiles": "files/ai-profiles/profiles.json", "catalog": "files/models/catalog.json"}
PROFILE_PREFIX = "profile:"
PROFILE_MODEL_SEPARATOR = ".model."
LOCAL_PREFIX = "local:"


def json_field(text, name):
    try:
        value = json.loads(text).get(name)
    except (ValueError, AttributeError):
        return None
    return value if isinstance(value, str) and value else None


def check_migration(install_epoch, before, after):
    """Returns (passed, lines) for the stored default target against the documents it came from."""
    lines = []
    passed = True
    for key, path in DOCUMENTS.items():
        mtime, digest = after["documents"][key]
        if digest is None:
            lines.append(f"  {key}: absent")
            continue
        untouched = mtime is not None and mtime < install_epoch and digest == before["documents"][key][1]
        passed &= untouched
        lines.append(f"  {key}: {'not written since the install' if untouched else 'WRITTEN after the install'}")
    target = after["target"].splitlines()
    if not target or target[0] != "v1":
        lines.append("  default target file: missing or not v1")
        return False, lines
    stored = target[1].strip() if len(target) > 1 else ""
    profile_id = json_field(after["profiles_text"], "defaultProfileId")
    model_id = json_field(after["catalog_text"], "selectedModelId")
    if stored.startswith(PROFILE_PREFIX):
        stored_profile = stored[len(PROFILE_PREFIX):].split(PROFILE_MODEL_SEPARATOR, 1)[0]
        alternative = PROFILE_MODEL_SEPARATOR in stored
        agrees = profile_id is not None and stored_profile == profile_id
        lines.append(f"  stored: online service ({'alternative' if alternative else 'first'} model), "
                     f"same service as defaultProfileId: {'yes' if agrees else 'no'}")
    elif stored.startswith(LOCAL_PREFIX):
        agrees = model_id is not None and stored[len(LOCAL_PREFIX):] == model_id
        lines.append(f"  stored: local model, same as selectedModelId: {'yes' if agrees else 'no'}"
                     f" (defaultProfileId {'set' if profile_id else 'empty'})")
    else:
        agrees = not stored and profile_id is None and model_id is None
        lines.append(f"  stored: {'empty (automatic choice)' if not stored else 'unknown kind'}")
    passed &= agrees
    lines.append(f"  file existed before the first launch: {'yes' if before['target'] else 'no'}")
    return passed, lines


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--no-install", action="store_true", help="use the APKs already on the device")
    parser.add_argument("--force", action="store_true", help="run even when another app is in the foreground")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/settings")
    parser.add_argument("--migration-check", action="store_true",
                        help="check the stored default target (D114) through run-as on the debug build")
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

    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    print(f"device {args.serial} model={model} api={sdk} abi={abi}")
    activities = shell("dumpsys", "activity", "activities", check=False)
    front = re.search(r"(?:topResumedActivity|mResumedActivity)[=:] ?ActivityRecord\{\S+ u\d+ ([^/ ]+)/", activities)
    front_package = front.group(1) if front else ""
    home = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                 "-c", "android.intent.category.HOME", check=False).strip().splitlines()
    home_package = home[-1].split("/")[0] if home else ""
    allowed = {home_package, PACKAGE, PACKAGE + ".test", ""}
    if front_package not in allowed and not front_package.endswith(".launcher") and not args.force:
        print(f"BUSY {front_package} is in the foreground; rerun with --force when the phone is free")
        return 2
    def run_as(*command):
        return shell("run-as", PACKAGE, *command, check=False)

    def read_state():
        documents = {}
        for key, path in DOCUMENTS.items():
            mtime = run_as("stat", "-c", "%Y", path).strip()
            digest = run_as("sha256sum", path).split()
            documents[key] = (int(mtime) if mtime.isdigit() else None,
                              digest[0] if digest and re.fullmatch(r"[0-9a-f]{64}", digest[0]) else None)
        return {"documents": documents, "target": run_as("cat", TARGET_FILE).strip(),
                "profiles_text": run_as("cat", DOCUMENTS["profiles"]),
                "catalog_text": run_as("cat", DOCUMENTS["catalog"])}

    install_epoch = int(shell("date", "+%s").strip())
    if not args.no_install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
    before = read_state() if args.migration_check else None
    if not args.no_install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
    try:
        result = shell("am", "instrument", "-w", "-r", "-e", "screenshots", "true", "-e", "class", TEST, RUNNER,
                       timeout=600)
    finally:
        # The test APK carries an exported fake recognizer; never leave it in system choosers.
        adb("uninstall", PACKAGE + ".test", check=False)
    (output / "screenshots-instrumentation.txt").write_text(result, encoding="utf-8")
    passed, tests, failures = summarize(result)
    print(f"{'PASS' if passed else 'FAIL'} screenshots: {tests} tests, {failures} failures")
    for failure in re.findall(r"^INSTRUMENTATION_STATUS: stack=(.*)$", result, re.MULTILINE):
        print("  " + failure[:300])

    shots = sorted(name for name in shell("ls", REMOTE, check=False).split() if name.endswith(".png"))
    for name in shots:
        adb("pull", f"{REMOTE}/{name}", output / name)
        print(f"  {name}: {(output / name).stat().st_size // 1024} KiB")
    shell("rm", "-rf", REMOTE, check=False)
    if passed and len(shots) != 4:
        print(f"FAIL expected 4 screenshots, found {len(shots)}")
        passed = False
    if args.migration_check:
        migrated, lines = check_migration(install_epoch, before, read_state())
        print(f"{'PASS' if migrated else 'FAIL'} default target migration")
        for line in lines:
            print(line)
        passed &= migrated
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
