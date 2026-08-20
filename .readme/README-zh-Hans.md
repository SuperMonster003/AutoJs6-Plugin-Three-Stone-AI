<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>设备端 AI 插件. 使用 LiteRT-LM 在本地设备流式生成文本, 无需联网</p>

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

On-Device AI (设备端 AI) 是 AutoJs6 的官方设备端 AI 文本生成插件. 它在 CPU 上运行用户导入的 LiteRT-LM 模型, 接收纯文本消息历史, 并通过受控流式会话返回纯文本. 全部推理在本地完成, 不联网, 不上传任何数据.

******

### 功能

******

- 通过 Android 系统文件选择器导入 `.litertlm` 模型包, 并将验证后的副本保存到应用私有存储.
- 使用纯文本 system, user 和 assistant 历史创建本地生成请求.
- 通过 credit 背压按序传送文本 chunk, 并只发布一个完成, 错误或取消终态.
- 列出并选择已导入模型, 删除未选中模型, 并在管理界面一键回收未引用模型文件.
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
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1
required host build: 5270
```

插件声明 ON_DEVICE 执行位置和 NONE credential 模式. 它只声明 `streaming` 能力及 `text/plain` 输入输出.

需要宿主构建版本 5270 或更高版本. 发布产物包含 arm64-v8a, x86_64, universal APK.

******

### 宿主集成状态

******

> AutoJs6 (构建 5276 及以上) 的 `ai.ask`, `ai.chat` 与 `ai.stream` 支持本地插件路由: 传入 `plugin: true` 即选择本插件, 单模型场景可省略模型 ID; `ai.models({ plugin: true })` 可枚举已导入模型. 插件未安装, 未在插件中心启用或未导入模型时, 脚本会收到明确的错误提示. 也可通过 `plugin: { component, providerId, modelId }` 显式固定组件.

******

### 安全性和隐私

******

插件不请求网络或存储权限. 模型只通过系统文件选择器授予的 URI 读取, 以流式 SHA-256 校验和 fsync 写入应用私有 `files/models` 目录, 再通过同目录原子 pointer 替换激活. Provider 服务还会核验 AutoJs6 包名, 调用 UID 归属及双方签名.

******

### 运行限制

******

- 模型导入硬上限为 8 GiB, 且导入后至少保留 256 MiB 可用空间.
- 应用级单导入协调器使 Activity 重建不会中断正在进行的导入. Fsync pending journal 支持冷启动恢复并清理 stale `.incoming`, `.current` 和 `.pending` 临时文件. 恢复只会删除本次尝试新建且从未由 current metadata 发布的 destination, 已发布或 current 模型及历史 hash 代际均会保留.
- 为避免与独立 `:provider` 进程发生竞态, 导入时不会自动删除先前以 SHA-256 hash 命名的模型代际. 模型管理界面可删除未选中的 catalog 模型, 并回收不再由 catalog 引用的 hash 命名文件.
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

路线图按可交付的用户功能组织, 每项均可单独勾选与验收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### 版本历史

******

# v1.1.0

###### 2026/08/20

* `新增` 插件品牌与运行时标识统一为 On-Device AI (设备端 AI), 同步应用名, 包名, 组件名, 发现标识, 协议 API, 构建产物及文档
* `新增` 适配 AutoJs6 `ai.ask`/`ai.chat`/`ai.stream` 的 `plugin: true` 简写选择器及 `ai.models` 模型枚举
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
