#!/usr/bin/env python3
"""OpenCode Go session header acceptance on one device (roadmap 1.8.0 P12.7, D170).

Installs the androidTest APK (and, unless --no-install, the debug APK for the device ABI over the
existing app, data kept), runs OpenCodeGoSessionHeaderAcceptanceTest with
`-e openCodeGoAcceptance true` and uninstalls the test APK again. For the OpenAI compatible,
Anthropic and Google endpoints of OpenCode Go the test saves a service with an invalid placeholder
key, expects HTTP 400 MissingSessionID from the connection test, adds the session header from the
failure explanation, saves, and expects HTTP 401. Three requests per endpoint at most, all rejected
before any model runs; no real key is used.

Before and after the run it reads, through run-as on the debug build, the stored default target,
the service IDs and the base URL history, and prints only whether each one is the same as before,
with counts; no file content, key or ID is printed. The instrumentation output is saved under
build/verification/opencode-go/<serial>/.

A remote adb server is used through the ADB_SERVER_SOCKET environment variable.
"""

import argparse
from pathlib import Path
import re
import shlex
import subprocess
import sys

from device_models_flow import STATUS, preference_values, profile_ids
from device_speech_check import PACKAGE, RUNNER, apk_from_metadata

ROOT = Path(__file__).resolve().parents[1]
TEST = PACKAGE + ".OpenCodeGoSessionHeaderAcceptanceTest"
TAG = "P127OpenCodeGo"
TARGET_FILE = "files/ai-default-target/default-target"
PROFILES = "files/ai-profiles/profiles.json"
HISTORY = "online-ai-base-url-history"


def verdicts(result):
    """(test, verdict, stack) for every finished test in `am instrument -r` output."""
    found = []
    current = None
    stack = None
    for line in result.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            current = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS: stack="):
            stack = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS_CODE:") and current:
            code = line.split(":", 1)[1].strip()
            if code != "1":
                found.append((current, STATUS.get(code, f"code {code}"), stack))
            current = None
            stack = None
    return found


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--no-install", action="store_true", help="keep the installed debug APK")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/opencode-go")
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

    def read_state():
        return {
            "default target": run_as("cat", TARGET_FILE).strip(),
            "services": profile_ids(run_as("cat", PROFILES)),
            f"prefs {HISTORY}": preference_values(run_as("cat", f"shared_prefs/{HISTORY}.xml")),
        }

    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    print(f"device {args.serial} model={model} api={sdk} abi={abi}")
    if not args.no_install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
    before = read_state()
    adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
    adb("logcat", "-c", check=False)
    try:
        result = shell("am", "instrument", "-w", "-r", "-e", "openCodeGoAcceptance", "true",
                       "-e", "class", TEST, RUNNER)
    finally:
        # The test APK carries an exported fake recognizer; never leave it in system choosers.
        adb("uninstall", PACKAGE + ".test", check=False)
    (output / "opencode-go-instrumentation.txt").write_text(result, encoding="utf-8")

    found = verdicts(result)
    passed = bool(found) and bool(re.search(r"^INSTRUMENTATION_CODE: -1", result, re.MULTILINE))
    for test, verdict, stack in found:
        passed &= verdict == "PASS"
        reason = stack if stack and verdict != "PASS" else ""
        print(f"{verdict} {test}" + (f": {reason[:300]}" if reason else ""))
    if not re.search(r"^INSTRUMENTATION_CODE: -1", result, re.MULTILINE):
        print("  the instrumentation did not finish: " + (result.strip().splitlines()[-1][:300] if result.strip() else "no output"))
    log = adb("logcat", "-d", "-s", f"{TAG}:I", check=False)
    for line in log.splitlines():
        if f"{TAG}:" in line or f" {TAG} " in line:
            print("  " + line.split(f"{TAG}", 1)[1].lstrip(" :"))

    after = read_state()
    for key, value in before.items():
        same = value == after[key]
        passed &= same
        count = ""
        if key == "services" and value is not None:
            count = f" ({len(value)} services)"
        elif key.startswith("prefs") and value is not None:
            count = f" ({len(value)} entries)"
        print(f"  {key}: {'same as before' if same else 'CHANGED'}{count}")
    print(f"{'PASS' if passed else 'FAIL'} OpenCode Go session header acceptance ({len(found)} tests)")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
