# P0 Context Accounting Baseline

This record covers Roadmap P0-3. P0 adds observation only. It does not change the existing
32-turn rotation or 24-turn / 192 KiB replay policy.

## Status

- Automated implementation: complete on 2026-08-31.
- JVM regression and debug APK build: recorded below after the final verification run.
- Required 24+ turn device baseline: pending. No device/model/credential result is fabricated in
  this document.

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

## Required pre-P1 device procedure

1. Install the debug APK without clearing application data.
2. Enable generation usage display.
3. Select one online target that reports input and output usage.
4. Start a new conversation and complete at least 40 short, similarly sized turns. This crosses
   both the 24-turn replay floor and the 32-turn backend rotation boundary.
5. Save logcat and copy representative rows into the table below.
6. Confirm that epochs change on backend rebuild and that the pre-P1 input curve grows again from
   the retained 24-turn suffix.
7. Repeat a shorter run with a local target. Confirm that `accountingTokens` follows the exact
   full-context counter while calibration uses only the incremental prompt after the first turn.

## Baseline data

Device execution is still required.

| Turn | Target/locality | Epoch | Rebuilt | Estimated input | Actual input | Accounting | Coefficient |
|---:|---|---:|---|---:|---:|---:|---:|
| Pending | Pending | Pending | Pending | Pending | Pending | Pending | Pending |

Record the device model, Android version, plugin version, target/model identity, output-token
setting, and raw log attachment alongside the completed table. P1 uses this capture as its before
dataset; `docs/dev/context-budget-smoke.md` will hold the bounded after dataset.

## Automated verification

```powershell
./gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug
```

- Result: passed on 2026-08-31, with 290 JVM tests and 0 failures.
- Debug artifacts: arm64-v8a, x86_64, and universal APKs were produced successfully.
