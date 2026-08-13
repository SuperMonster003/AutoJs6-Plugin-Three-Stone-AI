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

> AutoJs6 现已提供显式 public production route：`ai.ask(..., { plugin: ... })`、prompt-only `ai.chat(..., { plugin: ... })` 与 `ai.stream(..., { plugin: ... })`，严格选择 exact component/provider/model 且不 cloud fallback。真实插件/模型 public ask 已有 L3 实机证据，deterministic fake-provider stream/chat 已有 L2 Android 证据。仅安装插件仍不会切换 legacy `ai.*`；脚本必须传入显式 plugin selector 并使用已导入的 modelId。真实模型 chat/stream 与实机主动取消保留为非阻塞证据债务。

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

路线图按 28 个可勾选产品结果组织。D0（单模型本地 Provider）、D1（显式 public plugin `ai.ask`/prompt-only `ai.chat`/`ai.stream`）和 D2（大模型导入进度与精确取消）已完成；D3“多模型 catalog 与选择”是当前阶段。D2 的 L1 gate 以单次 invocation 运行 `:app:testDebugUnitTest :app:assembleDebug`：21s 内 `BUILD SUCCESSFUL`，48 tasks（21 executed/27 up-to-date），版本哈希未变，独立终审 High/Medium 为 0。L1 表示实现、focused tests 与受影响构建，L2 表示 Android/Binder 集成，L3 表示签名真实插件/模型的实机证据。更高等级证据欠缺不会反向打开已完成结果，soak 与广泛 API/设备矩阵仅作为 Release Candidate 风险检查，不阻塞普通进度。

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
