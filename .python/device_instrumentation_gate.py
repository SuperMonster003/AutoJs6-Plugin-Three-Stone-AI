#!/usr/bin/env python3
"""Release-gate instrumentation classes on one device, keeping the app data (roadmap P8.2, P9.2).

--install installs the debug APK for the device ABI over the existing app (data kept; never
uninstalls the app). --run installs the androidTest APK, runs each class in GATE (or --classes) in
its own `am instrument` call, prints one line per class (passed / failed / skipped counts and the
start of the first failure), removes the report files the classes left in the app's external
files and the test documents in Download, and uninstalls the test APK again.

--snapshot NAME / --compare NAME record and compare, through run-as on the debug build, the state
the gate must leave alone: the items of `device_attachments_flow.py` (digests of the default
target, the services document, the local model catalog and the conversation history, the entries
of every preference file, the names and sizes in files/models, the number of attachment files)
plus the content of the services document and of the model catalog without their revision
counters. A class that adds and deletes a test service stores the same services again with a
higher revision; that counts as kept when the content is the same. Only same / changed per item
is printed, never file content, keys, IDs or digests. Both refuse when run-as cannot reach the app:
on a release install (not debuggable) every item would read as gone, and an unchanged dumpsys
firstInstallTime is what shows that its data was kept.

--build points at the checkout whose app/build/outputs hold the APKs (default: this checkout), so
a clean clone of the release commit can be tested. The instrumentation output stays under
build/verification/gate/<serial>/ of this checkout.

The classes in GATE need no real AI request, no person and no emulator; LEFT_OUT lists the others
with the reason. A remote adb server is used through the ADB_SERVER_SOCKET environment variable,
for example ADB_SERVER_SOCKET=tcp:100.107.160.16:5037.
"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys

from device_attachments_flow import DOCUMENTS, read_state
from device_speech_check import PACKAGE, REMOTE, RUNNER, apk_from_metadata

ROOT = Path(__file__).resolve().parents[1]
GATE = [PACKAGE + name for name in (
    ".AbiCompatibilityAndroidTest",
    ".AboutIconFrameTest",
    ".ChatAttachmentDeviceTest",
    ".ChatBranchDeviceTest",
    ".ChatExportDeviceTest",
    ".ChatGenerationContinuityDeviceTest",
    ".ChatReasoningCardDeviceTest",
    ".HostSnapshotThreadTest",
    ".LauncherIconResourceTest",
    ".ModelDownloadDeviceTest",
    ".ModelPickerSheetDeviceTest",
    ".PluginDiscoveryAndroidTest",
    ".ServiceDetailDeviceTest",
    ".SettingsNavigationTest",
    ".provider.NativeToolSessionAndroidTest",
    ".provider.OwnedParcelFileDescriptorsAndroidTest",
    ".provider.VisionSessionAndroidTest",
    ".speech.SpeechConversationDeviceTest",
    ".speech.SpeechDictationDeviceTest",
    ".speech.SpeechReadingDeviceTest",
    ".speech.SpeechSettingsDeviceTest",
    ".speech.SpeechVoiceCommandDeviceTest",
)]
LEFT_OUT = {
    "AppearanceSettingsTest, LauncherIcon{ApplyAuto,Migration,PrepareDark,PrepareUpgrade,Selection}Test":
        "change the appearance or the launcher alias of the installed app; emulator only",
    "ModelsAndServicesScreenshotTest": "screenshots for the evidence, emulator",
    "ChatAttachmentPickerEmulatorTest": "presses the system pickers, emulator only (D131)",
    "EngineCacheCleanupDeviceTest": "needs an empty model catalog; skipped on a phone with models",
    "ChatAttachmentRealDeviceFlowTest, ChatBranchRealDeviceFlowTest, ModelsAndServicesRealDeviceFlowTest, "
    "OnlineVisionAcceptanceAndroidTest, SpeechConversationRealDeviceTest": "real AI requests, opt-in",
    "LiteRtRealModelInferenceInstrumentationTest": "real local model, opt-in",
    "SpeechEngineLatencyDeviceTest": "latency measurement that plays sound",
    "SpeechManualDeviceTest": "waits for a person to speak",
    "ReleaseAbiSmokeInstrumentation": "own runner, see device_release_smoke.py",
    "ChatBranchCompatibilityProbe": "probe for device_branch_compat.py",
}
CONTENT = {
    "services": "files/ai-profiles/profiles.json",
    "local model catalog": "files/models/catalog.json",
}
# Counters the stores raise on every write; the rest of the document is the content.
VOLATILE_KEYS = {"revision", "updatedAtEpochMillis"}
DOWNLOAD = "/sdcard/Download"
TEST_FILE_PREFIX = "3-Stone-P7-test-"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--build", type=Path, default=ROOT, help="checkout holding app/build/outputs")
    parser.add_argument("--snapshot", metavar="NAME", help="record the state under NAME")
    parser.add_argument("--install", action="store_true", help="install the debug APK over the app (data kept)")
    parser.add_argument("--run", action="store_true", help="run the gate classes")
    parser.add_argument("--classes", nargs="+", metavar="CLASS", help="run these classes instead of GATE")
    parser.add_argument("--compare", metavar="NAME", help="compare the state with the one recorded as NAME")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/gate")
    parser.add_argument("--list", action="store_true", help="print the gate classes and the ones left out")
    args = parser.parse_args()
    if args.list:
        print("\n".join(GATE))
        for names, reason in LEFT_OUT.items():
            print(f"left out: {names}: {reason}")
        return 0
    output = args.output / args.serial
    output.mkdir(parents=True, exist_ok=True)
    outputs = args.build / "app/build/outputs/apk"

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

    def names(directory):
        listing = shell("ls", "-1", directory, check=False)
        return set() if "No such file" in listing else {line.strip() for line in listing.splitlines() if line.strip()}

    def state():
        current = read_state(run_as)
        for key, path in CONTENT.items():
            current[f"{key} (content)"] = content_digest(run_as("cat", path))
        return current

    sdk = shell("getprop", "ro.build.version.sdk").strip()
    model = shell("getprop", "ro.product.model").strip()
    abi = shell("getprop", "ro.product.cpu.abi").strip()
    print(f"device {args.serial} model={model} api={sdk} abi={abi} versionCode={version_code()}")

    if args.snapshot:
        problem = run_as_problem(run_as("id"))
        if problem:
            print(f"FAIL {problem}; nothing recorded")
            return 1
        recorded = state()
        (output / f"state-{args.snapshot}.json").write_text(json.dumps(recorded, indent=1), encoding="utf-8")
        present = sum(1 for key, value in recorded.items() if key in DOCUMENTS and value)
        prefs = sum(1 for key in recorded if key.startswith("prefs "))
        print(f"recorded {args.snapshot}: {present}/{len(DOCUMENTS)} documents, {prefs} preference files, "
              f"{len(recorded['files/models (names and sizes)'])} entries in files/models, "
              f"{recorded['stored attachment files']} attachment files")

    if args.install or args.run:
        # A checkout built before sign.properties was in place signs with the Android debug key,
        # which the installed app refuses; find that out before a slow remote install.
        signers = {}
        for kind, apk in (("app", apk_from_metadata(outputs / "debug", abi)),
                          ("test", apk_from_metadata(outputs / "androidTest/debug"))):
            signers[kind] = apk_signer(apk)
            print(f"{kind} APK {apk.name}: signer {signers[kind][0][:12]}{' (Android debug key)' if signers[kind][1] else ''}")
        problem = signer_problem(signers)
        if problem:
            print(f"FAIL {problem}; nothing installed")
            return 1

    if args.install:
        adb("install", "-r", "-t", apk_from_metadata(outputs / "debug", abi))
        print(f"installed the debug APK over the app (data kept), versionCode={version_code()}")

    passed = True
    if args.run:
        reports_before = names(REMOTE)
        downloads_before = {name for name in names(DOWNLOAD) if name.startswith(TEST_FILE_PREFIX)}
        adb("install", "-r", "-t", apk_from_metadata(outputs / "androidTest/debug"))
        totals = [0, 0, 0]
        try:
            for name in args.classes or GATE:
                result = shell("am", "instrument", "-w", "-r", "-e", "class", name, RUNNER, timeout=1200)
                (output / f"run-{name.rsplit('.', 1)[-1]}.txt").write_text(result, encoding="utf-8")
                ok, counts, line = summarize(name.rsplit(".", 1)[-1], result)
                totals = [a + b for a, b in zip(totals, counts)]
                passed &= ok
                print(line)
        finally:
            # The test APK carries an exported fake recognizer; never leave it in system choosers.
            adb("uninstall", PACKAGE + ".test", check=False)
        print(f"{'PASS' if passed else 'FAIL'} {len(args.classes or GATE)} classes: "
              f"{totals[0]} passed, {totals[1]} failed, {totals[2]} skipped")
        for name in sorted(names(REMOTE) - reports_before):
            shell("rm", "-rf", f"{REMOTE}/{name}", check=False)
        if not reports_before:
            shell("rmdir", REMOTE, check=False)
        for name in sorted({name for name in names(DOWNLOAD) if name.startswith(TEST_FILE_PREFIX)} - downloads_before):
            shell("rm", "-f", f"{DOWNLOAD}/{name}", check=False)
        left = len(names(REMOTE) - reports_before)
        left += len({name for name in names(DOWNLOAD) if name.startswith(TEST_FILE_PREFIX)} - downloads_before)
        print(f"{'PASS' if not left else 'FAIL'} leftovers: {left}")
        passed &= not left

    if args.compare:
        problem = run_as_problem(run_as("id"))
        if problem:
            print(f"FAIL {problem}; nothing compared")
            passed = False
        else:
            saved = json.loads((output / f"state-{args.compare}.json").read_text(encoding="utf-8"))
            same, lines = compare(saved, state(), args.compare)
            print("\n".join(lines))
            passed &= same
    print(f"{'PASS' if passed else 'FAIL'} instrumentation gate")
    return 0 if passed else 1


def apk_signer(apk):
    """Returns (certificate SHA-256, signed with the Android debug key) of the first signer."""
    tools = sorted((sdk_directory() / "build-tools").iterdir(), key=lambda path: [
        int(part) if part.isdigit() else 0 for part in re.split(r"[.-]", path.name)])
    apksigner = tools[-1] / ("apksigner.bat" if sys.platform == "win32" else "apksigner")
    result = subprocess.run([str(apksigner), "verify", "--print-certs", str(apk)],
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=300)
    text = result.stdout.decode("utf-8", errors="replace")
    found = re.search(r"certificate SHA-256 digest: ([0-9a-fA-F]{64})", text)
    if result.returncode != 0 or not found:
        raise RuntimeError(f"apksigner cannot read {apk.name}: {text.strip()[-300:]}")
    return found.group(1).lower(), "CN=Android Debug" in text


def sdk_directory():
    for line in (ROOT / "local.properties").read_text(encoding="utf-8").splitlines():
        if line.startswith("sdk.dir="):
            return Path(line.split("=", 1)[1].replace("\\:", ":").replace("\\\\", "\\"))
    raise RuntimeError("sdk.dir is not set in local.properties")


def signer_problem(signers):
    """Why the app and test APKs cannot be installed over the official app, or None."""
    if any(debug_key for _, debug_key in signers.values()):
        return "an APK is signed with the Android debug key (build after copying sign.properties)"
    if len({digest for digest, _ in signers.values()}) != 1:
        return "the app and test APKs have different signers"
    return None


def run_as_problem(output):
    """Why run-as cannot read the app data, from the stdout of `run-as <package> id`, or None."""
    if output.strip().startswith("uid="):
        return None
    return ("run-as cannot read the app data (a release install is not debuggable; there an unchanged "
            "dumpsys firstInstallTime shows the data was kept)")


def content_digest(text):
    """Digest of a JSON document without its write counters; None when it is absent or unreadable."""
    try:
        document = json.loads(text)
    except ValueError:
        return None
    if isinstance(document, dict):
        document = {key: value for key, value in document.items() if key not in VOLATILE_KEYS}
    return hashlib.sha256(json.dumps(document, sort_keys=True).encode("utf-8")).hexdigest()


def compare(saved, current, name):
    """Returns (all kept, one line per item). A rewritten document with the same content is kept."""
    same = True
    lines = []
    for key in sorted(set(saved) | set(current)):
        before, after = saved.get(key), current.get(key)
        content = f"{key} (content)"
        if before == after:
            verdict = "same" if before not in (None, [], 0) else "same (absent)"
        elif key not in saved:
            verdict = "NEW"
        elif key not in current:
            verdict = "GONE"
        elif key in CONTENT and saved.get(content) and saved.get(content) == current.get(content):
            verdict = "rewritten, same content"
        else:
            verdict = "CHANGED"
        same &= verdict.startswith(("same", "rewritten")) or (key.startswith("prefs ") and verdict == "NEW")
        lines.append(f"  {key}: {verdict} compared with {name}")
    return same, lines


def summarize(label, result):
    """Returns (passed, [passed, failed, skipped], one line) for one `am instrument -w -r` output."""
    counts = [0, 0, 0]
    first = None
    current = stack = None
    for line in result.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: test="):
            current = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS: stack="):
            stack = line.split("=", 1)[1].strip()
        elif line.startswith("INSTRUMENTATION_STATUS_CODE:") and current:
            code = line.split(":", 1)[1].strip()
            if code == "0":
                counts[0] += 1
            elif code in ("-3", "-4"):
                counts[2] += 1
            elif code != "1":
                counts[1] += 1
                first = first or f"{current}: {(stack or 'code ' + code)[:200]}"
            if code != "1":
                current = stack = None
    finished = bool(re.search(r"^INSTRUMENTATION_CODE: -1", result, re.MULTILINE))
    passed = finished and counts[1] == 0 and counts[0] > 0
    line = f"{'PASS' if passed else 'FAIL'} {label}: {counts[0]} passed, {counts[1]} failed, {counts[2]} skipped"
    if first:
        line += f"; first failure {first}"
    elif not finished:
        last = result.strip().splitlines()[-1][:200] if result.strip() else "no output"
        line += f"; the instrumentation did not finish: {last}"
    elif counts[0] == 0:
        line += "; no test passed"
    return passed, counts, line


if __name__ == "__main__":
    sys.exit(main())
