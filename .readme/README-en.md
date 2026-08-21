<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>On-device AI plugin. LiteRT-LM inference stays local; optional model downloads are explicit</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The current README.md supports the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### Introduction

******

On-Device AI is the official on-device AI text-generation plugin for AutoJs6. It runs user-imported LiteRT-LM models on an explicitly selected CPU or compatible GPU backend, accepts a plain-text message history, and returns plain text or schema-constrained JSON text through a controlled streaming session. All inference happens locally without network access or data upload; the network is used only when the user explicitly downloads a recommended model.

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

******

### Model and data formats

******

Version 1 declares only the following model and text scope:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### Plugin interface

******

The host discovers and calls the plugin with the following identities:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

The plugin declares ON_DEVICE execution and the NONE credential mode. It declares the `streaming`, `usage`, `persistent-session`, and `structured-json` capabilities, accepts `text/plain` message input and `application/json` response schemas, and emits `text/plain` or `application/json` text. Protocol 1.3 adds explicit backend profiles and device-scoped availability without CPU fallback.

Host build 5276 or later is required. Releases include arm64-v8a, x86_64, universal APK variants.

******

### Host integration status

******

> In AutoJs6 (build 5276 and later), `ai.ask`, `ai.chat`, and `ai.stream` support the local plugin route. `ai.session({ plugin: true })` creates a persistent multi-turn Conversation whose later `ask`, `chat`, and `stream` calls send only the new user prompt. `ai.ask(messages, { plugin: true })` preserves ordered plain-text `system`, `user`, and `assistant` messages, and the final message must be `user`. `ai.chat` returns exact token counts in `usage` and the measured generation duration in `usage.raw.durationMillis`; `ai.stream` emits the same cumulative usage before completion. Pass `plugin: true` to select this plugin, and the model ID may be omitted when only one model is imported; `ai.models({ plugin: true })` lists imported models and their `backendProfiles`. Generation accepts `backend: 'cpu' | 'gpu' | 'npu'`; unavailable profiles fail explicitly and never fall back to CPU. When the plugin is not installed, not enabled in Plugin Center, or has no imported model, scripts receive a clear error message. A fully pinned `plugin: { component, providerId, modelId }` selector is also supported. `responseSchema` implies structured output; `structuredJson: true` without a schema uses a default object-root schema. `ai.ask` and `ai.chat().text` still return JSON text, streamed deltas are partial JSON text, and a persistent session keeps one fixed schema and backend for every turn.

******

### Security and privacy

******

The plugin requests `INTERNET` only for user-triggered recommended-model downloads and requests no broad storage permission. Catalog downloads use immutable HTTPS revisions and pinned byte counts and SHA-256 digests, write only to the SAF location chosen by the user, and are never treated as complete until the LiteRT-LM header, size, digest, flush, and fsync all pass. Import still reads only a system-picker URI, streams a verified copy into app-private `files/models`, and activates it atomically. Provider services also verify the AutoJs6 package name, calling UID ownership, and matching signatures.

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
- `maxTokens` accepts integers from 1 through 2,147,483,647. `temperature` must be finite and non-negative, `topK` a positive integer, and `topP` finite from 0 through 1. Leaving all three sampling controls unset preserves model/engine defaults; a partial override fills the omitted controls with the LiteRT-LM baseline `topK: 1`, `topP: 0.95`, and `temperature: 1`.
- Streaming uses finite credits and bounded chunks to prevent unbounded buffering or callbacks without backpressure.
- Usage token counts come from LiteRT-LM's conversation KV-cache and decode counters, without character-based estimation. `durationMillis` measures the provider generation call and excludes host discovery, binding, model listing, and dispatch time.
- A persistent `ai.session` permits one active turn, retains its native Conversation after normal completion, and must be recreated after cancellation, timeout, generation failure, or explicit close.
- Cancellation, session close, and timeout stop result publication and finish the request through one terminal state.

******

### Undeclared capabilities

******

- Reasoning and tools are not declared.
- Tool-role messages, tool schemas, tool calls, and tool results are not accepted.
- There is no network model discovery, arbitrary-URL download, cloud inference, or credential flow; only the pinned built-in recommendation catalog can be downloaded.
- NPU inference is not declared: the profile is discoverable as `unavailable` with `npu-runtime-not-packaged`. GPU is declared only when `libOpenCL.so` is loadable, and a `.litertlm` extension alone still does not guarantee model initialization.

******

### Roadmap

******

The roadmap is organized around deliverable user-facing features, each independently checkable

- [View ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### Release history

******

# v1.1.0

###### 2026/08/21

* `Feature` Plugin brand and runtime identity standardized as On-Device AI across display names, package and component names, discovery identifiers, protocol API, build artifacts, and documentation
* `Feature` Compatible with the AutoJs6 `plugin: true` shorthand selector for `ai.ask`/`ai.chat`/`ai.stream` and the `ai.models` model listing
* `Feature` Forwarded `temperature`, `topK`, `topP`, and `maxTokens` through On-Device AI protocol 1.1 to LiteRT-LM sampling and output-token controls
* `Feature` Reported exact LiteRT-LM input, output, and total token counts plus provider-measured generation duration through AutoJs6 `ai.chat().usage` and stream usage events
* `Feature` Added On-Device AI protocol 1.2 persistent sessions and AutoJs6 `ai.session` multi-turn Conversation reuse without resending prior history
* `Feature` Added native LiteRT-LM JSON Schema constrained decoding through AutoJs6 `structuredJson` and `responseSchema`, with single-call, streaming, and persistent-session support plus strict completed-JSON validation
* `Feature` Explicit `cpu`, `gpu`, and `npu` backend profiles through protocol 1.3 and AutoJs6 generation options, with device compatibility reporting, model/profile cache isolation, and no fallback from unavailable profiles; GPU is declared only after an OpenCL load probe and NPU remains unavailable because its EAP runtime is not packaged
* `Feature` Direct downloads of pinned LiteRT Community models to a user-selected SAF location, with progress, precise cancellation, incomplete-file cleanup, LiteRT-LM header and exact size/SHA-256 verification, and a download-to-import handoff
* `Fix` Fixed the model manager retaining light-theme text colors in system dark mode, which made body text, checkboxes, and model rows unreadable against the dark background
* `Improvement` Updated the plugin description, instructions, and 10-language README to match the formalized host `ai.*` local plugin route
* `Improvement` Rewrote the ROADMAP as a feature roadmap with individually checkable items

# v1.0.0

###### 2026/08/08

* `Feature` On-Device AI protocol V1 provider running entirely on device, with plugin ID and engine `on-device-ai`, provider ID `autojs6.on-device-ai`, and variant `default`
* `Feature` CPU-only LiteRT-LM plain-text generation with system, user, and assistant history plus credit-backed streaming
* `Feature` SAF import of `.litertlm` into app-private storage with an 8 GiB limit, free-space reserve, SHA-256, fsync, and atomic activation
* `Feature` One active session, bounded I/O, descriptor quotas, cancellation, timeout, one terminal state, and same-signature AutoJs6 caller verification
* `Feature` Explicit omission of reasoning, tools, structured JSON, usage, network, and credential capabilities
* `Feature` arm64-v8a, x86_64, and universal APKs plus README, changelog, Android UI, and plugin instructions in 10 languages
* `Feature` A model management screen for viewing the complete model catalog and private-storage usage, with atomic current-model selection that does not copy model files
* `Improvement` Retained previous model generations named by SHA-256 hash after replacement imports to avoid cross-process `:provider` races, with retained files continuing to occupy app-private storage
* `Improvement` Added an application-scoped single-import coordinator and fsynced pending journal for Activity-recreation continuity, cold-start recovery, stale temp cleanup, and deletion restricted to destinations created by the current attempt and never published, while preserving published, current, and historical hash generations
* `Dependency` Added LiteRT-LM 0.15.0 for on-device CPU text generation

##### For more releases

* [CHANGELOG-en.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-en.md)

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
on-device-ai-api.aar
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
