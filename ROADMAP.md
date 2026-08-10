# AI Text Generation 插件路线图

本文将后续工作拆分为可验证的阶段. 路线图描述的是计划和退出条件, 不代表尚未勾选的能力已经可用, 也不承诺具体发布日期.

当前稳定边界仍是 LiteRT-LM 0.15.0, CPU backend, `text/plain`, 单活动生成会话和 credit 背压流式输出. R0 仍在进行中, 构建门禁已通过, 真实插件/模型 Android/device smoke 尚未执行. R1 已推进至默认关闭且未接线的 model-list policy/catalog、Android coordinator/`IAiModelListCallback` Binder transport、transport-independent session transcript/credit policy, 以及 Android exact-component session coordinator/`IAiTextCallback`/PFD transport 窄切片; 新增 session transport 已有 standalone K2、focused Gradle、三 APK assemble 与 QV710AF65F 正向 PARTIAL 证据, R2 至 R8 仍为规划项.

## 状态说明

- `[x]` 表示该工作项已有实现和可定位证据; 阶段是否完成仍由全部退出门槛决定.
- `[ ]` 表示尚未完成或尚未取得完整验证证据.
- 阶段表中的 `进行中` 只表示当前正在实施, 不表示该阶段已经完成.

## 阶段表

| 阶段 | 主题 | 状态 | 主要依赖 | 实施仓库 |
| --- | --- | --- | --- | --- |
| R0 | 基线与模型身份 | 进行中, 真实模型/clipboard/example device 待执行 | 无 | 当前插件仓库 |
| R1 | 宿主 `ai.*` adapter | 进行中, default-off model-list 与 Android session transport 窄切片 | R0, AutoJs6 宿主 | AutoJs6 主仓为主 |
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

### 退出门槛

- [ ] 导入模型后, 用户可以在模型管理界面直接查看并复制与 provider `listModels` 一致的 `modelId`.
- [ ] 复制值可以直接替换示例脚本中的 `MODEL_ID` 并成功打开会话.
- [x] 示例模型, 下载链接, SHA-256 派生关系和示例 `modelId` 在文档中无歧义.
- [x] 10 种语言的生成源, 生成结果和 Android 字符串保持一致.
- [x] 本阶段相关测试通过, lint 为 0 error, Debug/Release 构建成功.
- [ ] R0 的所有工作项都有代码, 测试或实机证据后, 阶段状态才可改为 `已完成`.

## R1: 宿主 `ai.*` adapter

目标: 让普通 AutoJs6 脚本通过稳定的 `ai.*` API 使用本地 provider, 不再要求脚本直接操作 AIDL, Binder, callback 和 credit.

当前已覆盖六个默认关闭、未接线且没有生产调用点的宿主基础切片: 只读 PackageManager discovery/reinspection, 显式组件 metadata-only Binder 握手, transport-independent 模型枚举 transcript policy/catalog, Android model-list coordinator 与 `IAiModelListCallback` Binder transport, transport-independent `AiTextProviderSessionPolicy`, 以及 Android exact-component session coordinator 与 `IAiTextCallback`/PFD transport. 新 session coordinator 在 dispatch 前及 session Binder 建立边界精确复查 package/component/pinned metadata, 只绑定显式 component, 有界严格解码 provider/session callback envelope, 并将 request PFD 同步借用、callback PFD 唯一异步所有权转移、Binder death、absolute deadline、process gate 与 per-component fuse 接入既有 transcript/credit policy. 它保持 initial 8 credits 与逐 chunk 补充, 不对已 dispatch 会话自动重试, 且只在 descriptor、remote control、binding 与已接纳 operation 全部 unwind 后释放终态/门禁. 该切片仍为 default-off/unwired; 没有 runtime/UI/`ai.*` 生产路由. 已取得的 standalone、Gradle、APK 与 device 证据仅为窄范围 PARTIAL, 不代表普通脚本已可使用本地 provider, 也不满足 R1 退出门槛.

### 工作项

- [x] 在 AutoJs6 中加入默认关闭且未接线的只读 PackageManager `exact-action discovery` 与 `exact-component reinspection`, 仅采集本地包身份事实.
- [x] 在 AutoJs6 中加入默认关闭且无生产调用点的显式组件 metadata-only Binder 握手, 每条路径复查其实际到达的身份边界, 成功路径最多执行绑定前/连接后/metadata 解码后三次精确身份复查, 并在 descriptor 验证后仅有界严格解码 provider info/capabilities; 以 absolute deadline 的失败/晚到结果忽略语义约束尝试, 仅当 deadline 到期时 interface descriptor, `getProviderInfo()` 或 `getCapabilities()` 同步 Binder 调用仍在执行才进程级熔断, 同步 decode 及 worker queue/绑定/最终身份复查超时不熔断; 不进入模型枚举/会话/PFD/`IAiTextCallback`, 且此熔断也不能硬中止已阻塞的 Binder 调用.
- [x] 在 discovery/selection policy 中核验 service action, exported/enabled 状态, binding permission, UID, signer, 宿主版本, ABI 和协议范围, 并以 JVM tests 覆盖.
- [x] 在 AutoJs6 中加入默认关闭且未接线的 transport-independent 模型枚举 transcript policy: 接收调用方提供的 callback-entry UID 并按 pinned provider 验证, 有界严格解码单页或多页结果及 provider error, 拒绝 listing generation 漂移, token 重放/循环, 重复 model ID, 能力不匹配及过期/重复 callback, 并让 callback/cancel/timeout/binder-death 竞态只有一个终态; 形成不可变 provider-pinned catalog, 且只为精确匹配并能力兼容的 model 生成 session plan; 不含 Android `IAiModelListCallback` adapter、实际 Binder model-list transport、`openSession`/PFD/`IAiTextCallback`、runtime/UI 或 `ai.*` 接线.
- [x] 在 AutoJs6 中加入默认关闭、未接线且没有生产调用点的 Android model-list coordinator 与 `IAiModelListCallback` Binder transport: 仅在 listing 初始化阶段复用同一个完成 descriptor 验证的 Binder 复验 provider metadata, 在每次初始或 continuation dispatch 前以 `AiTextProviderPackageSnapshot.samePackageIdentityAs` 精确复查 package/component identity; callback 在唯一一次 payload copy 前依次核验调用 UID、typed page/error envelope 大小和有界 flood slot; page-token ledger 按完整且稳定的 pinned identity 隔离并有界持有; 对任何已接纳的 `operationsInFlight` (exact PackageManager inspect、bind、prepare、dispatch、callback admission 等) 保留 watchdog, deadline 到期仍未 unwind 时熔断 exact component, fuse 不硬中止卡住的 operation, gate 保持 `BUSY` 到晚到 unwind; 不含 session/`openSession`、PFD、credit、`IAiTextCallback`、session dispatch、runtime/UI 或 `ai.*` 接线; 已有 isolated Gradle 与 QV710AF65F 正向 PARTIAL 证据.
- [x] 在 AutoJs6 中加入默认关闭、未接线且没有生产调用点的 transport-independent `AiTextProviderSessionPolicy`: 固定 plan/request/provider/model/context 一致性, 以同步 `openSession` 返回后的显式 commit gate 提交或丢弃 provisional callback transcript; callback 按 UID、typed envelope、descriptor ownership 的顺序 fail-closed, 自动发放 initial 8 credits 并按已接纳 chunk 补充 backpressure credit; 有界处理 started/chunk/usage/completed/failed/cancelled, 让 cancel/timeout/binder-death 与 provider 终态只有一个胜出, 并等待 request/callback descriptor、remote control 及所有 cleanup settled 后才发布终态; tool request 与 hostile tool-calls 均 fail-closed; 不含 Android `IAiTextCallback`/`openSession`/PFD adapter, 不在 session dispatch 前重新核验 package identity, 也不含实际 session dispatch、runtime/UI 或 `ai.*` 接线.
- [x] 在 AutoJs6 中加入默认关闭、未接线且没有生产调用点的 Android exact-component session coordinator 与 `IAiTextCallback`/PFD transport: dispatch 前及 Binder 边界精确复查 pinned identity/metadata, 仅向显式 component 绑定, 验证 provider/session interface descriptor 与 callback UID, 将 request PFD 仅借给同步 `openSession` 并在 callback admission 后才对 callback PFD 执行唯一异步所有权转移, 保留 initial 8 credits/逐 chunk 补充、absolute deadline、Binder death、唯一终态、late unwind 和 per-component fuse 语义; 已有 standalone K2 18/18 且同一产物 30 轮 540/540、focused Gradle 18 tests/0 failures、三 APK assemble 与 QV710AF65F 两个 `OK (1 test)` 的 PARTIAL 证据; 不含 runtime/UI/`ai.*` 接线.
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
- [ ] 增加可选真实 `.litertlm` device smoke suite, 模型不提交到 Git 仓库.
- [ ] 增加 CI 的单元测试, lint, Debug/Release 构建和生成文档一致性检查.
- [ ] 固定四个本地协议 AAR 的来源和 digest, 并执行 ABI/golden-wire 兼容检查.
- [ ] CI 不依赖发布签名秘密; 正式签名和 APK 验证继续作为受控发布步骤.

### 退出门槛

- [ ] provider 主要成功路径和 hostile 生命周期路径均由自动化 Service/Binder 测试覆盖.
- [ ] descriptor 和终态泄漏测试可重复运行且无偶发失败.
- [ ] 每个 pull request 自动运行测试, lint, 构建和文档一致性检查.
- [ ] 真实模型 smoke test 有明确的本地运行入口和结果记录格式.
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
