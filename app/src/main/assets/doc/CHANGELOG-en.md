******

### Release history

******

# v1.1.0

###### 2026/08/24

* `Feature` Plugin brand and runtime identity standardized as 3-Stone AI across display names, package and component names, discovery identifiers, build artifacts, and documentation
* `Feature` Cross-process integration uses the neutral `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, and `IAiProvider`/`IAiSession`/`IAiCallback` identities without aliases from replaced identities
* `Feature` Compatible with the AutoJs6 `plugin: true` shorthand selector for `ai.ask`/`ai.chat`/`ai.stream` and the `ai.models` model listing
* `Feature` Forwarded `temperature`, `topK`, `topP`, and `maxTokens` through AI Provider protocol 1.1 to LiteRT-LM sampling and output-token controls
* `Feature` Reported exact LiteRT-LM input, output, and total token counts plus provider-measured generation duration through AutoJs6 `ai.chat().usage` and stream usage events
* `Feature` Added AI Provider protocol 1.2 persistent sessions and AutoJs6 `ai.session` multi-turn Conversation reuse without resending prior history
* `Feature` Added native LiteRT-LM JSON Schema constrained decoding through AutoJs6 `structuredJson` and `responseSchema`, with single-call, streaming, and persistent-session support plus strict completed-JSON validation
* `Feature` Explicit `cpu`, `gpu`, and `npu` backend profiles through protocol 1.3 and AutoJs6 generation options, with device compatibility reporting, model/profile cache isolation, and no fallback from unavailable profiles; GPU is declared only after an OpenCL load probe and NPU remains unavailable because its EAP runtime is not packaged
* `Feature` Direct downloads of pinned LiteRT Community models to a user-selected SAF location, with progress, precise cancellation, incomplete-file cleanup, LiteRT-LM header and exact size/SHA-256 verification, and a download-to-import handoff
* `Feature` Added a launcher conversation workspace with streaming Markdown, persistent history, warned branch replacement when editing prior prompts, multi-result search, and keyboard-aware input
* `Feature` Added application settings for theme color, dark mode, app language, app and developer information, and release history, with Follow AutoJs6 as the default wherever possible
* `Feature` Added conversation settings for font size, Enter-key behavior, unlimited or custom output tokens, and model-default or custom `temperature`, `topK`, and `topP` sampling
* `Feature` Rendered inline `$\text{...}$` content during streaming, with common math commands plus superscript and subscript styling
* `Feature` Added a plugin-managed Android Keystore credential store with AES-256-GCM, profile-bound authenticated ciphertext, cross-process atomic private files, configured-only status checks, and immediate plaintext zeroization
* `Feature` Added a strict non-secret online profile repository for HTTPS-only OpenAI-compatible endpoints, with canonical UUIDs, cross-process atomic metadata, and mandatory credential replacement or clearing when the provider or origin changes
* `Feature` Added the plugin-internal OpenAI-compatible HTTPS execution backend for custom base URL, credential, and model profiles, with bounded SSE and JSON-fallback streaming, precise cancellation, provider usage, completed-turn persistent history, JSON Schema request mapping, and fixed non-sensitive errors; AI Provider V1 host routing remains local-only
* `Feature` Added OpenAI, Anthropic, Gemini, DeepSeek, and OpenRouter presets aligned with the host catalog; the unified online execution layer reuses the OpenAI-compatible protocol and separately adapts native Anthropic Messages and Gemini GenerateContent authentication, requests, SSE terminals, usage, and JSON Schema without cross-protocol or local/online fallback
* `Feature` Added the 10-language online-services settings UI for profile add/edit/delete, non-disclosing API-key replacement and clearing, default-target selection, metered-network opt-in enforced before credential access, and explicit cancellable 60-second connection tests; settings share the atomic cross-process profile document and AI Provider V1 remains local-only
* `Fix` Removed the runnable instruction examples' implicit 256-token and 4 KiB output caps: omitted `maxTokens` now uses the model or engine default, while the raw Binder example uses the provider's full 64 KiB output allowance
* `Fix` Fixed the raw Binder sample in the 10 localized plugin instructions still invoking the 14-argument protocol 1.1 `AiGenerationOptions` constructor, which failed against the protocol 1.3 API
* `Fix` Fixed the model manager retaining light-theme text colors in system dark mode, which made body text, checkboxes, and model rows unreadable against the dark background
* `Fix` Kept the composer visible above the soft keyboard, selected send-button text for contrast with the active theme color, and unified the previous, next, and close search controls
* `Fix` Fixed a session close invoked from inside a generation listener callback waiting on itself indefinitely; close still waits for callbacks already running on other threads
* `Improvement` Updated the plugin description, instructions, and 10-language README to match the formalized host `ai.*` local plugin route
* `Improvement` Rewrote the ROADMAP as a feature roadmap with individually checkable items
* `Improvement` Normalized application and generated localized text to ASCII punctuation, with a regression test covering packaged and generated text
* `Improvement` Introduced a shared `AiBackend`/`AiTarget`/`AiBackendSession` layer so launcher chat and the Binder provider use the same `LiteRtLocalBackend` catalog, capabilities, session creation, streaming, and cancellation path
* `Improvement` Merged local `local:*` and online `profile:*` targets into one application-level catalog and dispatcher; the V1 model listing remains local-only and online targets report unavailable until their HTTPS execution transport is implemented

# v1.0.0

###### 2026/08/08

* `Feature` AI Provider protocol V1 provider running entirely on device, with plugin ID and engine `three-stone-ai`, provider ID `autojs6.three-stone-ai`, and variant `default`
* `Feature` CPU-only LiteRT-LM plain-text generation with system, user, and assistant history plus credit-backed streaming
* `Feature` SAF import of `.litertlm` into app-private storage with an 8 GiB limit, free-space reserve, SHA-256, fsync, and atomic activation
* `Feature` One active session, bounded I/O, descriptor quotas, cancellation, timeout, one terminal state, and same-signature AutoJs6 caller verification
* `Feature` Explicit omission of reasoning, tools, structured JSON, usage, network, and credential capabilities
* `Feature` arm64-v8a, x86_64, and universal APKs plus README, changelog, Android UI, and plugin instructions in 10 languages
* `Feature` A model management screen for viewing the complete model catalog and private-storage usage, with atomic current-model selection that does not copy model files
* `Improvement` Retained previous model generations named by SHA-256 hash after replacement imports to avoid cross-process `:provider` races, with retained files continuing to occupy app-private storage
* `Improvement` Added an application-scoped single-import coordinator and fsynced pending journal for Activity-recreation continuity, cold-start recovery, stale temp cleanup, and deletion restricted to destinations created by the current attempt and never published, while preserving published, current, and historical hash generations
* `Dependency` Added LiteRT-LM 0.15.0 for on-device CPU text generation
