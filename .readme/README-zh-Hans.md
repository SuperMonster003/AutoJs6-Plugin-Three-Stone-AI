<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>本地 AI 文本生成插件. 使用 LiteRT-LM 在设备端流式生成纯文本</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### 简介

******

AI Text Generation 是 AutoJs6 的独立 AI Text Generation 协议 V1 设备端 provider. 它在 CPU 上运行用户导入的 LiteRT-LM 模型, 接收纯文本消息历史, 并通过受控流式会话返回纯文本.

******

### 功能

******

- 通过 Android 系统文件选择器导入 `.litertlm` 模型包, 并将验证后的副本保存到应用私有存储.
- 使用纯文本 system, user 和 assistant 历史创建本地生成请求.
- 通过 credit 背压按序传送文本 chunk, 并只发布一个完成, 错误或取消终态.
- 列出当前已导入模型, 并在导入替换后为宿主提供新的模型列表 generation.
- 完全在设备端以 CPU backend 运行, 不下载模型, 不调用远程推理服务.

******

### 模型和数据格式

******

版本 1 仅声明以下模型和文本范围:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### 插件接口

******

宿主通过以下标识发现并调用插件:

```text
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

插件声明 ON_DEVICE 执行位置和 NONE credential 模式. 它只声明 `streaming` 能力及 `text/plain` 输入输出.

需要宿主构建版本 5270 或更高版本. 发布产物包含 arm64-v8a, x86_64, universal APK.

******

### 宿主集成状态

******

> AutoJs6 现已提供显式 public `ai.ask(..., { plugin: ... })` 与 `ai.stream(..., { plugin: ... })` production route, 严格固定 exact component/provider/model 且不 cloud fallback. 真实 release 插件/模型 one-shot ask 已在 QV710AF65F (API 31, arm64-v8a) 通过; 同一设备上的 deterministic fake-provider public stream smoke 也已自然完成, 产生超过 8 个 chunk 并跨过 initial 8-credit 窗口. 仅安装插件仍不会切换 legacy `ai.*`; 脚本必须传入显式 plugin selector 并使用已导入的 modelId. 旧 JavaAdapter/raw Binder 示例、clipboard 流程、UI、`chat`、真实模型 streaming 和实机主动 cancel 仍待验证.

******

### 安全性和隐私

******

插件不请求网络或存储权限. 模型只通过系统文件选择器授予的 URI 读取, 以流式 SHA-256 校验和 fsync 写入应用私有 `files/models` 目录, 再通过同目录原子 pointer 替换激活. Provider 服务还会核验 AutoJs6 包名, 调用 UID 归属及双方签名.

******

### 运行限制

******

- 模型导入硬上限为 8 GiB, 且导入后至少保留 256 MiB 可用空间.
- 应用级单导入协调器使 Activity 重建不会中断正在进行的导入. Fsync pending journal 支持冷启动恢复并清理 stale `.incoming`, `.current` 和 `.pending` 临时文件. 恢复只会删除本次尝试新建且从未由 current metadata 发布的 destination, 已发布或 current 模型及历史 hash 代际均会保留.
- 为避免与独立 `:provider` 进程发生竞态, 替换导入后仍会保留先前以 SHA-256 hash 命名的模型代际. 这些文件会继续占用应用私有存储.
- 同一进程最多有一个活动生成会话. 请求描述符会在异步处理前复制并按协议配额关闭.
- Provider 声明的上下文上限为 256 KiB, 输出上限为 64 KiB, 请求和模型还可施加更低上限.
- 流式输出使用有限 credit 和有界 chunk, 防止无限制缓冲或无背压回调.
- 取消, 会话关闭和超时会停止结果发布, 并通过唯一终态结束请求.

******

### 未声明的能力

******

- 不声明 reasoning, tools, structured JSON 或 usage 能力.
- 不接受 tool 角色消息, tool schema, tool call 或 tool result.
- 不提供联网模型发现, 模型下载, 云端推理或 credential 流程.
- 不声明 GPU 或 NPU backend. `.litertlm` 扩展名本身不保证模型能被当前 LiteRT-LM runtime 加载.

******

### 路线图

******

`R0` 仍在进行中. 2026-08-10 的构建门禁已通过: 32 tests/0 failures, lint 0 error, Debug/Release `BUILD SUCCESSFUL` (3m37s), 且 `VERSION_BUILD`/`BUILD_TIME` 未变化; 真实插件/模型的 public `ai.ask` 实机 smoke 现已通过, clipboard 写入与 Activity 重建后复制仍待执行. `R1` 现已覆盖五个没有生产调用点的默认关闭且未接线宿主切片: 只读 PackageManager `exact-action discovery`/`exact-component reinspection`, 显式组件 metadata-only Binder 绑定, transport-independent model-list transcript policy/catalog, 以及 Android model-list coordinator 与 `IAiModelListCallback` Binder transport. 旧 metadata handshake 复查实际到达的身份边界, 且仅在 absolute deadline 到期时 interface descriptor、`getProviderInfo()` 或 `getCapabilities()` 同步 Binder RPC 仍在执行才熔断. model-list policy 有界严格解码单页或多页结果及 provider error, 拒绝 listing generation 漂移、token 重放/循环、重复 model ID、能力不匹配及过期/重复 callback, 并让相互竞争的终态只有一个胜出. coordinator 仅在 listing 初始化阶段于同一个完成 descriptor 验证的 Binder 上复验 provider metadata, 随后在每次初始或 continuation dispatch 前以 `AiTextProviderPackageSnapshot.samePackageIdentityAs` 精确复查 package/component identity. 每个 callback 在唯一一次 payload copy 前依次核验调用 UID、typed page/error envelope 大小和有界 flood slot; page-token ledger 按完整且稳定的 pinned identity 隔离并有界持有. 与旧 handshake 不同, coordinator 对任何已接纳的 `operationsInFlight` 保留 watchdog, 包括 exact PackageManager inspect、bind、prepare、dispatch 与 callback admission; deadline 到期仍未 unwind 时熔断 exact component. 两种 fuse 均不能硬中止卡住的 operation; coordinator gate 保持 `BUSY` 直到晚到 unwind. 第五个切片是默认关闭、未接线且 transport-independent 的 `AiTextProviderSessionPolicy`: 它固定 plan/request/provider/model/context, 将同步 `openSession` 返回前收到的 callback 保留在显式 commit gate 之后, 依次校验 UID、typed envelope 边界与 descriptor ownership, 自动管理 initial 8 credits 和有界逐 chunk backpressure, 有界接纳 started/chunk/usage/completed/failed/cancelled, 让 provider completion、cancel、timeout 与 death 只有一个终态胜出, 并等待 descriptor、remote control 及 cleanup 全部 settled 后才发布终态; tools 一律 fail-closed. 它不含 Android `IAiTextCallback`/`openSession`/PFD adapter, 不在 session dispatch 前重新检查 package identity, 也不含实际 session dispatch、runtime/UI 或 `ai.*` 路由. 其证据现包含源码/静态与 focused JVM Gradle: standalone Kotlin 2.3.21 K2/JDK 21/JVM 17 编译、40/40 JUnit 以及同一产物 30 轮 1200/1200; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` 进程 exit 0, XML 记录 40 tests/0 skipped/0 failures/0 errors, Kotlin daemon 重试后通过 fallback 编译仍成功. 它仍不含 Android `IAiTextCallback`/`openSession`/PFD adapter、ADB/device、runtime/UI 或 `ai.*` 证据; 广义 R1 与退出门槛保持未勾选. 2026-08-10 isolated AutoJs6 Gradle gate 通过 coordinator 15/0, 并成功 assemble host Debug、androidTest 与 fake APK, version metadata 未变化. QV710AF65F (API 31, arm64-v8a) 的 metadata handshake 与 model-list 正向 PARTIAL 各为 `OK (1 test)`, `pageSize=1` 收集四页/四个 fake model; signer/hash、前后 identity、非 main callback 与唯一终态详见主仓 evidence. 三包安装前均不存在, 清理后恢复为均不存在. 该证据只支持窄 R1 item, 广义工作项与退出门槛保持未勾选. QV710AF65F 没有真实插件/模型, 因此 R0 实机覆盖仍未完成. `R2` 至 `R8` 仍为规划项. 第六个窄切片已超越前述 session policy 的 transport 边界: 默认关闭、未接线的 Android exact-component session coordinator 与 `IAiTextCallback`/PFD transport. standalone K2 为 18/18, 同一产物 30 轮 540/540; focused Gradle 为 18 tests/0 failures, 三 APK 成功 assemble. QV710AF65F (API 31, arm64-v8a) 两个 instrumentation 方法均为 `OK (1 test)`: reliable-pipe request PFD 驱动约 18 个有序 chunk, 跨过 initial 8-credit 窗口; Android descriptor owner 覆盖 exact、short、trailing、reliable-pipe producer error 与幂等 close. 该证据仍仅为 PARTIAL: 没有 callback completion/tool PFD 跨进程证据、wrong UID、hostile provider death/update、真实插件/模型、runtime/UI 或 `ai.*` 生产路由. 广义 R1 工作项与退出门槛保持未勾选. 后续新增一个已勾选的 hostile Android session conformance 窄切片, 由 isolated H1 commits `edd10008f`/`06ebc788c` 与主仓集成 commits `0cbc19d9f`/`72eb4d0c0` 记录. focused Gradle 通过 coordinator 18/0 与 fake provider 31/0, 并成功 assemble 三 APK. 在 QV710AF65F (API 31, arm64-v8a) 上, 七个 exact instrumentation 方法各为 `OK (1 test)`: ordinary-pipe completion callback 验证跨进程 PFD ownership 转移、exact length/EOF、SHA-256、UTF-8 物化与 cleanup; tool PFD 被接管后唯一拒绝为 `TOOLS_UNSUPPORTED`; chunk-before-start、sequence-gap 与非法 descriptor reference 均 fail-closed; duplicate terminal 仅取得有界 single-terminal smoke 结果; stall 在 `Started` 后由 host cancel, 随后 owner/gate 可复用. 此 `[x]` 仍是窄范围证据: 未覆盖 cross-process reliable-pipe status、wrong UID、no-credit、provider death、package update/uninstall、真实插件/模型、runtime/UI 或 `ai.*`. 广义 R1 工作项与退出门槛保持未勾选. 勾选状态以项目路线图为准. 后续新增一个已勾选的 H2 生命周期窄切片, 由主仓集成 commits `e5bd92b16`/`10dad3e39`/`0b9a94742` 记录. focused Gradle 通过 coordinator 18/0 与 fake provider 31/0, 三 APK 均成功 assemble. 在 QV710AF65F (API 31, arm64-v8a) 上, `callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner` 与 `providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner` 各返回 `OK (1 test)`: 前者在任何 transcript 发布前将 isolated-process callback 唯一拒绝为 `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH`, 随后 owner/gate 可复用; 后者在收到 `Started`、一个 sequence 0 合法 chunk 及 credit replenishment acknowledgement 后结束 provider 进程, host 仅发布一个 `BinderDied`, 随后 owner/gate 可复用. local/device SHA-256 精确一致: host `0685CE99C0F9E8F9056BE5F3A8EEBC2C7EA5FFCA2D422E21EAC69D0CB3364629`, androidTest `7C91A0AF651E2098AD124FF8A89AE3AC3018E0F1D0DEC068367595E964739178`, fake provider `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`; 三者 v2 signer certificate SHA-256 均为 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`. host、androidTest 与 fake 三包在安装前均为 absent, cleanup 后再次均为 absent. 该 Android 证据仅是 wrong-UID/provider-death 的 PARTIAL. no-credit 仍只有 JVM 证据; update/uninstall 形状仍仅由 final-reinspection JVM 用例 (`lastUpdateTime` 漂移与 `Completed(emptyList())`) 覆盖, 未执行实机 package 变更. cross-process no-credit、实机 update/uninstall、真实插件/模型、runtime/UI 与 `ai.*` 仍未覆盖; 广义 R1 工作项与退出门槛保持未勾选. 新增的已勾选窄切片由 AutoJs6 主仓 commits `e4297a688`/`64db31ea5` 记录, 接入显式 `ai.ask(..., { plugin: ... })` production source route: selector 严格固定 exact component/provider/model; `plugin` 缺席时保留 legacy cloud 行为, 一旦出现则不读取 vault、不进入 HTTP/cloud、也不 fallback; 当前仅接纳单条 plain-text user message 与 non-stream 请求, engine close 会传播远端 session teardown. standalone K2 为 15/15, focused Gradle 为 19/19 且 Android main compile 通过, 均为 source-only 证据. 该切片不含真实插件/模型/device 运行, 也不含 UI、`chat` 或 `stream` 路由; 广义 R1 工作项与退出门槛保持未勾选. 2026-08-11, 实测 APK 与设备运行直接源为隔离树 commit `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`; 同一 test blob 后续在当前 main parent 上集成为 `7ce26ceea`, 两者 app tree 一致, 但后者不是 APK 直接构建源. QV710AF65F (API 31, arm64-v8a) 以真实 Rhino global `ai.ask(..., { plugin: ... })` one-shot 经 release 插件运行 2,583,085,056-byte LiteRT-LM. 模型 SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42` 派生 modelId `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`; `AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin` 返回 `OK (1 test)`/7.071s. local/device APK SHA-256 精确一致: host `b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`, androidTest `09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf`, plugin `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`; 三者 v2 signer certificate SHA-256 均为 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`, device base 与候选 APK 一致. 插件 test/lint/Debug/Release 门禁与宿主 focused 19/19 tests/assemble 均通过; users 0/10 的 host/test/plugin 精确包与专用 staging/UI-dump 路径在验证前后均为 absent. 这不勾选 R0 raw Binder/JavaAdapter 示例基线项; 它仅勾选 R6 窄范围的可选真实模型 smoke/本地入口门禁, 但仍仅是单设备、单模型、non-stream one-shot 与 appDebug 宿主证据. 它不覆盖 clipboard、stream/`chat`、tools、structured output、usage、release 宿主/API 矩阵、实机 update/uninstall、性能或 soak. DocumentsUI last-location 状态无法无损复原. 广义 R1 工作项与退出门槛仍未勾选. 另一个已勾选的 public plugin stream PARTIAL 窄切片由 AutoJs6 commits `2ea3360a2`/`50b43d00c` 记录: focused Gradle 为 25/25; QV710AF65F (API 31, arm64-v8a) 上 exact method `AiTextPluginPublicStreamSmokeTest#publicRhinoAiStreamCompletesThroughExactFakeProvider` 在 1.645s 返回 `OK (1 test)`. fake provider (`fake.local`/`fake.stream`) 产生超过 8 个 chunk 并跨过 initial 8 credits, owned Rhino execution 随后自然完成. public cancel 与 engine teardown 仍只有 JVM 证据, 未覆盖真实模型 streaming、实机主动 cancel 或 release/API 矩阵; 广义 R1 工作项和退出门槛保持未勾选. 另记录一个窄 R1 已勾选切片：prompt-only 显式 plugin `ai.chat` production route。隔离树 production/test commits 为 `d480b6918`/`b0aa6768484d6046551264f69ecc84b527bbb442`，后续 integration commits 为 `2d256b99f`/`c35f199b8`；focused Gradle 通过 14/14（3+4+7）。Gradle、三 APK 与设备实测源均为 `b0aa6768484d6046551264f69ecc84b527bbb442`，不是 integration commits，中间无路径交集的 DEX commits 不改变该 provenance。QV710AF65F（API 31，arm64-v8a）上的 exact `AiTextPluginPublicChatSmokeTest#publicRhinoAiChatReturnsNormalizedResponseThroughExactFakeProvider` 以 `fake.local`/`fake.echo` 返回 `OK (1 test)`、`Time: 1.54`、code `-1`；Promise 自然完成、精确回显 prompt，并返回 `text`、`reasoning`、`toolCalls`、`usage`、`finishReason`、`message`、`error`、`raw`、`profile`、`route`、`provider`、`model` 的精确 12-key set。host/androidTest/fake APK SHA-256 分别为 `2D3DFB9C16AD91CCB73C6A969DB7DCC9B10046DF14F716C82D9E61F07FAAF1D3`、`9CAA1CC6936D98C380C9C9FFE81178A0E81E667207DA9623FA95C19BF3A11166`、`6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`，共同 v2 证书 SHA-256 为 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`；cleanup 后 users 0/10 三包与九个保留路径恢复 absent。这仅是 fake-provider natural-completion PARTIAL：未测真实模型、device failure/cancel 或 API matrix，public cancel/engine close 仅有 JVM 证据；广义 R1/退出门槛、R0 与 R6 均不提升。

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### 版本历史

******

# v1.0.0

###### 2026/08/08

* `新增` AI Text Generation 协议 V1 设备端 provider, 插件 ID 和引擎为 `ai-text-generation`, provider ID 为 `autojs6.local.text`, 变体为 `default`
* `新增` CPU-only LiteRT-LM 纯文本生成, 支持 system, user 和 assistant 历史及 credit 背压流式输出
* `新增` 通过 SAF 导入 `.litertlm` 到应用私有存储, 包含 8 GiB 上限, 空间预留, SHA-256, fsync 和原子激活
* `新增` 单活动会话, 有界 I/O, descriptor 配额, 取消, 超时, 唯一终态及同签名 AutoJs6 调用方核验
* `新增` 明确不声明 reasoning, tools, structured JSON, usage, 网络或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 种语言的 README, 更新日志, Android 界面和插件说明
* `优化` 为避免独立 `:provider` 进程竞态, 替换导入后保留先前以 SHA-256 hash 命名的模型代际, 保留文件会继续占用应用私有存储
* `优化` 增加应用级单导入协调器和 fsync pending journal, 在 Activity 重建时保持导入, 支持冷启动恢复和 stale 临时文件清理, 删除仅限本次新建而从未发布的 destination, 并保留已发布, current 和历史 hash 代际
* `依赖` 附加 LiteRT-LM 0.15.0, 用于设备端 CPU 文本生成

##### 更多版本

* [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

******

### 构建

******

```powershell
.\gradlew.bat :app:assembleDebug
```

发布构建:

```powershell
.\gradlew.bat :app:assembleRelease
```

构建参数来自 `version.properties`. 当前最低 SDK 为 24, 目标 SDK 为 36, 构建 JDK 为 21 或更高版本.

协议 ABI 由仓库 `libs` 目录中的本地 AAR 提供:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-text-generation-api.aar
```

运行时通过 Maven 使用 LiteRT-LM 0.15.0. 发布构建保留 LiteRT-LM runtime 类, 并生成两个 ABI APK 和一个 universal APK.

******

### 许可证

******

项目源码使用 MPL-2.0. LiteRT-LM 和其他第三方组件继续适用各自的许可证.

******

### 资源布局

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` 从 JSON 源生成 10 种语言的 README 和应用内更新日志. Android 字符串由各自资源目录管理.

******

### 链接

******

- AutoJs6 文档: https://docs.autojs6.com
- LiteRT-LM 项目: https://github.com/google-ai-edge/LiteRT-LM
