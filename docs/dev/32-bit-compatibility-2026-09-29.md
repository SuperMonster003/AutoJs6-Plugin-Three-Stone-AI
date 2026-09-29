# 32-bit Android compatibility, 2026-09-29

This is an unpublished 1.2.1 / 224 candidate. Application and online AI support now
include x86 and armeabi-v7a on Android API 24 or later. Local LiteRT-LM inference
still requires a compatible 64-bit process and the corresponding packaged JNI library.

## Runtime and packaging contract

The resolved `com.google.ai.edge.litertlm:litertlm-android:0.15.0` AAR contains only
`jni/arm64-v8a/liblitertlm_jni.so` and `jni/x86_64/liblitertlm_jni.so`.
Its SHA-256 is `b398c4745934a6035d192ffce5fdaf4f72a0009830a97b73c017c21f2a92b5bd`.
No dependency version or native library was changed for this work.

| APK | Native payload | Intended functionality |
|---|---|---|
| armeabi-v7a | None | App, online AI, plugin integration |
| x86 | None | App, online AI, plugin integration |
| arm64-v8a | arm64-v8a LiteRT-LM | Existing local and online AI |
| x86_64 | x86_64 LiteRT-LM | Existing local and online AI |
| universal | Both 64-bit LiteRT-LM libraries | Existing local and online AI on compatible 64-bit devices |

The two Java-only APKs are byte-identical and carry architecture-specific filenames
for selection alongside the existing outputs. A 32-bit-only device must use one of
these standalone APKs. The universal APK was also tried on the API 24 x86 emulator:
Android correctly returned `INSTALL_FAILED_NO_MATCHING_ABIS`, preserving the installed
x86 package. It is not a universal installation option for 32-bit-only devices.

`PluginInfo` and `AiProviderInfo` advertise all four application ABI variants.
LiteRT-LM profiles use a separate native capability check: the current process's
bitness/ABIs and `BaseDexClassLoader.findLibrary` must both allow the runtime.
The lookup does not load JNI. This also handles a Java-only APK installed on a
64-bit device, where checking `Build.SUPPORTED_ABIS` alone would be incorrect.
Unavailable local profiles report `ABI_UNSUPPORTED` and fail before OpenCL probing
or engine construction. Creating the unified target catalog no longer constructs
the local engine runtime.

The model manager explains unavailable local inference and disables local model
download, import, selection and health checks. Existing model files remain manageable.
Online settings remain available. All ten documentation languages and eleven Android
resource/instruction directories, including explicit English, were updated.

Release collection separately verifies the application output matrix and native
payload matrix. Empty native payloads are allowed only for the configured Java-only
outputs; missing 64-bit libraries or stray libraries in a 32-bit APK fail validation.
The native alignment plugin and signed APK identity, version, CRC32 and SHA-256 checks
remain enabled. CI checks all five APKs and adds API 24 x86 device coverage.

## Executed validation

| Check | Result |
|---|---|
| JVM regression | 423 tests, 0 failures/errors/skips |
| Custom Python regression | 56 tests passed; the 5 APK contract tests were rerun after the API 24 page-size fallback change |
| Debug assembly | All five APKs built and passed the APK payload verifier |
| Android test assembly and lint | Passed; lint has no errors |
| Release assembly and collection | All five R8 APKs signed, verified and archived locally |
| Debug/release native alignment | ELF and ZIP 16 KB checks passed for the existing 64-bit native payloads |
| Markdown and repository checks | Generation, read-only `--check`, pending-commit repository check and `git diff --check` passed |

| Environment | APK/process | Device evidence |
|---|---|---|
| API 24 emulator, x86-only, 4 KB | x86, confirmed 32-bit process | 31/31 Android regression tests; exact archived release install, launcher and platform smoke passed |
| Redmi 22120RN86C, API 33, 4 KB | armeabi-v7a, forced 32-bit process | 31/31 Android regression tests; exact archived release install, launcher and platform smoke passed |
| Same Redmi, 64-bit process | Java-only armeabi-v7a APK | 6/6 discovery/compatibility tests passed, including correct local-runtime unavailability |
| API 37.1 x86_64 emulator, 16 KB userspace | x86_64, confirmed 64-bit process | 6/6 discovery/compatibility tests; exact archived release platform smoke passed with `pageSize=16384` |
| Redmi restored to native arm64 | arm64-v8a, confirmed 64-bit process | Exact archived release platform smoke passed, including local-runtime availability |

The 31-test set comprises `PluginDiscoveryAndroidTest`, `AbiCompatibilityAndroidTest`,
`NativeToolSessionAndroidTest`, `VisionSessionAndroidTest` and
`OwnedParcelFileDescriptorsAndroidTest`. It covers discovery, protected Binder service
startup, actual descriptor handling, image/tool continuation and cancellation, and the
local-runtime guard. The online compatibility test uses the production encrypted
credential store, Android Keystore, profile registry, composite backend and HTTP/SSE
execution path with an intercepted synthetic HTTPS response. It does not call a paid
service or alter saved profiles, credentials, defaults or models.

The platform smoke exercises the actual signed R8 APK: Wake discovery and activation
from the plugin UID, installed version/ABI metadata through INFO Binder, the separate
AI provider process and its rejection of a non-host UID, and the model manager's
runtime notice and disabled actions. It confirms that these operations do not map
`liblitertlm_jni.so` into the instrumented process.

Initial attempts to reuse the normal AndroidX debug test runner against R8 APKs failed
before test execution with `NoClassDefFoundError: kotlin.jvm.internal.Intrinsics`.
These are test-harness failures, not successful release Binder tests. The new Java
`ReleaseAbiSmokeInstrumentation` uses platform instrumentation and preserved public
Binder APIs, without depending on unoptimized Kotlin runtime names or app internals.
No production keep rules were weakened. AGP selects one instrumentation runner, so
the platform runner is selected explicitly with `-PreleaseAbiSmoke=true`.

## Reproducing the release smoke

Build and collect the normal signed release, then build the platform test APK:

```powershell
.\gradlew.bat :app:appendDigestToReleasedFiles
.\gradlew.bat -PreleaseAbiSmoke=true :app:assembleDebugAndroidTest
```

Install the exact archived application APK and the resulting
`app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` with `adb install -r`
(add `-t` for the test APK). On a dual-ABI device, use `adb install -r --abi armeabi-v7a`
for the ARM 32-bit application test. Select the device with `adb -s SERIAL` and run:

```text
adb -s SERIAL shell am instrument --abi armeabi-v7a -w -r -e process64 false io.github.supermonster003.autojs6.plugin.threestoneai.test/io.github.supermonster003.autojs6.plugin.threestoneai.ReleaseAbiSmokeInstrumentation
```

Use the matching ABI and `process64 true` for a 64-bit APK. Require
`releaseAbiSmoke=passed`, the expected `process64` value and page size. Rebuild
`:app:assembleDebugAndroidTest` without `releaseAbiSmoke` before running ordinary
AndroidJUnitRunner suites. The final local test build was restored to that default.

The Redmi was upgraded from build 223 with `adb install -r`, without clearing app
data. After the 32-bit tests it was restored to the signed arm64-v8a build 224.

## Exact signed candidates

Artifacts are under ignored `releases/v1.2.1/`, with a `release-manifest.json` receipt.
They were collected from the pending change (`sourceDirty=true`), not published.

| ABI | CRC32 | SHA-256 |
|---|---|---|
| armeabi-v7a | BB3D2042 | 91ab4f4b67e5e998c0e27cd65ac1b43436512d5c6d1967d5c5333f83bd666449 |
| x86 | BB3D2042 | 91ab4f4b67e5e998c0e27cd65ac1b43436512d5c6d1967d5c5333f83bd666449 |
| arm64-v8a | 6B153D26 | 411b5a211ce7eb6236ee23e9bfacda29d9595ecb5d1b703644c3dc4b5f519f6b |
| x86_64 | 96D9395B | 964f770fd0e124956aa689eccb484f6553d707cf880315cd41dfd1e347338b06 |
| universal | B879F55D | 1b00b339695ce0a87dd4f02c31939dd41f5f83142792d9ef9339828e337e7a62 |

## Coverage limits

- No 32-bit-only ARM physical device was available. ARM evidence is a verified
  32-bit process on a physical device that supports both ARM ABIs.
- The 16 KB run used an emulator where bionic/getconf and `Os.sysconf` report 16384,
  while the underlying kernel's `smaps` reports 4096. It is userspace emulation,
  not new physical 16 KB native-inference acceptance.
- No real local model inference or live cloud-provider request was executed for
  this candidate. Existing 64-bit native payloads are unchanged.
- OEM restrictions on host activation, force-stop recovery, host-UID catalog and
  generation calls, and the full device/version matrix remain unexecuted here.
  The release smoke's Wake call comes from the plugin UID, and its AI Binder call
  deliberately verifies rejection of that non-host UID.
- CI configuration was validated locally; the updated GitHub Actions jobs have not
  been claimed as executed or green remotely. No release was published or pushed.
