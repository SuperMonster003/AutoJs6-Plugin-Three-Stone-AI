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

These are vendored inputs, not dependencies on a sibling build. Replace the
four API AARs and their checksums together from one verified host revision,
and `download-kit.aar` with its checksum on its own. No debug artifact or
signing material belongs in this directory.
