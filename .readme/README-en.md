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

******

### Security and privacy

******

The plugin requests `INTERNET` for user-triggered recommended-model downloads, requests to a user-configured online target, and public model-preset catalog updates from GitHub in Online AI settings. Catalog updates need no API key, make no inference calls, and never start during plugin activation or local generation. It requests no broad storage permission. Recommended model downloads use immutable HTTPS revisions and pinned byte counts and SHA-256 digests, write only to the SAF location chosen by the user, and are never treated as complete until the LiteRT-LM header, size, digest, flush, and fsync all pass. Import still reads only a system-picker URI, streams a verified copy into app-private `files/models`, and activates it atomically. Provider services also verify the AutoJs6 package name, calling UID ownership, matching signatures, target metadata, and declared origin boundaries. Voice features do not ask for the microphone permission; the recognition app records. Recognized and spoken text is not logged, and dictated messages are stored like typed ones.

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

- Reasoning output is not declared. Local LiteRT-LM targets still do not declare tools.
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
