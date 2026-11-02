#!/usr/bin/env python3
"""Send one prompt through the launcher chat on a connected device and report how the reply ended.

Opens ChatActivity, starts a new conversation, optionally switches the target through the model
switcher, types the prompt, taps Send, then polls the accessibility tree until the assistant
message carries its meta line: the usage line, the "Output limit reached" marker, "Generation
stopped" or the failure hint. Prints one line per step, the outcome, the token counts and an
escaped tail of the reply; it never prints the whole reply or any credential.

`input text` goes through the device IME: a pinyin keyboard rewrites letters, spaces and
punctuation, so switch the keyboard to an English layout first. The typed text is read back and
compared before Send is tapped; on a mismatch the field is cleared and nothing is sent. The texts
to match come from the app's own string resources: pass --locale for the values-* qualifier the
device UI uses (the app follows the AutoJs6 host language, not the system locale).
Confirmation dialogs are never tapped by this script. Written for the Anthropic max_tokens
truncation fix of 2026-11-02 (ROADMAP "Anthropic 输出上限与截断标注").

A failed reply also reports the provider explanation shown under the failure message (the
"Provider response:" paragraph added the same day), and --check-keep-screen-on samples the chat
window's FLAG_KEEP_SCREEN_ON right after Send and again once the reply has ended: it must be held
while generating and released afterwards.
"""

import argparse
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.supermonster003.autojs6.plugin.threestoneai"
ACTIVITY = PACKAGE + "/.ChatActivity"
STRING_NAMES = (
    "chat_send",
    "chat_new_conversation",
    "chat_open_model_switcher",
    "chat_generation_truncated",
    "chat_generation_stopped",
    "chat_generation_failure_choose_target",
    "chat_generation_failure_detail",
    "chat_usage_format",
)


def load_strings(locale):
    directory = "values" if locale in ("", "en") else f"values-{locale}"
    path = ROOT / "app" / "src" / "main" / "res" / directory / "strings.xml"
    root = ET.parse(path).getroot()
    found = {}
    for node in root.iter("string"):
        name = node.get("name")
        if name in STRING_NAMES:
            text = node.text or ""
            found[name] = text.replace('\\"', '"').replace("\\'", "'").replace("\\n", "\n")
    missing = [name for name in STRING_NAMES if name not in found]
    if missing:
        raise SystemExit(f"strings missing in {path}: {', '.join(missing)}")
    return found


def usage_pattern(format_text):
    """Turn chat_usage_format into a regex capturing the input and output token counts."""
    pattern = re.escape(format_text)
    pattern = pattern.replace(re.escape("%1$d"), r"(?P<input>\d[\d,]*)")
    pattern = pattern.replace(re.escape("%2$d"), r"(?P<output>\d[\d,]*)")
    pattern = pattern.replace(re.escape("%3$s"), r".+?")
    pattern = pattern.replace(re.escape("%4$d"), r"\d[\d,]*")
    return re.compile(pattern)


def adb(serial, *args, check=True):
    completed = subprocess.run(
        ["adb", "-s", serial, *args],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if check and completed.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {completed.stderr.strip()}")
    return completed.stdout


def dump(serial):
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/three_stone_probe.xml")
    xml = adb(serial, "exec-out", "cat", "/sdcard/three_stone_probe.xml")
    start = xml.find("<?xml")
    if start < 0:
        start = xml.find("<hierarchy")
    if start < 0:
        raise RuntimeError("uiautomator dump returned no hierarchy")
    return ET.fromstring(xml[start:])


def bounds_center(node):
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not match:
        return None
    left, top, right, bottom = map(int, match.groups())
    return (left + right) // 2, (top + bottom) // 2


def nodes(root):
    return [node for node in root.iter("node") if node.get("package") == PACKAGE]


def find(root, **attributes):
    for node in nodes(root):
        if all(node.get(key) == value for key, value in attributes.items()):
            return node
    return None


def find_text_containing(root, fragment, class_suffix=None):
    for node in nodes(root):
        if class_suffix and not node.get("class", "").endswith(class_suffix):
            continue
        if fragment in (node.get("text") or ""):
            return node
    return None


def texts(root):
    return [node.get("text", "") for node in nodes(root) if node.get("text")]


def tap(serial, node):
    center = bounds_center(node)
    if center is None:
        raise RuntimeError("Node has no bounds")
    adb(serial, "shell", "input", "tap", str(center[0]), str(center[1]))


def escaped(value):
    return value.encode("unicode_escape").decode("ascii")


def target_line(root):
    return next((text for text in texts(root) if " | " in text and "Token" not in text), "")


def keep_screen_on(serial):
    """True when the chat's base window holds FLAG_KEEP_SCREEN_ON, False when not, None when it is not listed."""
    output = adb(serial, "shell", "dumpsys", "window", "windows", check=False)
    blocks = re.split(r"\n(?=\s*Window #\d+ )", output)
    candidates = [block for block in blocks if ".ChatActivity}" in block.splitlines()[0]]
    base = [block for block in candidates if "ty=BASE_APPLICATION" in block]
    for block in base or candidates:
        named = re.search(r"\bfl=([A-Z_][A-Z0-9_ ]*)", block)
        if named:
            return "KEEP_SCREEN_ON" in named.group(1).split()
        hexadecimal = re.search(r"\bfl=#?([0-9a-fA-F]+)", block)
        if hexadecimal:
            return bool(int(hexadecimal.group(1), 16) & 0x80)
    return None


def clear_field(serial, length):
    # Ctrl+A then Delete; fall back to one Delete per character when key combinations are unsupported.
    adb(serial, "shell", "input", "keycombination", "113", "29", check=False)
    adb(serial, "shell", "input", "keyevent", "67")
    adb(serial, "shell", "input", "keyevent", "123")
    for _ in range(length):
        adb(serial, "shell", "input", "keyevent", "67")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--prompt", required=True, help="ASCII prompt to send, in capitals on a pinyin device")
    parser.add_argument("--locale", default="zh", help="values-* qualifier of the app UI language (zh, en, ja, ...)")
    parser.add_argument("--model", help="switch the new conversation to the switcher entry containing this text")
    parser.add_argument("--timeout", type=int, default=420, help="seconds to wait for the reply to end")
    parser.add_argument("--keep-conversation", action="store_true", help="append to the open conversation")
    parser.add_argument("--expect", choices=["complete", "truncated", "failed"], help="fail unless the reply ended this way")
    parser.add_argument(
        "--check-keep-screen-on",
        action="store_true",
        help="fail unless FLAG_KEEP_SCREEN_ON is held while generating and released once the reply has ended",
    )
    args = parser.parse_args()
    words = load_strings(args.locale)
    usage = usage_pattern(words["chat_usage_format"])
    detail_prefix = words["chat_generation_failure_detail"].replace("%1$s", "")
    serial = args.serial

    version = adb(serial, "shell", "dumpsys", "package", PACKAGE)
    version_line = next((line.strip() for line in version.splitlines() if "versionName=" in line), "unknown")
    code_line = next((line.strip() for line in version.splitlines() if "versionCode=" in line), "")
    print(f"installed: {version_line} {code_line.split(' ')[0]}")

    adb(serial, "shell", "am", "start", "-n", ACTIVITY)
    time.sleep(2.5)
    root = dump(serial)
    if not args.keep_conversation:
        button = find(root, **{"content-desc": words["chat_new_conversation"]})
        if button is None:
            print("new conversation: button not found, appending to the open conversation")
        else:
            tap(serial, button)
            time.sleep(1.5)
            root = dump(serial)
            print("new conversation: opened")
    if args.model:
        switcher = find(root, **{"content-desc": words["chat_open_model_switcher"]})
        if switcher is None:
            print("FAIL: model switcher not found")
            return 2
        tap(serial, switcher)
        time.sleep(1.5)
        entry = find_text_containing(dump(serial), args.model, class_suffix="CheckedTextView")
        if entry is None:
            adb(serial, "shell", "input", "keyevent", "4")
            print(f"FAIL: no switcher entry contains {args.model}")
            return 2
        tap(serial, entry)
        time.sleep(2.0)
        root = dump(serial)
    target = target_line(root)
    print(f"target: {escaped(target) if target else 'not shown'}")
    if args.model and args.model not in target:
        print("FAIL: target did not switch")
        return 2

    field = find(root, **{"class": "android.widget.EditText"})
    if field is None:
        print("FAIL: chat input not found")
        return 2
    if not args.prompt.isascii():
        print("FAIL: prompt must be ASCII")
        return 2
    tap(serial, field)
    time.sleep(0.5)
    adb(serial, "shell", "input", "text", args.prompt.replace(" ", "%s"))
    time.sleep(1.0)
    root = dump(serial)
    field = find(root, **{"class": "android.widget.EditText"})
    typed = (field.get("text") if field is not None else "") or ""
    if typed != args.prompt:
        print(f"FAIL: IME changed the prompt, nothing sent: {escaped(typed[:80])}")
        clear_field(serial, len(typed) + 8)
        return 3
    send = find(root, **{"content-desc": words["chat_send"]})
    if send is None:
        print("FAIL: send button not found")
        return 2
    tap(serial, send)
    started = time.time()
    print("sent: waiting for the reply to end")
    held_while_generating = None
    if args.check_keep_screen_on:
        time.sleep(0.5)
        held_while_generating = keep_screen_on(serial)

    outcome = None
    meta = ""
    tail = ""
    counts = ""
    detail = None
    while time.time() - started < args.timeout:
        time.sleep(3)
        root = dump(serial)
        visible = texts(root)
        for index, text in enumerate(visible):
            match = usage.search(text)
            if words["chat_generation_truncated"] in text:
                outcome = "truncated"
            elif text == words["chat_generation_stopped"]:
                outcome = "stopped"
            elif text == words["chat_generation_failure_choose_target"]:
                outcome = "failed"
            elif match:
                outcome = "complete"
            if outcome:
                meta = text
                if match:
                    counts = f"input={match.group('input')} output={match.group('output')}"
                body = [value for value in visible[:index] if value != args.prompt]
                tail = body[-1][-120:] if body else ""
                if outcome == "failed" and detail_prefix:
                    for value in body:
                        position = value.find(detail_prefix)
                        if position >= 0:
                            detail = value[position + len(detail_prefix):].split("\n")[0][:200]
                break
        if outcome:
            break
    elapsed = time.time() - started
    if outcome is None:
        print(f"FAIL: no terminal meta line within {args.timeout} s")
        return 1
    print(f"outcome: {outcome} after {elapsed:.0f} s {counts}".rstrip())
    print(f"meta: {escaped(meta)}")
    print(f"reply tail: {escaped(tail)}")
    if outcome == "failed":
        print(f"failure detail: {escaped(detail) if detail else 'not shown'}")
    failed = False
    if args.check_keep_screen_on:
        time.sleep(1.0)
        released = keep_screen_on(serial)
        print(f"keep screen on: while generating={held_while_generating} after={released}")
        if held_while_generating is not True or released is not False:
            print("FAIL: FLAG_KEEP_SCREEN_ON was not held exactly while generating")
            failed = True
    if args.expect and args.expect != outcome:
        print(f"FAIL: expected {args.expect}")
        failed = True
    if failed:
        return 1
    print("PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
