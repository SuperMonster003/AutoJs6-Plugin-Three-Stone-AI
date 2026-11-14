# 1.7.0 release gate (2026-11-14)

3-Stone AI 1.7.0 release, roadmap 1.6.0 P9.1 / P9.2 (release card 3-stone-p9-1-7-0). The gate follows P8.2 (1.6.0): unit tests in a clean clone, instrumentation on XQ-DQ72, a signed release build in a clean clone, release smoke on XQ-DQ72 and OnePlus 63751f63, then the GitHub Release and the official index entry.

XQ-DQ72 (QV770340J7, reached through remote adb) dropped off the remote adb server at about 15:3x, before anything was installed on it, and had not come back by 16:2x. The maintainer then decided in the release card's conversation: publish now, test QV770340J7 afterwards, and ship a fix update if that finds a problem. So the Release and the index entry went out after the OnePlus smoke, and the XQ-DQ72 part of the gate follows below once the phone is back.

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
- XQ-DQ72: pending, see the next section.

## XQ-DQ72 (after the release)

Pending until QV770340J7 is back on the remote adb server. The plan, unchanged from P8.2:

1. Instrumentation with `.python/device_instrumentation_gate.py` (new in this card): a snapshot of the installed debug 350 was taken before anything else (`before350`); the release-day debug APK 353 and its test APK, both signed with the release certificate, are installed over it, the 22 classes that can run on a phone run one by one, and the stored services, conversation history and model picker preferences are compared with the snapshot afterwards. The test package is always uninstalled. The script refuses to install when either APK is signed with the Android debug key or the two signers differ: the first install attempt at 15:26 failed with INSTALL_FAILED_UPDATE_INCOMPATIBLE because the clone's debug APKs had been built before `sign.properties` was copied in; the phone was not changed by it.
2. `device_engine_caches.py --expect-no-orphans`.
3. Release smoke with the arm64-v8a APK, recording `firstInstallTime` before and after.
4. One chat request on the release build with AIGoCode (Codex) gpt-6.1-sol through `device_chat_probe.py`; no Anthropic request.

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
- XQ-DQ72 QV770340J7: still the debug 350 it had before this card (nothing was installed); updated after the XQ-DQ72 part above.
