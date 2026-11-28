#!/usr/bin/env python3
"""Run the README voice script example in AutoJs6 on a device (roadmap P6-3b A2, 3-stone-voice-1c).

The script is the example of the README "Voice" section and of plugin_instruction (speech.listen -> ai.chat ->
speech.speak) with its two console.log lines replaced by a result file: the heard question, the answer, the error code
and message, and the times of ready, heard, answered and spoken. It runs through AutoJs6's RunIntentActivity with
AutoJs6 brought to the front first, --runs times for each --model (a substring of the local target's model or id, for
example E2B or E4B, which name the two recommended Gemma 4 models by their digest; ai.chat gets that target's id).

--play FILE pushes a recording and plays it with media.playMusic as soon as the listening session is ready (the phone
hears its own speaker; media volume raised to --min-volume percent only while the runs play, then restored). Without
--play nobody speaks and the session ends after 8 s of silence; --question TEXT skips listening and asks TEXT, which
makes no sound except the answer read aloud (--no-speak skips that too), for the memory comparison.

--memory samples, while each run waits for its answer, the PSS of the app's main and :provider processes, their oom
adj and process state (dumpsys meminfo / activity processes), the app's engine handover lines (logcat tag
ThreeStoneAiEngine, D227: whether the other process gave back its idle engine before the provider loaded one) and
afterwards the exit reasons the system recorded for the :provider process (dumpsys activity exit-info, API 30+).

Nothing taps the screen or answers a dialog. Printed per run: the target, the CER of the heard question against
--expected (when given), lengths, the error code and timings; the heard and answered text stay in the local result
file build/verification/script-voice/<serial>/<label>.json, never in logcat.
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
HOST = "org.autojs.autojs6"
RUN_INTENT = f"{HOST}/org.autojs.autojs.external.open.RunIntentActivity"
REMOTE = "/sdcard/Download/3-stone-script-voice"
# Local targets are named by the model digest; the recommended models by their catalog name.
ALIASES = {
    "E2B": "ab7838cdfc8f77e5",
    "E4B": "f335f2bfd1b758dc",
}

SCRIPT = r"""
const OUT = @@OUT@@;
const MODEL = @@MODEL@@;
const PLAY = @@PLAY@@;
const QUESTION = @@QUESTION@@;
const SPEAK = @@SPEAK@@;
const result = { startedAt: Date.now() };
const keep = setInterval(() => {}, 1000);
let finished = false;

function finish(extra) {
    if (finished) return;
    finished = true;
    Object.assign(result, extra || {}, { endedAt: Date.now() });
    files.write(OUT, JSON.stringify(result));
    clearInterval(keep);
}

function failure(error) {
    return { code: error && error.code ? String(error.code) : 'SCRIPT', message: String(error && error.message || error) };
}

function ask(question, target) {
    result.askedAt = Date.now();
    ai.chat(question, { maxTokens: 256, target: target.id })
        .then((response) => {
            result.answer = response.text;
            result.answeredAt = Date.now();
            return SPEAK ? speech.speak(response.text) : null;
        })
        .then(() => finish({ spokenAt: Date.now() }), (error) => finish({ error: failure(error) }));
}

ai.catalog().then((catalog) => {
    const wanted = MODEL.toLowerCase();
    const target = catalog.targets.find((item) => item.available && item.locality === 'local' &&
        (String(item.model) + ' ' + String(item.id)).toLowerCase().indexOf(wanted) >= 0);
    if (!target) {
        const local = catalog.targets.filter((item) => item.locality === 'local').map((item) => String(item.model));
        return finish({ error: { code: 'NO_TARGET', message: 'no available local target matches ' + MODEL + ': ' + local.join(', ') } });
    }
    result.target = String(target.model);
    if (QUESTION) return ask(QUESTION, target);
    let session = speech.listen({ timeoutMillis: 8000 });
    session.on('ready', () => {
        result.readyAt = Date.now();
        if (PLAY) threads.start(() => media.playMusic(PLAY));
    });
    session.on('final', () => session.stop());
    session.on('end', () => {
        let question = session.result();
        result.heard = question;
        result.heardAt = Date.now();
        if (!question) return finish({});
        ask(question, target);
    });
}).catch((error) => finish({ error: failure(error) }));
"""


def cer(reference, hypothesis):
    def normalize(text):
        return [character for character in (text or "").lower() if character.isalnum()]
    reference, hypothesis = normalize(reference), normalize(hypothesis)
    if not reference:
        return None
    previous = list(range(len(hypothesis) + 1))
    for index, expected in enumerate(reference, 1):
        current = [index]
        for position, heard in enumerate(hypothesis, 1):
            current.append(min(previous[position] + 1, current[position - 1] + 1,
                               previous[position - 1] + (expected != heard)))
        previous = current
    return previous[-1] / len(reference)


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--model", action="append", required=True, help="substring of the local target (repeatable)")
    parser.add_argument("--runs", type=int, default=5)
    parser.add_argument("--play", type=Path, help="recording played when the session is ready")
    parser.add_argument("--expected", help="the text of the recording, for the CER")
    parser.add_argument("--question", help="ask this text instead of listening (no recording)")
    parser.add_argument("--no-speak", action="store_true", help="do not read the answer aloud")
    parser.add_argument("--min-volume", type=int, default=70, help="--play: lowest media volume in percent")
    parser.add_argument("--memory", action="store_true", help="sample PSS, oom adj and exit reasons")
    parser.add_argument("--timeout", type=int, default=300, help="seconds to wait for one run")
    parser.add_argument("--label", default="runs")
    parser.add_argument("--output", type=Path, default=ROOT / "build/verification/script-voice")
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

    def launcher():
        listed = shell("cmd", "package", "query-activities", "--brief", "-a", "android.intent.action.MAIN",
                       "-c", "android.intent.category.LAUNCHER", HOST, check=False)
        own = [line.strip() for line in listed.splitlines() if line.strip().startswith(HOST + "/") and "leakcanary" not in line]
        return own[0] if own else f"{HOST}/org.autojs.autojs.ui.splash.SplashActivity"

    def volume():
        text = shell("cmd", "media_session", "volume", "--stream", "3", "--get", check=False)
        level = re.search(r"volume is (\d+) in range \[(\d+)\.\.(\d+)\]", text)
        return (int(level.group(1)), int(level.group(3))) if level else None

    def processes():
        """PSS in MB and oom adj / process state of the main and :provider processes."""
        state = {}
        listed = shell("dumpsys", "activity", "processes", check=False)
        for name in (PACKAGE, PACKAGE + ":provider"):
            block = re.search(r"\*APP\* UID \d+ ProcessRecord\{\S+ (\d+):" + re.escape(name) + r"/\S+\}(.*?)(?=\n\s*\*APP\*|\Z)",
                              listed, re.S)
            if not block:
                continue
            pid = block.group(1)
            adj = re.search(r"oom adj: max=\S+ curRaw=(-?\d+) setRaw=(-?\d+) cur=(-?\d+) set=(-?\d+)", block.group(2))
            procstate = re.search(r"curProcState=(\d+)", block.group(2))
            meminfo = shell("dumpsys", "meminfo", pid, check=False)
            pss = re.search(r"TOTAL PSS:\s+(\d+)", meminfo) or re.search(r"^\s+TOTAL\s+(\d+)", meminfo, re.M)
            state["main" if name == PACKAGE else "provider"] = {
                "pid": int(pid),
                "pssMb": round(int(pss.group(1)) / 1024) if pss else None,
                "adj": int(adj.group(3)) if adj else None,
                "procState": int(procstate.group(1)) if procstate else None,
            }
        available = re.search(r"MemAvailable:\s+(\d+) kB", shell("cat", "/proc/meminfo", check=False))
        state["memAvailableMb"] = round(int(available.group(1)) / 1024) if available else None
        return state

    print(f"device {args.serial} model={shell('getprop', 'ro.product.model').strip()} "
          f"api={shell('getprop', 'ro.build.version.sdk').strip()}")
    shell("mkdir", "-p", REMOTE)
    play_remote = ""
    if args.play:
        play_remote = f"{REMOTE}/{args.play.name}"
        adb("push", args.play, play_remote)
    restore_volume = None
    if args.play:
        current = volume()
        if current and current[0] < current[1] * args.min_volume / 100:
            restore_volume = current[0]
            raised = -(-current[1] * args.min_volume // 100)
            shell("cmd", "media_session", "volume", "--stream", "3", "--set", str(raised), check=False)
            print(f"  media volume {current[0]}/{current[1]} -> {raised}/{current[1]} while the runs play")
    exits_before = shell("dumpsys", "activity", "exit-info", PACKAGE, check=False) if args.memory else ""
    results = []
    try:
        for model in args.model:
            for run in range(1, args.runs + 1):
                out = f"{REMOTE}/result-{model}-{run}.json"
                shell("rm", "-f", out, check=False)
                body = SCRIPT
                wanted = ALIASES.get(model.upper(), model)
                for key, value in {"OUT": out, "MODEL": wanted, "PLAY": play_remote, "QUESTION": args.question or ""}.items():
                    body = body.replace(f"@@{key}@@", json.dumps(value))
                body = body.replace("@@SPEAK@@", "false" if args.no_speak else "true")
                local = output / "voice-example.js"
                local.write_text(body, encoding="utf-8", newline="\n")
                adb("push", local, f"{REMOTE}/voice-example.js")
                local.unlink()
                shell("input", "keyevent", "KEYCODE_WAKEUP", check=False)
                since = shell("date", "+%m-%d %H:%M:%S.000", check=False).strip()
                shell("am", "start", "-n", launcher(), check=False)
                time.sleep(3)
                shell("am", "start", "-n", RUN_INTENT, "-e", "path", f"{REMOTE}/voice-example.js", check=False)
                deadline = time.time() + args.timeout
                samples = []
                entry = None
                while time.time() < deadline:
                    raw = shell("cat", out, check=False)
                    if raw.startswith("{"):
                        entry = json.loads(raw)
                        break
                    if args.memory:
                        samples.append(processes())
                    time.sleep(2)
                entry = entry or {"error": {"code": "TIMEOUT", "message": f"no result within {args.timeout} s"}}
                entry.update({"model": model, "run": run})
                if samples:
                    entry["peakProviderPssMb"] = max((s.get("provider", {}).get("pssMb") or 0) for s in samples)
                    entry["peakMainPssMb"] = max((s.get("main", {}).get("pssMb") or 0) for s in samples)
                    entry["minMemAvailableMb"] = min((s.get("memAvailableMb") or 0) for s in samples)
                    entry["firstMainPssMb"] = samples[0].get("main", {}).get("pssMb")
                    entry["lastMainPssMb"] = samples[-1].get("main", {}).get("pssMb")
                    entry["samples"] = samples
                if args.memory and since:
                    lines = adb("logcat", "-d", "-v", "time", "-T", since, "-s", "ThreeStoneAiEngine:D", check=False)
                    entry["engineHandover"] = [line.split(": ", 1)[-1].strip() for line in lines.splitlines()
                                               if "Engine release" in line][:10]
                results.append(entry)
                error = entry.get("error") or {}
                heard_cer = cer(args.expected, entry.get("heard")) if args.expected and not args.question else None
                timing = ""
                if entry.get("answeredAt") and entry.get("askedAt"):
                    timing = f" answerMillis={entry['answeredAt'] - entry['askedAt']}"
                print(f"{'FAIL' if error else 'PASS'} {model} run {run}: target={entry.get('target')} "
                      f"heard={len(entry.get('heard') or '')}" + (f" cer={heard_cer:.3f}" if heard_cer is not None else "") +
                      f" answer={len(entry.get('answer') or '')}{timing}" +
                      (f" error={error.get('code')}" if error else "") +
                      (f" peakProviderPssMb={entry['peakProviderPssMb']} peakMainPssMb={entry['peakMainPssMb']} "
                       f"mainPssMb={entry['firstMainPssMb']}->{entry['lastMainPssMb']} "
                       f"minMemAvailableMb={entry['minMemAvailableMb']}" if samples else ""), flush=True)
                for line in entry.get("engineHandover") or []:
                    print(f"    {line}", flush=True)
                time.sleep(2)
    finally:
        if restore_volume is not None:
            shell("cmd", "media_session", "volume", "--stream", "3", "--set", str(restore_volume), check=False)
            print(f"  media volume restored to {restore_volume}")
        shell("rm", "-rf", REMOTE, check=False)
    report = {"results": results}
    if args.memory:
        after = shell("dumpsys", "activity", "exit-info", PACKAGE, check=False)
        report["providerExitsAdded"] = [line.strip() for line in after.splitlines()
                                        if "reason=" in line and line not in exits_before][:20]
    (output / f"{args.label}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"  results -> {output / (args.label + '.json')}")
    return 0 if all(not entry.get("error") for entry in results) else 1


if __name__ == "__main__":
    sys.exit(main())
