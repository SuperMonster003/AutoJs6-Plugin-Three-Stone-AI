#!/usr/bin/env python3
"""Check on a device that the app keeps one on-device engine, not one per process (roadmap P6-3b A2 (b), D227).

The chat (main process) and the AutoJs6 script provider (:provider) each own an engine cache. Before D227 a script call
after a local chat turn loaded a second copy of the model (about 1.5 GB more for Gemma 4 E2B on XQ-AT72). This script:

1. starts SpeechConversationRealDeviceTest#localEngineGivenBackToAScript (fake recognizer, no sound): one local voice
   round, after which the chat process holds the engine;
2. runs the README voice example in AutoJs6 with a fixed question and no listening or speaking
   (device_script_voice_example.py --question --no-speak --memory), so :provider loads the model and first asks the chat
   process to give its idle copy back;
3. brings the chat back for a second round, which loads the engine in the chat process again and asks :provider to give
   back its copy;
4. samples the PSS of both processes (dumpsys meminfo) after each step and prints the test's receipts.

Requires the debug app and its test APK installed (for example after device_speech_check.py --keep-test-apk), a ready
local model, and AutoJs6 with the voice example's modules. Nothing taps the screen; nothing is spoken or recorded.
Results: build/verification/engine-handover/<serial>/report.json (no conversation text).
"""

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.supermonster003.autojs6.plugin.threestoneai"
RUNNER = f"{PACKAGE}.test/androidx.test.runner.AndroidJUnitRunner"
TEST = f"{PACKAGE}.speech.SpeechConversationRealDeviceTest#localEngineGivenBackToAScript"
REMOTE = f"/sdcard/Android/data/{PACKAGE}/files/speech-check"
QUESTION = "What is two plus three? Answer with one number."


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--model", default="E2B", help="local target for the script (see device_script_voice_example.py)")
    parser.add_argument("--target-model", help="the chat's local target model id (default: the first ready one)")
    parser.add_argument("--timeout", type=int, default=900, help="seconds for the whole check")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/engine-handover")
    args = parser.parse_args()
    output = args.output / args.serial
    output.mkdir(parents=True, exist_ok=True)

    def adb(*command, check=True, timeout=120):
        result = subprocess.run([args.adb, "-s", args.serial, *map(str, command)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if check and result.returncode != 0:
            detail = (result.stderr or result.stdout).decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"adb {command[0]} failed: {detail[-400:]}")
        return result.stdout.decode("utf-8", errors="replace")

    def shell(*command, **kwargs):
        return adb("shell", shlex.join(map(str, command)), **kwargs)

    def receipt(name):
        raw = shell("cat", f"{REMOTE}/{name}.json", check=False)
        return json.loads(raw) if raw.lstrip().startswith("{") else None

    def wait_receipt(name, deadline):
        while time.time() < deadline:
            value = receipt(name)
            if value is not None:
                return value
            if instrument.poll() is not None:
                return None
            time.sleep(2)
        return None

    def pss():
        """PSS in MB of the main and :provider processes, by process name."""
        state = {}
        for name, key in ((PACKAGE, "main"), (PACKAGE + ":provider", "provider")):
            pid = shell("pidof", name, check=False).strip().split()
            if not pid:
                state[key] = None
                continue
            meminfo = shell("dumpsys", "meminfo", pid[0], check=False)
            total = re.search(r"TOTAL PSS:\s+(\d+)", meminfo) or re.search(r"^\s+TOTAL\s+(\d+)", meminfo, re.M)
            state[key] = round(int(total.group(1)) / 1024) if total else None
        return state

    print(f"device {args.serial} model={shell('getprop', 'ro.product.model').strip()} "
          f"api={shell('getprop', 'ro.build.version.sdk').strip()}")
    for name in ("engine-handover-ready", "engine-handover-released", "engine-handover-reloaded", "engine-handover",
                 "engine-handover-continue", "engine-handover-sampled"):
        shell("rm", "-f", f"{REMOTE}/{name}.json" if not name.endswith(("continue", "sampled")) else f"{REMOTE}/{name}",
              check=False)
    command = ["am", "instrument", "-w", "-r", "-e", "class", TEST, "-e", "engineHandover", "true"]
    if args.target_model:
        command += ["-e", "targetModel", args.target_model]
    instrument = subprocess.Popen([args.adb, "-s", args.serial, "shell", shlex.join(command + [RUNNER])],
                                  stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    deadline = time.time() + args.timeout
    report = {"steps": {}}
    try:
        ready = wait_receipt("engine-handover-ready", deadline)
        if ready is None:
            raise RuntimeError("the chat round did not finish (no engine-handover-ready receipt)")
        report["steps"]["chatHolds"] = pss()
        print(f"  1 chat holds the engine: {report['steps']['chatHolds']}", flush=True)

        script = subprocess.run([sys.executable, str(ROOT / ".python/device_script_voice_example.py"),
                                 "--serial", args.serial, "--adb", args.adb, "--model", args.model, "--runs", "1",
                                 "--question", QUESTION, "--no-speak", "--memory", "--label", "engine-handover"],
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=600)
        script_lines = script.stdout.decode("utf-8", errors="replace").splitlines()
        for line in script_lines:
            if line.startswith(("PASS", "FAIL", "    ")):
                print(f"  2 script: {line.strip()}", flush=True)
        report["script"] = {"exitCode": script.returncode,
                            "lines": [line.strip() for line in script_lines if line.startswith(("PASS", "FAIL", "    "))]}
        released = wait_receipt("engine-handover-released", deadline)
        report["steps"]["scriptLoaded"] = pss()
        report["released"] = released
        print(f"  2 after the script: {report['steps']['scriptLoaded']} chat receipt={released}", flush=True)

        # Back to the chat for the second round; the test also asks for it with ActivityScenario.
        shell("am", "start", "-n", f"{PACKAGE}/.ChatActivity", "--activity-reorder-to-front", check=False)
        shell("touch", f"{REMOTE}/engine-handover-continue", check=False)
        reloaded = wait_receipt("engine-handover-reloaded", deadline)
        time.sleep(3)
        report["steps"]["chatReloaded"] = pss()
        report["reloaded"] = reloaded
        print(f"  3 chat loaded again: {report['steps']['chatReloaded']} chat receipt={reloaded}", flush=True)
        shell("touch", f"{REMOTE}/engine-handover-sampled", check=False)
        final = wait_receipt("engine-handover", deadline)
        report["receipt"] = final
    finally:
        try:
            instrument.wait(timeout=max(30, deadline - time.time()))
        except subprocess.TimeoutExpired:
            instrument.kill()
        log = instrument.stdout.read().decode("utf-8", errors="replace") if instrument.stdout else ""
        failures = [line for line in log.splitlines() if "INSTRUMENTATION_STATUS: stack=" in line or "AssertionError" in line]
        report["instrumentation"] = {"ok": "OK (1 test)" in log, "failures": failures[:5]}
        for name in ("engine-handover-ready", "engine-handover-released", "engine-handover-reloaded", "engine-handover"):
            shell("rm", "-f", f"{REMOTE}/{name}.json", check=False)
        shell("rm", "-f", f"{REMOTE}/engine-handover-continue", f"{REMOTE}/engine-handover-sampled", check=False)
    (output / "report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    steps = report["steps"]
    provider_freed = (steps.get("scriptLoaded", {}).get("provider") or 0) - (steps.get("chatReloaded", {}).get("provider") or 0)
    print(f"{'PASS' if report['instrumentation']['ok'] else 'FAIL'} engine-handover: receipt={report.get('receipt')} "
          f"providerFreedMb={provider_freed}")
    for line in report["instrumentation"]["failures"]:
        print(f"  {line[:300]}")
    print(f"  report -> {output / 'report.json'}")
    return 0 if report["instrumentation"]["ok"] and report["script"]["exitCode"] == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
