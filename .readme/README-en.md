<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>Unified AI plugin. LiteRT-LM stays local; online targets are always selected explicitly</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The current README.md supports the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### Introduction

******

3-Stone AI is the official AI text-generation plugin for AutoJs6. It runs user-imported LiteRT-LM models on an explicitly selected CPU or compatible GPU backend and uses user-configured online services for online generation. Local and online destinations share one AI Provider V2 target catalog, controlled streaming pipeline, and explicit selection boundary. Local targets never access the network or upload data; online targets run only after the user selects one and remain bound to its plugin-managed credential and declared HTTPS origin.

******

### Features

******

- Import a `.litertlm` model package through the Android system picker and keep a verified copy in app-private storage.
- Download a pinned, ungated LiteRT Community model from the built-in catalog in one step: it is written to app-private storage and imported automatically once its exact size and SHA-256 are verified. A downloading model appears as a card in the "Local models" section with progress, pause, cancel, and retry on failure, and an interrupted download continues from the part already received; the download confirmation shows the model's minimum memory and warns when the device has less.
- Preflight private storage before opening the picker, show the current import budget and estimated private-copy footprint, and recheck the selected file before copying.
- Create local generation requests from plain-text system, user, and assistant history.
- Forward `temperature`, `topK`, `topP`, and `maxTokens` from AutoJs6 `ai.ask`, `ai.chat`, and `ai.stream` to LiteRT-LM.
- Constrain output natively with LiteRT-LM JSON Schema through AutoJs6 `structuredJson` and `responseSchema`; completed values remain JSON text for `JSON.parse`.
- Report exact LiteRT-LM input, output, and total token counts plus provider-side generation duration through AutoJs6 `ai.chat().usage` and stream usage events.
- Keep multi-turn context in one native LiteRT-LM Conversation through AutoJs6 `ai.session`, sending only the new user prompt on later turns.
- Reuse an initialized Engine by model SHA-256, eliminating repeated cold starts for consecutive requests to the same model.
- Optionally initialize each imported model once, persist its Available/Incompatible status, and rerun the check from the model manager.
- Deliver text chunks in order with credit backpressure and publish exactly one completed, failed, or cancelled terminal state.
- List, rename, check, and delete imported models in the "Local models" section, with each card showing the space the model takes; deleting a model also deletes the LiteRT-LM engine cache created when it first loaded. "Clean unreferenced files" reclaims unreferenced model files, leftover engine caches and attachments no longer in use.
- Select an explicit `cpu`, `gpu`, or `npu` backend through AutoJs6; CPU is the default, GPU is exposed only after an OpenCL loader probe, and NPU is reported unavailable because its EAP runtime is not packaged.
- Manage built-in and custom online services, Android Keystore credentials, the single default target (an online or a local model), metered-network access, and explicit bounded connection tests in Settings > Models and services. Services are edited on a full-screen "Service details" page: each service can hold several models, which can be reordered by dragging and given a preferred model, and changes are saved only with the check mark.
- Set up to 16 custom request headers per service under "Request headers" on the service details page; each value is a fixed value, a secret value or "Generated per conversation" (derived from the service and the conversation, so it stays the same within a conversation). Presets are provided for OpenCode Go session, OpenRouter attribution, Helicone and Cloudflare AI Gateway; when the service address is on opencode.ai without the session header, or the service returns MissingSessionID, a way to add it is offered. Headers the plugin sends itself or that the HTTP connection manages cannot be customized; User-Agent replaces the default value, and anthropic-beta is merged with the plugin's value. Request headers take effect automatically with the service configuration and are not exposed to scripts.
- Bind each launcher conversation to one local or online target snapshot; changing a populated conversation recommends a new conversation, while continuing requires explicit confirmation and records the change.
- Record the actual target, provider, model, and locality on every assistant response; regeneration reuses that recorded target, rejects identity drift or unavailability, and never silently falls back to the conversation default.
- Keep local and cloud generation failures on their selected boundary: launcher chat appends a bounded non-secret reason, states that no cross-boundary fallback occurred, and exposes an explicit manual target switch without discarding partial output.
- Native tool calling for online OpenAI-compatible, Anthropic Messages and Gemini GenerateContent targets, including streamed arguments, parallel calls and result continuation.
- Online JPEG/PNG image input and image tool results through negotiated AI Provider 2.1, with explicit per-model settings.
- Keep online model presets current with a public catalog from the project on GitHub. Automatic updates are enabled by default and check once every 24 hours when the "Models and services" page is opened; the "Online services" section menu can turn them off or refresh manually. Updates respect the metered-network setting, keep the cached or built-in list available offline or on failure, and leave saved services and custom model IDs unchanged.
- Group model presets by vendor and expand OpenRouter choices to include Qwen, Kimi, GLM, Grok, Meta and MiniMax, while preserving exact model IDs.
- Unify standalone settings with flat groups, consistent rows and centered rounded dialogs. Language, night mode, theme color and launcher icon changes apply only after OK; Cancel leaves saved values unchanged. Theme color follows AutoJs6 by default, with a shared palette, HEX/RGB input and a local preview. Neutral surfaces keep stable colors and controls follow the selected theme. The launcher default is adaptive automatic, while upgrades preserve explicit saved choices.
- The settings home page keeps only the appearance settings and five entries: "Conversation", "Models and services", "Voice", "Privacy and permissions" and "Updates and about". Each setting appears in one place, and the release history is under "Updates and about". The chat menu and sidebar reach model settings through "Settings"; opening this plugin's settings from AutoJs6 goes straight to "Models and services", and Back returns to the settings home page.
- Chat replies are no longer tied to the chat screen: they keep generating when the screen rotates or dark mode, the language or the message font size changes, and a reply that finishes while you are away from the chat is shown in full when you return (a reply is still lost if the system ends the app process). Tapping "Stop", starting or switching conversations, switching models and deleting messages end the online request within 1 second, so a stopped reply no longer adds usage.
- Online services can set a default effort (Default, Low, Medium, High, Extra high or Max; "Default" lets the model decide), and Anthropic services can turn on fast mode by default (faster replies at a higher price); both can be changed for the current conversation only, and the change is saved with it. When a model does not support the chosen level, the closest lower level is used. Scripts that call AI through the plugin interface do not use these two settings.
- Switching models opens a panel from the bottom: favorites are in the "Favorites" group at the top and the rest are grouped by service, with each model listed only once; search results are not grouped; each row shows its service underneath (local models show their file size) and labels for image input, reasoning and fast mode; the panel also changes the effort and fast mode of the current conversation, and "Manage models and services" at the bottom opens the model settings. The bar at the top of the chat shows the effort and fast mode of the current conversation.
- Shows how the model thinks (Claude 5 and newer, Gemini 2.5 and newer, and OpenAI-compatible services that return their reasoning, such as DeepSeek): while a reply is generated, the reasoning streams in a card above it, then folds into "Thought for N s"; a tap opens or closes it. "Show reasoning while generating" and "Collapse reasoning when done" in the settings are on by default. The reasoning is saved with the conversation, but it is not read aloud, searched or copied, and scripts do not receive it.
- Tap "+" on the left of the input box to add attachments, up to 20 per message: images come from the system photo picker (oversized images are scaled down) and go to models with "Image input" turned on; UTF-8 text files (such as txt, md, json, csv and code files) are sent as text; PDFs go to services whose provider is Anthropic or Gemini. Local models do not take attachments yet, and attachments the current model cannot take are dimmed and cannot be sent. Attachments are saved with the message, which shows a thumbnail or file name; tap one to open it in another app. Later turns resend earlier attachments as far as the context allows and only name the files that do not fit.
- Regenerating a reply or editing and resending a message adds a new version of that turn instead of deleting the conversation after it: "< 2/3 >" under the reply switches versions, the later messages switch with it, and a reopened conversation shows the version last chosen. Search, export, read-aloud, usage and continued chat use only the version shown.
- Conversations can be exported with "Export as Markdown" (you pick the location in the system save dialog, and other Markdown readers display the file correctly) or shared with "Share as image" (a long image drawn like the chat page and shared through the system share sheet), from the chat menu or by long-pressing a conversation in the sidebar. Only the versions shown are exported; when a conversation has reasoning, you are asked whether to include it, and it is left out by default.
- Conversation history uses format v6, which also stores the effort and fast mode of each conversation, the reasoning text and time of each reply, and the attachment details and other versions of messages; conversations saved by 1.5.0 and earlier still open. After going back to 1.6.0 or earlier, only the versions shown at that time remain: the old version discards the other versions of every conversation as soon as it opens the chat page, and upgrading again cannot bring them back.
- A microphone inside the input box: tap to dictate, and the recognized text goes to the cursor for you to check before sending; long press for a voice conversation that sends what you say and listens again after the reply has been read.
- Read replies aloud: long press a reply and choose "Read aloud", or turn on "Read replies aloud automatically" in Settings > Voice to hear new replies while they are generated. Heading and list markers are skipped, and a code block or table is announced in one sentence.
- Answers the `VOICE_COMMAND` of the 3-Macaw Speech digital assistant: hold the power button, speak and tap "3-Stone AI", and the question is sent in a new conversation and the answer read aloud.

******

### Voice

******

Dictation and reading use the Android system interfaces (`RecognizerIntent` and `TextToSpeech`). 3-Stone AI never records audio itself and does not ask for the microphone permission. With 3-Macaw Speech installed, "Prefer 3-Macaw Speech" in Settings > Voice is on by default: dictation opens its offline recognition sheet and reading uses its offline voices. Without it, or with the option off, the phone's own recognition app and default text-to-speech engine are used. In 1.4.0 every round of a voice conversation opens the recognizer's screen; while a reply is read, tap the status bar above the input box to interrupt and speak right away, or use its button to leave.

- **Several speech recognition apps ask me to choose every time?** Keep "Prefer 3-Macaw Speech" on to use 3-Macaw Speech directly. With it off the system decides, and you can make one app the default in the system chooser.
- **Pauses between sentences on a slow phone?** Some voices (such as MeloTTS and Kokoro) synthesize slower than they speak on slower phones. Choose AISHELL-3, Piper or Matcha as the default voice in 3-Macaw Speech.
- **The microphone says there is no recognition model?** The 3-Macaw Speech sheet guides you through downloading one; until then 3-Stone AI does not switch to another recognizer on its own. Turn off "Prefer 3-Macaw Speech" to use another app meanwhile.
- **How do I ask through the 3-Macaw Speech digital assistant?** Make 3-Macaw Speech the default digital assistant, hold the power (or home) button, speak, and tap "3-Stone AI" on the result card. 3-Stone AI opens a new conversation, sends the question at once and reads the answer; unlock the phone first if it is locked. Within 10 seconds of the previous such question the text only fills the input box and is not sent.
- **Where does the recognized text go?** To the selected model, like typed text; an online model sends it to its provider. 3-Macaw Speech works fully offline; other recognition or speech apps may use the network.

******

### Model and data formats

******

Supported model and input formats:

```text
model package: .litertlm
input: text/plain history + application/json schema; negotiated 2.1: image/jpeg and image/png descriptors
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### Plugin interface

******

The host discovers and calls the plugin with the following identities:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V2 (2.0 / 2.1)
required host build: 5276
```

AI Provider V2 exposes one paged catalog of `local:*` and `profile:*` targets. Each target independently declares provider, model, locality, configuration and availability, capabilities, limits, controls, and HTTPS origins. A local-only catalog declares ON_DEVICE/NONE; the presence of online profiles declares HYBRID/PLUGIN_MANAGED and the exact union of their HTTPS origins. Local backend profiles remain an optional target control, and unavailable profiles or targets never fall back silently.

Host build 5276 or later and Android 7.0 or later are required. Build outputs include armeabi-v7a, arm64-v8a, x86, x86_64, universal APK variants. On x86 and armeabi-v7a devices, use the matching standalone APK for the app, online AI and host plugin integration. LiteRT-LM local inference requires arm64-v8a or x86_64 and its packaged native library. The universal APK contains only 64-bit native libraries and cannot be installed on 32-bit-only devices.

******

### Host integration status

******

> In AutoJs6 (build 5276 and later), `ai.catalog()` returns every imported local model and configured online profile as one target catalog, including exact ID, provider, model, locality, configuration, availability, capabilities, controls, limits, origin, and local backend profiles. Pass an exact `target` to `ai.ask`, `ai.chat`, `ai.stream`, or `ai.session`; `target` alone selects the official 3-Stone AI plugin, while `plugin: true` uses its declared default target. Local targets may expose `cpu`, `gpu`, and unavailable `npu`; online targets have no local execution profile. An unavailable backend or target fails without fallback, and local/online routes never switch automatically. Completed and streamed responses expose target, plugin, profile, reasoning, finish reason, complete usage, and provider-measured duration. Stable errors distinguish a missing or disabled provider, an unknown, unconfigured, unavailable, or capability-mismatched target, and an unavailable backend. `responseSchema` implies structured output; `structuredJson: true` without a schema uses a default object-root schema, and persistent sessions fix the same target, schema, and optional backend for every turn.

Independently signed applications can request explicit AI access in the plugin, bound to their identity and installation, with settings-based revocation that stops active requests. This grant covers local models and saved online accounts. Online calls may consume paid quota. Use ai.hasAccess() and ai.requestAccess() in Node.js, Bun or Workers.

******

### Security and privacy

******

The plugin requests `INTERNET` for user-triggered recommended-model downloads, requests to a user-configured online target, and public model-preset catalog updates from GitHub on the "Models and services" page. Catalog updates need no API key, make no inference calls, and never start during plugin activation or local generation. It requests no broad storage permission. Recommended model downloads use immutable HTTPS revisions and pinned byte counts and SHA-256 digests, write only to a partial file in app-private `files/model-downloads` (retried automatically and resumed after an interruption), and are moved into `files/models` and handed to the import path for atomic activation only after the LiteRT-LM header, size, digest, flush, and fsync all pass. Import still reads only a system-picker URI, streams a verified copy into app-private `files/models`, and activates it atomically. The original host provider service also verifies the AutoJs6 package name, calling UID ownership, matching signatures, target metadata, and declared origin boundaries. Voice features do not ask for the microphone permission; the recognition app records. Recognized and spoken text is not logged, and dictated messages are stored like typed ones. Reasoning returned by a model is stored only in the local conversation history; it is not sent back to the online service in later turns, and scripts do not receive it through the plugin interface. Chat attachments are read through the system pickers, with no storage or photo permission; the chosen files are copied into app-private `files/attachments`, sent only to the service of the chosen online model, and deleted with the conversation. Opening an attachment in another app grants temporary read access through a FileProvider that exposes only `files/attachments`. A shared long image is written to the app cache `cache/share` and handed to the share sheet by a separate FileProvider that exposes only that directory; before each new image, images older than 24 hours are deleted and at most 3 are kept. Exported Markdown is written only to the location you choose in the system save dialog. Among a service's custom request headers, secret values are stored encrypted on the device through Android Keystore, like the API key; they are never shown back in the UI or included in exports, and must be entered again after importing, cloning, or changing the provider or HTTPS origin. API keys and secret values that appear in error details from the provider are replaced with `[redacted]`. Fixed values are stored in plain text and included in exports, so do not enter keys as fixed values.

******

### Operational limits

******

- Model imports have an 8 GiB hard limit and must leave at least 256 MiB of free space.
- Only one unfinished model download is kept; starting another model discards it (the confirmation says so). The shared download library of the official AutoJs6 plugins writes the download to app-private `files/model-downloads/<sha256>/model.litertlm.part`: after a network drop it retries automatically twice (after about 2 and 5 seconds), each time continuing with an HTTP Range request; pausing, closing the app, or process death keeps the file, and resuming or retrying first re-verifies the part already received and then continues; cancellation, invalid content, or a digest mismatch deletes it. An unfinished download is never deleted because of its age. A verified file is moved to `files/models/.download-<sha256>.part` and handed to the import path. On a cold start the newest download of a recommended model that has not been imported is restored as paused and the others are deleted; an unfinished file that 1.7.0 left at `files/models/.download-<sha256>.part` moves to the new location and continues from the bytes already received.
- Downloaded and imported local models are stored in app-private data (`files/models`), and the space they take is shown on the cards in the "Local models" section and in the model switching panel. Uninstalling the plugin or clearing its app data deletes these models as well; model files downloaded elsewhere before can be brought in with "Import model file".
- An application-scoped single-import coordinator keeps ongoing work alive across Activity recreation. A fsynced pending journal supports cold-start recovery and cleanup of stale `.incoming`, `.current`, and `.pending` temporary files. Recovery deletes only a destination created by the current attempt and never published through current metadata; published, current, and historical hash generations are retained.
- To avoid cross-process races with the isolated `:provider` process, imports do not automatically delete previous SHA-256-named model generations. The "Local models" section can delete catalog models and reclaim hash-named files no longer referenced by the catalog.
- At most one generation session is active in the process. Request descriptors are duplicated before asynchronous work and closed under protocol quotas.
- The provider caches at most one initialized Engine by model SHA-256 and backend profile. Consecutive requests with the same pair reuse it; changing either key releases it, as do five idle minutes or explicit Android memory pressure after the active session.
- A model health check proves only that `Engine.initialize()` succeeds on the current device and bundled runtime; it does not assess output quality and can be rerun after device or runtime changes.
- The provider advertises a 256 KiB context ceiling and a 64 KiB output ceiling. Requests and models may impose lower limits.
- A response schema must be a JSON object no larger than 64 KiB. Supported keywords are those implemented by the bundled LiteRT-LM/LLGuidance runtime; completed output is parsed and validated strictly, so reserve enough `maxTokens` for the entire JSON value.
- `maxTokens` accepts integers from 1 through 2,147,483,647. Omitting it leaves the output-token count to the model or engine; the provider's 64 KiB output safety limit remains. `temperature` must be finite and non-negative, `topK` a positive integer, and `topP` finite from 0 through 1. Leaving all three sampling controls unset preserves model/engine defaults; a partial override fills the omitted controls with the LiteRT-LM baseline `topK: 1`, `topP: 0.95`, and `temperature: 1`.
- Streaming uses finite credits and bounded chunks to prevent unbounded buffering or callbacks without backpressure.
- Usage token counts come from LiteRT-LM's conversation KV-cache and decode counters, without character-based estimation. `durationMillis` measures the provider generation call and excludes host discovery, binding, model listing, and dispatch time.
- A persistent `ai.session` permits one active turn, retains its native Conversation after normal completion, and must be recreated after cancellation, timeout, generation failure, or explicit close.
- Cancellation, session close, and timeout stop result publication and finish the request through one terminal state.
- In Settings > Models and services, open a service's "Service details" and turn on "Image input" from the row menu of each model in the model list that accepts images. It is off by default for existing services. Images go only to that service. LiteRT and persistent ai.session remain text-only; Agent screenshots require Android 11+, compatible AutoJs6 and AI Agent versions, the observe group, and image input enabled for the exact selected model.
- Attachment limits: 5 MB per image, 256 KB per text file, which must be valid UTF-8, and 32 MB and at most 100 pages per PDF (encrypted PDFs are rejected); 20 per message and 48 MB in total per conversation. Raw attachment bytes per request: 32 MB for Anthropic and OpenAI-compatible services, 20 MB for Gemini; OpenAI-compatible services take no PDFs. A message over the chosen target's limit is not sent, and the limit is stated.
- Each turn keeps at most 8 versions; when the hidden versions in a conversation exceed about 2 MiB in total, the oldest go first. Deleting a message with several versions deletes only the version shown.
- A long image taller than 16384 pixels is scaled down proportionally; when memory runs out, the scale is halved and retried, and if it still fails you are asked to export Markdown instead. Conversations whose reply is still being generated cannot be exported.
- AiGoCode gpt-5.6-sol passed real initial-image and tool-result-image probes on Provider 1.2.0 / build 218. These synthetic-image probes do not establish support for other targets or a complete Agent visual task.
- Online failures expose only fixed `ONLINE_*` categories in the optional `providerCode` field. Unknown exceptions have no category; URLs, credentials, provider responses and original exception text are never included. The failure code and retry policy remain unchanged.
- Each service has at most 16 custom request headers: names are 1 - 64 bytes of RFC 9110 token characters and unique regardless of case; fixed and secret values are 1 - 1024 bytes of visible ASCII characters and spaces; all request headers together are at most 8192 bytes (each "Generated per conversation" entry counts as 36 bytes). The 22 headers that the plugin sends or the HTTP connection manages cannot be customized and are rejected on import as well. A secret header whose value was never set is not sent; when a stored secret record cannot be read, the whole request fails instead of being sent without the header.

******

### Undeclared capabilities

******

- The plugin interface does not declare reasoning output, so scripts do not receive reasoning (it is shown only in the launcher chat). Local LiteRT-LM targets still do not declare tools.
- Native tools allow at most 16 rounds and 32 outstanding calls. Results must exactly match the pending batch; context/output limits, cancellation and the original deadline still apply. Native tools cannot be combined with persistent ai.session turns; initial tool-role history is not accepted.
- Downloading model files from arbitrary URLs is unsupported. Online model presets can be refreshed from the public project catalog, but do not create services or query account access. Launcher chat and AI Provider V2 expose only imported local models and explicitly configured online services; downloadable local models remain limited to the pinned built-in recommendations.
- NPU inference is not declared: the profile is discoverable as `unavailable` with `npu-runtime-not-packaged`. GPU is declared only when `libOpenCL.so` is loadable, and a `.litertlm` extension alone still does not guarantee model initialization.

******

### Roadmap

******

The roadmap is organized around deliverable user-facing features, each independently checkable

- [View ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Release history

******

# v1.8.0

###### 2026/11/27

* `Hint` After you add request headers to a service, the service configuration is saved in a new format (so are exports that contain request headers), which 1.7.2 and earlier cannot read. To go back to an older version, first delete the request headers of all services and save
* `Feature` Online services can have custom request headers: on the "Models and services" page, open a service and tap "Request headers" under "Connection" to add, edit or delete them, up to 16 per service. There are three value sources: a fixed value; a secret value (check "Secret"; it is checked by default when the name contains auth, key, token or secret); and "Generated per conversation" (stays the same within a conversation, even after switching to another model of the same service). Services of all 6 providers can use them. Request headers take effect automatically with the service configuration, so scripts need no changes, and persistent script sessions (ai.session) also use a fixed session ID
* `Feature` Secret values are stored encrypted on this device; the UI only shows "Set", they are not shown when editing, and they are not included in exports. After importing or cloning a service, secret values are "Not set" and the service card shows "Header value needed"; the header is not sent until you enter the value again. When you change the provider or HTTPS origin and save, stored secret values are cleared together with the API key. If an API key or secret value appears in error details returned by the provider, it is replaced with [redacted]
* `Feature` Headers the plugin sends itself (for example Authorization, x-api-key, Content-Type) and headers managed by the HTTP connection (for example Host, Content-Length) cannot be customized, and the dialog states why; User-Agent can be customized (replacing the plugin's default), and anthropic-beta is merged with the value the plugin computes for the request. When a name or value has an invalid format or length, or all request headers together exceed 8192 bytes, the dialog also states why
* `Feature` When adding a request header you can expand "Common presets": choosing OpenCode Go session or OpenRouter attribution adds the matching headers, and choosing Helicone or Cloudflare AI Gateway fills in the name so you only need to enter the key. When the service address is on opencode.ai but the session header x-opencode-session is missing, the service details page shows a hint with an "Add" button; when testing the connection or chatting gets a MissingSessionID error, the error message explains the cause and offers an "Add session header" button
* `Fix` When the plugin language and the system language are written in opposite directions (for example the plugin switched to Arabic on a Chinese system), the buttons at the bottom of dialogs such as "Cancel" and "OK" are now mirrored with the rest of the interface instead of staying on the system language's side

# v1.7.2

###### 2026/11/20

* `Improvement` The release history page now expands and collapses by version: a collapsed version shows its date and the number of entries per category, an expanded one names each category once; all versions can be expanded or collapsed at once, the newest one opens expanded and the subtitle shows the plugin name; the page and its entry in "Updates and about" are now called "Release history" instead of "Version history"
* `Improvement` Rewrote the instructions shown in Plugin Center in five parts: getting started, main features, using it in scripts, requirements and FAQ
* `Dependency` Added Jetpack Compose BOM 2026.09.00 (release history page)
* `Dependency` Upgraded AndroidX Core to 1.16.0, AndroidX Activity to 1.12.2 and AndroidX Lifecycle to 2.9.4 (needed by Jetpack Compose)
* `Dependency` Upgraded shared build plugins version 1.9.0 -> 1.9.7

# v1.7.1

###### 2026/11/17

* `Improvement` When a recommended local model download is interrupted (for example, the network drops), it is retried automatically twice (after about 2 and 5 seconds), each time continuing from where it stopped; the download is shown as failed only when both retries fail. After updating from 1.7.0, a model download that had not finished continues from the part already downloaded instead of starting over

##### For more releases

* [CHANGELOG-en.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-en.md)

******

### Build

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Release build:

```powershell
.\gradlew.bat :app:assembleRelease
```

Build parameters come from `version.properties`. The current minimum SDK is 24, the target SDK is 37, and JDK 21 or later is required.

The protocol ABI is supplied by repository-local AARs in `libs`:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-provider-api.aar
```

The runtime uses LiteRT-LM 0.15.0 from Maven. Release builds retain LiteRT-LM runtime classes and produce two ABI APKs plus one universal APK.

******

### License

******

Project source is licensed under MPL-2.0. LiteRT-LM and other third-party components remain under their respective licenses.

******

### Resource layout

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` generates README files and in-app changelogs in 10 languages from JSON sources. Android strings are maintained in their own resource directories.

******

### Links

******

- AutoJs6 documentation: https://docs.autojs6.com
- LiteRT-LM project: https://github.com/google-ai-edge/LiteRT-LM


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/docs/16kb.md)
