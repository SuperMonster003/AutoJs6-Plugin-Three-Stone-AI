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

The plugin requests `INTERNET` for user-triggered recommended-model downloads, requests to a user-configured online target, and public model-preset catalog updates from GitHub in Online AI settings. Catalog updates need no API key, make no inference calls, and never start during plugin activation or local generation. It requests no broad storage permission. Recommended model downloads use immutable HTTPS revisions and pinned byte counts and SHA-256 digests, write only to the SAF location chosen by the user, and are never treated as complete until the LiteRT-LM header, size, digest, flush, and fsync all pass. Import still reads only a system-picker URI, streams a verified copy into app-private `files/models`, and activates it atomically. Provider services also verify the AutoJs6 package name, calling UID ownership, matching signatures, target metadata, and declared origin boundaries.

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

# v1.3.0

###### 2026/09/30

* `Feature` Unify standalone settings with flat groups, consistent rows and centered rounded dialogs. Language, night mode, theme color and launcher icon changes apply only after OK; Cancel leaves saved values unchanged. Theme color follows AutoJs6 by default, with a shared palette, HEX/RGB input and a local preview. Neutral surfaces keep stable colors and controls follow the selected theme. The launcher default is adaptive automatic, while upgrades preserve explicit saved choices.
* `Fix` Through an OpenAI-compatible gateway, Claude or Gemini models stream an empty string instead of "{}" as the arguments of a tool without parameters; the session rejected it as an invalid response and ended with ONLINE_INVALID_RESPONSE (3-Stove Agent's first Claude Code Ex tool round and one Gemini Ex round on the AIGoCode gateway failed this way). Blank arguments are now treated as an empty object, and the assistant message replayed to the model carries the normalized form
* `Fix` Native tool continuation rounds used to subtract the output tokens of earlier rounds from the first request's ceiling, so long turns ended with ONLINE_INVALID_RESPONSE once the ceiling ran out after about ten rounds (3-Stove Agent's calculator case failed between the 9th and 14th call on both Claude Code Ex and Gemini Ex). Every continuation now keeps the full ceiling and the caller's task budget bounds the total, matching the 2026-09-29 decision to reset the continuation timeout
* `Fix` The Gemini GenerateContent protocol sent tool declarations and the responseSchema with their JSON Schema untouched, and the official API answers HTTP 400 ("Unknown name additionalProperties") for keywords it does not know, so every native tool task of 3-Stove Agent failed at its first call with ONLINE_REQUEST_REJECTED. Only the subset Gemini supports (type, enum, properties, required, items, anyOf, minimum / maximum, minLength / maxLength, default and similar) is sent now; callers keep validating arguments and replies against the full schema
* `Improvement` Keep the About icon in a rounded frame while its transparent interior reveals the surrounding page background. Show the launcher icon choices from the top with smaller explanatory notes.

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
