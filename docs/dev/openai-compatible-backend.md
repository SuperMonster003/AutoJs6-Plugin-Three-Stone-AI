# OpenAI Compatible Backend

本文记录 P1 插件内部在线执行层的实际契约. 它不改变 AI Provider V1 的本地模型公开范围; 在线 target 要等后续设置页, 目标选择器及 V2 统一目标协议接入后才会成为宿主脚本可选目标.

## 请求映射

一个非敏感 `OnlineAiProfile` 提供 canonical UUID, displayName, HTTPS baseUrl 与 modelId. 凭据只由插件的 Keystore 仓库按 profileId 提供, 不进入 profile JSON, target catalog 或 Binder 对象.

| 通用字段 | OpenAI Compatible 请求 |
|---|---|
| system/user/assistant 文本消息 | `messages[].role` + 字符串 `content` |
| modelId | `model` |
| maximumOutputTokens | `max_tokens` |
| temperature | `temperature` |
| topK | `top_k` |
| topP | `top_p` |
| reportUsage | `stream_options.include_usage=true` |
| responseJsonSchema | strict `response_format.type=json_schema` |

baseUrl 未以 `/chat/completions` 结束时追加该 endpoint; 已包含 endpoint 时不重复追加. 请求始终设置 `stream=true` 并使用一次性 JSON RequestBody, 防止凭据和 prompt 被 follow-up 自动重放.

## 网络与凭据边界

- Profile 只接受 HTTPS, 禁止 user-info, query 与 fragment. 每个 target 只声明规范化后的 HTTPS origin.
- OkHttp client 禁止 HTTP/HTTPS redirect, connection retry, authenticator, proxy authenticator, cookie, cache, application interceptor 与 network interceptor. EventListener 固定为 `NONE`.
- Bearer header 只在同步凭据作用域内构造. Profile 元数据与凭据在同一锁定快照点配对; 复制后的凭据 buffer 在 Call 成功或异常后清零, 网络期间不持有 profile 或 credential 文件锁.
- 请求 URL, Authorization, prompt, response body 与底层异常均不记录. 非 2xx body 只做有界丢弃, 不进入异常. HTTP 200 内的 provider error 也只映射为固定错误.
- 本地失败不转在线, 在线失败不转本地. Client 不实现任何隐式 fallback.

连接超时为 30 秒, 写超时为 60 秒. 流式读与 Call 不设置内部总超时; 上层会话拥有截止时间并通过 `Call.cancel()` 精确终止活动请求.

## 响应与会话语义

成功响应只接受 `text/event-stream`, `application/json` 或 `application/*+json`.

- SSE 支持 LF, CRLF, lone CR, BOM, comment, multiline data 及 EOF 前最后一个未终止 event. 正常完成必须收到 `[DONE]`.
- JSON fallback 从 `choices[0].message.content` 或 `choices[0].text` 提取一次文本 delta.
- 文本支持字符串或 OpenAI text/refusal part 数组. Reasoning 与 tools 当前不解析也不声明 capability.
- Usage 只接受非负整数 `prompt_tokens`/`completion_tokens` 或 `input_tokens`/`output_tokens`; 若存在 `total_tokens`, 必须严格等于两者之和.
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
| Bearer credential | 8 KiB visible ASCII |

超过任何响应或输出上限均返回 `RESPONSE_TOO_LARGE`; 不向 listener 交付导致越界的 delta.

## 错误分类

`OpenAiCompatibleFailureException` 只携带稳定 reason 与可选 HTTP status, 且不保存 cause.

| reason | 来源 |
|---|---|
| `INVALID_REQUEST` | 本地请求或 schema 映射失败 |
| `CREDENTIAL_UNAVAILABLE` | 凭据缺失, 不可解密或不符合 header 约束 |
| `PROFILE_CHANGED` | session 选择后 profile 已变化 |
| `AUTHENTICATION_FAILED` / `PERMISSION_DENIED` | HTTP 401 / 403 |
| `RATE_LIMITED` | HTTP 429 |
| `REDIRECT_REFUSED` | HTTP 3xx |
| `REQUEST_REJECTED` | 其他 HTTP 4xx |
| `SERVICE_UNAVAILABLE` | HTTP 5xx |
| `TIMED_OUT` / `TLS_FAILED` / `NETWORK_UNAVAILABLE` | 固定化后的 transport failure |
| `INVALID_RESPONSE` / `PROVIDER_ERROR` / `RESPONSE_TOO_LARGE` | content type, JSON/SSE, provider event 或边界校验失败 |

## 验证

离线回归命令:

```powershell
.\gradlew.bat --offline :app:testDebugUnitTest
```

测试使用无网络的 fake `Call.Factory`, 覆盖 request mapping, one-shot body 清零, SSE parser, JSON fallback, usage, persistent history, callback 重入, cancellation, redirect refusal, HTTP/provider/network error redaction 及全部 byte limit. 真机 endpoint 验证单独保留在 ROADMAP, 不使用个人或生产凭据进入自动化测试.
