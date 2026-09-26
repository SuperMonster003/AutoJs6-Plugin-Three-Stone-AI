# 在线模型目录更新

预置模型采用公开 JSON 目录, 不依赖 APK 发版更新. 目录用于在线配置编辑器的候选项, 不代表账户可用性或各模型协议能力已完成在线验收.

## 数据流

1. 默认分支保存采集器, 筛选策略和 app/src/main/assets/online-models.json 内置快照.
2. Online model catalog GitHub Actions 每天 UTC 03:17 检查公开来源, 支持手动运行. 相关采集器, 策略或快照推送到默认分支后也会触发.
3. 模型内容有变化时, CI 将 online-models.json 发布到独立的 model-catalog 数据分支. 首次发布创建不继承代码历史的分支, 后续只做快进推送.
4. App 打开在线 AI 设置时先读取有效缓存, 没有缓存则使用 APK 内置快照. 自动更新默认开启, 最近成功检查超过 24 小时才后台联网; 可以关闭自动更新或手动刷新.
5. 下载结果完整校验后才原子替换本地缓存. 无网络, 禁止计费网络, 请求失败或损坏内容均保留现有列表. 网络失败后自动检查退避 1 小时, 手动刷新可绕过节流.

[公开目录地址](https://raw.githubusercontent.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/model-catalog/online-models.json).

数据分支不合并回代码分支, 不执行 Android 构建工作流, 不增加代码分支的 VERSION_BUILD. 发布通过独立 Git index 创建提交, 不切换工作目录. 采集任务仅有仓库读取权限, 发布任务才具有 contents: write.

## JSON 契约

- 顶层字段: schemaVersion (当前为 1), revision (正整数), updatedAtEpochMillis (正整数), providers (对象).
- providers 必须包含且仅包含 openai, anthropic, gemini, deepseek, openrouter. 每项包含 defaultModelId 和 models 字符串数组.
- 每家 1..128 个唯一 ID. 默认 ID 必须在数组中. ID 仅包含 ASCII 字母, 数字及 ._:/-, 首字符为字母或数字, 最长 256 字符.
- 完整文档不超过 256 KiB. App 和 CI 拒绝未知字段, 未知 schema, 重复字段, 错误类型和尾部垃圾.
- 仅模型内容变化时 revision 加 1; 相同内容保留原字节, 版本和时间, 不产生每日空提交.
- updatedAtEpochMillis 表示内容更新时间, 不表示所有厂商最近访问成功. 各厂商本轮采集状态在 CI report artifact 和 Actions 摘要中查看.
- OpenAI-compatible 由 App 合并四家直连列表并去重, 默认候选继承 OpenAI. 单配置仍最多选择 32 个模型, 候选目录可多于 32 项.
- 默认候选与显示排序分离. 原默认仍有效时保留, 失效时按维护策略选择; 发现新型号不会直接把它设为默认.
- JSON 无法改变 API 地址, 凭据, 请求参数, 工具能力, 图片开关或用户默认目标.

## 无密钥来源与分组

| 提供方 | 来源与筛选 |
| --- | --- |
| OpenAI | 从[模型目录](https://developers.openai.com/api/docs/models/all.md)发现详情页, 验证实际 Model ID, 文本输出, Chat Completions 和 streaming, 结合[退役说明](https://developers.openai.com/api/docs/deprecations.md)排除明确停用项 |
| Anthropic | 从[模型对照表](https://platform.claude.com/docs/en/models/overview.md)提取原生 ID/alias, 结合[生命周期表](https://platform.claude.com/docs/en/about-claude/model-deprecations.md)的 Active 项, 排除受限型号 |
| Gemini | 从[官方模型表](https://ai.google.dev/gemini-api/docs/models.md.txt)的 Endpoint 列提取并验证文本输出, 排除旧用户限定和非聊天章节; 最早可能退役日不能当作实际停用日 |
| DeepSeek | 解析[官方模型规格](https://api-docs.deepseek.com/quick_start/pricing/)的 MODEL 表格行, 不从脚注或历史示例猜 ID |
| OpenRouter | 使用[公开 Models API](https://openrouter.ai/api/v1/models)的真实路由 ID 和模态信息, 排除媒体生成, batch 和已过期项 |

OpenRouter 扩充 OpenAI, Anthropic, Google, DeepSeek, xAI, Meta, Mistral, Alibaba/Qwen, Moonshot/Kimi, Z.ai/GLM, MiniMax, ByteDance/Seed, Tencent, Baidu, Cohere, Amazon, Microsoft, NVIDIA, AI21, IBM 和 Perplexity 的候选范围. 实际只收录公开接口中存在的文本模型; 某厂商没有可用项时不生成虚构 ID. 每个厂商限制为少量当前型号, 防止整个目录被单一厂商占满.

模型选择器按厂商/系列分组, 显示具体模型 ID 和各组数量, 支持展开/收起. 含已选模型的组默认展开, 未知命名空间保留原名而不丢弃. 聚合平台的路由 ID 与原生 ID 不混用, 不通过去掉前缀来猜直连型号.

筛选策略位于 .github/model-catalog-policy.json, 负责默认候选, 特殊排除和数量边界, 不需要手动填写每个新型号. 文档结构变化仍可能需要修复采集器, 不能承诺永远零维护.

各厂商独立校验. 超时, 格式变化, 空列表或异常锐减会保留该厂商上次目录. 部分失败时可以发布其他厂商的有效更新, 工作流最后仍明确失败并保留报告; 全部失败不生成新版本. 采集器仅执行公开 GET, 无厂商密钥, 无推理调用.

## App 缓存与编辑行为

- 仅在线 AI 设置页面触发检查; Application 初始化, Wake 激活, Provider 查询和本地生成不触发目录请求.
- 独立无凭据 HTTPS 客户端, 固定项目地址, 禁止重定向, 限制超时和响应体, 遵守计费网络设置.
- ETag 与已校验 JSON 一起保存; 304 仅在对应缓存有效时接受. 损坏缓存或过旧缓存不会发送遗留 ETag.
- 缓存与用户配置文件分开, 使用私有目录和原子写入. 网络期间不持文件锁; 发布前重新核对版本, 晚到旧响应不能覆盖新版本.
- APK 内置快照升级时忽略版本更低或同版本冲突的缓存. 远程版本也不能低于已生效版本或在相同 revision 下改变内容.
- Activity 销毁时取消请求, 完成回调不操作已销毁界面.
- 每次打开选择器固定目录快照, 后台更新不重置未保存勾选或输入. 下次打开时显示新目录.
- 已保存的模型列表, 默认模型, 凭据和图片开关不迁移. 被移出的旧 ID 仍显示为自定义项, 用户可显式删除或替换.

## 启用与维护

代码推送至默认分支后, Online model catalog 工作流会自动采集并首次发布; 也可在 Actions 手动运行. 默认 GITHUB_TOKEN 即可, 不需要 PAT 或厂商密钥. 仓库规则需允许此工作流写入 model-catalog 分支.

本地确定性检查:

    py -m unittest discover -s .python -p 'test_model_catalog*.py'
    py .python/update_model_catalog.py --validate-only app/src/main/assets/online-models.json

只读采集示例 (结果写入指定临时目录, 不发布):

    py .python/update_model_catalog.py --previous app/src/main/assets/online-models.json --output C:/temp/stone-online-models.json --report C:/temp/stone-online-models-report.json

修复已发布目录时生成新的 revision, 不回退版本. APK 发版时可将审查后的远程快照同步为内置兜底, 日常更新不依赖此操作. CI 以已发布目录和内置快照中 revision 较高者为采集基准; 如果内置快照领先, 即使本轮来源内容不变也会同步到数据分支. 两者同版本但内容冲突时停止发布, 要求先修正版本号.

GitHub 定时任务可能排队延迟, 长期不活跃的公开仓库可能停用定时工作流. 通过 Actions 状态和通知关注失败, App 继续使用缓存. 见 [GitHub schedule 说明](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#schedule).

## 协议边界

目录更新不会自动完成新模型协议适配. GPT-6 的部分 Chat Completions 工具调用仍需要额外接口/参数支持; DeepSeek 思考工具续轮仍需 reasoning_content 回传; 新代 Claude 可能拒绝非默认采样参数. 相关依据见 [OpenAI 模型指南](https://developers.openai.com/api/docs/guides/latest-model), [DeepSeek Thinking Mode](https://api-docs.deepseek.com/guides/thinking_mode/), [Claude Opus 5.5](https://platform.claude.com/docs/en/models/opus-5-5/overview).

公开目录查询成功不等于使用用户账户完成逐模型推理验证.
