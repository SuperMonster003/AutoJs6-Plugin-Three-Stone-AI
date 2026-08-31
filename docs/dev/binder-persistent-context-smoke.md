# P3 Binder Persistent Context Smoke

This record closes Roadmap P3. It verifies that AutoJs6 persistent provider sessions keep their
host-visible protocol stable while the plugin bounds, compacts, and rebuilds backend context for
both online and LiteRT targets.

## Status

- P3 implementation: complete on 2026-08-31.
- Online persistent session: 100/100 successful turns with two transparent rebuilds.
- LiteRT persistent session: 100/100 successful turns with two in-place Conversation rebuilds on
  one initialized Engine lease.
- No formal-run context overflow, session interruption, failed completion, non-`stop` finish, or
  target change occurred.
- Full JVM regression: 66 suites and 336 tests, with no failures, errors, or skipped tests.
- Test-only device state was restored: brightness 48, USB stay-awake disabled, 256 KiB logcat
  buffers, 16K chat input budget, and Unlimited chat output.

## Implementation under test

Persistent Binder sessions now use the same budget and accounting primitives as launcher chat,
without persisting a second copy of script-owned history:

- `RemoteThreeStoneAiSession` keeps original SYSTEM messages, valid anchored initial history, and
  only successful user/assistant turns. Failed, cancelled, timed-out, output-limited, or invalid
  completions never enter the transcript.
- The transcript has an independent 512 KiB memory guard. Crossing it step-evicts the oldest
  complete raw turns toward 45% and forces the next backend rebuild. Binder compaction never makes
  a hidden LLM request.
- Exact backend context accounting is preferred. Estimation, calibrated per target, is only the
  fallback and preflight projection. Accounting preserves an exact high-water mark when an online
  provider reports a lower non-monotonic value on a later continuation.
- Rebuild input retains original SYSTEM messages and a newest complete-turn suffix. Online targets
  replace the backend session; LiteRT retains the initialized Engine and replaces the completed
  `Conversation` in place. The outer Binder session ID, fixed configuration, chunks, usage event,
  and completion order do not change.
- An irreducible request above the absolute token or byte protection fails closed through the
  existing `AiErrorCode.INVALID_REQUEST`; no host API or error-code extension was introduced.
- LiteRT uses a single 4,096-token capacity constant for `EngineConfig.maxNumTokens`, model health
  checks, and `AiTargetLimits.maximumContextTokens`. This prevents the policy budget from exceeding
  the native KV cache.

## Device and controlled setup

- Device: Sony G8441, Android 9 / API 28, arm64-v8a.
- Host: AutoJs6 6.8.0, version code 5277.
- Plugin: 1.1.0, version code 55, arm64 debug APK installed with `adb install -r`.
- Online target: PoloAPI profile `profile:5d99a40b-0ea5-4479-85d4-d34e3c38c1b7`, model
  `claude-opus-4-8`, OpenAI-compatible protocol.
- Local target: `local:litertlm.ab7838cdfc8f77e54d8ca45eadceb204`, Gemma E2B LiteRT model, CPU
  backend, native target capacity 4,096 tokens.
- Harness: one `ai.session` first turn followed by 99 sequential `generateNext` calls. Every sample
  checked the same target, a non-empty response, usage presence, and `stop` finish reason.
- Generation: maximum output 16 tokens. Prompts requested an `OK` prefix and contained deterministic
  filler so that transcript growth and rebuild boundaries were reproducible.

The online run used 160 filler characters per turn. The final local acceptance run used 16 filler
characters because the 2017 device needs roughly 35-40 seconds for later CPU continuations. The
host MainActivity remained resumed during the local run so Android 9 did not classify the script
and provider as empty processes. Several `SIGSTOP`/`SIGCONT` cooling pauses kept the device near
44-47 degrees Celsius; the same provider process and Binder session remained alive throughout.
Those pauses are included in elapsed time, so this is a correctness run rather than a latency
benchmark.

## Formal 100-turn result

| Observation | Online | LiteRT local |
|---|---:|---:|
| Requested/completed turns | 100/100 | 100/100 |
| Result status | `ok` | `ok` |
| Input-token sum | 766,506 | 7,088 |
| Input-token minimum / maximum | 480 / 13,032 | 33 / 1,836 |
| Output-token sum | 196 | 1,278 |
| `finishReason=stop` | 100 | 100 |
| Response starts with `OK` | 100 | 100 |
| Response is exactly `OK` | 100 | 13 |
| Transparent rebuilds | 2 | 2 |
| Elapsed time | 1,664,707 ms | 4,196,851 ms, including cooling pauses |

LiteRT continuation `inputTokens` are incremental, while rebuilt-turn input includes the complete
prefill. The policy itself uses `Conversation.getTokenCount()` through
`contextTokensAfterTurn`, so the 7,088 sum is transport telemetry rather than the final KV-cache
size. Online input is the provider's full request-context count and is therefore expected to be
much larger when summed across 100 turns.

The local model obeyed the requested `OK` prefix on every turn. Beginning around turn 53 it often
continued with short sampled characters until the 16-token output cap, which explains 13 exact
matches and 100 prefix matches. All such turns were non-empty, reported usage, ended with `stop`,
and remained in the same session; this is model output behavior, not a transport or context
failure.

## Online rebuild boundaries

The effective online hard watermark was 13,107 tokens and the 45% compaction target was 7,372.
The first rebuild was the 64-completed-turn leak guard; the second was projected before the next
prompt could cross the hard watermark.

| Rebuilt turn | Previous provider input | Compile estimate | Retained / evicted turns | Rebuilt provider input | Result |
|---:|---:|---:|---:|---:|---|
| 65 | 12,054 at turn 64 | 7,359 | 38 / 26 | 7,562 | Continued normally |
| 92 | 13,032 at turn 91 | 7,227 | 35 / 30 | 7,275 | Continued normally |

Turn 100 reported 8,851 input and 1 output token, still below the 13,107 hard watermark. Across
adjacent samples, including the two expected rebuild drops, the provider returned 14
lower-than-previous input values. Within each backend epoch, exact high-water accounting prevented
an anomalous decrease from erasing a previously observed boundary, while ordinary usage events
were forwarded unchanged to the script. All 100 response texts were exactly `OK`.

## LiteRT rebuild boundaries

The 4,096-token runtime contract, 16-token output reservation, and 8% safety margin produced an
effective input budget of 3,752 tokens: hard watermark 3,001, absolute protection 3,376, and
compaction target 1,688.

| Rebuilt turn | Previous exact KV accounting | Compile estimate | Retained / evicted turns | Rebuilt input / output | Post-turn accounting |
|---:|---:|---:|---:|---:|---:|
| 64 | 2,976 at turn 63 | 1,686 | 33 / 30 | 1,836 / 16 | 1,852 |
| 87 | 3,011 at turn 86 | 1,666 | 30 / 26 | 1,686 / 16 | 1,702 |

Both rebuilds closed only the old completed `Conversation`; the initialized Engine lease and
provider process remained the same. Turn 100 completed with exact KV accounting 2,379, below the
3,001 hard watermark. The formal provider log contained no `FAILED_PRECONDITION`, task
cancellation, or fatal exception for the session process.

## Stress and defect-directed probes

Real-device probes found five boundary defects before the formal acceptance run. Each was fixed
and covered by a regression test rather than waived:

| Probe observation | Correction |
|---|---|
| Concurrent service shutdown could skip sessions while draining the registry | Snapshot and clear the registry before closing sessions |
| Rebuild estimation drifted from exact provider usage | Calibrate retained history from exact input while keeping conservative fallback limits |
| Online usage sometimes decreased between continuations | Preserve backend accounting high water for rotation decisions |
| A local run reached native context 3,841 and failed with only 255 state entries remaining | Bind both LiteRT Engine and target policy to the 4,096-token runtime capacity |
| Closing and reacquiring a local backend let delayed native cancellation poison a later turn | Rebuild the LiteRT Conversation in place and cancel only an active generation |

A higher-density local stress run used 160 filler characters and completed 55/55 observed turns,
with every response exactly `OK`. It completed three in-place rebuilds at turns 23, 34, and 45;
their rebuilt inputs were 1,668, 1,650, and 1,643 tokens. A fourth rebuild was about to begin after
turn 55 when Android 9's empty-process reaper killed the finished `RunIntentActivity` host process
and then the provider after roughly 30 minutes at `oom_adj=904`. Logcat recorded an OS empty-process
kill rather than an application crash. This diagnostic run is not counted as the required formal
100-turn result; keeping MainActivity resumed removed that harness condition for the successful
100-turn local run.

A separate 4,000-character calibration probe completed 10/10 online turns. It reached 13,553 input
tokens at turn 7, rebuilt turn 8 with a 5,818-token estimate after evicting five turns, and reported
6,121 rebuilt input tokens. This verified that a single high-density prefix is step-compacted
without spending a hidden summarization call.

## Automated verification

The complete JVM regression and all debug APK variants were built after the final LiteRT in-place
rebuild change:

```powershell
./gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug
```

- Result: `BUILD SUCCESSFUL` on 2026-08-31.
- JVM result: 66 suites, 336 tests, 0 failures, 0 errors, and 0 skipped.
- Debug artifacts: arm64-v8a, x86_64, and universal APKs were produced successfully.
- `PersistentSessionContextTest` covers success/failure/cancellation transcript boundaries, the
  512 KiB guard, online replacement, LiteRT in-place rebuild, unchanged host event order, and the
  absolute-limit `INVALID_REQUEST` path.
- `PluginInstructionCompatibilityTest` verifies the synchronized contract in all eleven localized
  instruction resources.

## Device cleanup

After collecting the final accounting logs, both test packages were force-stopped. Device settings
were read back as brightness 48, `stay_on_while_plugged_in=0`, and 256 KiB main/system/crash logcat
buffers. Plugin preferences still contained `context-token-budget=16384` and
`maximum-output-tokens-limited=false`; no formal smoke setting was left active.
