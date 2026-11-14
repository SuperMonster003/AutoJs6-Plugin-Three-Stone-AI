# 1.7.0 release gate (2026-11-14)

3-Stone AI 1.7.0 release, roadmap 1.6.0 P9.1 / P9.2 (release card 3-stone-p9-1-7-0). The gate follows P8.2 (1.6.0): unit tests in a clean clone, instrumentation on XQ-DQ72, a signed release build in a clean clone, release smoke on XQ-DQ72 and OnePlus 63751f63, then the GitHub Release and the official index entry.

XQ-DQ72 (QV770340J7, reached through remote adb) dropped off the remote adb server at about 15:3x, before anything was installed on it, and had not come back by 16:2x. The maintainer then decided in the release card's conversation: publish now, test QV770340J7 afterwards, and ship a fix update if that finds a problem. So the Release and the index entry went out after the OnePlus smoke. The phone was back on the remote adb server at 17:13, and the XQ-DQ72 part of the gate ran from 17:14 to 17:37 (below). It found no problem in the app, so no fix update is needed: the five failures of the first run were test defects, fixed in `12d0829` (test code and the gate script only).

## Commits and tag

- `64258fc` docs(release): prepare the 1.7.0 changelog and README (P9.1). VERSION_NAME 1.7.0, VERSION_BUILD 352.
- `673620a` chore(release): set the 1.7.0 release date. released_date 2026/11/14 in all ten changelog languages, documents regenerated, VERSION_BUILD 353.
- Annotated tag `v1.7.0` ("Release 1.7.0") on `673620ab151215dc40fe8484f5e434c719a422e4`, pushed 2026-11-14 08:26 UTC (16:26 +08:00). The release APKs carry versionCode 353.
- CI on `673620a`: Documentation (run 43570438406) and Build and plugin contract (run 43570438410) passed; both also passed on `64258fc`. The tag push started Icon geometry (run 43575834571) and Icon Studio recipe (run 43575834613); both passed.

## Unit tests

Clean clone of `673620a` in `E:/.cache/stone-p9-170` (ignored `local.properties`, `sign.properties` and `app/sm003.jks` copied in, `git status` clean): `:app:testDebugUnitTest --rerun` ran 689 tests in 110 result files, 0 failures, 0 errors, 0 skipped (2 min 46 s). The baseline from P7 was 689.

## Release build

The same clone, `sourceDirty` false: `:app:appendDigestToReleasedFiles` (5 min 15 s). `release_archive.py` verified every APK against the configured signer and refuses the Android debug certificate.

| File | Bytes | SHA-256 (first 12) |
| --- | ---: | --- |
| autojs6-plugin-three-stone-ai-v1.7.0-universal-FCF72938.apk | 50232412 | 98b681887162 |
| autojs6-plugin-three-stone-ai-v1.7.0-arm64-v8a-0437DBBC.apk | 25008681 | 88bb6c0d9074 |
| autojs6-plugin-three-stone-ai-v1.7.0-x86_64-7153B6E5.apk | 29031438 | 2cfed41bd527 |
| autojs6-plugin-three-stone-ai-v1.7.0-armeabi-v7a-48A03D39.apk | 3807587 | 9f714bf4fb6b |
| autojs6-plugin-three-stone-ai-v1.7.0-x86-48A03D39.apk | 3807587 | 9f714bf4fb6b |
| SHA256SUMS | 623 | fcba3988ac31 |

- release-manifest.json: sourceRevision `673620ab151215dc40fe8484f5e434c719a422e4`, sourceDirty false, versionCode 353 for all five.
- The armeabi-v7a and x86 APKs carry no native libraries and are byte-identical, as in 1.5.0 and 1.6.0.
- Signer certificate SHA-256 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` (CN=SM003), the same as the published 1.6.0 armeabi-v7a APK (checked with apksigner) and the 1.4.1 / 1.5.0 release manifests.
- `SHA256SUMS` was written from the release manifest in the 1.6.0 format (`<sha256>  <file>`, LF); `sha256sum -c` passed for all five.

## Release smoke

`device_release_smoke.py` from the clean clone, without `--force`. It checks the APK against the release manifest, installs it with `adb install -r`, checks versionCode and ABI, then runs `ReleaseAbiSmokeInstrumentation` from the `-PreleaseAbiSmoke=true` test APK (built in the same clone, signer `31a681fcfffb`) and uninstalls the test APK.

- OnePlus 63751f63 (OPD2413, Android 15, ColorOS), 2026-11-14 16:1x - 16:2x: the official 1.6.0 APK (versionCode 320) was replaced by the arm64-v8a 1.7.0 APK: `PASS install versionCode=353 primaryCpuAbi=arm64-v8a`, `PASS smoke releaseAbiSmoke=passed process64=true pageSize=4096`. `firstInstallTime` stayed 2026-09-29 10:26:36, so the data was kept. On the first run ColorOS held the test APK behind its install confirmation (`com.oplus.appdetail` InstallGuideActivity, source "from computer") until the 600 s adb timeout; the maintainer confirmed it on the phone and the second run passed. The test package is uninstalled and `/data/local/tmp` holds nothing from this run.
- XQ-DQ72 QV770340J7 (Android 13), 2026-11-14 17:31 - 17:32, after the instrumentation below: the release-day debug APK 353 was replaced by the arm64-v8a 1.7.0 APK (same signer, same versionCode, `adb install -r` without other flags): `PASS sha256`, `PASS install versionCode=353 primaryCpuAbi=arm64-v8a`, `PASS smoke releaseAbiSmoke=passed process64=true pageSize=4096`. The launcher was in front, so the BUSY check passed without `--force`. `firstInstallTime` stayed 2026-10-17 16:51:11 (before and after), so the data was kept; dumpsys no longer lists the DEBUGGABLE flag. The test package is uninstalled.

## XQ-DQ72 (after the release)

The plan was unchanged from P8.2: instrumentation, engine caches, release smoke, one chat request on the release build. All four passed. The system locale of the phone is en-US and the AutoJs6 host language is zh.

1. Instrumentation with `.python/device_instrumentation_gate.py` (new in this card). A snapshot of the installed debug 350 was taken at 15:25, before anything else (`before350`). The release-day debug APK 353 and its test APK, both signed with the release certificate, were installed over it at 17:14 (data kept). The 22 classes that can run on a phone then ran one by one (17:14 - 17:17), and the stored state was compared with the snapshot afterwards. The test package is always uninstalled. The script refuses to install when either APK is signed with the Android debug key or the two signers differ: the first install attempt at 15:26 failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE because the clone's debug APKs had been built before `sign.properties` was copied in; the phone was not changed by it.
   - First run: 83 passed, 5 failed, 1 skipped (ChatExportDeviceTest.theSystemPickerSavesAFileAnotherAppCanOpen, opt-in with `-e systemPickers true` and emulator only). The failures were in SettingsNavigationTest (1), ServiceDetailDeviceTest (3) and ModelPickerSheetDeviceTest (1). All five read their expected texts through the instrumentation context, which follows the system locale (English), while the pages follow the host language (Chinese), so the texts never matched. The app shows the right texts; the tests looked for the wrong ones. In P5 these classes had passed on the AVD_API_33 emulator.
   - The comparison also showed `model_picker` changed: ServiceDetailDeviceTest put the favorites back through `ModelPickerFavoritesStore.toggle()`, which uses `apply()`, and the process ended before the write. The phone kept the favorite of the deleted test service. A digest search against `before350` showed that removing that one ID restored the file exactly. It was written back through run-as while the plugin was not running, and the digest then matched.
   - Fix (`12d0829`, test code only): the three classes read the texts through the activity, the sheet's context or `AppConfiguration.wrap()`. The favorites restore ends with a synchronous `commit()`. Rebuilt in the clean clone, the three classes then passed (10 of 10). That rerun exposed one more leftover: saving a service records its base URL in the address history (`online-ai-base-url-history`), and the test did not put it back. The phone's history was restored the same way (removing the test URL gave back `before350` exactly). ServiceDetailDeviceTest now saves the history before each test and writes it back with `commit()` afterwards.
   - Second rerun of the three classes (17:30): 10 passed, 0 failed, and every compared item is the same as `before350`: services (content; the document was rewritten with a higher revision), conversation history, default target, local model catalog, files/models, attachments, and all nine preference files. The overall result for the 22 classes is 88 passed, 0 failed, 1 skipped. No test reports or test documents were left.
   - The gate script now also refuses `--snapshot` / `--compare` when run-as cannot reach the app. On the release install every item read as gone in a snapshot taken after the smoke; that snapshot was deleted, and the release build is checked through `firstInstallTime` instead.
2. `device_engine_caches.py --expect-no-orphans` (17:31): 1 catalog entry, 1 model file, 1 CPU cache of 2.21 GB kept, orphans 0 files.
3. Release smoke with the arm64-v8a APK: see "Release smoke" above.
4. One chat request on the release build (17:37) through `device_chat_probe.py`: a new conversation switched to AIGoCode (Codex) | gpt-6.1-sol, prompt "PING", outcome complete after 7 s, input 4223 / output 6 tokens, reply "PONG". No Anthropic request was sent. A first attempt at 17:36 with a prompt containing spaces sent nothing: Gboard (pinyin layout) dropped the spaces, the script read back a different text, cleared the field and stopped before Send.

Classes left out of the phone run and why (`device_instrumentation_gate.py --list`): AppearanceSettingsTest and the LauncherIcon{ApplyAuto,Migration,PrepareDark,PrepareUpgrade,Selection}Test classes change the appearance or the launcher alias of the installed app (emulator only); ModelsAndServicesScreenshotTest takes screenshots for evidence (emulator); ChatAttachmentPickerEmulatorTest presses the system pickers (emulator only, D131); EngineCacheCleanupDeviceTest needs an empty model catalog; ChatAttachmentRealDeviceFlowTest, ChatBranchRealDeviceFlowTest, ModelsAndServicesRealDeviceFlowTest, OnlineVisionAcceptanceAndroidTest and SpeechConversationRealDeviceTest send real AI requests (opt-in); LiteRtRealModelInferenceInstrumentationTest needs a real local model (opt-in); SpeechEngineLatencyDeviceTest plays sound to measure latency; SpeechManualDeviceTest waits for a person to speak; ReleaseAbiSmokeInstrumentation has its own runner (`device_release_smoke.py`); ChatBranchCompatibilityProbe belongs to `device_branch_compat.py`.

## GitHub Release

- Release `1.7.0` on tag `v1.7.0`, published 2026-11-14 08:27 UTC (16:27 +08:00) and marked Latest (`/releases/latest` returns `v1.7.0`). Body in the 1.6.0 format: the source commit, then the 12 changelog entries of v1.7.0 from CHANGELOG-en.md (4 Feature, 2 Fix, 6 Improvement), then the pointer to `SHA256SUMS`.
- Assets: the five APKs and `SHA256SUMS`. Size and GitHub's SHA-256 digest of every asset were compared with the local files: all six match.

## Official index

- `release-manifests/io.github.supermonster003.autojs6.plugin.threestoneai/353.json` in AutoJs6-Official-Plugins-Index (releaseTag v1.7.0, sourceCommit `673620a`, versionCode 353, the release signer, the five APKs with SHA-256 and size, ABIs arm64-v8a / armeabi-v7a / x86_64 / x86), written from the release manifest with every digest recomputed from the files.
- `tools/generate_official_plugin_index.py` regenerated `plugins.official.generated.json`: of 84 entries only the 3-Stone AI entry changed (release, assets, instruction URLs on the v1.7.0 tag), top-level fields and entry order unchanged. The index tool unit tests passed (81 tests), `git diff --check` clean.
- Index commit `14af0d0` feat(index): admit 3-Stone AI v1.7.0, pushed to main without conflicts.

## State left on the devices

- OnePlus 63751f63: 3-Stone AI official 1.7.0 (versionCode 353, arm64-v8a), data kept; no test package. The host and other plugins were not touched.
- XQ-DQ72 QV770340J7: 3-Stone AI official 1.7.0 (versionCode 353, arm64-v8a, not debuggable), data kept (`firstInstallTime` 2026-10-17 16:51:11). The data matched the debug 350 snapshot after the instrumentation, apart from the two test leftovers that were restored. No test package. The probe's dump file on `/sdcard` was removed by the script, and `/data/local/tmp` holds nothing from this card. The screen was left on the plugin's chat page from the chat request (Home was not pressed). The host and other plugins were not touched.
