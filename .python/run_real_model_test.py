#!/usr/bin/env python3
"""Install debug APKs, stage a local LiteRT-LM model and verify a real inference receipt."""

import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = "io.github.supermonster003.autojs6.plugin.threestoneai"
TEST_CLASS = PACKAGE + ".LiteRtRealModelInferenceInstrumentationTest"
RECEIPT = "litertlm-real-model-inference.json"


def apk_from_metadata(directory, abi=None):
    metadata = json.loads((directory / "output-metadata.json").read_text(encoding="utf-8"))
    elements = metadata["elements"]
    matching = [item for item in elements if any(
        entry.get("filterType") == "ABI" and entry.get("value") == abi
        for entry in item["filters"]
    )] if abi else []
    candidates = matching or [item for item in elements if not item["filters"]]
    if len(candidates) != 1:
        raise ValueError(f"Expected one compatible APK in {directory}")
    apk = directory / candidates[0]["outputFile"]
    if not apk.is_file():
        raise FileNotFoundError(apk)
    return apk


def require_test_pass(output):
    if not re.search(r"^OK \(1 test\)\s*$", output, re.MULTILINE) or re.search(
        r"INSTRUMENTATION_STATUS_CODE: -(?:1|2|3|4)\b|FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed",
        output,
    ):
        raise RuntimeError("Real-model instrumentation did not pass; inspect instrumentation.txt")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True, help="adb device serial")
    parser.add_argument("--model", required=True, type=Path, help="local .litertlm file, retaining its catalog filename")
    parser.add_argument("--download-on-device", action="store_true", help="download a pinned catalog model directly on the device instead of pushing it")
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/real-model")
    args = parser.parse_args()
    if not args.model.is_file() or not re.fullmatch(r"[A-Za-z0-9_.-]+\.litertlm", args.model.name):
        parser.error("--model must be an existing .litertlm file with a safe ASCII filename")
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / RECEIPT).unlink(missing_ok=True)

    def adb(*command, **kwargs):
        try:
            return subprocess.run(
                [args.adb, "-s", args.serial, *map(str, command)], check=True,
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=1800, **kwargs,
            ).stdout
        except subprocess.CalledProcessError as error:
            detail = (error.stderr or error.stdout or b"").decode("utf-8", errors="replace").strip()
            raise RuntimeError(f"adb {command[0]} failed ({error.returncode}): {detail}") from error

    def shell(*command, **kwargs):
        return adb("shell", "-T", shlex.join(command), **kwargs)

    print("Hashing the local model...", flush=True)
    with args.model.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    size = args.model.stat().st_size
    abi = shell("getprop", "ro.product.cpu.abi").decode().strip()
    print(f"Installing debug APKs for {abi}...", flush=True)
    adb("install", "-r", apk_from_metadata(ROOT / "app/build/outputs/apk/debug", abi))
    adb("install", "-r", apk_from_metadata(ROOT / "app/build/outputs/apk/androidTest/debug"))
    target_name = f"model-{digest}.litertlm"
    target = "files/models/" + target_name
    exists = shell("run-as", PACKAGE, "sh", "-c", f"if test -f {target}; then echo present; fi").strip()
    if exists != b"present" and not args.download_on_device:
        staging = "files/import-staging/" + args.model.name
        shell("run-as", PACKAGE, "mkdir", "-p", "files/import-staging")
        print(f"Staging {size} bytes through adb into app-private storage...", flush=True)
        # adb's file-sync transport handles large binary files reliably on Windows. The
        # device-side pipe lets shell read /data/local/tmp while run-as writes private files.
        remote_directory = shell("mktemp", "-d", "/data/local/tmp/threestone-model-XXXXXXXX").decode().strip()
        if not re.fullmatch(r"/data/local/tmp/threestone-model-[A-Za-z0-9]{8}", remote_directory):
            raise RuntimeError("Unexpected staging directory from the device")
        remote_model = remote_directory + "/model.litertlm"
        try:
            adb("push", args.model, remote_model)
            destination_command = shlex.quote(f"cat > {staging}.partial")
            shell("sh", "-c", f"cat {remote_model} | run-as {PACKAGE} sh -c {destination_command}")
        finally:
            try:
                shell("rm", "-f", remote_model)
                shell("rmdir", remote_directory)
            except (RuntimeError, subprocess.SubprocessError) as error:
                # A disconnected device can prevent cleanup; keep the original transfer error.
                print(f"Unable to clean staging directory {remote_directory}: {error}", file=sys.stderr)
        received_size = int(shell("run-as", PACKAGE, "wc", "-c", staging + ".partial").split()[0])
        if received_size != size:
            raise RuntimeError(f"Incomplete staging: expected {size} bytes, received {received_size}")
        shell("run-as", PACKAGE, "mv", staging + ".partial", staging)
    elif exists == b"present":
        print("Reusing the managed model; instrumentation will verify its complete digest.", flush=True)
    else:
        print("The device will download and verify the pinned catalog model directly over HTTPS.", flush=True)

    remote_receipt = f"/sdcard/Android/data/{PACKAGE}/files/{RECEIPT}"
    shell("rm", "-f", remote_receipt)
    print("Running catalog integration, health check and CPU inference...", flush=True)
    output = shell(
        "am", "instrument", "-w", "-r", "-e", "class", TEST_CLASS,
        "-e", "modelFile", args.model.name, "-e", "targetFile", target_name,
        "-e", "modelSha256", digest,
        *(["-e", "downloadModel", "true"] if args.download_on_device else []),
        PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner",
    ).decode("utf-8", errors="replace")
    (args.output / "instrumentation.txt").write_text(output, encoding="utf-8")
    require_test_pass(output)
    receipt = json.loads(shell("cat", remote_receipt))
    if not (receipt.get("ok") is True and receipt.get("modelSha256") == digest
            and receipt.get("modelSizeBytes") == size and receipt.get("generatedText", "").strip()):
        raise RuntimeError("Inference receipt does not match the local model or contains no generated text")
    (args.output / RECEIPT).write_text(json.dumps(receipt, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(receipt, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
