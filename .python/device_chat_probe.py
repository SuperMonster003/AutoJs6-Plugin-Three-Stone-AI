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

Roadmap D77 (2026-11-05, 1.6.0 P1): --rotate-during turns the screen to landscape and back while
the reply streams (accelerometer_rotation 0, then user_rotation 1 and the original rotation, both
settings restored afterwards); --night-during flips the system night mode with `cmd uimode night`
and restores it. Either one checks that the reply is still generating after each recreation and
that the chat log (tag ThreeStoneAiChat) shows no cancel. --stop-after taps the chat's own Stop
button that many seconds after Send and checks the log: "Generation cancel complete reason=stopped"
within 1000 ms and httpCall=cancelled for an online target. Log checks print only the matched
fields, never prompts, replies or credentials. uiautomator cannot dump a chat that is streaming (no
idle state, the dump fails after about 11 s), so "still generating" is read from the window's
FLAG_KEEP_SCREEN_ON, Stop is tapped where Send was (the keyboard is closed first so the composer
does not move), and every dump removes the previous file so a failed dump is never read as current.

Roadmap P2.4 / P2.6 (2026-11-05, 1.6.0): the switcher is a bottom sheet, so --model taps the first
clickable row containing the text (group headers are not clickable). --effort opens the sheet again,
taps that effort choice for the conversation, closes the sheet and checks the target bar badge; after
the reply it reads "Generation start ... effort=<level> fast=<bool>" from the chat log (the level the
adapters send after clamping) and compares the usage line with the logged output tokens.

Roadmap P3.3 (2026-11-05, 1.6.0): --expect-reasoning shown / absent looks for the folded thinking
card header ("Thought for N s", D94) of the finished reply and prints only whether it is shown and
the seconds; the thinking text itself is never printed. Use a prompt with a short answer so the card
stays on screen.
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
    "chat_reasoning_duration",
)
EFFORT_IDS = ("default", "low", "medium", "high", "xhigh", "max")
EFFORT_NAMES = tuple("effort_" + effort for effort in EFFORT_IDS)
LOG_TAG = "ThreeStoneAiChat"
SETTLE_SECONDS = 2.5


def load_strings(locale):
    directory = "values" if locale in ("", "en") else f"values-{locale}"
    wanted = STRING_NAMES + EFFORT_NAMES
    found = {}
    folder = ROOT / "app" / "src" / "main" / "res" / directory
    for path in sorted(folder.glob("strings*.xml")):
        root = ET.parse(path).getroot()
        for node in root.iter("string"):
            name = node.get("name")
            if name in wanted:
                text = node.text or ""
                found[name] = text.replace('\\"', '"').replace("\\'", "'").replace("\\n", "\n")
    missing = [name for name in wanted if name not in found]
    if missing:
        raise SystemExit(f"strings missing in {folder}: {', '.join(missing)}")
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
    # Remove the previous dump first: a failed dump ("could not get idle state" while a reply
    # streams) leaves the old file, which would be read back as the current screen.
    adb(serial, "shell", "rm", "-f", "/sdcard/three_stone_probe.xml", check=False)
    adb(serial, "shell", "uiautomator", "dump", "/sdcard/three_stone_probe.xml", check=False)
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


def find_clickable_containing(root, fragment):
    """The first clickable node with a descendant whose text contains [fragment]: a switcher row,
    never its group header (headers are not clickable)."""
    for node in nodes(root):
        if node.get("clickable") != "true":
            continue
        if any(fragment in (child.get("text") or "") for child in node.iter("node")):
            return node
    return None


def close_sheet(serial):
    """BACK closes the keyboard first when the search field opened it, then the bottom sheet."""
    if ime_shown(serial):
        adb(serial, "shell", "input", "keyevent", "4")
        time.sleep(0.8)
    adb(serial, "shell", "input", "keyevent", "4")
    time.sleep(1.5)


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


def ime_shown(serial):
    return "mInputShown=true" in adb(serial, "shell", "dumpsys", "input_method", check=False)


def clear_field(serial, length):
    # Ctrl+A then Delete; fall back to one Delete per character when key combinations are unsupported.
    adb(serial, "shell", "input", "keycombination", "113", "29", check=False)
    adb(serial, "shell", "input", "keyevent", "67")
    adb(serial, "shell", "input", "keyevent", "123")
    for _ in range(length):
        adb(serial, "shell", "input", "keyevent", "67")


def generating(serial):
    """Whether the chat is generating, read from FLAG_KEEP_SCREEN_ON (held exactly while a reply is
    generating). uiautomator cannot dump a streaming chat (it never reaches an idle state), so the
    Stop button itself is not looked up."""
    return keep_screen_on(serial) is True


def device_time(serial):
    """Device clock in the format `logcat -T` accepts, so later reads skip older lines."""
    # One shell word: adb joins arguments with spaces, which would split the format.
    return adb(serial, "shell", "date '+%m-%d %H:%M:%S.000'").strip()


def chat_log(serial, since):
    output = adb(serial, "logcat", "-d", "-v", "brief", "-T", since, "-s", f"{LOG_TAG}:D", check=False)
    return [line for line in output.splitlines() if LOG_TAG in line]


def recreate_events(serial, words, args):
    """Recreates the chat while it streams; returns (event, still generating, time checked) triples."""
    events = []
    if args.rotate_during:
        accelerometer = adb(serial, "shell", "settings", "get", "system", "accelerometer_rotation").strip()
        rotation = adb(serial, "shell", "settings", "get", "system", "user_rotation").strip()
        original = rotation if rotation.isdigit() else "0"
        landscape = "0" if original in ("1", "3") else "1"
        try:
            adb(serial, "shell", "settings", "put", "system", "accelerometer_rotation", "0")
            adb(serial, "shell", "settings", "put", "system", "user_rotation", landscape)
            time.sleep(SETTLE_SECONDS)
            events.append((f"rotate to {landscape}", generating(serial), time.time()))
            adb(serial, "shell", "settings", "put", "system", "user_rotation", original)
            time.sleep(SETTLE_SECONDS)
            events.append((f"rotate back to {original}", generating(serial), time.time()))
        finally:
            adb(serial, "shell", "settings", "put", "system", "user_rotation", original, check=False)
            if accelerometer.isdigit():
                adb(serial, "shell", "settings", "put", "system", "accelerometer_rotation", accelerometer, check=False)
    if args.night_during:
        current = adb(serial, "shell", "cmd", "uimode", "night").strip().split(":")[-1].strip()
        original = current if current in ("yes", "no", "auto") else "no"
        flipped = "no" if original == "yes" else "yes"
        try:
            adb(serial, "shell", "cmd", "uimode", "night", flipped)
            time.sleep(SETTLE_SECONDS)
            events.append((f"night mode {flipped}", generating(serial), time.time()))
        finally:
            adb(serial, "shell", "cmd", "uimode", "night", original, check=False)
        time.sleep(SETTLE_SECONDS)
        events.append((f"night mode back to {original}", generating(serial), time.time()))
    return events


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--prompt", required=True, help="ASCII prompt to send, in capitals on a pinyin device")
    parser.add_argument("--locale", default="zh", help="values-* qualifier of the app UI language (zh, en, ja, ...)")
    parser.add_argument("--model", help="switch the new conversation to the switcher entry containing this text")
    parser.add_argument("--timeout", type=int, default=420, help="seconds to wait for the reply to end")
    parser.add_argument("--keep-conversation", action="store_true", help="append to the open conversation")
    parser.add_argument(
        "--expect",
        choices=["complete", "truncated", "failed", "stopped"],
        help="fail unless the reply ended this way",
    )
    parser.add_argument("--rotate-during", action="store_true", help="rotate to landscape and back while the reply streams")
    parser.add_argument("--night-during", action="store_true", help="flip the system night mode and back while the reply streams")
    parser.add_argument(
        "--disturb-after",
        type=float,
        default=4.0,
        help="seconds after Send before --rotate-during / --night-during start",
    )
    parser.add_argument("--stop-after", type=float, help="tap Stop this many seconds after Send and check the cancel log")
    parser.add_argument(
        "--effort",
        choices=EFFORT_IDS,
        help="pick this effort for the conversation in the switcher (a level the model supports) and check the logged request",
    )
    parser.add_argument(
        "--expect-reasoning",
        choices=["shown", "absent"],
        help="fail unless the finished reply shows (or does not show) the folded thinking card",
    )
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
        sheet = dump(serial)
        entry = find_clickable_containing(sheet, args.model)
        search = find(sheet, **{"class": "android.widget.EditText"})
        if entry is None and search is not None and args.model.isascii():
            # The row may lie below the fold: filter the list through the sheet's search field.
            tap(serial, search)
            time.sleep(0.5)
            adb(serial, "shell", "input", "text", args.model.replace(" ", "%s"))
            time.sleep(1.0)
            entry = find_clickable_containing(dump(serial), args.model)
        if entry is None:
            close_sheet(serial)
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
    if args.effort:
        switcher = find(root, **{"content-desc": words["chat_open_model_switcher"]})
        if switcher is None:
            print("FAIL: model switcher not found")
            return 2
        tap(serial, switcher)
        time.sleep(1.5)
        label = words["effort_" + args.effort]
        pill = find(dump(serial), text=label, clickable="true")
        if pill is None:
            close_sheet(serial)
            print(f"FAIL: no effort choice {args.effort} for this target")
            return 2
        tap(serial, pill)
        time.sleep(1.0)
        close_sheet(serial)
        root = dump(serial)
        badge = find(root, text=label)
        print(f"effort: chose {args.effort}, target bar badge shown={badge is not None}")
        if (badge is not None) != (args.effort != "default"):
            print("FAIL: the target bar badge does not match the chosen effort")
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
    if args.stop_after is not None and ime_shown(serial):
        # The keyboard closes after Send and the composer moves down; close it first so the
        # Send position, where Stop appears, stays valid. BACK only when the keyboard is shown.
        adb(serial, "shell", "input", "keyevent", "4")
        time.sleep(1.0)
        root = dump(serial)
    send = find(root, **{"content-desc": words["chat_send"]})
    if send is None:
        print("FAIL: send button not found")
        return 2
    since = device_time(serial)
    tap(serial, send)
    started = time.time()
    print("sent: waiting for the reply to end")
    held_while_generating = None
    if args.check_keep_screen_on:
        time.sleep(0.5)
        held_while_generating = keep_screen_on(serial)
    events = []
    stop_requested = False
    if args.stop_after is not None:
        time.sleep(max(0.0, args.stop_after - (time.time() - started)))
        if not generating(serial):
            print("FAIL: not generating, the reply ended before --stop-after")
            return 1
        # Send turns into Stop in place while generating.
        tap(serial, send)
        stop_requested = True
        print(f"stop: tapped {time.time() - started:.1f} s after Send")
    elif args.rotate_during or args.night_during:
        time.sleep(max(0.0, args.disturb_after - (time.time() - started)))
        if not generating(serial):
            print("FAIL: the reply ended before the screen was recreated, use a longer prompt")
            return 1
        events = recreate_events(serial, words, args)
        for event, active, checked in events:
            print(f"{event}: generating={active} at {checked - started:.1f} s")

    outcome = None
    meta = ""
    tail = ""
    counts = ""
    detail = None
    while time.time() - started < args.timeout:
        time.sleep(3)
        try:
            root = dump(serial)
        except (RuntimeError, ET.ParseError):
            continue  # still streaming
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
    if args.expect_reasoning:
        # The folded card header of the last reply ("Thought for N s"); its text is never printed.
        folded = re.compile(re.escape(words["chat_reasoning_duration"]).replace(re.escape("%1$d"), r"(\d+)"))
        seconds = [found.group(1) for found in (folded.fullmatch(value) for value in visible) if found]
        shown = bool(seconds)
        print(f"reasoning card: shown={shown} seconds={seconds[-1] if seconds else '-'}")
        if (args.expect_reasoning == "shown") != shown:
            print(f"FAIL: expected the thinking card {args.expect_reasoning}")
            failed = True
    if args.check_keep_screen_on:
        time.sleep(1.0)
        released = keep_screen_on(serial)
        print(f"keep screen on: while generating={held_while_generating} after={released}")
        if held_while_generating is not True or released is not False:
            print("FAIL: FLAG_KEEP_SCREEN_ON was not held exactly while generating")
            failed = True
    if events or stop_requested:
        time.sleep(1.0)
        log = chat_log(serial, since)
        cancels = [line for line in log if "Generation cancel requested" in line]
        completes = [line for line in log if "Context complete" in line]
        print(f"log: lines={len(log)} cancel requests={len(cancels)} context completions={len(completes)}")
        if events:
            idle = [event for event, active, _ in events if not active]
            if idle:
                # A complete outcome with no cancel logged means the reply simply ended first.
                print(f"FAIL: not generating after: {', '.join(idle)} (use a longer reply if no cancel was logged)")
                failed = True
            if cancels:
                print("FAIL: recreating the screen requested a cancel")
                failed = True
            if outcome != "complete" or len(completes) != 1:
                print("FAIL: expected exactly one completed turn")
                failed = True
            elif counts:
                logged = re.search(r"actualOutputTokens=(\d+)", completes[-1])
                shown = re.search(r"output=([\d,]+)", counts)
                logged_value = logged.group(1) if logged else None
                shown_value = shown.group(1).replace(",", "") if shown else None
                print(f"usage check: shown output={shown_value} logged output={logged_value}")
                if logged_value != shown_value:
                    print("FAIL: the usage line does not match the logged output tokens")
                    failed = True
        if stop_requested:
            done = [line for line in log if "Generation cancel complete reason=stopped " in line]
            fields = re.search(r"elapsedMillis=(\d+) success=(\w+)(?: (httpCall=[\w-]+))?", done[-1]) if done else None
            if fields is None:
                print("FAIL: no cancel completion logged for the stop")
                failed = True
            else:
                elapsed_millis = int(fields.group(1))
                http = fields.group(3) or "httpCall=unreported"
                print(f"cancel: elapsedMillis={elapsed_millis} success={fields.group(2)} {http}")
                if elapsed_millis > 1000 or fields.group(2) != "true":
                    print("FAIL: the backend was not cancelled within 1000 ms")
                    failed = True
                if http not in ("httpCall=cancelled", "httpCall=unreported"):
                    print("FAIL: the HTTP call was not cancelled")
                    failed = True
    if args.effort:
        time.sleep(1.0)
        log = chat_log(serial, since)
        starts = [line for line in log if "Generation start " in line]
        completes = [line for line in log if "Context complete" in line]
        fields = re.search(r"effort=(\w+) fast=(\w+)", starts[-1]) if starts else None
        if fields is None:
            print("FAIL: no generation start logged")
            failed = True
        else:
            print(f"request: effort={fields.group(1)} fast={fields.group(2)} starts={len(starts)}")
            if len(starts) != 1 or fields.group(1) != args.effort:
                print(f"FAIL: expected exactly one turn sent with effort={args.effort}")
                failed = True
        if outcome == "complete" and counts:
            logged = re.search(r"actualOutputTokens=(\d+)", completes[-1]) if completes else None
            shown = re.search(r"output=([\d,]+)", counts)
            logged_value = logged.group(1) if logged else None
            shown_value = shown.group(1).replace(",", "") if shown else None
            print(f"usage check: shown output={shown_value} logged output={logged_value}")
            if logged_value != shown_value:
                print("FAIL: the usage line does not match the logged output tokens")
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
