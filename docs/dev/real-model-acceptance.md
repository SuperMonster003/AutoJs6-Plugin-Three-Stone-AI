# Automated LiteRT-LM model acceptance

## Run

Use Python 3.11+, adb, a local `.litertlm` model and a supported test device. Keep the original
recommended-model filename so instrumentation also checks the catalog's pinned SHA-256 and size.
No Storage Access Framework picker or manual copy into private app storage is required.

From the repository root:

```powershell
.\gradlew.bat --no-daemon --max-workers=2 :app:assembleDebug :app:assembleDebugAndroidTest
python .python/run_real_model_test.py --serial <adb-serial> --model '<local-model.litertlm>'
```

For a remote device with a slow adb link, add `--download-on-device`. This explicitly opts in to
a direct device HTTPS download using the production downloader and the catalog's pinned URL,
size and SHA-256. The filename must identify a recommended model, and the local model's digest
must match that entry before the download starts. Ordinary test runs never download a model.

The script performs the following steps:

1. Hash the local model and select debug APKs from Gradle's output metadata for the device ABI.
2. Install both APKs with `adb install -r`.
3. Reuse a matching managed model when available. Otherwise use `adb push` and a device-side
   `cat | run-as` pipe to stage the file privately, verify its byte count, and remove the temporary
   shared copy. A temporary `.partial` file is renamed only after the complete transfer.
   With `--download-on-device`, the verified HTTPS response is staged privately instead.
4. Run real catalog integration, repository lookup, model health checking, engine initialization,
   and streamed CPU text generation. Instrumentation compares the device model to the local
   SHA-256, as well as the pinned catalog digest for recommended filenames.
5. Require a successful non-skipped test and a matching receipt with nonempty generated text.

Receipts and instrumentation logs are saved in `build/verification/real-model/`; `--output <dir>`
selects a different local directory. A previous success receipt is removed at the start so it
cannot be mistaken for the result of a failed run. Failures, crashes and assumption skips return
a failure from the script even if adb itself exits successfully.

This acceptance fixture imports/selects the model in the test app's catalog and leaves it
available for repeat runs. Use a test installation, or back up and restore the existing catalog
when reusing a developer installation. It does not uninstall the app or clear its data.

## Ordinary instrumentation runs

Without an explicitly supplied model and without the default staged file, the real-model fixture
reports an assumption skip with the automation command. Passing `modelFile` or `targetFile`
explicitly keeps missing files, digest mismatches and inference failures as test failures.

The test also accepts `prompt` and `maxOutputTokens` via `am instrument -e`; the automation uses
the default short greeting prompt and a maximum of 48 output tokens.

## Evidence, 2026-09-16

- Sony XQ-DQ72, API 33, arm64-v8a, 4096-byte pages: the automated runner reused and verified
  `gemma-4-E2B-it-litert-lm.litertlm`, 2,583,085,056 bytes, SHA-256
  `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`.
- Health check 415 ms, engine initialization 293 ms, generation 1,729 ms; output `Hello!`,
  two streamed deltas, receipt `ok: true`. The original catalog was backed up before this run
  and restored afterward.
- Sony XQ-AT72, API 31, arm64-v8a, 4096-byte pages: the runner pushed the complete 1,597,931,520-byte
  Qwen2.5 1.5B model from Windows, staged it privately, verified SHA-256
  `faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`, and completed real inference.
  Health check 3,912 ms, initialization 609 ms, generation 2,492 ms; output
  `Hello tester! How's the testing going today?`, ten streamed deltas, receipt `ok: true`.
  The original empty catalog was restored and the test's imported model removed afterward.
- Samsung SM-A566B, API 36, arm64-v8a, 16384-byte pages, reset rental device: the runner installed
  both APKs and `--download-on-device` downloaded and verified the same pinned Qwen model in
  268,831 ms. Health check 5,180 ms, initialization 476 ms, generation 3,822 ms; output
  `Hello tester! How's the testing going today?`, ten streamed deltas, receipt `ok: true`.
  The app and selected model remain installed. The slower redundant adb transfer was stopped
  after this success and its temporary file/directory removed.
- JVM suite: 359/359 passed. Debug and instrumentation assembly, lintDebug, Markdown check and
  repository validation passed. Python runner result checks passed, including rejection of
  skipped tests and adb-only success.
- Signed release collection and native 16 KB alignment checks passed. On the API 37 emulator,
  the default suite had two passes and one expected model-dependent skip, with no failures.

The local Sony receipt is `build/verification/real-model-sony/litertlm-real-model-inference.json`.
The full USB staging receipt is `build/verification/real-model-sony-usb/litertlm-real-model-inference.json`.
The Samsung receipt is `build/verification/real-model-samsung-https/litertlm-real-model-inference.json`.
Build logs and device output remain ignored and are not release artifacts.
