# AutoJs6 AI 文本生成

此插件通过 Android Storage Access Framework (SAF) 导入一个本地 `.litertlm` 模型包, 将其复制到应用私有存储, 并使用 CPU-only LiteRT-LM 根据纯文本历史生成流式纯文本.

插件需要 AutoJs6 宿主构建版本 5270 或更高版本, 以及 Android API 24 或更高版本.

安全性和运行限制:

- 模型导入上限为 8 GiB, 完成后必须至少保留 256 MiB 可用空间.
- 上下文上限为 256 KiB, 输出上限为 64 KiB, 同一时间仅允许一个生成会话.
- Provider 不声明 token 数量上限, 因此不支持设置 `maximumOutputTokens` 的请求.
- 仅声明 streaming 和 `text/plain`. 不支持 reasoning, tools, structured JSON 和 usage.
- 插件不请求网络或存储权限.
- 仅允许同签名 AutoJs6 宿主绑定 provider 服务.
- 为保证跨进程安全, 会保留先前以 SHA-256 hash 命名的模型代际, 它们会继续占用应用私有存储.
