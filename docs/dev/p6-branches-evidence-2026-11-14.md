# Engine caches, emulator timeouts and message branches (2026-11-14)

3-Stone AI 1.7.0 development, roadmap 1.6.0 P6 (P6.0, P6.4, P6.1 - P6.3; D82, D143, D144, D146 onwards). No release, VERSION_NAME stays 1.6.0 until the 1.7.0 release card.

## P6.0 Engine caches of deleted models (D143, D146)

### What LiteRT-LM writes

LiteRT-LM 0.15.0 (`runtime/executor/executor_settings_base.cc`, the version in `app/build.gradle.kts`) names every cache file it writes into the cache directory after the model file:

`<basename of the model file>_<model mtime in seconds>_<model size in bytes>[<sub-model infix>]<suffix>`

with the suffixes `.xnnpack_cache` (CPU weight cache), `_mldrift_program_cache.bin` and `_mldrift_weight_cache.bin` (GPU) and `.mtp_drafter`. The library itself only replaces caches of the same basename whose mtime or size no longer match; a cache whose model file is gone stays forever. Managed model files are content addressed (`files/models/model-<sha256>.litertlm`), so the basename identifies the model without reading anything else.

Emulator check (AVD_API_33 read-only instance emulator-5972, x86_64): `.python/run_real_model_test.py` staged Qwen2.5 1.5B (1,597,931,520 bytes) and ran one CPU inference ("Hello tester! How's everything going?", 4.7 s). `cache/litertlm` then held exactly one file, 1.55 GB, named after the model file with the model size 1597931520 embedded; `.python/device_engine_caches.py` classified it "keep".

### What the plugin now does (D146)

- `backend/LiteRtLmEngineCaches.kt`: `LiteRtLmEngineCachePolicy` maps a cache name to its owner (`^(model-[0-9a-f]{64}\.litertlm)[._].+$`); a name that does not match is never touched. `LiteRtLmEngineCacheStore` deletes only regular files (lstat, links are not followed), at most 4,096 directory entries, and leaves a cache written within the last 10 minutes for a later sweep.
- Retained models are the catalog entries plus every name in `files/models` (`ModelRepository.retainedModelFileNames()`), so a model that is present, still being imported, loading or generating keeps its caches. Unlinking a cache that an engine has mapped does not disturb it.
- `ModelImportCoordinator` runs all of it on the single manager worker: deleting a model deletes its caches after the model file is gone; "Clean unreferenced files" also removes orphan caches and counts their files and bytes in its result; the first use of the manager in the main process (opening the chat or "Models and services") sweeps orphans once. Failures are only logged by exception type and never turn a deletion or a cleanup into a failure.
- The changelog of v1.7.0 has the fix in 10 languages; `model_cleanup_empty` in 11 resource directories now mentions engine caches.

### Tests

- `:app:testDebugUnitTest`: 664 tests, all passing (P4 baseline 654, 10 new); `:app:lintDebug`: 0 errors (`java.nio.file` is not used in main code because minSdk is 24; the store checks entries with `Os.lstat`).

- `LiteRtLmEngineCachesTest` (JVM, 10 tests): the owner mapping for every suffix and sub-model infix, names that must not match (other basenames, upper-case or short digests, the model file itself), orphans versus retained models, the 10-minute grace period, directories and links left alone, a missing directory that is not created, and the cleanup path being the directory the engine writes to.
- `.python/test_device_engine_caches.py` (4 tests) for the read-only device classifier.
- `EngineCacheCleanupDeviceTest` on emulator-5972, 1 / 1 PASS, through the real coordinator with two staged fake models: "Clean unreferenced files" removes the cache of a model that is gone and counts it; deleting a model removes its CPU and GPU caches but leaves a cache written a second ago; caches of the remaining model and an unrelated file stay.
- Real cache on emulator-5972: after the inference above, the catalog entry and the model file were removed the way 1.6.0 deleted a model (cache left behind) and the cache was dated one hour back; `.python/device_engine_caches.py` then reported "orphans: 1 files, 1.55 GB". Launching the plugin (cold start of the main process) removed it within 5 s: "0 entries in cache/litertlm, orphans: 0 files".

## P6.4 Two instrumentation tests that timed out on the emulator (D144, D147)

`ChatReasoningCardDeviceTest.theCardStreamsCollapsesExpandsAndSurvivesReopening` ("the card streams the reasoning") and `SpeechReadingDeviceTest.repliesAreReadWhileTheyStream` ("reading starts") timed out on the AVD_API_33 read-only instance, also on the baseline before P4 (703ba9e).

### Cause: the waits, on a slow emulator

- Both waits look for a short-lived state. With the scripted backend (three reasoning chunks of 400 ms) the card says "Thinking..." for about 1.2 s; reading lasts only as long as the scripted reply streams (four chunks of 250 ms), about 0.3 - 1.4 s.
- `waitUntil` polls with `waitForIdleSync` followed by `onActivity`. While a reply is generating, the meta line under it pulses (`ChatActivity.setMetaPulsing`, a 750 ms ValueAnimator with infinite repeats), and the emulator renders in software, so the main thread rarely goes idle: a probe test (not committed) measured 0.4 - 1.3 s per `waitForIdleSync` during generation, 0 - 60 ms with animations off. One or two polls are enough to miss the whole window. The probe saw "Thinking..." at 973 ms and "Thought for 1 s" at 1,896 ms; reading started at 883 ms and was over at 1,453 ms, with the three sentences queued at 339, 869 and 1,201 ms.
- With `animator_duration_scale 0` the unchanged tests pass, and the timeline the probe recorded is what the app should do, so the app's timing is correct. The chat also saves the history on the main thread every 750 ms while a reply streams (`persistConversationRunnable`); that existed before and is not changed here.

### Fix (tests only)

- `ScriptedAiBackend.pauseBeforeReply` keeps a turn after its reasoning chunks until the test calls `continueReply()` (or `release()`); `ChatReasoningCardDeviceTest` sets it and continues once it has seen the streaming card. The check that the thinking time is between 1 and 10 s is unchanged (it now includes the wait for the check, typically 1 - 2 s).
- `SpeechReadingDeviceTest.repliesAreReadWhileTheyStream` sets `FakeSpeechEngine.hold`, so the first sentence keeps being read until the test has checked the reading state and taken its screenshot, then releases it.
- No assertion removed and no wait limit raised. With animations on, both classes (8 tests) passed in 5 of 5 runs on emulator-5972.

### XQ-DQ72

The two files the P4 card left in `cache/litertlm` (2.21 GB from 2026-10-17, 1.55 GB from 2026-11-13 14:28) are checked with `.python/device_engine_caches.py` before and after the install of the P6 build (see the XQ-DQ72 section below).

## P6.1 - P6.3 Message versions (D82, D148)

### Model

- A turn is a user message and everything after it on the shown path. Regenerating a reply or sending an edited message adds a version of the turn; nothing is deleted. The versions live on the user message that starts the shown version (`ChatMessageBranches(selected, others)` in `ChatBranches.kt`); the shown version is `selected + 1` of `others.size + 1`, a new version is appended last and selected, and its user message gets a new ID. Every other version starts with a user message, and a turn inside another version may branch again (nested versions).
- The shown path stays the plain message list, so search, export, context compilation, reading aloud, the usage line and the reasoning card follow the selected versions without knowing about branches (D148 (e)).
- Deleting the user message of a turn with several versions asks "Delete this version?" and removes only the shown version with its later messages; the version before it is shown (the next one when the first is deleted). With a single version, and for any later message, deleting works as before (the message and everything after it on the shown path); the other versions of earlier turns stay.
- At most 8 versions per turn (`MAXIMUM_VERSIONS`): regenerating or editing a turn that has 8 shows "This message already has 8 versions, the maximum. ..." and changes nothing. The other versions of one conversation may take about 2 MiB together (`MAXIMUM_CONVERSATION_BRANCH_BYTES`, estimated as in the history file); beyond that the oldest other version of the earliest branched turn is dropped first, and the shown path is never changed.
- Regenerating no longer warns about later messages (they stay as a version); the warning about a changed destination (cloud / local) stays. Editing no longer warns at all.

### Storage (v6 properties, no format bump)

- Header key `branch` on the turn's user message: format 1, selected index, number of chunks. The other versions are encoded with the normal message codec (nested versions included, at most 32 levels) and split into `branch.00`, `branch.01`, ... of at most 64 KiB each, at most 48 chunks (3 MiB), inside the limits of D91 (64 entries, keys up to 32 bytes, values up to 64 KiB).
- Reading fails soft: a damaged or unknown branch block leaves the shown path alone (the versions are dropped). `ChatBranchPolicy.sanitized` runs on every read: another version must start with a user message, IDs must increase and must not repeat anywhere in the conversation, versions that break this are dropped; a hidden reply still marked as generating becomes stopped.
- New message IDs come from the largest ID of all versions, so a version never reuses an ID.

### Interface

- "< 2/3 >" (`ChatBranchSwitchView`) sits under the first reply of the shown version (under the user message when no reply follows it). The arrows switch the version together with all later messages and keep the turn where it was on screen; the choice is stored and survives reopening. Content descriptions "Previous version", "Next version", "Version 2 of 3".
- While a reply is generating or a message is being edited, the arrows are disabled. Deleting a version while generating stops the generation first.

### Boundaries (D148)

- (a) Attachments: a regenerated or edited version keeps the attachments of its user message (same files). The storage cleanup and the conversation's file sweep count the files of every version (`ChatBranchPolicy.attachmentFileNames`), so a file is deleted only when no version refers to it any more, for example after deleting the last version that used it.
- (b) Summary checkpoints and context: summaries describe the shown path only. Adding, switching or deleting a version invalidates the context from the smallest message ID of any version of that turn (`firstIdOfTurn`): the running summary is cancelled, the summary failure count is reset and `ConversationContextPolicy.invalidateFrom` drops the checkpoints from there on. The next request compiles the shown path only.
- (c) Online persistent sessions (D137) and the generation coordinator (D77): adding, switching and deleting a version close the current backend session, so the next request rebuilds it from the shown path and never reuses a session that saw another version. The coordinator still owns one generation; versions cannot be switched while it runs.
- (d) Older builds: see "Downgrade check" below.
- (e) The reasoning card, the usage line and reading aloud belong to the reply they were recorded on, so they switch with the version. Reading stops when the message being read leaves the shown path.

### Tests

- `ChatBranchPolicyTest` (JVM, 9 tests): a new version keeps the shown one and becomes the selected last version; 8 versions at most; switching and switching back; nested versions stay with their version; deleting the shown version shows the one before it (the next for the first); where the switch sits; attachments of every version counted once; sanitizing damaged versions and hidden generating replies; fitting to the byte limit.
- `ConversationHistoryTest` (4 new): codec round trip of nested versions and the selection; 64 KiB chunks keeping the newest versions that fit; a file whose branch keys cannot be read shows only the selected version; at most 2 MiB of other versions per conversation.
- `:app:testDebugUnitTest`: 676 tests (664 after P6.0, 13 new, 1 removed with the edit warning), all passing; `:app:lintDebug`: 0 errors; `ApplicationTextPunctuationTest` passes (part of the unit tests).
- `ChatBranchDeviceTest` on emulator-5972 (scripted backend), 2 / 2 PASS: regenerating twice gives 3/3; switching back to 1/3 draws the first reply with its later messages again (the hidden reply is not drawn, the IDs are the original ones); the next turn replays only the shown version; the choice survives reopening; deleting the first version shows the next one (1/2); editing and sending adds a version (3/3) whose user message has a new ID; a turn stops at 8 versions with the notice, and neither regenerating nor editing starts after that.
- `ChatBranchRealDeviceFlowTest` (opt-in, `.python/device_branches_flow.py`) for the real-device step of P6.3, see "XQ-DQ72" below.

## Downgrade check (D148 (d))

`.python/device_branch_compat.py` on emulator-5972 (AVD_API_33 read-only instance) with three builds: this checkout (debug, versionCode 344 at the time), the debug build 339 that XQ-DQ72 had (rebuilt from 5b99a44 in a clone, signed like the installed one) and the 1.6.0 release (versionCode 320). `ChatBranchCompatibilityProbe` seeds a conversation whose first turn has three versions (the third shown, the first holding the only attachment, 2 KiB) and a plain conversation, and reads them back; the script installs the older build over the data, opens the chat, compares the history file before and after, and installs this checkout again. Final run: PASS.

| Step | Result |
| --- | --- |
| Seed with this checkout | 3 versions, the third shown, IDs 1 - 8, hidden attachment present |
| 339: open the chat | shows Q, A3; no switch |
| 339: open and leave the chat | history file rewritten (`onStop` saves the conversation) |
| 339: storage cleanup | 1 file, 2,048 bytes deleted (the attachment only the hidden version used) |
| 339: send a message | context replays Q, A3; history rewritten |
| This checkout after 339 | 1 version (IDs 7 - 10), hidden attachment gone, plain conversation intact |
| 1.6.0 release: open the chat | shows Q, A3 (screenshot); history file rewritten while the chat is shown |
| 1.6.0 release: leave the chat | history file unchanged |
| This checkout after the release | 1 version (IDs 7, 8); the attachment file is still there until a cleanup |

So an older build shows the version that was selected and, as soon as it saves the history (opening the chat page is enough), drops the other versions of every conversation, as D81 / D91 say for every unknown key. Upgrading again does not bring them back. The changelog entry says so in 10 languages.

## XQ-DQ72

Pending: the install of this build over debug 339 needs the maintainer's consent first. Planned: `.python/device_branches_flow.py --snapshot before`, the install (overwrite only, data kept), `--request "AIGoCode (Codex)" gpt-6.1-sol` (three real requests), `--compare before`, and `.python/device_engine_caches.py` before and after for the two files in `cache/litertlm`.
