# 在线模型预置目录

最近核对日期: 2026-09-26.

`OnlineAiModelPresetCatalog` 为在线配置编辑器提供模型 ID 快捷选项. 它不是账户可用模型查询结果, 也不代表每个模型的工具调用, 图片输入或自定义采样参数均已完成适配和真机验收.

## 当前选择与来源

| 提供方 | 本次更新 | 官方来源 |
| --- | --- | --- |
| OpenAI | 加入 `gpt-6-astra`, `gpt-6-sol`, `gpt-6-luna`, `gpt-5.6-terra`, `gpt-5.6-luna`, `gpt-5.5`, `gpt-5.4-mini`, `gpt-5.4-nano`; 保留 `gpt-5.6-sol`, `gpt-5.4`, `gpt-4.1` | [模型目录](https://developers.openai.com/api/docs/models/all), [GPT-5.6 Sol](https://developers.openai.com/api/docs/models/gpt-5.6-sol), [o4-mini](https://developers.openai.com/api/docs/models/o4-mini) |
| Anthropic | 优先列出 `claude-opus-5-5`, `claude-fable-5-1`, `claude-sonnet-5`, `claude-haiku-4-5`; 保留仍 Active 的 Opus 5, Fable 5, Opus 4.8 和 Sonnet 4.6 | [模型目录](https://platform.claude.com/docs/en/models/overview), [退役表](https://platform.claude.com/docs/en/about-claude/model-deprecations) |
| Gemini | 加入稳定版 `gemini-3.8-flash` 和 `gemini-3.5-flash-lite`; 保留仍在列的 `gemini-3.1-pro-preview` 和 `gemini-3-flash-preview` | [模型目录](https://ai.google.dev/gemini-api/docs/models), [退役安排](https://ai.google.dev/gemini-api/docs/deprecations) |
| DeepSeek | 使用当前推荐的 `deepseek-flash` (V4.1 Flash) 与 `deepseek-v4-pro` | [接入说明](https://api-docs.deepseek.com/), [当前模型规格](https://api-docs.deepseek.com/quick_start/pricing/), [更新日志](https://api-docs.deepseek.com/updates/) |
| OpenRouter | 按其公开目录单独核实上述厂商的路由 ID, 不从原生 ID 自动拼接 | [公开 Models API](https://openrouter.ai/api/v1/models) |

OpenRouter 当前补充的路由 ID:

- `openai/gpt-6-astra`, `openai/gpt-6-sol`, `openai/gpt-6-luna`.
- `anthropic/claude-opus-5.5`, `anthropic/claude-fable-5.1`, `anthropic/claude-sonnet-5`, `anthropic/claude-haiku-4.5`.
- `google/gemini-3.8-flash`, `google/gemini-3.5-flash-lite`.
- `deepseek/deepseek-v4.1-flash`, `deepseek/deepseek-v4-pro-0813`.

保留原有 OpenRouter GPT-5.6 Sol, Opus 5 和 Gemini 3.1 Pro Preview 路由. Anthropic 在 OpenRouter 的版本号使用点号, 原生 API 使用连字符. OpenRouter 的 `deepseek/deepseek-v4-pro` 当前指向 0423 版本, 本次使用目录中标为 GA 的 0813 版本.

## 移出的快捷选项

- OpenAI `gpt-5.6` 是 `gpt-5.6-sol` 的别名, 移除重复入口; `o4-mini` 的快照已标记 Deprecated, 当前轻量模型由 GPT-6 Luna 和 GPT-5.6 Luna 等覆盖.
- Gemini 2.5 Pro/Flash 当前限制为既有使用者访问, 因此移出面向新配置的预置. 这不表示这两个模型已经停用.
- DeepSeek 直连 `deepseek-chat` 与 `deepseek-reasoner` 已于 2026-07-24 停用, 见[官方公告](https://api-docs.deepseek.com/news/news260424/). OpenRouter 的 `deepseek/deepseek-chat` 仍是其旧 V3 路由, 本次改列 V4 系列, 不将直连停用状态套用于 OpenRouter.

## 配置与协议边界

- 首项会用于新建配置的默认选择. OpenAI 及 OpenRouter 保留原先的 GPT-5.6 Sol 首项, 避免仅更新目录就将新配置默认切到有工具协议限制的 GPT-6. Anthropic 改为 Opus 5.5, Gemini 改为 3.8 Flash, DeepSeek 改为当前 Flash.
- 已保存的模型列表, 默认模型和图片开关不会随预置更新而迁移. 编辑器会将不再属于预置的旧 ID 放入自定义输入区域, 继续允许显式编辑或删除. 旧 DeepSeek 直连配置需由用户选择新模型.
- OpenAI-compatible 继续合并四家直连列表并去重, 不混入 OpenRouter 的命名空间. 当前合并 25 项, 未超过单配置 32 个模型的限制.
- GPT-6 普通聊天支持 Chat Completions, 但 Astra 的工具调用要求 Responses API; Sol/Luna 在 Chat Completions 中仅在 `reasoning_effort: "none"` 时支持工具. 当前插件没有该参数配置或 Responses 适配, 因此这些预置不能视为原生 Agent 工具可用认证. 见 [OpenAI 模型指南](https://developers.openai.com/api/docs/guides/latest-model).
- DeepSeek 新模型默认开启 thinking, 工具续轮要求回传 `reasoning_content`. 当前 OpenAI-compatible collector 尚未保留该字段, 本次目录更新未改变此行为, 因此 DeepSeek 思考模式的工具续轮仍需单独适配. 见 [DeepSeek Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode/).
- Opus 5.5/Fable 5.1 使用始终开启的 adaptive thinking, 新代 Claude 不接受非默认采样参数. 默认聊天设置不发送这些参数, 本次也未增加 `thinking` 或强制 `tool_choice`. 如需自定义采样或其他能力, 应按具体模型另行验收. 见 [Opus 5.5](https://platform.claude.com/docs/en/models/opus-5-5/overview) 和上述 Anthropic 退役表.

此次核对仅访问官方公开文档和无鉴权模型目录, 未使用用户 API Key, 未执行收费推理或宣称逐模型在线验收通过.
