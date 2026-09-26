# P9.2 Provider image input

Date: 2026-09-26. Real image-input acceptance now passes for the explicitly
selected AiGoCode / `gpt-5.6-sol` target on Provider `7138fd0` / 1.2.0 / 218,
for both initial images and images returned by native tools. The final
section records those measurements and their limits.

The implementation receipt and earlier unsuccessful probes below are
preserved as historical evidence from the build 216 candidate based on
`4e887e8`, subsequently committed as `d0ad293`. No release, tag, push, new
roadmap stage or public script API is part of this documentation update.

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

## Earlier real online attempts and the original pending gate

The original receipt recorded XQ-DQ72 / API 35 with the configured Model8 target for `claude-fable-5-1`.
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

At that time, Agent `screen_capture`, its original
longest-edge-1280/JPEG-70 conversion, visual prompts and image budgeting were
the next original P9.2 plugin item. That Agent integration was subsequently
completed and is distinct from the selected-model probes recorded below.
P9.1 Wi-Fi comparison failures are independent.

## Initial candidate artifacts and environment

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

## Accepted AiGoCode follow-up

The maintainer explicitly authorized the newly configured AiGoCode /
`gpt-5.6-sol` target for text comparison and an image-input trial. The public
catalog confirmed that exact model before the probes. This device now
reports serial QV770340J7, model XQ-DQ72, Android 13 / API 33. Earlier receipts
labeled this device API 35; that historical value has not been reverified
and is not evidence of an OS change. This follow-up uses the current API 33
reading.

Both probes used the matching officially signed Debug and androidTest
builds of Provider `7138fd085660edfa0b8b7e918b5eb06c33982ffe` / 1.2.0 / 218.
Each generated a 512 x 192 JPEG at quality 70 containing six random digits;
the prompt did not contain the answer. The exact configured target was
vision-enabled only in the test's in-memory profile. Saved profile data,
credentials, default target and persistent image-input selection were not
changed by the probes. No device screenshot or personal image was uploaded.

| Probe | Result | Session time | Input / output tokens | Tool calls | Output |
| --- | --- | --- | --- | --- | --- |
| Initial image | Passed, instrumentation OK (1 test) | 17263 ms | 138 / 6 | 0 | Six characters, exact image match |
| Native tool-result image | Passed, instrumentation OK (1 test) | 26480 ms | 308 / 39 | 1 | Six characters, exact image match |

Both runs kept the 1024-token maximum and the existing bounded deadline.
The second probe first requested `observe_image`, submitted the image as
that call's result, then received the matching final answer. Its reported
usage covers the native conversation; 39 output tokens are not claimed to
be the length of the six-character final text. These are bounded real HTTP
probes, not repeated samples selected for success.

The probe uses the real credential repository, network policy, HTTP backend
and Provider session, with a test-only same-UID owner verifier. It closes
the existing P9.2 Provider model gate requiring both image paths. It is not
a new real host-to-Agent cross-UID visual task, does not establish every
protocol or every model's support, and does not turn on image input for
future user tasks. The Agent integration and cross-UID deterministic tests
were completed separately in the existing Agent implementation receipt.
The original Model8 empty-response failures remain failures.

The initial attempt to run the test against the previously installed R8
build 216 crashed before a model probe. It is retained as an instrumentation
setup failure, not counted as model acceptance or folded into the two
successful 218 probe results.

### Matched build and archive

Debug, androidTest, R8 release and signed archive tasks completed successfully
in 3m 3s, with 126 actionable tasks and 16 KiB native alignment verified for
Debug and Release. The archive records the exact clean source above with
`sourceDirty=false`. The tests and all app APKs use the official certificate
SHA-256 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`.

| Artifact | SHA-256 |
| --- | --- |
| Debug arm64 / 218 | `d44607b11f51eaf760397d03ed48fbf5be460f424bd7ac9d657263ad31e19cc7` |
| Matching androidTest | `0261fcd30f98f562267e4272a5892f6f00b263f35954669949801301475fb927` |
| R8 arm64 / 218, CRC32 A931BB09 | `cad1df45c6bcd79fc3de635a6eadada305689c809318484064e99a8dac071bc5` |

An initial Windows command launch error, native filesystem-enumeration stall
and resulting cache-lock failure are preserved in ignored build records.
The successful build used a separate Gradle home and project cache with
file watching and Gradle native services disabled; the native-service
property was verified from the installed Gradle bytecode. No shared locks
were deleted or unrelated build processes terminated. These setup failures
are separate from the successful APK build and model probes.

### Documentation follow-up and build identity

The current documentation update prepares Provider 1.2.0 / build 220 after
`40ac023` / build 219, which separately preserves fixed online failure
categories. Neither build 219 nor the documentation build 220 is the build
used for the successful vision probes; those results belong to build 218.
This update changes no runtime or public API. Ten-language README and
current changelog text now state the implemented Agent requirements and
the verified exact target, without claiming universal visual support.

Agent screenshots require Android 11+, a compatible AutoJs6 host and AI
Agent, the observe tool group, and image input explicitly enabled for the
exact selected model. The successful probes did not persist that opt-in.
Existing profiles remain disabled for image input unless selected by the
user; LiteRT and persistent `ai.session` remain text-only. The completed
Provider model gate requires no additional device, SIM or full Agent
screenshot task. The independent P9.1 Wi-Fi gate is recorded in the Agent
roadmap.

The build 220 documentation package passed generation, the read-only
Markdown check and the pending-commit repository check. Its R8 release and
signed three-ABI archive completed in 5m 33s (56 actionable tasks: 19 executed,
37 up-to-date), including release native 16 KiB alignment. The arm64 archive
has CRC32 `838FF3D1`, 24,658,606 bytes and SHA-256
`408312dab105422949260e8b9aa3f6c322825d8b6b1024665ec481e0eae06db5`.
The manifest correctly records parent `40ac023` with `sourceDirty=true` for
this documentation/version update, not a clean build of the earlier commit.
Runtime suites were not repeated for this documentation-only update; build
219 runtime validation remains separately recorded. No device installation
or online generation was performed as part of producing build 220.
