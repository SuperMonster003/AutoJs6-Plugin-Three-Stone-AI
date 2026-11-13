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

3-Stone AI is the official AI text-generation plugin for AutoJs6. It runs user-imported LiteRT-LM models on an explicitly selected CPU or compatible GPU backend and uses user-configured profiles for online generation. Local and online destinations share one AI Provider V2 target catalog, controlled streaming pipeline, and explicit selection boundary. Local targets never access the network or upload data; online targets run only after the user selects one and remain bound to its plugin-managed credential and declared HTTPS origin.

******

### Features

******

- Import a `.litertlm` model package through the Android system picker and keep a verified copy in app-private storage.
- Download a pinned, ungated LiteRT Community model directly to a user-selected SAF location with progress, cancellation, incomplete-file cleanup, and exact size and SHA-256 verification.
- Preflight private storage before opening the picker, show the current import budget and estimated private-copy footprint, and recheck the selected file before copying.
- Create local generation requests from plain-text system, user, and assistant history.
- Forward `temperature`, `topK`, `topP`, and `maxTokens` from AutoJs6 `ai.ask`, `ai.chat`, and `ai.stream` to LiteRT-LM.
- Constrain output natively with LiteRT-LM JSON Schema through AutoJs6 `structuredJson` and `responseSchema`; completed values remain JSON text for `JSON.parse`.
- Report exact LiteRT-LM input, output, and total token counts plus provider-side generation duration through AutoJs6 `ai.chat().usage` and stream usage events.
- Keep multi-turn context in one native LiteRT-LM Conversation through AutoJs6 `ai.session`, sending only the new user prompt on later turns.
- Reuse an initialized Engine by model SHA-256, eliminating repeated cold starts for consecutive requests to the same model.
- Optionally initialize each imported model once, persist its Available/Incompatible status, and rerun the check from the model manager.
- Deliver text chunks in order with credit backpressure and publish exactly one completed, failed, or cancelled terminal state.
- List, select, and rename imported models, delete unselected models, and reclaim unreferenced model files from the manager.
- Select an explicit `cpu`, `gpu`, or `npu` backend through AutoJs6; CPU is the default, GPU is exposed only after an OpenCL loader probe, and NPU is reported unavailable because its EAP runtime is not packaged.
- Manage built-in and custom online profiles, Android Keystore credentials, the default online target, metered-network access, and explicit bounded connection tests in the app settings.
- Bind each launcher conversation to one local or online target snapshot; changing a populated conversation recommends a new conversation, while continuing requires explicit confirmation and records the change.
- Record the actual target, provider, model, and locality on every assistant response; regeneration reuses that recorded target, rejects identity drift or unavailability, and never silently falls back to the conversation default.
- Keep local and cloud generation failures on their selected boundary: launcher chat appends a bounded non-secret reason, states that no cross-boundary fallback occurred, and exposes an explicit manual target switch without discarding partial output.
- Native tool calling for online OpenAI-compatible, Anthropic Messages and Gemini GenerateContent targets, including streamed arguments, parallel calls and result continuation.
- Online JPEG/PNG image input and image tool results through negotiated AI Provider 2.1, with explicit per-model settings.
- Keep online model presets current with a public catalog from the project on GitHub. Automatic updates are enabled by default and check once every 24 hours when Online AI settings are opened; a switch and manual refresh are available. Updates respect the metered-network setting, keep the cached or built-in list available offline or on failure, and leave saved profiles and custom model IDs unchanged.
- Group model presets by vendor and expand OpenRouter choices to include Qwen, Kimi, GLM, Grok, Meta and MiniMax, while preserving exact model IDs.
- Unify standalone settings with flat groups, consistent rows and centered rounded dialogs. Language, night mode, theme color and launcher icon changes apply only after OK; Cancel leaves saved values unchanged. Theme color follows AutoJs6 by default, with a shared palette, HEX/RGB input and a local preview. Neutral surfaces keep stable colors and controls follow the selected theme. The launcher default is adaptive automatic, while upgrades preserve explicit saved choices.
- Chat replies are no longer tied to the chat screen: they keep generating when the screen rotates or dark mode, the language or the message font size changes, and a reply that finishes while you are away from the chat is shown in full when you return (a reply is still lost if the system ends the app process). Tapping "Stop", starting or switching conversations, switching models and deleting messages end the online request within 1 second, so a stopped reply no longer adds usage.
- Online profiles can set a default effort (Default, Low, Medium, High, Extra high or Max; "Default" lets the model decide), and Anthropic profiles can turn on fast mode by default (faster replies at a higher price); both can be changed for the current conversation only, and the change is saved with it. When a model does not support the chosen level, the closest lower level is used. Scripts that call AI through the plugin interface do not use these two settings.
- Switching models opens a panel from the bottom: search, grouping by profile, favorites pinned on top, and labels for image input, reasoning and fast mode; the panel also changes the effort and fast mode of the current conversation, and "Manage models and services" at the bottom opens the model settings. The bar at the top of the chat shows the effort and fast mode of the current conversation.
- Shows how the model thinks (Claude 5 and newer, Gemini 2.5 and newer, and OpenAI-compatible services that return their reasoning, such as DeepSeek): while a reply is generated, the reasoning streams in a card above it, then folds into "Thought for N s"; a tap opens or closes it. "Show reasoning while generating" and "Collapse reasoning when done" in the settings are on by default. The reasoning is saved with the conversation, but it is not read aloud, searched or copied, and scripts do not receive it.
- Conversation history uses format v6, which also stores the effort and fast mode of each conversation and the reasoning text and time of each reply; conversations saved by 1.5.0 and earlier still open.
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

The plugin requests `INTERNET` for user-triggered recommended-model downloads, requests to a user-configured online target, and public model-preset catalog updates from GitHub in Online AI settings. Catalog updates need no API key, make no inference calls, and never start during plugin activation or local generation. It requests no broad storage permission. Recommended model downloads use immutable HTTPS revisions and pinned byte counts and SHA-256 digests, write only to the SAF location chosen by the user, and are never treated as complete until the LiteRT-LM header, size, digest, flush, and fsync all pass. Import still reads only a system-picker URI, streams a verified copy into app-private `files/models`, and activates it atomically. The original host provider service also verifies the AutoJs6 package name, calling UID ownership, matching signatures, target metadata, and declared origin boundaries. Voice features do not ask for the microphone permission; the recognition app records. Recognized and spoken text is not logged, and dictated messages are stored like typed ones. Reasoning returned by a model is stored only in the local conversation history; it is not sent back to the online service in later turns, and scripts do not receive it through the plugin interface.

******

### Operational limits

******

- Model imports have an 8 GiB hard limit and must leave at least 256 MiB of free space.
- Only one model download runs in the app process. Activity recreation retains progress and cancellation ownership; cancellation or failure deletes the newly created destination when supported, otherwise truncates it, while process termination can still leave a partial external document that the user should delete.
- An application-scoped single-import coordinator keeps ongoing work alive across Activity recreation. A fsynced pending journal supports cold-start recovery and cleanup of stale `.incoming`, `.current`, and `.pending` temporary files. Recovery deletes only a destination created by the current attempt and never published through current metadata; published, current, and historical hash generations are retained.
- To avoid cross-process races with the isolated `:provider` process, imports do not automatically delete previous SHA-256-named model generations. The model manager can delete unselected catalog models and reclaim hash-named files no longer referenced by the catalog.
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
- In Settings > Online AI, edit a profile and select its models with image input. Existing profiles stay disabled by default. Images go only to that configured service. LiteRT and persistent ai.session remain text-only; Agent screenshots require Android 11+, compatible AutoJs6 and AI Agent versions, the observe group, and image input enabled for the exact selected model.
- AiGoCode gpt-5.6-sol passed real initial-image and tool-result-image probes on Provider 1.2.0 / build 218. These synthetic-image probes do not establish support for other targets or a complete Agent visual task.
- Online failures expose only fixed `ONLINE_*` categories in the optional `providerCode` field. Unknown exceptions have no category; URLs, credentials, provider responses and original exception text are never included. The failure code and retry policy remain unchanged.

******

### Undeclared capabilities

******

- The plugin interface does not declare reasoning output, so scripts do not receive reasoning (it is shown only in the launcher chat). Local LiteRT-LM targets still do not declare tools.
- Native tools allow at most 16 rounds and 32 outstanding calls. Results must exactly match the pending batch; context/output limits, cancellation and the original deadline still apply. Native tools cannot be combined with persistent ai.session turns; initial tool-role history is not accepted.
- Downloading model files from arbitrary URLs is unsupported. Online model presets can be refreshed from the public project catalog, but do not create profiles or query account access. Launcher chat and AI Provider V2 expose only imported local models and explicitly configured online profiles; downloadable local models remain limited to the pinned built-in recommendations.
- NPU inference is not declared: the profile is discoverable as `unavailable` with `npu-runtime-not-packaged`. GPU is declared only when `libOpenCL.so` is loadable, and a `.litertlm` extension alone still does not guarantee model initialization.

******

### Roadmap

******

The roadmap is organized around deliverable user-facing features, each independently checkable

- [View ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Release history

******

# v1.7.0

###### 2026/11/12

* `Fix` In long conversations, earlier messages are automatically condensed into a summary to save context, but some online services and local models wrapped the summary in Markdown code block markers or added explanatory text, so condensing failed and the model was more likely to forget earlier parts of the conversation; these markers and text are now removed before the summary is read
* `Improvement` Reorganized the settings: the home page now holds only the appearance settings and 5 entries, "Conversation", "Models and services", "Voice", "Privacy and permissions" and "Updates and about", and each setting appears in only one place; "Generation" under "Conversation" is renamed "Output length and sampling", and its form no longer closes when swiped down or tapped outside, asking first when there are unsaved changes; the version history moved to "Updates and about". The chat page menu and side drawer no longer have a separate "Models" entry, everything is reached through "Settings"; opening this plugin's settings from AutoJs6 goes straight to "Models and services", and Back returns to the settings home page
* `Improvement` Merged the former "Models" and "Online services" pages into a single "Models and services" page: the default target and "Allow mobile and metered networks" are at the top, followed by two sections, "Online services" and "Local models". Every service and local model is shown as the same kind of card with a "Default", "Ready", "API key missing" or "Unavailable" label; actions such as set as default, test, rename, clone, clear API key and delete are in the menu on the right of each card, while import / export services, refresh model presets and clean up unreferenced files are in the menu on the right of each section title. Tap a local model to see its details and copy its model ID. Online profiles are now called "services" throughout
* `Improvement` Merged the former "Default online target" and the selected local model into a single "Default target", which can be any online or local model and is used by new conversations and by scripts that do not specify a model; after the upgrade it keeps your previous default. When the default target is temporarily unavailable (for example after clearing its API key), another available model is used automatically until it is available again; if its service or model is deleted, another available model is used as well. The "Default model" of a service is renamed "Preferred model"
* `Improvement` Services are now added and edited on a full-screen "Service details" page instead of the former bottom sheet form: the connection details (service name, provider, Base URL, API key and so on) are at the top, and the "Model list" is below; long-press a row or drag its handle to change the order, or use each row's menu to set it as the preferred model, mark it for "Image input", move it up or down, or remove it, and add several models at once. The former "Model ID", "Default model" and "Image input models" selectors are therefore gone. Tap the check mark at the top right to save, then test the connection on the same page; going back with unsaved changes asks first, and what you have entered is kept when the screen rotates or the system reclaims the app in the background. After reordering, the default target and favorites still point to the same models
* `Improvement` Downloading a recommended local model now takes one step: after you confirm, the model is downloaded to the app's private storage and imported automatically once it is verified, with no more "Choose save location" first and "Import downloaded model" afterwards. While it downloads, the "Models and services" page shows a download card where you can pause, resume or cancel, and retry after a failure; if the network drops or the app is closed, the downloaded part is kept and resuming or retrying continues from where it stopped. The download confirmation shows the model's minimum memory and warns when this device has less, but still allows the download. Model files downloaded elsewhere earlier can still be added with "Import model file". Downloaded models are stored in the app's data and are deleted when the app data is cleared or the app is uninstalled

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

Build parameters come from `version.properties`. The current minimum SDK is 24, the target SDK is 36, and JDK 21 or later is required.

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
