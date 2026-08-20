<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>On-device AI plugin. Streams text locally with LiteRT-LM, no network required</p>

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

On-Device AI is the official on-device on-device AI plugin for AutoJs6. It runs user-imported LiteRT-LM models on the CPU, accepts a plain-text message history, and returns plain text through a controlled streaming session. All inference happens locally: no network access and no data upload.

******

### Features

******

- Import a `.litertlm` model package through the Android system picker and keep a verified copy in app-private storage.
- Preflight private storage before opening the picker, show the current import budget and estimated private-copy footprint, and recheck the selected file before copying.
- Create local generation requests from plain-text system, user, and assistant history.
- Forward `temperature`, `topK`, `topP`, and `maxTokens` from AutoJs6 `ai.ask`, `ai.chat`, and `ai.stream` to LiteRT-LM.
- Deliver text chunks in order with credit backpressure and publish exactly one completed, failed, or cancelled terminal state.
- List, select, and rename imported models, delete unselected models, and reclaim unreferenced model files from the manager.
- Run entirely on-device with a CPU backend, without downloading models or calling a remote inference service.

******

### Model and data formats

******

Version 1 declares only the following model and text scope:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
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
protocol: V1.1
required host build: 5276
```

The plugin declares ON_DEVICE execution and the NONE credential mode. It declares only the `streaming` capability and `text/plain` input and output.

Host build 5276 or later is required. Releases include arm64-v8a, x86_64, universal APK variants.

******

### Host integration status

******

> In AutoJs6 (build 5276 and later), `ai.ask`, `ai.chat`, and `ai.stream` support the local plugin route: pass `plugin: true` to select this plugin, and the model ID may be omitted when only one model is imported; `ai.models({ plugin: true })` lists imported models. When the plugin is not installed, not enabled in Plugin Center, or has no imported model, scripts receive a clear error message. A fully pinned `plugin: { component, providerId, modelId }` selector is also supported.

******

### Security and privacy

******

The plugin requests no network or storage permission. It reads a model only through a URI granted by the system picker, computes SHA-256 while streaming it into the app-private `files/models` directory, calls fsync, and activates it with an atomic pointer replacement in the same directory. Provider services also verify the AutoJs6 package name, calling UID ownership, and matching signatures.

******

### Operational limits

******

- Model imports have an 8 GiB hard limit and must leave at least 256 MiB of free space.
- An application-scoped single-import coordinator keeps ongoing work alive across Activity recreation. A fsynced pending journal supports cold-start recovery and cleanup of stale `.incoming`, `.current`, and `.pending` temporary files. Recovery deletes only a destination created by the current attempt and never published through current metadata; published, current, and historical hash generations are retained.
- To avoid cross-process races with the isolated `:provider` process, imports do not automatically delete previous SHA-256-named model generations. The model manager can delete unselected catalog models and reclaim hash-named files no longer referenced by the catalog.
- At most one generation session is active in the process. Request descriptors are duplicated before asynchronous work and closed under protocol quotas.
- The provider advertises a 256 KiB context ceiling and a 64 KiB output ceiling. Requests and models may impose lower limits.
- `maxTokens` accepts integers from 1 through 2,147,483,647. `temperature` must be finite and non-negative, `topK` a positive integer, and `topP` finite from 0 through 1. Leaving all three sampling controls unset preserves model/engine defaults; a partial override fills the omitted controls with the LiteRT-LM baseline `topK: 1`, `topP: 0.95`, and `temperature: 1`.
- Streaming uses finite credits and bounded chunks to prevent unbounded buffering or callbacks without backpressure.
- Cancellation, session close, and timeout stop result publication and finish the request through one terminal state.

******

### Undeclared capabilities

******

- Reasoning, tools, structured JSON, and usage are not declared.
- Tool-role messages, tool schemas, tool calls, and tool results are not accepted.
- There is no network model discovery, model download, cloud inference, or credential flow.
- No GPU or NPU backend is declared. A `.litertlm` extension alone does not guarantee that the current LiteRT-LM runtime can load the model.

******

### Roadmap

******

The roadmap is organized around deliverable user-facing features, each independently checkable

- [View ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### Release history

******

# v1.1.0

###### 2026/08/20

* `Feature` Plugin brand and runtime identity standardized as On-Device AI across display names, package and component names, discovery identifiers, protocol API, build artifacts, and documentation
* `Feature` Compatible with the AutoJs6 `plugin: true` shorthand selector for `ai.ask`/`ai.chat`/`ai.stream` and the `ai.models` model listing
* `Feature` Forwarded `temperature`, `topK`, `topP`, and `maxTokens` through On-Device AI protocol 1.1 to LiteRT-LM sampling and output-token controls
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
