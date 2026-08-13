<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>Local AI text generation plugin. Stream plain text on-device with LiteRT-LM</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Languages

******

The current README.md supports the following languages:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- English [en] # current
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### Introduction

******

AI Text Generation is an independent on-device provider for version 1 of the AutoJs6 AI Text Generation protocol. It runs a user-imported LiteRT-LM model on CPU, accepts plain-text message history, and returns plain text through a controlled streaming session.

******

### Features

******

- Import a `.litertlm` model package through the Android system picker and keep a verified copy in app-private storage.
- Create local generation requests from plain-text system, user, and assistant history.
- Deliver text chunks in order with credit backpressure and publish exactly one completed, failed, or cancelled terminal state.
- List the currently imported model and expose a new model-list generation after replacement.
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
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

The plugin declares ON_DEVICE execution and the NONE credential mode. It declares only the `streaming` capability and `text/plain` input and output.

Host build 5270 or later is required. Releases include arm64-v8a, x86_64, universal APK variants.

******

### Host integration status

******

> AutoJs6 now provides explicit public production routes for `ai.ask(..., { plugin: ... })`, prompt-only `ai.chat(..., { plugin: ... })`, and `ai.stream(..., { plugin: ... })`, with exact component/provider/model selection and no cloud fallback. Real-plugin/model public ask has L3 device evidence; deterministic fake-provider stream and chat have L2 Android evidence. Installing the plugin alone still does not redirect legacy `ai.*`; scripts must pass the explicit plugin selector and use an imported modelId. Real-model chat/stream and device-side active cancellation remain non-blocking evidence debt.

******

### Security and privacy

******

The plugin requests no network or storage permission. It reads a model only through a URI granted by the system picker, computes SHA-256 while streaming it into the app-private `files/models` directory, calls fsync, and activates it with an atomic pointer replacement in the same directory. Provider services also verify the AutoJs6 package name, calling UID ownership, and matching signatures.

******

### Operational limits

******

- Model imports have an 8 GiB hard limit and must leave at least 256 MiB of free space.
- An application-scoped single-import coordinator keeps ongoing work alive across Activity recreation. A fsynced pending journal supports cold-start recovery and cleanup of stale `.incoming`, `.current`, and `.pending` temporary files. Recovery deletes only a destination created by the current attempt and never published through current metadata; published, current, and historical hash generations are retained.
- To avoid cross-process races with the isolated `:provider` process, replacement imports retain previous model generations named by SHA-256 hash. These files continue to occupy app-private storage.
- At most one generation session is active in the process. Request descriptors are duplicated before asynchronous work and closed under protocol quotas.
- The provider advertises a 256 KiB context ceiling and a 64 KiB output ceiling. Requests and models may impose lower limits.
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

The roadmap contains 28 checkable product outcomes: 14 complete and 14 open. D3 remains current because transactional catalog migration is still open. Model-management and atomic-selection L2 is complete at plugin production `98d438e`, focused tests `a6bc0e3`, 18 JVM XML reports and 57 tests with 0 failures/errors/skipped (focused 7+7+1+5), and successful `lintDebug` plus Debug/Release assemblies: 105 tasks on the full run and a successful final incremental rerun. Host test `6495079ea` and its 622-task build passed. On QV710AF65F/API 31/arm64-v8a, the exact real Activity changed A to B and B survived force-stop/restart; before and after, Binder exposed the same two exact models and generation `litertlm-catalog-v2-08e6ec0aff29d234cbdf6acb07be8916`, and `planExact` resolved both. The three APK hashes, shared signer, and cleanup receipts are frozen in ROADMAP. The fixtures were two 8-byte listing-only files; no `openSession`, LiteRT-LM/native path, real model, or old active generation ran. Old-session safety therefore rests on request-time path pinning, no file mutation during selection, and JVM policy tests, not real-generation evidence.

- [View ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### Release history

******

# v1.0.0

###### 2026/08/08

* `Feature` On-device AI Text Generation protocol V1 provider with plugin ID and engine `ai-text-generation`, provider ID `autojs6.local.text`, and variant `default`
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

* [CHANGELOG-en.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-en.md)

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
ai-text-generation-api.aar
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
