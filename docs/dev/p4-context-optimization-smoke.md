# P4 Context Optimization Smoke

This record closes Roadmap P4. It verifies byte-stable online request prefixes, provider cache
telemetry, bounded context UI controls, SQLite FTS4 history recall, and the decision gate for
embedding-based semantic recall.

## Status

- P4-1: complete. OpenAI-compatible, Anthropic, and Gemini request adapters have a byte-prefix
  stability regression test. Their cache-read usage fields are normalized into one telemetry
  model.
- P4-2: complete. Launcher chat shows total context usage and L1, L2, Recall, and L3 estimates.
  The details dialog exposes all thresholds and a manual compact action.
- P4-3: complete. A disposable SQLite FTS4 sidecar indexes stable 2 to 4 turn chunks and recalls
  at most four chunks within a dedicated token cap.
- P4-4: evaluated. The fixed keyword workload passes the Recall@4 gate, so an embedding runtime
  and vector store are not included in this phase.
- Device smoke: complete on 2026-08-31 with a local deterministic HTTPS provider and an API 36
  emulator.

## Implementation under test

### Stable prefixes and cache telemetry

`OnlineAiPrefixStabilityTest` serializes the same L0, L1, L2, and append-only conversation prefix
through all three online protocol adapters. The shorter and extended requests must be byte equal
through the last shared user message. The test also verifies that the fixed and derived layers
remain before raw conversation turns.

Provider usage is normalized as follows:

| Protocol | Read telemetry | Write telemetry | Eligible input |
|---|---|---|---|
| OpenAI-compatible | `prompt_tokens_details.cached_tokens` or equivalent input details | Optional compatible `cache_write_tokens` | Reported prompt/input tokens |
| Anthropic | `cache_read_input_tokens` | `cache_creation_input_tokens` | Uncached input plus cache read plus cache creation |
| Gemini | `cachedContentTokenCount` | Not reported | Prompt token count |

All fields are optional and fail open. Providers that omit cache details retain their ordinary
input/output accounting. When an eligible count exists, `promptCacheHitRate` is computed as cached
input divided by eligible input and is written to the context completion diagnostic.

### Context usage UI

The compact status row has this shape:

```text
Context <used> / <budget> tokens (<percent>%) | L1 <n> | L2 <n> | Recall <n> | L3 <n>
```

The details dialog distinguishes exact backend accounting from calibrated layer estimates and
shows L0, L1, L2, Recall, L3, provider/template overhead, and the soft, hard, and absolute
watermarks. `Compact now` closes the active backend and makes the next turn compile to the 45%
target. It does not delete visible history, mutate durable checkpoints, or issue a hidden model
request.

### FTS recall sidecar

`conversation-recall.db` is deliberately not the authoritative history store. AtomicFile history
and its L1/L2 context state remain the source of truth. The sidecar can be deleted or rebuilt
without losing a conversation.

The index uses:

- A regular `recall_chunks` table for conversation ID, stable source range, source hash, payload,
  recency, and turn count.
- An FTS4 `recall_chunk_terms` virtual table with the Android `unicode61` tokenizer.
- Stable three-turn chunks, a final two-turn chunk, and no index entry for an incomplete or
  one-turn tail.
- Text excerpts capped at 4,096 code points per message.
- CJK, Japanese, and Hangul bigrams plus Latin and Cyrillic identifiers, camel-case splits, and
  kebab-case splits.
- Ranking weighted 85% by keyword coverage and 15% by recency. At most four chunks are admitted,
  with a 1,024-token Recall layer cap.
- Original USER and ASSISTANT roles on injection. A recalled chunk is never converted to SYSTEM.

Index synchronization runs only after an authoritative history write succeeds. Changed ranges
are replaced incrementally, and clear, delete, import, and edited-source invalidation paths update
the sidecar. Database errors discard or bypass the sidecar and never block chat or history
persistence.

## Device and controlled setup

- Device: Android SDK emulator `sdk_gphone64_x86_64`.
- Android: 16 / API 36, x86_64.
- Plugin: 1.1.0 debug, version code 55.
- Provider profile: test-only OpenAI-compatible profile `P4-Smoke`.
- Endpoint: `https://127.0.0.1:18443/v1`, reached through an `adb reverse` mapping.
- Provider: `.python/p4_mock_provider.py`, bound only to host loopback and authenticated with a
  dummy key.
- Initial stress budget: 512 tokens, with hard watermark 409 and compaction target 230.
- Recall-fit budget: 768 tokens, with hard watermark 614 and compaction target 345.

The server reports cache usage from the longest common byte prefix of canonical request messages
and returns OpenAI-compatible SSE. This validates the complete Android transport, parser,
accounting, logging, and UI path. It is not a benchmark or performance claim for OpenAI,
Anthropic, Gemini, or any other production provider.

The HTTPS certificate and its debug-only trust configuration existed only for this controlled
run. No private key, certificate, dummy profile, or relaxed network policy is present in the final
workspace or APK.

## Prefix and cache result

Eight visible turns were sent before the long-range recall probe. The selected rows below are the
app requests; an earlier direct curl probe was excluded.

| Visible turn | Backend epoch | Rebuilt | Input tokens | Cached input | Cache hit rate | Observation |
|---:|---:|:---:|---:|---:|---:|---|
| 1 | 1 | yes | 25 | 6 | 24.00% | Initial bounded prefix |
| 2 | 1 | no | 61 | 24 | 39.34% | Append-only reuse |
| 3 | 1 | no | 108 | 60 | 55.56% | Append-only reuse |
| 4 | 1 | no | 139 | 107 | 76.98% | Stable shared prefix |
| 5 | 1 | no | 184 | 138 | 75.00% | Stable shared prefix |
| 6 | 1 | no | 231 | 183 | 79.22% | Stable shared prefix |
| 7 | 1 | no | 278 | 230 | 82.73% | Highest pre-rotation hit |
| 8 | 2 | yes | 188 | 6 | 3.19% | Step eviction and expected cache reset |

The device log and provider log agree for every row. Turn 8 compiled a trimmed eight-message raw
suffix at 196 estimated input tokens. The provider measured 188 input tokens. This is the intended
sawtooth: cache reuse rises while a prefix is append-only, then a low-frequency bounded rebuild
resets it instead of shifting the prefix on every turn.

## UI and manual compact result

After the next turn, the context details dialog showed:

```text
Used: 208 / 512 tokens
Source: Exact backend count
L0 fixed: 0
L1 memory: 0
L2 summary: 0
Recall: 0
L3 recent: 225
Provider/template: 0
Soft / hard / absolute: 332 / 409 / 460
```

Tapping `Compact now` closed the backend and immediately changed the status row to the prospective
compacted view `223 / 512 tokens`. The provider request count did not change, proving that the
action did not make a hidden generation call. The following visible turn created a new epoch and
compiled against the compaction target.

## FTS schema and incremental indexing result

The Android platform SQLite implementation created the expected regular and FTS4 tables:

```text
recall_chunks
recall_chunk_terms
recall_chunk_terms_content
recall_chunk_terms_docsize
recall_chunk_terms_segdir
recall_chunk_terms_segments
recall_chunk_terms_stat
```

After eleven complete visible turns, the sidecar contained four stable chunks:

| Source messages | Complete turns | Payload characters |
|---|---:|---:|
| 1-6 | 3 | 362 |
| 7-12 | 3 | 348 |
| 13-18 | 3 | 354 |
| 19-22 | 2 | 234 |

Earlier log observations showed `deleted=1, inserted=1` when a provisional tail became a stable
three-turn chunk, and `inserted=1` when a new chunk was added. This confirms incremental range
replacement instead of full database rewrites.

The actual FTS payload for messages 1-6 contained `orchid`, `orchid-731`, `launch`, and `code`.
The query `Please retrieve the exact ORCHID-731 launch code constraint from earlier` produced:

```text
Recall search: candidates=1, results=1, ranges=1-6
Context compile: estimatedInputTokens=318, historyTokens=302, coveredMessages=8,
                 recallCandidates=1, recalledChunks=1, compactionTargetTokens=345
Context complete: actualInputTokens=312, actualOutputTokens=12
```

The visible status then showed:

```text
Context 324 / 768 tokens (42%) | L1 0 | L2 0 | Recall 130 | L3 208
```

At the earlier 512-token setting, the same old chunk was found but could not fit beside the four
minimum recent turns under the 230-token compaction target. The compiler omitted Recall instead of
exceeding the budget. At 768 tokens it admitted the chunk before packing the recent raw suffix.
Both branches are expected and preserve the invariant that current input and the minimum recent
turn floor take precedence over optional recall.

## Embedding decision gate

`ConversationRecallEvaluationTest` builds a reproducible 15-topic corpus covering Chinese,
English, Japanese, Russian, code identifiers, provider terms, devices, and context policy. Its 16
keyword and identifier probes reported:

```text
P4 keyword evaluation: probes=16, top1=1.0, recallAt4=1.0
```

A separate semantic-only boundary probe intentionally removes shared lexical terms:

```text
P4 boundary evaluation: keywordRecallAt4=1.0, semanticOnlyRecallAt4=0.0
```

The second result is a documented limitation, not evidence that lexical recall is semantic. P4
does not add embeddings because the intended exact-fact, identifier, API, error-code, and policy
workload clears the 90% Recall@4 gate, while L1/L2 already provide model-generated semantic
compression for older context.

Reconsider an on-device embedding model only when both implementation cost and observed need are
measurable. The initial production trigger is either:

- At least 100 audited real recall queries produce keyword Recall@4 below 90%.
- Semantic-only misses materially change task outcomes despite valid L1/L2 checkpoints.

A new proposal must separately budget model package size, indexing latency, battery use, vector
storage, deletion behavior, and the privacy boundary for on-device versus remote embeddings.

## Automated verification

The final gate runs the complete debug JVM suite, lint, and all debug APK builds after removing the
temporary network-security source set:

```powershell
./gradlew.bat --offline :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The regression specifically includes:

- Three-protocol byte-prefix stability and cache-usage parsing.
- Exact-versus-estimated context layer reconciliation.
- Stable chunk boundaries, multilingual tokenization, ranking, payload codec, sticky recall,
  duplicate prevention, Recall layer cap, and emergency omission.
- The fixed P4 keyword and semantic-boundary evaluation corpus.
- Existing P0 through P3 accounting, summary, persistence, Binder, and UI compatibility suites.

Final result:

- `BUILD SUCCESSFUL` in 3m 38s.
- JVM result: 70 suites, 352 tests, 0 failures, 0 errors, and 0 skipped.
- Lint result: `lintDebug` completed without a blocking issue.
- Debug artifacts: arm64-v8a, x86_64, and universal APKs were produced.

After the gate, the standard x86_64 debug APK is reinstalled on `emulator-5556` with empty app
data. The loopback reverse mapping, test profile, smoke history, local provider process, temporary
certificate directory, and debug-only CA trust files are removed. The restored launcher showed
`No target selected` and `Context usage unavailable`; its newly initialized recall index contained
zero chunks.
