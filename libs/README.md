# Bundled host API artifacts

All four release AARs come from the same AutoJs6 host build at commit
`52ce694f92` (6.8.0 / 5297), built with Temurin 21. AI Provider V2 negotiates
2.0 or 2.1. See `SHA256SUMS` for the exact SHA-256 of each local artifact.

Source: [AutoJs6 plugin-api](https://github.com/SuperMonster003/AutoJs6/tree/52ce694f92/plugin-api).
License: [Mozilla Public License 2.0](https://github.com/SuperMonster003/AutoJs6/blob/52ce694f92/LICENSE).

These are vendored inputs, not dependencies on a sibling build. Replace the
four AARs and checksums together from one verified host revision. No debug
artifact or signing material belongs in this directory.
