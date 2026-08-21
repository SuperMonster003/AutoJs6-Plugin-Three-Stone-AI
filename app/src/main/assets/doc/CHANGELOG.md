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
* `修复` 修复 10 种本地化插件说明中的底层 Binder 示例仍调用协议 1.1 的 14 参数 `AiGenerationOptions` 构造方法, 导致其在协议 1.3 API 下报告 Java 构造方法不存在
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
