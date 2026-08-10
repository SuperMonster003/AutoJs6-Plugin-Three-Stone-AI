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

> The current AutoJs6 main repository does not yet provide the AI Android adapter, provider selector, or runtime bridge, and built-in `ai.*` has not migrated to this protocol. Installing this plugin alone does not redirect existing `ai.*` calls. End-to-end use requires a future host adapter or one explicitly enabled by the host, plus selection of this provider.

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

`R0` remains in progress. Its 2026-08-10 build gates passed: 32 tests/0 failures, lint 0 errors, Debug and Release `BUILD SUCCESSFUL` in 3m37s, with `VERSION_BUILD`/`BUILD_TIME` unchanged; the real-model, clipboard, and example device smokes remain pending. `R1` now covers five default-off, unwired host slices with no production call site: read-only PackageManager `exact-action discovery`/`exact-component reinspection`, an explicit-component metadata-only Binder bind, a transport-independent model-list transcript policy/catalog, and an Android model-list coordinator with `IAiModelListCallback` Binder transport. The legacy metadata handshake reinspects each reached identity boundary and fuses only if its absolute deadline expires while the interface descriptor, `getProviderInfo()`, or `getCapabilities()` synchronous Binder RPC is still executing. The model-list policy strictly decodes bounded single- or multi-page results and provider errors, rejects generation drift, token replay/cycles, duplicate model IDs, capability mismatch, and stale/duplicate callbacks, and gives competing terminal events one winner. The coordinator revalidates provider metadata over the same descriptor-verified Binder only during listing initialization, then uses `AiTextProviderPackageSnapshot.samePackageIdentityAs` to recheck exact package/component identity before every initial or continuation dispatch. Each callback validates its calling UID, typed page/error envelope size, and a bounded flood slot before the sole payload copy; a bounded page-token ledger is isolated per complete stable pinned identity. Unlike the legacy handshake, the coordinator retains its watchdog for any admitted `operationsInFlight`, including exact PackageManager inspect, bind, prepare, dispatch, and callback admission, and fuses the exact component if the deadline expires before unwind. Neither fuse can hard-interrupt a stuck operation; the coordinator gate remains `BUSY` until late unwind. The fifth slice is the default-off, unwired, transport-independent `AiTextProviderSessionPolicy`: it pins plan/request/provider/model/context, holds callbacks received before synchronous `openSession` returns behind an explicit commit gate, validates UID then typed envelope bounds and descriptor ownership, manages initial 8 credits and bounded per-chunk backpressure, admits started/chunk/usage/completed/failed/cancelled with one winning terminal across provider completion, cancel, timeout, and death, waits for descriptor, remote-control, and cleanup settlement before publishing that terminal, and rejects tools fail-closed. It has no Android `IAiTextCallback`/`openSession`/PFD adapter, no package reinspection before session dispatch, and no actual session dispatch, runtime/UI integration, or `ai.*` routing. Its evidence now includes source/static plus focused JVM Gradle: standalone Kotlin 2.3.21 K2/JDK 21/JVM 17 compilation, 40/40 JUnit, and 1200/1200 across 30 runs; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` reported process exit 0 and its XML recorded 40 tests, 0 skipped, 0 failures, and 0 errors, with compilation still succeeding through fallback after a Kotlin daemon retry. It still has no Android `IAiTextCallback`/`openSession`/PFD adapter, ADB/device, runtime/UI, or `ai.*` evidence; broad R1 items and exit gates remain unchecked. On 2026-08-10, the isolated AutoJs6 Gradle gate passed coordinator 15/0 and assembled the host Debug, androidTest, and fake APKs without changing version metadata. QV710AF65F (API 31, arm64-v8a) then passed positive PARTIAL metadata and model-list runs at `OK (1 test)` each, collecting four pages/four fake models with `pageSize=1`; signer/hash, before/after identity, non-main callback, and sole-terminal details are recorded in the host evidence. All three packages were absent before installation and absent again after cleanup. This evidence supports only the narrow `R1` item; all broad items and exit gates remain unchecked. QV710AF65F had no real plugin/model, so the R0 device coverage remains open. `R2` through `R8` remain planned. Refer to the project roadmap for checkbox status.

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
