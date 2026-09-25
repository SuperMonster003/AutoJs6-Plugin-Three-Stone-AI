# P9.2 Provider image input

Date: 2026-09-26. Candidate: 3-Stone AI 1.2.0 / build 216, based on
`4e887e8`. This implements the original AI Agent P9.2 model item at the
Provider layer. Real online vision acceptance remains pending. No release,
tag, push, new roadmap stage or public script API is part of this change.

## Implemented boundary

- The four bundled release SDKs come from AutoJs6 `52ce694f92` / build 5297.
  `libs/README.md` records their source/license and `libs/SHA256SUMS` locks
  their hashes. Gradle verifies this exact artifact set without reading a
  sibling repository at build time.
- Provider V2 negotiates 2.0 or 2.1. Plugin metadata declares the image-input
  extension, but an online target declares `vision` only when its exact model
  ID is explicitly enabled in that profile. All pages of the 2.0 target
  catalog omit vision. Protocol names and model names do not imply support.
- The profile editor adds a model checkbox picker for image input. Existing
  profiles remain disabled. Profile schema 4 preserves this list through
  cloning, import and export; schemas 2/3 are still readable and initialize
  an empty list. Invalid, duplicate and unselected IDs are rejected. Provider
  changes clear the editor selection and removed models lose their opt-in.
- Requests stay bound to the configured service, exact model and original
  credentials/network policy. The picker explains that host-requested images
  will be sent to that service. No automatic model, provider, local/cloud or
  protocol fallback is introduced.
- Initial user image parts and native tool-result images enter the same
  bounded online conversation. JPEG/PNG are accepted; image generation,
  audio and video are outside this feature. LiteRT and persistent `ai.session`
  do not accept images. A vision request for the persistent path is rejected
  even if its first turn has no image yet.
- Before reading attachments, validate the negotiated request, target,
  result IDs, descriptor indices and declared quotas. Then check exact byte
  length, SHA-256, actual MIME, decoded dimensions and decodability. Image
  storage uses an immutable byte copy; base64 appears only while constructing
  the HTTP body, not in retained tool JSON or ordinary diagnostics.

| Limit | Value |
| --- | --- |
| Each image | 4 MiB |
| Initial or tool-result image batch | 4 images / 8 MiB |
| Each edge / pixel count | 4096 / 16,777,216 |
| Initial images plus all continuation images | 16 images / 32 MiB |
| Existing text context ceiling | 256 KiB, counted separately |
| Encoded HTTP request body with images | 48 MiB |
| Encoded HTTP request body without images | Existing 512 KiB ceiling |

The larger image HTTP ceiling accounts for base64 expansion of the already
bounded conversation. It does not enlarge text, image, result or session
quotas. Tool continuations retain the original deadline, output budget and
cumulative usage behavior from P9.1.

## Three protocol mappings

| Protocol | Initial image | Native tool result with image |
| --- | --- | --- |
| OpenAI-compatible Chat Completions | User content array with a data-URL `image_url` | Text-only `tool` messages first for every pending call, followed by one user observation containing images labeled with their call IDs |
| Anthropic Messages | User image block with a base64 source | Text and image blocks inside the matching `tool_result`, preserving `tool_use_id` and `is_error` |
| Gemini GenerateContent | User `inlineData` part | All `functionResponse` parts first, then sibling label/`inlineData` parts in the same user content; original signed assistant parts remain intact |

OpenAI Chat Completions tool messages accept text content; image arrays are
therefore not placed directly in those messages. Image observations are
labeled as untrusted tool data, and are never inserted between parallel tool
results. For Gemini, sibling `inlineData` is the implementation's use of the
general user-content schema; it does not require model-specific nested
multimodal `functionResponse` support. This choice has deterministic mapping
coverage, not a successful real Gemini backend acceptance result.

## Descriptor ownership and cancellation

The Provider reopens a private nonblocking read descriptor, preserving a
regular file's current offset. A bounded poll checks cancellation and worker
interruption every 100 ms. That private descriptor stays owned by the worker
until its `finally` block, so another thread cannot close and recycle its
number underneath an active read. Declared length plus one-byte EOF checking
rejects both short and overlong payloads.

Reliable pipe status must survive asynchronous ownership transfer. The
incoming `ParcelFileDescriptor` is transferred through a parcel with
`PARCELABLE_WRITE_RETURN_VALUE`, preserving both descriptors and silently
closing the original received owner. A plain `dup` loses the communication
channel; an ordinary close of the old owner can consume a queued producer
status before the new owner calls `checkError`.

The sender also must retain its read endpoint through consumption or transfer
that ownership silently. The existing exact-text native-result test previously
closed its sender read copy immediately after the AIDL call. It now keeps
that copy until the terminal result, with the same output assertion. New
tests cover producer failure before initial ownership transfer and through
an actual AIDL tool-result proxy. Cancellation tests assert that blocked
workers terminate, in addition to asserting the terminal callback.

## Verification

| Check | Result |
| --- | --- |
| Full JVM suite | 379 tests, 0 failures/errors/skips |
| New JVM image cases | 5 mapping/profile/quota/catalog cases and 1 three-protocol HTTP session continuation case |
| API 24 / x86_64 | 22/22 Android tests, 4.425 s |
| API 37.1 / x86_64 / 16 KiB pages | 22/22 Android tests, 4.396 s |
| Debug / androidTest / R8 release / signed archive | Passed |
| Lint | 0 errors / 97 warnings |
| ELF/ZIP alignment | Debug and release sets passed 16,384-byte checks |
| Ten-language resources, README and changelog | Generator read-only check and repository checks passed |
| Exact archived release install and launcher | API 24, API 37.1 and XQ-DQ72 passed, version 1.2.0 / 216 |
| New model picker on API 24 R8 release | Initially Disabled; child selection updated the parent summary; cancelling the parent left no saved profile |

Each Android run comprises 9 `VisionSessionAndroidTest` cases, 11 native-tool
session cases and 2 discovery/metadata cases. Coverage includes PNG/JPEG,
legacy text, mixed image/text descriptor indices, streaming/non-streaming
continuations, cumulative usage, incorrect hash/MIME/dimensions/encoding/length,
unsupported targets, persistent vision rejection, unrequested images,
incorrect call IDs, initial/result pipe cancellation and timeouts, regular
file offsets and reliable-pipe failures. These use deterministic backends
and a test-only UID verifier. Production `HostCallerVerifier` is unchanged.
They do not establish a cross-UID production host-to-cloud path or a release
Binder pass. The real native runtime was not changed or newly benchmarked;
16 KiB process execution is not evidence of new Gemma inference coverage.

Initial failed validation runs are retained in ignored build logs: old
schema assertions, a Gradle import resolution error, test named-argument
compilation errors, an invalid 400 ms test timeout, and the reliable-pipe
sender ownership issue above. All affected suites passed after correction.
An initial instrumentation attempt against an unmatched old installed test
APK crashed before the suite; it is not a product pass. The final production
build/lint/archive run took 1m 37s. A later Android-test-only change added
probe metrics before its assertion; it did not change production code.

## Real online attempts and the remaining gate

XQ-DQ72 / API 35 used the configured Model8 target for `claude-fable-5-1`.
The explicitly enabled probe uses the real credential repository, network
policy, HTTP execution and Provider session, with a generated 512 x 192 JPEG
containing six random digits. The prompt does not include the expected
digits. It enables that exact profile's image capability only in memory;
saved profiles, credentials, default target and metered-network consent are
not changed. It does not upload a device screenshot or execute a device tool.

| Attempt | Observation | Result |
| --- | --- | --- |
| Initial image 1 | Empty final text; full test time 4.285 s | Failed |
| Native image tool 1 | Empty final text; full test time 2.399 s; tool-callback count was not recorded before the failing assertion | Failed; does not establish that image continuation was reached |
| Initial image 2 | 5277 ms session time, 8395 input tokens, 0 output tokens, 0 output characters, no tool calls, maximum output 1024 tokens | Failed |

All three failures remain failures. The evidence does not establish whether
the configured service supports image input or identify the cause of its
empty responses. Output exhaustion is not established by the last attempt,
which reported zero output tokens.

The maintainer subsequently stated that no suitable online image/video model
is currently available. Online vision testing is paused; PoloAPI was not
called and no further Model8 retry was made. This feature needs image input
with text output, not image/video generation. A future configured image-input
target must pass both initial-image and tool-result-image acceptance before
the original roadmap's model item is marked fully accepted. The gated probe
never performs a paid request in ordinary CI/connected test runs.

Agent `screen_capture`, its original longest-edge-1280/JPEG-70 conversion,
visual prompts and image budgeting remain the next original P9.2 plugin
item. P9.1 Wi-Fi comparison failures are independent and remain pending.

## Candidate artifacts and environment

The archive manifest correctly records pre-commit `4e887e8` and
`sourceDirty=true`. These exact candidate files are local, not published:

| ABI | CRC32 | SHA-256 |
| --- | --- | --- |
| universal | `3701DBAC` | `a207695776d11d589c81dab24bf3176de929500e064ef8cf539f074b8ca2e35b` |
| arm64-v8a | `39FF33F2` | `d537a7ff8681321d0b769536939cab55223b34028461bb05e604dbbd36a6597c` |
| x86_64 | `A9558EE6` | `49758e23e65ed7f4d3ab276365824f0fab8f428078a7e063c6835dbf83980b0c` |

XQ-DQ72 was updated from build 215 without clearing app data and was left on
the exact arm64 R8 candidate above, after the debug-only online probes. The
other three physical devices were unchanged. Private API 24/37 AVD data is
retained; no user AVD was resized or wiped. No network setting, order or
payment was changed. This work does not require a SIM card. Host and Rhino
sources were not modified during this Provider implementation.

Private `build/p92-*.log` files retain build, test and failed probe output;
`releases/v1.2.0/release-manifest.json` records the signed archive. Neither
directory belongs in Git.

## Primary references

- [OpenAI Chat Completions message content schemas](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create)
- [Anthropic tool-result content and ordering](https://platform.claude.com/docs/en/agents-and-tools/tool-use/handle-tool-calls)
- [Gemini image input](https://ai.google.dev/gemini-api/docs/generate-content/image-understanding)
- [Gemini GenerateContent](https://ai.google.dev/api/generate-content) and [Content/Part schema](https://ai.google.dev/api/caching#Content)
- [AOSP ParcelFileDescriptor ownership and reliable status](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/os/ParcelFileDescriptor.java)
