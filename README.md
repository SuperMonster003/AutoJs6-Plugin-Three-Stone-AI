<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>统一 AI 插件. LiteRT-LM 推理始终在本地; 在线目标始终由用户明确选择</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### 简介

******

3-Stone AI 是 AutoJs6 的官方 AI 文本生成插件. 它可在显式选择的 CPU 或兼容 GPU backend 上运行用户导入的 LiteRT-LM 模型, 并且只连接用户配置的在线 profile. 本地与在线执行位置共用一个 AI Provider V2 目标目录, 受控流式管线及明确选择边界. 本地目标绝不联网或上传数据; 在线目标仅在用户明确选择后执行, 且始终绑定插件管理的凭据及其声明的 HTTPS origin.

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
- 可选地将每个导入模型初始化一次, 持久化其"可用/不兼容"状态, 并可在模型管理界面重新检查.
- 通过 credit 背压按序传送文本 chunk, 并只发布一个完成, 错误或取消终态.
- 列出, 选择和重命名已导入模型, 删除未选中模型, 并在管理界面一键回收未引用模型文件.
- 通过 AutoJs6 显式选择 `cpu`, `gpu` 或 `npu` backend; CPU 为默认值, GPU 仅在 OpenCL 加载探测通过后开放, NPU 因未打包 EAP 运行时而明确报告不可用.
- 在应用设置中管理内置及自定义在线 profile, Android Keystore 凭据, 默认在线目标, 计量网络访问和显式有界连接测试.
- 将每个启动器会话绑定到一个本地或在线目标快照; 更改有消息的会话时默认建议新建会话, 继续当前会话必须明确确认并记录变更.
- 为每条助手回复记录实际 target/provider/model/locality 快照; 重新生成沿用该已记录目标, 身份变化或不可用时明确拒绝, 绝不静默回退到会话默认目标.
- 本地和云端生成失败始终停留在所选边界: 启动器聊天追加有界且不含敏感信息的失败原因, 明确说明未发生跨边界自动回退, 并在保留部分输出的同时提供显式手动目标切换入口.

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
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V2
required host build: 5276
```

AI Provider V2 通过分页目录统一公开 `local:*` 与 `profile:*` 目标. 每个目标分别声明 provider, model, locality, 配置及可用状态, 能力, 限制, 控件与 HTTPS origins. 目录仅含本地目标时声明 ON_DEVICE/NONE; 存在在线 profile 时声明 HYBRID/PLUGIN_MANAGED 及其 HTTPS origins 精确并集. 本地 backend profile 是可选目标控件, 不可用 profile 或目标绝不静默回退.

需要宿主构建版本 5276 或更高版本. 发布产物包含 arm64-v8a, x86_64, universal APK.

******

### 宿主集成状态

******

> 在 AutoJs6 (构建 5276 及以上) 中, `ai.catalog()` 将全部已导入本机模型与已配置在线 profile 作为统一目标目录返回, 包含精确 ID, 提供方, 模型, 本机/在线属性, 配置与可用状态, 能力, 控制项, 限制, 来源及本机 backend profile. 向 `ai.ask`, `ai.chat`, `ai.stream` 或 `ai.session` 传入精确 `target`; 仅传 `target` 会选择官方 3-Stone AI 插件, `plugin: true` 则使用其声明的默认目标. 本机目标可提供 `cpu`, `gpu` 与不可用的 `npu`, 在线目标没有本机执行 profile. backend 或目标不可用时会直接失败且不回退, 本机与在线路由也绝不自动切换. 完成及流式响应公开 target, plugin, profile, reasoning, finish reason, 完整 usage 和提供端实测耗时. 稳定错误码可区分提供方缺失或停用, 目标未知, 未配置, 不可用或能力不匹配, 以及 backend 不可用. `responseSchema` 会隐式启用结构化输出; 仅设置 `structuredJson: true` 时使用默认对象根 schema, 持久会话在所有轮次固定同一 target, schema 与可选 backend.

******

### 安全性和隐私

******

插件为用户主动发起的推荐模型下载及用户配置的在线目标请求 `INTERNET` 权限; 本地生成不使用网络. 插件不请求广泛存储权限. 目录下载使用不可变 HTTPS 版本及固定字节数和 SHA-256, 仅写入用户选择的 SAF 位置; LiteRT-LM 文件头, 大小, 摘要, flush 与 fsync 全部通过后才算完成. 导入仍只读取系统选择器授予的 URI, 将验证副本流式写入应用私有 `files/models` 并原子激活. Provider 服务还会核验 AutoJs6 包名, 调用 UID 归属, 双方签名, 目标元数据及声明来源边界.

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
- `maxTokens` 接受 1 至 2,147,483,647 的整数. 省略时将输出 token 数交由模型或引擎默认值决定, 插件的 64 KiB 输出安全上限仍然生效. `temperature` 必须为非负有限数, `topK` 必须为正整数, `topP` 必须为 0 至 1 的有限数. 三项采样参数全部省略时保留模型或引擎默认值; 部分覆盖时, 未设置项使用 LiteRT-LM 基线 `topK: 1`, `topP: 0.95`, `temperature: 1`.
- 流式输出使用有限 credit 和有界 chunk, 防止无限制缓冲或无背压回调.
- Usage token 数直接来自 LiteRT-LM Conversation 的 KV cache 与 decode 计数, 不做字符数估算. `durationMillis` 只测量插件生成调用, 不包含宿主发现, 绑定, 模型枚举和分发时间.
- 持久 `ai.session` 只允许一个活动轮次, 正常完成后保留原生 Conversation; 取消, 超时, 生成失败或显式关闭后必须重新创建会话.
- 取消, 会话关闭和超时会停止结果发布, 并通过唯一终态结束请求.

******

### 未声明的能力

******

- 不声明 reasoning 或 tools 能力.
- 不接受 tool 角色消息, tool schema, tool call 或 tool result.
- 不提供联网模型发现或任意 URL 模型下载. 启动器聊天与 AI Provider V2 只公开已导入本地模型及用户明确配置的在线 profile, 且只能下载内置目录中固定版本的推荐模型.
- 不声明 NPU 推理可用: profile 可发现但以 `npu-runtime-not-packaged` 标记为 `unavailable`. GPU 仅在 `libOpenCL.so` 可加载时声明, 且 `.litertlm` 扩展名本身仍不保证模型初始化成功.

******

### 路线图

******

路线图按可交付的用户功能组织, 每项均可单独勾选与验收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本历史

******

# v1.1.1

###### 2026/09/11

* `优化` 构建阶段校验 64 位原生库的 16 KB 页大小对齐, 检查 manifest 契约并输出 JSON 报告

# v1.1.0

###### 2026/09/01

* `新增` 插件品牌与运行时标识统一为 3-Stone AI, 同步应用名, 包名, 组件名, 发现标识, 构建产物及文档
* `新增` 跨进程集成统一采用中性 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` 及 `IAiProvider`/`IAiSession`/`IAiCallback` 身份, 不保留被替换身份的别名
* `新增` 增加受签名权限保护, 可导出且无参数的 AI 设置入口, AutoJs6 可直接打开插件统一设置且不发送档案或凭据数据
* `新增` 通过 AI Provider V2 分页目标目录直接公开 `local:*` 与 `profile:*`, 每项目标独立声明 provider/model/locality, 配置与可用状态, capabilities, limits, controls, HTTPS origins 及精确的 `isDefault` 标记
* `新增` 通过 AI Provider V2 生成请求将 `temperature`, `topK`, `topP` 与 `maxTokens` 透传至 LiteRT-LM 采样和输出 token 控制
* `新增` 通过 AutoJs6 `ai.chat().usage` 和流式 usage 事件返回 LiteRT-LM 精确的输入, 输出及总 token 数, 以及插件实测生成耗时
* `新增` AI Provider V2 持久会话及 AutoJs6 `ai.session` 多轮 Conversation 复用, 后续轮次无需重传既有历史
* `新增` 通过 AutoJs6 `structuredJson` 与 `responseSchema` 启用 LiteRT-LM 原生 JSON Schema 约束解码, 支持单次调用, 流式输出和持久会话, 并严格验证完整 JSON
* `新增` 将显式 `cpu`, `gpu` 和 `npu` backend profile 作为 AI Provider V2 可选目标控件, 包含设备兼容性报告, 模型/profile 缓存隔离及不可用 profile 禁止回退; GPU 仅在 OpenCL 加载探测成功后声明, NPU 因未打包 EAP runtime 而保持不可用
* `新增` 将固定版本的 LiteRT Community 推荐模型直接下载到用户选择的 SAF 位置, 支持进度, 精确取消, 残缺文件清理, LiteRT-LM 文件头与精确大小/SHA-256 校验, 以及下载后直接导入
* `新增` 新增可由启动器打开的会话工作区, 支持流式 Markdown, 持久会话历史, 编辑旧消息时的分支替换风险提醒, 多结果搜索及软键盘适配输入
* `新增` 新增主题色, 暗色模式, 应用语言, 应用与开发者信息及版本历史等应用设置, 可跟随 AutoJs6 的选项默认均设为跟随 AutoJs6
* `新增` 新增字体大小, Enter 键行为, 无限制或自定义 output token, 以及模型默认或自定义 `temperature`, `topK`, `topP` 等会话设置
* `新增` 支持在流式输出中渲染内联 `$\text{...}$` 内容, 并适配常用数学命令, 上标与下标样式
* `新增` 新增插件自管的 Android Keystore 凭据仓库, 使用 AES-256-GCM, 与 profile 绑定的认证密文, 跨进程原子私有文件, 仅 configured 状态查询及明文即时清零
* `新增` 新增严格的非敏感在线配置档案仓库, 仅接受 HTTPS OpenAI Compatible 端点, 使用 canonical UUID 与跨进程原子元数据, 并在 provider 或 origin 变更时强制明确替换或清除凭据
* `新增` 新增插件内部 OpenAI Compatible HTTPS 执行后端, 支持自定义 baseUrl, 凭据和模型名, 有界 SSE 与 JSON 回退流式响应, 精确取消, provider usage, 完成轮次多轮历史, JSON Schema 请求映射及不含敏感信息的固定错误; 已配置的 `profile:*` 目标可通过 AI Provider V2 直接调用
* `新增` 新增与宿主目录对齐的 OpenAI, Anthropic, Gemini, DeepSeek 与 OpenRouter 预置模板; 统一在线执行层复用 OpenAI-compatible 协议, 并分别适配 Anthropic Messages 与 Gemini GenerateContent 的原生认证, 请求, SSE 终态, usage 和 JSON Schema, 不提供协议间或本地/在线自动回退
* `新增` 新增 10 语言在线服务设置 UI, 支持档案添加, 编辑, 删除, 不回显的 API Key 替换与清除, 默认目标选择, 在读取凭据前强制执行的计量网络开关, 以及可取消且最长 120 秒的显式连接测试; 设置与档案共用跨进程原子文档并动态刷新 V2 目标目录
* `新增` 启动器聊天新增统一本地/云端目标选择器: 每个会话持久化一个目标快照, 有消息的会话切换时默认建议新建会话, 携带既有上下文继续当前会话必须明确确认并记录变更
* `新增` 会话历史为每条助手回复保存实际 target/provider/model/locality 快照; 重新生成默认精确沿用原响应目标, 目标身份变化或不可用时明确失败, 不静默回退到当前会话目标
* `新增` 本地和云端生成失败始终停留在所选边界: 启动器聊天追加有界且不含敏感信息的失败原因, 明确说明未发生跨边界自动回退, 并在保留部分输出的同时提供显式手动目标切换入口
* `修复` 移除插件说明可运行示例默认设置的 256 token 与 4 KiB 输出限制: 省略 `maxTokens` 时改用模型或引擎默认值, raw Binder 示例使用插件完整的 64 KiB 输出额度
* `修复` 将 10 种本地化插件说明中的底层 Binder 示例更新为最终 AI Provider V2 请求及目标目录 API
* `修复` 修复模型管理界面在系统暗色模式下仍使用亮色主题文字, 导致正文, 复选框和模型列表与深色背景对比不足
* `修复` 确保输入框位于软键盘上方, 根据当前主题色对比度选择发送按钮文字颜色, 并统一搜索的上一个, 下一个及关闭控件
* `修复` 修复在生成 listener callback 内关闭 session 时 callback quiescence 等待自身而死锁; 关闭仍会等待其他线程中已开始的 callback
* `修复` 修复 Android 将可信的 `/data/user/0` 应用数据根规范化为 `/data/data` 时误拒绝应用私有在线档案与凭据存储的问题; 仍会拒绝直接子项符号链接及目录逃逸
* `优化` 更新插件描述, 使用说明及 10 种语言的 README, 与宿主 `ai.*` 统一目标路由的正式化保持一致
* `优化` 重写 ROADMAP 为可逐项勾选的功能路线图
* `优化` 将应用及生成的本地化文档标点统一为 ASCII, 并增加覆盖打包文本和生成文本的回归测试
* `优化` 引入共享的 `AiBackend`/`AiTarget`/`AiBackendSession` 层, 使启动器聊天和 Binder Provider 共用 `LiteRtLocalBackend` 的目录, 能力, 会话创建, 流式输出及取消路径
* `优化` 将本地 `local:*` 与在线 `profile:*` 目标合入 Application 级统一目录及分发层, 通过 AI Provider V2 直接公开二者, 并从当前目录动态推导 provider locality, credential mode 和 HTTPS origins, 不公开凭据字节
* `优化` 统一 README 版式与 Gradle 平台版本管理方式
* `优化` 插件更新对话框的发行历史按钮改为打开内置发行历史页面

# v1.0.0

###### 2026/08/08

* `新增` 设备端 AI Provider 基础实现, 插件 ID 和引擎为 `three-stone-ai`, provider ID 为 `autojs6.three-stone-ai`, 变体为 `default`
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

* [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

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
ai-provider-api.aar
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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/ui-redesign/docs/16kb.md)
