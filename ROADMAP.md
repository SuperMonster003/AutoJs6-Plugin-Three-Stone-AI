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

2026-10-19 开工前复核: V1 (输入区 `ChatActivity.kt` 1233-1332 行, `MessageAction` 3809 行; 回复消息菜单为 `COPY` / `REGENERATE`, 用户消息为 `COPY` / `EDIT` / `DELETE`), V3 与 V8 仍成立, V2 已更正; Sony 三台装有 3-Macaw Speech versionCode 41 (弹层与 TTS 引擎均可解析), 小米两台仍是 versionCode 22 (两者都不可解析, 按 "无 Macaw" 处理).

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
| D38 | `VOICE_COMMAND` 入口实现 | 独立的透明跳板 Activity `voice.VoiceCommandActivity` (导出, 无权限, `excludeFromRecents`, label 为 `@string/app_name` 即 "3-Stone AI", icon 取应用图标), 只做校验后转交 `ChatActivity` (`onNewIntent` 或冷启动) 并立即结束, 不在跳板内初始化 backend; 纯 JVM `VoiceCommandPolicy`: `EXTRA_TEXT` 缺失或去空白后为空时只打开聊天页, 控制字符与截断 (D37), 10 s 限速窗口, `VOICE_LANGUAGE` 解析为 BCP 47 (非法即忽略). `ChatActivity` 收到后: 正在编辑消息则取消编辑; 正在生成则先停止当前生成 (已输出内容按停止轮保留) 再新建对话; 当前没有可用 target (未配置在线 provider, 本地模型未就绪) 时预填输入框并提示, 不发送 | P5-5 |

### 10.5 阶段任务

#### P5 - 1.4.0 系统接口: 听写, 朗读, 语音对话模式, `VOICE_COMMAND` 入口

- [x] **P5-1** 可解析判定与设置: `<queries>` 两条 (D22); `SpeechAvailabilityPolicy` 与 Android 适配层; `ChatUiSettings` 新增 `autoReadReplies = false`, `preferMacawSpeech = true`; 设置页 "语音" 组三行 (D17); 10 种语言文案 (ASCII 标点; 阿拉伯语中紧跟非拉丁文字的 "3-Macaw Speech" 加 FSI / PDI).
  - 验收: `ChatUiSettingsTest` 扩展; `SpeechAvailabilityPolicy` 单测 (Macaw 有 / 无, 其他识别应用有 / 无, 无 TTS 引擎, 开关开 / 关); Manifest 断言 `<queries>` 形状 (只有 action); `ApplicationTextPunctuationTest` 通过; 设置页在有 / 无 Macaw 两种设备状态下目视.
  - 完成 (2026-10-19): `speech/SpeechAvailability.kt` (纯策略) 与 `speech/SpeechDeviceProbe.kt` (适配层; 引擎是否存在用 `queryIntentServices(TTS_SERVICE)`, 与 `TextToSpeech.getEngines()` 同一查询, 不必绑定引擎); 文案在 `strings_speech.xml` (10 种语言 + 显式英语). 单测: `SpeechAvailabilityPolicyTest` 7 项, `SpeechManifestContractTest` 2 项 (`<queries>` 只有宿主包与两条 action, 权限仍为 3 项), `ChatUiSettingsTest` 4 项, 标点守卫通过. 真机 (`py .python/device_speech_check.py --serial <X> --only settings`, 截图在 `build/verification/speech/<X>/`): XQ-AT72 (API 31, Macaw versionCode 41) 听写与朗读均为 MACAW, "优先使用" 行可见, 关闭后两者变为 SYSTEM; 小米 23046RP50C (API 35, Macaw versionCode 22 不提供弹层与引擎, 小米识别与合成) 两者为 SYSTEM, 无 "优先使用" 行, 显示安装提示; 两台截图已目视. 设备上无任何 TTS 引擎的状态只有单测覆盖 (当时没有可用的空白模拟器).
- [ ] **P5-2** 单次听写 (D9, D23): 输入框内麦克风按钮, 短按发起 `RECOGNIZE_SPEECH`, 结果按 `DictationInsertion` 插入; 内容描述与 10 种语言文案.
  - 验收: `DictationInsertion` 单测 (光标, 选区, 空格补齐, 截断); instrumentation 以 androidTest 内的假识别 Activity 核对插入与各返回码; 真机 E4: 装 Macaw 的 Sony 一台 (弹层直接打开, 无选择器) 与只有 Google 的设备一台, 系统选择器与权限对话框由维护者在手机上操作.
  - 实现与自动化证据 (2026-10-19): `speech/DictationInsertion.kt`, `DictationOutcome.kt`, `DictationIntents.kt`; `ChatActivity` 输入框内麦克风. 单测 `DictationInsertionTest` 10 项, `DictationOutcomePolicyTest` 4 项. instrumentation `SpeechDictationDeviceTest` 3 项 (测试 APK 内的 Java 假识别 Activity, 经 `setPackage` 固定; 假模型目标 `ScriptedAiBackend`, 测试结束删除新建会话并还原偏好): 光标插入, 选区替换, 中文直连, 取消 / 无语音 / 成功但无文字均静默, 网络 / 麦克风 / 客户端 / 服务端错误各提示一次, 请求 extras (free_form, 界面语言, 提示语, 1 条), 16,384 上限截断与提示, 生成中只改输入框不发送, 固定的识别应用消失时提示, 无识别应用时隐藏按钮; G8441 (API 28), XQ-AT72 (API 31), 小米 23046RP50C (API 35) 均 3/3 通过 (`py .python/device_speech_check.py --serial <X> --only dictation`, 运行后卸载测试 APK, 假识别器不会留在系统选择器里). 真机 E4 (真人说话) 待维护者操作.
- [x] **P5-3** 朗读 (D10, D11, D12, D18, D19, D24, D25): `SpeakableTextPolicy`, `SpeechOutput`; 回复消息菜单新增 "朗读 / 停止朗读" (完成且非空的回复); 自动朗读; 输入框上方状态条.
  - 验收: `SpeakableTextPolicyTest` (标题 / 列表 / 链接 / 裸 URL / 代码块 / 表格 / 行内代码 / 数学 / 未闭合围栏, 中英日混排, 小数与缩写, 超长句, 流式增量与一次性结果一致); 状态条与菜单状态单测; 真机: Macaw 引擎 (AISHELL-3 与 MeloTTS 各一次, 含 G8441 的慢于实时场景) 与系统默认引擎各一次, 记录首句出声延迟; 来电或其他播放抢占音频焦点时停读.
  - 完成 (2026-10-19): `speech/SpeakableTextPolicy.kt` (`SpeakableTextSegmenter` 流式切句), `ReadingQueue.kt`, `ReadAloudPolicy.kt`, `VoiceStatus.kt` (纯策略), `SpeechEngine.kt` 与 `SpeechOutput.kt` (Android); `StreamingMarkdownParser` 只新增两个表格判定的内部入口. 单测: `SpeakableTextPolicyTest` 12 项 (含 18 段样本 x 14 种分块的流式与一次性一致), `ReadAloudStateTest` 7 项 (队列, 菜单, 自动朗读, 语言顺序, 状态条). 设备: `SpeechReadingDeviceTest` 5 项在 G8441 (API 28), XQ-AT72 (API 31), 小米 23046RP50C (API 35) 均通过 (边生成边读, 首句比回复结束早约 710 ms; 菜单朗读 / 停止; 状态条, 发送新消息, 新建会话, `onStop`, 停止生成, 其他播放抢占音频焦点 6 种停读; 语言回退提示一次; 引擎启动失败; 无引擎时无菜单项); 真实引擎 `SpeechEngineLatencyDeviceTest` 在四台设备上记录首句延迟, 数值见 `docs/dev/speech-reading-smoke.md` (例: G8441 上 MeloTTS 冷启动首个音频 7724 ms, 句间停顿约 1 s, AISHELL-3 无停顿; XQ-DQ72 上 AISHELL-3 冷启动 159 ms). 未实际拨打电话, 焦点丢失由另一播放方的 `AUDIOFOCUS_GAIN` 产生.
- [ ] **P5-4** 语音对话模式 (D13): 纯 JVM 状态机 `VoiceConversationMachine` (空闲 -> 听写 -> 生成并朗读 -> 听写 ..., 各退出条件); 长按进入, 状态条退出与打断; 进入时若在编辑消息则先取消编辑.
  - 验收: 状态机单测覆盖全部退出条件 (取消, 无语音, 识别错误, 生成失败, `onStop`, 点退出, 朗读中打断); 真机连续 10 轮语音对话 (在线 target 与 LiteRT 各一次), 记录 `docs/dev/voice-conversation-smoke.md` (每轮听写返回耗时, 首句出声延迟, 退出路径).
- [ ] **P5-5** `VOICE_COMMAND` 入口 (D35-D38): 跳板 Activity 与 `VoiceCommandPolicy`; `ChatActivity` 新建对话立即发送, 这一轮总是朗读, 10 s 限速, 无可用 target 时预填.
  - 验收: `VoiceCommandPolicyTest` (缺失 / 空白文字, 控制字符, 截断, 限速窗口, 语言标签); Manifest 断言 (导出, 无权限, action + DEFAULT, label 与 icon); instrumentation 以显式 intent + `FLAG_ACTIVITY_NEW_TASK` 冷启动与热启动各一次, 核对新对话, 发送与朗读开始, 以及生成中 / 编辑中 / 无 target 三种状态; 真机 E4: Macaw P5.7 数字助理可用后, 长按电源键听写并点 "3-Stone AI" 一次 (助理设置与解锁由维护者在手机上操作).
- [ ] **P5-6** 文档与发布: `.readme` 10 种语言 (功能, 隐私 D16, 常见问题: 多个识别应用, 低端机句间停顿, 未下载模型, 怎样经 3-Macaw Speech 数字助理提问), changelog 1.4.0 (10 种语言, 写给用户看), 生成与 `--check`; 界面截图; `VERSION_NAME=1.4.0`; 按 `AGENTS.md` 的发布流程 (签名包, `appendDigestToReleasedFiles`, 真机安装正式包, 官方索引登记).

#### P6 - 1.5.0 Binder: 输入框内实时听写, 无弹层对话, 常驻监听

- [ ] **P6-0** 前置: 宿主 6.8.0 已发布且 `speech-api` 契约 v1 冻结; 3-Macaw Speech 1.0.0 已发布; D31 的常驻监听互斥约定已写入 3-Stove Agent 与 3-Macaw Speech 的路线图并经三方确认 (Macaw 已采纳, `bf19529`; Stove 由父会话另行统筹). 开工前复核 Macaw D16 / D21 / D22 与本节 V6 / V9 是否仍成立.
- [ ] **P6-1** 契约接入 (D26): `speech-api.aar` 暂存与校验和; `MacawSpeechClient` (绑定, 能力协商, `linkToDeath`, 退回 P5).
  - 验收: 能力协商与退回规则单测; instrumentation 覆盖真实 Macaw, Macaw 未装, 宿主未装三种退回路径.
- [ ] **P6-2** 输入框内实时听写: Macaw 可用且偏好开启时短按麦克风不再弹层, `openSession` (VAD 开, `silenceEndMillis` 1200 ms), partial / final 写入输入框 (D26), 再按麦克风为 `finish`, 离开界面为 `cancel`.
  - 验收: 真机 API 31 / 33 / 35 各一台, partial 首字延迟与 CER 记录; `PERMISSION_DENIED` 与 `SESSION_BUSY` 两条引导由维护者在手机上确认.
- [ ] **P6-3** 无弹层语音对话: P5-4 的状态机换用会话听写.
  - 验收: 真机连续 10 轮, 对照 P5-4 的数据.
- [ ] **P6-4** 常驻监听 (D15, D27, D28, D30, D31, D32): 设置三行; 互斥组件与查询, "需要先关闭 xxx" 提示与 "打开 xxx 设置"; `specialUse` 常驻服务持有 `openWakeSession`; 关键词编辑 (至多 5 条, 显示 Macaw 返回的拼写 warning); 灵敏度 3 档实测定值并回填 D15; 打开应用即恢复, 重启后的恢复通知; 与聊天界面听写的暂停 / 恢复.
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
