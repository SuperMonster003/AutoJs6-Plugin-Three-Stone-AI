#!/usr/bin/env python3
"""Opt-in "Models and services" flows on one device (roadmap 1.7.0 P5.7, D130).

Installs the androidTest APK (and, unless --no-install, the debug APK for the device ABI over the
existing app, data kept), runs ModelsAndServicesRealDeviceFlowTest with `-e realDeviceFlow true`
and uninstalls the test APK again:

- always: add a test service (placeholder key, `.invalid` address) with two models on the service
  details page, make one of them the default from the "Models and services" page, check that a new
  conversation uses it, then delete the service;
- --real-request: make an existing ready target with --request-model (default gpt-6.1-sol) the
  default and get one short reply in a new conversation (one real request);
- --download: download --download-model (default qwen2-5-1-5b-instruct) from the page in one step,
  check the import, then delete that model only.

Before and after the run it reads, through run-as on the debug build, the stored default target, the
service IDs, the local model catalog (entries and selected model) and the preference files the tests
restore, and prints only whether each one is the same as before, with counts; no file content, key,
ID or digest is printed. The instrumentation output is saved under build/verification/flow/<serial>/.

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
import xml.etree.ElementTree as ElementTree

from device_speech_check import PACKAGE, RUNNER, apk_from_metadata

ROOT = Path(__file__).resolve().parents[1]
TEST = PACKAGE + ".ModelsAndServicesRealDeviceFlowTest"
TAG = "P57DeviceFlow"
TARGET_FILE = "files/ai-default-target/default-target"
PROFILES = "files/ai-profiles/profiles.json"
CATALOG = "files/models/catalog.json"
PREFERENCES = ["model_picker", "online-ai-base-url-history", "context-token-calibration", "conversation-navigation"]
STATUS = {"0": "PASS", "-2": "FAIL", "-1": "ERROR", "-3": "SKIPPED", "-4": "SKIPPED"}


def profile_ids(text):
    try:
        document = json.loads(text)
    except ValueError:
        return None
    profiles = document.get("profiles") if isinstance(document, dict) else None
    if not isinstance(profiles, list):
        return None
    return sorted(str(item.get("profileId")) for item in profiles if isinstance(item, dict))


def catalog_state(text):
    try:
        document = json.loads(text)
    except ValueError:
        return None
    if not isinstance(document, dict):
        return None
    entries = document.get("entries", [])
    ids = sorted(str(item.get("modelId", item.get("id"))) for item in entries if isinstance(item, dict))
    return ids, document.get("selectedModelId")


def preference_values(text):
    """The entries of a SharedPreferences XML file; a missing or empty file has none."""
    if not text.strip().startswith("<"):
        return {}
    try:
        root = ElementTree.fromstring(text)
    except ElementTree.ParseError:
        return None
    values = {}
    for element in root:
        name = element.get("name")
        if element.tag == "set":
            values[name] = ("set", tuple(sorted(child.text or "" for child in element)))
        else:
            values[name] = (element.tag, element.get("value", element.text or ""))
    return values


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--no-install", action="store_true", help="keep the installed debug APK")
    parser.add_argument("--real-request", action="store_true", help="also send one real request")
    parser.add_argument("--request-model", default="gpt-6.1-sol")
    parser.add_argument("--download", action="store_true", help="also download and delete a recommended model")
    parser.add_argument("--download-model", default="qwen2-5-1-5b-instruct")
    parser.add_argument("--download-timeout-minutes", type=int, default=60)
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/flow")
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
            "local models": catalog_state(run_as("cat", CATALOG)),
            **{f"prefs {name}": preference_values(run_as("cat", f"shared_prefs/{name}.xml")) for name in PREFERENCES},
        }

    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    print(f"device {args.serial} model={model} api={sdk} abi={abi}")
    if not args.no_install:
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
    before = read_state()
    adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
    extras = ["-e", "realDeviceFlow", "true"]
    if args.real_request:
        extras += ["-e", "realRequest", "true", "-e", "realRequestModel", args.request_model]
    if args.download:
        extras += ["-e", "realDownload", "true", "-e", "downloadModel", args.download_model,
                   "-e", "downloadTimeoutMinutes", str(args.download_timeout_minutes)]
    timeout = 900 + (args.download_timeout_minutes * 60 if args.download else 0)
    adb("logcat", "-c", check=False)
    try:
        result = shell("am", "instrument", "-w", "-r", *extras, "-e", "class", TEST, RUNNER, timeout=timeout)
    finally:
        # The test APK carries an exported fake recognizer; never leave it in system choosers.
        adb("uninstall", PACKAGE + ".test", check=False)
    (output / "flow-instrumentation.txt").write_text(result, encoding="utf-8")

    passed = "INSTRUMENTATION_CODE: -1" in result
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
                verdict = STATUS.get(code, f"code {code}")
                passed &= verdict in ("PASS", "SKIPPED")
                reason = stack if stack and (verdict != "PASS") else ""
                print(f"{verdict} {current}" + (f": {reason[:300]}" if reason else ""))
            current = None
            stack = None
    if not re.search(r"^INSTRUMENTATION_CODE: -1", result, re.MULTILINE):
        print("  the instrumentation did not finish: " + result.strip().splitlines()[-1][:300] if result.strip() else "no output")
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
        elif key == "local models" and value is not None:
            count = f" ({len(value[0])} models, selected {'set' if value[1] else 'none'})"
        elif key.startswith("prefs") and value is not None:
            count = f" ({len(value)} entries)"
        print(f"  {key}: {'same as before' if same else 'CHANGED'}{count}")
    print(f"{'PASS' if passed else 'FAIL'} models and services flow")
    return 0 if passed else 1


if __name__ == "__main__":
    sys.exit(main())
