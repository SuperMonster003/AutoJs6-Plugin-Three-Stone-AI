# P1 Context Budget Smoke

This record closes Roadmap P1-7. It verifies the token-watermark implementation on both the
online and LiteRT launcher-chat paths and compares the online result with the P0 baseline.

## Status

- P1 implementation: complete on 2026-08-31.
- Online long conversation: 40 successful turns, including nine watermark-driven rebuilds after
  the initial session.
- LiteRT long conversation: 40 successful turns, including one watermark-driven, trimmed rebuild.
- Persistence: both conversations decoded as 80 complete messages with 40 usage rows.
- Test-only settings: restored to the original 16K context budget and Unlimited output after the
  run.

## Implementation under test

P1 replaces the launcher chat's 32-turn primary rotation rule and fixed 24-turn replay suffix
with two token watermarks:

- Reuse a target-matching backend while exact or estimated context accounting is below the hard
  watermark. A 64-turn counter remains only as a leak guard.
- Once a completed turn reaches the hard watermark, rebuild before the next turn.
- On rebuild, compile the newest suffix of successful, complete user/assistant pairs to the 45%
  compaction target. Never split a turn. The current prompt remains complete.
- Prefer online `input + output` usage or LiteRT `Conversation.getTokenCount()` as the full-context
  value. Fall back to calibrated estimation only when no exact value exists.
- Keep the 192 KiB rule as a transport guard rather than a history-selection policy.

The stress run used a deliberately small 512-token application budget. Both targets have unknown
model context capacity, so the application budget applies directly: the hard watermark was 409
tokens (80%) and the compaction target was 230 tokens (45%). The production default remains
16,384 tokens.

## Device and controlled setup

- Device: Sony G8441, Android 9 / API 28, arm64-v8a.
- Plugin: 1.1.0 arm64 debug APK installed with `adb install -r`.
- Prompt: `Reply only OK.` on every turn.
- Temporary settings: 512 context tokens, 16 maximum output tokens, generation usage visible.
- Online target: `PoloAPI`, `claude-opus-4-8`, OpenAI-compatible protocol.
- Local target: `Gemma 4 E2B IT`, LiteRT model
  `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`.

The 512-token value was entered through the new Custom budget dialog. The 8K, 16K, 32K, and
Custom choices were also inspected on-device. At the end of the run the settings screen was used
to restore 16K and Unlimited; direct preference inspection then confirmed:

```xml
<int name="context-token-budget" value="16384" />
<boolean name="maximum-output-tokens-limited" value="false" />
```

## Online result

The table selects rotation boundaries and the final stable suffix from the 40-turn capture.
`Accounting` is the exact provider input plus output value used to decide the following turn.
An em dash in `Compile` means the existing backend was reused.

| Turn | Epoch | Rebuilt | Compile | Provider input | Output | Accounting | Result |
|---:|---:|:---:|---|---:|---:|---:|---|
| 1 | 1 | yes | 10 estimated, no history | 5 | 1 | 6 | Initial session |
| 2 | 1 | no | — | 534 | 4 | 538 | Provider usage spike crossed hard watermark |
| 3 | 2 | yes | Full available suffix | 39 | 4 | 43 | Immediate next-turn recovery |
| 5 | 2 | no | — | 573 | 4 | 577 | Provider usage spike |
| 6 | 3 | yes | Full available suffix | 60 | 1 | 61 | Rebuilt before generation |
| 12 | 3 | no | — | 664 | 4 | 668 | Provider usage spike |
| 13 | 4 | yes | Full available suffix | 137 | 1 | 138 | Rebuilt before generation |
| 15 | 4 | no | — | 703 | 4 | 707 | Provider usage spike |
| 16 | 5 | yes | Full available suffix | 716 | 4 | 720 | Rebuild exposed another provider spike |
| 17 | 6 | yes | 222 estimated; 30 messages; trimmed | 208 | 4 | 212 | First true step compaction |
| 21 | 6 | no | — | 781 | 4 | 785 | Provider usage spike |
| 22 | 7 | yes | 223 estimated; 30 messages; trimmed | 184 | 1 | 185 | Returned below 230 target |
| 23 | 7 | no | — | 742 | 4 | 746 | Provider usage spike |
| 24 | 8 | yes | 218 estimated; 28 messages; trimmed | 173 | 1 | 174 | Returned below 230 target |
| 26 | 8 | no | — | 742 | 4 | 746 | Provider usage spike |
| 27 | 9 | yes | 217 estimated; 28 messages; trimmed | 173 | 1 | 174 | Returned below 230 target |
| 28 | 9 | no | — | 729 | 4 | 733 | Provider usage spike |
| 29 | 10 | yes | Trimmed suffix | 173 | 1 | 174 | Final rebuild |
| 34 | 10 | no | — | 228 | 1 | 229 | Stable suffix growth |
| 40 | 10 | no | — | 294 | 1 | 295 | Still below hard watermark |

The first genuinely history-limited rebuild was turn 17. It compiled 30 messages (15 complete
turns), 240 history bytes, and 213 estimated history tokens; the current prompt brought the
estimate to 222. Later rebuilds retained 28 to 30 complete messages. No failed, stopped, partial,
or mismatched message entered a compiled suffix.

Binary history decoding after the run produced:

- 80 messages: 40 user and 40 assistant.
- 80 `COMPLETE` statuses and 40 assistant usage records.
- 11,427 persisted cumulative provider input tokens, matching the final UI and logcat value.
- Final epoch 10, exact accounting 295/409, and calibrated coefficient 0.2293493501.

### Provider usage boundary

The same provider discontinuity found in P0 remained visible. Turns 2, 5, 12, 15, 16, 21, 23,
26, and 28 reported 534 to 781 input tokens despite the tiny prompt and bounded client transcript;
most coincided with approximately 44-second responses. Normal neighboring requests reported 5 to
312 tokens.

Exact usage is only known after a request completes, so no client can prevent an unexpected
provider-side prefix or accounting offset in that same turn. P1 handles the actionable part: each
spike marks the backend over hard watermark and forces a bounded rebuild before the next turn.
The client-assembled transcript stayed bounded, and each trimmed recovery landed at 174 to 212
exact tokens, below the 230-token compaction target. Capacity accounting and provider billing
telemetry therefore remain related but not interchangeable.

## LiteRT result

LiteRT continued turns report incremental `inputTokens`, while `contextTokensAfterTurn` is the
engine's exact full KV-cache token count. The table intentionally shows both semantics.

| Turn | Epoch | Rebuilt | Incremental/rebuilt input | Output | Exact accounting | Duration | Result |
|---:|---:|:---:|---:|---:|---:|---:|---|
| 1 | 11 | yes | 13 | 2 | 15 | 9.4 s | Initial local session |
| 8 | 11 | no | 12 | 2 | 121 | 5.3 s | Exact cache count grows by 14 |
| 16 | 11 | no | 12 | 2 | 233 | 6.4 s | Backend reused |
| 21 | 11 | no | 12 | 2 | 303 | 6.5 s | Backend reused |
| 26 | 11 | no | 12 | 2 | 373 | 11.1 s | Backend reused |
| 28 | 11 | no | 12 | 2 | 401 | 9.8 s | Below hard watermark |
| 29 | 11 | no | 12 | 2 | 415 | 9.1 s | Crossed hard watermark |
| 30 | 12 | yes | 193 | 2 | 195 | 16.7 s | Trimmed rebuild below target |
| 31 | 12 | no | 12 | 2 | 209 | 9.2 s | Reuse resumed |
| 40 | 12 | no | 12 | 2 | 335 | 9.1 s | Still below hard watermark |

Turn 30 compiled 24 messages (12 complete turns), 192 history bytes, and 206 estimated history
tokens. The current prompt produced a 218-token estimate with `trimmed=true`. LiteRT measured 193
input plus 2 output tokens after prefill, so the rebuilt session landed at 195/409 and remained on
epoch 12 through turn 40.

The local persisted result was 80 complete messages, 40 usage rows, and 670 cumulative input
tokens. Continuation latency varied rather than increasing monotonically. The expected one-time
prefill cost appeared on the trimmed rebuild, after which turn latency returned to roughly nine
seconds on this 2017 device. There was no crash, generation failure, context overflow, or session
interruption.

## P0 comparison

| Behavior | P0 legacy turn policy | P1 token-watermark policy |
|---|---|---|
| Primary rotation signal | Fixed 32 completed turns | Exact context tokens; 64 turns only as guard |
| Rebuild suffix | Fixed latest 24 turns / 192 KiB | Newest complete turns fitting 45% target |
| First observed history trim | Turn 33: 368 to 291 input | Online turn 17: 720/spike to 212; local turn 30: 415 to 195 |
| Response to provider usage spike | Continued until fixed turn boundary | Rebuilt before the immediately following turn |
| Local KV-cache control | No token-driven bound | Exact 415 triggered a rebuild to 195 |
| Persisted transcript | Complete | Still complete; context selection is independent |

The P0 run used the old 32/24 policy and grew from 5 input tokens on turn 1 to 368 on turn 32.
Turn 33 retained 24 turns and dropped only to 291 before growth resumed. In P1, rotation timing is
independent of conversation age, and every true compaction recovered to at most the configured
45% target. The intentionally tiny 512-token stress budget should not be interpreted as a
recommended user setting; it exists to cross multiple watermarks in a 40-turn smoke run.

## Automated verification

Run the complete JVM regression and build every debug artifact after the documentation update:

```powershell
./gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug
```

- Result: `BUILD SUCCESSFUL` on 2026-08-31.
- JVM result: 63 suites, 302 tests, 0 failures, 0 errors, and 0 skipped.
- Debug artifacts: arm64-v8a, x86_64, and universal APKs were all produced successfully.
