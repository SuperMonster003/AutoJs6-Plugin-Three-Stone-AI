#!/usr/bin/env python3
"""Opt-in message versions against a real service on one device (roadmap P6.3, D148).

Runs ChatBranchRealDeviceFlowTest with `-e realBranches true` and uninstalls the test APK again.
--request SERVICE MODEL sends one short question in a new conversation and regenerates the reply
twice (three real requests), switches between 1/3 and 3/3, leaves 1/3 shown and checks after
reopening the chat that the choice and the three versions are kept. The test deletes the
conversation and restores the default target and the preferences it touched; only statuses,
lengths, counts and positions are printed.

--install installs the debug APK for the device ABI over the existing app first (data kept; never
uninstalls the app). --snapshot NAME and --compare NAME record and compare, through run-as on the
debug build, the state the flow must leave alone (default target, services, local model catalog,
conversation history, every preference file, files/models, the number of attachment files), as
`device_attachments_flow.py` does; only same / changed per item is printed. The saved states and
the instrumentation output stay under build/verification/branches-real/<serial>/.

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
from device_speech_check import PACKAGE, RUNNER, apk_from_metadata

ROOT = Path(__file__).resolve().parents[1]
TEST = PACKAGE + ".ChatBranchRealDeviceFlowTest"
TAG = "P6Branches"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--snapshot", metavar="NAME", help="record the state under NAME")
    parser.add_argument("--install", action="store_true", help="install the debug APK over the app (data kept)")
    parser.add_argument("--request", nargs=2, metavar=("SERVICE", "MODEL"),
                        help="run the flow (three real requests) with this service and model")
    parser.add_argument("--compare", metavar="NAME", help="compare the state with the one recorded as NAME")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/branches-real")
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
    if args.request:
        service, model_id = args.request
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
        try:
            since = shell("date", "+%m-%d %H:%M:%S.000").strip()
            result = shell("am", "instrument", "-w", "-r", "-e", "realBranches", "true",
                           "-e", "service", service, "-e", "model", model_id, "-e", "class", TEST, RUNNER,
                           timeout=1200)
            (output / "run-branches.txt").write_text(result, encoding="utf-8")
            passed &= report(f"branches {model_id}", result)
            log = adb("logcat", "-d", "-T", since, "-s", f"{TAG}:I", check=False)
            for line in log.splitlines():
                if TAG in line and ":" in line.split(TAG, 1)[1]:
                    print("  " + line.split(TAG, 1)[1].lstrip(" :"))
        finally:
            # The test APK carries an exported fake recognizer; never leave it in system choosers.
            adb("uninstall", PACKAGE + ".test", check=False)

    if args.compare:
        saved = json.loads((output / f"state-{args.compare}.json").read_text(encoding="utf-8"))
        passed &= compare_states(saved, read_state(run_as), args.compare)
    print(f"{'PASS' if passed else 'FAIL'} branch flow")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
