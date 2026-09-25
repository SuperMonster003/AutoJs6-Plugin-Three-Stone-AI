# P9.1 Provider native tool calling

Date: 2026-09-25. Candidate: 3-Stone AI 1.2.0 / build 215. This implements the original AI Agent roadmap's P9.1 model item; it is not a published release or completion of the Agent loop item.

## Implementation

- Online OpenAI-compatible Chat Completions, Anthropic Messages and Gemini GenerateContent targets declare `tools`. Local LiteRT-LM targets keep `tools=false`. No model, credential, target or local/cloud fallback is introduced.
- Tool definitions are materialized and validated through AI Provider V2. The HTTP adapters encode their native schema fields, accumulate streamed call arguments, return complete calls to the host, and append matching tool results to the same online conversation. JSON response fallback uses the same continuation path.
- OpenAI call indices can be interleaved. Anthropic input fragments must form a complete object and each content block must finish. Gemini retains signed parts in their original order and preserves wire IDs when present; omitted IDs receive unique session-facing IDs. Opaque provider content is private replay data, not host tool arguments or diagnostic text.
- The Provider does not execute tools. It enforces declared names, unique IDs, exact result batches, at most 16 rounds / 32 outstanding calls, bounded schemas/arguments/results and cumulative protocol tool-data quotas. Continuations recheck network policy and credentials and count retained history against the 256 KiB context ceiling. An explicit output-token budget decreases across HTTP calls instead of restarting each round.
- A tool callback waits for preceding credited text. Waiting and descriptor reads remain subject to the original timeout, cancellation, callback death and backend cleanup. IDs and declared result quotas are checked before reading a descriptor. Output or completion while awaiting results is a protocol violation.
- Per-invocation usage is accumulated for the Provider request. Missing required usage fails before tool execution; cumulative snapshots do not reuse only the last HTTP call's token count.
- Native tool requests currently reject `persistentSession=true` and initial tool-role history. Their tool continuations still share one Provider session; they do not use the separate persistent KV-cache conversation API. Public script `ai.ask`/`ai.stream` options are unchanged. Agent integration uses host build 5297+ and still needs the Agent P9.1 implementation.

## Verification

| Check | Result |
| --- | --- |
| Complete JVM suite | 373 tests, 0 failures/errors/skips |
| Native HTTP protocol/session tests | 11 new tests, including all three JSON paths and streamed/parallel/sequential calls |
| Cumulative usage tests | 3 new tests, including unavailable counters and overflow |
| API 24, default x86_64 image | 13/13 Android tests, 2.025 s |
| API 37.1, Google APIs x86_64, 16 KiB pages | 13/13 Android tests, 1.662 s |
| Debug / androidTest / R8 release / signed archive | Passed |
| Debug lint | 0 errors, 97 warnings |
| ELF and ZIP native page alignment | Debug and release artifact sets passed 16,384-byte checks |
| Ten-language README/changelog generation | Read-only check passed |
| Exact archived x86_64 release installation and launcher | Both emulators passed, installed version 1.2.0 / 215 |

Each Android run contains 11 new `NativeToolSessionAndroidTest` tests and 2 existing plugin discovery/metadata tests. The session tests force AIDL proxy marshalling and real Android file descriptors, using a deterministic backend and a test-only UID verifier. Production continues to use `HostCallerVerifier` with package, UID and signature checks. These tests are not cross-UID host-to-cloud acceptance.

The final build took 2m 12s. The recorded 299 source/resource hashes remained unchanged through JVM and device validation. Both debug device runs used the final build. The initial API 24 x86 emulator was stopped before installation; a separate x86_64 AVD matched the plugin's packaged ABI.

The release launcher checks succeeded, but an extra attempt to run the debug instrumentation APK against the R8 release failed in AndroidJUnitRunner initialization with `NoClassDefFoundError: kotlin.jvm.internal.Intrinsics`. Debug instrumentation depends on unoptimized target classes, so those two attempts are retained as failed test-harness runs, not release Binder passes. No production keep rules were weakened to accommodate them. A release-aware external/Binder harness remains required before a release-level native-tool acceptance claim.

## Candidate artifacts

The signed archive was built before the logical commit, so its private manifest correctly records the pre-commit revision and `sourceDirty=true`. These are local candidate files, not GitHub release assets or a new tag.

| ABI | CRC32 | SHA-256 |
| --- | --- | --- |
| universal | `56B3E5C9` | `48bb027e90660ee9376bcddc023191d5ae998a14671c7f770ad76eea1f0e4876` |
| x86_64 | `F1A5BABA` | `6f75e0873203d669478e8fb269b4ca950b17b652910fdf6ae3415071281c36fd` |
| arm64-v8a | `91B9A1BA` | `db8755d6a1778fd8cbae87991989c884ca79fc300eecd79d1d7635fdb5e0db21` |

Private logs, source hashes and instrumentation output are under `build/p91-model-private/`; APKs and their signed manifest are under `releases/v1.2.0/`. Neither directory is committed.

## Remaining roadmap work

The Agent `ModelClient` native loop, common decision validation/confirmation/journal integration, and Wi-Fi/calculator native-versus-JSON task comparison remain original P9.1 items. No real model API was called for this acceptance, no real device setting or SIM was used, and no order was created. Running the app on a 16 KiB image does not substitute for new LiteRT inference evidence; the native runtime dependency did not change.

## Protocol references

- [OpenAI function calling and streamed Chat Completions calls](https://developers.openai.com/api/docs/guides/function-calling)
- [Anthropic client tool calls and results](https://platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls)
- [Anthropic stream event and input JSON delta structure](https://platform.claude.com/docs/en/build-with-claude/streaming)
- [Gemini signed function-call parts and result continuation examples](https://ai.google.dev/gemini-api/docs/generate-content/thought-signatures)
