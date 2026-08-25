# Online Provider Backend

本文记录 P1 插件内部统一在线执行层, 在线服务设置页与启动器聊天目标选择器的实际契约. 它不改变 AI Provider V1 的本地模型公开范围; 用户可在插件设置中管理在线 target, 并在插件启动器聊天中明确选择本地或在线 target. 宿主脚本仍要等 V2 统一目标协议接入后才能选择在线 target.

## 提供方目录

内置目录与 AutoJs6 宿主保持相同的稳定顺序. 模型 ID 不写死在模板中, 仍由用户创建 profile 时填写, 以免发布包携带会快速过期的模型目录.

| providerId | 显示名 | 默认 baseUrl | 协议 | 认证 | JSON Schema |
|---|---|---|---|---|---|
| `openai` | OpenAI | `https://api.openai.com/v1` | OpenAI-compatible Chat Completions | `Authorization: Bearer` | strict `json_schema` |
| `anthropic` | Anthropic | `https://api.anthropic.com/v1` | Anthropic Messages | `x-api-key` + `anthropic-version: 2023-06-01` | `output_config.format` |
| `gemini` | Gemini | `https://generativelanguage.googleapis.com/v1beta` | Gemini GenerateContent | `x-goog-api-key` | `responseMimeType` + `responseSchema` |
| `deepseek` | DeepSeek | `https://api.deepseek.com` | OpenAI-compatible Chat Completions | `Authorization: Bearer` | 不声明; 当前公开契约只有 text/json_object, 不冒充 strict schema |
| `openrouter` | OpenRouter | `https://openrouter.ai/api/v1` | OpenAI-compatible Chat Completions | `Authorization: Bearer` | strict `json_schema` |
| `openai-compatible` | OpenAI Compatible | 无 | OpenAI-compatible Chat Completions | `Authorization: Bearer` | 由用户配置的服务负责兑现 |

参考提供方公开契约: [OpenAI Chat Completions](https://platform.openai.com/docs/api-reference/chat), [Anthropic Messages](https://platform.claude.com/docs/en/api/messages), [Anthropic streaming](https://platform.claude.com/docs/en/build-with-claude/streaming), [Gemini GenerateContent](https://ai.google.dev/api/generate-content), [DeepSeek Chat Completions](https://api-docs.deepseek.com/api/create-chat-completion/), [OpenRouter Chat Completions](https://openrouter.ai/docs/api/api-reference/chat/send-chat-completion-request).

`OnlineAiProviderCatalog` 只保存非敏感模板元数据. `OnlineAiProfile` 保存 canonical UUID, displayName, providerId, HTTPS baseUrl 与 modelId; 凭据只由插件的 Keystore 仓库按 profileId 提供, 不进入 profile JSON, target catalog 或 Binder 对象. 所有在线档案统一映射为 `backendId=online`, `targetId=profile:*`, `REMOTE` 与 `PLUGIN_MANAGED`.

## 设置页与配置状态

应用设置中的 "在线服务" 页面提供以下显式操作:

- 添加与编辑档案: 内置模板只预填 provider 与 baseUrl, 模型 ID 仍由用户输入. 自定义 OpenAI-compatible 档案必须输入 HTTPS baseUrl.
- API Key 输入框从不回显既有值, 禁止 Autofill 与 Activity 状态保存, 编辑弹窗期间启用 `FLAG_SECURE`, 弹窗关闭时立即清空输入缓冲. 更换 provider 或 HTTPS origin 时, 已配置档案必须重新输入 Key.
- 清除 Key 与删除档案均要求二次确认. 清除默认档案的 Key 时同时清除默认选择; 删除会先清除 Keystore 密文, 再原子发布不含该档案的元数据, 并在同一文档事务中清除默认值.
- 默认在线目标只能从已配置档案中选择. 统一目录保留本地 target 排序, 但显式选择的在线默认值优先于本地 backend 默认值; 目标不可用时明确失败, 不自动回退本地.
- "允许移动网络和按流量计费网络" 默认关闭. 设置只影响在线请求, 不影响本地推理或模型下载.

非敏感 profile 文档直接采用 schema 2, 将 `defaultProfileId` 与 `allowMeteredNetwork` 和档案列表放在同一个跨进程锁, fsync 与原子 rename 事务内. 本项目尚未发布, 因此不提供 schema 1 读取或迁移分支; 不支持的文档会 fail closed. 凭据仍完全独立保存在 Android Keystore 保护的密文仓库中.

"测试连接" 是一次用户确认后才会执行的真实生成: 固定发送 `Reply with OK.`, 最多请求 8 个输出 token, 不保存或显示响应文本, 可随时取消, UI 在 120 秒后主动取消活动 Call. 测试调用统一 `OnlineAiBackend` 和协议适配器, 因而同时验证 profile, Key, 网络策略, 请求认证, 协议解析及正常终态. 它可能产生提供方费用, 所以 UI 在每次执行前明确提示. 120 秒上限覆盖了 G8441 上维护者早期观测到约 64 秒才完成的宿主在线调用, 同时保持测试有界; 同一设备随后在插件端配置 PoloAPI OpenAI-compatible profile 后, 连接测试实测成功且耗时不足 10 秒.

## 启动器聊天目标绑定

启动器聊天直接读取 Application 级 `AiTargetCatalog`, 不再从所选本地模型推导 `local:*` 目标. 选择在线 profile 时, `AiBackendSessionRequest` 使用原始 `profile:*` targetId 且不传本地 execution profile; 选择本地模型时优先使用可用 CPU profile, 否则使用目录声明的首个可用本地 profile. 两种路径共用相同的流式, 取消, Markdown 与会话复用管线, 任何失败都不会跨本地与在线边界回退.

- 新的空会话只在首次解析统一目录时捕获一次默认 target. 后续本地模型选择或默认在线档案变化不会静默改写当前会话.
- `ConversationTargetSnapshot` 保存 targetId, providerId, modelId, displayName 与 locality. 会话级目标与每条 assistant 消息的实际执行目标共用该不可变快照. 会话历史二进制格式直接升级为 version 3; 项目尚未发布, 因此不保留 version 1/2 的读取或迁移分支.
- 顶部目标栏始终显示 Local/Cloud, provider, 显示名与 modelId, 点击后列出统一目录中的全部 target. 未配置或不可用 target 不能被选中, 只提供相应的本地模型或在线服务设置入口; Cloud 项明确标注可能产生费用.
- 空会话可直接更换 target. 已有消息的会话默认主操作是使用所选 target 新建会话; "继续当前会话" 是次要操作, 对话框会明确说明保留消息将作为上下文交给新 target, Cloud 情况额外说明数据离开设备及可能产生费用.
- 用户确认继续当前会话后, 页面关闭既有 backend session, 更新会话级目标快照, 写入可见的 target-change notice 并立即持久化. 若已保存 target 从目录消失或变为不可用, 会话保持原快照并禁用发送, 不自动采用新的默认 target.
- 每条 assistant 占位消息在请求开始前先捕获目录中的目标快照; backend session 建立后再以 `AiBackendSession.target` 校正并立即持久化实际 target. 完成, 失败, 停止及 Activity 状态恢复均保留 target/provider/model/locality, user 与 notice 消息则禁止携带目标快照.
- "重新生成" 精确沿用原响应快照, 但不改写会话默认 target. 历史 targetId 只有在 providerId, modelId 与 locality 仍一致时才可解析; 仅显示名重命名可继续. 目标被删除, 不可用或身份漂移时明确失败并提供对应设置入口, 绝不回退到当前会话目标. 当原响应目标不同于会话默认目标时, 确认对话框会显示实际目标以及本地/云端隐私和费用边界.
- G8441 / Android 9 真机以 `Cloud / OpenAI Compatible / PoloAPI / claude-opus-4-8` 完成首次生成与同一响应的重新生成. 两次目标栏身份保持一致且响应内容不同; provider usage 与耗时分别显示为 `18788 input | 106 output | 8.0 s` 和 `18786 input | 212 output | 6.0 s`.

文本历史仍可作为跨目标上下文使用, 但每条响应的执行来源独立可审计; target 变化不会改写已经完成的响应快照.

## 请求映射

三个协议适配器共用同一个有界, 一次性且可擦除的 JSON RequestBody, 但不伪造彼此不兼容的字段或事件.

| 通用字段 | OpenAI-compatible | Anthropic Messages | Gemini GenerateContent |
|---|---|---|---|
| system 文本 | `messages[].role=system` | 顶层 `system[]` text block | 顶层 `systemInstruction.parts[]` |
| user/assistant 文本 | `messages[]` | `messages[]` user/assistant | `contents[]` user/model |
| modelId | `model` | `model` | URL `models/{model}:streamGenerateContent` |
| maximumOutputTokens | `max_tokens` | `max_tokens` (省略时使用必填基线 1024) | `generationConfig.maxOutputTokens` |
| temperature | `temperature` | `temperature` | `generationConfig.temperature` |
| topK | 仅 OpenRouter/自定义为 `top_k` | `top_k` | `generationConfig.topK` |
| topP | `top_p` | `top_p` | `generationConfig.topP` |
| reportUsage | `stream_options.include_usage=true` | provider 原生 usage event | provider 原生 `usageMetadata` |
| responseJsonSchema | `response_format.type=json_schema` | `output_config.format.type=json_schema` | `responseMimeType=application/json` + `responseSchema` |

OpenAI-compatible baseUrl 未以 `/chat/completions` 结束时追加该 endpoint. Anthropic 未以 `/messages` 结束时追加 endpoint. Gemini 将 modelId 作为单一路径段追加为 `/models/{model}:streamGenerateContent?alt=sse`; 已有 GenerateContent action 时原位替换, 不重复拼接. 含 `/` 或 `:` 的歧义 Gemini modelId 在发起网络请求前拒绝.

请求始终启用流式模式. DeepSeek profile 在本地拒绝 `responseJsonSchema`, 且 capability 如实为 false. 不实现协议降级或提供方间自动 fallback.

## 网络与凭据边界

- Profile 只接受 HTTPS, 禁止 user-info, query 与 fragment. 每个 target 只声明规范化后的 HTTPS origin.
- Profile 元数据与凭据存储以 `filesDir` 为可信根, 分别拒绝其直接子项的符号链接及 canonical containment 逃逸. Android 可能把 Context 返回的 `/data/user/0` 系统路径规范化为 `/data/data`; 两侧都相对同一个 canonical 可信根校验, 不把这个系统级路径别名误判成直接子项链接.
- 应用声明 `ACCESS_NETWORK_STATE`. 每轮在线请求在读取凭据前检查活动网络是否具备 Internet capability; 系统判定为 metered 的网络必须由用户显式开启. 配置读取失败, 无活动网络或缺少 capability 均 fail closed.
- OkHttp client 禁止 HTTP/HTTPS redirect, connection retry, authenticator, proxy authenticator, cookie, cache, application interceptor 与 network interceptor. EventListener 固定为 `NONE`.
- 认证 header 只在同步凭据作用域内构造. Profile 元数据与凭据在同一锁定快照点配对; 复制后的凭据 buffer 在 Call 成功或异常后清零, 网络期间不持有 profile 或 credential 文件锁.
- 请求 URL, 认证 header, prompt, response body 与底层异常均不记录. 非 2xx body 只做有界丢弃, 不进入异常. HTTP 200 内的 provider error 也只映射为固定错误.
- 本地失败不转在线, 在线失败不转本地. Client 不实现任何隐式 fallback.

连接超时为 30 秒, 写超时为 60 秒. 流式读与 Call 不设置内部总超时; 上层会话拥有截止时间并通过 `Call.cancel()` 精确终止活动请求.

## 响应与会话语义

成功响应只接受 `text/event-stream`, `application/json` 或 `application/*+json`.

- 通用 SSE reader 支持 LF, CRLF, lone CR, BOM, comment, multiline data 及 EOF 前最后一个未终止 event.
- OpenAI-compatible 解析 `choices[0].delta.content`, text/refusal part 与 provider usage; 正常流式完成必须收到 `[DONE]`.
- Anthropic 解析 `message_start`, `content_block_start`, `content_block_delta`, `message_delta` 与 `message_stop`; `[DONE]` 不属于该协议. `stop_reason=refusal` 与 error event 固定映射为 provider error, 不提交历史也不触发其他 provider fallback.
- Gemini 解析 `candidates[0].content.parts[].text`, 忽略标记为 thought 的 part, 并以 `STOP` 或 `MAX_TOKENS` finishReason 结束. Safety/block/recitation 等终止原因固定映射为 provider error.
- JSON fallback 使用各自协议的非流式响应形状. Reasoning 与 tools 当前不解析也不声明 capability.
- Usage 只接受非负整数. OpenAI-compatible 的 `total_tokens` 必须等于 input + output; Gemini 的 `totalTokenCount` 可包含 thoughts 等额外计数, 因此保留 provider 校验语义而不强行套用 OpenAI 等式. Anthropic 分散在多个 event 的 input/output 会在同一轮合并.
- 只有收到正常终态的 turn 才把 user prompt 与完整 assistant 文本提交到 session 历史. 失败或取消的 partial output 不进入下一轮上下文.
- `cancel()` 取消活动 Call, 停止后续 delta 并抑制 completed/failed callback. `close()` 同时封闭 callback gate; listener 可在 completed/failed callback 中同步启动下一轮或关闭 session.

## 固定上限

| 对象 | 上限 |
|---|---:|
| 消息文本 + response schema | 256 KiB UTF-8 |
| JSON request body | 512 KiB |
| 输出文本 | 64 KiB UTF-8 |
| 单个 SSE event | 1 MiB |
| SSE stream 总量 | 32 MiB |
| JSON fallback response | 8 MiB |
| 非 2xx error body 读取量 | 64 KiB + 1 byte |
| 认证凭据 | 8 KiB visible ASCII |

超过任何响应或输出上限均返回 `RESPONSE_TOO_LARGE`; 不向 listener 交付导致越界的 delta.

## 错误分类

`OnlineAiFailureException` 只携带稳定 reason 与可选 HTTP status, 且不保存 cause.

| reason | 来源 |
|---|---|
| `INVALID_REQUEST` | 本地请求, modelId 或 schema 映射失败 |
| `CREDENTIAL_UNAVAILABLE` | 凭据缺失, 不可解密或不符合 header 约束 |
| `PROFILE_CHANGED` | session 选择后 profile 已变化 |
| `AUTHENTICATION_FAILED` / `PERMISSION_DENIED` | HTTP 401 / 403 |
| `RATE_LIMITED` | HTTP 429 |
| `REDIRECT_REFUSED` | HTTP 3xx |
| `REQUEST_REJECTED` | 其他 HTTP 4xx |
| `SERVICE_UNAVAILABLE` | HTTP 5xx |
| `METERED_NETWORK_DISALLOWED` | 当前网络由 Android 判定为 metered, 且用户未显式开启 |
| `TIMED_OUT` / `TLS_FAILED` / `NETWORK_UNAVAILABLE` | 固定化后的 transport failure |
| `INVALID_RESPONSE` / `PROVIDER_ERROR` / `RESPONSE_TOO_LARGE` | content type, JSON/SSE, provider event 或边界校验失败 |

## 验证

离线回归命令:

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
```

测试使用无网络的 fake `Call.Factory`, 覆盖六种 profile 模板, schema 2 设置状态与默认档案删除, 统一目录默认值优先级, 每轮凭据读取前的网络门禁, 有界连接测试, 三个协议的 request mapping, one-shot body 清零, SSE/JSON response parser, partial/cumulative usage, persistent history, callback 重入, cancellation, redirect refusal, HTTP/provider/network error redaction 及全部 byte limit. 真机 endpoint 验证单独保留在 Roadmap, 不使用个人或生产凭据进入自动化测试.
