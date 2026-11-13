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
