# P2 Rolling Context Checkpoint Smoke

This record closes Roadmap P2. It verifies incremental summary checkpoints, structured working
memory, bounded context assembly, failure isolation, and durable checkpoint storage in launcher
chat.

## Status

- P2 implementation: complete on 2026-08-31.
- Online long conversation: 63 successful visible turns with three incremental checkpoint
  segments.
- Long-range recall: the model returned the exact confirmed constraint from turn 1 after only the
  newest two raw turns remained in the request.
- Failure isolation: a separate checkpoint exhausted both attempts while Wi-Fi was disabled; the
  following visible chat turn still completed successfully after network recovery.
- Test-only device state: restored to the original 16K context budget, Unlimited output, connected
  Wi-Fi, and 256 KiB logcat buffers after the run.

## Implementation under test

P2 layers durable compressed context on top of the P1 token-watermark compiler:

- Conversation history codec v4 stores contiguous `SummarySegment` checkpoints and typed working
  memory with exact source-message provenance. Older codec versions remain fail-closed, matching
  the project's pre-release storage policy.
- The 65% soft watermark schedules only complete successful turns that the next step compaction
  will evict. A checkpoint starts after the visible turn and uses a one-shot backend session for
  the current target, independent of the active chat session.
- Summary input, output tokens, and response bytes have hard limits. Strict JSON parsing and local
  validation reject unknown fields, invalid source IDs, oversized data, illegal memory-state
  transitions, stale source hashes, and confirmations not grounded in a user message.
- A malformed result or backend failure receives one retry. If structured transport output is not
  accepted consistently by a provider, the retry removes the transport schema while retaining the
  complete JSON contract in the prompt. Final failure preserves the previous checkpoint and never
  fails or delays a visible chat turn.
- Context assembly places L1 working memory before L2 summary segments, then the newest complete
  raw-turn suffix. Each derived layer has an independent token cap. The 90% absolute guard drops
  both derived layers for one emergency turn; a distinct compilation fingerprint then forces the
  next safe turn to rebuild and restore them.
- Editing, deleting, or regenerating covered history invalidates the affected checkpoint suffix and
  its dependent memory. Target changes retain target-neutral text checkpoints but still rebuild
  the backend when the compilation fingerprint changes.
- History normalization may discard only a prefix already covered by a validated checkpoint.
  Completion persistence is debounced for 750 ms and merged with a nearby checkpoint write; no
  streaming text delta writes the conversation file.

## Device and controlled setup

- Device: Sony G8441, Android 9 / API 28, arm64-v8a.
- Plugin: 1.1.0 arm64 debug APK installed with `adb install -r`.
- Online target: `PoloAPI`, `claude-opus-4-8`, OpenAI-compatible protocol.
- Stress settings: 1,024 context tokens, 16 maximum output tokens, generation usage visible.
- First user message: `Confirmed constraint: project codename is ORCHID-731. Remember it. Reply
  only ACK.`
- Turns 2 through 60: short cross-topic prompts, deliberately unrelated to the codename.
- Turn 61: requested the exact project codename from turn 1.
- Turns 62 and 63: exercised entry into and recovery from the absolute-protection path.

The 1K application budget is intentionally much smaller than the production default. Its P1
watermarks were 665 soft, 819 hard, and 921 absolute tokens. The summary call has its own bounded
request allowance so it can compress an eviction range larger than the active chat budget.

## Incremental checkpoint result

The checkpoints advanced monotonically and never re-summarized an earlier range:

| Observation | Newly covered messages | Stored segments | Working-memory items | Attempts | Summary usage |
|---|---:|---:|---:|---:|---:|
| First successful checkpoint | 1-14 | 1 | 2 | 1 | 1,293 input / 267 output |
| Second successful checkpoint | 15-18 | 2 | 2 | 1 | 1,530 input / 269 output |
| Third successful checkpoint | 19-22 | 3 | 4 | 1 | 1,063 input / 389 output |

Real-provider testing exposed two compatibility cases before this successful sequence. One
structured response omitted `workingMemory`, and a later response incorrectly cited an assistant
acknowledgement as confirmation provenance. The implementation now retries parse or validation
failures without the transport schema, embeds the full response contract in the fallback prompt,
and explicitly requires confirmed items to cite only user messages that state or confirm them. The
strict local validator was not weakened.

The persisted v4 conversation file contained the checkpoint ranges and the typed
`project_codename` constraint with `CONFIRMED` status, source provenance, and value `ORCHID-731`.
The source hash and range are revalidated before a generated checkpoint can replace durable state.

## Bounded context and long-range recall

At turn 61, only four raw messages (two recent complete turns) were retained. L1 fitted two working
memory items; the deliberately tight 1K budget left no room for L2 summary segments. The compiled
estimate was bounded, and the provider reported 916 input plus 9 output tokens. The visible answer
was exactly:

```text
ORCHID-731
```

This recalls the turn-1 constraint through working memory after the original raw message had left
the request. It does not depend on semantic search or an unbounded transcript replay.

The same run verified the absolute-watermark lifecycle:

| Turn | Rebuilt epoch | Derived context | Compile estimate | Post-turn accounting | Result |
|---:|---:|---|---:|---:|---|
| 61 | 1 | 2 L1 items, 0 L2 segments | bounded below the request cap | 925 | Recall succeeded; absolute watermark crossed |
| 62 | 2 | L1/L2 intentionally omitted | 390 | 269 | Emergency compacted turn completed |
| 63 | 3 | 3 L1 items restored | 407 | 490 | Omitted-state fingerprint forced safe rebuild |

Provider-side token reporting can still jump after a request is sent, as noted in the P0 and P1
records. P2 keeps the client-assembled input bounded and ensures an over-absolute completion causes
the immediately following request to shed derived context. It then restores durable memory on the
next request whose accounting is safe.

## Offline summary failure

A separate conversation was used so the failure test could not disturb the 63-turn recall run.
After five setup turns, turn 6 completed with 1,240 provider input and 4 output tokens, which
scheduled a checkpoint for messages 1-2. Wi-Fi was disabled immediately after the `Context summary
start` diagnostic.

Both hidden attempts failed at the online network-policy boundary. The outcome retained the old
checkpoint state and produced no assistant failure bubble or blocked composer. Wi-Fi was then
enabled and confirmed `CONNECTED/CONNECTED`; the next visible prompt requested only `RECOVERED` and
completed successfully with 343 post-turn accounting in a rebuilt epoch.

This covers the intended degraded mode: checkpoint generation is optional background maintenance,
while ordinary chat remains the primary operation.

## Persistence and invalidation observations

- The main conversation persisted all 63 visible turns independently of how many raw messages were
  selected for a backend request.
- Checkpoint completion wrote v4 context state atomically. Instrumented `Conversation persist`
  events showed start-state writes followed by debounced completion writes, with no write per
  streaming delta; a checkpoint arriving in the pending window shared the coalesced write.
- Codec tests round-trip every context field and reject v3 input. Policy tests retain uncovered old
  messages, permit trimming only through `coveredThroughMessageId`, and preserve complete turns.
- Invalidation tests cover edits before and after the checkpoint boundary, repeated edits, stale
  in-flight results, deletion, and regeneration. A changed context fingerprint rebuilds the chat
  backend before reuse.

## Automated verification

The full debug regression, lint analysis, and APK build were run after the provider-hardening and
emergency-restore fixes:

```powershell
./gradlew.bat --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

- JVM result: 65 suites, 326 tests, 0 failures, 0 errors, and 0 skipped.
- Lint result: no debug lint errors.
- Debug artifacts: arm64-v8a, x86_64, and universal APKs were produced successfully.
