# P0 Context Accounting Baseline

This record covers Roadmap P0-3. P0 adds observation only. It does not change the existing
32-turn rotation or 24-turn / 192 KiB replay policy.

## Status

- Automated implementation: complete on 2026-08-31.
- JVM regression and debug APK build: passed as recorded below.
- Required online 24+ turn device baseline: complete on 2026-08-31 with 40 successful turns.
- LiteRT-specific incremental-counter sanity coverage remains a follow-up for the P1 device run.

## Instrumentation contract

The launcher chat writes two debug records per successful turn under tag `ThreeStoneAiChat`:

- `Context start`: target, estimated input tokens, UTF-8 input bytes, current accounting value,
  backend epoch, rebuild flag, and coefficient used for the estimate.
- `Context complete`: estimated and actual input tokens, actual output tokens, post-turn context
  accounting, conversation cumulative input, backend epoch, rebuild flag, and coefficient before
  and after calibration.

The visible assistant usage line also includes cumulative input for the conversation whenever
`showGenerationUsage` is enabled. It remains derived from persisted successful-turn usage, so it
survives activity recreation and history reopening.

Counter normalization is intentional:

- Online targets calibrate against the complete replayed request because their reported input
  usage covers the full outbound conversation.
- A rebuilt LiteRT turn calibrates against initial history plus the current prompt.
- A continued LiteRT turn calibrates against only the current prompt because its existing
  `inputTokens` counter excludes retained KV cache.
- `contextTokensAfterTurn` records online `input + output` and the exact LiteRT
  `Conversation.getTokenCount()` value. Estimation is used only when that value is unavailable.

## Log collection

Use a debug build and keep message text out of diagnostic logs. The instrumentation emits only
counts and stable target identity.

```powershell
adb logcat -c
adb logcat -v time ThreeStoneAiChat:D *:S
```

For a saved capture:

```powershell
adb logcat -d -v time -s ThreeStoneAiChat:D > context-accounting-before-p1.log
```

## Pre-P1 device procedure

1. Install the debug APK without clearing application data.
2. Enable generation usage display.
3. Select one online target that reports input and output usage.
4. Start a new conversation and complete at least 40 short, similarly sized turns. This crosses
   both the 24-turn replay floor and the 32-turn backend rotation boundary.
5. Save logcat and copy representative rows into the table below.
6. Confirm that epochs change on backend rebuild and that the pre-P1 input curve grows again from
   the retained 24-turn suffix.
7. For LiteRT-specific changes, repeat a shorter run with a local target. Confirm that
   `accountingTokens` follows the exact full-context counter while calibration uses only the
   incremental prompt after the first turn.

## Baseline data

The run used the following controlled setup:

- Device: Sony G8441, Android 9 / API 28, arm64-v8a.
- Plugin: 1.1.0 arm64 debug APK, installed with `adb install -r` so existing configuration was
  retained.
- Target: online `PoloAPI` profile, model `claude-opus-4-8`, OpenAI-compatible protocol.
- Prompt: `Reply only OK.` repeated for every turn.
- Generation limit: 16 output tokens for the run. The previous Unlimited setting was restored
  afterward.
- Persisted result: 80 messages, 40 complete assistant turns, final usage 950 input / 4 output,
  and 10,198 cumulative input tokens.
- Calibration result: 40 persisted samples and a final coefficient of 0.3979176072 token per
  UTF-8 byte.

Representative `Context complete` rows are below. `Coefficient` shows before and after EMA values.

| Turn | Epoch | Rebuilt | Estimated input | Actual input | Output | Accounting | Cumulative input | Coefficient |
|---:|---:|:---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | yes | 10 | 5 | 1 | 6 | 5 | 0.400000 -> 0.350000 |
| 8 | 1 | no | 87 | 82 | 1 | 83 | 348 | 0.213676 -> 0.205861 |
| 16 | 1 | no | 171 | 170 | 1 | 171 | 1,400 | 0.184889 -> 0.184132 |
| 19 | 1 | no | 204 | 203 | 16 | 219 | 1,976 | 0.183245 -> 0.183020 |
| 23 | 1 | no | 256 | 259 | 1 | 260 | 2,946 | 0.192079 -> 0.193765 |
| 28 | 1 | no | 320 | 324 | 1 | 325 | 4,416 | 0.196979 -> 0.198853 |
| 31 | 1 | no | 356 | 357 | 1 | 358 | 5,454 | 0.201234 -> 0.201929 |
| 32 | 1 | no | 367 | 368 | 1 | 369 | 5,822 | 0.201929 -> 0.202388 |
| 33 | 2 | yes | 289 | 291 | 1 | 292 | 6,113 | 0.202388 -> 0.203577 |
| 34 | 2 | no | 301 | 302 | 1 | 303 | 6,415 | 0.203577 -> 0.204387 |
| 37 | 2 | no | 335 | 335 | 1 | 336 | 7,387 | 0.205192 -> 0.205308 |
| 38 | 2 | no | 347 | 924 | 4 | 928 | 8,311 | 0.205308 -> 0.284246 |
| 39 | 2 | no | 401 | 937 | 4 | 941 | 9,248 | 0.284246 -> 0.347397 |
| 40 | 2 | no | 450 | 950 | 4 | 954 | 10,198 | 0.347397 -> 0.397918 |

### Findings

1. The pre-P1 online request grows linearly. Apart from the turn-19 output-cap event, actual input
   grew by about 11 tokens per successful turn through turn 32.
2. The legacy rotation boundary is exact and visible. Turn 32 completed at epoch 1 with 368 input
   tokens. Turn 33 rebuilt epoch 2 from the retained 24-turn suffix and dropped to 291 input tokens,
   a 20.9% reduction, before linear growth resumed.
3. Calibration converged closely before the provider discontinuity. Across turns 31 through 37,
   representative estimates differed from reported input by at most 2 tokens, with two exact
   matches.
4. Provider usage changed discontinuously at turn 38. Outbound UTF-8 bytes grew only from 520 at
   turn 37 to 536 at turn 38, with no rebuild or target change, while reported input jumped from
   335 to 924 and retained an approximately 589-token offset through turn 40. The client-side
   transcript did not contain a corresponding payload increase. A provider-side accounting,
   cache, or injected-prefix change is plausible, but the capture cannot distinguish those causes.
5. Because the current absolute-ratio EMA intentionally treats provider usage as truth, the last
   three samples moved the coefficient from 0.2053 to 0.3979. P1 must keep capacity accounting and
   provider billing telemetry conceptually separate, and should not let a fixed provider-side
   offset masquerade as a permanent per-byte slope without an outlier policy.

This is the P1 before dataset. `docs/dev/context-budget-smoke.md` will hold the bounded after
dataset and compare its rotation/watermark behavior against these rows.

## Automated verification

```powershell
./gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug
```

- Result: passed on 2026-08-31, with 290 JVM tests and 0 failures.
- Debug artifacts: arm64-v8a, x86_64, and universal APKs were produced successfully.
