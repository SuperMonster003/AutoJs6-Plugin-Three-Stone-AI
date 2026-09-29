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

3-Stone AI 是 AutoJs6 的官方 AI 文本生成插件. 它可在显式选择的 CPU 或兼容 GPU backend 上运行用户导入的 LiteRT-LM 模型, 在线生成则使用用户配置的 profile. 本地与在线执行位置共用一个 AI Provider V2 目标目录, 受控流式管线及明确选择边界. 本地目标绝不联网或上传数据; 在线目标仅在用户明确选择后执行, 且始终绑定插件管理的凭据及其声明的 HTTPS origin.

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
- 在线 OpenAI 兼容, Anthropic Messages 与 Gemini GenerateContent 目标支持原生工具调用, 包含流式参数, 并行调用与结果续轮.
- 在线模型通过协商 AI Provider 2.1 接收 JPEG/PNG 图片及工具结果图片, 支持按具体模型启用.
- 从 GitHub 项目公开目录更新在线预置模型. 自动更新默认开启, 仅在打开在线 AI 设置时每 24 小时检查一次, 也可关闭自动更新或手动刷新. 更新遵循计量网络设置, 离线或更新失败时继续使用缓存或内置列表, 不改动已保存配置与自定义模型 ID.
- 按厂商分组浏览预置模型, 并扩充 OpenRouter 中的 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型选择, 保留准确的模型 ID.
- 启动器图标可选择自适应亮色, 自适应暗色 (默认), 自适应自动或透明背景. 自动模式尝试跟随系统主题, 但启动器可能缓存单一配色; 透明图标可能被启动器添加背景或遮罩. 切换保持应用运行, 显示刷新可能需要几秒钟.

******

### 模型和数据格式

******

支持的模型与输入格式:

```text
model package: .litertlm
input: text/plain history + application/json schema; negotiated 2.1: image/jpeg and image/png descriptors
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
protocol: V2 (2.0 / 2.1)
required host build: 5276
```

AI Provider V2 通过分页目录统一公开 `local:*` 与 `profile:*` 目标. 每个目标分别声明 provider, model, locality, 配置及可用状态, 能力, 限制, 控件与 HTTPS origins. 目录仅含本地目标时声明 ON_DEVICE/NONE; 存在在线 profile 时声明 HYBRID/PLUGIN_MANAGED 及其 HTTPS origins 精确并集. 本地 backend profile 是可选目标控件, 不可用 profile 或目标绝不静默回退.

需要宿主构建版本 5276 或更高版本及 Android 7.0 或更高版本. 构建产物包含 armeabi-v7a, arm64-v8a, x86, x86_64, universal APK. x86 和 armeabi-v7a 设备请使用对应的独立 APK, 支持应用界面, 在线 AI 和宿主插件集成. LiteRT-LM 本地推理需要 arm64-v8a 或 x86_64 及安装包中的对应原生库. universal APK 仅包含 64 位原生库, 无法安装到仅支持 32 位的设备.

******

### 宿主集成状态

******

> 在 AutoJs6 (构建 5276 及以上) 中, `ai.catalog()` 将全部已导入本机模型与已配置在线 profile 作为统一目标目录返回, 包含精确 ID, 提供方, 模型, 本机/在线属性, 配置与可用状态, 能力, 控制项, 限制, 来源及本机 backend profile. 向 `ai.ask`, `ai.chat`, `ai.stream` 或 `ai.session` 传入精确 `target`; 仅传 `target` 会选择官方 3-Stone AI 插件, `plugin: true` 则使用其声明的默认目标. 本机目标可提供 `cpu`, `gpu` 与不可用的 `npu`, 在线目标没有本机执行 profile. backend 或目标不可用时会直接失败且不回退, 本机与在线路由也绝不自动切换. 完成及流式响应公开 target, plugin, profile, reasoning, finish reason, 完整 usage 和提供端实测耗时. 稳定错误码可区分提供方缺失或停用, 目标未知, 未配置, 不可用或能力不匹配, 以及 backend 不可用. `responseSchema` 会隐式启用结构化输出; 仅设置 `structuredJson: true` 时使用默认对象根 schema, 持久会话在所有轮次固定同一 target, schema 与可选 backend.

******

### 安全性和隐私

******

插件为用户主动发起的推荐模型下载, 用户配置的在线目标请求, 以及在线 AI 设置中从 GitHub 更新公开预置模型目录使用 `INTERNET` 权限. 目录更新无需 API Key, 不调用推理服务, 插件激活及本地生成不会触发目录联网. 插件不请求广泛存储权限. 推荐模型文件下载使用不可变 HTTPS 版本及固定字节数和 SHA-256, 仅写入用户选择的 SAF 位置; LiteRT-LM 文件头, 大小, 摘要, flush 与 fsync 全部通过后才算完成. 导入仍只读取系统选择器授予的 URI, 将验证副本流式写入应用私有 `files/models` 并原子激活. Provider 服务还会核验 AutoJs6 包名, 调用 UID 归属, 双方签名, 目标元数据及声明来源边界.

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
- 在 设置 > 在线 AI 中编辑配置并选择支持图片输入的模型. 已有配置默认关闭. 图片仅发送给所选配置的服务. LiteRT 与持久 ai.session 仍仅支持文本; Agent 截图要求 Android 11+, 兼容的 AutoJs6 与 AI Agent, observe 工具组及为所选精确模型启用图片输入.
- AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通过真实初始图片与工具结果图片探针. 合成图片探针不代表其他目标也支持图片或已通过完整 Agent 视觉任务.
- 在线失败仅通过可选的 `providerCode` 字段提供固定的 `ONLINE_*` 分类. 未知异常不提供分类; 不包含 URL, 凭据, 服务响应或原始异常文本. 失败代码与重试策略保持不变.

******

### 未声明的能力

******

- 不声明 reasoning 输出能力. 本地 LiteRT-LM 目标仍不声明 tools 能力.
- 原生工具最多 16 轮, 同时最多 32 个调用. 结果必须完整匹配待处理批次; 上下文/输出限制, 取消与原始截止时间仍生效. 原生工具暂不与持久 ai.session 轮次组合, 初始历史不接受 tool 角色.
- 不支持从任意 URL 下载模型文件. 在线预置模型可从项目公开目录刷新, 但不会据此创建配置或查询账户权限. 启动器聊天与 AI Provider V2 只公开已导入本地模型及用户明确配置的在线 profile, 本地模型下载仍限于内置目录中固定版本的推荐模型.
- 不声明 NPU 推理可用: profile 可发现但以 `npu-runtime-not-packaged` 标记为 `unavailable`. GPU 仅在 `libOpenCL.so` 可加载时声明, 且 `.litertlm` 扩展名本身仍不保证模型初始化成功.

******

### 路线图

******

路线图按可交付的用户功能组织, 每项均可单独勾选与验收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本历史

******

# v1.3.0

###### 2026/09/29

* `新增` 启动器图标可选择自适应亮色, 自适应暗色 (默认), 自适应自动或透明背景. 自动模式尝试跟随系统主题, 但启动器可能缓存单一配色; 透明图标可能被启动器添加背景或遮罩. 切换保持应用运行, 显示刷新可能需要几秒钟.
* `优化` 关于页面保留图标圆角描边容器, 透明内部透出与容器外侧一致的页面底色; 启动器图标选项从顶部展示并采用较小的说明文字.

# v1.2.1

###### 2026/09/29

* `提示` 32 位兼容性修改为尚未发布的候选版本. 已执行的检查及未覆盖的设备见 docs/dev/32-bit-compatibility-2026-09-29.md.
* `修复` 宿主传来的工具结果图片以宿主私有缓存中的普通文件描述符交付时, Provider 经 /proc/self/fd 重新打开会因无权遍历宿主目录而失败 (EACCES), 整轮工具续轮以 PROTOCOL_VIOLATION 中止; 现在对普通文件回退为复制描述符直接读取 (普通文件不会阻塞读取), 管道仍使用私有的非阻塞重开描述符. 真机 (Sony XQ-DQ72, AutoJs6 5298, 3-Stove Agent 1.3.0) 上 screen_capture 图片经原生工具结果送入 Codex / Gemini 模型时曾必现此失败
* `修复` 原生工具调用的每一轮工具结果被接受后, 该次生成的超时重新计时: 此前整个工具轮 (包括等待工具结果的时间) 共用首个请求的超时, 超过它的多轮任务一律以 TIMEOUT 结束; 现在只有在未提交结果时才按原超时到期. 与 AutoJs6 宿主和 3-Stove Agent 的同步改动配合
* `修复` 新增 x86 和 armeabi-v7a 安装包以支持在线 AI 及宿主集成; 根据进程架构和已安装的原生库判断本地推理能力, 并在模型管理页说明不可用原因
* `优化` 统一 Three 系列启动器图标为浅色图案配深色背景, 插件中心与应用内图案随应用主题切换并保持透明背景, 避免部分设备出现启动器背景套环

# v1.2.0

###### 2026/09/26

* `提示` 开发候选版本, 尚未发布. 在线工具调用经 AI Provider V2 提供; Agent 集成需要 build 5297+ 的宿主原生工具代理及支持此能力的 Agent 版本. 插件向宿主返回调用, 自身不执行设备操作.
* `提示` 在 设置 > 在线 AI 中编辑配置并选择支持图片输入的模型. 已有配置默认关闭. 图片仅发送给所选配置的服务. LiteRT 与持久 ai.session 仍仅支持文本; Agent 截图要求 Android 11+, 兼容的 AutoJs6 与 AI Agent, observe 工具组及为所选精确模型启用图片输入.
* `提示` AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通过真实初始图片与工具结果图片探针. 合成图片探针不代表其他目标也支持图片或已通过完整 Agent 视觉任务.
* `新增` 在线 OpenAI 兼容, Anthropic Messages 与 Gemini GenerateContent 目标支持原生工具调用, 包含流式参数, 并行调用与结果续轮
* `新增` 在线模型通过协商 AI Provider 2.1 接收 JPEG/PNG 图片及工具结果图片, 支持按具体模型启用
* `新增` 新增在线预置模型自动更新与手动刷新, 支持本地缓存及离线回退, 保留已保存配置与自定义模型 ID
* `新增` 按厂商分组浏览预置模型, 并扩充 OpenRouter 中的 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型选择, 保留准确的模型 ID
* `修复` 描述符读取在取消或超时后释放工作线程, 并保留可靠管道的生产者错误
* `修复` 通过 AI Provider 回调保留固定的在线失败分类, 不暴露请求或响应内容, 不增加自动重试
* `修复` 修复 IntelliJ IDEA F10 运行时 No APK found 错误, 按构建变体读取 AGP 实际 APK 目录, 保留 16 KB 对齐检查
* `优化` 根据官方目录更新在线预置模型, 包含 Claude Fable 5.1 等当前模型并移除已停用的模型 ID, 保留已有配置与自定义模型

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/docs/16kb.md)
