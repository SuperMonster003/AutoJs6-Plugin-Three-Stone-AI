# IDEA Run native alignment verification

Validated on 2026-09-26 for the unpublished 1.2.0 candidate, build 221.

## Cause and fix

The existing IDEA `app` run configuration reproduced `No APK found for 'Debug'`.
AGP had produced the device-specific APK and `output-metadata.json` under
`app/build/intermediates/apk/debug`, but native alignment plugin 1.8.3 searched
`app/build/outputs/apk`.

Each application variant now supplies its `SingleArtifact.APK` directory to the
corresponding `VerifyNativePageAlignment` task. The binding runs after evaluation
because plugin 1.8.3 registers these tasks and sets their default directory at that
stage. Registering an earlier `configureEach` action allowed that default to
overwrite the binding. APK selection still uses the plugin's metadata reader, and
the existing ELF, ZIP and Manifest checks remain enabled.

## Executed validation

| Check | Result |
| --- | --- |
| Assemble with IDEA properties, without a command-line Debug APK | Passed; the report inspected the arm64-v8a APK under `intermediates/apk/debug` |
| Build through IntelliJ IDEA | Passed |
| Command-line `assembleDebug` | Passed; arm64-v8a, x86_64 and universal APKs checked under `outputs/apk/debug` |
| `testDebugUnitTest` | 422 tests in 79 suites, zero failures, errors or skipped tests |
| `assembleDebugAndroidTest` | Passed; instrumentation APK built |
| `lintDebug` | Passed |
| `appendDigestToReleasedFiles` | Passed; three signed Release APKs at version 1.2.0 / build 221 verified and archived with CRC32 |
| Debug and Release alignment reports | `ok: true`, `skipped: false`, no errors |
| Generated Markdown `--check`, repository pending-commit check, `git diff --check` | Passed |
| Existing IDEA `app` run configuration | Installed build 221 and launched `ChatActivity` on the selected Sony XQ-DQ72, Android API 33; ADB confirmed the process and resumed activity |

The command-line validation used the repository-prescribed Java vendor properties
and completed all five Gradle targets in one invocation (139 actionable tasks).
The IDE-like assembly additionally used `android.injected.invoked.from.ide=true`,
`android.injected.build.api=33` and
`android.injected.build.abi=arm64-v8a,armeabi-v7a,armeabi`.

The selected device reports a 4096-byte page size. These checks cover IDE build,
installation and launcher startup, plus static 16 KB ELF/ZIP alignment. OEM Wake
activation, host Binder smoke tests, execution on a 16 KB device and the full device
matrix were not rerun for this candidate. The local Release archives are not a
published release or evidence of completed release acceptance.
