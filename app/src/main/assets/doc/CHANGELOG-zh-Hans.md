******

### 版本历史

******

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
