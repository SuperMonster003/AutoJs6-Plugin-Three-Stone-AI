# Roadmap: 多轮对话上下文治理

> 目标一句话: 把 "聊天记录" 与 "发给模型的上下文" 彻底解耦; 完整记录继续服务 UI, 真正发给模型的上下文由统一的 Context Compiler 按 token 预算编译, 并以水位驱动会话轮换, 使单轮输入恒定有界, 累计费用从近似 O(n²) 回落为 O(n).
>
> 制定日期: 2026-08-31.参考: 与 Codex 的方案讨论 (已逐项核实其现状描述), 结合本仓库实际结构调整.所有条目均可独立勾选,独立合入; 按 ID 引用 (如 "P1-3").

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

## 4. 总体设计

分层上下文 (编译产物按此优先级装箱, 超预算按完整 turn 驱逐, 不拆散 user/assistant 配对):

| 层 | 内容 | 保留策略 |
|---|---|---|
| L0 固定指令 | Binder 首轮 SYSTEM 消息; 启动器聊天暂为空槽位 | 必须原样保留 |
| L1 结构化工作记忆 | 目标 / 约束 / 已确认决定 / 待解决 / 偏好, 带来源消息 ID 与状态 | 固定 token 上限 (P2) |
| L2 摘要检查点 | 已移出原文窗口的旧 turn 的分段摘要 | 固定 token 上限 (P2) |
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
    └── SummaryCheckpointer      (P2: 检查点生成/验证/失效)
            │
            ▼
      AiBackendSession (stream / streamNext 不变)
```

关键机制:

- 轮换判定从 "轮数" 改为 "上下文水位": 记账值达到硬水位 → 下一轮前关闭旧 session, 用编译后的精简上下文重建.`MAXIMUM_BACKEND_TURNS` 保留为泄漏保护 (提高到 64), 不再是主策略.
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

- [x] **P1-1** `AiTargetLimits` 增加 `maximumContextTokens: Int?` (插件内部字段, 不经 `TargetPager` 透出 Binder); `OnlineAiBackend` 目录 codec 版本 +1 (fail-closed); 本地 target 暂不填, 走全局默认预算.
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
  - 状态: 2026-08-31 已在 G8441 上分别完成 40 轮在线与 40 轮 LiteRT 长会话.两条通道均由精确记账触发水位轮换, 裁剪仅保留完整 turn, 重建后回落至 45% 目标以内; 设置已恢复为 16K / Unlimited.数据、provider usage 异常边界与 P0 对比见 [`docs/dev/context-budget-smoke.md`](docs/dev/context-budget-smoke.md).

> P1 已于 2026-08-31 完成: 由客户端历史增长造成的输入曲线从 O(n) 锯齿封顶为常数带, 累计费用回落为 O(n).已知边界是 provider 可在客户端请求之外注入或上报固定开销; 该开销只能在本轮 usage 返回后被发现, 但会在下一轮触发重建.超出预算的旧原文目前直接遗忘, 由 P2 补记忆.

### P2 - 摘要检查点与结构化工作记忆 (把遗忘变成压缩)

- [ ] **P2-1** 数据模型: `ConversationContextState { coveredThroughMessageId, summarySegments[], workingMemory, schemaVersion }`; `SummarySegment` 带 `firstMessageId / lastMessageId / sourceHash`; `MemoryItem` 带 `sourceMessageIds / status (PROPOSED | CONFIRMED | REJECTED | SUPERSEDED)`; 随 `StoredConversation` 持久化, 会话历史 codec 升 v4 (fail-closed, 无迁移分支, D4).
  - 验收: codec 编解码单测; 旧 v3 文件按既有约定整体拒读.
- [ ] **P2-2** 触发与范围: 软水位后台生成; 每次只摘要 "下次台阶驱逐将移出原文窗口的完整 turn 段" (上个检查点之后), 绝不重摘全会话, 也绝不摘要进行中/失败/停止轮.
  - 验收: 单测验证检查点链连续覆盖,范围不重叠不跳跃.
- [ ] **P2-3** 摘要调用 (D1/D5): 用当前会话 target 新建一次性 backend session, 要求严格 JSON 输出 (target 具 `structuredJson` 能力时用 schema, 否则 prompt 约束 + 严格解析); 输入输出均设硬上限; 后台执行,与聊天互不阻塞, 失败重试 1 次后放弃并保留旧检查点, 聊天退化为纯截断继续可用.
  - 验收: 伪 backend 单测覆盖 成功 / 超限 / 解析失败 / 网络失败退化; 摘要调用不占用聊天的 backend session.
- [ ] **P2-4** 本地验证器: 引用的 `sourceMessageIds` 必须落在覆盖范围内且真实存在; 字段长度 / 条数硬上限; 状态机规则 - 模型未经用户确认的建议只能是 PROPOSED, 不得直接产出 CONFIRMED 决定; 验证失败整段丢弃, 保留旧检查点.
  - 验收: 单测逐条覆盖拒绝分支 (越界来源 / 超长 / 非法状态跃迁).
- [ ] **P2-5** 装配整合: `compileContext` 增加 L1 (工作记忆) / L2 (摘要段) 层, 按第 4 节优先级与各层上限装箱; 绝对保护水位时丢弃 L1/L2.
  - 验收: 单测覆盖各层上限,优先级,紧急降级路径.
- [ ] **P2-6** 失效规则: 编辑 / 删除 / 重新生成使 "覆盖该消息及之后" 的检查点与相关记忆失效并异步重建; target 切换保留检查点 (纯文本, 跨 target 通用); 检查点变更即触发 backend 重建 (fingerprint 变化).
  - 验收: 单测覆盖 编辑早于 / 晚于 检查点边界,连续编辑,重建期间再次编辑.
- [ ] **P2-7** 存储配套: `ConversationHistoryPolicy.normalized()` 裁剪最旧消息前, 确保其已被检查点覆盖 (未覆盖则先保留); 缓解 S8 写放大 - 首选对 `persistConversationNow` 节流合并 (流式增量期已有 UI 侧缓冲, 落盘可去抖), 不足时再评估拆分文件, 单独立项.
  - 验收: 单测验证 "未覆盖不裁剪"; 长会话下落盘频率可观测下降.
- [ ] **P2-8** 真机冒烟并记录: 60+ 轮跨话题长会话, 验证 (a) 单轮输入仍有界; (b) 询问 30 轮前的已确认约束能被正确回忆 (命中工作记忆/摘要); (c) 断网时摘要失败聊天不受影响.

### P3 - Binder 持久会话透明压缩 (堵住旁路, D2)

- [ ] **P3-1** 会话转写留存: `RemoteThreeStoneAiSession` 记录每轮 user prompt (来自 `PromptPlanner` 产物) 与完成输出 (来自 `StreamingOutputBuffer.snapshot()`), 仅成功轮入账; 首轮完整 history 与 SYSTEM 消息一并留存; 内存护栏 512 KiB, 超出即对最旧 turn 做台阶驱逐 (原文裁剪, 无 LLM 摘要, D2).
  - 验收: 单测覆盖 成功/失败/取消轮的入账边界,护栏驱逐.
- [ ] **P3-2** 记账与透明重建: 复用 `ContextAccounting` (usage 已随 `GenerationStatistics` 可得); 达到硬水位后, 在下一次 `generateNext` 前于插件内部关闭旧 backend session, 用 `compileContext` 产物 (SYSTEM 原样置顶 + 最近原文) 调用新 session 的 `stream()`; `sessionId`,`FixedConfiguration`,回调时序对脚本完全不变.
  - 验收: 伪 backend 单测 - 40+ 轮连续 `generateNext`, 断言脚本视角流式/完成/usage 事件序列与不重建时一致, 且底层 session 发生过重建,重建后输入有界.
- [ ] **P3-3** 硬保底: 极端情况下 (单轮 prompt 加保底原文仍超绝对保护水位) 沿用现有 fail-closed 路径与既有错误码关闭会话, 不引入新协议错误码.
  - 验收: 单测覆盖该路径; 错误码不超出宿主 API 现有集合.
- [ ] **P3-4** 文档: `plugin_instruction.md` 增补持久会话上下文行为说明 (透明压缩,护栏,硬保底), 按既有流程同步各语言资源; `docs/dev/` 增补设计要点.
  - 验收: `PluginInstructionCompatibilityTest` 通过.
- [ ] **P3-5** 真机冒烟并记录: AutoJs6 脚本经 provider 连续 100 轮对话 (本地与在线 target 各一轮次), 无溢出,无会话中断; usage 曲线有界.

### P4 - 可选优化 (按需立项, 非本 Roadmap 承诺)

- [ ] **P4-1** 前缀稳定性复核: 校验各协议适配器在台阶驱逐间隔内保持字节级稳定请求前缀 (L0/L1/L2 置前), 实测 provider prompt cache 命中率.
- [ ] **P4-2** 上下文用量 UI: 会话页展示 `已用 / 预算` 与分层构成; 提供 "立即压缩" 手动动作.
- [ ] **P4-3** FTS 历史召回 (L3 之外的按需片段召回): SQLite FTS 按 2~4 turn 分块索引, 关键词 + 时间权重选 2~4 片段计入预算; 涉及存储迁移, 需单独评估后立项.
- [ ] **P4-4** embedding 语义召回: 仅当 P4-3 证实不足时评估.

## 7. 阶段门槛

每阶段合入前须满足: (a) 该阶段全部单测通过且新增策略对象无 Android 依赖; (b) 真机冒烟记录落入 `docs/dev/` (含数据, 惯例同 `p3-host-public-api-smoke.md`); (c) 不改变宿主 API 面; (d) 失败路径显式测试 (摘要失败,网络失败,估算缺失).P0→P1→P2→P3 顺序推进, P2 与 P3 可在 P1 合入后并行.

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
5. FTS/Room 召回降级为 P4 可选项: 现有 AtomicFile 体系近期够用, 先以 P2-7 缓解写放大, 避免过早引入存储迁移.
6. 摘要失败退化路径按当前网络环境 (5xx/524 常见) 设计为常态而非异常 (D5).
