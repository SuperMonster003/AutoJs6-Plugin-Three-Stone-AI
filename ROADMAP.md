# AI Text Generation 插件路线图

本文将后续工作拆分为可验证的阶段. 路线图描述的是计划和退出条件, 不代表尚未勾选的能力已经可用, 也不承诺具体发布日期.

当前稳定边界仍是 LiteRT-LM 0.15.0, CPU backend, `text/plain`, 单活动生成会话和 credit 背压流式输出. R0 仍在进行中; 构建门禁以及真实插件/模型的 public `ai.ask` Android/device smoke 已通过, 但 clipboard 写入与 Activity 重建后复制仍待验证. R1 已推进至默认关闭的 model-list/session 基础设施、hostile Android session conformance H1/H2 窄切片, 以及显式 `ai.ask(..., { plugin: ... })` production route 与真实单次 non-stream 设备 smoke; 证据仍仅限单设备、单模型和 appDebug 宿主, 不涵盖 UI/`chat`/`stream` 或广义 R1 退出门槛, R2 至 R8 仍为规划项.

## 状态说明

- `[x]` 表示该工作项已有实现和可定位证据; 阶段是否完成仍由全部退出门槛决定.
- `[ ]` 表示尚未完成或尚未取得完整验证证据.
- 阶段表中的 `进行中` 只表示当前正在实施, 不表示该阶段已经完成.

## 阶段表

| 阶段 | 主题 | 状态 | 主要依赖 | 实施仓库 |
| --- | --- | --- | --- | --- |
| R0 | 基线与模型身份 | 进行中, 真实模型/public `ai.ask` device 已覆盖, clipboard/view-copy 待执行 | 无 | 当前插件仓库 |
| R1 | 宿主 `ai.*` adapter | 进行中, default-off transport、hostile conformance、显式 `ai.ask` route 与真实 one-shot smoke 窄切片 | R0, AutoJs6 宿主 | AutoJs6 主仓为主 |
| R2 | 多模型与安全 GC | 规划 | R0 | 当前插件仓库 |
| R3 | 模型自检与稳定错误码 | 规划 | R0, R2 catalog | 当前插件仓库 |
| R4 | Engine 复用与性能 | 规划 | R2 model lease, R3 健康状态 | 当前插件仓库 |
| R5 | 导入进度与取消 | 规划 | R0, 现有导入事务 | 当前插件仓库 |
| R6 | 服务级测试与 CI | 规划 | R0; 后续阶段持续接入 | 当前插件仓库 |
| R7 | Structured JSON | 规划 | R3, AI Text Generation 协议 V1 | 当前插件仓库; 宿主 adapter 提供易用入口 |
| R8 | GPU/NPU backend | 规划 | R3, R4, R6, 设备和模型矩阵 | 当前插件仓库; per-request 选择需宿主或协议演进 |

## R0: 基线与模型身份

目标: 固化当前可工作的导入与生成基线, 让用户无需自行计算即可获得脚本需要的稳定 `modelId`, 并让示例模型和本机导入模型之间的关系清晰可核验.

### 工作项

- [x] 固化 R0 至 R8 的阶段依赖, 可勾选工作项, 退出门槛和共同完成标准.
- [x] 在模型管理界面显示当前模型的 `modelId`.
- [x] 提供复制 `modelId` 的明确操作, 并在没有模型时保持隐藏.
- [x] 保持 `modelId` 唯一来源为已验证 SHA-256 的稳定派生规则, 不从文件名或下载地址推断.
- [x] 以纯 JVM presentation 测试覆盖无模型, 导入中保留旧模型, 导入成功和导入失败状态.
- [ ] 以 Android/device smoke 覆盖真实剪贴板写入和 Activity 重建后的复制操作.
- [x] 在插件使用说明中提供可复制的 Binder 示例, 明确示例模型名称, 下载链接和示例专用 `modelId`.
- [x] 明确提示其他模型必须使用模型管理界面显示的 `modelId`, 不能照抄示例值.
- [x] 在 10 种语言中同步模型身份相关界面文案和插件使用说明.
- [ ] 记录当前基线验证结果, 包括单元测试, lint, Debug/Release 构建和示例脚本实机结果.

### 已记录证据

- 2026-08-10: `testDebugUnitTest` 共 32 tests/0 failures; `lintDebug` 为 0 error; `assembleDebug` 与 `assembleRelease` 均 `BUILD SUCCESSFUL`, 总耗时 3m37s; `VERSION_BUILD`/`BUILD_TIME` 未变化. 设备 smoke 与示例脚本实机结果仍待执行.
- 2026-08-11: 在 QV710AF65F (API 31, arm64-v8a) 以真实 release 插件和 2,583,085,056-byte LiteRT-LM 通过 Rhino global `ai.ask(..., { plugin: ... })` 完成单次 non-stream 生成; exact instrumentation 方法为 `OK (1 test)`/7.071s. 插件的 test/lint/Debug/Release 与宿主 focused 19/19 tests/assemble 均通过. 这是 public `ai.ask` 生成脚本证据, 不是使用说明中 raw Binder/`JavaAdapter` 示例的实机结果, 也未验证 clipboard/view-copy 和将复制值替换示例 `MODEL_ID`; 因此上述 R0 工作项与退出门槛 50/51 仍为 `[ ]`.

### 退出门槛

- [ ] 导入模型后, 用户可以在模型管理界面直接查看并复制与 provider `listModels` 一致的 `modelId`.
- [ ] 复制值可以直接替换示例脚本中的 `MODEL_ID` 并成功打开会话.
- [x] 示例模型, 下载链接, SHA-256 派生关系和示例 `modelId` 在文档中无歧义.
- [x] 10 种语言的生成源, 生成结果和 Android 字符串保持一致.
- [x] 本阶段相关测试通过, lint 为 0 error, Debug/Release 构建成功.
- [ ] R0 的所有工作项都有代码, 测试或实机证据后, 阶段状态才可改为 `已完成`.

## R1: 宿主 `ai.*` adapter

目标: 让普通 AutoJs6 脚本通过稳定的 `ai.*` API 使用本地 provider, 不再要求脚本直接操作 AIDL, Binder, callback 和 credit.

当前已覆盖六个默认关闭、未接线且没有生产调用点的宿主基础切片: 只读 PackageManager discovery/reinspection, 显式组件 metadata-only Binder 握手, transport-independent 模型枚举 transcript policy/catalog, Android model-list coordinator 与 `IAiModelListCallback` Binder transport, transport-independent `AiTextProviderSessionPolicy`, 以及 Android exact-component session coordinator 与 `IAiTextCallback`/PFD transport; 第六个切片现另有 opt-in hostile fake provider H1/H2 instrumentation 窄验证. 显式 `ai.ask(..., { plugin: ... })` production route 已在此基础上接线, 且已有一个 opt-in 真实插件/模型的 public Rhino one-shot device smoke. 这个窄证据证明普通脚本无需 `JavaAdapter`/AIDL 即可完成一次 non-stream 本地生成, 但尚未覆盖 stream/`chat`、UI、所有失败形态或 release 宿主/API 矩阵; 因此广义 R1 工作项和退出门槛仍保持未勾选.

### 工作项

- [x] 在 AutoJs6 中加入默认关闭且未接线的只读 PackageManager `exact-action discovery` 与 `exact-component reinspection`, 仅采集本地包身份事实.
- [x] 在 AutoJs6 中加入默认关闭且无生产调用点的显式组件 metadata-only Binder 握手, 每条路径复查其实际到达的身份边界, 成功路径最多执行绑定前/连接后/metadata 解码后三次精确身份复查, 并在 descriptor 验证后仅有界严格解码 provider info/capabilities; 以 absolute deadline 的失败/晚到结果忽略语义约束尝试, 仅当 deadline 到期时 interface descriptor, `getProviderInfo()` 或 `getCapabilities()` 同步 Binder 调用仍在执行才进程级熔断, 同步 decode 及 worker queue/绑定/最终身份复查超时不熔断; 不进入模型枚举/会话/PFD/`IAiTextCallback`, 且此熔断也不能硬中止已阻塞的 Binder 调用.
- [x] 在 discovery/selection policy 中核验 service action, exported/enabled 状态, binding permission, UID, signer, 宿主版本, ABI 和协议范围, 并以 JVM tests 覆盖.
- [x] 在 AutoJs6 中加入默认关闭且未接线的 transport-independent 模型枚举 transcript policy: 接收调用方提供的 callback-entry UID 并按 pinned provider 验证, 有界严格解码单页或多页结果及 provider error, 拒绝 listing generation 漂移, token 重放/循环, 重复 model ID, 能力不匹配及过期/重复 callback, 并让 callback/cancel/timeout/binder-death 竞态只有一个终态; 形成不可变 provider-pinned catalog, 且只为精确匹配并能力兼容的 model 生成 session plan; 不含 Android `IAiModelListCallback` adapter、实际 Binder model-list transport、`openSession`/PFD/`IAiTextCallback`、runtime/UI 或 `ai.*` 接线.
- [x] 在 AutoJs6 中加入默认关闭、未接线且没有生产调用点的 Android model-list coordinator 与 `IAiModelListCallback` Binder transport: 仅在 listing 初始化阶段复用同一个完成 descriptor 验证的 Binder 复验 provider metadata, 在每次初始或 continuation dispatch 前以 `AiTextProviderPackageSnapshot.samePackageIdentityAs` 精确复查 package/component identity; callback 在唯一一次 payload copy 前依次核验调用 UID、typed page/error envelope 大小和有界 flood slot; page-token ledger 按完整且稳定的 pinned identity 隔离并有界持有; 对任何已接纳的 `operationsInFlight` (exact PackageManager inspect、bind、prepare、dispatch、callback admission 等) 保留 watchdog, deadline 到期仍未 unwind 时熔断 exact component, fuse 不硬中止卡住的 operation, gate 保持 `BUSY` 到晚到 unwind; 不含 session/`openSession`、PFD、credit、`IAiTextCallback`、session dispatch、runtime/UI 或 `ai.*` 接线; 已有 isolated Gradle 与 QV710AF65F 正向 PARTIAL 证据.
- [x] 在 AutoJs6 中加入默认关闭、未接线且没有生产调用点的 transport-independent `AiTextProviderSessionPolicy`: 固定 plan/request/provider/model/context 一致性, 以同步 `openSession` 返回后的显式 commit gate 提交或丢弃 provisional callback transcript; callback 按 UID、typed envelope、descriptor ownership 的顺序 fail-closed, 自动发放 initial 8 credits 并按已接纳 chunk 补充 backpressure credit; 有界处理 started/chunk/usage/completed/failed/cancelled, 让 cancel/timeout/binder-death 与 provider 终态只有一个胜出, 并等待 request/callback descriptor、remote control 及所有 cleanup settled 后才发布终态; tool request 与 hostile tool-calls 均 fail-closed; 不含 Android `IAiTextCallback`/`openSession`/PFD adapter, 不在 session dispatch 前重新核验 package identity, 也不含实际 session dispatch、runtime/UI 或 `ai.*` 接线.
- [x] 在 AutoJs6 中加入默认关闭、未接线且没有生产调用点的 Android exact-component session coordinator 与 `IAiTextCallback`/PFD transport: dispatch 前及 Binder 边界精确复查 pinned identity/metadata, 仅向显式 component 绑定, 验证 provider/session interface descriptor 与 callback UID, 将 request PFD 仅借给同步 `openSession` 并在 callback admission 后才对 callback PFD 执行唯一异步所有权转移, 保留 initial 8 credits/逐 chunk 补充、absolute deadline、Binder death、唯一终态、late unwind 和 per-component fuse 语义; 已有 standalone K2 18/18 且同一产物 30 轮 540/540、focused Gradle 18 tests/0 failures、三 APK assemble 与 QV710AF65F 两个 `OK (1 test)` 的 PARTIAL 证据; 不含 runtime/UI/`ai.*` 接线.
- [x] 为上述 default-off/unwired Android session transport 增加 opt-in hostile conformance H1 窄切片: isolated H1 commits `edd10008f`/`06ebc788c`, 主仓集成 commits `0cbc19d9f`/`72eb4d0c0`; ordinary-pipe completion callback 验证跨进程 PFD ownership、exact length/EOF、SHA-256、UTF-8 物化与 cleanup, tool PFD 在任何执行前被唯一拒绝为 `TOOLS_UNSUPPORTED`, malformed transcript fail-closed, duplicate terminal 仅作有界 single-terminal smoke, stall 在 `Started` 后由 host cancel 并验证 owner/gate 可复用. 该 `[x]` 仅表示这些精确 H1 方法已有证据; H1 自身不覆盖 cross-process reliable status、wrong UID/no-credit、provider death、package update/uninstall、真实插件/模型、runtime/UI 或 `ai.*`, 也不勾选任何广义 R1 工作项或退出门槛.
- [x] 增加 hostile session H2 生命周期窄切片, 由主仓集成 commits `e5bd92b16`/`10dad3e39`/`0b9a94742` 记录: isolated-process callback 的实际 UID 与 pinned package UID 不同, host 在 decode 或发布 transcript 前以唯一 `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH` fail-closed 并释放 owner/gate; fake provider 在 `Started` 和 sequence 0 合法 chunk 已获 host credit replenishment acknowledgement 后结束进程, host 发布唯一 `BinderDied` 并释放 owner/gate. 该 Android 证据仅为这两个 exact 方法的 PARTIAL; no-credit 仍只有确定性 JVM policy 证据, package `lastUpdateTime` 漂移和 final reinspection `Completed(emptyList())` 仅模拟 update/uninstall 形状的 JVM 证据, 本轮没有执行任何实机 package update/uninstall. 因此 cross-process no-credit、实机 update/uninstall、真实插件/模型、runtime/UI 与 `ai.*` 仍未覆盖, 广义 R1 工作项和退出门槛保持 `[ ]`.
- [x] 在 AutoJs6 中接入显式 `ai.ask(..., { plugin: ... })` production route, 由主仓 commits `e4297a688`/`64db31ea5` 记录; 实机构建与 smoke 直接源为隔离树 commit `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`, 同一 test blob 后续在当前 main parent 上集成为 `7ce26ceea`, 两者 app tree 一致. selector 严格固定 exact component/provider/model; `plugin` 缺席时保留 legacy cloud 行为, 一旦出现则不读取 vault、不进入 HTTP/cloud、也不 fallback; 当前仅接纳单条 plain-text user message 与 non-stream 请求, 并在 engine close 时传播 cancel/close 到远端 session. standalone K2 为 15/15, focused Gradle 为 19/19 且 Android assemble 通过; QV710AF65F 上的真实插件/模型 exact method 为 `OK (1 test)`. 该 `[x]` 仍仅是单设备、单模型、appDebug 宿主的 one-shot 窄证据, 不含 UI、`chat`、`stream`、tools、structured output 或 usage; 广义 R1 工作项与全部退出门槛保持 `[ ]`.
- [ ] 固定精确 component, provider 和 model, 并在 dispatch 前重新核验身份.
- [ ] 实现 Binder 绑定, PFD 所有权, callback UID 校验和连接死亡处理.
- [ ] 自动管理初始 credit 和后续 credit, 不把背压细节暴露给普通脚本.
- [ ] 将脚本停止, cancel, timeout 和 engine teardown 传播到远端 session.
- [ ] 提供显式 provider/model 选择, 并显示 signer, locality, credential mode 和不可用原因.
- [ ] 定义协议文本, stream, completion 和 error 到现有 `ai.*` 返回值的兼容映射.
- [ ] 保持现有内置 provider 为默认基线, 禁止本地失败后静默回退到云端.

### 已记录证据

- 2026-08-10 isolated AutoJs6 Gradle gate: coordinator tests 为 15/0; host Debug、androidTest 与 fake provider APK 均成功 assemble; `VERSION_BUILD`/`BUILD_TIME` 未变化.
- 2026-08-10 QV710AF65F (API 31, arm64-v8a) 正向 PARTIAL: metadata handshake 与 model-list instrumentation 各 `OK (1 test)`; `pageSize=1` 收集四页及 fake provider 的四个 model. signer/APK hash、安装前后 identity、非 main callback 与唯一终态由 AutoJs6 主仓 evidence 详记; host、androidTest、fake provider 三包安装前均不存在, 验证后卸载并恢复为均不存在. 该证据只支持上述窄 R1 item, 不勾选任何 R1 退出门槛. QV710AF65F 没有真实插件或模型, 因此 R0 的真实模型、clipboard 与示例脚本实机验证仍未覆盖.
- 2026-08-10 transport-independent session policy 源码/静态 JVM 与 focused Gradle 证据: standalone Kotlin 2.3.21 K2/JDK 21/JVM 17 编译通过, 40/40 JUnit 通过, 同一产物 30 轮为 1200/1200 且零失败; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` 进程 exit 0, XML 汇总为 40 tests/0 skipped/0 failures/0 errors, Kotlin daemon 重试后以 fallback 编译仍成功. 该证据不包含 Android `IAiTextCallback`/`openSession`/PFD adapter、ADB/device、dispatch 前 package reinspection、runtime/UI 或 `ai.*`, 只支持上述窄 session policy item, 不勾选广义 R1 工作项或任何退出门槛.
- 2026-08-10 Android session transport 窄切片 PARTIAL 证据: standalone Kotlin 2.3.21 K2/JDK 21/JVM 17 的 18/18 JUnit 通过, 同一产物 30 轮为 540/540 且零失败; focused AutoJs6 Gradle 为 18 tests/0 failures, host Debug、androidTest 与 fake provider 三 APK 成功 assemble. QV710AF65F (API 31, arm64-v8a) 两个 instrumentation 方法均为 `OK (1 test)`: exact-component `openSession` 以 reliable-pipe request PFD 传入长文本, 按序收到 Started/Usage/Completed 与约 18 个 chunk, 跨过 initial 8-credit 窗口; Android descriptor owner 覆盖 exact/short/trailing/reliable-pipe producer error 及幂等 close. 该证据不包含 callback completion/tool PFD 的跨进程传输、wrong UID、hostile provider death/update、真实插件/模型、runtime/UI 或 `ai.*` 生产路由; 只支持上述窄 `[x]`, 广义 R1 工作项与退出门槛仍为 `[ ]`.
- 2026-08-10 hostile Android session conformance 窄切片 PARTIAL 证据: isolated H1 commits `edd10008f`/`06ebc788c`, 主仓集成 commits `0cbc19d9f`/`72eb4d0c0`; focused Gradle 的 `AiTextProviderSessionCoordinatorTest` 为 18 tests/0 failures, fake provider 为 31 tests/0 failures, host Debug、androidTest 与 fake provider 三 APK 均成功 assemble. 在 QV710AF65F (API 31, arm64-v8a) 上, 以下七个 exact `Class#method` instrumentation 分别返回 `OK (1 test)`:
  - `AiTextProviderSessionAndroidConformanceTest#completionDescriptorIsMaterializedAcrossProcess`: ordinary-pipe completion callback 的 PFD 跨进程唯一转移 ownership, 并以 exact length/EOF、SHA-256、UTF-8 物化及 cleanup 完成.
  - `AiTextProviderSessionAndroidConformanceTest#toolDescriptorIsOwnedThenRejectedWithoutExecution`: host 接管 tool PFD 后, 在任何 tool 执行前以唯一 `TOOLS_UNSUPPORTED` 终态拒绝并释放资源.
  - `AiTextProviderSessionAndroidConformanceTest#chunkBeforeStartFailsClosedAfterInitialCreditCommit`: initial-credit/open commit 后的 chunk-before-start fail-closed.
  - `AiTextProviderSessionAndroidConformanceTest#sequenceGapFailsClosedOnce`: sequence gap 以唯一终态 fail-closed.
  - `AiTextProviderSessionAndroidConformanceTest#invalidCompletionDescriptorReferenceFailsClosedOnce`: completion 的非法 descriptor reference 以唯一终态 fail-closed.
  - `AiTextProviderSessionAndroidConformanceTest#duplicateTerminalPublishesOnlyOneSettledOutcome`: duplicate terminal 在这次 smoke 中保持有界且仅发布一个 settled host 终态; 该单次方法不是竞态压力或穷尽证明.
  - `AiTextProviderSessionAndroidConformanceTest#stalledSessionCanBeCancelledAfterStartAndReleasesOwner`: stall 在 `Started` 后接受 host cancel, 发布唯一 `Cancelled`, 随后 owner/gate 可复用.
  该 H1 证据使用 ordinary pipe 验证 completion PFD 的跨进程内容与 ownership, 不声称 cross-process reliable-pipe producer status; 后者仍只有 process-local reliable error gate. H1 自身也不覆盖 wrong UID、no-credit、provider death、session 中 package update/uninstall、真实插件/模型、runtime/UI 或 `ai.*` 生产路由; 下列 H2 仅补充其中的 wrong-UID 与 provider-death 两个 exact Android 方法. 因此广义 R1 工作项和全部退出门槛保持 `[ ]`.
- 2026-08-11 hostile Android session H2 生命周期窄切片 PARTIAL 证据: 主仓集成 commits `e5bd92b16`/`10dad3e39`/`0b9a94742`; focused Gradle 的 `AiTextProviderSessionCoordinatorTest` 为 18 tests/0 failures, fake provider 为 31 tests/0 failures, host Debug、androidTest 与 fake provider 三 APK 均成功 assemble. QV710AF65F (API 31, arm64-v8a) 上两个 exact `Class#method` instrumentation 分别返回 `OK (1 test)`:
  - `AiTextProviderSessionAndroidConformanceTest#callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner`: isolated-process relay 以不同于 pinned package UID 的实际 Binder UID 发送 `Started`, host 在发布任何 transcript 前唯一拒绝为 `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH`, 随后 owner/gate 可复用.
  - `AiTextProviderSessionAndroidConformanceTest#providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner`: fake provider 发出一个 `Started` 和 sequence 0 合法 chunk, 等待 host credit replenishment 作为已处理 acknowledgement 后结束 provider 进程; host 仅发布一个 `BinderDied` 终态, 随后 owner/gate 可复用.
  三 APK 的 local/device SHA-256 均已精确一致: host `0685CE99C0F9E8F9056BE5F3A8EEBC2C7EA5FFCA2D422E21EAC69D0CB3364629`, androidTest `7C91A0AF651E2098AD124FF8A89AE3AC3018E0F1D0DEC068367595E964739178`, fake provider `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`; 三者 v2 signer certificate SHA-256 均为 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`. host、androidTest 与 fake provider 三包在安装前均为 absent, cleanup 后再次均为 absent. no-credit 仍只复用已有确定性 JVM 用例; update/uninstall 仅由 coordinator final reinspection 的 `lastUpdateTime` 漂移与 `Completed(emptyList())` JVM 用例覆盖形状, 本轮没有实机 package 变更. 该 Android 证据仅支持 wrong-UID/provider-death 两个窄方法, 不覆盖 cross-process no-credit、实机 update/uninstall、真实插件/模型、runtime/UI 或 `ai.*`; 广义 R1 工作项与全部退出门槛保持 `[ ]`.

- 2026-08-11 显式 `ai.ask` plugin route production source 接线窄切片: AutoJs6 主仓 commits `e4297a688`/`64db31ea5`; selector 严格固定 exact component/provider/model, `plugin` 缺席时保留 legacy cloud 行为, 一旦出现则禁止 vault、HTTP/cloud 与 fallback; 仅接纳单条 plain-text user message 和 non-stream 请求, engine close 会传播远端 session teardown. standalone Kotlin K2/JDK 21/JVM 17 为 15/15, focused Gradle 为 19 tests/0 failures, Android main source compile 通过. 这些均为 source-only 证据, 不包含真实插件/模型/device、UI、`chat` 或 `stream`; 广义 R1 工作项与退出门槛保持 `[ ]`.
- 2026-08-11 真实 public `ai.ask` device smoke: 构建与实机运行直接源为 AutoJs6 隔离树 commit `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`; 同一 test blob 已集成为当前 main parent 上的 `7ce26ceea`, 两者 app tree 一致, 但后者不是 APK 直接构建源. QV710AF65F (API 31, arm64-v8a) 上的 Rhino global `ai.ask(..., { plugin: ... })` 经 release 插件运行 2,583,085,056-byte LiteRT-LM. 模型 SHA-256 为 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, 派生 `modelId` 为 `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`; `AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin` 返回 `OK (1 test)`/7.071s. local/device APK SHA-256 精确一致: host `b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`, androidTest `09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf`, plugin `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`; 三者 v2 signer certificate SHA-256 均为 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`, device base 与候选 APK 一致. users 0/10 的 host/test/plugin 三包与专用 staging/UI-dump 路径在前置检查与 cleanup 后均为 absent. 该证据仅覆盖单设备/单模型/non-stream one-shot/appDebug 宿主; 不覆盖 clipboard、stream/`chat`、tools、structured output、usage、release 宿主/API 矩阵、实机 update/uninstall、性能或 soak. DocumentsUI 的 last-location 状态无法无损复原; 广义 R1 工作项与退出门槛仍为 `[ ]`.

### 退出门槛

- [ ] 普通脚本无需 `JavaAdapter` 或 AIDL 类型即可完成本地一次性和流式生成.
- [ ] provider 未安装, 签名不符, 模型缺失, 绑定失败和进程死亡都有稳定脚本错误.
- [ ] 脚本停止后 provider 不继续生成, 不继续回调, 不遗留 descriptor 或绑定.
- [ ] hostile fake provider 的身份, 生命周期, credit, descriptor 和终态测试通过.
- [ ] 选择本地 provider 时不存在任何隐式网络 fallback.

## R2: 多模型与安全 GC

目标: 将当前单一可见模型和隐藏历史代际演进为可管理的模型 catalog, 同时安全释放不再使用的私有存储.

### 工作项

- [ ] 将单一 `current.json` 演进为事务化 catalog, 并提供旧 schema 的无损迁移.
- [ ] 为每个模型保存 `modelId`, 显示名, SHA-256, 大小, 导入时间和选择状态.
- [ ] `listModels` 返回所有公开模型; 超过单页上限时实现 V1 page token.
- [ ] 任意可见 catalog 变化都更新 `listingGeneration`, 包括重命名, 删除和选择变化.
- [ ] 相同内容重复导入保持幂等, 不生成重复条目或重复大文件.
- [ ] 建立跨进程 model lease 或等价所有权机制, 覆盖查找, Engine 初始化和 Engine 存活期.
- [ ] 活动或已租用模型的删除操作明确失败; 未占用模型可以安全删除.
- [ ] 显示单模型和 catalog 总占用, 并提供明确的删除确认.
- [ ] 为 catalog publish, 文件 rename, 删除和进程终止补充崩溃恢复策略与测试.

### 退出门槛

- [ ] 连续导入多个模型后, `listModels` 返回全部稳定且唯一的模型 ID.
- [ ] 切换模型不复制模型文件, 也不破坏正在进行的旧模型会话.
- [ ] 删除未占用模型会真实释放对应字节, 且不留下孤立 metadata 或 hash 文件.
- [ ] 删除活动模型被安全拒绝, 会话结束后可以再次删除.
- [ ] 所有关键事务崩溃点均能恢复到完整旧状态或完整新状态.

## R3: 模型自检与稳定错误码

目标: 在正式生成前识别损坏或 backend 不兼容的模型, 并向宿主返回可操作但不泄露敏感信息的错误.

### 工作项

- [ ] 在隔离的 `:provider` 进程中提供显式模型验证, 至少完成 `Engine.initialize()`.
- [ ] 验证与正式生成共享资源所有权门, 避免同时加载多个大模型.
- [ ] catalog 保存 runtime 版本, backend, 最近验证时间和健康状态.
- [ ] runtime 或 backend 变化后将旧验证结果标记为待重新验证.
- [ ] 保留 backend Throwable 供内部分类, 不再在 callback 边界直接丢弃.
- [ ] 使用 `AiError.providerCode` 提供稳定分类, 例如格式不支持, backend 不兼容, 初始化失败, 资源不足和缓存失败.
- [ ] Release 日志和公开错误不包含 prompt, 生成内容, URI, 私有路径或原始 native 消息.
- [ ] 重用同 SHA 私有文件前复核完整性, 或使用新副本原子修复.

### 退出门槛

- [ ] 已知正常模型通过自检, 损坏模型不能被标记为健康.
- [ ] backend 不兼容, 初始化失败, 资源不足, cancel 和 provider process death 可被稳定区分.
- [ ] 模型自检不会与活动生成并行占用第二份 Engine.
- [ ] runtime 升级后不会继续展示过期的健康结论.
- [ ] 公开错误和 Release 日志通过敏感信息回归测试.

## R4: Engine 复用与性能

目标: 避免同一模型连续请求重复执行昂贵的 Engine 初始化, 同时保持 native 生命周期和内存释放可控.

### 工作项

- [ ] 增加以 model SHA, backend config 和 runtime version 为键的 Engine holder.
- [ ] 同一模型的连续请求复用已初始化 Engine, 每个请求创建独立 Conversation.
- [ ] 不同模型, backend 或 runtime 版本之间禁止错误复用.
- [ ] cancel, timeout 或 native error 后执行健康判定; 状态不确定时淘汰 Engine.
- [ ] model lease 持续到 Engine 真正关闭, 不只覆盖单次 Conversation.
- [ ] 增加空闲 TTL 和内存压力回收.
- [ ] 记录脱敏的 cold init, warm TTFT, 输出吞吐, 峰值 RSS 和取消延迟.
- [ ] 本阶段继续保持单活动生成会话, 不增加大模型并发.

### 退出门槛

- [ ] 同一模型第二次健康请求不会再次调用 `Engine.initialize()`.
- [ ] warm TTFT 有可复现改善, 并记录测试设备, 模型和测量方法.
- [ ] 空闲超时或内存压力后 Engine 和 native 内存可释放.
- [ ] cancel, timeout, 模型切换和服务销毁后没有晚到 callback.
- [ ] 一次失败的 Engine 不会污染下一次健康请求.

## R5: 导入进度与取消

目标: 为数 GiB 模型提供可理解, 可取消且不破坏现有模型的导入流程.

### 工作项

- [ ] Copier 上报已复制字节, 总字节和当前阶段.
- [ ] UI 区分头部校验, 复制, SHA-256 和原子发布阶段.
- [ ] 对已知大小显示确定进度, 对未知大小显示已复制字节.
- [ ] coordinator 持有可取消任务, 并提供明确取消操作.
- [ ] 取消后保留原 catalog 和当前模型, 清理临时文件和临时 URI 权限.
- [ ] Activity 重建后继续观察同一导入任务, 不重复启动或重复通知.
- [ ] 导入前显示预计空间需求, 当前可用空间和保留空间.
- [ ] 评估大文件前台任务或安全恢复; 任何恢复都必须重新验证最终 SHA-256.

### 退出门槛

- [ ] 已知大小, 未知大小, 慢速流, 零长度, 空间不足和 cancel 路径都有测试.
- [ ] header 失败不会写入模型正文.
- [ ] cancel 与完成竞态只产生一个终态.
- [ ] Activity 重建不丢失进度, 不产生第二个导入任务.
- [ ] 取消和失败不会改变原有可用模型.

## R6: 服务级测试与 CI

目标: 将当前 policy/helper 单元测试扩展到真实 Binder 生命周期和独立仓库的持续验证.

### 工作项

- [ ] 为 provider service 提供可注入 fake backend, 不在测试中加载大模型.
- [ ] 覆盖 bind, provider info, capabilities, listModels, openSession, credits, chunks 和 completion.
- [ ] 覆盖 callback death, bind death, timeout, cancel, close 和 service destroy 竞态.
- [ ] 覆盖 descriptor 重复, 缺失, 截断, 超长, pipe error 和所有权释放.
- [ ] 覆盖 worker/callback queue 饱和和唯一终态.
- [x] 增加可选真实 `.litertlm` device smoke suite, 模型不提交到 Git 仓库.
- [ ] 增加 CI 的单元测试, lint, Debug/Release 构建和生成文档一致性检查.
- [ ] 固定四个本地协议 AAR 的来源和 digest, 并执行 ABI/golden-wire 兼容检查.
- [ ] CI 不依赖发布签名秘密; 正式签名和 APK 验证继续作为受控发布步骤.

### 真实模型 smoke 本地入口与记录格式

前置条件: 在显式 serial 对应的 user 0 安装 host appDebug、androidTest 和 release plugin; 三个 APK 的 v2 signer 一致且 installed base SHA-256 与本地候选一致; 通过插件 SAF UI 导入真实 `.litertlm`, 并从已验证 SHA-256 派生与 UI 显示的 `modelId`. 模型只保存在本地, 不提交到 Git. 可复制入口为:

```text
adb -s QV710AF65F shell am instrument -w -r -e autojs.aiText.publicAskSmoke.enabled true -e autojs.aiText.publicAskSmoke.serial QV710AF65F -e autojs.aiText.publicAskSmoke.modelId litertlm.ab7838cdfc8f77e54d8ca45eadceb204 -e autojs.aiText.publicAskSmoke.timeoutMillis 600000 -e class org.autojs.autojs.core.plugin.ai.AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin org.autojs.autojs6.test/androidx.test.runner.AndroidJUnitRunner
```

每次结果记录固定包含: tested-source commit 与 integration commit, device serial/API/ABI, model bytes/SHA-256/`modelId`, host/androidTest/plugin APK SHA-256, v2 signer certificate SHA-256, local/device digest 是否一致, exact `Class#method`/runner, elapsed time, `OK (1 test)` 或完整失败终态, 以及 users 0/10 的 exact packages 与专用 staging/UI-dump 路径在安装前/cleanup 后的 absent/present 状态. 上述 2026-08-11 记录是该格式的首个完整样例.

### 退出门槛

- [ ] provider 主要成功路径和 hostile 生命周期路径均由自动化 Service/Binder 测试覆盖.
- [ ] descriptor 和终态泄漏测试可重复运行且无偶发失败.
- [ ] 每个 pull request 自动运行测试, lint, 构建和文档一致性检查.
- [x] 真实模型 smoke test 有明确的本地运行入口和结果记录格式.
- [ ] 协议 AAR 发生未审阅变化时 CI 明确失败.

## R7: Structured JSON

目标: 使用协议 V1 和 LiteRT-LM 的 response format 提供适合自动化脚本消费的受约束 JSON 输出.

### 工作项

- [ ] 仅在实现和验证完成后声明 `supportsStructuredJson`.
- [ ] 声明并验证 JSON response MIME type 和 schema MIME type.
- [ ] 严格物化 schema, 验证 UTF-8, 长度, SHA-256 和 JSON 复杂度限制.
- [ ] 将 schema 映射到 LiteRT-LM `ResponseFormat` 和对应 Conversation 配置.
- [ ] 允许流式 JSON 片段, 但只在完整 aggregate 通过严格 JSON 校验后完成会话.
- [ ] 对最终 JSON 再次验证深度, 节点数, 字符串上限和重复 decoded key.
- [ ] 为不兼容的模型或 runtime 保持能力关闭, 不做虚假 capability 声明.
- [ ] 在 R1 adapter 中提供脚本友好的 JSON 结果映射.

### 退出门槛

- [ ] 无 schema 和带 schema 请求均通过, 且覆盖 streaming/non-streaming.
- [ ] 非法 schema, 非法 UTF-8, 超深 JSON, 重复 key 和截断输出被稳定拒绝.
- [ ] 现有 `text/plain` 请求和 completion aggregate 无回归.
- [ ] 至少一个已验证真实模型完成 structured JSON smoke test.
- [ ] provider capability, model capability 和实际行为保持一致.

## R8: GPU/NPU backend

目标: 在明确的设备和模型兼容性证据基础上增加硬件加速, 不以硬件存在替代 backend 可用性验证.

### 工作项

- [ ] 提供 CPU, GPU 和 NPU 的显式 backend profile.
- [ ] 补齐 GPU 所需的可选 native library 声明和打包验证.
- [ ] NPU 验证设备库, native library path, ABI 和模型辅助数据.
- [ ] 按 `model × backend × runtime` 保存独立验证结果.
- [ ] backend 设置变化使相关 Engine cache 和 model listing/config generation 失效.
- [ ] fallback 必须由用户显式启用, 且只允许同一本地模型的受支持 backend; 禁止云端 fallback.
- [ ] 提供统一 benchmark, 记录 init, prefill, decode, TTFT 和峰值 RSS.
- [ ] 建立覆盖 CPU-only, GPU 可用, GPU 不兼容和 NPU 不可用的设备/模型矩阵.
- [ ] 如果需要 per-request backend 选择, 先在宿主或新协议中定义公开字段, 不使用隐藏字符串约定.

### 退出门槛

- [ ] backend 初始化失败不会循环重试, 崩溃 provider 或污染 CPU 路径.
- [ ] profile 切换不会复用错误 Engine, 也不会使用过期健康状态.
- [ ] Release APK 的 ABI 和 native library 声明经过构建产物验证.
- [ ] 每个公开 backend 至少有一组受支持模型和真实设备的可复现证据.
- [ ] UI, provider capability 和文档不会把实验性 NPU 支持描述为普遍可用.

## 共同完成标准

每个阶段只有在满足以下适用条件后才能标记为已完成:

- [ ] 所有新增状态和边界具有单元测试; Binder/native 相关工作具有对应 Service, instrumentation 或 device 证据.
- [ ] `testDebugUnitTest`, `lintDebug`, Debug 和 Release 构建通过, lint 为 0 error.
- [ ] 协议模型, capability, quota, descriptor ownership, credit 和唯一终态规则没有被绕过.
- [ ] 不记录或公开 prompt, 生成文本, URI, 私有路径, credential 或未经处理的 native 错误.
- [ ] 未经单独安全设计和用户同意, 不新增网络或广泛存储权限.
- [ ] 10 种语言的 README 源, 生成 README, 插件使用说明, 更新日志和 Android 文案按实际改动同步.
- [ ] 生成文档可重复生成且工作树不产生意外差异.
- [ ] 变更按逻辑拆分提交, 不混入无关文件.
- [ ] 发布前完成版本, `VERSION_BUILD`, APK metadata, ABI, 签名和 digest 验证.
- [ ] 阶段退出门槛有可定位的测试输出, 构建产物或实机记录, 不以“代码已写”替代完成证据.
