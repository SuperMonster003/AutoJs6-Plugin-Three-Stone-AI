******

### Release history

******

# v1.7.0

###### 2026/11/12

* `Feature` You can now add attachments in chat: tap "+" to the left of the input box to choose images or files, up to 20 per message. Supported are images (up to 5 MB each; oversized images are scaled down automatically), UTF-8 text files (such as txt, md, json, csv and code files, up to 256 KB) and PDFs (up to 32 MB and 100 pages). Images need "Image input" turned on for the current model; PDFs can currently be sent only to services whose provider is Anthropic or Gemini (for Gemini, the attachments of one request may total at most 20 MB); local models do not take attachments yet. Attachments the current model does not support are dimmed; remove them or switch to another model before sending
* `Feature` Attachments are saved with the message in the app's data: the message shows image thumbnails or file names, and tapping one opens it in another app; attachments that are chosen but not yet sent are kept when the screen rotates or the app is closed. When the conversation continues later, earlier attachments are sent to the model again as far as the context allows; for those that do not fit, the model is told only the file names. The attachments of one conversation may total at most 48 MB; deleting a conversation deletes its attachments, and "Clean unreferenced files" on the "Models and services" page also removes attachments that are no longer used
* `Feature` Regenerating a reply or editing and resending a message no longer deletes the rest of the conversation; it adds a new version of that turn instead: "< 2/3 >" appears under the reply, the arrows on either side switch versions, the later messages switch with them, and the conversation reopens on the version chosen last. A turn has up to 8 versions; when the versions not shown in a conversation take more than about 2 MB together, the oldest ones are dropped first. Search, export, reading aloud, usage and continuing the conversation use only the version shown, and deleting a message with several versions deletes only the version shown. After going back to 1.6.0 or earlier, only the version shown at that time is visible; as soon as the older release opens the chat page, the other versions of every conversation are dropped, and upgrading again does not bring them back
* `Feature` Conversations can be exported as a Markdown file or shared as an image: long-press a conversation in the side drawer, or open the chat page menu, and choose "Export as Markdown" or "Share as image". Both contain the version shown; the Markdown file lists attachments by file name only, and the image shows image thumbnails or file names; when the conversation has reasoning, you are asked first whether to include it (not included by default). You choose where the Markdown file is saved, and other Markdown readers display it properly; the image follows the look of the chat page and then opens the share sheet, and very long conversations are scaled down (to at most 16384 pixels high). While a reply is still being generated, wait for it to finish before exporting
* `Fix` In long conversations, earlier messages are automatically condensed into a summary to save context, but some online services and local models wrapped the summary in Markdown code block markers or added explanatory text, so condensing failed and the model was more likely to forget earlier parts of the conversation; these markers and text are now removed before the summary is read
* `Fix` After a local model was deleted, the cache files created when it was first loaded (up to several GB per model) stayed in the app cache and kept using storage; deleting a local model now deletes these caches too. Caches left behind by models deleted earlier are cleaned up automatically the next time you open this plugin, or with "Clean unreferenced files" on the "Models and services" page; caches of the local models you still have are not affected
* `Improvement` Reorganized the settings: the home page now holds only the appearance settings and 5 entries, "Conversation", "Models and services", "Voice", "Privacy and permissions" and "Updates and about", and each setting appears in only one place; "Generation" under "Conversation" is renamed "Output length and sampling", and its form no longer closes when swiped down or tapped outside, asking first when there are unsaved changes; the version history moved to "Updates and about". The chat page menu and side drawer no longer have a separate "Models" entry, everything is reached through "Settings"; opening this plugin's settings from AutoJs6 goes straight to "Models and services", and Back returns to the settings home page
* `Improvement` Merged the former "Models" and "Online services" pages into a single "Models and services" page: the default target and "Allow mobile and metered networks" are at the top, followed by two sections, "Online services" and "Local models". Every service and local model is shown as the same kind of card with a "Default", "Ready", "API key missing" or "Unavailable" label; actions such as set as default, test, rename, clone, clear API key and delete are in the menu on the right of each card, while import / export services, refresh model presets and clean up unreferenced files are in the menu on the right of each section title. Tap a local model to see its details and copy its model ID. Online profiles are now called "services" throughout
* `Improvement` Merged the former "Default online target" and the selected local model into a single "Default target", which can be any online or local model and is used by new conversations and by scripts that do not specify a model; after the upgrade it keeps your previous default. When the default target is temporarily unavailable (for example after clearing its API key), another available model is used automatically until it is available again; if its service or model is deleted, another available model is used as well. The "Default model" of a service is renamed "Preferred model"
* `Improvement` Services are now added and edited on a full-screen "Service details" page instead of the former bottom sheet form: the connection details (service name, provider, Base URL, API key and so on) are at the top, and the "Model list" is below; long-press a row or drag its handle to change the order, or use each row's menu to set it as the preferred model, mark it for "Image input", move it up or down, or remove it, and add several models at once. The former "Model ID", "Default model" and "Image input models" selectors are therefore gone. Tap the check mark at the top right to save, then test the connection on the same page; going back with unsaved changes asks first, and what you have entered is kept when the screen rotates or the system reclaims the app in the background. After reordering, the default target and favorites still point to the same models
* `Improvement` Downloading a recommended local model now takes one step: after you confirm, the model is downloaded to the app's private storage and imported automatically once it is verified, with no more "Choose save location" first and "Import downloaded model" afterwards. While it downloads, the "Models and services" page shows a download card where you can pause, resume or cancel, and retry after a failure; if the network drops or the app is closed, the downloaded part is kept and resuming or retrying continues from where it stopped. The download confirmation shows the model's minimum memory and warns when this device has less, but still allows the download. Model files downloaded elsewhere earlier can still be added with "Import model file". Downloaded models are stored in the app's data and are deleted when the app data is cleared or the app is uninstalled
* `Improvement` In the model switching panel each model now appears only once: favorites are shown only in the "Favorites" group at the top instead of also appearing under their service, and return to their place when unfavorited; search results are no longer grouped and are listed in their usual order. Each row shows its service underneath, and local models show their file size; the "On this device" group is renamed "Local models"

# v1.6.0

###### 2026/11/12

* `Feature` Online profiles can set a default effort (Low, Medium, High, Extra high or Max; with "Default" the model decides), and Anthropic profiles can use fast mode by default (faster replies at a higher price); both can also be changed for the current conversation only, the change is saved with the conversation, and new conversations start from the profile defaults. When a model does not support the chosen level, the closest lower level is used
* `Feature` Switching models now opens a panel from the bottom: search, models grouped by profile, favorites pinned on top, and labels showing which models support image input, reasoning and fast mode; the panel also changes the effort and fast mode of the current conversation, and "Manage models and services" at the bottom opens the model settings. The bar at the top of the chat shows the effort and fast mode of the current conversation
* `Feature` Chats now show how the model thinks (Claude 5 and newer, Gemini 2.5 and newer, and OpenAI-compatible services that return their reasoning, such as DeepSeek): while a reply is generated, the reasoning streams in a card above it; when the reply is done the card folds into "Thought for N s", and a tap opens or closes it. The reasoning is saved with the conversation, but it is not read aloud and message search does not match it. "Show reasoning while generating" and "Collapse reasoning when done" in the settings are on by default and can be turned off. Scripts that call AI through the plugin do not receive the reasoning
* `Fix` A reply being generated was stopped when the screen was rotated or when dark mode, the language or the message font size was changed; the reply now keeps generating, and a reply that finishes while you are away from the chat is shown in full when you come back
* `Fix` After tapping "Stop", the online request kept running in the background until the reply was complete (and still counted toward usage); tapping Stop now ends the request within 1 second, and so do starting a new conversation, switching conversations, switching models and deleting messages
* `Fix` Newer Claude models rejected requests from Anthropic profiles that set temperature, Top K or Top P; Anthropic profiles no longer send these three settings, and the settings page says so
* `Fix` After the default model of a profile was changed, a new conversation that was already open but had no messages kept the old default model; it now switches to the new default model (unless you picked another model in that conversation)
* `Fix` When adding or editing an online profile, swiping down on the form easily closed it and everything you had typed was lost; the form now closes only with the close button at the top right, "Cancel" or Back, asks first when there are unsaved changes, and keeps what you typed when the screen is rotated
* `Fix` Online model catalog updates failed after the Gemini documentation moved and changed its table format

# v1.5.0

###### 2026/11/04

* `Feature` Independently signed applications can request explicit AI access in the plugin, bound to their identity and installation, with settings-based revocation that stops active requests
* `Feature` The screen stays on while a reply is being generated instead of turning off at the screen timeout
* `Fix` Subsequent AI requests failed when service shutdown discarded a queued session cleanup
* `Fix` Sequential AI requests were rejected while a cancelled model session was still closing
* `Fix` Anthropic profiles always sent max_tokens=1024 while the maximum output tokens setting was unlimited, so replies were cut off around 1024 tokens without any notice (Claude models that think by default count their thinking against it); the default is now 16384 tokens, and a reply stopped at the output limit is marked "Output limit reached" under the message, for OpenAI-compatible and Gemini profiles as well
* `Fix` The connection test allowed the model only 8 output tokens; Claude models that think by default spent them all on thinking and returned no text, so a correctly configured Anthropic profile was reported as an invalid response. The test now allows 1024 tokens
* `Fix` When cloud generation or the connection test failed, only a fixed failure category was shown and the actual cause (for example an exhausted account balance) stayed invisible; the HTTP status and the provider's own explanation (at most 300 characters, with keys masked) now appear under the failure message

# v1.4.1

###### 2026/10/20

* `Fix` The 1.4.0 APKs declared only the 64-bit processor architectures (arm64-v8a, x86_64), so on 32-bit phones (armeabi-v7a, x86) the AutoJs6 Plugin Center could treat the plugin as incompatible; all four architectures are now declared correctly

# v1.4.0

###### 2026/10/20

* `Hint` 3-Stone AI does not ask for the microphone permission; the recognition app records. 3-Macaw Speech works fully offline, other recognition or speech apps may use the network, and recognized text goes to the selected model just like typed text
* `Feature` A microphone button inside the input box: tap and speak, and the recognized text is inserted at the cursor so you can check it before sending. With 3-Macaw Speech installed it recognizes offline; otherwise the phone's own speech recognition app is used
* `Feature` Replies can be read aloud: long press a reply and choose "Read aloud", or turn on "Read replies aloud automatically" in Settings > Voice to hear new replies while they are generated. Heading and list markers are skipped, and code blocks and tables are announced in one sentence. The offline voices of 3-Macaw Speech are preferred when it is installed
* `Feature` Long press the microphone for a voice conversation: what you say is sent at once, and the app listens again after the reply has been read. A status bar above the input box shows what is happening and lets you leave at any time; while a reply is read, tap it to interrupt and speak right away
* `Feature` Ask through the 3-Macaw Speech digital assistant: hold the power button, speak and tap "3-Stone AI" to get the answer in a new conversation, read aloud. Another such question within 10 seconds only fills the input box, so it cannot be misused
* `Feature` Settings have a new "Voice" group: automatic reading, "Prefer 3-Macaw Speech" and which app currently dictates and reads

# v1.3.0

###### 2026/10/15

* `Feature` Unify standalone settings with flat groups, consistent rows and centered rounded dialogs. Language, night mode, theme color and launcher icon changes apply only after OK; Cancel leaves saved values unchanged. Theme color follows AutoJs6 by default, with a shared palette, HEX/RGB input and a local preview. Neutral surfaces keep stable colors and controls follow the selected theme. The launcher default is adaptive automatic, while upgrades preserve explicit saved choices.
* `Fix` Through an OpenAI-compatible gateway, Claude or Gemini models stream an empty string instead of "{}" as the arguments of a tool without parameters; the session rejected it as an invalid response and ended with ONLINE_INVALID_RESPONSE (3-Stove Agent's first Claude Code Ex tool round and one Gemini Ex round on the AIGoCode gateway failed this way). Blank arguments are now treated as an empty object, and the assistant message replayed to the model carries the normalized form
* `Fix` Native tool continuation rounds used to subtract the output tokens of earlier rounds from the first request's ceiling, so long turns ended with ONLINE_INVALID_RESPONSE once the ceiling ran out after about ten rounds (3-Stove Agent's calculator case failed between the 9th and 14th call on both Claude Code Ex and Gemini Ex). Every continuation now keeps the full ceiling and the caller's task budget bounds the total, matching the 2026-09-29 decision to reset the continuation timeout
* `Fix` The Gemini GenerateContent protocol sent tool declarations and the responseSchema with their JSON Schema untouched, and the official API answers HTTP 400 ("Unknown name additionalProperties") for keywords it does not know, so every native tool task of 3-Stove Agent failed at its first call with ONLINE_REQUEST_REJECTED. Only the subset Gemini supports (type, enum, properties, required, items, anyOf, minimum / maximum, minLength / maxLength, default and similar) is sent now; callers keep validating arguments and replies against the full schema
* `Fix` Complete translations for model sampling and online provider settings
* `Fix` In the Arabic interface, 3-Stone AI, 4-bit and model revisions that start with a digit no longer appear reversed or split (such as Stone AI-3 and bit-4)
* `Improvement` Keep the About icon in a rounded frame while its transparent interior reveals the surrounding page background. Show the launcher icon choices from the top with smaller explanatory notes.
* `Improvement` Consistent visual sizing for launcher and Plugin Center icons, with transparent backgrounds and neutral black, white or grayscale artwork
* `Improvement` Plugin Center icons use the sizes, positions, light and dark artwork, and circular backgrounds adjusted in Icon Studio, retaining reproducible sources and parameters
* `Improvement` Android App info icons share Icon Studio artwork and light/dark backgrounds while preserving transparent Plugin Center artwork and existing launcher choices

# v1.2.1

###### 2026/09/29

* `Hint` The 32-bit compatibility changes are an unpublished candidate. See docs/dev/32-bit-compatibility-2026-09-29.md for executed checks and remaining device coverage.
* `Fix` When the host delivers a tool-result image as a regular-file descriptor from its private cache, re-opening it through /proc/self/fd failed with EACCES because the Provider may not traverse the host directory, and the whole tool continuation aborted with PROTOCOL_VIOLATION; regular files now fall back to reading through a duplicated descriptor (a regular file never blocks a read) while pipes keep the private non-blocking re-open. On a real device (Sony XQ-DQ72, AutoJs6 5298, 3-Stove Agent 1.3.0) every screen_capture image sent to Codex / Gemini models as a native tool result hit this failure
* `Fix` After each accepted batch of native tool results, the generation timeout starts over: previously the whole tool turn, including the time spent waiting for results, shared the first request's timeout, so multi-round tasks longer than it always ended with TIMEOUT; now only waiting without submitting results still expires on the original timeout. Matches the corresponding AutoJs6 host and 3-Stove Agent changes
* `Fix` Add x86 and armeabi-v7a APKs for online AI and host integration; check process ABI and the installed native payload before exposing local inference, and explain unavailable local models in the manager
* `Improvement` Unify Three series launcher icons with light artwork on a stable dark background, while plugin-center and in-app icons remain transparent and follow the application theme; prevent nested launcher backgrounds on some devices

# v1.2.0

###### 2026/09/26

* `Hint` Development candidate, not published. Online tool calling uses AI Provider V2; Agent integration requires the host native-tool broker in build 5297+ and an Agent version that supports it. The plugin returns calls to the host and does not execute device actions itself.
* `Hint` In Settings > Online AI, edit a profile and select its models with image input. Existing profiles stay disabled by default. Images go only to that configured service. LiteRT and persistent ai.session remain text-only; Agent screenshots require Android 11+, compatible AutoJs6 and AI Agent versions, the observe group, and image input enabled for the exact selected model.
* `Hint` AiGoCode gpt-5.6-sol passed real initial-image and tool-result-image probes on Provider 1.2.0 / build 218. These synthetic-image probes do not establish support for other targets or a complete Agent visual task.
* `Feature` Native tool calling for online OpenAI-compatible, Anthropic Messages and Gemini GenerateContent targets, including streamed arguments, parallel calls and result continuation
* `Feature` Online JPEG/PNG image input and image tool results through negotiated AI Provider 2.1, with explicit per-model settings
* `Feature` Add automatic and manual online model-preset updates with local caching and offline fallback, preserving saved profiles and custom model IDs
* `Feature` Group model presets by vendor and expand OpenRouter choices to include Qwen, Kimi, GLM, Grok, Meta and MiniMax, while preserving exact model IDs
* `Fix` Descriptor reads release workers on cancellation or timeout and preserve reliable-pipe producer errors
* `Fix` Preserve fixed online failure categories across AI Provider callbacks without exposing request or response contents or adding automatic retries
* `Fix` Fix IntelliJ IDEA F10 runs failing with No APK found by using the actual AGP APK directory for each build variant while retaining 16 KB alignment checks
* `Improvement` Refresh online model presets from official catalogs, including Claude Fable 5.1 and other current models, and remove retired model IDs while preserving existing profiles and custom models

# v1.1.4

###### 2026/09/19

* `Fix` SDK XML v4 parsing warnings with AGP 9.1 and APK native alignment checks incorrectly triggered by JVM unit-test assembly tasks, using shared build plugins 1.8.3
* `Improvement` Raise targetSdk to 37 (Android 17) after compileSdk; the plugin's behavior does not depend on the new target

# v1.1.3

###### 2026/09/15

* `Improvement` Raise compileSdk to 37 (Android 17); targetSdk stays at 36 until the behavior that depends on the target is verified

# v1.1.2

###### 2026/09/13

* `Fix` Keep the plugin version date in English regardless of the build machine locale
* `Improvement` Consistent localized resources, explicit plugin activation and validated release preparation

# v1.1.1

###### 2026/09/12

* `Feature` Delete the selected local model with automatic selection of a remaining model and a clear notice in conversations that use the deleted model
* `Improvement` Mark imported models in the catalog and provide download confirmation and destination selection directly from their cards
* `Improvement` Improve settings summaries when following AutoJs6, theme color contrast, and chat history actions
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
