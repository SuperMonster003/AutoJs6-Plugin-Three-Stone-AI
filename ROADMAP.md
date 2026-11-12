# Roadmap: 多轮对话上下文治理

> 目标一句话: 把 "聊天记录" 与 "发给模型的上下文" 彻底解耦; 完整记录继续服务 UI, 真正发给模型的上下文由统一的 Context Compiler 按 token 预算编译, 并以水位驱动会话轮换, 使单轮输入恒定有界, 累计费用从近似 O(n²) 回落为 O(n).
>
> 制定日期: 2026-08-31.参考: 与 Codex 的方案讨论 (已逐项核实其现状描述), 结合本仓库实际结构调整.所有条目均可独立勾选,独立合入; 按 ID 引用 (如 "P1-3").
>
> 2026-10-17 追加第 10 节 "语音能力接入 (3-Macaw Speech)": 决策 D6-D38, 阶段 P5 (1.4.0) 与 P6 (1.5.0), 与第 1-9 节的上下文治理相互独立.

## 1. 背景与已核实现状

膨胀机理: 启动器聊天在 24~32 轮之间锯齿式增长 (重建装入最近 24 轮, 至 32 轮再重建); 在线会话每轮全量重发内部 `conversation`; 本地会话 KV cache 持续增长; Binder 持久会话则完全没有轮换上限.

| # | 现状事实 (已核实) | 位置 |
|---|---|---|
| S1 | 复用同一 backend 至 32 轮 (`MAXIMUM_BACKEND_TURNS`), 重建装最近 24 轮 / 192 KiB (`historyForFreshBackend`), 重建后轮计数从 24 起算, 形成 24→32 锯齿 | `app/src/main/java/.../threestoneai/ChatConversation.kt` |
| S2 | 复用/重建判定仅依据轮数与 target 一致性 (`completedTurnsOnBackend`) | `.../threestoneai/ChatActivity.kt` (`startBackendTurn`) |
| S3 | 在线会话内部持有完整 `conversation`, 每轮 `conversation + prompt` 全量重发; `streamNext` 禁止携带 history; 传输护栏 256 KiB (`ThreeStoneAiPlugin.MAXIMUM_CONTEXT_BYTES`) | `.../backend/OnlineAiHttpExecution.kt` (`OnlineAiSession.runTurn`) |
| S4 | 本地会话 `Conversation` KV cache 随轮增长; `getTokenCount()` 可取全量真值; 后续轮 `inputTokens` 为增量口径, 与在线全量口径语义不一致 | `.../backend/LiteRtLocalSession.kt` (`collectStatistics` 注释) |
| S5 | Binder 持久会话无轮换上限, 插件不留存会话文本; 续轮协议限定恰好 1 条 USER 消息且 `FixedConfiguration` 不变; 错误码定义在宿主 API 库 (`org.autojs.plugin.ai.*`), 插件无法自行新增 | `.../provider/RemoteThreeStoneAiSession.kt` |
| S6 | 限额模型只有字节维度: `AiTargetLimits.maximumContextBytes`; 本地模型目录同样仅有 `maximumContextBytes` | `.../backend/AiBackend.kt`, `.../model/ModelCatalog.kt` |
| S7 | 在线 usage 实报 input/output tokens 且随 assistant 消息持久化 (`ChatMessageUsage`) - token 估算的校准数据现成 | `.../threestoneai/ChatConversation.kt` |
| S8 | 历史存储上限 256 条 / 1 MiB / 24 MiB, codec v3 fail-closed; `ConversationHistoryStore` 每次 upsert 全量读写整个历史文件 (写放大) | `.../threestoneai/ConversationHistory.kt`, `ConversationHistoryStore.kt` |
| S9 | 启动器聊天当前不发送 SYSTEM 消息; Binder 调用方可在首轮携带 SYSTEM, 各协议适配器均已支持 | `.../provider/PromptPlanner.kt`, 各 `*Protocol.kt` |

字节上限 (S3, S6) 是传输安全护栏, 不是上下文治理: 192 KiB 中文场景约折合 4 万+ tokens, 且中英文/代码的字节-token 比差异巨大.

## 2. 目标与非目标

目标:

- G1: 单轮模型输入 token 恒定有界 (默认 16K 预算), 与会话总长无关.
- G2: 启动器聊天与 Binder 持久会话共用同一套上下文编译与轮换机制, 不再有旁路.
- G3: 本地会话 KV cache 与在线累计费用均受水位控制, 重建后明显回落.
- G4: 旧对话信息通过结构化摘要检查点保留, 可验证,可重建,失败可退化, 不阻塞聊天.
- G5: 每阶段独立可合入,可验证, 保持既有 "纯策略对象 + JVM 单测 + 真机冒烟记录" 的工程惯例.

非目标 (本 Roadmap 不做):

- 向量数据库 / embedding 语义召回 (仅在 P4 末尾留评估位).
- 跨会话全局用户记忆 (本方案的记忆均为会话内).
- 宿主 API 库 (`org.autojs.plugin.ai.*`) 的协议变更 (含新错误码); 全部改造限定在插件进程内.
- 依赖 provider prompt cache 或超长上下文模型来替代逻辑上下文治理.

## 3. 已定决策

| ID | 决策 | 说明 |
|---|---|---|
| D1 | 滚动摘要跟随当前会话 target 执行 | 本地会话离线可用; 无新增凭据/网络策略边界.摘要调用使用一次性 backend session, 不与聊天会话争用 |
| D2 | Binder 持久会话采用插件内透明压缩 | 协议零改动, 脚本无感, `sessionId` 不变; 仅更新 `plugin_instruction` 行为说明.首版透明压缩只裁剪原文,不做隐藏 LLM 摘要调用 (避免用调用方额度做不可见请求), LLM 摘要仅用于启动器聊天 |
| D3 | 默认输入 token 预算 16K, 按 target 可覆盖 | 见第 5 节参数基线 |
| D4 | 持久化沿用现有二进制 codec, 直接升版本,fail-closed,不留迁移分支 | 项目未发布, 与会话历史 v3 的既有做法一致 |
| D5 | 摘要生成必须后台化且失败可退化 | 当前网络环境 5xx/524 常见, 摘要失败保留旧检查点并退化为纯截断, 绝不阻塞或拖垮聊天主链路 |

D6 起为语音能力接入的决策, 见第 10 节.

## 4. 总体设计

分层上下文 (编译产物按此优先级装箱, 超预算按完整 turn 驱逐, 不拆散 user/assistant 配对):

| 层 | 内容 | 保留策略 |
|---|---|---|
| L0 固定指令 | Binder 首轮 SYSTEM 消息; 启动器聊天暂为空槽位 | 必须原样保留 |
| L1 结构化工作记忆 | 目标 / 约束 / 已确认决定 / 待解决 / 偏好, 带来源消息 ID 与状态 | 固定 token 上限 (P2) |
| L2 摘要检查点 | 已移出原文窗口的旧 turn 的分段摘要 | 固定 token 上限 (P2) |
| Recall 按需历史 | L3 之外与当前查询匹配的完整旧 turn chunk | 固定 token 上限, FTS 失败时直接省略 (P4) |
| L3 最近原文 | 最近若干完整成功 turn | 按剩余预算装箱, 至少保底 2 turn |
| 当前消息 | 本轮用户输入 | 必须完整保留 |

统一入口 (新增组件均为无 Android 依赖的纯策略对象, 便于 JVM 单测):

```text
ChatActivity / RemoteThreeStoneAiSession
            │
            ▼
ConversationContextCoordinator
    ├── ContextTokenEstimator    (P0: 估算 + 按 target 校准)
    ├── ContextBudgetCalculator  (P1: 预算与水位)
    ├── ContextAssembler         (P1: compileContext 分层装箱)
    ├── ContextAccounting        (P1: 实际 usage 优先的会话 token 记账)
    ├── SummaryCheckpointer      (P2: 检查点生成/验证/失效)
    └── ConversationRecallStore  (P4: FTS4 旁路索引与按需召回)
            │
            ▼
      AiBackendSession (stream / streamNext / 插件内 streamRebuilt)
```

关键机制:

- 轮换判定从 "轮数" 改为 "上下文水位": 记账值达到硬水位 → 下一轮前用编译后的精简上下文重建.在线 backend 关闭旧 session 后新建; LiteRT 保留已初始化 Engine, 原位替换已完成的 `Conversation`.`MAXIMUM_BACKEND_TURNS` 保留为泄漏保护 (提高到 64), 不再是主策略.
- 台阶式驱逐: 达到水位时一次驱逐一批最旧 turn (回落到压缩目标), 而不是每轮滑动 1 turn - 两次重建之间请求前缀保持稳定, 减少重建频率, 也利于 provider 侧 prompt cache.
- 记账口径归一: 在线用实报 `inputTokens + outputTokens` 近似当前上下文全量; 本地经 `getTokenCount()` 取全量真值; 均不可得时用估算兜底.
- 摘要是派生索引不是唯一真相: 原文在存储保留期内不因摘要而删除; 检查点带来源 ID 与哈希, 可随时从原文重建.

## 5. 默认参数基线 (全部集中在 `ContextPolicy`, 可调; 数值为初始值, P0 校准后可修订)

| 参数 | 默认值 | 说明 |
|---|---|---|
| 输入预算 | 16,384 tokens | D3; 设置页可选 8K / 16K / 32K / 自定义, 按 target 覆盖 |
| 输出预留 | `maximumOutputTokens` 设置值, 未设时 4,096 | 参与容量校验, 不参与预算扣减时置 0 的讨论见 P1-2 |
| 安全余量 | 预算的 8% | 吸收估算误差 |
| 软水位 | 预算的 65% | 触发后台摘要 (P2) |
| 硬水位 | 预算的 80% | 下一轮前必须重建 |
| 绝对保护 | 预算的 90% | 丢弃 L1/L2, 仅保最近原文 + 当前消息紧急重建 |
| 压缩目标 | 重建后 ≤ 预算的 45% | 与硬水位形成高低水位差, 避免每轮重建 |
| 最近原文保底 | 2 个完整 turn | 即使超水位也保留 |
| 估算初始系数 | 0.40 token/byte + 每消息 4 tokens 角色开销 | 对中英文/代码均偏保守 (宁可高估) |
| 校准系数带 | [0.15, 0.60] token/byte | 按 target EMA 校准, 夹在带内 |
| 摘要层上限 | 2,048 tokens | P2 |
| 工作记忆上限 | 1,024 tokens | P2 |
| Binder 会话转写留存上限 | 512 KiB / 会话 | P3, 内存护栏 |

## 6. 阶段任务

### P0 - 观测与校准 (先能看见, 再动刀)

- [x] **P0-1** 新增 `ContextTokenEstimator` 纯策略对象: 按初始系数估算 `GenerationMessage` 列表与单条文本的 token 数, 含每消息角色开销.
  - 验收: JVM 单测覆盖中文 / 英文 / 代码 / 空消息 / 多 part; 对同一文本估算值 ≥ 实际值的场景在校准前可接受 (保守方向正确).
- [x] **P0-2** 校准机制: 每次成功轮结束后, 用实报 usage (在线 `inputTokens`; 本地按 S4 的增量口径换算) 更新该 target 的 EMA 系数并持久化 (SharedPreferences, 跟随 `ChatUiSettingsStore` 模式); 系数夹在校准带内.
  - 验收: 单测模拟连续 usage 序列, 系数收敛且不越带; 无 usage 时系数不变.
- [x] **P0-3** 观测日志: 每轮以 debug 级输出 估算输入 tokens / 实际 usage / 当前记账值 / backend epoch / 是否重建; 启动器聊天 usage 行 (`showGenerationUsage`) 增加会话累计输入展示 (可选开关).
  - 验收: logcat 可直接观察锯齿曲线, 作为 P1 前后对比基线; 真机记录一段 24+ 轮会话的数据存入 `docs/dev/`.
  - 状态: 2026-08-31 已在 G8441 / Android 9 / PoloAPI `claude-opus-4-8` 完成 40 轮在线基线, 捕获第 33 轮 epoch 重建锯齿及第 38 轮 provider usage 断层; 数据与结论见 [`docs/dev/context-accounting-baseline.md`](docs/dev/context-accounting-baseline.md).

### P1 - token 预算装配与水位轮换 (止血: 输入从锯齿增长变为恒定有界)

- [x] **P1-1** `AiTargetLimits` 增加 `maximumContextTokens: Int?` (插件内部字段, 不经 `TargetPager` 透出 Binder); `OnlineAiBackend` 目录 codec 版本 +1 (fail-closed); 本地 target 在 P1 阶段先走全局默认预算, P3 真机校准后已与 LiteRT `EngineConfig.maxNumTokens` 同源声明 4,096 tokens.
  - 验收: 既有 `OnlineAiBackendTest` / 目录编解码测试更新通过.
- [x] **P1-2** 新增 `ContextBudgetCalculator`: `有效输入预算 = min(target.maximumContextTokens - 输出预留 - 安全余量, 应用预算设置)`; 上下文窗口未知的在线 target 只受应用预算约束 (预算即成本上限, 不冒充容量推断).
  - 验收: 单测覆盖 有/无 target 上限,有/无输出设置,极小预算钳制 (保底 2 turn + 当前消息).
- [x] **P1-3** `ChatConversationPolicy` 新增 `compileContext(transcript, target, prompt, policy): CompiledContext` - 复用现有成功 turn 提取, 按 token 从尾部装箱完整 turn; 返回 `messages / estimatedInputTokens / coveredMessageIds / requiresSessionRebuild`; 当前消息永远完整保留 (输入框 16K 字符硬限已存在, 单条消息可能占满预算属预期行为, 记录警告日志即可); 现有 24 轮 / 192 KiB 规则退役为传输护栏.
  - 验收: 单测覆盖 长短混合 turn,单条超长消息,全部失败轮被排除,装箱不拆散配对,时序保持.
- [x] **P1-4** 新增 `ContextAccounting`: 会话内维护当前上下文 token 记账 - 在线每轮以实报 `inputTokens + outputTokens` 刷新, 本地以 `getTokenCount()` 全量真值刷新 (为此给 `GenerationStatistics` 增加内部可空字段 `contextTokensAfterTurn`, LiteRT 填真值, 在线填 input+output, 不透出 Binder), 均不可得时以估算累加兜底; `shouldRotateBackend` 改为 `记账值 ≥ 硬水位 || 轮数 ≥ 64 (泄漏保护)`.
  - 验收: 单测覆盖三种口径的记账与轮换触发; S4 的口径差异有专门测试固定语义.
- [x] **P1-5** `ChatActivity` 集成: `startBackendTurn` 复用判定改为 (target 一致 && 记账未达硬水位 && 未发生编辑/重生成/target 切换); 重建路径改用 `compileContext` 且回落至压缩目标 (台阶式驱逐); `completedTurnsOnBackend` 让位于记账, 仅作泄漏保护计数.
  - 验收: 现有编辑 / 重新生成 / 停止 / target 切换流程回归通过; 重建后记账值 ≤ 45% 预算.
- [x] **P1-6** 设置项: "上下文 token 预算" (8K / 16K / 32K / 自定义, 默认 16K), 存取跟随 `ChatUiSettings` / `ChatUiSettingsStore` 既有模式.
  - 验收: `ChatUiSettingsTest` 扩展通过; 修改预算即刻影响下一轮编译.
- [x] **P1-7** 真机冒烟并记录 `docs/dev/context-budget-smoke.md`: 同一在线 target 连续 40+ 轮, usage 显示单轮输入 tokens 有界且重建后明显回落; 本地 target 长会话延迟不再单调上升.
  - 验收: 文档含前后对比数据 (对照 P0-3 基线).
  - 状态: 2026-08-31 已在 G8441 上分别完成 40 轮在线与 40 轮 LiteRT 长会话.两条通道均由精确记账触发水位轮换, 裁剪仅保留完整 turn, 重建后回落至 45% 目标以内; 设置已恢复为 16K / Unlimited.数据, provider usage 异常边界与 P0 对比见 [`docs/dev/context-budget-smoke.md`](docs/dev/context-budget-smoke.md).

> P1 已于 2026-08-31 完成: 由客户端历史增长造成的输入曲线从 O(n) 锯齿封顶为常数带, 累计费用回落为 O(n).已知边界是 provider 可在客户端请求之外注入或上报固定开销; 该开销只能在本轮 usage 返回后被发现, 但会在下一轮触发重建.超出预算的旧原文目前直接遗忘, 由 P2 补记忆.

### P2 - 摘要检查点与结构化工作记忆 (把遗忘变成压缩)

- [x] **P2-1** 数据模型: `ConversationContextState { coveredThroughMessageId, summarySegments[], workingMemory, schemaVersion }`; `SummarySegment` 带 `firstMessageId / lastMessageId / sourceHash`; `MemoryItem` 带 `sourceMessageIds / status (PROPOSED | CONFIRMED | REJECTED | SUPERSEDED)`; 随 `StoredConversation` 持久化, 会话历史 codec 升 v4 (fail-closed, 无迁移分支, D4).
  - 验收: codec 编解码单测; 旧 v3 文件按既有约定整体拒读.
- [x] **P2-2** 触发与范围: 软水位后台生成; 每次只摘要 "下次台阶驱逐将移出原文窗口的完整 turn 段" (上个检查点之后), 绝不重摘全会话, 也绝不摘要进行中/失败/停止轮.
  - 验收: 单测验证检查点链连续覆盖,范围不重叠不跳跃.
- [x] **P2-3** 摘要调用 (D1/D5): 用当前会话 target 新建一次性 backend session, 要求严格 JSON 输出 (target 具 `structuredJson` 能力时用 schema, 否则 prompt 约束 + 严格解析); 输入输出均设硬上限; 后台执行,与聊天互不阻塞, 失败重试 1 次后放弃并保留旧检查点, 聊天退化为纯截断继续可用.
  - 验收: 伪 backend 单测覆盖 成功 / 超限 / 解析失败 / 网络失败退化; 摘要调用不占用聊天的 backend session.
- [x] **P2-4** 本地验证器: 引用的 `sourceMessageIds` 必须落在覆盖范围内且真实存在; 字段长度 / 条数硬上限; 状态机规则 - 模型未经用户确认的建议只能是 PROPOSED, 不得直接产出 CONFIRMED 决定; 验证失败整段丢弃, 保留旧检查点.
  - 验收: 单测逐条覆盖拒绝分支 (越界来源 / 超长 / 非法状态跃迁).
- [x] **P2-5** 装配整合: `compileContext` 增加 L1 (工作记忆) / L2 (摘要段) 层, 按第 4 节优先级与各层上限装箱; 绝对保护水位时丢弃 L1/L2.
  - 验收: 单测覆盖各层上限,优先级,紧急降级路径.
- [x] **P2-6** 失效规则: 编辑 / 删除 / 重新生成使 "覆盖该消息及之后" 的检查点与相关记忆失效并异步重建; target 切换保留检查点 (纯文本, 跨 target 通用); 检查点变更即触发 backend 重建 (fingerprint 变化).
  - 验收: 单测覆盖 编辑早于 / 晚于 检查点边界,连续编辑,重建期间再次编辑.
- [x] **P2-7** 存储配套: `ConversationHistoryPolicy.normalized()` 裁剪最旧消息前, 确保其已被检查点覆盖 (未覆盖则先保留); 缓解 S8 写放大 - 首选对 `persistConversationNow` 节流合并 (流式增量期已有 UI 侧缓冲, 落盘可去抖), 不足时再评估拆分文件, 单独立项.
  - 验收: 单测验证 "未覆盖不裁剪"; 长会话下落盘频率可观测下降.
- [x] **P2-8** 真机冒烟并记录: 60+ 轮跨话题长会话, 验证 (a) 单轮输入仍有界; (b) 询问 30 轮前的已确认约束能被正确回忆 (命中工作记忆/摘要); (c) 断网时摘要失败聊天不受影响.

> P2 已于 2026-08-31 完成: 在线 target 真机连续完成 63 个可见轮次, 三个增量检查点连续覆盖消息 1-22; 第 61 轮仅携带最近两轮原文与 L1 工作记忆, 仍精确回忆首轮约束 `ORCHID-731`.断网测试中隐藏摘要两次失败后保留旧检查点, 恢复网络后的下一条可见消息正常完成.绝对保护水位的临时 L1/L2 丢弃会以独立 fingerprint 强制下一轮重建并恢复记忆.完整数据, provider 兼容性修正与环境恢复记录见 [`docs/dev/context-summary-smoke.md`](docs/dev/context-summary-smoke.md).

### P3 - Binder 持久会话透明压缩 (堵住旁路, D2)

- [x] **P3-1** 会话转写留存: `RemoteThreeStoneAiSession` 记录每轮 user prompt (来自 `PromptPlanner` 产物) 与完成输出 (来自 `StreamingOutputBuffer.snapshot()`), 仅成功轮入账; 首轮完整 history 与 SYSTEM 消息一并留存; 内存护栏 512 KiB, 超出即对最旧 turn 做台阶驱逐 (原文裁剪, 无 LLM 摘要, D2).
  - 验收: 单测覆盖 成功/失败/取消轮的入账边界,护栏驱逐.
- [x] **P3-2** 记账与透明重建: 复用 `ContextAccounting` (usage 已随 `GenerationStatistics` 可得); 达到硬水位后, 在下一次 `generateNext` 前于插件内部重建精简上下文 (SYSTEM 原样置顶 + 最近原文).在线 backend 关闭旧 session 后新建并调用 `stream()`; LiteRT 保留 Engine lease, 关闭旧 `Conversation` 后调用内部 `streamRebuilt()`; `sessionId`,`FixedConfiguration`,回调时序对脚本完全不变.
  - 验收: 伪 backend 单测 - 40+ 轮连续 `generateNext`, 断言脚本视角流式/完成/usage 事件序列与不重建时一致, 且底层 session 发生过重建,重建后输入有界.
- [x] **P3-3** 硬保底: 极端情况下 (单轮 prompt 加保底原文仍超绝对保护水位) 沿用现有 fail-closed 路径与既有错误码关闭会话, 不引入新协议错误码.
  - 验收: 单测覆盖该路径; 错误码不超出宿主 API 现有集合.
- [x] **P3-4** 文档: `plugin_instruction.md` 增补持久会话上下文行为说明 (透明压缩,护栏,硬保底), 按既有流程同步各语言资源; `docs/dev/` 增补设计要点.
  - 验收: `PluginInstructionCompatibilityTest` 通过.
- [x] **P3-5** 真机冒烟并记录: AutoJs6 脚本经 provider 连续 100 轮对话 (本地与在线 target 各一轮次), 无溢出,无会话中断; usage 曲线有界.

> P3 已于 2026-08-31 完成: AutoJs6 provider 持久会话在在线与 LiteRT target 上分别连续完成 100/100 轮, host 可见 `sessionId` 与回调协议全程不变.在线两次重建将输入从 12,054/13,032 回落到 7,562/7,275; 本地两次原位重建将 KV 记账从 2,976/3,011 回落到 1,852/1,702, 第 100 轮结束为 2,379/3,001.完整脚本结果, 重建边界, 压力诊断, 自动化回归与设备恢复记录见 [`docs/dev/binder-persistent-context-smoke.md`](docs/dev/binder-persistent-context-smoke.md).

### P4 - 可选优化 (已按需部署)

- [x] **P4-1** 前缀稳定性复核: 校验 OpenAI / Anthropic / Gemini 协议适配器在台阶驱逐间隔内保持字节级稳定请求前缀 (L0/L1/L2 置前); 解析各 provider cache usage 字段并观测命中率.
- [x] **P4-2** 上下文用量 UI: 会话页展示 `已用 / 预算` 与 L1 / L2 / Recall / L3 分层构成; 提供 "立即压缩" 手动动作, 不删除历史且不发起隐藏模型调用.
- [x] **P4-3** FTS 历史召回 (L3 之外的按需片段召回): AtomicFile 继续作为唯一真相, SQLite FTS4 作为可丢弃旁路索引; 按 2~4 turn 分块, 关键词 85% + 时序 15% 排序, 最多选择 4 个片段并计入 token 预算.
- [x] **P4-4** embedding 语义召回评估: 固定 15 主题 / 16 查询语料的关键词 Top-1 与 Recall@4 均为 100%, 语义改写专用查询 Recall@4 为 0%; 当前不引入 embedding.仅当至少 100 条真实查询审计的 Recall@4 低于 90%, 或语义漏召回对任务结果形成实质影响时重新立项.

> P4 已于 2026-08-31 完成: 三类在线协议的稳定前缀与 cache usage 解析已有自动化覆盖; API 36 模拟器上的本地 HTTPS provider 冒烟显示连续复用阶段 cache 命中率升至 82.73%, 台阶驱逐后输入从 278 回落到 188 tokens.旧 `1-6` 消息 chunk 被 FTS4 命中并实际装入 Recall 层, UI 显示 `Recall 130` 与总上下文 `324 / 768 tokens`.该本地 provider 用于端到端协议验证, 不代表任一厂商的生产缓存性能.完整数据, embedding 门槛与测试环境还原记录见 [`docs/dev/p4-context-optimization-smoke.md`](docs/dev/p4-context-optimization-smoke.md).

## 7. 阶段门槛

每阶段合入前须满足: (a) 该阶段全部单测通过且新增策略对象无 Android 依赖; (b) 真机冒烟记录落入 `docs/dev/` (含数据, 惯例同 `p3-host-public-api-smoke.md`); (c) 不改变宿主 API 面; (d) 失败路径显式测试 (摘要失败,网络失败,估算缺失).P0→P1→P2→P3 已依序完成, P4 已按需部署并通过独立阶段门槛.

## 8. 风险与回退

| 风险 | 缓解 |
|---|---|
| token 估算偏差导致 provider 侧容量溢出 | 保守初始系数 + 校准带 + 8% 余量; 溢出错误按现有失败呈现, 用户下调预算即可; 不自动重试以免双倍计费 |
| 摘要漂移 (建议被记成决定) | P2-4 状态机 + 来源验证; 摘要只处理增量段, 检查点可由原文随时重建 |
| 摘要调用受 5xx/524 影响 | D5: 后台化 + 单次重试 + 退化为纯截断, 永不阻塞聊天 |
| LiteRT 重建 prefill 变慢 (重建轮延迟尖峰) | 台阶驱逐拉大重建间隔; 压缩目标 45% 控制 prefill 规模; 冒烟记录尖峰数据 |
| 记账口径混淆 (S4) | P1-4 以 `contextTokensAfterTurn` 归一并有专门测试固定语义 |
| codec 升版风险 | D4 fail-closed 惯例, 升版条目均带编解码单测 |
| 回退 | 各阶段独立提交, 逐阶段 revert 即可; P1 参数全部集中在 `ContextPolicy`, 极端情况下调回等效旧行为 (预算调大 + 水位 100%) |

## 9. 相对 Codex 参考稿的主要调整

1. 增加 P0 (观测与校准先行): 在线 usage 已随消息持久化 (S7), 校准几乎零成本, 且 P1 的收益需要基线数据来证明.
2. 台阶式驱逐替代逐轮滑动窗口: 同时服务重建频率与 prompt cache 前缀稳定性.
3. Binder 首版透明压缩不做隐藏 LLM 摘要 (D2): 避免用调用方额度发起不可见请求; 也因此完全不需要宿主 API 变更 (Codex 稿中 CONTEXT_EXHAUSTED 新错误码方案被放弃).
4. 记账优先采用实际值 (在线实报 usage / 本地 `getTokenCount`), 估算仅兜底 - 比纯估算方案更稳.
5. FTS/Room 召回最初降级为 P4 可选项: P4 后续按需部署时仍保留 AtomicFile 为唯一真相, 仅增加可丢弃的 SQLite FTS4 旁路索引, 因而没有把主历史存储迁移到 Room.
6. 摘要失败退化路径按当前网络环境 (5xx/524 常见) 设计为常态而非异常 (D5).

## 10. 语音能力接入 (3-Macaw Speech)

> 目标一句话: 让 3-Stone AI 的聊天可以用嘴说, 用耳朵听, 并能免手连续对话, 开启常驻监听后说出唤醒词即可对话; 装了 3-Macaw Speech 时默认全程离线, 没装时听写与朗读仍可用系统已有的识别与合成应用.
>
> 制定日期: 2026-10-17. 决策 D6-D21 由维护者经四轮选择题确认 (全部为推荐项; 第一轮能力范围在推荐的三项之外加选了唤醒词); 同日维护者追加决定 3-Stone AI, 3-Stove Agent, 3-Macaw Speech 都具备可开关的常驻监听且同一时刻只允许一个, 经第五轮选择题确认 D27-D30 (全部为推荐项), D14 由 D27 取代; 3-Macaw Speech 随后定下系统数字助理与 `VOICE_COMMAND` 入口 (Macaw D48-D56), 维护者经第六轮选择题确认 3-Stone AI 应答该入口并保留自己的常驻监听 (选 "两者都要"), 其余三项为推荐项, 即 D35-D37; D22-D26, D31-D34 与 D38 为据此派生的技术决策, 进入对应条目前可推翻 (推翻点已逐条标注), 之后视同固定. 起点: 3-Stone AI 1.3.0 (2026-10-15 已发布, `VERSION_BUILD=275`), 3-Macaw Speech `master@f020635` (P0-P5.6 已推送, 1.0.0 未发布), 3-Stove Agent `a1d90101` (其 D59 已推送); 宿主 6.8.0 未发布, `speech-api` 契约与 `speech` 脚本模块只在宿主本地提交 (`b91aef068` .. `bd410162c` 等). 本节不改宿主与 3-Macaw Speech.

### 10.1 已核实现状

| # | 现状事实 | 位置 |
|---|---|---|
| V1 | 聊天输入为 View 体系的 `EditText` + 46dp 发送按钮 (发送 / 停止共用), 输入上限 `ChatConversationPolicy.MAXIMUM_INPUT_CHARACTERS = 16_384`; 消息操作 4 项 `COPY` / `EDIT` / `DELETE` / `REGENERATE`; 流式输出经 `GenerationListener.onTextDelta` / `onCompleted` 到达; 回复由 `StreamingMarkdownParser` 解析为纯文本 + span (标题, 列表, 链接, 代码块, 表格, 行内代码, 数学) | `ChatActivity.kt` (输入区约 1233-1332 行, `MessageAction` 约 3809 行), `ChatConversation.kt`, `StreamingMarkdownParser.kt` |
| V2 | 设置页分组: 外观 / 对话 / 更新 / 信息 ("生成" 是 "对话" 组内打开底部表单的一行, 2026-10-19 复核更正); 聊天设置存于 `ChatUiSettings` / `ChatUiSettingsStore` (SharedPreferences) | `AppSettingsActivity.kt`, `ChatUiSettings.kt` |
| V3 | 权限只有 `org.autojs.permission.PLUGIN`, `INTERNET`, `ACCESS_NETWORK_STATE`; `<queries>` 只有 `org.autojs.autojs6`; 仓库内没有任何语音代码 | `AndroidManifest.xml` |
| V4 | 3-Macaw Speech 的系统语音输入: 导出且无权限的 `ui.voice.VoiceInputActivity` 应答 `android.speech.action.RECOGNIZE_SPEECH`, 底部弹层留在调用方任务中; 首句 `final` 即 `RESULT_OK`, 开始收音 8 s 无语音 `RESULT_NO_MATCH`, 单句上限 60 s; `EXTRA_LANGUAGE` 只用于选模型 (SenseVoice 自动识别语种), `EXTRA_PROMPT` 为弹层标题, 只返回 1 条结果; 无麦克风权限 / 无识别模型 / 麦克风被占用都在弹层内引导; 组件可被其设置开关禁用 | Macaw ROADMAP D41-D47 |
| V5 | 3-Macaw Speech 的系统 TTS 引擎 `ttsengine.ThreeMacawSpeechTtsEngineService` (`TTS_SERVICE`, 无插件权限), 按语言选默认语音, 说话人取其设置页; 单句 warm RTF: XQ-DQ72 上 AISHELL-3 0.035 / MeloTTS 0.504, OPD2413 上 0.042 / 0.564, G8441 上 MeloTTS 2.262 与 Kokoro 3.434 (慢于实时, 句间停顿), AISHELL-3 / Piper / Matcha 在三台设备上都快于实时 | Macaw D19 / D35, `docs/development/tts-baseline.md` |
| V6 | 3-Macaw Speech 的能力服务 `org.autojs.plugin.SPEECH` 只受 `org.autojs.permission.PLUGIN` 保护 (宿主定义, `protectionLevel="signature"`), 没有额外的调用方校验; 契约 `speech-api` (`ISpeechPlugin`, `CONTRACT_VERSION = 1`) 只在宿主本地提交; 整个插件同一时刻只有 1 个麦克风会话 (听写或唤醒, 超出为 `SESSION_BUSY`); API 34+ 由透明 `MicrophoneStartActivity` 启动麦克风前台服务, 绑定方需带 `BIND_ALLOW_ACTIVITY_STARTS`; 宿主未安装时该权限不存在, 任何应用都绑定不了 | Macaw D16 / D21 / D22, `service/ThreeMacawSpeechService.kt` |
| V7 | 宿主 `speech` 模块 (`listen` / `wake` / `speak`) 与 `ai` 模块 (`chat` / `ask` / `stream` / `session`) 都运行在宿主进程, 插件调用不到; 脚本可以自行组合二者 | Macaw 附录 B, AutoJs6-Documentation `api/ai.md` |
| V8 | 3-Stove Agent 先例 (D59): 偏好开启且 Macaw 弹层可解析时, `RecognizerIntent` 以 `setPackage` 固定到 Macaw, 否则交给系统选择; 设置行只在可解析时显示, 回到设置页重新判断; `<queries>` 只写 action; 未下载识别模型时由 Macaw 弹层引导安装, 不自动回退其他识别应用 | Stove ROADMAP D59, 提交 `a1d90101` |
| V9 | 常驻监听的平台限制: Android 10+ 后台启动 Activity 需 "显示在其他应用上层" (`SYSTEM_ALERT_WINDOW`) 等豁免; API 34+ 前台服务必须声明类型, 不录音的应用不能用 `microphone` 类型; Android 14+ 不允许从 `BOOT_COMPLETED` 启动麦克风前台服务, Macaw 在 API 34+ 也只能在可见状态下启动其麦克风前台服务 (V6); 3-Stone AI 的 AI provider 只接受宿主 uid 的调用 (`HostCallerVerifier`), 其他插件无法借用; 3-Macaw Speech 的 1.1.0 候选 "内置语音助手" (其 D8 / P7) 计划由 Macaw 常驻唤醒并把指令交给 3-Stove Agent; Android 10+ 有其他应用在录音 (通话, 录音机, 其他语音输入) 时, 常驻监听只收到静音; ColorOS / MIUI 需用户手动开启自启动与电池白名单, 否则常驻服务可能被杀; 常驻监听的耗电尚未在真机测过 | 平台文档, `provider/HostCallerVerifier.kt`, Macaw ROADMAP P7 |
| V10 | 3-Macaw Speech 的系统数字助理 (Macaw D48-D57, P5.7, 已推送 `bf19529`, 1.0.0 未发布): 用户把 Macaw 设为默认数字助理后, 长按电源键 / Home 键听写, 不常驻录音; 听写结束后显示可编辑的文字与目标按钮, 用户点按钮才发送 (D49); 目标入口契约 (D50): 目标导出一个 Activity 应答 `org.autojs.plugin.action.VOICE_COMMAND` + DEFAULT, extras 为 `Intent.EXTRA_TEXT` (识别文字, 必有) 与 `org.autojs.plugin.extra.VOICE_LANGUAGE` (BCP 47, 可缺), 发送用显式组件 + `FLAG_ACTIVITY_NEW_TASK`, 入口不受权限保护, 接收方把文本当作不可信输入; 按钮文字与图标取该 Activity 的 label 与 icon; 3-Stone AI 收到后打开聊天页, 在新对话里立即发送并显示回答 (D52), 是否朗读由本路线图定; 锁屏时先解锁再发送 (D55); 不读取屏幕内容 (D54); Macaw 以本节 D27 为准, 三个应用各有常驻监听开关并按 D28 / D31 互斥, 与数字助理并存 (`bf19529`); 数字助理, `RECOGNIZE_SPEECH` 弹层, `RecognitionService` 与试听试说页开始收音时, 若麦克风正被唤醒会话占用 (例如 3-Stone AI 常驻服务持有的 kws 会话), 唤醒会话保持打开但静默 (不报命中, 不结束, 不发新事件), 接管方结束后自动继续, 对客户端透明且不改契约; 正在进行的听写会话 (含 `thenListen` 已转入听写的阶段) 不被接管, 仍得到占用引导 (Macaw D57) | Macaw ROADMAP D48-D57 |

2026-10-19 开工前复核: V1 (输入区 `ChatActivity.kt` 1233-1332 行, `MessageAction` 3809 行; 回复消息菜单为 `COPY` / `REGENERATE`, 用户消息为 `COPY` / `EDIT` / `DELETE`), V3 与 V8 仍成立, V2 已更正; Sony 三台装有 3-Macaw Speech versionCode 41 (弹层与 TTS 引擎均可解析), 小米两台仍是 versionCode 22 (两者都不可解析, 按 "无 Macaw" 处理). 2026-10-20 发布前更正: 本节开头 "1.3.0 (2026-10-15 已发布)" 不准确, GitHub 上最近一次发布是 v1.1.4 (2026-09-20, versionCode 213, 官方索引 213.json); 1.2.0, 1.2.1 与 1.3.0 只写入 changelog, 没有发布, 它们的改动随 1.4.0 首次发布.

### 10.2 目标与非目标

目标:

- G6: 输入框一键听写, 文字插入光标处, 由用户核对后发送.
- G7: 回复可朗读: 手动朗读任一条回复, 或开启自动朗读后边生成边读; 代码与表格不逐字念.
- G8: 免手语音对话: 长按麦克风进入, 说完自动发送, 读完自动再听; 1.5.0 起装了 Macaw 时无弹层.
- G9: 没装 3-Macaw Speech 时用系统已有的识别 / 合成应用; 装了时默认固定用它, 全程离线.
- G10: 常驻监听 (1.5.0, 可开关, 需 3-Macaw Speech): 说出唤醒词即唤醒 3-Stone AI 对话; 与 3-Stove Agent, 3-Macaw Speech 的常驻监听互斥, 开启时能指出是哪个应用占用.
- G11: 经 3-Macaw Speech 的数字助理提问 (1.4.0): 长按电源键说话, 在 Macaw 弹层点 "3-Stone AI", 新对话里立即得到并听到回答.

非目标:

- 3-Stone AI 自己录音, 申请 `RECORD_AUDIO` 或处理音频 (D16, D27).
- 新增脚本 API, 修改宿主 `ai` / `speech` 模块或 AI provider 契约 (D21).
- 开机自动恢复常驻监听 (D30), 悬浮麦克风按钮.
- 3-Stove Agent 与 3-Macaw Speech 两侧的常驻监听实现 (各自仓库, 按 D31 的共用约定).
- 朗读时插话打断 (barge-in, 需要回声消除), 把音频作为多模态输入发给模型, 实现 `RecognitionService` 或自带语音模型.

### 10.3 已定决策 (2026-10-17, 维护者四轮选择题)

| ID | 决策 | 说明 |
|---|---|---|
| D6 | 路线: 先系统接口, 后 Binder | 1.4.0 用 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH` 听写, 用 `android.speech.tts.TextToSpeech` 朗读, 不依赖任何契约, 只新增 2 条 `<queries>`; 1.5.0 在宿主 6.8.0 发布 `speech-api` 后直接绑定 3-Macaw Speech 的 `org.autojs.plugin.SPEECH`. 未采用: 只用系统接口 (做不了输入框内实时文字与唤醒词); 现在就绑定 Binder (要暂存未推送宿主提交构建的 AAR, 契约 v1 可能在 6.8.0 前变化, 发布被宿主阻塞). 宿主 `speech` 模块插件侧调用不到 (V7), 不作为路线 |
| D7 | 能力范围: 听写, 朗读, 语音对话模式, 唤醒词 | 前三项在 1.4.0 (P5); 唤醒词只能经 Binder (`openWakeSession`), 在 1.5.0 (P6), 形态按 D27 为常驻监听 |
| D8 | 未装 3-Macaw Speech: 回退系统应用 + 设置引导 | 与 Stove D59 一致. 听写: 任一识别应用可解析时显示麦克风按钮, 一个都没有时隐藏; 朗读: Macaw 引擎未装时用系统默认 TTS 引擎, 设备上没有任何 TTS 引擎时不显示 "朗读" 菜单项, 自动朗读开关显示为不可用; 设置页说明写明 "安装 3-Macaw Speech 并下载模型即可离线识别与朗读" |
| D9 | 听写结果: 插入光标处, 手动发送 | 插入输入框光标处, 有选区则替换; 受 16,384 字符上限约束; 不自动发送 (语音对话模式除外, D13). 未采用: 设置项可选自动发送; 直接发送 |
| D10 | 自动朗读: 边生成边读, 默认关 | 流式输出按句切分 (D25), 每成一句即 `QUEUE_ADD`; 设置开关 "自动朗读回复" 默认关, 语音对话模式下始终自动读; 停止生成, 发送新消息, 切换或新建会话, 离开聊天界面 (`onStop`) 即停读并清空队列. 手动 "朗读" 读整条已完成的回复. 未采用: 生成完成后再读; 默认开 |
| D11 | Markdown 朗读: 读正文, 代码与表格用提示语 | 去掉标题 / 强调 / 列表 / 引用记号; 链接只读文字, 不读 URL; 代码块与表格各读一句本地化提示 (如 "此处有代码块" / "此处有表格"); 行内代码照读. 复用 `StreamingMarkdownParser` 的解析结果. 未采用: 去记号后全部照读; 静默跳过 |
| D12 | 朗读引擎: 优先 3-Macaw Speech, 与听写共用一个开关 | 设置开关 "优先使用 3-Macaw Speech" 默认开: 开启时听写 intent 在 Macaw 弹层可解析时 `setPackage` 到 Macaw, `TextToSpeech` 在 Macaw 引擎已安装时以其包名创建; 关闭或不可用时由系统选择识别应用, 用系统默认 TTS 引擎. 语音与语速取引擎自身的设置 (Macaw D35). 未采用: 听写与朗读各一个开关; 设置页选引擎与语音 |
| D13 | 语音对话模式: 长按麦克风进入, 状态条退出 | 短按 = 单次听写 (D9); 长按 = 进入对话模式: 听写 -> 结果自动发送 -> 回复边生成边读 -> 读完自动再听. 输入框上方显示 "语音对话中" 状态条 (含退出按钮); 识别弹层取消 (`RESULT_CANCELED`), 无语音 (`RESULT_NO_MATCH`), 其他识别错误, 生成失败, 离开聊天界面都退出; 朗读中点状态条可打断朗读并立即听下一句. 1.4.0 每轮都会弹出识别弹层, 1.5.0 装了 Macaw 时无弹层 (D20). 未采用: 顶栏单独按钮; 设置开关控制 |
| D14 | (已由 D27 取代) 唤醒词: 仅聊天界面可见时监听, 默认关 | 第三轮选定: 聊天界面 `onStart` 后经 `openWakeSession` 监听, `onStop` 即释放, 无常驻通知, 不加前台服务. 同日维护者改为三个应用都具备可开关的常驻监听 (D27), 本条不再实施 |
| D15 | 唤醒关键词: 默认 "小石小石" + "Hey Stone", 可改 | Macaw KWS `kws.zipformer.zh-en.2025-12-20` 为开放词表 (中文按拼音, 英文按模型 `en.phone`); 设置页可编辑, 至多 5 条; 灵敏度 3 档 (对应 Macaw 关键词的 `threshold` / `boost`, 数值在 P6-4 实测后确定). 未采用: 无默认词; 只用中文 |
| D16 | 权限与隐私: 不申请麦克风, 不记日志 | 3-Stone AI 永不申请 `RECORD_AUDIO`, 不接触音频 (由识别应用或 Macaw 录音; 常驻监听同样由 Macaw 录音, 只为它新增前台服务, 通知与可选的悬浮窗权限, 见 D32); 识别文字与朗读文本不写 logcat (与 Macaw 附录 D Q8 (a) 一致); 语音输入的消息与打字消息在会话记录中没有区别, 会话历史 codec 不升版; README 与设置说明写明: 3-Macaw Speech 全程离线, 其他识别 / 合成应用可能联网, 识别出的文字仍按所选模型发送 (在线 target 会发给服务商). 未采用: 消息标记语音来源 (需会话历史 codec 升 v5) |
| D17 | 设置: 新增 "语音" 组, 位于 "对话" 组之后 | 1.4.0 三行: 自动朗读回复 (开关, 默认关), 优先使用 3-Macaw Speech (开关, 默认开, 只在 Macaw 弹层或引擎任一可解析时显示), 状态说明 (听写与朗读当前分别由谁提供, 未装 Macaw 时的安装提示); 回到设置页时重新判断. 1.5.0 追加: 常驻监听 (开关, D27), 唤醒关键词 (编辑), 唤醒灵敏度. 遵循独立设置规范 (`AUTOJS6_PLUGIN_STANDALONE_SETTINGS_AGENTS.md`). 未采用: 并入 "对话" 组 |
| D18 | 停止朗读: 输入框上方状态条 | 与语音对话模式共用同一状态条: 朗读时显示 "正在朗读" + 停止按钮; 正在朗读的那条回复, 消息菜单中的 "朗读" 显示为 "停止朗读"; 失去音频焦点 (来电, 其他播放) 即停. 未采用: 只用消息菜单 |
| D19 | 朗读语言: 跟随应用语言 | `TextToSpeech.setLanguage` 取 3-Stone AI 当前界面语言 (应用语言设置; 跟随 AutoJs6 时取解析后的语言); 引擎返回 `LANG_MISSING_DATA` / `LANG_NOT_SUPPORTED` 时退回引擎默认语音并提示一次; 听写的 `EXTRA_LANGUAGE` 同样取界面语言 (Macaw 只用它选模型, SenseVoice 仍自动识别语种, V4). 未采用: 按每句文字判断 (中英混排的回复会频繁换声音); 完全交给引擎默认 |
| D20 | 版本分期: 1.4.0 系统接口, 1.5.0 Binder | 1.4.0 = P5 (听写, 朗读, 语音对话模式), 可立即开工并独立发布; 1.5.0 = P6 (装了 Macaw 时听写改为输入框内实时文字, 语音对话无弹层, 常驻监听), 宿主 6.8.0 发布 `speech-api` 后开工. 未采用: 拆成 1.4.0 / 1.5.0 / 1.6.0 三版; 全部等 Binder 一版发布 |
| D21 | 不新增脚本 API | 3-Stone AI 是 AI provider, 脚本侧的 `ai` 与 `speech` 模块都在宿主 (V7); 脚本自行组合 `speech.listen` -> `ai.chat` / `ai.stream` -> `speech.speak`. 1.5.0 (宿主 6.8.0 发布后) 在 README 与 `plugin_instruction` 中增加组合示例; 不改宿主模块与 AI provider 契约 |
| D27 | 常驻监听: 三个应用都可开关, 同一时刻只允许一个 (取代 D14) | 第六轮维护者再次确认保留, 与 Macaw 的数字助理 (V10, D35) 并存. 维护者 2026-10-17 追加决定: 3-Stone AI, 3-Stove Agent, 3-Macaw Speech 各有常驻监听开关 (默认关); 3-Stone AI 命中唤醒词后唤醒自身并使用对话能力, 3-Stove Agent 唤醒自身并使用 agent 能力, 3-Macaw Speech 按其规划把能力指派给 3-Stone AI 或 3-Stove Agent. 3-Stone AI 侧 (1.5.0, Binder): 由自己的前台服务持有 Macaw 的唤醒会话, 仍不申请 `RECORD_AUDIO` (Macaw 录音); 开启时 3-Stone AI 与 Macaw 各有一条常驻通知; 需 Macaw 已装, 支持 `kws` 且已装 KWS 模型 (`kws.zipformer.zh-en.2025-12-20`, 8.4 MB), 否则开关不可用并说明原因. 未采用: 委托 Macaw 常驻, 3-Stone AI 只作指派目标 (要改 Macaw 契约, 与 Macaw 自己的开关语义重叠); 3-Stone AI 自己录音并内置 sherpa-onnx 与 KWS (推翻 D16, 每 ABI 约多 12-15 MB) |
| D28 | 互斥检测: 组件状态即开关 | 三个应用各导出一个带统一 action 的组件, 只在常驻监听开启时启用 (与 Macaw D43 同法, 组件状态就是设置本身); 开启前查询其他包的该组件, 已有则提示 "系统只能支持一个常驻监听应用, 需要先关闭 xxx 才能开启" 并提供 "打开 xxx 设置" 按钮 (不能代为关闭别的应用); 不改宿主契约, 三个仓库各自实现同一约定 (D31). 未采用: 由 Macaw 仲裁 (未装 Macaw 时无从判断, 可能要进 `speech-api`); 只靠 Macaw 的 `SESSION_BUSY` 事后报错 (说不出是哪个应用) |
| D29 | 唤醒后的行为: 有悬浮窗权限开界面, 否则后台语音对话 | 已授予 "显示在其他应用上层" 时, 命中唤醒词即打开聊天界面并进入语音对话模式 (D13); 未授予时不开界面, 在常驻通知中显示状态, 听写 -> 发送 -> 朗读回复, 记入会话历史, 点通知可打开该会话. 开启常驻监听时询问一次是否授予悬浮窗权限 (可拒绝). 未采用: 始终后台语音对话; 必须授权悬浮窗并始终开界面 |
| D30 | 重启与进程被杀: 不自启, 打开应用即恢复 | 开关保持开启, 下次打开 3-Stone AI (界面可见) 时自动恢复监听; 手机重启后发一条通知 "点此恢复常驻监听", 点击后在可见状态下恢复 (V9 的平台限制). 未采用: 开机尽力自启; 重启即关闭开关 |
| D35 | 应答 3-Macaw Speech 的 `VOICE_COMMAND`, 并保留自己的常驻监听 | 第六轮选定 "两者都要": 3-Stone AI 导出一个 Activity 应答 `org.autojs.plugin.action.VOICE_COMMAND` + DEFAULT (Macaw D50), 收到后打开聊天页, 在新对话里用当前选中的 target 立即发送并显示回答 (Macaw D52); 同时保留 D27-D33 的常驻监听. 入口不受权限保护, 不新增权限; 放在 1.4.0 (P5-5), 不依赖 Binder 与宿主, Macaw 1.0.0 发布前不会被调用. Macaw 已随之改为以 D27 为准 (`bf19529`, V10). 未采用: 以 Macaw D48-D56 为准并撤销 D27-D34 |
| D36 | `VOICE_COMMAND` 的回答: 这一轮总是朗读 | 该条回复边生成边读 (D10, D11 的规则), 读完即止, 不自动再听; 朗读语言优先取 `org.autojs.plugin.extra.VOICE_LANGUAGE`, 缺失或引擎不支持时按 D19 取应用语言; 之后的轮次照 "自动朗读回复" 设置. 未采用: 进入语音对话模式; 跟随自动朗读设置 |
| D37 | `VOICE_COMMAND` 的防护: 立即发送 + 可见 + 限速 | 按 Macaw D52 立即发送, 但总是打开可见的聊天页并新建对话; 上一次立即发送后 10 s 内再收到的调用只填入新对话的输入框, 不发送; 文字去掉控制字符并按 16,384 字符截断; 文字内容不写日志 (D16). 未采用: 只在能确认调用方是同签名的 3-Macaw Speech 时立即发送 (Macaw 用 `startActivity`, API 34 以下多数情况无法可靠识别调用方); 不加限制 |
| D39 | 限时监听, 与 3-Macaw Speech / 3-Stove Agent 一致 | 维护者 2026-10-20 选定 (3-Macaw Speech D67): 常驻监听开关旁增加 "监听时长" (10 / 30 / 60 分钟后自动关闭, 或一直开着) 与 "只在充电时监听"; 到时自动关闭并撤下常驻通知, "只在充电时监听" 开启时拔掉电源即暂停唤醒会话, 接上后继续; 车载蓝牙等情景用 AutoJs6 脚本样例. 写进 P6-4. 背景: 第三方应用的唤醒词必须持续录音, 麦克风指示灯常亮, 系统热词通道只给系统级助理 (Macaw D58); 常驻监听保留为默认关的可选项 (Macaw D66). 未采用: 只加在 Macaw; Stove / Stone 保持开关两态. |

### 10.4 派生技术决策 (进入对应条目前可推翻)

| ID | 决策 | 说明 | 推翻点 |
|---|---|---|---|
| D22 | 可解析判定 | `<queries>` 新增 `<intent><action android:name="android.speech.action.RECOGNIZE_SPEECH" /></intent>` 与 `<intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>` (只写 action, 同 Stove); 听写提供方 = Macaw (带 `setPackage` 的同一 intent 可解析) / 其他 (不带包名可解析) / 无; 朗读提供方 = Macaw (`TTS_SERVICE` + Macaw 包名可解析) / 系统默认引擎 (`TextToSpeech.getEngines()` 非空) / 无. 规则为纯 JVM 对象 `SpeechAvailabilityPolicy`, 聊天页与设置页在 `onResume` 重新判断. Macaw 包名 `io.github.supermonster003.autojs6.plugin.three.macaw.speech` | P5-1 |
| D23 | 听写调用与插入 | `registerForActivityResult(StartActivityForResult)`; extras: `EXTRA_LANGUAGE_MODEL = LANGUAGE_MODEL_FREE_FORM`, `EXTRA_LANGUAGE` (D19), `EXTRA_PROMPT` (单次听写 "请说话", 对话模式 "请说出下一句", 10 种语言), `EXTRA_MAX_RESULTS = 1`; 取 `EXTRA_RESULTS[0]`. 插入规则为纯 JVM `DictationInsertion`: 光标插入或替换选区, 插入点两侧都是拉丁字母或数字时补一个空格, 超出 16,384 字符时截断并提示. 单次听写时取消与无语音静默, 其他错误码提示一次; `ActivityNotFoundException` 提示并刷新按钮. 麦克风按钮放在输入框内末端 (36dp, 与发送按钮分开), 生成中仍可听写 (只改输入框). 实现时细化 (P5-2): 空格规则扩展为 "两侧都是用空格分词的文字 (拉丁, 西里尔, 希腊, 韩文, 阿拉伯等) 的字母或数字", 汉字, 假名, 泰文等直接相连, 俄语与韩语听写因此也能正确分词; 输入框没有焦点时追加到末尾; 网络与麦克风错误各有专门提示, 其余错误码用通用提示; 输入框已满时不改动 (含选区) 并提示 | P5-2 |
| D24 | 朗读输出 | 聊天页持有一个 `SpeechOutput` (包装 `TextToSpeech`): 首次需要时异步初始化, 引擎偏好变化或 `onDestroy` 时 `shutdown`; utterance ID = `<messageId>:<sentenceIndex>`; `UtteranceProgressListener` 驱动状态条与对话模式的 "读完再听"; 朗读期间请求 `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, 失去焦点即 `stop()` 清队列; 单句长度不超过 `min(TextToSpeech.getMaxSpeechInputLength(), 1000)`, 超长句在逗号或空白处再切. 文本内容不写日志 (D16). 实现时细化 (P5-3): utterance ID 前加朗读序号, 即 `<session>:<messageId>:<sentenceIndex>`, 停止后迟到的回调不会影响同一条回复的下一次朗读; 音频焦点在整次朗读期间持有, 申请被拒 (如通话中) 则不朗读; 引擎通过 `SpeechEngine` 接口使用, 设备测试可换成静音的假引擎; 引擎是否存在用 `queryIntentServices(TTS_SERVICE)` 判断 (P5-1); 某句被引擎拒绝时跳过该句 | P5-3 |
| D25 | 流式切句与 Markdown 规则 | 纯 JVM `SpeakableTextPolicy`: 输入 `StreamingMarkdownParser` 的结果, 按 D11 生成可读文本 (数学 span 读其渲染文字, 分隔线与图片忽略); 句末为全角句号 / 问号 / 叹号 (U+3002, U+FF1F, U+FF01), 半角 `.` `?` `!` 后跟空白或结尾 (排除小数与常见缩写), 省略号 (U+2026), 换行; 代码围栏内不切句, 围栏闭合时整块换成一句提示; 未结束的末句在 `onCompleted` 时冲刷. 约束: 对同一回复, 流式增量切出的句子序列与一次性处理完整文本的结果一致. 实现时细化 (P5-3): 为满足这条约束, 只有以 `|` 开头的表头行才按表格处理 (解析器也接受不以 `|` 开头的表头, 朗读时把这种行当普通文字读), 这样未结束的普通行可以提前读出已完成的句子; 未结束的行从第一个 `[` (链接 / 图片) 或 `$` (数学) 起等行结束再读; 句末另加阿拉伯问号 (U+061F) 与全角右引号 / 括号等收尾符号; 有序与无序列表的序号都不读; 只含标点的句子不读; 超长句的切分点另含阿拉伯逗号与分号 | P5-3 |
| D26 | Binder 接入 (1.5.0) | `libs/` 新增宿主 6.8.0 发布提交构建的 `speech-api.aar`, 登记 `libs/SHA256SUMS`, `hostApiNames` 由 4 个变为 5 个; 只在聊天界面可见时绑定 `org.autojs.plugin.SPEECH` + `setPackage(Macaw)`, flags `BIND_AUTO_CREATE` (+ API 34 `BIND_ALLOW_ACTIVITY_STARTS`, V6); 先读 `getCapabilities` 的契约版本与 `speechFeatures` (`asrStreaming`, `kws`), 不满足, 绑定失败 (宿主未装时 PLUGIN 权限不存在, `SecurityException`) 或回调 Binder 死亡时退回 P5 的系统接口; partial 以灰色临时文字写在光标处, `final` 定稿为正文; `PERMISSION_DENIED` 时打开 Macaw 的 `MicrophonePermissionActivity`, `SESSION_BUSY` 时提示麦克风被占用 | P6-1 |
| D31 | 常驻监听互斥约定 (三个仓库共用) | action `org.autojs.plugin.speech.action.RESIDENT_LISTENER`; 承载组件为各应用的常驻监听服务 (3-Stone AI 为 `speech.ResidentListenerService`), 导出并受 `org.autojs.permission.PLUGIN` 保护, Manifest 默认 `enabled="false"`, 开关切换即以 `DONT_KILL_APP` 改组件状态, 不另存偏好; 组件 meta-data `org.autojs.plugin.speech.RESIDENT_LISTENER_SETTINGS` 写自己的设置 Activity 类名, 供对方的 "打开 xxx 设置" 按钮使用 (缺省时用启动 intent); 提示中的 "xxx" 取对方 application label; `<queries>` 增加该 action. 开启前与常驻服务每次启动前都查询一次, 发现多个时全部列出; 极端竞态下 Macaw 的单麦克风会话 (`SESSION_BUSY`) 兜底. 3-Stove Agent 与 3-Macaw Speech 的会话照抄本条, 三方确认后才可实现 | P6-0 |
| D32 | 3-Stone AI 常驻服务 | 前台服务类型 `specialUse` (3-Stone AI 不录音, 不能用 `microphone` 类型, V9), 新增权限 `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `POST_NOTIFICATIONS` (API 33+, 开启时申请) 与可选的 `SYSTEM_ALERT_WINDOW` (D29, 系统设置页授予); 只在 3-Stone AI 界面可见时开启或恢复; 绑定 Macaw 同 D26 (含 API 34 `BIND_ALLOW_ACTIVITY_STARTS`); Macaw 进程死亡或唤醒会话以 `error` / `end` 结束时停止服务并通知; 3-Stone AI 自己经 Binder 发起的听写 (P6-2, P6-3) 不在 Macaw D57 的接管范围内, 先暂停 (`stop`) 唤醒会话, 听写结束后重开 (Macaw 只有 1 个麦克风会话); 经 Macaw 弹层的听写 (P5) 与 Macaw 数字助理由 Macaw D57 临时接管唤醒会话, 3-Stone AI 不做处理, 唤醒会话一直有效; 常驻期间脚本的 `speech.listen` 会得到 `SESSION_BUSY`, 设置说明与 README 写明 | P6-4 |
| D33 | 后台语音对话 | 发送目标为聊天界面当前选中的 target; 距上一次唤醒对话结束不到 5 分钟时续用同一会话, 否则新建会话; 生成与朗读在常驻服务内运行 (从 `ChatActivity` 抽出可在服务中运行的会话协调层, 上下文治理 P1-P4 的编译与记账照常生效); 通知显示 "正在听 / 正在思考 / 正在朗读" 与 "停止" / "打开" 两个动作; 一轮对话结束后回到唤醒监听 | P6-5 |
| D34 | (已由 D35 / D38 取代) 3-Macaw Speech 指派入口 | 原定另开受 PLUGIN 权限保护的入口, 形态随 Macaw P7 评审确定; Macaw 已定为不受权限保护的 `VOICE_COMMAND` (Macaw D50), 由 D35-D38 实现, 本条不再实施 | - |
| D38 | `VOICE_COMMAND` 入口实现 | 独立的透明跳板 Activity `voice.VoiceCommandActivity` (导出, 无权限, `excludeFromRecents`, label 为 `@string/app_name` 即 "3-Stone AI", icon 取应用图标), 只做校验后转交 `ChatActivity` (`onNewIntent` 或冷启动) 并立即结束, 不在跳板内初始化 backend; 纯 JVM `VoiceCommandPolicy`: `EXTRA_TEXT` 缺失或去空白后为空时只打开聊天页, 控制字符与截断 (D37), 10 s 限速窗口, `VOICE_LANGUAGE` 解析为 BCP 47 (非法即忽略). `ChatActivity` 收到后: 正在编辑消息则取消编辑; 正在生成则先停止当前生成 (已输出内容按停止轮保留) 再新建对话; 当前没有可用 target (未配置在线 provider, 本地模型未就绪) 时预填输入框并提示, 不发送. 实现时细化 (P5-5): 跳板设 `taskAffinity=""` 与 `noHistory`, 以 `NEW_TASK | CLEAR_TOP | SINGLE_TOP` 打开聊天页所在的任务, 跳板自身不进最近任务; 命令经进程内收件箱 `VoiceCommandInbox` 交给聊天页, 聊天页的 intent 只带 "查看收件箱" 标记, 其他应用无法借聊天页的导出入口发送 (超过 60 s 未处理的命令丢弃); 聊天页在 target 目录就绪后处理; 限速记录用 `elapsedRealtime` 存于 SharedPreferences, 重启后自动失效; 无文字时只把聊天页带到前台, 不新建对话; 立即发送时保留原输入框草稿, 预填时以语音文字替换输入框; 也会退出语音对话并收起搜索与抽屉; 聊天页在本次 resume 的外观检查结束后才处理命令 (冷启动的外观重建发生在发送之前) | P5-5 |

### 10.5 阶段任务

#### P5 - 1.4.0 系统接口: 听写, 朗读, 语音对话模式, `VOICE_COMMAND` 入口

- [x] **P5-1** 可解析判定与设置: `<queries>` 两条 (D22); `SpeechAvailabilityPolicy` 与 Android 适配层; `ChatUiSettings` 新增 `autoReadReplies = false`, `preferMacawSpeech = true`; 设置页 "语音" 组三行 (D17); 10 种语言文案 (ASCII 标点; 阿拉伯语中紧跟非拉丁文字的 "3-Macaw Speech" 加 FSI / PDI).
  - 验收: `ChatUiSettingsTest` 扩展; `SpeechAvailabilityPolicy` 单测 (Macaw 有 / 无, 其他识别应用有 / 无, 无 TTS 引擎, 开关开 / 关); Manifest 断言 `<queries>` 形状 (只有 action); `ApplicationTextPunctuationTest` 通过; 设置页在有 / 无 Macaw 两种设备状态下目视.
  - 完成 (2026-10-19): `speech/SpeechAvailability.kt` (纯策略) 与 `speech/SpeechDeviceProbe.kt` (适配层; 引擎是否存在用 `queryIntentServices(TTS_SERVICE)`, 与 `TextToSpeech.getEngines()` 同一查询, 不必绑定引擎); 文案在 `strings_speech.xml` (10 种语言 + 显式英语). 单测: `SpeechAvailabilityPolicyTest` 7 项, `SpeechManifestContractTest` 2 项 (`<queries>` 只有宿主包与两条 action, 权限仍为 3 项), `ChatUiSettingsTest` 4 项, 标点守卫通过. 真机 (`py .python/device_speech_check.py --serial <X> --only settings`, 截图在 `build/verification/speech/<X>/`): XQ-AT72 (API 31, Macaw versionCode 41) 听写与朗读均为 MACAW, "优先使用" 行可见, 关闭后两者变为 SYSTEM; 小米 23046RP50C (API 35, Macaw versionCode 22 不提供弹层与引擎, 小米识别与合成) 两者为 SYSTEM, 无 "优先使用" 行, 显示安装提示; 两台截图已目视. 设备上无任何 TTS 引擎的状态只有单测覆盖 (当时没有可用的空白模拟器).
- [x] **P5-2** 单次听写 (D9, D23): 输入框内麦克风按钮, 短按发起 `RECOGNIZE_SPEECH`, 结果按 `DictationInsertion` 插入; 内容描述与 10 种语言文案.
  - 验收: `DictationInsertion` 单测 (光标, 选区, 空格补齐, 截断); instrumentation 以 androidTest 内的假识别 Activity 核对插入与各返回码; 真机 E4: 装 Macaw 的 Sony 一台 (弹层直接打开, 无选择器) 与只有 Google 的设备一台, 系统选择器与权限对话框由维护者在手机上操作.
  - 实现与自动化证据 (2026-10-19): `speech/DictationInsertion.kt`, `DictationOutcome.kt`, `DictationIntents.kt`; `ChatActivity` 输入框内麦克风. 单测 `DictationInsertionTest` 10 项, `DictationOutcomePolicyTest` 4 项. instrumentation `SpeechDictationDeviceTest` 3 项 (测试 APK 内的 Java 假识别 Activity, 经 `setPackage` 固定; 假模型目标 `ScriptedAiBackend`, 测试结束删除新建会话并还原偏好): 光标插入, 选区替换, 中文直连, 取消 / 无语音 / 成功但无文字均静默, 网络 / 麦克风 / 客户端 / 服务端错误各提示一次, 请求 extras (free_form, 界面语言, 提示语, 1 条), 16,384 上限截断与提示, 生成中只改输入框不发送, 固定的识别应用消失时提示, 无识别应用时隐藏按钮; G8441 (API 28), XQ-AT72 (API 31), 小米 23046RP50C (API 35) 均 3/3 通过 (`py .python/device_speech_check.py --serial <X> --only dictation`, 运行后卸载测试 APK, 假识别器不会留在系统选择器里). 真机 E4 (2026-10-19, 维护者在 XQ-DQ72 (API 33) 上说话, `--only manual-dictation`): "优先使用 3-Macaw Speech" 开启时直接打开 Macaw 弹层, 无选择器, 识别成功, 从请求到结果 6505 ms (含说话), 8 字插入输入框; 关闭该选项后出现系统选择器 (Google 与 3-Macaw Speech), 维护者选 Google "仅此一次", 识别成功 9999 ms (含选择与说话), 7 字插入, 默认识别应用未被改动.
- [x] **P5-3** 朗读 (D10, D11, D12, D18, D19, D24, D25): `SpeakableTextPolicy`, `SpeechOutput`; 回复消息菜单新增 "朗读 / 停止朗读" (完成且非空的回复); 自动朗读; 输入框上方状态条.
  - 验收: `SpeakableTextPolicyTest` (标题 / 列表 / 链接 / 裸 URL / 代码块 / 表格 / 行内代码 / 数学 / 未闭合围栏, 中英日混排, 小数与缩写, 超长句, 流式增量与一次性结果一致); 状态条与菜单状态单测; 真机: Macaw 引擎 (AISHELL-3 与 MeloTTS 各一次, 含 G8441 的慢于实时场景) 与系统默认引擎各一次, 记录首句出声延迟; 来电或其他播放抢占音频焦点时停读.
  - 完成 (2026-10-19): `speech/SpeakableTextPolicy.kt` (`SpeakableTextSegmenter` 流式切句), `ReadingQueue.kt`, `ReadAloudPolicy.kt`, `VoiceStatus.kt` (纯策略), `SpeechEngine.kt` 与 `SpeechOutput.kt` (Android); `StreamingMarkdownParser` 只新增两个表格判定的内部入口. 单测: `SpeakableTextPolicyTest` 12 项 (含 18 段样本 x 14 种分块的流式与一次性一致), `ReadAloudStateTest` 7 项 (队列, 菜单, 自动朗读, 语言顺序, 状态条). 设备: `SpeechReadingDeviceTest` 5 项在 G8441 (API 28), XQ-AT72 (API 31), 小米 23046RP50C (API 35) 均通过 (边生成边读, 首句比回复结束早约 710 ms; 菜单朗读 / 停止; 状态条, 发送新消息, 新建会话, `onStop`, 停止生成, 其他播放抢占音频焦点 6 种停读; 语言回退提示一次; 引擎启动失败; 无引擎时无菜单项); 真实引擎 `SpeechEngineLatencyDeviceTest` 在四台设备上记录首句延迟, 数值见 `docs/dev/speech-reading-smoke.md` (例: G8441 上 MeloTTS 冷启动首个音频 7724 ms, 句间停顿约 1 s, AISHELL-3 无停顿; XQ-DQ72 上 AISHELL-3 冷启动 159 ms). 未实际拨打电话, 焦点丢失由另一播放方的 `AUDIOFOCUS_GAIN` 产生.
- [x] **P5-4** 语音对话模式 (D13): 纯 JVM 状态机 `VoiceConversationMachine` (空闲 -> 听写 -> 生成并朗读 -> 听写 ..., 各退出条件); 长按进入, 状态条退出与打断; 进入时若在编辑消息则先取消编辑.
  - 验收: 状态机单测覆盖全部退出条件 (取消, 无语音, 识别错误, 生成失败, `onStop`, 点退出, 朗读中打断); 真机连续 10 轮语音对话 (在线 target 与 LiteRT 各一次), 记录 `docs/dev/voice-conversation-smoke.md` (每轮听写返回耗时, 首句出声延迟, 退出路径).
  - 实现与自动化证据 (2026-10-19): `speech/VoiceConversationMachine.kt` (纯状态机, 事件与效果分离), `ChatActivity` 长按麦克风进入 (无障碍长按动作 "开始语音对话"), 状态条显示 "正在听 / 正在等待回复 / 正在朗读, 点按可打断" 与退出按钮. 实现时的取舍: 用户按停止生成也退出; 打字发送, 新建 / 切换会话, 编辑消息, 切换 target 都退出; 进入时若在生成则先停止 (保留为停止轮) 并停读; 没有朗读引擎时回复完成即再听; 识别结果超过 16,384 字符时截断后发送; 界面被重建后迟到的对话识别结果改为插入输入框, 不发送. 单测 `VoiceConversationMachineTest` 7 项 (全部退出条件, 打断只在朗读中有效, 过期事件不改状态). 设备 `SpeechConversationDeviceTest` 6 项在 G8441 (API 28), XQ-AT72 (API 31), 小米 23046RP50C (API 35) 均通过 (假识别器连续 10 轮, 7 种退出路径, 朗读中打断后立即听下一句, 无引擎时仍循环); 真实 LiteRT 本地模型 + 3-Macaw Speech 引擎在 XQ-AT72 上连续 10 轮通过, 首句出声 1747-4735 ms, 详见 `docs/dev/voice-conversation-smoke.md`. 真人说话 (2026-10-19, 维护者在 XQ-DQ72 上, `--only manual-conversation`, 测试用固定回复, 3-Macaw Speech 弹层与引擎): 3 轮, 识别 6023 / 6016 / 5425 ms (含说话与静音判定), 发送到出声 1649 (引擎冷启动) / 447 / 477 ms, 每轮 4315 / 3165 / 3314 ms, 以关闭弹层结束 (`RecognitionCanceled`). 在线 target 10 轮 (2026-10-20, 维护者同意后, `--only conversation-online --target-model claude-opus-4-8`): G8441 上 OpenAI 兼容配置 + 3-Macaw Speech 引擎连续 10 轮通过, 首句出声 3080-4844 ms, 以取消识别结束 (`RecognitionCanceled`); 其他在线配置 (XQ-DQ72 `gpt-6.1-sol`, XQ-AT72 `gpt-5.6-sol` / `claude-fable-5-1`, G8441 `claude-fable-5-1`) 被服务商以 HTTP 403 拒绝, 均在第 1 轮按 `GenerationFailed` 正确退出.
- [x] **P5-5** `VOICE_COMMAND` 入口 (D35-D38): 跳板 Activity 与 `VoiceCommandPolicy`; `ChatActivity` 新建对话立即发送, 这一轮总是朗读, 10 s 限速, 无可用 target 时预填.
  - 验收: `VoiceCommandPolicyTest` (缺失 / 空白文字, 控制字符, 截断, 限速窗口, 语言标签); Manifest 断言 (导出, 无权限, action + DEFAULT, label 与 icon); instrumentation 以显式 intent + `FLAG_ACTIVITY_NEW_TASK` 冷启动与热启动各一次, 核对新对话, 发送与朗读开始, 以及生成中 / 编辑中 / 无 target 三种状态; 真机 E4: Macaw P5.7 数字助理可用后, 长按电源键听写并点 "3-Stone AI" 一次 (助理设置与解锁由维护者在手机上操作).
  - 实现与自动化证据 (2026-10-19): `voice/VoiceCommandPolicy.kt` (纯策略: 清理, 截断, 语言标签, 限速, 处置), `voice/VoiceCommandActivity.kt` (跳板, 收件箱, 限速记录), `ChatActivity` 处理命令 (这一轮按 `VOICE_LANGUAGE` 优先朗读, D36). 单测 `VoiceCommandPolicyTest` 7 项, `SpeechManifestContractTest` 新增入口断言 (导出, 无权限, 仅 action + DEFAULT, label, icon, NoDisplay, 不进最近任务, 空 taskAffinity). 设备 `SpeechVoiceCommandDeviceTest` 5 项 (显式组件 + `NEW_TASK` 启动跳板: 冷启动新对话发送并以 en-US 开始朗读; 热启动时停止生成中的回复并新建对话; 编辑中先取消编辑; 无 target 预填并提示; 10 s 内第二条只预填; 无文字只带到前台; 聊天页启动 intent 自带的文字被忽略) 在 G8441 (API 28), XQ-AT72 (API 31), XQ-DQ72 (API 33) 均通过. 两台 Sony 上 3-Macaw Speech (versionCode 41) 已是默认数字助理, `pm query-activities` 能解析 3-Stone AI 的入口. 真机 E4 (2026-10-19, 维护者在 XQ-DQ72 上长按电源键说话后点 "3-Stone AI", `--only manual-assistant`): 第一次问题已送达并发送, 但冷启动的聊天页拿到 AutoJs6 外观后自行重建, 回答与朗读被掐断; 已修复 (`a1252ef`: `ConfiguredActivity` 在生成 / 朗读 / 听写 / 语音对话进行中推迟外观重建, 语音命令在外观检查结束后才处理, 回归用例 `theAppearanceCheckNeverCutsOffTheAnswer` 在 XQ-DQ72 与 G8441 通过). 重做: 8 字问题进入新对话 (2 条消息) 并立即发送, 从开始朗读到出声 1863 ms (AISHELL-3, 引擎冷启动).
- [x] **P5-6** 文档与发布: `.readme` 10 种语言 (功能, 隐私 D16, 常见问题: 多个识别应用, 低端机句间停顿, 未下载模型, 怎样经 3-Macaw Speech 数字助理提问), changelog 1.4.0 (10 种语言, 写给用户看), 生成与 `--check`; 界面截图; `VERSION_NAME=1.4.0`; 按 `AGENTS.md` 的发布流程 (签名包, `appendDigestToReleasedFiles`, 真机安装正式包, 官方索引登记).
  - 文档就绪 (2026-10-19): README 模板新增 "语音" 一节 (`h3_voice`, `p_voice`, `voice_faq`, 生成器新增 `placeholder_voice_faq`), 10 种语言写入用法, 隐私 (D16) 与 5 条常见问题 (多个识别应用, 低端机句间停顿, 未下载识别模型, 经数字助理提问, 识别文字发往何处), 功能列表补 3 条, 安全性一节补语音隐私; changelog v1.4.0 10 种语言 (1 条提示, 5 条新增, 日期暂写 2026/10/19, 发布时按实际日期更新); `generate_markdown.py` 与 `--check` 通过; `VERSION_NAME=1.4.0`. 界面截图 (设置 "语音" 组, 输入框麦克风, 朗读状态条, 回复菜单 "朗读", 语音对话状态条) 已在 XQ-AT72, XQ-DQ72 与小米 23046RP50C 上目视, 存于 `build/verification/speech/`, 不入库 (README 不放截图, 与现状一致). 人工项已于 2026-10-19 完成, 在线 target 10 轮于 2026-10-20 补跑 (见 P5-4).
  - 发布 (2026-10-20, 维护者同意): GitHub Release v1.4.0 (tag 指向 `c50de87`, versionCode 291, 5 个 APK + SHA256SUMS) 已发布, 但未登记索引: nativeAlignment 1.9.0 按打包的原生库写入 `org.autojs.plugin.contract.SUPPORTED_ABIS`, 每个 1.4.0 APK 都只写了 `arm64-v8a,x86_64` (32 位包是纯 Java), 宿主以该元数据为准, 索引生成器也因 Release 资源名含 4 种 ABI 而拒绝. 维护者选择发 1.4.1: `ab46e17` 在构建中显式声明 4 种 ABI, 插件发现测试与正式包 smoke 增加该元数据检查, changelog v1.4.1 (10 种语言); GitHub Release v1.4.1 (versionCode 293, 5 个 APK + SHA256SUMS, 上传后核对摘要) 为 Latest, 官方索引 `293.json` (索引仓库 `a998e6f`, 只有 3-Stone AI 条目变化, 单测 76 项通过). 正式包 smoke (`py .python/device_release_smoke.py`, 1.4.0 与 1.4.1 各跑一遍, 均通过): XQ-DQ72 arm64-v8a; XQ-AT72 armeabi-v7a (32 位进程) 后装回 arm64-v8a; G8441 universal; API 35 16 KB 模拟器 x86_64 从 v1.1.4 覆盖升级 (pageSize 16384, 启动无崩溃) 及全新安装. 真机均以 `adb install -r` 覆盖, 保留数据.

#### P6 - 1.5.0 Binder: 输入框内实时听写, 无弹层对话, 常驻监听

- [ ] **P6-0** 前置: 宿主 6.8.0 已发布且 `speech-api` 契约 v1 冻结; 3-Macaw Speech 1.0.0 已发布; D31 的常驻监听互斥约定已写入 3-Stove Agent 与 3-Macaw Speech 的路线图并经三方确认 (Macaw 已采纳, `bf19529`; Stove 由父会话另行统筹). 开工前复核 Macaw D16 / D21 / D22 与本节 V6 / V9 是否仍成立.
- [ ] **P6-1** 契约接入 (D26): `speech-api.aar` 暂存与校验和; `MacawSpeechClient` (绑定, 能力协商, `linkToDeath`, 退回 P5).
  - 验收: 能力协商与退回规则单测; instrumentation 覆盖真实 Macaw, Macaw 未装, 宿主未装三种退回路径.
- [ ] **P6-2** 输入框内实时听写: Macaw 可用且偏好开启时短按麦克风不再弹层, `openSession` (VAD 开, `silenceEndMillis` 1200 ms), partial / final 写入输入框 (D26), 再按麦克风为 `finish`, 离开界面为 `cancel`.
  - 验收: 真机 API 31 / 33 / 35 各一台, partial 首字延迟与 CER 记录; `PERMISSION_DENIED` 与 `SESSION_BUSY` 两条引导由维护者在手机上确认.
- [ ] **P6-3** 无弹层语音对话: P5-4 的状态机换用会话听写.
  - 验收: 真机连续 10 轮, 对照 P5-4 的数据.
- [ ] **P6-4** 常驻监听 (D15, D27, D28, D30, D31, D32, D39): 设置三行, 另加 "监听时长" (10 / 30 / 60 分钟后自动关闭, 或一直开着) 与 "只在充电时监听" (D39); 互斥组件与查询, "需要先关闭 xxx" 提示与 "打开 xxx 设置"; `specialUse` 常驻服务持有 `openWakeSession`; 关键词编辑 (至多 5 条, 显示 Macaw 返回的拼写 warning); 灵敏度 3 档实测定值并回填 D15; 打开应用即恢复, 重启后的恢复通知; 与聊天界面听写的暂停 / 恢复.
  - 验收: 互斥查询与提示规则单测 (无其他监听, 1 个, 多个, 对方无 settings meta-data); instrumentation 以测试包内的假监听组件核对提示; 真机 API 31 / 33 / 35 唤醒命中率 (每个默认关键词 20 次), 熄屏与 30 分钟静置的误唤醒次数与耗电, 进程被杀与重启后的恢复路径, 记录 `docs/dev/resident-listening-smoke.md`; 权限对话框与悬浮窗授权由维护者在手机上操作.
- [ ] **P6-5** 唤醒后的对话 (D29, D33): 有悬浮窗权限时打开聊天界面进入语音对话模式; 没有时在常驻服务内后台对话 (服务可用的会话协调层, 通知状态与动作, 5 分钟内续用会话).
  - 验收: 会话协调层与续用规则单测; 真机两条路径各连续 5 轮 (含锁屏下的后台对话), 记录在同一文档.
- [ ] **P6-6** 常驻监听与 Macaw 数字助理并存 (D35; 原 "Macaw 指派入口" 已由 D34 取消): Macaw D57 在其助理, `RECOGNIZE_SPEECH` 弹层, `RecognitionService` 与试听试说页收音时临时接管 Stone 的唤醒会话 (保持打开, 静默, 结束后自动继续), 对 Stone 透明, Stone 侧不重开会话也不发通知; 本条只做联调验证, 以及确认静默期间常驻通知状态不变.
  - 验收: 真机上常驻监听开启时 (a) 长按电源键听写并点 "3-Stone AI", (b) 在聊天页短按麦克风经 Macaw 弹层听写 (P5 路径), 两者都正常完成, 之后唤醒词仍能命中且唤醒会话未结束; 另测唤醒命中后已转入听写的阶段触发助理时得到 Macaw 的占用引导. 记录在 `docs/dev/resident-listening-smoke.md`.
- [ ] **P6-7** 文档与发布: README (常驻监听: 互斥, 两条常驻通知, 耗电, 重启后需打开一次, 常驻期间脚本听写占用, 其他应用录音时听不到, ColorOS / MIUI 的自启动与电池白名单) 与 `plugin_instruction` 的脚本组合示例 (D21), changelog 1.5.0, 发布流程同 P5-6.

### 10.6 阶段门槛与风险

每个条目合入前须满足: (a) 新增规则为无 Android 依赖的纯策略对象并有 JVM 单测; (b) 真机证据记入 `docs/dev/` 或条目后 (设备型号 / API / 引擎与模型 ID / 数值); (c) P5 不新增任何权限与契约依赖, P6 只作为 `speech-api` 的调用方, 新增权限仅限 D32 所列; (d) 失败路径显式测试 (无识别应用, 取消, 无语音, TTS 初始化失败, 语言不支持, 音频焦点丢失, Binder 退回). 手机上需要人工确认的对话框 (系统选择器, 权限) 由维护者操作.

| 风险 | 缓解 |
|---|---|
| 设备有多个识别应用, 每次弹系统选择器 | D12 默认 `setPackage` 固定到 Macaw; 关掉开关则交给系统 |
| 低端机 (G8441) 上 MeloTTS / Kokoro 慢于实时, 句间停顿 | README 建议低端机在 3-Macaw Speech 中选 AISHELL-3 / Piper / Matcha 作为默认语音 (V5); 3-Stone AI 不做预合成缓冲 |
| 1.4.0 语音对话每轮弹出识别弹层, 体验割裂 | 1.5.0 改为无弹层 (D20) |
| 朗读声音被下一轮听写收进去 | 读完 (`onDone`) 后才开始听写, 不做 barge-in |
| Macaw 已装但未下载识别模型 | 由 Macaw 弹层引导安装模型 (Macaw D47), 3-Stone AI 不自动改用其他识别应用 (同 Stove D59), 设置说明写明 |
| `speech-api` v1 在宿主 6.8.0 发布前变化 | P6 等宿主发布后开工 (D20), P6-0 复核 |
| 宿主未安装时 PLUGIN 权限不存在, 无法绑定 Macaw | P6 退回系统接口 (D26); 常驻监听开关不可用并说明 |
| 三个应用对互斥约定的实现不一致 | D31 写死 action / meta-data / 组件状态规则, 三方路线图引用同一条, P6-0 要求三方确认; 不一致时 Macaw `SESSION_BUSY` 兜底 |
| 常驻监听耗电与两条常驻通知 | 默认关, README 写明; P6-4 记录熄屏 30 分钟耗电 |
| 后台语音对话要把聊天逻辑搬进服务, 改动面大 | P6-5 先抽出会话协调层并保持 `ChatActivity` 行为不变, 单独提交 |
| 其他应用录音时常驻监听只收到静音 (V9), 唤醒会话不报错却听不到 | 设置说明与 README 写明; P6-4 实测通话与录音机期间的行为 |
| ColorOS / MIUI 杀掉常驻服务 | 设置说明引导开启自启动与电池白名单; P6-4 在 OnePlus 63751f63 与 Xiaomi Pad 968e9f18 实测 |
| `VOICE_COMMAND` 入口被其他应用滥用, 消耗模型额度 | D37: 总是可见, 新建对话, 10 s 限速; 调用方必须在前台才能启动界面 (Android 10+) |
| 常驻唤醒与 Macaw 助理 / 弹层争用唯一的麦克风 | Macaw D57 临时接管并静默唤醒会话, 结束后自动继续, 对 Stone 透明; Stone 自己的 Binder 听写不在其范围, 按 D32 先暂停再重开; P6-6 联调 |
| 回退 | P5 / P6 各条目独立提交; 语音入口全部由可解析判定控制, 关闭 "优先使用 3-Macaw Speech" 即回到系统默认行为 |


## 2026-10-22 Runtime AI core

Node.js, Bun and Worker use the existing native provider through a separate entry for independently signed applications. The normal AI_RUNTIME entry permission is supplemented by explicit per-app consent bound to package, UID, signer and installation. Settings can revoke access and interrupt active requests. Both entries share provider admission. Service destruction takes over queued session cleanup exactly once before discarding executor work. The original host signature entry remains. Exact source, device/package acceptance and restoration are recorded in the host docs/dev/runtime-ai-core-2026-10-22.md report. A single successor may wait while a terminal session releases its native backend; its original timeout and cancellation remain active, and concurrent inference stays rejected. AI Agent runtime parity remains separate work.

## 2026-11-02 Anthropic 输出上限与截断标注

维护者在 XQ-DQ72 上用 Anthropic 官方接口聊天时回复提前结束. 根因 (代码核实): Anthropic 适配器在 "最大输出 token" 不限制时固定发送 max_tokens=1024 (OpenAI 兼容与 Gemini 适配器则省略该字段), 且 message_delta 的 stop_reason=max_tokens 被当作正常结束; 当前 Claude 模型默认开启自适应思考并计入 max_tokens, 所以普通问答也会在约 1024 个 token 处被静默截断, 思考占满额度时则报 "响应无效".

决策:

1. Anthropic 默认 max_tokens 改为 16384: 请求始终流式, 该值与 64 KiB 传输输出上限相称; 不直接取 128K 模型上限, 以免自定义 base URL 后面的小模型返回 400. 用户仍可在生成设置里填写明确上限.
2. 三种协议的输出上限结束 (Anthropic max_tokens / model_context_window_exceeded, OpenAI finish_reason=length, Gemini MAX_TOKENS) 统一作为 truncated 经 GenerationListener.onCompleted(statistics, truncated) 上报; 聊天页在消息下方标注 "已达输出上限" 并弹出提示, 标注随会话历史持久化 (历史格式 v5, 仍可读取 1.4.x 写出的 v4).
3. 连接测试的 max_tokens 从 8 提高到 1024, 避免默认开启思考的模型把额度全部用于思考而被判为响应无效.

未改, 待维护者决定:

- Binder 路径 (宿主脚本) 仍只在字节上限处报 AiFinishReason.LENGTH, 提供方 max_tokens 截断暂不改写 finishReason: 宿主对 LENGTH 会关闭持久会话, 改动影响 3-Stove Agent 等调用方. 已决 (2026-11-03, D76): 1.6.0 维持现状, 文档注明.
- 自定义采样参数 (temperature / top_k / top_p) 在 Claude 4.7+ 上返回 400, 需要与 effort 选项一起设计. 已决 (2026-11-03, D75): Anthropic 协议不再发送采样参数, 随 1.6.0 P2 实施.
- effort / fast 模式 / 文件附件 / 设置页重组 / 生成跨越 Activity 重建等需求另行立项. 已立项 (2026-11-03), 见下一节.

补充 (同日): 维护者随后提出两项要求, 已随 1.5.0 一并落地.

4. 云端失败要让用户看到提供方的原话 (额度耗尽时此前只显示 "提供方拒绝了请求或当前不可用"). OnlineAiFailureException 新增 providerDetail: HTTP 错误体 (Anthropic / OpenAI / Gemini 的 error 信封或纯文本, 最多读 64 KiB, HTML 丢弃), 流内 error 事件, Anthropic refusal (含 stop_details) 与 Gemini blockReason / finishReason 都会填入; 文本折叠空白, 去控制字符, 截到 300 字符, 遮盖形似密钥的片段, 执行层再用请求凭据逐字节遮盖. message / toString 保持原样, 日志不含提供方文本. 聊天失败信息与连接测试对话框在类别下方追加 "提供方返回: HTTP <状态码>: <说明>". Binder 路径 (AiError 没有提供方文本字段) 未改.
5. 生成期间聊天窗口持有 FLAG_KEEP_SCREEN_ON (isGenerating 的 setter 统一加减), 避免长回复被熄屏超时打断.

真机证据 (XQ-DQ72, 调试版 build 302 覆盖安装在 1.4.1 之上, .python/device_chat_probe.py): Anthropic 资料 (额度耗尽) 发送 5 秒后失败并显示 "HTTP 400: invalid_request_error: Your credit balance is too low ..."; Codex 资料 93 秒完整回复 (输出 2606 token), 生成中窗口 fl 含 KEEP_SCREEN_ON, 结束后释放.

## 2026-11-03 1.6.0 路线图: effort / fast, 附件, 思考卡片, 设置页重组, 生成脱离 Activity, 分支与导出

### 1. 背景与需求来源

维护者 2026-11-02 在 XQ-DQ72 上使用聊天页后提出六项需求: (1) 文件选择 (图片 / 文本等); (2) effort (Default / Low / Medium / High / Xhigh / Max); (3) fast 模式与 Ultracode; (4) 参考 RikkaHub (https://github.com/rikkahub/rikkahub) 补充功能与布局; (5) 重新设计设置页 (用户反馈经常迷路, 有的目标出现两次); (6) 生成过程中重建 Activity (如旋转) 会中止生成. 截断, 错误详情与屏幕常亮三项已随 1.5.0 修复 (见上一节).

Ultracode 是 Claude Code 工具自身的多代理编排设置, 不是 Anthropic API 参数, 插件无法实现; API 侧对应的只有 fast 模式, 故需求 3 按 fast 处理.

需求细化经三轮选择题 (2026-11-02 23:5x - 11-03 00:1x) 完成, 结论记为 D68 - D83. 调研底稿: RikkaHub 功能与布局清单 (master, 2026-11-02 抓取), 本插件设置页盘点与聊天页生成生命周期盘点 (两份都以 1.4.1 源码为准), 要点已并入本节与附录 A, 底稿不入库. 2026-11-05 15:5x - 16:0x 维护者追加需求 (7) 重新设计 "模型" 页及其子页 (用户反馈经常迷路, 同一目标出现两次, 布局不整齐; 总分类为在线模型与本地模型, 两者的操作要有明确的相同点与不同点) 与 (8) 添加在线档案的底部表单下滑即收起, 输入全部丢失; 调度会话盘点现状 (从聊天页最深 6 层, 5 个入口, 13 类重复与术语问题; 底稿不入库) 后经三轮 11 题选择题确认, 全部为推荐项, 结论记为 D95 - D105 (D105 进 1.6.0 作 P8.0, 其余进 1.7.0 的 P5a / P5b, 顺序见 D102).

调度: 看板 D:/idea-projects/.claude/boards/3-stone.md (主题 3-stone), 由 dispatch 环境的调度会话按阶段派 Opus 5.5 子会话; 子会话登记的新决策从 D84 起, 待决从 Q12 起.

### 2. 已定决策 (D68 - D94)

| 编号 | 决策 | 内容 |
|---|---|---|
| D68 | 发布节奏 | 1.5.0 先发 (三项修复已验证); 六项新需求进 1.6.0 路线图, 允许按阶段分批发 1.6.x (切点见 Q11) |
| D69 | 调度与并行 | 看板 + 调度会话 + Opus 5.5 子会话. 本仓库是单模块应用且共用一个 checkout, 代码卡一律依次执行不并行 (并行编译会互相编到对方的半成品); P0 发布卡也先于 P1 单独执行, 避免发布提交与开发提交混在一个工作区 |
| D70 | 附件范围 (需求 1) | 图片 (jpeg / png / gif / webp, 单张 <= 5 MB, 每条消息 <= 20 张) + 文本类文件 (按 UTF-8 读入, <= 256 KB, 以 "文件: 名称" 信封注入提示, 二进制或超限提示并拒收) + PDF (Anthropic document 块 <= 32 MB / 100 页, Gemini inline_data <= 20 MB; OpenAI 兼容资料提示不支持 PDF). 本地 LiteRT 目标不支持附件 (入口禁用并提示). 音频 / 视频不做 |
| D71 | 附件持久化 | 文件复制到 filesDir/attachments/<会话 id>/<uuid>.<ext>; 历史记录附件元数据 (名称, MIME, 大小, 路径, 图片宽高) 与文件引用; 重开会话显示缩略图 / 文件名; 后续轮次在上下文预算内重新发送图片 / PDF (估算: 图片约 宽 x 高 / 750 token, PDF 约每页 2000 token); 删除会话时删目录; 单会话附件总量 <= 48 MB (与 MAXIMUM_IMAGE_REQUEST_BYTES 一致) |
| D72 | effort / fast 的位置 (需求 2 / 3) | 在线资料保存默认 effort (DEFAULT / LOW / MEDIUM / HIGH / XHIGH / MAX) 与 fast 开关; 聊天页可对当前会话临时覆盖, 覆盖随会话历史保存; 新会话从资料默认值开始 |
| D73 | effort 映射 | DEFAULT 不发任何字段 (模型自适应). Anthropic: output_config.effort = low / medium / high / xhigh / max (无需 beta 头). OpenAI 兼容: reasoning_effort = low / medium / high, XHIGH 与 MAX 发 xhigh. Gemini: thinkingConfig.thinkingLevel = low (LOW) / high (其余), 并 includeThoughts 按 D79. 实施前按各提供方当前文档复核, 不支持的值降到最近的支持值. Binder (宿主脚本) 路径不带 effort / fast, AIDL 不变 |
| D74 | fast 范围 | 所有 Anthropic 资料可开 fast; GA 模型 (claude-opus-5-5 系列) 只发 speed: "fast", 其他 Anthropic 模型自动附加 anthropic-beta: fast-mode-2026-02-01; 其他协议不显示 fast. 不支持的模型或代理返回 400 时由错误详情显示原因 |
| D75 | 采样参数 | Anthropic 协议一律不发送 temperature / top_k / top_p (Claude 4.7 及以后拒绝它们); 设置页采样小节注明 "Anthropic 模型忽略此项". 其他协议照旧 |
| D76 | Binder 截断 | 1.6.0 维持现状: 提供方输出上限截断仍报 STOP, 插件文档注明 "达到输出上限时文本不完整且不单独标记"; AIDL 不动 |
| D77 | 生成归属 (需求 6) | 进程级 ChatGenerationCoordinator (仿 ModelImportCoordinator): 生成线程, generationEpoch, 后端会话与目标, 流式缓冲与 32 ms 刷新, 上下文遥测, 摘要检查点, 进行中消息的持久化都移入协调器; ChatActivity 在 onStart 附着 (取回快照并重绘进行中的回复) 并在 onStop 分离; 旋转, 夜间模式, 语言切换, 字号切换都不中断; 停止 / 新建会话 / 切换会话 / 切换目标执行硬取消 (在独立线程调用 session.cancel(), 本地推理经 cancelProcess), 不再只是 epoch 软停止. 前台服务不做 (进程被系统杀掉仍会丢, 以后按需) |
| D78 | RikkaHub 采纳 | 只借鉴设计与数据形状, 不复制代码 (AGPL-3.0). 本期采纳: 模型切换器底部面板; 思考过程卡片; 消息分支; 导出 Markdown / 长图. 不做: MCP, 语音模式 (已有 3-Macaw Speech), 搜索服务, 工作区, Web 服务, 媒体生成, 完整 LaTeX / Mermaid 渲染, 翻译, 收藏 |
| D79 | 思考卡片默认 | 生成中展开并流式显示思考摘要, 结束后折叠为 "思考了 N 秒", 点按展开; 对话设置两个开关: 生成中显示思考内容 (默认开), 结束后自动折叠 (默认开); effort 默认 DEFAULT |
| D80 | 设置页结构 (需求 5) | 两级结构. 首页 = 外观 4 行 (语言, 深色模式, 主题色, 启动图标, 按共享规范顺序) + 5 个子页入口: 模型与服务 (并入现有 "模型" 与 "在线服务", 含默认在线目标与计费网络), 对话 (显示, 输入, 推理限制, 思考显示), 语音, 隐私与权限 (AI 访问应用), 更新与关于. 每个设置只出现一次: 删除 "关于" 里重复的 "版本历史" (保留在 "更新与关于"), 删除 "模型" 页重复的 "添加在线资料" (保留在在线服务页); "生成" 改名 "输出长度与采样". 聊天页溢出菜单与抽屉底部各只保留一个 "设置" 入口, "模型" 入口移到模型切换器面板底部的 "管理模型与服务". 宿主 "AI 服务设置" 动作仍落到设置首页, 并经 "模型与服务" 到达在线服务 (修复 docs/dev/p4-p5-host-plugin-settings-smoke.md 第 4 步). 修订 (2026-11-05 16:0x): 模型页与在线服务页合为一页两段的 "模型与服务" 页 (D95, 本行 "并入" 与 "删除模型页重复的添加在线资料" 两句由其取代), 宿主动作直接落到该页 (D103) |
| D81 | 历史格式 v6 可扩展属性块 | v6 在会话级与消息级各加一个 "键 -> 字节" 属性表 (键为短 ASCII, 未知键跳过, 单条 <= 64 KiB); effort / fast 覆盖, 思考文本与用时, 附件元数据, 分支信息都以键写入, 之后新增字段不再升版; 继续读 v4 / v5 |
| D82 | 消息分支模型 | 助手消息节点保存多个版本 (重新生成或编辑后重发产生新版本, 不再删除后续消息), 节点记录选中版本; 搜索, 导出, 上下文编译, 朗读, 用量累计都按选中分支; 删除版本只删该版本, 删除最后一个版本删除节点及其后续 |
| D83 | 导出 | Markdown 文件 (CreateDocument text/markdown, 可选含思考摘要, 附件写文件名) 与长图 (离屏渲染现有消息视图为 PNG, 经 FileProvider 分享; 高度超过 16384 px 时按比例缩放), 入口在会话菜单 |
| D84 | 进行中回复的持久化归属 (P1, 2026-11-05) | 聊天页附着时由 ChatActivity 按原有路径保存; 分离 (onStop 之后) 时由协调器直接写历史库 (upsert, reviveDeleted = false, 已删除的会话不复活), 再附着时 attach 快照带回 changedConversationIds, 聊天页据此重载. 进程被杀时仍按 restore 把残留的 GENERATING 改为 STOPPED |
| D85 | 单一观察者 (P1) | 协调器同一时刻只服务一个附着的聊天页: attach 替换旧观察者, detach 只解除自己. 多窗口同时开两个聊天页时以后附着者为准, 前者回到前台时重新附着并拿快照 |
| D86 | 进程内只有一个生成 (P1) | 发送新一轮前, 协调器先以 reason = superseded 硬取消正在生成的回复 (包括其他会话在后台的回复), 被取代的回复按 STOPPED 保存 |
| D87 | 硬取消的执行线程与诊断 (P1) | 根因: 旧的停止把 close 排在同一单线程执行器里阻塞的 call.execute() 之后, 界面只是忽略回调, HTTP 请求一直跑到结束并计费. 现在 cancelAndClose 在新的守护线程 three-stone-ai-chat-cancel 上调用; AiBackendSession 新增默认返回 null 的 cancellationDiagnostics(), OnlineAiSession 报告 httpCall = cancelled / not-cancelled / none 供日志取证. Binder / AIDL 不变 (D73 / D76) |
| D88 | Anthropic effort 支持表与降级 (P2, 2026-11-05) | 按 2026-11-05 的 Anthropic 官方文档复核 D73, 文档为准: output_config.effort 不需要 beta 头; 不支持 effort 的旧模型 (claude-3 系列, claude-opus-4-0 / 4-1, claude-sonnet-4-0 / 4-5, claude-haiku-4-5) 不发该字段; claude-opus-4-5 只有 low / medium / high; claude-opus-4-6, claude-sonnet-4-6, claude-mythos-preview 没有 xhigh; 其他模型五档全有. 模型 ID 先去掉代理前缀 (取最后一个 / 之后) 并转小写再按前缀匹配. 统一降级规则 (三协议共用): 取不高于所选档位的最高支持档位, 没有时取最低支持档位; 面板高亮与目标条徽标都显示降级后的档位. fast: claude-opus-5-5 系列只发 speed: "fast", 其他 Anthropic 模型加 anthropic-beta: fast-mode-2026-02-01 (与 D74 一致) |
| D89 | Gemini thinkingLevel 按模型 (P2) | 与 D73 原文 "low (LOW) / high (其余)" 不同, 以官方文档为准: thinkingConfig.thinkingLevel 发 low / medium / high (XHIGH / MAX 降为 high); gemini-3-pro-preview 只有 low / high (MEDIUM 降为 low); gemini-3.1-flash-lite-image 只有 high; gemini-1.x 与 gemini-2.0 不发 (它们用 thinkingBudget, 本期不做) |
| D90 | OpenAI 兼容 reasoning_effort (P2; 2026-11-05 14:2x 按 Q12 = A 修订) | 原文 "MAX 发 xhigh, 官方最高档是 xhigh" 有误, 改为按模型分档 (模型 ID 先去掉代理前缀取最后一个 / 之后并转小写, 再按前缀匹配), 统一降级规则同 D88; 不发 minimal / none. (1) gpt-5.6* 与 gpt-6* (gpt-6-astra / gpt-6-luna / gpt-6.1-sol / gpt-6.1-terra / gpt-5.6-terra 等) 五档 low - max, MAX 发 max. (2) gpt-5.5*, gpt-5.4*, gpt-5.3* (gpt-5.3-codex), gpt-5.2* (含 gpt-5.2-codex), gpt-5.1-codex-max 上限 xhigh. (3) gpt-5 / gpt-5-* (mini / nano / codex), gpt-5.1* (codex-max 除外), o1 / o3 / o4 系列上限 high. (4) 其他未知模型 (DeepSeek, 代理的非 OpenAI 模型) 维持原四档 low - xhigh, 不改既有行为. 出处 (2026-11-05 逐页核对): API 参考 https://developers.openai.com/api/docs/api-reference/chat/create (reasoning_effort 取值 none / minimal / low / medium / high / xhigh / max, 并非每个推理模型都支持全部值); 模型页 https://developers.openai.com/api/docs/models/<id>: gpt-6.1-sol 列 low - max, gpt-6-luna 与 gpt-5.6-terra 列 none - max, gpt-5.5 / gpt-5.4 / gpt-5.4-mini / gpt-5.2 列 none - xhigh, gpt-5.3-codex 与 gpt-5.2-codex 列 low - xhigh (这两个只走 Responses API), gpt-5.1 列 none - high, gpt-5 列 minimal - high; gpt-5-mini, gpt-5.1-codex, gpt-5.1-codex-max, o3, o4-mini 的模型页没有列出档位, 按 Azure 推理模型文档 (2026-10-30, xhigh 只在 GPT-6 / GPT-5.6 / GPT-5.5 / GPT-5.4 / gpt-5.1-codex-max) 推断: gpt-5.1-codex-max 上限 xhigh, 其余上限 high. 已知限制: Azure 经 Chat Completions 不接受 max (只在 Responses API), 自定义接入 Azure 的用户选 MAX 会收到 400, 1.6.0 不处理. 真机 (2026-11-05 15:1x, XQ-DQ72 调试包 312, AIGoCode (Codex) / gpt-6.1-sol, device_chat_probe.py --effort max --expect complete): 代理接受 max (无 4xx), 日志 effort=max fast=false 恰好 1 轮, 回复 17 s 完成, 用量行输出 38 与日志一致, PASS |
| D91 | 历史格式 v6 编码 (P2, D81 的落地) | 每个会话在上下文状态之后, 每条消息在目标之后各写一个属性表: 大端 Int 条目数 (0 - 64), 条目按键排序, 键为 [a-z][a-z0-9_.]* 且 <= 32 字节 (表内唯一, 重复或格式错误按损坏处理), 值为大端 Int 长度 (0 - 64 KiB) + 原始字节. 读取时跳过未知键和无法解析的已知键值 (例如将来新增的 effort 档位), 跳过的条目不回写. 会话键: effort = GenerationEffort.persistentId (ASCII), fast = 1 字节 (0 关 / 1 开); 键不存在表示用资料默认值. 消息键暂无. 继续读 v4 / v5. 规则同时写在 ConversationHistoryProperties.kt 注释里 |
| D92 | 空会话跟随新的默认目标 (P2.5, Q10) | 刷新目标目录时, 若当前会话还没有消息且仍停在旧的默认目标上, 改为新的默认目标 (例如在资料里把默认模型改成 claude-fable-5-1 后回到已打开的新会话); 用户在空会话里手动选过其他目标时保持不变; 有消息的会话不受影响 (ConversationTargetPolicy.afterCatalogRefresh) |
| D93 | 思考内容的请求与解析 (P3.1, Q9 结论, 2026-11-05) | 以官方文档为准. (1) Anthropic: Claude 5 及以后 (含 Mythos) 是自适应思考, thinking.display 只有 summarized / omitted 两个值, 默认值按模型而定, 5.x 默认 omitted (思考块只带签名, 没有文本); 要显示摘要必须发 thinking: {"type": "adaptive", "display": "summarized"}, 摘要与 omitted 按同样的思考 token 计费; output_config.effort 与 thinking 同属一个请求结构, 可以同时发送. 聊天页只对 Claude 5+ 与 Mythos 发该块 (OnlineAiEffortSupport.anthropicSummarizedThinking); Opus 4.6 - 4.8 / Sonnet 4.6 不发 (发了才开始思考, 见 Q14), Opus / Sonnet / Haiku 4.5 不发 (不接受 adaptive). 流式: content_block_start 的 thinking 块与 thinking_delta 是思考文本, signature_delta 与 redacted_thinking 忽略; 非流式收集 thinking 块. anthropic-version 保持 2023-06-01 (额度原因未真机验证). (2) OpenAI 兼容: 请求不变; 流式 delta 与非流式 message 的 reasoning_content (DeepSeek 等) 与 reasoning (OpenRouter 等) 两个字段都收, 都有时取非空的 reasoning_content. (3) Gemini: 同一个 thinkingConfig 带 thinkingLevel (D89) 与 includeThoughts: true (gemini- 开头且不是 1.x / 2.0), parts 里 thought: true 的文本是思考, 不计入回复. (4) 只有聊天页设 GenerationRequest.includeReasoning = true; Binder, 摘要检查点, 连接测试保持 false, AIDL 与脚本输出不含思考; 思考不计入输出上限. 出处: https://platform.claude.com/docs/en/build-with-claude/thinking , https://platform.claude.com/docs/en/build-with-claude/thinking-steering-and-cost , https://platform.claude.com/docs/en/api/messages/create (thinking.display 取值与默认值), https://platform.claude.com/docs/en/about-claude/models/overview (5.x 均为 Adaptive), https://aistudio.google.com/api-reference/generate-content (ThinkingConfig.includeThoughts, Part.thought), https://aistudio.google.com/docs/thinking (各模型支持的 thinking level) |
| D94 | 思考文本的保存与显示 (P3.2, 2026-11-05) | 助手消息的 v6 消息级属性键 (D91 的表, 写在 ConversationHistoryProperties.kt KDoc): reasoning = UTF-8 思考文本 (按码点截到 64 KiB), reasoning.ms = 思考用时毫秒 (大端 8 字节 Long, 长度不对或为负时只丢用时), reasoning.cut = 1 字节 1 (被截断); 只有助手消息读写, 用户消息上的同名键忽略, 不升版. 用时按段计: 第一个思考增量开始, 下一个正文增量或本轮结束时停止, 多段累加. 协调器 (D77) 在内存里缓冲思考文本 (上限 64 K 字符), 快照与完成 / 停止 / 失败结果都带思考, 旋转后照常续上, 分离时由协调器写入历史. 卡片 ReasoningCardView 只借鉴 RikkaHub 的设计, 未用其代码 (AGPL-3.0): 思考进行中标题 "思考中...", 有用时后 "思考了 N 秒" (四舍五入, 至少 1 秒), 点按标题切换, 生成中点按的选择保留到本轮结束; 对话设置 "生成中显示思考内容" 与 "结束后自动折叠" 默认开 (ChatUiSettings 键 show-reasoning-while-generating / collapse-reasoning-when-done, 设置页在 "跟随输出" 之后, P5 重组时并入 "对话" 子页), 从设置页返回时卡片立即按新开关刷新. 朗读, 搜索, 复制, 上下文编译与摘要只用正文, 不含思考; 导出 (D83) 的 "含思考摘要" 选项由 P7 读同一字段. |
| D95 | "模型与服务" 页布局 (需求 7, 2026-11-05 第一轮 1) | 一页两段, 取代 D80 中 "并入现有模型与在线服务" 与 "删除模型页重复的添加在线资料" 两句: 单一列表页 "模型与服务" (ModelManagerActivity 与 OnlineAiSettingsActivity 合为一个 Activity, 名称由子会话定), 顶部 "默认目标" 行 (D97) 与 "允许移动网络" 开关; "在线服务" 段 = 服务卡片 + "添加服务" 行; "本地模型" 段 = 模型卡片 (含下载中的卡片, D99) + "下载模型" (进 LiteRT-LM 目录页) 与 "导入模型文件" 两行; 两段卡片同一样式 (D98), 点按卡片进详情 (在线 = 服务详情页 D96, 本地 = 模型详情 D104). 从聊天页出发深度 3 层 (聊天 -> 模型与服务 -> 详情), 现为 6 层 (聊天 -> 模型 -> 在线服务 -> 档案编辑器 -> Base URL 历史 -> 管理历史). 入口按 D80: 设置首页 "模型与服务" 入口, 切换器面板底部 "管理模型与服务", 聊天页溢出菜单与抽屉只保留 "设置"; "没有可用目标" / "目标不可用" 对话框都跳到这一页 (在线的不再直达在线服务页). 未采用: 顶部 Tab 分在线 / 本地 (默认目标与全局开关无处放, 模型少时两个 Tab 都空); 保留两个独立子页 (深度不减) |
| D96 | 服务详情页 (第一轮 2) | 全屏 Activity 取代底部表单编辑器: 上半连接信息 (提供方 / 协议模板, 名称, HTTPS Base URL + 历史, API Key 状态与更新 / 清除, 默认推理强度, 快速模式 (仅 Anthropic), "测试连接" 按钮), 下半该服务的模型列表 (每行: 模型 ID, 标签 图片 / 推理, 首选标记; 行的更多菜单: 设为首选 / 图片输入开关 / 移除; 长按拖动排序, 顺序即 modelIds 顺序, 第一个仍对应裸目标 ID, Q13 不变) + "添加模型" 行 (预置模型按厂商分组可折叠 + 自定义多行输入, 一次可加多个, 上限仍 32). 原编辑器的 "模型 ID / 默认模型 / 支持图片输入的模型" 三个选择器 (同一组模型列三次) 合为这一张列表; 数据模型不变 (modelIds / modelId / visionModelIds, schema 5). 新建服务 = 同一页的空表单 (保存前不写入 registry). 连接信息字段改动后顶部 "保存" 可用, 离开时有未保存改动弹 "放弃更改?" 确认, 旋转 / 进程被杀由 onSaveInstanceState 恢复草稿 (API Key 只在内存态与 Bundle, 不持久化). 未采用: 保留底部表单只合并选择器 (层级与排序问题保留); 模型为一级, 服务只作标签 (服务级操作难找, 与裸目标 ID 语义不对齐) |
| D97 | 单一全局默认目标 (第一轮 3) | 只保留一个 "默认目标" (新会话与脚本未指定目标时用), 可指向任一在线模型或本地模型, 存于已有的 AiTargetCatalog.defaultTargetId; 设置处: "模型与服务" 页顶部一行 (弹出与切换器相同的目标列表) 与卡片 / 模型行更多菜单的 "设为默认"; 卡片上只保留一个 "默认" 徽章. 删除 "默认在线目标" 行与 设为 / 清除默认 菜单项, 删除本地模型 "点按选中" 与勾; 迁移: 有 defaultProfileId 则取该服务首选模型的 targetId, 否则取 local:<selectedModelId>, 两者都无则空 (沿用现有在线优先的自动选择); OnlineAiProfileDocument.defaultProfileId 与 ModelCatalogDocument.selectedModelId 的去留由子会话按 CompositeAiBackend / LiteRtLocalBackend 的实际依赖决定 (保留为派生字段或删除, 记决策). 服务的 modelId 界面上改叫 "首选模型", 只在服务详情页显示 (裸目标 ID 与宿主脚本只给服务不给模型时用, Q13 不变); 卡片上不再显示 "默认: 是 / 否" 与 "(默认)". 未采用: 保留三层只改文案 (本地选中了为什么新会话还是在线的困惑保留); 在线 / 本地各一个默认 (多一个上次类别状态, 脚本 API 另定规则) |
| D98 | 统一卡片与操作菜单 (第一轮 4, 第三轮 2) | 两类卡片同一结构: 标题 (服务名 / 模型显示名), 副标题 (在线: 提供方 + N 个模型; 本地: 体积 + 运行时状态), 状态徽章 (已就绪 / 缺 API Key / 不可用 / 下载中 / 默认); 更多菜单固定顺序: 设为默认, 测试 (在线 = 测试连接, 本地 = 检查模型), 重命名, [仅在线] 克隆, 清除 API Key, 删除. 段级更多菜单在段标题右侧: 在线段 = 导入服务 / 导出服务 / 自动更新预置模型 / 刷新预置模型; 本地段 = 导入模型文件 / 清理未引用文件 (P4.3 后含附件目录). 页面不再有溢出菜单. 不加 "启用" 或 "在切换器中显示" 开关: 可用性只用状态徽章表达, 切换器只列已就绪目标; 不想用的服务清除 Key 或删除, 不想用的模型从服务的模型列表移除 (第三轮 2). 未采用: 只统一样式; 每服务 / 每模型一个开关 (多一个概念, schema 6 与 catalog.json 加字段) |
| D99 | 本地下载一步 (第一轮 4, 第三轮 3) | LiteRT-LM 目录页点 "下载" 直接下载到 filesDir/models 并在校验后自动导入, 不再选 SAF 位置; 下载中的模型以卡片出现在 "本地模型" 段 (进度, 暂停 / 取消, 失败可重试), 完成后变普通模型卡片; 卡片副标题显示占用体积 (清除应用数据会连带删除, README 注明). 删除 "选择保存位置" 与 "导入已下载模型" 两条路径及其偏好记录; 保留 "导入模型文件" (SAF 选 .litertlm) 给自备文件与以前下到 SAF 位置的文件, changelog 写明. 目录页的 "已导入" 徽章保留. 未采用: 保留一版兼容行; 保留 SAF 两步 |
| D100 | 术语: 服务 / 模型 (第二轮 1) | 在线段标题 "在线服务", 每个 profile 叫 "服务" (添加服务, 服务详情, 导入 / 导出服务, 测试连接), 服务下的条目叫 "模型"; 本地段标题 "本地模型", 切换器的 "本机" 改 "本地模型", 分组标题用服务名; 去掉 "在线档案 / 配置档案 / 在线模型 (段标题) / 在线 AI" 等叫法. 英文 service / model, 10 种语言同步 (约 40 条文案), README 与 changelog 以后用 "服务" (本文件旧文中的 "资料 / 档案" 不回改); 代码标识 profile 不改. 与宿主动作名 "AI 服务设置" 和子页名 "模型与服务" 一致. 未采用: 档案 / 模型 (对普通用户不直观); 提供方 / 模型 (与编辑器的协议模板字段 "提供方" 冲突) |
| D101 | 切换器面板去重 (第二轮 2) | 每个目标只出现一次: 已收藏的只在顶部 "收藏" 组 (行带服务名副标题与实心星, 点星取消收藏即回原组), 原组隐藏已收藏的; 搜索时不分组, 每行带服务名. 所有在线行副标题 = 服务名, 本地行副标题 = 体积; "本机" 改 "本地模型" (D100). 改 ModelPickerPolicy.sections 与单测. 未采用: 不设收藏组只在组内置顶 (跨服务常用模型不再集中); 保持两处显示 |
| D102 | 1.7.0 顺序: P5 提前为首卡并拆两张 (第二轮 3) | P8 -> P5a -> P5b -> P4 -> P6 -> P7 -> P9. 理由: 迷路是现有用户的投诉; P4 若新增设置项直接落在新结构, 不用改两次. P5a = 设置两级结构 (D80) + "模型与服务" 页框架 + 单一默认目标 (D95, D97, D98, D100, D103, D104); P5b = 服务详情页 + 本地下载一步 + 切换器去重 + 测试与文档 (D96, D99, D101). 1.7.0 的 changelog 条目由 P5a 卡新建 (原定 P4 卡). 估算: P5 由约 2 天变为约 6 天. 未采用: 保持 P4 先; 模型页重设计单独发 1.6.1 |
| D103 | 宿主动作落点 (第二轮 4) | org.autojs.plugin.AI_PROVIDER_SETTINGS 直接打开 "模型与服务" 页 (在线服务段在顶部, 不需要滚动), 返回键回到设置首页 (合成返回栈), 再返回回宿主; 取代 D80 中 "仍落到设置首页, 并经模型与服务到达在线服务" 一句; docs/dev/p4-p5-host-plugin-settings-smoke.md 第 4 步改为核对落点 (该步目前已失效: 设置首页没有模型入口). 未采用: 维持落到设置首页 |
| D104 | 本地模型详情与段内说明 (调度会话按第一轮结论补充, 2026-11-05) | 本地模型卡片点按打开详情 (显示名, 模型 ID + 复制, 文件大小, 健康状态与最近检查时间, 来源), 代替现在 "当前模型详情块 + 带勾卡片" 的两处显示; 原 "模型" 页的 screen_description (讲导入却放在下载段) 删除, 段内说明各放各段; "预置模型" 一词只用于在线段的更新设置, 服务详情页的选择列表叫 "可选模型". 不新增数据字段 |
| D105 | 1.6.0 修复: 编辑表单防丢输入 (需求 8, 第三轮 1) | 进入 1.6.0 (P8.0): 添加 / 编辑服务的底部表单禁止下滑收起与点外部关闭 (BottomSheetBehavior.isDraggable = false, setCancelable(false), 右上角关闭按钮); 关闭按钮 / 返回键在有未保存改动时弹 "放弃更改?" 确认 (10 种语言); 旋转 / 进程被杀后表单内容由 onSaveInstanceState 恢复 (含未保存的 API Key, 只在内存态与 Bundle, 不写持久化); 仪器测试 1 项 (输入后旋转内容保留, 下滑不收起). changelog 加一条 Fix (10 种语言). 由 P8 子会话在 P8.1 之后, Anthropic 补测之前单独提交, 不改数据模型. 1.7.0 的服务详情页 (D96) 取代这张表单后随之移除. 未采用: 只禁止收起不做恢复 (旋转仍丢); 等 1.7.0 (用户再受影响几周) |
| D106 | 1.6.1 修复版先于 P5a (F26 / F37, Q18) | 维护者 2026-11-12 22:2x 选 A: 在 P8 与 P5a 之间插入 P10 小卡并发布 1.6.1 (约 1 天, P5a 顺延). 范围: 结构化输出收尾, 即模型返回的 JSON 自带 Markdown 围栏或首尾说明文字时在解析前剥离, 在线三协议与本地 LiteRT-LM 共用一个纯 JVM 策略并加单测; 不改请求体与 Binder 契约. 子会话先定位报错所在层 (插件结构化输出路径, 工具调用参数解析, 脚本 API / Binder 返回值, 或宿主侧调用方), 落在本仓库以外时登记待决, 不改其他仓库. 来源: feedback-680 转入的 F26 (智谱 (GLM) 输出的 JSON 带围栏, 任务收尾解析报错) 与 F37 (本地模型写 JSON 不稳定). 未选: 进 1.7.0 排在 P5a 前不单独发版 (用户多等 1 - 2 周) / 1.7.0 之后再排. |
| D107 | 下载本地模型前显示内存要求 (F37 第二项, Q19) | 维护者 2026-11-12 22:2x 选 A: 并入 P5b 的 P5.5 下载一步, 在下载确认处显示该模型的内存要求, 设备内存低于要求时警告 (仍允许下载); 约 +0.5 天, 随 1.7.0. 未选: 随 1.6.1 先在现有模型目录页做 (P5b 重做该页, 约半天界面工作会返工) / 暂不做. |
| D108 | F25 本地模型 "断网更快" 只读排查 (Q20) | 维护者 2026-11-12 22:2x 选 A: 随 P10 做一次只读排查, 约 2 小时: 本地推理路径在生成前后有无联网动作 (目录刷新, 下载校验, 记忆召回, 更新检查), XQ-DQ72 上同一本地模型对比有网与飞行模式的首字时间 (各 3 轮). 有代码原因登记待决由维护者定是否修, 没有就记结论并关闭 F25. 未选: 先不查等更多信息 / 单独发排查卡. |
| D109 | 结构化 JSON 收尾剥离只用于摘要检查点, 不单发 1.6.1 (Q21) | 维护者 2026-11-12 23:0x 选 A (P10.1 定位 F26 在 3-Stove Agent 之后). 新增纯 JVM 的 StructuredJsonText.extract(text, shape): 先找行首的 Markdown 围栏 (``` 或 ~~~, 可带语言标记; 未闭合时取到结尾), 在围栏内 (没有围栏时在全文) 按出现顺序找期望形状 (对象或数组) 的起点, 按字符串与转义感知的括号配对, 取第一个完整且能被严格 JsonReader 解析的值; 都没有时返回 null, 调用方按原文报原来的错误. 只在 SummaryCheckpointJsonCodec.decode 使用 (聊天页摘要检查点对在线三协议与本地 LiteRT-LM 共用), 取出的 JSON 仍走原来的严格校验 (精确键集, 整数 ID, 条数上限). 不用于 Binder 回复: 插件原样转交文本, 宿主按流式分片校验输出字节. 不改请求体与 Binder 契约. 修复随 1.7.0 发布, 10 语言 changelog 的 v1.7.0 条目由 P10 新建 (released_date 为占位, P9 发布日改为实际日期), P5a 起各卡继续写入. 未选: 照常发 1.6.1 (F26 的群友升级后看不到改善) / 只记结论不改代码. |

### 3. 阶段条目

每个条目一张卡片, 验收以写入本节的证据为准. 勾选用 `[x]`.

P0 发布 1.5.0

- [x] P0.1 十个 .changelog/lang_*.json 的 v1.5.0 released_date 改为实际发布日 (当前是占位 2026/10/22), 重新生成文档, 单独提交.
- [x] P0.2 按既有发布流程: 推送 master, tag v1.5.0, 从 tag 的干净克隆构建签名 release (appendDigestToReleasedFiles), GitHub Release, 登记官方索引; 发布前正式包在 XQ-DQ72 覆盖安装冒烟 (Codex 资料: 完整回复 + 用量行; Anthropic 资料额度耗尽时跳过).
- 验收: Release 资产与索引 hash 记入本节; 看板记录 tag 与 versionCode. 证据 (2026-11-05 补记): tag v1.5.0 = d3db0bf, versionCode 305, GitHub Release 2026-11-04 06:27 UTC (维护者手动完成), 资产 5 个 ABI APK + SHA256SUMS, 官方索引 release-manifests/io.github.supermonster003.autojs6.plugin.threestoneai/305.json (索引提交 8f8c679); released_date 已改为 2026/11/04 并重新生成文档 (P0.1 提交). XQ-DQ72 正式包冒烟由维护者自行完成, 未留证据.

P1 生成协调器与硬取消 (需求 6, D77)

- [x] P1.1 ChatGenerationCoordinator 进程单例: 从 ChatActivity 迁出生成线程, epoch, 后端会话, 流式缓冲, 上下文遥测, 摘要检查点与进行中消息的持久化; 观察者接口 (onDelta / onCompleted / onFailed / onStateChanged), attach 返回快照.
- [x] P1.2 ChatActivity 改为 onStart 附着 / onStop 分离; onDestroy 不再 bump epoch; ChatConversationPolicy.restore 只在协调器不持有该会话时把 GENERATING 改为 STOPPED; 屏幕常亮标志跟随协调器状态.
- [x] P1.3 硬取消: OnlineAiSession.cancel() / LiteRtLocalSession.cancelProcess 在独立线程调用; 停止, 新建会话, 切换会话, 切换目标, 删除消息都走硬取消; 停止后 1 s 内 OkHttp call 已取消 (单测 + 设备 logcat).
- 证据 (P1.1 - P1.3, 2026-11-05): 新文件 ChatGenerationCoordinator.kt (约 1220 行), ChatActivity.kt 从 4644 行降到 3868 行. 协调器用注入的 Store / Scheduler / Logger, JVM 单测不依赖 Android 框架. 硬取消: ChatActivity 的停止, 语音 "停止", 新建会话 (new-conversation), 切换会话 (switch-conversation), 切换目标 (target-changed), 删除消息 (delete-messages), 编辑重发 (edit), 手动压缩 (manual-compaction) 都经 ChatGenerationCoordinator.stop / closeBackend, 在守护线程 three-stone-ai-chat-cancel 上调用 cancelAndClose (D87). 日志 (标签 ThreeStoneAiChat) 先写 "Generation cancel requested reason=<原因> target=<目标>", 取消完成后写 "Generation cancel complete reason=<原因> elapsedMillis=<毫秒> success=<true|false> httpCall=<cancelled|not-cancelled|none>". 单测证据: OnlineAiSessionTest.cancelAndCloseOnAnotherThreadCancelsTheBlockedCallWithinOneSecond (阻塞在 execute() 的 OkHttp call 在 1000 ms 内被取消, 诊断由 httpCall=none 变为 httpCall=cancelled); ChatGenerationCoordinatorTest "stop hard-cancels a blocked backend" (真实单线程执行器上阻塞的会话, stop 在 1 s 内返回, cancel 在 1 s 内运行在 three-stone-ai-chat-cancel 线程, 日志带 success=true). 新单测在迁移中发现并修复一个缺陷: 完成时把用量写进仍为 GENERATING 的消息, 违反 "只有完成的消息带用量" 的不变量, 每次完成都会抛 IllegalArgumentException.
- [x] P1.4 测试: 协调器状态机单测; ChatConversationPolicyTest 更新; 仪器测试 (ScriptedAiBackend) 在流式中 recreate() 后回复继续; device_chat_probe.py 增加 --rotate-during (发送后用 settings put system user_rotation 旋转再转回) 并在 XQ-DQ72 用 Codex 资料取证: 旋转 / 切夜间模式期间回复不中断, 全文与用量行正常.
- 证据 (P1.4, 2026-11-05): 单测 529 项全绿 (基线 517, 新增 ChatGenerationCoordinatorTest 10 项, ChatConversationPolicyTest 1 项, OnlineAiSessionTest 1 项; UnifiedAiBackendWiringTest 改为检查协调器). 仪器测试 ChatGenerationContinuityDeviceTest (ScriptedAiBackend, 3 项: 流式中 recreate() 后回复继续且用量正确, 重建后的页面停止仍能取消, 页面停在后台时完成的回复返回时显示) 在 XQ-DQ72 通过 (OK (3 tests)). device_chat_probe.py 新增 --rotate-during (accelerometer_rotation / user_rotation 旋转再转回, finally 恢复), --night-during (cmd uimode night 切换再恢复), --disturb-after, --stop-after 与 --expect stopped; 旋转 / 夜间检查要求重建后仍在生成, 日志里没有 "Generation cancel requested", 恰好一次 "Context complete" 且 actualOutputTokens 与用量行输出数一致; 停止检查要求 "Generation cancel complete reason=stopped" 的 elapsedMillis <= 1000, success=true, httpCall=cancelled. 真机 XQ-DQ72 (QV770340J7, 调试包 VERSION_BUILD 308, Codex 资料 AIGoCode (Codex) / gpt-6.1-sol, 2026-11-05 12:4x - 12:5x): (1) --rotate-during --night-during --check-keep-screen-on --expect complete, 提示约 1500 词: 横屏, 转回, 夜间 no, 夜间 yes 四次重建 (事件日志 wm_relaunch ChatActivity 4 次) 分别在发送后 6.4 / 9.5 / 13.3 / 16.5 s, 每次重建后仍在生成; 回复 83.8 s 正常完成, 用量行 输入 4239 / 输出 1962, 日志 actualOutputTokens=1962 一致, 0 次 "Generation cancel requested", 恰好 1 次 Context complete; 屏幕常亮生成中 true / 结束后 false; 正文 1541 词 99 句, 无重复句 (排除快照与增量重复拼接); 旋转与夜间设置已恢复 (0 / 0 / yes). (2) --stop-after 8 --expect stopped: 8.2 s 点停止, 回复停在中途 (状态 "已停止生成"), 日志 "Generation cancel complete reason=stopped elapsedMillis=1 success=true httpCall=cancelled", 屏幕常亮随之释放. 探针修正 (取证中发现): device_time 的 date 格式被 adb shell 按空格拆开, 读日志一行都读不到; 回复流式输出时 uiautomator dump 拿不到空闲状态 (约 11 s 后报错), 旧版会读回上一次的 xml, "仍在生成" 改为读 FLAG_KEEP_SCREEN_ON, 每次 dump 前删旧文件; 发送后键盘收起使输入框下移, --stop-after 先收起键盘再取 Send 坐标. Gboard 需为英文布局 (拼音布局会改写提示词, 探针核对不一致时不发送).
- 依赖: P0 完成. 影响: ChatActivity.kt (约 4600 行) 大幅瘦身, 新文件 ChatGenerationCoordinator.kt.

P2 effort / fast 与模型切换器面板 (需求 2 / 3, D72 - D75, D78 第 1 项)

- [x] P2.1 OnlineAiProfile 新增 defaultEffort 与 fastMode (资料编辑器增加两个字段; 导入 / 导出 JSON 兼容旧文件); GenerationRequest 新增 effort / fast (Binder 路径为 null).
- [x] P2.2 三个协议适配器按 D73 / D74 写请求体与请求头; Anthropic 按 D75 不再发采样参数; 单测覆盖每个 effort 值与 fast 的 GA / 非 GA 分支.
- [x] P2.3 历史格式 v6 (D81) 与会话级 effort / fast 覆盖; 兼容读 v4 / v5 的单测.
- [x] P2.4 模型切换器底部面板: 搜索, 按资料 / 提供方分组, 收藏置顶, 行内标签 (图片输入 / 推理 / fast), 当前会话的 effort 选项行与 fast 开关 (只在协议支持时显示), 底部 "管理模型与服务"; 目标条显示 effort 与 fast 徽标.
- [x] P2.5 排查 Q10 (新会话默认目标).
- 证据 (P2.1 - P2.5, 2026-11-05): (1) 资料 JSON schema 5 新增 defaultEffort (default / low / medium / high / xhigh / max) 与 fastMode; schema 3 / 4 的旧文件缺字段时按 DEFAULT / false 读入, schema 5 两个字段必须同时存在且取值已知; 非 Anthropic 资料的 fastMode 归一化为 false. 资料编辑器新增 "默认 effort" 选择项与 "默认开启 fast 模式" 开关 (只对 Anthropic 显示). GenerationRequest 新增 effort / fast, 默认 null, Binder 路径不设置, AIDL 不变. 新文件 backend/GenerationEffort.kt (GenerationEffort 与 OnlineAiEffortSupport 支持表, D88 - D90). (2) Anthropic: output_config.effort (与结构化输出的 output_config.format 合并), fast 写 speed: "fast", claude-opus-5-5 系列不带 beta 头, 其他模型加 anthropic-beta: fast-mode-2026-02-01; 一律不发 temperature / top_k / top_p (D75), 设置页 "输出长度与采样" 加注 "Anthropic 模型忽略此项". OpenAI 兼容: reasoning_effort, MAX 发 xhigh (D90). Gemini: generationConfig.thinkingConfig.thinkingLevel (D89). DEFAULT 三个协议都不发字段. 单测 GenerationEffortRequestTest 11 项 (每个 effort 值 x 三协议, 降级, 无 effort 的旧模型, fast 的 GA / 非 GA / 关闭, Anthropic 不发采样参数, 支持表), AnthropicMessagesProtocolTest 改为断言不发采样参数, OnlineAiProfilePolicyTest 3 项. (3) 历史格式 v6 (D91): 会话级与消息级属性表, 写会话键 effort / fast, 未知键与无法解析的值跳过; ConversationHistoryTest 新增 5 项 (读已发布的 v5 布局, 每个 effort / fast 覆盖往返, 跳过未知键, 已知键与未知键并存, 拒绝损坏的属性表), 原有 v4 兼容单测保持通过. 聊天页覆盖随历史保存, 新会话从资料默认值开始 (ChatActivity 的保存状态键 chatEffortOverride / chatFastOverride 保证旋转不丢). (4) 新文件 ModelPicker.kt: 底部面板只借鉴 RikkaHub 的设计 (搜索, 收藏置顶, 分组, 行内标签, 推理档位行), 未使用其代码 (AGPL-3.0); 收藏只存本机 (SharedPreferences model_picker); effort 选项行在目标支持 effort 时显示, fast 开关只在 Anthropic 目标显示, 高亮显示降级后实际发送的档位; 底部 "管理模型与服务" 打开现有 "模型" 设置页; 目标条在 effort 不是 DEFAULT 时显示档位徽标, fast 开启时显示 Fast 徽标, 本地 LiteRT 目标两者都不显示. 旧的对话框选择器 (TargetChoiceAdapter) 与字符串 chat_target_selector_item 已删除. 单测 ModelPickerPolicyTest 6 项. (5) Q10 结论见第 5 节, 关联修复 D92, 单测 ConversationTargetTest 1 项. 单测合计 555 项全绿 (基线 529).
- [x] P2.6 真机: XQ-DQ72 上切换 effort / fast 后发送, 请求体经单测证实; Codex 资料用 reasoning_effort 发送一轮完整回复; Anthropic 资料额度恢复后补 fast 一轮.
- 证据 (P2.6, 2026-11-05): 请求体由 GenerationEffortRequestTest 证实 (见上). 协调器每轮先写日志 "Generation start target=<目标> effort=<降级后实际发送的档位> fast=<true|false>" 供真机取证. device_chat_probe.py 适配底部面板 (--model 点包含该文字的可点按行, 首屏没有时经面板搜索框过滤) 并新增 --effort (在面板里选档位, 关闭面板后核对目标条徽标, 回复结束后核对日志档位与用量). 真机 XQ-DQ72 (QV770340J7, 调试包 VERSION_BUILD 309, Gboard 英文布局经维护者确认, 2026-11-05 14:0x): --model gpt-6.1-sol --effort high --expect complete --check-keep-screen-on, 目标 AIGoCode (Codex) | gpt-6.1-sol, 目标条显示 "高" 徽标; 回复 12 s 正常完成, 用量行 输入 4404 / 输出 192, 日志 effort=high fast=false 恰好 1 轮, actualOutputTokens=192 与用量行一致; 屏幕常亮生成中 true / 结束后 false; PASS. 强制停止后冷启动, 聊天页恢复同一会话, 目标仍为 Codex, "高" 徽标仍在 (覆盖经 v6 历史恢复). Anthropic fast 一轮按卡片要求未发送 (未用 Anthropic 资料发任何请求), 看板登记 "[维护者待办] 待补 Anthropic 真机: P2.6 fast 一轮".
- 证据 (P2.6 Anthropic 补测, 2026-11-12 14:2x, 子会话 3-stone-p8-release-160): 维护者确认额度已恢复, XQ-DQ72 (QV770340J7, 经远程 adb 服务器) 覆盖安装调试包 318 (HEAD 2889c4e, 同签名 install -r, 数据保留). device_chat_probe.py 新增 --fast on / off (在面板里设置本会话 fast 开关并读回勾选状态, 回复结束后核对日志 fast 值). --model claude-opus-5-5 --effort low --fast on --expect complete: 目标 Anthropic | claude-opus-5-5, 开关勾选 true; 回复 "OK", 25 s 内完成, 用量行 输入 17 / 输出 4 / 3.2 秒, 日志 effort=low fast=true 恰好 1 轮, 输出 4 与日志一致. 请求带 speed: "fast" 且 claude-opus-5-5 走 GA 不带 beta 头, 未返回 400, 请求形状无需修正. 本轮与 P3 一轮的 --check-keep-screen-on 都报生成中 false: 远程 adb 每条命令往返约 4 s (shell true 3.95 / 4.43 s, dumpsys window 端到端约 5 s), 采样落在 2 - 6 s 之后, 晚于 1.9 - 3.2 s 的生成, 属取证时序问题; 屏幕常亮已于 2026-11-05 经本地 adb 证实 (见上), 未为此追加付费请求.
- 依赖: P1.

P3 思考过程卡片 (D78 第 2 项, D79)

- [x] P3.1 核实 Q9 后实现: Anthropic 思考摘要 (thinking 块 / display), OpenAI 兼容 reasoning_content / reasoning 流式字段, Gemini thought 部分; GenerationListener 新增 onReasoningDelta (默认空实现).
- [x] P3.2 ChatMessage 保存思考文本与用时 (v6 属性键); 卡片组件 (折叠 / 流式展开 / 展开), 两个对话设置开关; 朗读与搜索不包含思考文本.
- [x] P3.3 单测 (三协议解析, 持久化) + 真机 (Codex 资料若返回推理字段则取证, 否则 ScriptedAiBackend 仪器测试).
- 证据 (P3.1 - P3.3, 2026-11-05): Q9 按官方文档核实为 D93 (出处 URL 在 D93), 存储与界面为 D94; 新待决 Q14 / Q15 (都不阻塞, 见第 5 节). (1) 单测 572 项全绿 (P2 基线 555, 第 0 步 +2, P3 +15): ReasoningProtocolTest 8 项 (Anthropic 流式 thinking / signature / redacted 与 JSON thinking 块, Claude 5+ / Mythos 才发 adaptive + summarized 且与 output_config.effort 同发, 4.x 不发; OpenAI reasoning_content / reasoning 两种字段与优先级, 请求体不因 includeReasoning 改变; Gemini thinkingLevel 与 includeThoughts 合并, 2.0 不发, thought 部分进思考; Binder 请求默认不要思考), ConversationHistoryTest 2 项 (v6 reasoning / reasoning.ms / reasoning.cut 往返, 只在助手消息读, 坏长度只丢用时, 孤立用时忽略), ChatGenerationCoordinatorTest 3 项 (缓冲与分段计时在页面重建后续上, 无页面时完成写入历史并截断, 停止保留思考), OnlineAiSessionTest 1 项 (思考与正文分开且不进下一轮上下文), ChatUiSettingsTest 1 项 (两个开关默认开, 卡片展开策略与秒数取整). (2) 仪器测试 ChatReasoningCardDeviceTest (ScriptedAiBackend, 3 项: 流式时卡片展开显示 "思考中...", 完成后折叠为 "思考了 N 秒", 点按展开 / 收起, 历史里存有思考与用时, 重开后卡片折叠且可展开; 两个开关都关时生成中折叠, 完成后保持展开; 无思考的回复不显示卡片) 在 XQ-DQ72 通过 (OK (3 tests)); 回归 ChatGenerationContinuityDeviceTest OK (3 tests), SpeechReadingDeviceTest OK (5 tests). (3) 真机 XQ-DQ72 (QV770340J7, 调试包 312, Gboard 英文布局经维护者确认, 2026-11-05 15:1x): Codex 资料 AIGoCode (Codex) / gpt-6.1-sol 返回了思考字段, device_chat_probe.py --effort max --expect complete --expect-reasoning shown --check-keep-screen-on: 回复 17 s 完成, 卡片折叠为 "思考了 1 秒", 屏幕常亮生成中 true / 结束后 false, PASS; 强制停止后冷启动, 同一会话的卡片从历史恢复 (仍为 "思考了 1 秒"). 探针新增 --expect-reasoning shown / absent (只打印是否显示与秒数), 字符串改为读取该语言目录下全部 strings*.xml (原先只读两个文件, 非 zh / en 语言缺键). Anthropic 一轮因维护者额度跳过 (未用 Anthropic 资料发任何请求), 看板登记 "[维护者待办] 待补 Anthropic 真机: P3 思考卡片一轮".
- 证据 (P3 Anthropic 补测, 2026-11-12 14:3x, 子会话 3-stone-p8-release-160): 同上设备与调试包 318. --model claude-opus-5-5 --effort max --expect complete --expect-reasoning shown, 提问 "What is 37 times 43? Reply with the number only.": 回复 1591, 22 s 内完成, 用量行 输入 25 / 输出 37 / 1.9 秒, 卡片折叠为 "思考了 1 秒", 日志 effort=max fast=false 恰好 1 轮, 输出 37 与日志一致. 请求带 thinking {type: adaptive, display: summarized} 与 output_config.effort, 未返回 400, 返回了思考摘要, 请求形状无需修正. 另跑 claude-fable-5-2 --effort high 一轮: 回复 1591, 输出 4 / 3.8 秒, 日志 effort=high fast=false, 无卡片, 因为模型这次没有思考 (输出 4 token 就是答案), 不是缺陷. 共发 Anthropic 请求 3 次, 均被接受.
- 依赖: P2 (v6 与面板).

P4 附件 (需求 1, D70, D71)

- [ ] P4.1 选择入口 (输入框左侧 "+": 选择图片 PickVisualMedia 多选, 选择文件 OpenMultipleDocuments), 附件条 (缩略图 / 文件名 + 移除), 草稿与 onSaveInstanceState 保留待发附件; 不支持的目标禁用入口并提示.
- [ ] P4.2 存储与限额 (D71), 文本类注入, 图片与 PDF 按协议编码 (复用 GenerationImages / OnlineAiImages, 新增 document 块与 Gemini inline PDF), 上下文编译回放与 token 估算.
- [ ] P4.3 消息气泡显示附件 (缩略图点按用系统查看器打开, 文件芯片), 历史 v6 属性键, 删除会话删目录, "清理未引用文件" 覆盖附件目录.
- [ ] P4.4 单测 (编码, 限额, 回放, codec) + 真机 (XQ-DQ72: 图片发给标记图片输入的 Codex 模型; PDF 与 Anthropic 待额度恢复, 先以单测与 ScriptedAiBackend 仪器测试为证据).
- 依赖: P2.

P5 设置页重组与 "模型与服务" 页重设计 (需求 5 / 7, D80, D95 - D104; 2026-11-05 16:0x 按维护者三轮 11 题扩充, 拆为 P5a / P5b 两张顺序卡, 均为 1.7.0; 原 P5.1 - P5.4 并入下列条目)

P5a 设置两级结构 + "模型与服务" 页框架 + 单一默认目标

- [ ] P5.1 设置首页 (外观 4 行 + 5 入口) 与 对话 / 语音 / 隐私与权限 / 更新与关于 4 个子页 Activity (D80); 共享 hairline 插入逻辑移入 ui/Rows.kt; 新选择器用 confirmedChoiceDialog; 去重与改名 (D80: 删 "关于" 里重复的版本历史, "生成" 改名 "输出长度与采样"); 聊天页溢出菜单与抽屉各只留一个 "设置" 入口, 切换器面板底部 "管理模型与服务".
- [ ] P5.2 "模型与服务" 页 (D95, D98, D100, D104): 合并 ModelManagerActivity 与 OnlineAiSettingsActivity 为一个 Activity, 一页两段, 顶部默认目标行与允许移动网络开关, 统一卡片 / 状态徽章 / 更多菜单 / 段级菜单, 本地模型详情; "没有可用目标" / "目标不可用" 对话框与宿主动作 (D103, 合成返回栈) 都落到这一页; 术语与 10 种语言文案 (D100); 服务详情页在 P5b 之前仍用现有底部表单编辑器 (含 P8.0 的修复).
- [ ] P5.3 单一全局默认目标 (D97): defaultTargetId 迁移与写入, 删除默认在线目标与本地选中, "首选模型" 改名, 脚本 API 默认目标语义不变 (api-coverage 矩阵不受影响); 纯策略单测 (迁移四种情形, 删除默认目标所指的服务 / 模型后的回退).
  - 验收: 单测全绿; 仪器测试 (设置首页 5 入口可达, 模型与服务页两段与默认目标行, 宿主动作落点与返回栈); 新增脚本检查 "同一标题只出现一次" (遍历所有设置页字符串); 在 1.7.0 changelog 条目里追加本卡的条目 (10 种语言; 条目已由 P10 新建, released_date 为占位, 见 D109); XQ-DQ72 浅色 / 深色截图.
- 依赖: P8 (1.6.0 已发布, D102).

P5b 服务详情页 + 本地下载一步 + 切换器去重

- [ ] P5.4 服务详情页 (D96): 全屏 Activity, 连接信息 + 模型列表 (排序, 首选, 图片输入, 移除, 添加模型), 测试连接, 未保存确认与草稿恢复; 删除底部表单编辑器 (含 P8.0 的修复代码) 与 "模型 ID / 默认模型 / 图片输入模型" 三个选择器.
- [ ] P5.5 本地下载一步 (D99): 下载到 filesDir/models 并自动导入, 下载中卡片 (进度 / 暂停 / 取消 / 重试), 删除 SAF 保存位置与 "导入已下载模型" 路径及偏好, 保留 "导入模型文件"; changelog 说明. 下载确认处显示该模型的内存要求, 设备内存低于要求时警告, 仍允许下载 (D107, 2026-11-12 加入; 策略单测 1 组).
- [ ] P5.6 切换器面板 (D101): 收藏去重, 行副标题, 搜索不分组, "本机" 改 "本地模型"; ModelPickerPolicy 单测.
- [ ] P5.7 测试与文档: 更新仪器测试 (AppearanceSettingsTest, LauncherIconSelectionTest, SpeechSettingsDeviceTest, 新增 模型与服务 / 服务详情 / 下载 用例), docs/dev/p4-p5-host-plugin-settings-smoke.md 第 4 步 (D103), docs/dev/online-provider-backend.md (schema 5 与首选模型), README (失效路径 "设置 > 在线 AI" 等, 服务 / 模型术语, 本地模型占用体积说明); 真机 XQ-DQ72: 添加服务 -> 加模型 -> 设为默认 -> 新会话使用, 下载一个小模型一步完成, 浅色 / 深色截图.
- 依赖: P5a.

P6 消息分支 (D78 第 3 项, D82)

- [ ] P6.1 数据模型与策略 (节点 / 版本 / 选中), 重新生成与编辑改为新建版本, 删除语义; v6 属性键; 搜索 / 导出 / 上下文编译 / 朗读 / 用量累计按选中分支.
- [ ] P6.2 UI: 消息下方 "< 2/3 >" 切换, 切换时重绘后续消息; 分支数上限 (每节点 <= 8 个版本) 与提示.
- [ ] P6.3 单测 (策略与 codec) + 真机 (重新生成两次后在 1/3 - 3/3 间切换, 重开会话保持选中).
- 依赖: P4.

P7 导出 Markdown / 长图 (D78 第 4 项, D83)

- [ ] P7.1 Markdown 导出 (含附件文件名, 可选思考摘要) 与长图分享 (FileProvider 需在 Manifest 新增, 只暴露 cache/share 目录).
- [ ] P7.2 单测 (Markdown 文本生成) + 真机 (导出文件可在其他应用打开; 50 条消息的会话 3 s 内生成长图并出现分享面板).
- 依赖: P6.

P8 发布 1.6.0

Q11 = A: 1.6.0 = P1 - P3 (生成协调器与硬取消, effort / fast 与模型切换器面板, 思考过程卡片); P4 - P7 归 1.7.0, 由 P9 发布.

- [x] P8.0 编辑表单防丢输入 (D105, 需求 8; 2026-11-05 16:0x 加入, 由 P8 子会话在 P8.1 之后, Anthropic 补测之前单独提交): 底部表单禁止下滑收起与点外部关闭, 右上角关闭按钮, 未保存改动确认, onSaveInstanceState 草稿恢复; 仪器测试 1 项; 10 语言 changelog 加一条 Fix; VERSION_BUILD 递增.
  - 证据 (2026-11-05, 子会话 3-stone-p8-release-160): 提交 0765118 (VERSION_BUILD 316), 已推送 master; CI 四项全过 (Documentation, Build and plugin contract, Icon geometry, Icon Studio recipe, 均由推送触发). 实现: formBottomSheet 加可选关闭守卫 (SheetCloseGuard), 只用于在线档案编辑表单: setCancelable(false), isHideable / isDraggable = false, 标题行右上角关闭按钮, 返回键经 onBackPressedDispatcher; 关闭按钮, "取消" 与返回键在有改动时弹 "放弃更改?", 没有改动直接关闭. 改动判断比较 8 个表单值 (OnlineAiProfileEditorValues, 不含 API Key) 与打开时的基线, 填了 API Key 也算改动. 草稿 (含未保存的 API Key, 以 CharArray 存放) 只写 onSaveInstanceState 的 Bundle, 不写持久化; 重建后重新打开表单并回填, 回填后清零. 真机首轮发现 Activity 启动后立即被系统重建时, EXTRA_ADD_PROFILE 的打开请求会在已销毁的旧实例上执行 (表单挂在旧窗口, WindowLeaked), 已改为把未执行的请求交给新实例. 不改数据模型. 文案: 10 语言加 4 条字符串 (form_discard_changes_confirm / message / title, form_sheet_close), changelog v1.6.0 十语言 fix 加第 5 条, released_date 未动. 单测 578 全绿 (新增 OnlineAiProfileEditorPolicyTest 6 项). 仪器测试 OnlineAiProfileEditorDeviceTest 3 项在 XQ-DQ72 (API 33, 覆盖安装调试包 316, 数据保留) 连续两轮通过: 对照组同样的下滑能收起无守卫表单; 档案表单从标题与中部各下滑一次并点外部后仍展开; recreate 后名称, 地址与 API Key 回填; 返回键与关闭按钮有改动时先询问, 选取消保留内容, 选放弃后关闭; 没有改动直接关闭; 草稿 Bundle 经 Parcel 往返. 前后档案列表不变, 未发 Anthropic 请求, 未用 Anthropic 资料. generate_markdown --check 与 check_repository (提交前 --pending-commit, 提交后不带) 均无错误. 表单里打开的子选择器未加保护, 见 Q16 (已决 A).
- [x] P8.1 十语言 changelog (通俗文案), VERSION_NAME 1.6.0, README 功能说明 (effort / fast, 模型切换器面板, 思考卡片, 生成不随界面重建中断与硬取消, 历史格式 v6; 原文的 "附件, 设置页" 划归 1.7.0, 见 P9.1) 与权限说明核对.
  - 证据 (2026-11-05, 子会话 3-stone-p8-release-160): 提交 a012633 (VERSION_NAME 1.6.0, VERSION_BUILD 313), 已推送 master; CI 四项全过 (Documentation, Build and plugin contract 由推送触发; Icon geometry, Icon Studio recipe 按路径过滤未触发, 手动 workflow_dispatch 于 a012633 跑过). changelog v1.6.0 十语言核对后未改 (各 3 条新功能 + 4 条修复, 无全角标点, 术语与界面字符串一致, released_date 保留占位 2026/11/05). README 十语言各加 5 条功能 (共 30 条), 安全一节加思考内容只存本机, 不回传在线服务, 不经插件接口返回脚本 (对应 OnlineAiSessionTest "思考不进下一轮上下文"), 能力限制一节写明插件接口不声明 reasoning; Manifest 自 v1.5.0 起无变化, 网络与权限说明无需改. 单测 572 全绿 (testDebugUnitTest --rerun 实跑), generate_markdown --check 与 check_repository (提交前 --pending-commit, 提交后不带) 均无错误.
- [x] P8.2 发布门: 单测全绿; 仪器测试在 XQ-DQ72 通过; XQ-DQ72 与 OnePlus 63751f63 (ColorOS) 正式包冒烟; 发布与索引登记. 发布前先补 Anthropic 真机两轮 (P2.6 fast, P3 思考卡片), 维护者额度预计 2026-11-11 恢复.
  - 证据 (2026-11-12, 子会话 3-stone-p8-release-160): Anthropic 真机两轮见 P2.6 / P3 的补测证据 (1654c2d). 发布日提交 ffc0ff5 (10 语言 released_date 2026/11/12 并重新生成文档, VERSION_BUILD 320), 附注标签 v1.6.0 指向它, 正式包 versionCode 320. 单测 578 全绿 (ffc0ff5 的干净克隆, testDebugUnitTest --rerun 实跑). 仪器测试: XQ-DQ72 (API 33, 经远程 adb) 覆盖安装 ffc0ff5 调试包 320 (同签名, 数据保留), 可在真机跑的 16 个类共 67 项 OK (ChatGenerationContinuity, ChatReasoningCard, OnlineAiProfileEditor, PluginDiscovery, AbiCompatibility (process64=true, 页大小 4096), AboutIconFrame, HostSnapshotThread, LauncherIconResource, NativeToolSession, OwnedParcelFileDescriptors, VisionSession 与 5 个 Speech 类); 未跑只在 AVD 上跑的外观 / 启动器类, 以及手动, 真实模型, 时延与需显式开启的联网类. 正式包: 干净克隆 (sourceDirty false) 跑 appendDigestToReleasedFiles, 5 个 APK (universal, arm64-v8a, x86_64, armeabi-v7a, x86; 后两个不含原生库, 内容相同, 与 1.5.0 一样), 签名者摘要与 1.5.0 相同. 冒烟: XQ-DQ72 首轮报 "Service discovery differs", 原因是冒烟仪器测试 ReleaseAbiSmokeInstrumentation 自 1.5.0 起就过时 (d9552e6 在 AI_PROVIDER 下加了 ThreeStoneAiRuntimeService, 测试仍要求恰好一个服务; 1.5.0 的正式包冒烟由维护者手动完成, 没有跑这个测试), 不是正式包的问题; 修正提交 2e11cb4 (只改测试, VERSION_BUILD 321; 标签与正式包不变) 改为核对 INFO 与 AI_PROVIDER 下声明的全部服务及其权限, 只按组件绑定面向宿主的那个服务. 重跑: XQ-DQ72 正式 arm64 包 320 覆盖安装调试包 (同签名, 保留数据; 当时 VPN Hotspot 在前台, 经维护者同意加 --force) releaseAbiSmoke=passed, process64=true, 页大小 4096, 正式包上 Codex (gpt-6.1-sol) 一轮 20 s 完成 (输入 4228 / 输出 5, 回复 "OK"); OnePlus 63751f63 (OPD2413, Android 15, ColorOS) 正式 arm64 包 320 覆盖安装调试包 317 (保留数据) releaseAbiSmoke=passed, process64=true, 页大小 4096. 两台的测试包都已卸载, 设备上留正式包 320. 维护者预览确认标题, 正文与资产后, 选择等 OnePlus 冒烟通过再发布: GitHub Release 1.6.0 于 2026-11-12 08:30 UTC (16:30 +08:00) 发布并标为 Latest, 资产 5 个 APK + SHA256SUMS, 线上大小与摘要逐个与 release-manifest 核对一致. 官方索引: release-manifests/io.github.supermonster003.autojs6.plugin.threestoneai/320.json, 索引提交 45437c0 (重新生成后 83 个条目中只有 3-Stone AI 一条变化, 索引工具单测 81 项 OK). CI: ffc0ff5 与 2e11cb4 的 Documentation, Build and plugin contract 全过, 标签推送触发的 Icon geometry, Icon Studio recipe 全过.
- 备注 (2026-11-05 15:5x, 维护者拍板 "先做 P8.1 准备"): P8.1 提交在额度恢复前完成, released_date 保留占位 2026/11/05; 发布日再单独提交一笔 (10 语言 released_date 改为实际发布日并重新生成文档, VERSION_BUILD 届时递增), tag v1.6.0 指向这笔日期提交, 不指向 P8.1 提交. 若 Anthropic 补测发现请求形状要改, 修正提交 (D95 起) 也排在 tag 之前.
- 依赖: P1 - P3 (Q11 = A).

P10 发布 1.6.1 (修复版; D106, D108; 2026-11-12 加入, 排在 P8 与 P5a 之间; 同日 23:0x 按 Q21 = A 改为不发 1.6.1, 修复随 1.7.0, 见 D109)

来源: feedback-680 转入的 F26 (智谱 (GLM) 输出的 JSON 自带 Markdown 围栏, 工具执行能过, 任务收尾一步解析报错), F37 (本地模型写 JSON 不稳定), F25 (本地模型 "不让它使用网络倒快很多", 原因未明). 群友只用 feedback-680 的代号, 已跟踪文件不写昵称与 QQ 号.

- [x] P10.1 定位: 确认 "收尾一步因围栏解析报错" 发生在哪一层 (插件结构化输出路径 response_format / json_schema, 工具调用参数解析, 脚本 API / Binder 返回值, 还是宿主侧调用方), 用 .python/p4_mock_provider.py 或 ScriptedAiBackend 构造带围栏的回复复现; 结论写进本条证据. 落在本仓库以外时登记待决 (Q21 起), 不改其他仓库.
  - 证据 (2026-11-12, 子会话 3-stone-p10-1-6-1-json-f, 只读): 报错不在本仓库, 也不在宿主, 在 3-Stove Agent 1.4.0 的决策解析. (1) 3-Stove Agent v1.4.0 的 DecisionValidator (第 163 行) 调用 `DecisionParser.parse(text, format.degraded)`; 非降级模式下 DecisionParser 第 18 行直接报 "Return exactly one valid JSON object without a fence or trailing text.", 即群友 E 说的 "限制写在源码里". 复现即 Stove 自带单测 DecisionParserTest 的 "strict fence" 用例: 合法的 done JSON 包在 ```json 围栏里, degraded=false 时解析失败 (同一输入 degraded=true 时为 EXTRACTED 成功). 1.4.0 的 ModelClient 在目标支持工具时优先原生工具模式: 工具调用走原生 tool_calls 字段, 所以 "工具步骤都能过"; 收尾的 done 是正文文本, 按严格模式解析, 所以只有 "完成任务" 一步失败, 与 F26 现象一致. Stove issue #1 (2026-11-06, 智谱 GLM-4-Flash-250414 / GLM-5 经 3-Stone AI 1.5.0 OpenAI 兼容模板, DECISION_UNPARSABLE 三次) 是同一问题. Stove 的 P25 (本地提交 9ee5b47d, 2026-11-07) 已改为严格解析失败后退回提取模式并标记 schemaIgnored, 随 Stove 1.4.1 发布 (未推送, 依赖宿主 6.8.1), 不归本仓库. (2) 3-Stone AI 不解析也不改写 Binder 回复: RemoteThreeStoneAiSession.finishCompleted 把流式累积文本原样放进 AiCompletionResult; 宿主 AiProviderQuotaPolicy.validateCompletionAggregate 要求有流式分片时输出字节与分片累积完全一致, 插件在收尾剥离围栏会被宿主判为违约; 原生工具模式的请求不带 schema, 插件也无从得知这段正文应是 JSON. 宿主 ThreeStoveAgentModelBroker 同样原样转交. 所以 F26 不能在本仓库修, 也不应在 Binder 返回值上改写. (3) 本仓库内的同类缺陷: 聊天页的上下文摘要检查点 SummaryCheckpointJsonCodec.decode 只接受首尾正好是 { } 的严格 JSON (SummaryCheckpointTest 断言 ```json 围栏抛 IllegalArgumentException); SummaryCheckpointer 第二次尝试不带 schema, 不遵守 response_format 的服务商 (如智谱) 与本地模型返回带围栏或说明文字的 JSON 时两次都失败, ChatGenerationCoordinator 只记一条警告并保留旧检查点, 按 D5 退化为纯截断, 长对话里较早的内容不再进入摘要. 这是 D106 剥离策略在本仓库唯一适用的结构化输出收尾点 (在线三协议与本地 LiteRT-LM 的摘要都走它). 后续处理见 Q21.
- [x] P10.2 修复 (D106): 纯 JVM 策略, 解析前剥离 Markdown 围栏 (带或不带语言标记) 与 JSON 前后的说明文字; 在线三协议与本地 LiteRT-LM 的结构化输出收尾共用; 不改请求体, 不改 Binder 契约. 单测覆盖: 纯 JSON, 带 json 标记的围栏, 无标记围栏, 围栏前后有说明文字, 未闭合围栏, 字符串值里含围栏字符, 数组与对象, 非 JSON 文本 (保持原有报错). 没有智谱资料时以单测与模拟提供方为证据, 真机用 Codex 资料发一轮确认未回归.
  - 证据 (2026-11-12, 子会话 3-stone-p10-1-6-1-json-f; 按 Q21 = A 只用于摘要检查点, 见 D109): 新增 StructuredJsonText (纯 JVM, Gson 严格 JsonReader 校验候选; 候选起点最多 32 个, 括号深度最多 64, 防止噪声文本耗时). SummaryCheckpointJsonCodec.decode 先按原逻辑直接解析, 原来能过的输入行为不变; 失败时才用 StructuredJsonText 取出对象再走同一套严格校验 (精确键集, 整数 ID, 条数上限), 取不到时抛原来的错误 ("Summary response must be a JSON object"), 取到但校验失败时抛校验错误并附原错误. 请求体, schema 与 Binder 回复都未改. 单测 594 全绿 (1.6.0 为 578): StructuredJsonTextTest 13 项 (纯 JSON, 带 json / JSON 标记与无标记围栏, ~~~ 与四个反引号围栏, 围栏前后说明文字, 无围栏的前后说明文字, 未闭合围栏, 字符串里的围栏与括号字符, 关闭围栏须同字符且不短于开头, CRLF, 对象 / 数组形状过滤, 跳过非严格 JSON 候选, 无 JSON 的围栏退回全文, 非 JSON / 截断 / 括号错配 / 顶层原始值返回 null, 深嵌套与大量噪声有界); SummaryCheckpointTest 改写原 "围栏应被拒绝" 的断言并新增 2 项 (6 种包装都能解出; 围栏内多余键与小数 ID 仍拒绝, 纯文本保持原报错, 截断不会解出); SummaryCheckpointerTest 新增 1 项 (模拟提供方返回 "说明 + ```json 围栏 + 说明", 第 1 次尝试即成功, 不重试). assembleDebug, assembleDebugAndroidTest, lintDebug, generate_markdown --check, check_repository --pending-commit 与 git diff --check 均通过. 未做真机: 改动只在摘要检查点, 该路径要在长对话越过上下文软水位后才触发, Codex 发一轮普通对话碰不到它, 也不发版; 1.7.0 的真机门由 P9.2 覆盖. changelog: 10 种语言新建 v1.7.0 条目 (1 条修复, released_date 占位 2026/11/12, P9 发布日改为实际日期), VERSION_NAME 仍为 1.6.0 (P9.1 改). 提交 2aa3cf2 (VERSION_BUILD 324, 含 P10.1 / P10.3 证据, D109, Q21 / Q22 已决), prepush_check 8 / 8 通过, 已推送 master; CI: Documentation 与 Build and plugin contract (verify, 5 个 ABI, x86 / x86_64 4 KB 与 x86_64 16 KB 页的发现与在线 AI 仪器测试) 全过, 两个图标工作流按路径过滤未触发.
- [x] P10.3 F25 只读排查 (D108): 本地推理路径在生成前后有无联网动作 (目录刷新, 下载校验, 记忆召回, 更新检查); XQ-DQ72 上同一本地模型对比有网与飞行模式的首字时间 (各 3 轮). 有代码原因登记待决由维护者定, 没有就在本条记结论并关闭 F25.
  - 代码排查 (2026-11-12, 子会话 3-stone-p10-1-6-1-json-f, 只读): 没有找到代码原因. (1) 本地生成路径 (ChatActivity -> ChatGenerationCoordinator -> LiteRtLocalBackend / LiteRtLocalSession / LiteRtLmEngineRuntime) 不引用 OkHttp, OnlineAiNetworkAccess 或下载类; 模型目录 AvailableLiteRtModelCatalog 是内置静态表, refreshTargetCatalog 只读本地资料登记表, 记忆召回 (ConversationRecallStore) 是本地 SQLite, 摘要检查点用的是当前会话的同一目标. (2) 本地模型不挂工具声明: LiteRtLocalBackend 的能力 tools = false, 聊天页本身也不发工具. (3) 聊天页唯一的联网动作是 onResume 时的自动更新检查 (AppUpdateController.checkAutomaticallyIfDue, 每 12 小时最多一次, OkHttp 异步 enqueue, 超时 15 / 20 / 30 s), 不在生成线程上, 不阻塞本地推理. 模型下载只在模型管理页由用户发起. (4) LiteRT-LM 0.15.0 本身: arm64-v8a 的 liblitertlm_jni.so 不含 getaddrinfo / gethostbyname / socket / connect / SSL_connect 符号, 只有文档类 URL 字符串; classes.jar 里没有引用 java.net 或 OkHttp 的类; AAR 清单不申请权限. 结论: 本地推理链路上没有联网动作, 群友 A 看到的差异更可能来自设备环境 (联网时系统或其他应用的后台同步, 代理应用占用 CPU 与内存, 温度), 不是本插件. XQ-DQ72 有网 / 断网对比是否还做, 见 Q22 (该设备 2026-11-12 22:5x 未连接). 维护者 2026-11-12 23:0x 选 Q22 = A: 按代码结论关闭 F25, 不做设备对比.
- [-] P10.4 (取消: 维护者 2026-11-12 23:0x 选 Q21 = A, 不单发 1.6.1, 修复随 1.7.0 发布, 见 D109) 发布 1.6.1: 十语言 changelog 新建 v1.6.1 条目 (通俗文案), VERSION_NAME 1.6.1; 发布门同 P8.2 (单测全绿, 仪器测试在 XQ-DQ72 通过, XQ-DQ72 与 OnePlus 63751f63 正式包冒烟); tag v1.6.1, 干净克隆签名构建, 创建 Release 前请维护者预览, 登记官方索引.
- 依赖: P8 (1.6.0 已发布). P10 打 tag 前 P5a 不开工 (单 master 分支). Q21 = A 之后不再打 v1.6.1 tag: P10 的修复提交推送后 P5a 即可开工.

P9 发布 1.7.0 (P4 - P7, Q11 = A)

- [ ] P9.1 十语言 changelog 的 v1.7.0 条目 (P10 已新建并写入摘要检查点修复 (D109; 原定 P5a 卡新建, D102), 之后各卡随卡写入, 通俗文案; 发布日把占位的 released_date 改为实际日期), VERSION_NAME 1.7.0, README 功能说明 (设置页与 "模型与服务" 页重设计, 附件, 消息分支, 导出) 与权限说明核对 (P7 新增 FileProvider 只暴露 cache/share).
- [ ] P9.2 发布门同 P8: 单测全绿; 仪器测试在 XQ-DQ72 通过; XQ-DQ72 与 OnePlus 63751f63 (ColorOS) 正式包冒烟; 发布与索引登记.
- 依赖: P8 (1.6.0 已发布), P4 - P7.

### 4. 顺序与批次

P0 -> P1 -> P2 -> P3 -> P8 -> P10 -> P5a -> P5b -> P4 -> P6 -> P7 -> P9, 每次只跑一张卡 (D69; 2026-11-12 按 D106 在 P8 与 P5a 之间插入 P10 发布 1.6.1, P10 打 tag 前 P5a 不开工; 同日 23:0x 按 Q21 = A 不发 1.6.1, P10 的修复随 1.7.0, P10 推送后 P5a 即可开工 (D109); 2026-11-05 16:0x 按 D102 把 P5 提前为 1.7.0 首卡并拆为 P5a / P5b; 此前为 P0 -> P1 -> P2 -> P3 -> P8 -> P4 -> P5 -> P6 -> P7 -> P9 (2026-11-05 按 Q11 = A 改), 原顺序 P0 -> P1 -> ... -> P7 -> P8). 单 master 分支: P8 发布 (打 tag) 前 P4 不开工, 避免半成品进 1.6.0. 调度会话可把 P1 拆成 P1a (协调器) 与 P1b (硬取消 + 探针) 两张顺序卡, 也可在 P3 之后插入 P5. 每张卡完成时子会话把勾选, 新决策 (D84 起) 与新待决 (Q12 起) 写回本节与看板.

### 5. 待决事项 (Q9 起)

- Q9 已决 (2026-11-05, P3 子会话按官方文档核实, 结论与出处见 D93: Claude 5+ 必须发 adaptive + summarized 才有摘要文本, 与 output_config.effort 可同时发送). 原问题: 思考内容的请求参数: Anthropic 5.x 默认自适应思考下是否需要 thinking.display = "summarized" 才返回摘要, 以及 output_config.effort 与 thinking 块能否同时发送; P3 子会话实施前按官方文档核实, 结论登记为 D84 起的决策. 推荐: 以官方文档为准, 文档未写明时先按 RikkaHub 的做法 (adaptive + summarized) 实测.
- Q10 新会话默认目标: 2026-11-02 在 1.4.1 上观察到新会话默认目标是 claude-opus-5-5 而不是资料默认模型 claude-fable-5-1; P2 子会话排查是 "上次使用" 记忆, 资料默认模型设置还是目录顺序; 若是缺陷则修并记决策. 已决 (P2.5 子会话核实, 2026-11-05): 来源是资料默认模型设置, 不是 "上次使用" 记忆, 也不是目录顺序. 新会话的默认目标就是默认资料的 "默认模型" 字段; XQ-DQ72 上默认资料 Anthropic 的模型列表为 [claude-opus-5-5, claude-fable-5-1], 默认模型为 claude-opus-5-5 (run-as 只读取证). 原因是资料编辑器新建 Anthropic 资料时按内置目录预填默认模型 claude-opus-5-5 (1.4.1 与当前一致), 之后添加 claude-fable-5-1 不会改默认模型; 默认目标逻辑自 1.4.1 未变, 不算缺陷. 维护者要以 fable 为默认, 在资料里把默认模型改为 claude-fable-5-1 即可. 排查中发现并修复一个关联缺口: 改默认模型后回到已打开的空会话, 目标不跟随 (D92).
- Q12 已决 (2026-11-05 14:2x, 维护者选 A 按模型分档并发 max; 调度会话多来源核实 max 是 gpt-5.6 / gpt-6 系列的正式档位, 原推荐的 "官方最高档是 xhigh" 有误; P3 子会话落地, 分档表与出处见 D90). 原问题: OpenAI 兼容的 MAX 是否改发 max (P2, 不阻塞): 选项 (a) 保持发 xhigh (推荐, 已实施): 符合 D73 / D90, 官方文档列出的最高档是 xhigh; (b) 发 max: 个别代理也许接受, 官方接口会拒绝. 影响 P2.2 (OnlineAiEffortSupport 的 OpenAI 支持表). 改选返工量: 支持表 1 行 + 单测 1 项.
- Q13 已决 (2026-11-05 14:2x, 维护者选 A 1.6.0 保持现状, 未改动). 原问题: 资料第一个模型改变时裸目标 ID 的含义漂移 (P2.5 排查中发现, 不阻塞): 资料的第一个模型用裸 ID profile:<资料 id>, 其他模型用 profile:<资料 id>.model.<哈希>; 维护者调整模型顺序使第一个模型改变后, 裸 ID 指向新的第一个模型, refreshTargetCatalog 会把有消息的会话的目标快照静默更新为新模型并保存. 选项 (a) 1.6.0 保持现状 (推荐, 未改动): 只在调整模型顺序时出现, 改目标 ID 方案牵涉 Binder 已发布的目标 ID; (b) 有消息的会话遇到模型 ID 变化时不再静默更新, 改为提示重新选择目标: 约半天, 影响 ConversationTargetPolicy 与 ChatActivity; (c) 目标 ID 一律带模型哈希: 需兼容已发布的 Binder 目标 ID 与历史, 约 1 - 2 天. 影响条目: 无 (可放进 P4 之后任意卡). 改选返工量见各选项.
- Q14 已决 (2026-11-05 15:3x, 维护者选 A 不发, 已实施, 无改动). 原问题: Opus 4.6 - 4.8 / Sonnet 4.6 是否也请求思考摘要 (P3.1 发现, 不阻塞): 这些模型不发 thinking 时不思考, Claude 5+ 默认就思考. 选项 (a) 不发 (推荐, 已实施): 行为与费用不变, 这些模型没有思考卡片; (b) 聊天页对它们也发 adaptive + summarized: 有思考卡片, 但每轮开始思考, 用时与输出 token 增加 (思考按输出计费). 影响条目: P3.1 (D93 第 1 项). 改选返工量: 约 15 分钟 (anthropicSummarizedThinking 增加这些前缀并补单测), 不涉及存储与界面.
- Q15 已决 (2026-11-05 15:3x, 维护者选 A 保持现状, 未改动; 有 Gemini 资料时真机一轮, 返回 400 再改). 原问题: Gemini 2.5 是否继续发 thinkingLevel (P3.1 核实 D89 时发现, 不阻塞, 未真机验证): generateContent API 参考页写 thinkingLevel "用于 Gemini 3 之前的模型会报错", 思考指南的模型表却列出 gemini-2.5-pro / 2.5-flash / 2.5-flash-lite 支持 low / medium / high (该页示例都是 Interactions API). 只在用户选了非 DEFAULT 的 effort 时发送, DEFAULT 不发. 选项 (a) 保持现状 (推荐, 未改动): 等有 Gemini 资料时真机一轮确认, 若返回 400 再改; (b) gemini-2.5 不发 thinkingLevel (仍发 includeThoughts), 2.5 的 effort 选项行隐藏: 约半小时 + 单测. 影响条目: P2.2 (D89). 出处: https://aistudio.google.com/api-reference/generate-content , https://aistudio.google.com/docs/thinking
- Q16 已决 (2026-11-05 16:4x, 维护者选 A 1.6.0 保持现状, 未改动). 原问题: 在线档案编辑表单本身已不可下滑收起 (P8.0), 但从表单里打开的三个子选择器 (模型 ID / 默认模型 / 支持图片输入的模型) 仍可下滑或点外部收起, 收起只丢子选择器里的当前勾选, 表单内容不丢. 选项: A 1.6.0 保持现状 (推荐, 0 工时; 1.7.0 P5b 的服务详情页取代这三个选择器, D96) / B 1.6.0 也给子选择器加同样的关闭保护 (约 1 - 2 小时 + 仪器测试 1 项) / C 只禁下滑, 点外部仍可关 (约 1 小时). 来源: P8.0 (3-stone-p8-release-160).
- Q17 已决 (2026-11-05 16:5x, 维护者选 A 保持现状, 未改动; 1.7.0 P5a 设置页重组时再看). 原问题: 应用设置里的 "生成参数" 底部表单 (AppSettingsActivity.showGenerationSettingsDialog: 输出上限, 采样参数等数字输入框与两个勾选框) 仍可下滑或点外部收起, 收起即丢弃这次的修改; 它不在 D105 的范围 (D105 只管添加 / 编辑服务的表单), 而统一设置规范 (AUTOJS6_PLUGIN_STANDALONE_SETTINGS_AGENTS.md) 写明取消 / 返回 / 点外部关闭不保存草稿, 现状符合规范. 选项: A 保持现状, 1.7.0 P5a 设置页重组时再看 (推荐, 0 工时) / B 1.6.0 也给它加 P8.0 的关闭守卫 (约 1 小时 + 仪器测试 1 项, tag 前补一笔提交, changelog 第 5 条 Fix 文案要改为泛指表单). 影响: P8.2 (选 B 时), P5a. 来源: P8.0 (3-stone-p8-release-160).
- Q11 已决 (2026-11-05 15:3x, 维护者选 A: P1 - P3 发 1.6.0, P4 - P7 发 1.7.0 (P9); P8 发布前先补 Anthropic 真机两轮, 期间 P4 不开工; 15:5x 又定额度恢复前先做 P8.1 准备, 见 P8 备注). 原问题: 1.6.x 切点: D68 允许分批发布; 推荐 P1 - P3 完成后发 1.6.0 (生成不中断 + effort / fast + 思考卡片), P4 - P7 发 1.7.0; 由调度会话在 P3 完成时请维护者拍板.
- Q18 已决 (2026-11-12 22:2x, 维护者选 A 先发 1.6.1, 见 D106 与 P10). 原问题: F26 / F37 结构化输出修复 (JSON 带 Markdown 围栏致收尾解析报错, 本地模型写 JSON 不稳定) 的排期. 选项: A P5a 之前插一张小卡并发 1.6.1 (推荐, 约 1 天) / B 进 1.7.0 排在 P5a 前, 不单独发版 / C 1.7.0 之后再排.
- Q19 已决 (2026-11-12 22:2x, 维护者选 A 并入 P5b, 见 D107 与 P5.5). 原问题: 下载本地模型前提示内存要求放在哪张卡. 选项: A 并入 P5b 下载一步 (推荐, 约 +0.5 天) / B 随修复小卡先做 (P5b 重做页面会返工) / C 暂不做.
- Q20 已决 (2026-11-12 22:2x, 维护者选 A 随修复小卡只读排查, 见 D108 与 P10.3). 原问题: F25 本地模型 "不让它使用网络倒快很多" 怎么处理. 选项: A 随修复小卡只读排查 (推荐, 约 2 小时) / B 先不查等更多信息 / C 单独发排查卡. 新待决从 Q21 起, 新决策从 D109 起.
- Q21 已决 (2026-11-12 23:0x, 维护者选 A: 剥离策略只用于摘要检查点, 随 1.7.0 发布, P10.4 取消, 见 D109). 原问题 (P10.1 子会话 3-stone-p10-1-6-1-json-f 登记): F26 的报错在 3-Stove Agent 1.4.0 的决策解析 (见 P10.1 证据), 3-Stone AI 与宿主都只原样转交文本, 本仓库唯一的同类缺陷是聊天页上下文摘要检查点的严格 JSON 解析. P10 怎么继续? 选项: A (推荐) 本卡照做 D106 剥离策略, 只用在本仓库的摘要检查点解析 (纯 JVM 策略 + P10.2 列出的单测, 改写 SummaryCheckpointTest 里 "围栏应被拒绝" 的断言), 提交到 master 随 1.7.0 发布, 不单发 1.6.1 (P10.4 取消), 约半天, P5a 随后即可开工; F26 的缓解等 3-Stove Agent 1.4.1 (P25 已在其本地 master, 依赖宿主 6.8.1), 由 Stove 主题处理 / B 剥离策略照做并照常发 1.6.1 (changelog 写 "长对话的上下文摘要在部分服务商与本地模型上失败" 的修复), 约 1 天; 缺点: F26 的群友升级 1.6.1 后看不到改善 / C 只记结论不改代码, P10 关闭, 摘要检查点问题另排进 1.7.0 某卡. 影响条目: P10.2, P10.4, P5a 开工时间. 改选返工量: A 改 B 只多发版流程 (约 3 - 4 小时, P5a 开工前可改); B 改 A 在打 tag 前无返工.
- Q22 已决 (2026-11-12 23:0x, 维护者选 A: 按代码结论关闭 F25, 不做设备对比). 原问题 (P10.3 子会话 3-stone-p10-1-6-1-json-f 登记): F25 代码排查没有找到联网动作 (见 P10.3 代码排查), 还要不要做 XQ-DQ72 有网 / 断网对比? 选项: A (推荐) 按代码结论关闭 F25, 不做设备对比, 0 工时; 维护者的设备与群友 A 的不同, 对比结果也难以解释群友那边的差异 / B 做对比: XQ-DQ72 为 Android 13, 没有只断单个应用网络的 shell 命令 (cmd connectivity set-package-networking-enabled 从 Android 14 起才有), 只能由维护者手动开关飞行模式 (远程 adb 会断开), 每种条件发 3 轮同一问题, 重连后读 logcat 里 "Generation start" 与 "Context complete" 的时间差 (正式包保留这两行; 没有首字时间日志, 只能比整轮耗时); 前提是该设备已导入本地模型 (不下载大模型), 约 1 小时, 维护者全程操作 / C 先请群友 A 补充信息 (设备型号, 是否开代理或 VPN, 慢的是首字还是整轮) 再定. 影响条目: P10.3. 改选返工量: 无.

### 6. 测试与证据要求

- 每张代码卡: 单测全绿 (当前 572 项为基线, P8.1 完成时, 与 P3 相同), check_repository --pending-commit 通过, VERSION_BUILD 按规则递增, 10 语言 changelog 条目随卡写入 (release_date 留给 P8).
- 真机以 XQ-DQ72 (QV770340J7) 为主, 用 .python/device_chat_probe.py 取证 (输出只留通过 / 失败与关键数值); Anthropic 真机验证受维护者额度限制, 额度恢复前以单测 + ScriptedAiBackend 仪器测试为证据, 并在看板登记 "待补 Anthropic 真机".
- 发布门 (P8 / P9): 仪器测试在一台真机通过; XQ-DQ72 与 ColorOS 设备正式包冒烟; README 的网络与数据权限说明准确.

### 7. 子会话约定

- 共用 D:/idea-projects/AutoJs6-Plugin-Three-Stone-AI 这一个 checkout, 只提交自己的文件, 不切分支, 不 amend; 提交后可推送 master 以跑 CI (插件仓库推送策略), 发布只在 P0 / P8.
- 设备占用登记 D:/idea-projects/locks.md; 手机上的确认对话框由维护者操作.
- 完成时把结论追加到看板 "子会话结果" (勾选条目, 新决策 / 待决, 提交 hash, 可否归档), 并按会话清理规则清理临时文件.

### 附录 A. 提供方 API 要点 (2026-11-02 核实, 实施前复核官方文档)

- Anthropic Messages: max_tokens 必填 (插件默认 16384); Claude 5.x / Fable 默认自适应思考且思考计入 max_tokens; output_config.effort 取 low / medium / high / xhigh / max, 不需要 beta 头; speed: "fast" 为顶层字段, Opus 5.5 正式可用, Opus 5 / 4.8 需 anthropic-beta: fast-mode-2026-02-01, 价格高于普通请求; Claude 4.7 及以后拒绝 temperature / top_k / top_p (400); 图片块 {"type":"image","source":{"type":"base64","media_type":...,"data":...}} 单张 <= 5 MB; 文档块 {"type":"document","source":{"type":"base64","media_type":"application/pdf","data":...}} <= 32 MB / 100 页; stop_reason 取 end_turn / max_tokens / model_context_window_exceeded / refusal / tool_use / stop_sequence.
- OpenAI 兼容: reasoning_effort (low / medium / high, 部分模型支持 xhigh); 图片用 image_url data URL; 推理文本的流式字段名因服务商而异 (reasoning_content / reasoning), 要按资料实测; finish_reason = length 表示输出上限.
- Gemini generateContent: thinkingConfig { thinkingLevel 或 thinkingBudget, includeThoughts: true } 后 parts[] 带 thought: true; inline_data 支持 application/pdf (<= 20 MB, 更大需 Files API, 本期不做); finishReason = MAX_TOKENS 表示输出上限.
- RikkaHub 参考位置 (master 2026-11-02): ChatInput.kt (effort 按钮只在模型有 REASONING 能力时显示), ReasoningPicker.kt (OFF / AUTO / LOW ... MAX 滑杆面板), ModelList.kt (搜索 + 收藏 + 分组的模型面板), ChatMessageReasoning.kt (思考卡片三态), ChatMessageBranch.kt 与 data/model/Conversation.kt (消息节点多版本), ConversationExport.kt (Markdown / PNG 导出), ChatAttachmentPicker.kt 与 FilesPicker.kt (附件入口), service/ChatService.kt 与 ChatVM.kt (生成归应用级单例, Activity 只订阅), SettingPage.kt 与 SettingPreferencesPage.kt (两级设置结构). RikkaHub 没有 fast 模式 / beta 头处理, 其 ClaudeStreamDecoder 把连接中断当正常结束, 这两点不作参考.
