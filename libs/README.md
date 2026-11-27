# Bundled host API artifacts

The four API AARs (`ai-common-api`, `ai-provider-api`, `common-plugin-api`,
`protocol-wire-api`) come from the same AutoJs6 host build at commit
`52ce694f92` (6.8.0 / 5297), built with Temurin 21. AI Provider V2 negotiates
2.0 or 2.1. See `SHA256SUMS` for the exact SHA-256 of each local artifact.

Source: [AutoJs6 plugin-api](https://github.com/SuperMonster003/AutoJs6/tree/52ce694f92/plugin-api).
License: [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6/blob/52ce694f92/LICENSE).

`download-kit.aar` is the shared download library of the official plugins
(`plugin-api/download-kit`, package `org.autojs.plugin.downloadkit`). It is
the release AAR of host commit `b38fb2c467` (the latest change of
`plugin-api/download-kit`), built from host revision `287856198` with
`:plugin-api:download-kit:assembleRelease`; 3-Macaw Speech and Ace Editor
bundle the same file. It carries no transitive dependencies: the app declares
kotlinx-coroutines itself, and `org.json` comes from the platform. It is not
published to Maven Central and has no compatibility promise; a change of the
library means a new AAR here, a new checksum and an adapted downloader.

`speech-api.aar` is speech contract version 3 of `plugin-api/speech-api`
(package `org.autojs.plugin.speech.api`: `ISpeechPlugin`, `ISpeechSession`,
the session, wake, progress and resident observer callbacks and the constant
objects). It is the release AAR of host commit `1b95cc0201` (2026-11-06, not
yet public), assembled with `:plugin-api:speech-api:assembleRelease` in a clean
clone; 3-Macaw Speech stages the byte-identical file (SHA-256
`ad3dff26233d43afac148fce5d2844b3d631694a0517060621bf2dc4d0e1639c`, its
`locks/host-api-aars.lock`). It was copied once on 2026-11-27 (roadmap D203,
D208); the build never reads the 3-Macaw Speech checkout. The app binds
3-Macaw Speech's `org.autojs.plugin.SPEECH` service directly, so the contract
neither raises the minimum AutoJs6 version nor adds a permission.

These are vendored inputs, not dependencies on a sibling build. Replace the
four API AARs and their checksums together from one verified host revision,
and `download-kit.aar` and `speech-api.aar` each with its checksum on its own. No debug artifact or
signing material belongs in this directory.
