# Model downloads on the shared download library (2026-11-17)

3-Stone AI 1.7.1, AutoJs6 plugin-center roadmap P13.3 (D22, D26, T31, T32, Q51 A). The recommended-model download keeps its state machine, coordinator and download card; the transfer and the staging now go through `download-kit.aar`, the download library shared by the AutoJs6 host and the official plugins (Ace Editor, 3-Macaw Speech, 3-Stone AI).

## What changed

- `libs/download-kit.aar`: release AAR of host commit `b38fb2c467` (the latest change of `plugin-api/download-kit`, built from host revision `287856198`), 172207 bytes, SHA-256 `31d4cdd169c6d05f08066716e95307576ca4976525257dbe023802cbc5e81364`, registered in `libs/SHA256SUMS` and in the configuration-time check of `app/build.gradle.kts`. kotlinx-coroutines 1.9.0 was already a dependency; unit tests add `org.json:json:20231013`.
- `ModelDownloadStorage` (new): one `DownloadStore` under `files/model-downloads` (one directory per model SHA-256, `model.litertlm.part` while unfinished) and one `DownloadFetcher`, owned by the process-wide `ModelDownloadCoordinator`. Range + If-Range resume, two automatic retries (2 s, 5 s, each continuing the part), mirrors, size and SHA-256 checks all come from the library. Timeouts stay 30 s connect / 60 s read.
- `ModelDownloadTransport` / `PolicyRedirectConnection` (new): the library follows redirects by itself (`instanceFollowRedirects = true` after `open`), so the plugin's `open` returns a connection that walks the redirects itself with the previous rules: at most 8 redirects, HTTPS on port 443, only `huggingface.co`, `*.huggingface.co`, `hf.co` and `*.hf.co`, the request headers (Range, If-Range) sent on every hop. A refused host is a `SecurityException` (not retried); a redirect over the limit or without a usable Location is handed to the library as that 3xx (an HTTP error, not retried).
- The store is staging only: a verified file is renamed to `files/models/.download-<sha256>.part` (the name `ModelRepository.importVerifiedDownload` expects as a direct child of the model directory), fsynced, and the store entry is removed before the hand-off. If the import is cancelled or fails, the file stays there and is verified again on the next start, as before.
- Q51 A: a `files/models/.download-<sha256>.part` (left by 1.7.0, or a verified file the import gave back) is moved into the store with `DownloadStore.importPartial` at the cold-start restore and before each start. It is a rename on the same file system, so a GB-sized part moves at once; the next fetch continues it with `Range` (or only hashes it when complete). A file shorter than what the store already has, or one that cannot be continued, is deleted.
- Retention: no age limit (`DownloadRetention.Limits` with `maxIdleMillis` and `partMaxAgeMillis` at `Long.MAX_VALUE`); a `Rule` keeps only the download being shown or started. This is the previous behaviour: an unfinished model of several GB is never deleted because it waited; only one model download is kept; cancellation, invalid content and digest mismatches delete it.
- Kept from the old transfer: the LiteRT-LM header check fails fast (read from the part once progress passes 8 bytes, and for an existing part before a start), the free-space check before a start, the stages (`CHECKING_PARTIAL` while hashing or until the first progress of a resume, `CONNECTING` for a fresh start and during automatic retries, `DOWNLOADING`), monotonic progress (a restart from zero goes through `restartProgress`), pause / cancel keeping / deleting the part. A pause or cancellation sets the library's cancel flag, closes the open connections and returns only once the library's transfer has stopped writing (the key is no longer in use), so a resumed transfer never shares the part with the old one.
- Failure reasons: HTTP status and refused hosts -> `HTTP_ERROR`; network errors -> `NETWORK_UNAVAILABLE` (`INSUFFICIENT_STORAGE` when the message is ENOSPC); a file shorter than pinned after the retries -> `NETWORK_UNAVAILABLE` (part kept); a longer file or a digest mismatch -> `INTEGRITY_MISMATCH` (deleted); non-LiteRT-LM content -> `INVALID_CONTENT` (deleted); anything else -> `UNKNOWN`.
- Removed: `ModelDownloadHttpClient.kt` and `ModelDownloadTransfer.kt` with `ModelDownloadTransferTest` (8 tests) and `ModelDownloadPolicyTest.resumedResponseMustCoverExactlyTheMissingRange`; the library checks Content-Range itself (start, end, total) and the final SHA-256 decides. `LiteRtRealModelInferenceInstrumentationTest` stages its real model through the same storage.

Intended differences from 1.7.0: a cut transfer is retried twice by itself before the card shows a failure; the partial file lives in `files/model-downloads`; a resume from a byte range that the server answers with a whole file or a changed ETag starts over (as before), and a resumed file whose SHA-256 fails is downloaded whole once more before failing.

## Unit tests

`:app:testDebugUnitTest` (all tasks rerun, Temurin 21): 694 tests in 110 result files, 0 failures, 0 errors, 0 skipped. Baseline 689 (1.7.0): minus 9 tests of the removed transfer code, plus 14 in `ModelDownloadStorageTest`, which runs the real library against a loopback HTTP server (`ModelTestHttpServer`, scripted answers, Range, cut connections, redirects, recorded exchanges):

- a cut transfer is retried by itself and continues from the cut (`Range: bytes=65536-` with `If-Range`), progress monotonic;
- the next mirror continues the part without `If-Range` after the first mirror's two retries (503);
- a 1.7.0 partial file is adopted and continued with one `Range` request; a complete one is only hashed (no request) and handed off to `files/models`;
- a wrong SHA-256 is rejected and nothing is kept; a resumed file with a wrong SHA-256 is downloaded whole once (206 then 200) and then rejected;
- non-LiteRT-LM content fails fast as `INVALID_CONTENT`; an old part without the header is refused before a start;
- a pause stops the library's transfer before `fetch` returns, and the next fetch continues the part;
- a redirect to an unapproved host is refused without retrying; an approved redirect target receives the same `Range`; the redirect limit ends as an HTTP error after 9 requests;
- a cold start keeps only the newest download of a model that is not imported (older, imported and unknown files deleted); an older file shorter than the stored part is dropped;
- failure kinds map to the reasons of the download card.

## Device tests

Temporary AVD `pc2_p13_stone_api33` (pixel_6, API 33 google_apis x86_64, 3 GB, no window, swiftshader), emulator-5644, debug APK x86_64 built from the working tree of this change (VERSION_NAME 1.7.0 / build 357 at build time; the sources are those of the commit). No host is installed; the plugin's pages run on their own.

`ModelDownloadDeviceTest` now serves the downloads from a loopback HTTP/1.1 server inside the test process (`ModelDownloadLoopback.kt`) instead of a fake stream: the library talks real HTTP to it. The app forbids cleartext traffic, which the platform's HTTP stack enforces, so the test transport opens the hops with a minimal socket client; the app itself is unchanged. The catalog model `qwen2-5-1-5b-instruct` (1,597,931,520 bytes) is served as the LiteRT-LM header followed by zeros from the requested offset, 64 KiB every 15 ms, so it never reaches verification and nothing is imported.

`am instrument -e class ...ModelDownloadDeviceTest` (16:11 - 16:12): OK (6 tests).

| Test | Result | Key numbers |
| --- | --- | --- |
| `aCutTransferRetriesByItselfAndContinuesFromTheCut` (interrupted transfer) | PASS | the server cut the first response after 2,097,152 bytes; the library retried after 2000 ms with `Range: bytes=2097152-` and `If-Range: "loopback-1"`; the card stayed in "downloading" and passed 3 MiB |
| `aFailureKeepsThePartialFileAndRetryContinuesFromIt` | PASS | cut at 2,097,152 bytes, two automatic retries refused, card "failed" (network) with 2,097,152 bytes kept; the retry button sent `Range: bytes=2097152-` |
| `theCardPausesResumesFromThePartialFileAndCancels` | PASS | paused at 5,636,096 bytes = length of the part; resume sent `Range: bytes=5636096-` with `If-Range`; cancel deleted the part |
| `aColdStartShowsTheLeftoverPartialFileAsAPausedDownload` | PASS | a new coordinator restored the kept part as paused with the same length |
| `aPartialFileOfTheOlderDownloaderIsAdoptedAndContinued` (Q51 A) | PASS | a 3,145,728-byte `files/models/.download-<sha256>.part` was moved into the store at the cold start (restored as paused, old file gone); resume sent one request, `Range: bytes=3145728-` |
| `aFileWithTheWrongHashIsRejected` (hash mismatch) | PASS | a pinned 2 MiB test model served with another last byte: one request, card "failed" (integrity), 0 bytes kept, nothing in either location |

The AVD was closed right after the run.

## Lint

`:app:lintDebug`: no errors. The warnings in the changed files are the free-space hint on the existing check before a start (`UsableSpace`, unchanged code) and dependency / version-catalog hints on `app/build.gradle.kts`; nothing new to act on.

The release build and the release smoke are recorded with the release below.
