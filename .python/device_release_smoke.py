#!/usr/bin/env python3
"""Install an archived 3-Stone AI release on one device and run the release smoke.

Reads releases/v<version>/release-manifest.json, checks the archived APK's SHA-256, installs
it with `adb install -r` (keeping the app data; a 32-bit APK on a 64-bit device is installed
with --abi), checks the installed version code, then runs ReleaseAbiSmokeInstrumentation from
the androidTest APK built with -PreleaseAbiSmoke=true (WAKE discovery, INFO Binder with the
installed version, AI provider caller check, model manager runtime notice). Prints one line
per step and uninstalls the test APK afterwards, because it carries an exported fake
recognizer. See docs/dev/32-bit-compatibility-2026-09-29.md.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.supermonster003.autojs6.plugin.threestoneai"
TEST_PACKAGE = PACKAGE + ".test"
RUNNER = TEST_PACKAGE + "/" + PACKAGE + ".ReleaseAbiSmokeInstrumentation"
BITS_32 = {"armeabi-v7a", "x86"}


def version_name():
    for line in (ROOT / "version.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("VERSION_NAME="):
            return line.split("=", 1)[1].strip()
    raise ValueError("VERSION_NAME missing")


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def result_values(output):
    """Returns the INSTRUMENTATION_RESULT key/value pairs of `am instrument -w -r`."""
    return dict(re.findall(r"^INSTRUMENTATION_RESULT: (\w+)=(.*?)\s*$", output, re.MULTILINE))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--version", default=version_name())
    parser.add_argument("--abi", help="archived APK to install: arm64-v8a, armeabi-v7a, x86, x86_64 or universal "
                                      "(default: the device's primary ABI)")
    parser.add_argument("--test-apk", type=Path,
                        default=ROOT / "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    parser.add_argument("--keep-test-apk", action="store_true")
    parser.add_argument("--force", action="store_true",
                        help="run even when another app is in the foreground")
    args = parser.parse_args()

    def adb(*command, check=True, timeout=600):
        result = subprocess.run([args.adb, "-s", args.serial, *map(str, command)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and result.returncode != 0:
            detail = (result.stderr or result.stdout).decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"adb {command[0]} failed: {detail[-400:]}")
        return result.stdout.decode("utf-8", errors="replace")

    def shell(*command, **kwargs):
        return adb("shell", shlex.join(map(str, command)), **kwargs)

    directory = ROOT / "releases" / f"v{args.version}"
    manifest = json.loads((directory / "release-manifest.json").read_text(encoding="utf-8"))
    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abis = shell("getprop", "ro.product.cpu.abilist").strip().split(",")
    abi = args.abi or abis[0]
    print(f"device {args.serial} model={model} api={sdk} abis={','.join(abis)}")
    print(f"release v{args.version} source={manifest['sourceRevision'][:10]} dirty={manifest['sourceDirty']}")

    artifacts = [item for item in manifest["artifacts"] if item["abi"] == abi]
    if len(artifacts) != 1:
        print(f"FAIL no archived APK for {abi}")
        return 1
    artifact = artifacts[0]
    apk = directory / artifact["file"]
    if sha256(apk) != artifact["sha256"]:
        print(f"FAIL {apk.name} differs from the manifest")
        return 1
    print(f"PASS sha256 {apk.name}")

    # The smoke opens the model manager in front; do not take the screen from someone using the phone.
    home = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                 "-c", "android.intent.category.HOME", check=False).strip().splitlines()
    home_package = home[-1].split("/")[0] if home else ""
    activities = shell("dumpsys", "activity", "activities", check=False)
    front = re.search(r"(?:topResumedActivity|mResumedActivity)[=:] ?ActivityRecord\{\S+ u\d+ ([^/ ]+)/", activities)
    front_package = front.group(1) if front else ""
    if front_package not in {home_package, PACKAGE, ""} and not front_package.endswith(".launcher") and not args.force:
        print(f"BUSY {front_package} is in the foreground; rerun with --force when the phone is free")
        return 2

    force_32 = abi in BITS_32 and any(item not in BITS_32 | {"armeabi"} for item in abis)
    abi_option = ["--abi", abi] if force_32 else []
    adb("install", "-r", *abi_option, apk)
    installed = shell("dumpsys", "package", PACKAGE, check=False)
    code = re.search(r"versionCode=(\d+)", installed)
    primary = re.search(r"primaryCpuAbi=(\S+)", installed)
    installed_code = int(code.group(1)) if code else -1
    ok = installed_code == artifact["versionCode"]
    print(f"{'PASS' if ok else 'FAIL'} install versionCode={installed_code} primaryCpuAbi={primary.group(1) if primary else '?'}")
    if not ok:
        return 1

    all_passed = True
    try:
        adb("install", "-r", "-t", args.test_apk)
        runners = shell("pm", "list", "instrumentation", check=False)
        if RUNNER not in runners:
            print("FAIL the test APK has no ReleaseAbiSmokeInstrumentation; build it with -PreleaseAbiSmoke=true")
            return 1
        expected64 = "false" if abi in BITS_32 else "true"
        output = shell("am", "instrument", *abi_option, "-w", "-r", "-e", "process64", expected64, RUNNER,
                       timeout=600)
        values = result_values(output)
        all_passed = values.get("releaseAbiSmoke") == "passed" and values.get("process64") == expected64
        detail = " ".join(f"{key}={values.get(key)}" for key in ("releaseAbiSmoke", "process64", "pageSize"))
        print(f"{'PASS' if all_passed else 'FAIL'} smoke {detail}")
        if values.get("failure"):
            print("  " + values["failure"][:300])
    finally:
        if not args.keep_test_apk:
            # The test APK carries an exported fake recognizer; never leave it in system choosers.
            adb("uninstall", TEST_PACKAGE, check=False)
    return 0 if all_passed else 1


if __name__ == "__main__":
    sys.exit(main())
