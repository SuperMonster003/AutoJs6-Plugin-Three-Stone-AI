# AutoJs6 AI Text Generation

This plugin imports one local `.litertlm` model package through the Android Storage Access Framework (SAF), copies it into app-private storage, and runs CPU-only LiteRT-LM generation with plain-text history and streaming text output.

The plugin requires AutoJs6 host build 5270 or later and Android API 24 or later.

Safety and operational limits:

- Model import is limited to 8 GiB and must leave at least 256 MiB of free space.
- Context is limited to 256 KiB, output is limited to 64 KiB, and only one generation session may be active.
- The provider declares no token-count ceiling, so requests that set `maximumOutputTokens` are unsupported.
- Only streaming and `text/plain` are declared. Reasoning, tools, structured JSON, and usage are unsupported.
- The plugin requests no network or storage permission.
- Only the same-signature AutoJs6 host may bind the provider service.
- Previous model generations named by SHA-256 hash are retained for cross-process safety and continue to occupy app-private storage.
