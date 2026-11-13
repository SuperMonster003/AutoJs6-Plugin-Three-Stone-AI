#!/usr/bin/env python3
"""Opt-in chat attachment requests on one device (roadmap P4.4, D131).

Runs ChatAttachmentRealDeviceFlowTest with `-e realAttachments true`, one instrumentation run per
case, and uninstalls the test APK again:

- --list: log the online targets (service name, protocol, model, attachment input, effort levels,
  readiness); sends nothing;
- --request CASE SERVICE MODEL [EFFORT] (repeatable): one real request in a new conversation, with
  the files the test generates itself (CASE image: a two-color PNG; pdf: a one-page PDF plus a text
  file; effort: no files, the conversation's effort set to EFFORT). The test deletes the
  conversation and its files and restores the default target and the preferences it touched.

--install installs the debug APK for the device ABI over the existing app first (data kept; never
uninstalls the app). --snapshot NAME records, through run-as on the debug build, the state the flow
must leave alone: digests of the default target, the services document, the local model catalog,
the conversation history and of the entries of every preference file, the names and sizes in files/models and the
number of stored attachment files. --compare NAME reads it again and prints, per item, only whether
it is the same as in NAME; no file content, key, ID or digest is printed. The saved states and the
instrumentation output stay under build/verification/attachments/<serial>/.

A remote adb server is used through the ADB_SERVER_SOCKET environment variable, for example
ADB_SERVER_SOCKET=tcp:100.107.160.16:5037.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys

from device_models_flow import preference_values
from device_speech_check import PACKAGE, RUNNER, apk_from_metadata

ROOT = Path(__file__).resolve().parents[1]
TEST = PACKAGE + ".ChatAttachmentRealDeviceFlowTest"
TAG = "P44Attachments"
DOCUMENTS = {
    "default target": "files/ai-default-target/default-target",
    "services": "files/ai-profiles/profiles.json",
    "local model catalog": "files/models/catalog.json",
    "conversation history": "files/conversation-history.bin",
}
CASES = ("image", "pdf", "effort")
STATUS = {"0": "PASS", "-2": "FAIL", "-1": "ERROR", "-3": "SKIPPED", "-4": "SKIPPED"}
SHA256 = re.compile(r"[0-9a-f]{64}")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--snapshot", metavar="NAME", help="record the state under NAME")
    parser.add_argument("--install", action="store_true", help="install the debug APK over the app (data kept)")
    parser.add_argument("--list", action="store_true", help="log the online targets, send nothing")
    parser.add_argument("--request", nargs="+", action="append", default=[], metavar="ARG",
                        help="CASE SERVICE MODEL [EFFORT]; CASE is one of " + ", ".join(CASES))
    parser.add_argument("--compare", metavar="NAME", help="compare the state with the one recorded as NAME")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/attachments")
    args = parser.parse_args()
    for request in args.request:
        if len(request) not in (3, 4) or request[0] not in CASES:
            parser.error(f"--request needs CASE SERVICE MODEL [EFFORT] with CASE in {CASES}")
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

    def digest(path):
        words = run_as("sha256sum", path).split()
        return words[0] if words and SHA256.fullmatch(words[0]) else None

    def read_state():
        state = {key: digest(path) for key, path in DOCUMENTS.items()}
        for name in sorted(run_as("ls", "shared_prefs").split()):
            if name.endswith(".xml"):
                # Entries, not bytes: a rewrite with the same values may store them in another order.
                values = preference_values(run_as("cat", f"shared_prefs/{name}"))
                canonical = "unparsable" if values is None else json.dumps(sorted(values.items()))
                state[f"prefs {name[:-4]}"] = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
        models = []
        for line in run_as("ls", "-ln", "files/models").splitlines():
            words = line.split()
            if len(words) >= 8 and words[0].startswith(("-", "d")):
                models.append([words[-1], words[4]])
        state["files/models (names and sizes)"] = sorted(models)
        attachments = run_as("find", "files/attachments", "-type", "f")
        state["stored attachment files"] = len([line for line in attachments.splitlines() if line.startswith("files/")])
        return state

    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    version = re.search(r"versionCode=(\d+)", shell("dumpsys", "package", PACKAGE, check=False))
    print(f"device {args.serial} model={model} api={sdk} abi={abi} versionCode={version.group(1) if version else 'none'}")

    if args.snapshot:
        state = read_state()
        (output / f"state-{args.snapshot}.json").write_text(json.dumps(state, indent=1), encoding="utf-8")
        present = sum(1 for key, value in state.items() if key in DOCUMENTS and value)
        prefs = sum(1 for key in state if key.startswith("prefs "))
        print(f"recorded {args.snapshot}: {present}/{len(DOCUMENTS)} documents, {prefs} preference files, "
              f"{len(state['files/models (names and sizes)'])} entries in files/models, "
              f"{state['stored attachment files']} attachment files")

    if args.install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
        version = re.search(r"versionCode=(\d+)", shell("dumpsys", "package", PACKAGE, check=False))
        print(f"installed the debug APK over the app (data kept), versionCode={version.group(1) if version else 'none'}")

    runs = []
    if args.list:
        runs.append(("list", ["-e", "case", "list"]))
    for request in args.request:
        extras = ["-e", "case", request[0], "-e", "service", request[1], "-e", "model", request[2]]
        if len(request) == 4:
            extras += ["-e", "effort", request[3]]
        runs.append((f"{request[0]} {request[2]}", extras))

    passed = True
    if runs:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
        try:
            for index, (label, extras) in enumerate(runs, start=1):
                since = shell("date", "+%m-%d %H:%M:%S.000").strip()
                result = shell("am", "instrument", "-w", "-r", "-e", "realAttachments", "true", *extras,
                               "-e", "class", TEST, RUNNER, timeout=900)
                (output / f"run-{index}-{label.split()[0]}.txt").write_text(result, encoding="utf-8")
                passed &= report(label, result)
                log = adb("logcat", "-d", "-T", since, "-s", f"{TAG}:I", check=False)
                for line in log.splitlines():
                    if f"{TAG}" in line and ":" in line.split(TAG, 1)[1]:
                        print("  " + line.split(TAG, 1)[1].lstrip(" :"))
        finally:
            # The test APK carries an exported fake recognizer; never leave it in system choosers.
            adb("uninstall", PACKAGE + ".test", check=False)

    if args.compare:
        saved = json.loads((output / f"state-{args.compare}.json").read_text(encoding="utf-8"))
        current = read_state()
        for key in sorted(set(saved) | set(current)):
            before, after = saved.get(key), current.get(key)
            if before == after:
                verdict = "same" if before not in (None, [], 0) else "same (absent)"
            elif key not in saved:
                verdict = "NEW"
            elif key not in current:
                verdict = "GONE"
            else:
                verdict = "CHANGED"
            passed &= verdict.startswith("same") or (key.startswith("prefs ") and verdict == "NEW")
            print(f"  {key}: {verdict} compared with {args.compare}")
    print(f"{'PASS' if passed else 'FAIL'} attachment flow")
    return 0 if passed else 1


def report(label, result):
    """Prints one line per finished test; True when nothing failed and the run completed."""
    passed = bool(re.search(r"^INSTRUMENTATION_CODE: -1", result, re.MULTILINE))
    current = None
    stack = None
    ran = False
    for line in result.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            current = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS: stack="):
            stack = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS_CODE:") and current:
            code = line.split(":", 1)[1].strip()
            if code != "1":
                verdict = STATUS.get(code, f"code {code}")
                passed &= verdict in ("PASS", "SKIPPED")
                if verdict != "SKIPPED":
                    ran = True
                    reason = f": {stack[:300]}" if stack and verdict != "PASS" else ""
                    print(f"{verdict} {label} ({current}){reason}")
            current = None
            stack = None
    if not ran:
        print(f"FAIL {label}: no test ran (target missing, or not accepted for this case)")
        passed = False
    if not re.search(r"^INSTRUMENTATION_CODE: -1", result, re.MULTILINE):
        last = result.strip().splitlines()[-1][:300] if result.strip() else "no output"
        print(f"  the instrumentation did not finish: {last}")
    return passed


if __name__ == "__main__":
    sys.exit(main())
