#!/usr/bin/env python3
"""List the LiteRT-LM engine caches of the plugin on one device, read only (roadmap P6.0, D146).

Reads, through run-as on a debuggable build, cache/litertlm, the names in files/models and the
file names of files/models/catalog.json, and classifies every cache entry the way the plugin does
(LiteRtLmEngineCachePolicy): the owner is the content-addressed model file the name starts with.

- keep: its model file is still in the catalog or in files/models;
- orphan: its model file is gone, so "Clean unreferenced files", deleting the model or the next
  cold start of the model manager removes it;
- recent: an orphan written within the last 10 minutes, left for a later sweep;
- unrecognized / not a regular file: never touched by the plugin.

Each entry is printed with its kind (CPU cache, GPU program or weight cache, MTP drafter), its
size, its modification time and the model size embedded in its name; file names, digests and
display names are never printed. --expect-no-orphans exits with 1 when an orphan older than the
grace period is left (for the check after an install).

A remote adb server is used through the ADB_SERVER_SOCKET environment variable, for example
ADB_SERVER_SOCKET=tcp:100.107.160.16:5037.
"""

import argparse
from datetime import datetime
import json
import re
import shlex
import subprocess
import sys

from device_speech_check import PACKAGE

CACHE_NAME = re.compile(r"^(model-[0-9a-f]{64}\.litertlm)[._].+$")
EMBEDDED = re.compile(r"^_(\d+)_(\d+)")
GRACE_SECONDS = 10 * 60
KINDS = (
    ("_mldrift_program_cache.bin", "GPU program cache"),
    ("_mldrift_weight_cache.bin", "GPU weight cache"),
    (".xnnpack_cache", "CPU cache"),
    (".mtp_drafter", "MTP drafter"),
)
LISTING = (
    "cd cache/litertlm 2>/dev/null || exit 0; "
    "for f in * .[!.]*; do "
    "if [ -e \"$f\" ] || [ -L \"$f\" ]; then stat -c '%s|%Y|%F|%n' -- \"$f\"; fi; "
    "done"
)


def catalog_file_names(text):
    """File names of every catalog entry; an unreadable catalog keeps everything (None)."""
    try:
        document = json.loads(text)
    except ValueError:
        return None
    entries = document.get("entries") if isinstance(document, dict) else None
    if not isinstance(entries, list):
        return None
    return {entry["fileName"] for entry in entries if isinstance(entry, dict) and isinstance(entry.get("fileName"), str)}


def kind_of(name):
    for suffix, label in KINDS:
        if name.endswith(suffix):
            return label
    return "other cache"


def classify(entries, retained, now_seconds):
    """entries: (size, mtime seconds, type, name); retained: model file names, or None when unknown."""
    result = []
    for size, mtime, file_type, name in entries:
        match = CACHE_NAME.match(name)
        owner = match.group(1) if match else None
        embedded = EMBEDDED.match(name[len(owner):]) if owner else None
        if file_type != "regular file":
            status = "not a regular file"
        elif owner is None:
            status = "unrecognized"
        elif retained is None or owner in retained:
            status = "keep"
        elif abs(now_seconds - mtime) < GRACE_SECONDS:
            status = "recent"
        else:
            status = "orphan"
        result.append({
            "kind": kind_of(name) if owner else "-",
            "size": size,
            "mtime": mtime,
            "status": status,
            "embeddedModelSize": int(embedded.group(2)) if embedded else None,
        })
    return result


def parse_listing(text):
    entries = []
    for line in text.splitlines():
        parts = line.split("|", 3)
        if len(parts) == 4 and parts[0].isdigit() and parts[1].isdigit():
            entries.append((int(parts[0]), int(parts[1]), parts[2], parts[3]))
    return entries


def gigabytes(size):
    return f"{size / 1_000_000_000:.2f} GB"


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--expect-no-orphans", action="store_true", help="exit with 1 when an old orphan is left")
    args = parser.parse_args()

    def run_as(script):
        command = shlex.join(["run-as", PACKAGE, "sh", "-c", script])
        result = subprocess.run([args.adb, "-s", args.serial, "shell", command],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=300)
        if result.returncode != 0:
            detail = (result.stderr or result.stdout).decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"run-as failed: {detail[-200:]}")
        return result.stdout.decode("utf-8", errors="replace")

    now = int(run_as("date +%s").strip())
    model_files = {name for name in run_as("ls files/models 2>/dev/null").split() if name.endswith(".litertlm")}
    catalog = catalog_file_names(run_as("cat files/models/catalog.json 2>/dev/null"))
    retained = None if catalog is None else catalog | model_files
    entries = classify(parse_listing(run_as(LISTING)), retained, now)
    print(f"catalog {'unreadable, everything kept' if catalog is None else f'{len(catalog)} entries'}, "
          f"{len(model_files)} model files, {len(entries)} entries in cache/litertlm")
    for index, entry in enumerate(entries, start=1):
        embedded = entry["embeddedModelSize"]
        when = datetime.fromtimestamp(entry["mtime"]).strftime("%Y-%m-%d %H:%M")
        print(f"#{index} {entry['kind']}, {gigabytes(entry['size'])}, {when}, "
              f"model size in name {embedded if embedded is not None else '-'}: {entry['status']}")
    orphans = [entry for entry in entries if entry["status"] == "orphan"]
    print(f"orphans: {len(orphans)} files, {gigabytes(sum(entry['size'] for entry in orphans))}")
    return 1 if args.expect_no_orphans and orphans else 0


if __name__ == "__main__":
    sys.exit(main())
