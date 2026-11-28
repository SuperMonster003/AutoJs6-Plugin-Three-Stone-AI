# P6-3b: fixes after the first voice hand test (2026-11-28)

The maintainer's hand test of the voice release candidate (2026-11-28 10:16 - 13:02, XQ-DQ72, debug build vc 385)
found five problems, F1 - F5. This records how each was traced and verified (roadmap 10.5 P6-3b, decisions D222 - D229,
pending items Q94 - Q96). Device: XQ-AT72 (QV710AF65F, Android 12 / API 31), Gemma 4 E2B IT downloaded on the device,
3-Macaw Speech 1.6.1, AutoJs6 6.8.1. XQ-DQ72 (Gemma 4 E4B) was offline; the checks that need it are listed at the end.

## A1: "Resource cache already has an active lease" with a local model (F2)

Cause (read in the code, reproduced in JVM tests): the process keeps one on-device engine and lends it to one session
at a time. When a turn has to rebuild its session (hard watermark, recall change, another conversation), the replaced
session closes on the cancel thread while the new one asks for the engine at once on the backend thread, before the old
lease is returned. A local target with "maximum output tokens" left unlimited (the default) rebuilds every turn (Q95),
so a voice conversation met this race almost every round.

Fixes: D222 (the cache waits for the lease, at most 60 s, then `LOCAL_BUSY` with a readable message), D223 (a local
summary only after the next turn would rebuild anyway and after 30 s of idle chat; it closes the idle chat session
first and yields to a new turn), D228 (the summary schema no longer uses `uniqueItems`, which LiteRT-LM's LLGuidance
rejects with `Unimplemented keys: ["uniqueItems"]`), D229 (a summary is kept when only some of its memory items break
the rules; the rules themselves stay).

| Check | Result |
|---|---|
| `LocalEngineHandoffTest` F2 and D223 cases on an export of HEAD `4824468` (before the fix) | fail: `IllegalStateException: Resource cache already has an active lease`, "the summary got the engine once the idle chat session returned it" |
| Same tests after the fix | pass (3 / 3 with the D227 case) |
| `SpeechConversationRealDeviceTest#localTargetRoundsAcrossASummary` (default settings, fake recognizer, muted reading) | 6 rounds, 6 complete replies, 4 session rebuilds, 1 summary completed after round 3 (2 attempts, 199 s, 1 memory item set aside), 0 failed |

The device case took five runs to get there; the earlier runs are the evidence for D228 and D229:

| Run | Settings | Result |
|---|---|---|
| 1 | budget 800, output 64 | no summary started within 30 rounds |
| 2 | budget 1500, output 64 | 30 rounds complete, no summary started (recall rebuilds kept the context near 500 tokens) |
| 3 | defaults | summary started after round 3, both attempts failed: `Failed to create LLGuidance constraint: Unimplemented keys: ["uniqueItems"]` |
| 4 | defaults, D228 | both attempts generated (about 90 s each), both rejected: "Only a user message may confirm working memory" |
| 5 | defaults, D228, D229 | PASS |

## A2 (b): two copies of the model, one per process (F3)

Measured on XQ-AT72 with E2B before D227: after one local chat turn and then the README voice example run in AutoJs6,
the main process (adj 700, process state 15) and `:provider` (adj 100) each had a PSS of about 1.55 GB. The chat
keeps its idle session (and the engine) while the conversation stays open; a script's engine stays cached for five
minutes. D227: before loading, a process asks the app's other process with an ordered broadcast inside the package to
give back an engine it does not use (at most 4 s); the chat closes an idle local session and cancels a local summary,
a reply in progress keeps its engine.

`.python/device_engine_handover.py --serial QV710AF65F` (drives `SpeechConversationRealDeviceTest#localEngineGivenBackToAScript`
and `device_script_voice_example.py --question ... --no-speak --memory`; no sound, no recording):

| Step | Main PSS | `:provider` PSS | Handover |
|---|---|---|---|
| After a local voice round | 2074 MB | not running | - |
| After the script answered | 240 MB | 1976 MB | main answered `free` in 128 ms |
| After a second voice round | 2096 MB | 166 MB | `:provider` answered `free` in 148 ms (1810 MB freed) |

Both voice replies were complete; the script's answer was correct.

## A3: dark or light jump of the starting window (F5)

D224 hands the resolved night mode to `UiModeManager.setApplicationNightMode` on API 31+; D226 stores the last accepted
AutoJs6 appearance so a cold start's first frame uses it (language, night mode and theme color).

| Check | Result |
|---|---|
| `ApplicationNightModeDeviceTest` with the system dark | 5 / 5 (one case runs only for the splash script) |
| `ApplicationNightModeDeviceTest` with the system light | 5 / 5, including "dark recorded, then follow the system: the screen is light" |
| `HostSnapshotCopyDeviceTest` | 1 / 1 |
| `device_splash_night_mode.py --instrumentation`, system dark + app light, 2 cold starts | 92 / 88 frames, 0 inverted |
| Same, system light + app dark, 2 cold starts | 63 / 62 frames, 0 inverted |

The running process's application configuration keeps the system's night mode after `setApplicationNightMode` until
the next process start (XQ-AT72), so the tests check the recorded value and what a newly opened screen is drawn with,
and the recording checks the starting window. Recipe for the other plugins:
`.claude/boards/notes/3-stone/splash-night-mode-recipe.md` (workspace notes, not in this repository).

## A4 and A5

- "Preparing the microphone" before the session is ready (D225): `SpeechLiveDictationDeviceTest` 7 / 7,
  `SpeechLiveConversationDeviceTest` 13 / 13 (including `theExitButtonEndsARoundThatIsStillPreparing` with a delayed
  fake ready), `MacawSpeechDeviceTest#theFirstRoundAfterAColdStartPreparesUntilTheSessionIsReady` on the real
  3-Macaw Speech after a cold start: PREPARING then LISTENING after 1930 ms.
- Busy microphone in every round (F1): `SpeechLiveConversationDeviceTest#aMicrophoneBusyEveryRoundUsesTheSheetEveryRound`
  (4 sheets, 3 sentences sent). Hand test item 9 keeps its expectation (Q89).

## Still open (XQ-DQ72)

- A2 (b): whether `LOW_MEMORY` (`BINDER_DIED`) of `:provider` with E4B is gone after D227; compare oom adj, process
  state, meminfo and the exit reasons. If it is still the device's memory limit: keep the error code, explain the lack
  of memory, add a README FAQ entry in ten languages.
- A2 (c): the README example five times each with E2B and E4B from a played recording
  (`device_script_voice_example.py --model E2B --model E4B --runs 5 --play <wav> --expected <text> --memory`).
