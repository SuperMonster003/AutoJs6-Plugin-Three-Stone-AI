<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>设备端 AI 插件. LiteRT-LM 推理始终在本地; 推荐模型下载仅由用户明确发起</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### 简介

******

On-Device AI (设备端 AI) 是 AutoJs6 的官方设备端 AI 文本生成插件. 它在显式选择的 CPU 或兼容 GPU backend 上运行用户导入的 LiteRT-LM 模型, 接收纯文本消息历史, 并通过受控流式会话返回纯文本或受 schema 约束的 JSON 文本. 全部推理在本地完成, 不联网也不上传任何数据; 只有用户明确下载推荐模型时才会访问网络.

******

### 功能

******

- 通过 Android 系统文件选择器导入 `.litertlm` 模型包, 并将验证后的副本保存到应用私有存储.
- 将固定版本且无需登录的 LiteRT Community 推荐模型直接下载到用户选择的 SAF 位置, 支持进度, 取消, 残缺文件清理及精确大小与 SHA-256 校验.
- 打开系统文件选择器前预检私有存储空间, 显示当前导入预算和私有副本预计占用, 并在复制前再次检查所选文件.
- 使用纯文本 system, user 和 assistant 历史创建本地生成请求.
- 将 AutoJs6 `ai.ask`, `ai.chat` 和 `ai.stream` 的 `temperature`, `topK`, `topP` 与 `maxTokens` 透传到 LiteRT-LM.
- 通过 AutoJs6 `structuredJson` 和 `responseSchema` 启用 LiteRT-LM 原生 JSON Schema 约束解码; 完整结果仍为可供 `JSON.parse` 解析的 JSON 文本.
- 通过 AutoJs6 `ai.chat().usage` 和流式 usage 事件返回 LiteRT-LM 精确的输入, 输出及总 token 数, 以及插件侧生成耗时.
- 通过 AutoJs6 `ai.session` 在同一个 LiteRT-LM 原生 Conversation 中保留多轮上下文, 后续轮次只发送新的用户提示词.
- 按模型 SHA-256 复用已初始化 Engine, 消除同模型连续请求的重复冷启动.
- 可选地将每个导入模型初始化一次, 持久化其「可用/不兼容」状态, 并可在模型管理界面重新检查.
- 通过 credit 背压按序传送文本 chunk, 并只发布一个完成, 错误或取消终态.
- 列出, 选择和重命名已导入模型, 删除未选中模型, 并在管理界面一键回收未引用模型文件.
- 通过 AutoJs6 显式选择 `cpu`, `gpu` 或 `npu` backend; CPU 为默认值, GPU 仅在 OpenCL 加载探测通过后开放, NPU 因未打包 EAP 运行时而明确报告不可用.

******

### 模型和数据格式

******

版本 1 仅声明以下模型和文本范围:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### 插件接口

******

宿主通过以下标识发现并调用插件:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

插件声明 ON_DEVICE 执行位置和 NONE credential 模式. 它声明 `streaming`, `usage`, `persistent-session` 与 `structured-json` 能力, 接受 `text/plain` 消息输入和 `application/json` 响应 schema, 并输出 `text/plain` 或 `application/json` 文本. 协议 1.3 增加显式 backend profile 和设备级可用性, 且禁止静默回退到 CPU.

需要宿主构建版本 5276 或更高版本. 发布产物包含 arm64-v8a, x86_64, universal APK.

******

### 宿主集成状态

******

> AutoJs6 (构建 5276 及以上) 的 `ai.ask`, `ai.chat` 与 `ai.stream` 支持本地插件路由. `ai.session({ plugin: true })` 可创建持久多轮 Conversation, 后续 `ask`, `chat` 与 `stream` 调用只发送新的用户提示词. `ai.ask(messages, { plugin: true })` 会按顺序保留纯文本 `system`, `user` 与 `assistant` 消息, 且最后一条消息必须为 `user`. `ai.chat` 会在 `usage` 中返回精确 token 数, 并在 `usage.raw.durationMillis` 中返回实测生成耗时; `ai.stream` 会在完成前发送同一份累计 usage. 传入 `plugin: true` 即选择本插件, 单模型场景可省略模型 ID; `ai.models({ plugin: true })` 可枚举已导入模型及其 `backendProfiles`. 生成选项接受 `backend: 'cpu' | 'gpu' | 'npu'`; 不可用 profile 会明确失败且绝不回退 CPU. 插件未安装, 未在插件中心启用或未导入模型时, 脚本会收到明确的错误提示. 也可通过 `plugin: { component, providerId, modelId }` 显式固定组件. `responseSchema` 会隐式启用结构化输出; 仅设置 `structuredJson: true` 时使用默认的对象根 schema. `ai.ask` 和 `ai.chat().text` 仍返回 JSON 文本, 流式 delta 是不完整的 JSON 片段, 持久会话则在所有轮次固定使用同一 schema 和 backend.

******

### 安全性和隐私

******

插件仅为用户主动发起的推荐模型下载请求 `INTERNET` 权限, 不请求广泛存储权限. 目录下载使用不可变 HTTPS 版本及固定字节数和 SHA-256, 仅写入用户选择的 SAF 位置; LiteRT-LM 文件头, 大小, 摘要, flush 与 fsync 全部通过后才算完成. 导入仍只读取系统选择器授予的 URI, 将验证副本流式写入应用私有 `files/models` 并原子激活. Provider 服务还会核验 AutoJs6 包名, 调用 UID 归属及双方签名.

******

### 运行限制

******

- 模型导入硬上限为 8 GiB, 且导入后至少保留 256 MiB 可用空间.
- 应用进程内一次只运行一个模型下载. Activity 重建会保留进度和取消归属; 取消或失败时会删除新建目标, 不支持删除时将其截断清空. 进程被终止仍可能留下外部残缺文件, 用户应手动删除.
- 应用级单导入协调器使 Activity 重建不会中断正在进行的导入. Fsync pending journal 支持冷启动恢复并清理 stale `.incoming`, `.current` 和 `.pending` 临时文件. 恢复只会删除本次尝试新建且从未由 current metadata 发布的 destination, 已发布或 current 模型及历史 hash 代际均会保留.
- 为避免与独立 `:provider` 进程发生竞态, 导入时不会自动删除先前以 SHA-256 hash 命名的模型代际. 模型管理界面可删除未选中的 catalog 模型, 并回收不再由 catalog 引用的 hash 命名文件.
- 同一进程最多有一个活动生成会话. 请求描述符会在异步处理前复制并按协议配额关闭.
- Provider 以模型 SHA-256 和 backend profile 为联合键, 最多缓存一个已初始化 Engine. 相同组合的连续请求会复用它; 任一键变化, 空闲 5 分钟或收到系统明确内存压力通知时会安全释放.
- 模型自检仅证明 `Engine.initialize()` 能在当前设备和内置运行时中成功; 它不评估输出质量, 设备或运行时变化后可重新检查.
- Provider 声明的上下文上限为 256 KiB, 输出上限为 64 KiB, 请求和模型还可施加更低上限.
- 响应 schema 必须是 JSON 对象且不超过 64 KiB. 可用关键字以当前内置 LiteRT-LM/LLGuidance 运行时为准; 插件会严格解析并验证完整输出, 因此应为整个 JSON 值预留足够的 `maxTokens`.
- `maxTokens` 接受 1 至 2,147,483,647 的整数. `temperature` 必须为非负有限数, `topK` 必须为正整数, `topP` 必须为 0 至 1 的有限数. 三项采样参数全部省略时保留模型或引擎默认值; 部分覆盖时, 未设置项使用 LiteRT-LM 基线 `topK: 1`, `topP: 0.95`, `temperature: 1`.
- 流式输出使用有限 credit 和有界 chunk, 防止无限制缓冲或无背压回调.
- Usage token 数直接来自 LiteRT-LM Conversation 的 KV cache 与 decode 计数, 不做字符数估算. `durationMillis` 只测量插件生成调用, 不包含宿主发现, 绑定, 模型枚举和分发时间.
- 持久 `ai.session` 只允许一个活动轮次, 正常完成后保留原生 Conversation; 取消, 超时, 生成失败或显式关闭后必须重新创建会话.
- 取消, 会话关闭和超时会停止结果发布, 并通过唯一终态结束请求.

******

### 未声明的能力

******

- 不声明 reasoning 或 tools 能力.
- 不接受 tool 角色消息, tool schema, tool call 或 tool result.
- 不提供联网模型发现, 任意 URL 下载, 云端推理或 credential 流程; 只能下载内置目录中固定版本的推荐模型.
- 不声明 NPU 推理可用: profile 可发现但以 `npu-runtime-not-packaged` 标记为 `unavailable`. GPU 仅在 `libOpenCL.so` 可加载时声明, 且 `.litertlm` 扩展名本身仍不保证模型初始化成功.

******

### 路线图

******

路线图按可交付的用户功能组织, 每项均可单独勾选与验收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### 版本历史

******

# v1.1.0

###### 2026/08/21

* `新增` 插件品牌与运行时标识统一为 On-Device AI (设备端 AI), 同步应用名, 包名, 组件名, 发现标识, 协议 API, 构建产物及文档
* `新增` 适配 AutoJs6 `ai.ask`/`ai.chat`/`ai.stream` 的 `plugin: true` 简写选择器及 `ai.models` 模型枚举
* `新增` 通过 On-Device AI 协议 1.1 将 `temperature`, `topK`, `topP` 与 `maxTokens` 透传至 LiteRT-LM 采样和输出 token 控制
* `新增` 通过 AutoJs6 `ai.chat().usage` 和流式 usage 事件返回 LiteRT-LM 精确的输入, 输出及总 token 数, 以及插件实测生成耗时
* `新增` On-Device AI 协议 1.2 持久会话及 AutoJs6 `ai.session` 多轮 Conversation 复用, 后续轮次无需重传既有历史
* `新增` 通过 AutoJs6 `structuredJson` 与 `responseSchema` 启用 LiteRT-LM 原生 JSON Schema 约束解码, 支持单次调用, 流式输出和持久会话, 并严格验证完整 JSON
* `新增` 通过协议 1.3 与 AutoJs6 生成选项提供显式 `cpu`, `gpu` 和 `npu` backend profile, 包含设备兼容性报告, 模型/profile 缓存隔离及不可用 profile 禁止回退; GPU 仅在 OpenCL 加载探测成功后声明, NPU 因未打包 EAP runtime 而保持不可用
* `新增` 将固定版本的 LiteRT Community 推荐模型直接下载到用户选择的 SAF 位置, 支持进度, 精确取消, 残缺文件清理, LiteRT-LM 文件头与精确大小/SHA-256 校验, 以及下载后直接导入
* `修复` 修复模型管理界面在系统暗色模式下仍使用亮色主题文字, 导致正文, 复选框和模型列表与深色背景对比不足
* `优化` 更新插件描述, 使用说明及 10 种语言的 README, 与宿主 `ai.*` 本地插件路由的正式化保持一致
* `优化` 重写 ROADMAP 为可逐项勾选的功能路线图

# v1.0.0

###### 2026/08/08

* `新增` On-Device AI 协议 V1 设备端 provider, 插件 ID 和引擎为 `on-device-ai`, provider ID 为 `autojs6.on-device-ai`, 变体为 `default`
* `新增` CPU-only LiteRT-LM 纯文本生成, 支持 system, user 和 assistant 历史及 credit 背压流式输出
* `新增` 通过 SAF 导入 `.litertlm` 到应用私有存储, 包含 8 GiB 上限, 空间预留, SHA-256, fsync 和原子激活
* `新增` 单活动会话, 有界 I/O, descriptor 配额, 取消, 超时, 唯一终态及同签名 AutoJs6 调用方核验
* `新增` 明确不声明 reasoning, tools, structured JSON, usage, 网络或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 种语言的 README, 更新日志, Android 界面和插件说明
* `新增` 模型管理界面可查看完整模型目录和私有存储占用, 并在不复制模型文件的前提下原子切换当前模型
* `优化` 为避免独立 `:provider` 进程竞态, 替换导入后保留先前以 SHA-256 hash 命名的模型代际, 保留文件会继续占用应用私有存储
* `优化` 增加应用级单导入协调器和 fsync pending journal, 在 Activity 重建时保持导入, 支持冷启动恢复和 stale 临时文件清理, 删除仅限本次新建而从未发布的 destination, 并保留已发布, current 和历史 hash 代际
* `依赖` 附加 LiteRT-LM 0.15.0, 用于设备端 CPU 文本生成

##### 更多版本

* [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

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
on-device-ai-api.aar
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
