#!/usr/bin/env python3
"""Downgrade check of message versions on an emulator (roadmap P6, D148).

Seeds, with the debug build of this checkout, a conversation whose first turn has three versions
(the third shown, the first holding the only attachment) plus a plain conversation, through
ChatBranchCompatibilityProbe. Then, for each older build given:

- --old-apk APK --old-test-apk APK (an older debug build and a test APK built from the same commit
  that contains OldBuildBranchProbe, steps open / sweep / send): installs it over the app (data
  kept), opens the conversation, runs the storage cleanup sweep of the model manager and sends one
  scripted message (closing the chat each time), then installs this checkout's build again and
  reads the conversation back.
- --release-apk APK (a release build, signed with the release key): uninstalls the app, installs
  the release, copies the seeded history, attachments and navigation preferences into it as root,
  opens the chat, saves a screenshot and the test texts it shows, leaves it with the home key, then
  installs this checkout's build again with the history the release left.

Each phase prints whether the older build rewrote the history file (on opening and leaving the
chat, after the sweep, after a message), what it shows, and how many versions and whether the
hidden file are left for this checkout's build. Only the test's own texts are printed. Emulators only (needs `adb root` and
uninstalls the app); the outputs stay under build/verification/branches/<serial>/.
"""

import argparse
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time

from device_speech_check import PACKAGE, RUNNER, apk_from_metadata

ROOT = Path(__file__).resolve().parents[1]
PROBE = PACKAGE + ".ChatBranchCompatibilityProbe"
OLD_PROBE = PACKAGE + ".OldBuildBranchProbe"
DATA = f"/data/data/{PACKAGE}"
HISTORY = f"{DATA}/files/conversation-history.bin"
COPIED = ("files/conversation-history.bin", "files/attachments", "shared_prefs/conversation-navigation.xml")
TEST_TEXTS = ("Q", "A1", "Q2", "B1", "A2", "A3", "old question", "old reply")


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--old-apk", type=Path)
    parser.add_argument("--old-test-apk", type=Path)
    parser.add_argument("--release-apk", type=Path)
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/branches")
    args = parser.parse_args()
    if not args.serial.startswith("emulator-"):
        parser.error("emulators only: the check uninstalls the app and needs adb root")
    if (args.old_apk is None) != (args.old_test_apk is None):
        parser.error("--old-apk and --old-test-apk go together")
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

    def digest():
        words = shell("sha256sum", HISTORY, check=False).split()
        return words[0] if words and re.fullmatch(r"[0-9a-f]{64}", words[0]) else None

    def version_code():
        found = re.search(r"versionCode=(\d+)", shell("dumpsys", "package", PACKAGE, check=False))
        return found.group(1) if found else "none"

    def instrument(test, step_key, step, label):
        result = shell("am", "instrument", "-w", "-r", "-e", step_key, step, "-e", "class", test, RUNNER)
        (output / f"{label}.txt").write_text(result, encoding="utf-8")
        values = dict(re.findall(r"^INSTRUMENTATION_STATUS: (?:probe|old)\.(\w+)=(.*)$", result, re.MULTILINE))
        failed = re.search(r"^INSTRUMENTATION_STATUS_CODE: -[24]", result, re.MULTILINE) or \
            not re.search(r"^INSTRUMENTATION_CODE: -1", result, re.MULTILINE)
        shown = ", ".join(f"{key}={value.strip()}" for key, value in values.items())
        print(f"{'FAIL' if failed else 'PASS'} {label}: {shown}")
        return not failed, values

    def install_current(downgrade_allowed=True):
        flags = ["-r", "-d", "-t"] if downgrade_allowed else ["-r", "-t"]
        abi = shell("getprop", "ro.product.cpu.abi").strip()
        adb("install", *flags, apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
        adb("install", "-r", "-t", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))

    def root():
        adb("root")
        adb("wait-for-device")
        for _ in range(20):
            if shell("id", "-u", check=False).strip() == "0":
                return
            time.sleep(1)
        raise RuntimeError("adb root did not take effect")

    def rewritten(before, label):
        after = digest()
        if before is None or after is None:
            print(f"  history file MISSING {label}")
        else:
            print(f"  history file {'REWRITTEN' if after != before else 'unchanged'} {label}")
        return after

    print(f"device {args.serial} api={shell('getprop', 'ro.build.version.sdk').strip()} app versionCode={version_code()}")
    root()
    passed = True

    if args.old_apk:
        install_current()
        ok, _ = instrument(PROBE, "branchProbe", "seed", "current-seed")
        if not ok:
            raise RuntimeError("seeding with this checkout's build failed")
        before = digest()
        adb("install", "-r", "-d", "-t", args.old_apk)
        adb("install", "-r", "-t", args.old_test_apk)
        print(f"  installed the older build over the app, versionCode={version_code()}")
        ok, _ = instrument(OLD_PROBE, "oldProbe", "open", "old-open")
        passed &= ok
        before = rewritten(before, "by opening and closing the chat")
        ok, _ = instrument(OLD_PROBE, "oldProbe", "sweep", "old-sweep")
        passed &= ok
        before = rewritten(before, "by the storage cleanup")
        ok, _ = instrument(OLD_PROBE, "oldProbe", "send", "old-send")
        passed &= ok
        rewritten(before, "by sending a message")
        install_current(downgrade_allowed=False)
        print(f"  installed this checkout's build again, versionCode={version_code()}")
        ok, _ = instrument(PROBE, "branchProbe", "inspect", "current-after-old")
        passed &= ok
        passed &= instrument(PROBE, "branchProbe", "clean", "current-clean")[0]

    if args.release_apk:
        install_current()
        ok, _ = instrument(PROBE, "branchProbe", "seed", "release-seed")
        if not ok:
            raise RuntimeError("seeding with this checkout's build failed")
        saved = output / "seeded"
        pull_data(adb, shell, saved)
        adb("uninstall", PACKAGE + ".test", check=False)
        adb("uninstall", PACKAGE)
        adb("install", "-t", args.release_apk)
        print(f"  installed the release in place of the app, versionCode={version_code()}")
        push_data(adb, shell, saved)
        before = digest()
        shell("am", "start", "-W", "-n", f"{PACKAGE}/.ChatActivity")
        time.sleep(6)
        shell("screencap", "-p", "/data/local/tmp/p6-release.png")
        adb("pull", "/data/local/tmp/p6-release.png", output / "release-open.png")
        shell("rm", "/data/local/tmp/p6-release.png", check=False)
        shell("uiautomator", "dump", "/data/local/tmp/p6-release.xml", check=False)
        dump = shell("cat", "/data/local/tmp/p6-release.xml", check=False)
        shell("rm", "/data/local/tmp/p6-release.xml", check=False)
        texts = [text for text in re.findall(r' text="([^"]*)"', dump) if text in TEST_TEXTS]
        print(f"  the release shows: {', '.join(texts) or 'none of the test texts'} (screenshot release-open.png)")
        before = rewritten(before, "while the release shows the chat")
        # Leaving the chat runs onStop, where the chat stores the conversation it shows.
        shell("input", "keyevent", "KEYCODE_HOME")
        time.sleep(3)
        before = rewritten(before, "by leaving the chat in the release")
        shell("am", "force-stop", PACKAGE)
        left = output / "release-left"
        pull_data(adb, shell, left)
        adb("uninstall", PACKAGE)
        install_current()
        push_data(adb, shell, left)
        print(f"  installed this checkout's build again with the release's history, versionCode={version_code()}")
        ok, _ = instrument(PROBE, "branchProbe", "inspect", "current-after-release")
        passed &= ok
        passed &= instrument(PROBE, "branchProbe", "clean", "release-clean")[0]

    adb("unroot", check=False)
    print(f"{'PASS' if passed else 'FAIL'} branch downgrade check")
    return 0 if passed else 1


def pull_data(adb, shell, directory):
    directory.mkdir(parents=True, exist_ok=True)
    shell("am", "force-stop", PACKAGE)
    shell("rm", "-rf", "/data/local/tmp/p6-data", check=False)
    shell("mkdir", "-p", "/data/local/tmp/p6-data/files", "/data/local/tmp/p6-data/shared_prefs")
    for path in COPIED:
        shell("cp", "-r", f"{DATA}/{path}", f"/data/local/tmp/p6-data/{path}", check=False)
    adb("pull", "/data/local/tmp/p6-data", directory)
    shell("rm", "-rf", "/data/local/tmp/p6-data", check=False)


def push_data(adb, shell, directory):
    # Start once so that the app's data directories exist, then put the files in place as root.
    shell("am", "start", "-W", "-n", f"{PACKAGE}/.ChatActivity")
    time.sleep(3)
    shell("am", "force-stop", PACKAGE)
    owner = shell("stat", "-c", "%u", DATA).strip()
    shell("rm", "-rf", "/data/local/tmp/p6-data", check=False)
    adb("push", directory / "p6-data", "/data/local/tmp/")
    for path in COPIED:
        shell("rm", "-rf", f"{DATA}/{path}", check=False)
        shell("cp", "-r", f"/data/local/tmp/p6-data/{path}", f"{DATA}/{path}", check=False)
    shell("chown", "-R", f"{owner}:{owner}", f"{DATA}/files", f"{DATA}/shared_prefs")
    shell("restorecon", "-R", f"{DATA}/files", f"{DATA}/shared_prefs")
    shell("rm", "-rf", "/data/local/tmp/p6-data", check=False)


if __name__ == "__main__":
    sys.exit(main())
