******

### 版本历史

******

# v1.1.0

###### 2026/08/20

* `新增` 插件名称调整为 On-Device AI (设备端 AI), 明确定位为 AutoJs6 官方设备端 AI 插件
* `新增` 适配 AutoJs6 `ai.ask`/`ai.chat`/`ai.stream` 的 `plugin: true` 简写选择器及 `ai.models` 模型枚举
* `优化` 更新插件描述, 使用说明及 10 种语言的 README, 与宿主 `ai.*` 本地插件路由的正式化保持一致
* `优化` 重写 ROADMAP 为可逐项勾选的功能路线图

# v1.0.0

###### 2026/08/08

* `新增` AI Text Generation 协议 V1 设备端 provider, 插件 ID 和引擎为 `ai-text-generation`, provider ID 为 `autojs6.local.text`, 变体为 `default`
* `新增` CPU-only LiteRT-LM 纯文本生成, 支持 system, user 和 assistant 历史及 credit 背压流式输出
* `新增` 通过 SAF 导入 `.litertlm` 到应用私有存储, 包含 8 GiB 上限, 空间预留, SHA-256, fsync 和原子激活
* `新增` 单活动会话, 有界 I/O, descriptor 配额, 取消, 超时, 唯一终态及同签名 AutoJs6 调用方核验
* `新增` 明确不声明 reasoning, tools, structured JSON, usage, 网络或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 种语言的 README, 更新日志, Android 界面和插件说明
* `新增` 模型管理界面可查看完整模型目录和私有存储占用, 并在不复制模型文件的前提下原子切换当前模型
* `优化` 为避免独立 `:provider` 进程竞态, 替换导入后保留先前以 SHA-256 hash 命名的模型代际, 保留文件会继续占用应用私有存储
* `优化` 增加应用级单导入协调器和 fsync pending journal, 在 Activity 重建时保持导入, 支持冷启动恢复和 stale 临时文件清理, 删除仅限本次新建而从未发布的 destination, 并保留已发布, current 和历史 hash 代际
* `依赖` 附加 LiteRT-LM 0.15.0, 用于设备端 CPU 文本生成
