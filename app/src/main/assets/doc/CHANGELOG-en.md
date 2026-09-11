******

### Release history

******

# v1.1.1

###### 2026/09/11

* `Improvement` Build verification of 16 KB page alignment for 64-bit native libraries, including manifest contract checks and JSON reports

# v1.1.0

###### 2026/09/01

* `Feature` Plugin brand and runtime identity standardized as 3-Stone AI across display names, package and component names, discovery identifiers, build artifacts, and documentation
* `Feature` Cross-process integration uses the neutral `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, and `IAiProvider`/`IAiSession`/`IAiCallback` identities without aliases from replaced identities
* `Feature` Added a signature-protected, exported, parameterless AI settings entry so AutoJs6 can open the plugin's unified settings without sending profile or credential data
* `Feature` Exposed `local:*` and `profile:*` directly through the paged AI Provider V2 target catalog, with independent provider/model/locality, configured and available state, capabilities, limits, controls, HTTPS origins, and an exact `isDefault` marker for every target
* `Feature` Forwarded `temperature`, `topK`, `topP`, and `maxTokens` through AI Provider V2 generation requests to LiteRT-LM sampling and output-token controls
* `Feature` Reported exact LiteRT-LM input, output, and total token counts plus provider-measured generation duration through AutoJs6 `ai.chat().usage` and stream usage events
* `Feature` Added AI Provider V2 persistent sessions and AutoJs6 `ai.session` multi-turn Conversation reuse without resending prior history
* `Feature` Added native LiteRT-LM JSON Schema constrained decoding through AutoJs6 `structuredJson` and `responseSchema`, with single-call, streaming, and persistent-session support plus strict completed-JSON validation
* `Feature` Exposed explicit `cpu`, `gpu`, and `npu` backend profiles as optional AI Provider V2 target controls, with device compatibility reporting, model/profile cache isolation, and no fallback from unavailable profiles; GPU is declared only after an OpenCL load probe and NPU remains unavailable because its EAP runtime is not packaged
* `Feature` Direct downloads of pinned LiteRT Community models to a user-selected SAF location, with progress, precise cancellation, incomplete-file cleanup, LiteRT-LM header and exact size/SHA-256 verification, and a download-to-import handoff
* `Feature` Added a launcher conversation workspace with streaming Markdown, persistent history, warned branch replacement when editing prior prompts, multi-result search, and keyboard-aware input
* `Feature` Added application settings for theme color, dark mode, app language, app and developer information, and release history, with Follow AutoJs6 as the default wherever possible
* `Feature` Added conversation settings for font size, Enter-key behavior, unlimited or custom output tokens, and model-default or custom `temperature`, `topK`, and `topP` sampling
* `Feature` Rendered inline `$\text{...}$` content during streaming, with common math commands plus superscript and subscript styling
* `Feature` Added a plugin-managed Android Keystore credential store with AES-256-GCM, profile-bound authenticated ciphertext, cross-process atomic private files, configured-only status checks, and immediate plaintext zeroization
* `Feature` Added a strict non-secret online profile repository for HTTPS-only OpenAI-compatible endpoints, with canonical UUIDs, cross-process atomic metadata, and mandatory credential replacement or clearing when the provider or origin changes
* `Feature` Added the plugin-internal OpenAI-compatible HTTPS execution backend for custom base URL, credential, and model profiles, with bounded SSE and JSON-fallback streaming, precise cancellation, provider usage, completed-turn persistent history, JSON Schema request mapping, and fixed non-sensitive errors; configured `profile:*` targets invoke it directly through AI Provider V2
* `Feature` Added OpenAI, Anthropic, Gemini, DeepSeek, and OpenRouter presets aligned with the host catalog; the unified online execution layer reuses the OpenAI-compatible protocol and separately adapts native Anthropic Messages and Gemini GenerateContent authentication, requests, SSE terminals, usage, and JSON Schema without cross-protocol or local/online fallback
* `Feature` Added the 10-language online-services settings UI for profile add/edit/delete, non-disclosing API-key replacement and clearing, default-target selection, metered-network opt-in enforced before credential access, and explicit cancellable 120-second connection tests; settings share the atomic cross-process profile document and refresh the V2 target catalog dynamically
* `Feature` Added a unified local/cloud target selector to launcher chat: every conversation persists one target snapshot, populated conversations recommend starting a new conversation when switching, and continuing with retained context requires explicit confirmation and records the change
* `Feature` Added an actual target/provider/model/locality snapshot to every assistant response; regeneration precisely reuses the recorded response target, fails explicitly on identity drift or unavailability, and never silently falls back to the current conversation target
* `Feature` Kept local and cloud generation failures on their selected boundary: launcher chat appends a bounded non-secret reason, states that no cross-boundary fallback occurred, and exposes an explicit manual target switch without discarding partial output
* `Fix` Removed the runnable instruction examples' implicit 256-token and 4 KiB output caps: omitted `maxTokens` now uses the model or engine default, while the raw Binder example uses the provider's full 64 KiB output allowance
* `Fix` Updated the raw Binder sample in all 10 localized plugin instructions to the final AI Provider V2 request and target-list API
* `Fix` Fixed the model manager retaining light-theme text colors in system dark mode, which made body text, checkboxes, and model rows unreadable against the dark background
* `Fix` Kept the composer visible above the soft keyboard, selected send-button text for contrast with the active theme color, and unified the previous, next, and close search controls
* `Fix` Fixed a session close invoked from inside a generation listener callback waiting on itself indefinitely; close still waits for callbacks already running on other threads
* `Fix` Fixed app-private online-profile and credential storage being rejected when Android canonicalizes the trusted `/data/user/0` app-data root to `/data/data`; direct-child links and containment escapes remain rejected
* `Improvement` Updated the plugin description, instructions, and 10-language README to match the formalized host `ai.*` unified target route
* `Improvement` Rewrote the ROADMAP as a feature roadmap with individually checkable items
* `Improvement` Normalized application and generated localized text to ASCII punctuation, with a regression test covering packaged and generated text
* `Improvement` Introduced a shared `AiBackend`/`AiTarget`/`AiBackendSession` layer so launcher chat and the Binder provider use the same `LiteRtLocalBackend` catalog, capabilities, session creation, streaming, and cancellation path
* `Improvement` Merged local `local:*` and online `profile:*` targets into one application-level catalog and dispatcher, exposed both directly through AI Provider V2, and derived provider locality, credential mode, and allowed HTTPS origins from the current catalog without exposing credential bytes
* `Improvement` Standardize the README layout and Gradle platform version management
* `Improvement` Open the built-in release history page from the release history button in the update dialog

# v1.0.0

###### 2026/08/08

* `Feature` AI Provider foundation with plugin ID and engine `three-stone-ai`, provider ID `autojs6.three-stone-ai`, and variant `default`
* `Feature` CPU-only LiteRT-LM plain-text generation with system, user, and assistant history plus credit-backed streaming
* `Feature` SAF import of `.litertlm` into app-private storage with an 8 GiB limit, free-space reserve, SHA-256, fsync, and atomic activation
* `Feature` One active session, bounded I/O, descriptor quotas, cancellation, timeout, one terminal state, and same-signature AutoJs6 caller verification
* `Feature` Explicit omission of reasoning, tools, structured JSON, usage, network, and credential capabilities
* `Feature` arm64-v8a, x86_64, and universal APKs plus README, changelog, Android UI, and plugin instructions in 10 languages
* `Feature` A model management screen for viewing the complete model catalog and private-storage usage, with atomic current-model selection that does not copy model files
* `Improvement` Retained previous model generations named by SHA-256 hash after replacement imports to avoid cross-process `:provider` races, with retained files continuing to occupy app-private storage
* `Improvement` Added an application-scoped single-import coordinator and fsynced pending journal for Activity-recreation continuity, cold-start recovery, stale temp cleanup, and deletion restricted to destinations created by the current attempt and never published, while preserving published, current, and historical hash generations
* `Dependency` Added LiteRT-LM 0.15.0 for on-device CPU text generation
