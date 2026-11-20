# 3-Stone AI 1.7.2: release history page in Compose (AutoJs6 plugin-center P21.9)

Date: 2026-11-20. Source: AutoJs6 host plugin-center phase 3, P21.9 (T60 release history page, T62 instruction, T63 release, Q117 A, Q160 A), board `.claude/boards/plugin-center.md`. The page follows the workspace spec `AUTOJS6_PLUGIN_RELEASE_HISTORY_AGENTS.md` and its model page in 3-Maple Image 3.5.1.

## Commits

- `cfcdbdc` docs(instruction): the plugin instruction rewritten in the new style for all ten languages (each file under 6 KB).
- `97533c7` feat(release-history): the release history page rebuilt in Jetpack Compose (BOM 2026.09.00, activity-compose 1.12.2, shared build plugins 1.9.0 -> 1.9.7), links in release notes shown as their text and opened on tap (Q160 A), the 1.7.2 changelog in ten languages, VERSION_BUILD 362. The same commit lets the release archive and the CI APK contract accept `libandroidx.graphics.path.so` (see below).

## Verification

- JVM unit tests: 707 / 707 (`ReleaseHistoryTest` covers locales, fallbacks, the changelog parser, category groups and counts, the bundled changelogs and four link cases). `.python` tests of the release archive and the APK contract: 20 / 20.
- Device tests on a temporary API 33 phone AVD: 12 / 12 (title and subtitle, expand / collapse per release and for all, the "Updates and about" row, the update dialog, keyboard order, links show only their text and a tap opens the URL).
- Screenshots (kept with the board notes, not in the repository): phone zh-CN / en-US / ar x light / dark, all collapsed, the settings row, links; tablet (pixel_tablet 2560 x 1600) zh-CN / en-US x light / dark. Arabic is laid out right to left; the page title starts where the settings page title starts.
- `D:/idea-projects/prepush_check.py`: 8 / 8 (clean worktree, build number drift 0, generated documentation, icon resources, repository contract, both Python test sets, `:app:testDebugUnitTest`).
- `:app:lintDebug`: 0 errors. Of the 144 warnings with a source line, two come from the 1.7.2 commits, both `GradleDependency` hints on the pinned Compose versions (activity-compose 1.12.2, Compose BOM 2026.09.00, the versions the other Compose plugins use).

## Native libraries of the 32-bit APKs

Compose ui-graphics depends on `androidx.graphics:graphics-path`, which packages `libandroidx.graphics.path.so` for all four ABIs. The first release build therefore failed in `appendDigestToReleasedFiles`: the release archive only allowed native libraries in the 64-bit APKs (LiteRT-LM 0.15.0 is 64-bit only). `anyAbiNativeLibraries` in `app/build.gradle.kts` now names exactly this library; `.python/release_archive.py` and `.github/scripts/verify_apk_contract.py` require it to cover exactly the ABIs of each APK, while the LiteRT-LM runtime still may appear only in arm64-v8a, x86_64 and the universal APK. Consequence: the armeabi-v7a and x86 APKs are no longer Java-only and no longer byte-identical.

## Release build

`:app:lintDebug :app:appendDigestToReleasedFiles` on `97533c7`, run through the workspace build gate on the build worker (the local worktree was clean when the build was queued; the outputs were copied back). release-manifest.json: sourceRevision `97533c720dda2785eb50ce217d6c8f17e0e594fe`, versionCode 362 for all five APKs. Its `sourceDirty` is true because `git status` ran in the worker's mirror, where the 172 files that this machine checks out with CRLF (system-wide `core.autocrlf=true`) and the mirror's marker file show up as changes; the 15 assets of the universal APK that come from the repository are byte-identical to `97533c7`.

| File | Bytes | 1.7.1 | Change | SHA-256 (first 12) |
| --- | ---: | ---: | ---: | --- |
| autojs6-plugin-three-stone-ai-v1.7.2-universal-3328D144.apk | 51116375 | 50316258 | +800117 (+1.59%) | efc4715d5c06 |
| autojs6-plugin-three-stone-ai-v1.7.2-arm64-v8a-8350B3AF.apk | 25843235 | 25092527 | +750708 (+2.99%) | 017f27f4ce81 |
| autojs6-plugin-three-stone-ai-v1.7.2-x86_64-F2B8298B.apk | 29865989 | 29115284 | +750705 (+2.58%) | a1f42a9da984 |
| autojs6-plugin-three-stone-ai-v1.7.2-armeabi-v7a-B44E6D05.apk | 4634771 | 3882317 | +752454 (+19.38%) | 0cbadd86a016 |
| autojs6-plugin-three-stone-ai-v1.7.2-x86-6285A6AD.apk | 4636795 | 3882317 | +754478 (+19.43%) | e7af4cc285e1 |

- The growth is Compose (code and the graphics-path library); the native runtime is unchanged.
- `SUPPORTED_ABIS` meta-data of all five APKs: `arm64-v8a,armeabi-v7a,x86_64,x86`. Signer certificate SHA-256 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`, the same as 1.7.1.
- `SHA256SUMS` in the 1.7.1 format (`<sha256>  <file>`, LF).

## 16 KB page size smoke

`cold_start_16k.py` (board notes tool of P21.8) on AVD_API_37.1_16K (read-only, emulator-5736; started on this machine because the build worker's 16 KB AVD stayed offline in adb), x86_64 release APK:

- PASS 16 KB page size: 16384
- PASS install
- PASS launcher cold start: TotalTime 1476 ms
- PASS release history page cold start: TotalTime 1364 ms, page drawn
- PASS process alive
- The tool's logcat check matched one line: nativeloader's debug message `dlopen failed: library "libOpenCL.so" not found`. It is the GPU availability probe of `LiteRtLmBackendProfiles` (unchanged since 1.2.1), which is expected to fail on an emulator without OpenCL; there was no crash, `UnsatisfiedLinkError` or failure to load a packaged library.

The AVD was closed right after the run.

## Release 1.7.2

The maintainer approved pushing, releasing and the index entry through a multiple-choice question in the P21.9 session (D27: 3-Stone AI asks before each push).

- Pushed `6b23421..97533c7` to master (`cfcdbdc`, `97533c7`). CI on `97533c7`: Build and plugin contract (run 44731834879), Documentation (run 44731834915), Icon geometry and Icon Studio recipe passed.
- Annotated tag `v1.7.2` ("Release 1.7.2") on `97533c720dda2785eb50ce217d6c8f17e0e594fe`, pushed after CI.
- GitHub Release `1.7.2` on tag `v1.7.2`, published 2026-11-20 12:24:43 UTC and marked Latest (`/releases/latest` returns `v1.7.2`). Body in the 1.7.1 format: the source commit and the five changelog entries. Assets: the five APKs and `SHA256SUMS` (623 bytes); GitHub's size and SHA-256 digest of every asset match the local files, and `sha256sum -c` passed on the downloaded assets.
- Official index: `release-manifests/io.github.supermonster003.autojs6.plugin.threestoneai/362.json` in AutoJs6-Official-Plugins-Index (releaseTag v1.7.2, sourceCommit `97533c7`, versionCode 362, the release signer, five artifacts, supportedAbis arm64-v8a, armeabi-v7a, x86_64, x86). `tools/generate_official_plugin_index.py` (authenticated) changed only the 3-Stone AI and 3-Maple Image entries; the generator measured 16 KB page alignment for all five APKs. Index commit `2f525bc` chore(index): publish 3-Maple Image 3.5.1 and 3-Stone AI 1.7.2, pushed to main without conflicts.
