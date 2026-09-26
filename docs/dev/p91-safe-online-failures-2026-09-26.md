# Safe online failure categories (2026-09-26)

3-Stone AI 1.2.0 development candidate / build 219. This P9.1 follow-up preserves diagnostic information previously lost at the Provider callback boundary; it does not change networking, retry behavior or device actions.

## Boundary

BackendFailurePolicy maps only OnlineAiFailureException.reason, a closed enum, to the existing optional AiError.providerCode as ONLINE_<NAME>. AiErrorCode.PROVIDER_FAILED, the fixed public message and AiRetryDisposition.NEVER remain unchanged. Unknown Throwables omit providerCode without reading message, toString or nested causes. HTTP status, URL, credential, request and response text are not forwarded.

RemoteThreeStoneAiSession applies this mapping only to backend listener failures. Output-limit completion, cancellation and timeout precedence are unchanged. Synchronous initialization/continuation rejection paths retain their previous handling. No published AIDL transaction, SDK AAR or model capability changes. The host must independently allowlist any Provider diagnostic category; it must not trust arbitrary providerCode text.

## Verification

- Six relevant JVM test classes: 30 tests, no failures, errors or skips. Coverage includes every known online enum and codec round trip, unknown exceptions containing secret-looking text, and a Throwable that fails if its message is inspected.
- QV770340J7 / Sony XQ-DQ72 / current Android 13 API 33: NativeToolSessionAndroidTest, OK (12 tests), on matching official-signed debug and androidTest build 219. The new test covers streaming and non-streaming tool-result continuations, known and unknown failures, exactly one terminal event and no extra generation/continuation after duplicate callbacks.
- Android tests use deterministic backends, real AIDL proxy marshalling and the existing test owner verifier. They do not count as real cloud-model acceptance or cross-UID host validation. No online request is issued by this suite.
- Debug, androidTest, R8 release, lintVital, 16 KiB native alignment and appendDigestToReleasedFiles passed. The related build took 2m 47s with 131 tasks. The separate :app:lintDebug run also passed in 2m 20s.
- All ten changelogs and README sources were regenerated; generator --check, check_repository --pending-commit and git diff --check passed.

## Signed candidate

All three APKs use the official signing certificate SHA-256 31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213. The archive manifest correctly records parent 7138fd085660edfa0b8b7e918b5eb06c33982ffe with sourceDirty=true because the candidate was built before its logical commit.

| Artifact | SHA-256 |
| --- | --- |
| Debug arm64 | 0fe06da570b52ed529841d9a1fedc6ded92883491f56062ddda1d6bd448afcf0 |
| Android test | fbbf108f3a795799725b47d35e9d8ee6a3aecbda30de8f175accbe1c625dd658 |
| R8 arm64, CRC32 8D594FCF | f0e72ab427d113cc25616de93aada72312e37374fcccfc16706a12dcdedbd4be |

No remote push, release, tag or package publication is performed. AiGoCode synthetic vision probes used the earlier unmodified source build 218 and are recorded separately; their success is not attributed to this error-classification change.
