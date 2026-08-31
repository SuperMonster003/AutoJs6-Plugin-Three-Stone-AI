# Binder Persistent Context Design

This document describes the bounded-context contract used by persistent provider sessions opened
through AutoJs6 `ai.session` or the raw Binder API. Launcher chat has a separate persisted
checkpoint and working-memory pipeline; Binder sessions intentionally use transparent raw-turn
compaction only.

## Scope and invariants

- `RemoteThreeStoneAiSession` retains the initial request history, all original SYSTEM messages,
  and successfully completed user/assistant turns in plugin memory.
- A failed, cancelled, timed-out, output-limited, or completion-validation-failed turn never enters
  the transcript. Its prepared context plan is abandoned when the Binder turn is disposed.
- No hidden LLM call summarizes Binder history. Compaction only removes the oldest complete raw
  turns.
- The host-visible Binder session ID, target, fixed generation options, response schema, chunk
  sequence, usage event, and completion event remain unchanged when the backend is rebuilt.
- Only one Binder turn can be active, as before. Every abnormal terminal still closes the complete
  remote session so an uncertain backend cache is never reused.

Initial non-SYSTEM history is normally parsed as complete user/assistant pairs. If a valid caller
supplies an irregular sequence, it remains an anchored block rather than being silently reordered
or partially evicted. SYSTEM messages are copied separately and always lead a rebuilt request.

## Limits and compaction

The session applies both capacity and memory limits:

| Limit | Default | Behavior |
|---|---:|---|
| Application input budget | 16,384 tokens | Same default budget as launcher chat; target capacity can reduce it |
| Hard watermark | 80% of effective input | Rebuild before the next turn |
| Absolute protection | 90% of effective input | Reject an irreducible request before generation |
| Rebuild target | 45% of effective input | Pack a recent complete-turn suffix |
| Recent raw floor | 2 complete turns | May exceed the 45% target, but never the absolute limit |
| In-memory transcript guard | 512 KiB | Step-evict oldest turns toward 45%; force a rebuild |
| Request byte limit | Target limit, at most 256 KiB | Includes rebuilt history and the current prompt |
| Backend turn guard | 64 completed turns | Leak guard inherited from shared context accounting |

`ContextAccounting` prefers `GenerationStatistics.contextTokensAfterTurn`, which both current
online and LiteRT backends provide. If an implementation omits the full counter, the Binder policy
falls back to conservative incremental estimation. A large new prompt participates in the
preflight projection, so it can trigger an early rebuild instead of overflowing an otherwise
reusable backend.

The 512 KiB transcript guard is independent of the per-request byte limit. It bounds plugin-owned
session memory between rebuilds. If retaining the recent floor cannot reach the 45% byte target,
the policy keeps the floor while it remains below 512 KiB. It may evict below the floor only to
honor the hard memory guard. If even the newest turn cannot be retained, the context is marked
exhausted and the next turn fails closed.

## Turn lifecycle

1. `PromptPlanner` materializes the Binder request. The first turn yields full history plus the
   current USER prompt; later protocol turns yield only the new USER prompt.
2. `PersistentSessionContext` returns an immutable plan:
   - `INITIAL`: use the first request unchanged with a new backend session.
   - `CONTINUE`: keep the backend and call `streamNext()` with only the new prompt.
   - `REBUILD`: close the completed backend, create a new one, and call `stream()` with original
     SYSTEM messages, anchored initial data, the compacted recent suffix, and the current prompt.
3. The ordinary listener forwards text deltas. The context plan is still provisional.
4. After output aggregation and protocol completion validation succeed, the output snapshot is
   appended as the assistant side of the turn and accounting is updated. Every failure path
   abandons the plan without changing the last successful transcript.

Backend replacement happens before `onStarted` for that turn. `persistentSessionId` remains stored
on the outer Binder session, so every `onStarted` event continues to report the same ID. The same
`FixedConfiguration` comparison runs before either continuation or rebuild.

## Hard failure contract

If the pinned SYSTEM/anchored data, current prompt, and minimum available recent suffix still exceed
the absolute token protection or request byte limit, generation does not start. The whole session
closes through the existing failure path with `AiErrorCode.INVALID_REQUEST` and message
`Persistent AI context exceeds the safe limit`.

No `CONTEXT_EXHAUSTED` or other host API code is introduced. A later `generateNext` against the
closed Binder object follows the existing `PROVIDER_UNAVAILABLE` closed-session behavior.

## Diagnostics

Debug builds emit text-free records under tag `ThreeStoneAiBinder`:

- `Persistent context start`: target, mode, input estimate, hard/absolute watermarks, retained
  bytes and turns, evicted turns, and backend epoch.
- `Persistent context complete`: actual usage, normalized accounting, transcript bytes, retained
  turns, memory-guard eviction, pending rebuild state, and backend epoch.

These records are sufficient to verify that host callbacks remain continuous while backend epochs
change and rebuilt input returns to the compaction band. Prompts and model output are never logged.

## Automated coverage

`PersistentSessionContextTest` covers:

- initial SYSTEM/history capture after success;
- failure and cancellation abandonment;
- complete-turn memory-guard eviction;
- irregular initial-history anchoring;
- 48 turns through fake persistent backend sessions, including both `streamNext()` and rebuilt
  `stream()` dispatch with an unchanged host event sequence;
- absolute-limit failure and reuse of the existing `INVALID_REQUEST` code.

`PluginInstructionCompatibilityTest` requires all eleven localized instruction resources to carry
the synchronized persistent-context contract and 512 KiB guard marker.
