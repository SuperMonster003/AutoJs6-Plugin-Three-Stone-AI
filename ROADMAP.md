# AI Text Generation 插件部署路线图

本文以可交付的用户结果为主线，而不是按测试方法或单个协议边界拆分进度。路线图中的 checkbox 只统计能够独立交付的 outcome；更细的测试矩阵、设备回执和发布审计分别进入证据索引、非阻塞证据债务与 Release Candidate 清单。

当前稳定边界为 LiteRT-LM 0.15.0、CPU backend、`text/plain`、单活动生成会话和 credit 背压流式输出。单模型导入/生成基线、AutoJs6 显式 public plugin `ai.ask`/prompt-only `ai.chat`/`ai.stream`，以及大模型导入进度与精确取消均已交付；当前阶段推进到多模型 catalog 与选择。

## 完成与证据规则

- `[x]` 表示 outcome 已达到其条目声明的最低证据等级；`[ ]` 表示实现或该最低证据仍未完成。
- **L1 实现证据**：生产路径已接线，focused 单元测试通过，且受影响模块能够构建。普通插件功能默认到 L1 即可完成。
- **L2 集成证据**：在 L1 之上，由 Android、Binder、PFD、fake provider 或等价的跨边界测试证明关键集成行为。只有实际跨进程或 Android 生命周期 outcome 才默认要求 L2。
- **L3 实机证据**：由可定位的 tested-source、签名 APK、真实插件/模型和设备结果证明端到端行为。只在 native/runtime 核心路径或 Release Candidate 明确要求时执行。
- 达到条目声明的最低等级后即可勾选；更高等级证据缺失只形成非阻塞证据债务，不反向打开已经完成的 outcome。
- soak、完整 API/ABI/设备矩阵和穷尽 hostile 组合不阻塞普通阶段；仅在对应产品风险发生变化或进入 Release Candidate 时提升为必需证据。
- 每阶段不再另设一组重复的退出 checkbox。该阶段所有 outcome 勾选即完成，条目末尾的 `L1`、`L2` 或 `L3` 就是其 Definition of Done。

## 总体进度

| 阶段 | 产品结果 | 状态 | 最低证据 | 主要实施仓库 |
| --- | --- | --- | --- | --- |
| D0 | 单模型本地 Provider 基线 | 已完成 | L1；真实生成 L3 | 当前插件仓库 |
| D1 | 显式 public `ai.*` 本地插件链路 | 已完成 | L2；真实 ask L3 | AutoJs6 与当前插件仓库 |
| D2 | 大模型导入进度与精确取消 | 已完成 | L1 | 当前插件仓库 |
| D3 | 多模型 catalog 与选择 | 当前阶段 | L1；迁移 L2 | 当前插件仓库 |
| D4 | model lease、安全删除与 GC | 规划 | L2 | 当前插件仓库 |
| D5 | 模型自检与稳定脱敏错误 | 规划 | L2；正常模型 L3 | 当前插件仓库 |
| D6 | Engine 复用、回收与性能 | 规划 | L2；性能结论 L3 | 当前插件仓库 |
| D7 | Structured JSON | 远期 | L2；真实模型 L3 | 当前插件仓库与 AutoJs6 |
| D8 | GPU/NPU profile | 远期 | L3 | 当前插件仓库；必要时演进协议 |

## D0：单模型本地 Provider 基线

- [x] 独立 `:provider` 进程以诚实 capability 提供 CPU-only LiteRT-LM 0.15.0、`text/plain`、单活动会话和 credit 背压流式输出；reasoning、tools、structured JSON 与 usage 保持关闭。`L1`
- [x] SAF 导入完成 8-byte `LITERTLM` header 早期校验、流式 SHA-256、空间上限、fsync/原子 pointer、pending transaction 恢复，并由稳定 SHA 派生的 `modelId` 驱动 UI 展示、复制和当前模型 listing。`L1`
- [x] 签名 release 插件与真实 2,583,085,056-byte LiteRT-LM 已通过 public Rhino `ai.ask(..., { plugin: ... })` 单次生成，tested-source、APK digest、signer 与可逆设备清理均有记录。`L3`

## D1：显式 public `ai.*` 本地插件链路

- [x] 显式 selector 固定 exact component、provider 和 model，并在 discovery、绑定及 dispatch 边界复查 UID、signer、package identity、协议和 capability；插件仍为 default-off。`L2`
- [x] public `ai.ask`、prompt-only `ai.chat` 与 `ai.stream` 已接入 production route；选择插件后不读取 vault、不进入 HTTP/cloud，也不静默 fallback。`L1`
- [x] 宿主管理 Binder/PFD ownership、callback UID、initial/后续 credit、唯一终态、timeout、cancel、Binder death 和 engine teardown，普通脚本无需 `JavaAdapter`、AIDL 或背压细节。`L2`
- [x] 真实插件/模型 public ask 已通过 L3；deterministic fake provider 的 public stream 已跨过 initial 8-credit 窗口自然完成，public chat 已返回规范化 12-key response。`stream/chat L2；ask L3`

## D2：大模型导入进度与精确取消

目标：让数 GiB 导入变得可观察、可精确取消，并在 Activity 重建、取消竞态或失败后继续保留原有可用模型。本阶段只要求 focused 单元测试与 Debug assemble，不要求 ADB、真实大模型、soak 或设备矩阵。

- [x] 导入状态公开 `VALIDATING`、`COPYING`、`PUBLISHING` 阶段、单调已处理字节和可选总字节；Activity 重建继续观察同一进程内任务且不重复启动。`L1`
- [x] 模型管理 UI 对已知大小显示确定进度，对未知大小显示已处理字节，并只为当前可取消 operation 提供明确取消操作。`L1`
- [x] 取消精确绑定 operation ID；cancel 与完成只有一个状态机胜者，取消请求后的晚到 progress/success 不得覆盖取消终态。`L1`
- [x] 取消或失败保留旧模型，释放本次 URI permission，并清理本次 temporary/pending 文件；相关 focused tests 与 Debug assemble 通过。`L1`

## D3：多模型 catalog 与选择

- [ ] 将单一 `current.json` 迁移为事务化 catalog，旧 schema 无损升级，异常或中断后只能看到完整旧状态或完整新状态。`L2`
- [ ] catalog 保存稳定 `modelId`、显示名、SHA-256、大小、导入时间和选择状态；相同内容重复导入幂等。`L1`
- [ ] `listModels` 返回全部公开模型，必要时使用有界 V1 page token，并在任何可见 catalog 变化后更新 `listingGeneration`。`L2`
- [ ] 模型管理 UI 支持导入、查看占用和显式选择；切换模型不复制大文件，也不破坏已经打开的旧模型会话。`L2`

## D4：model lease、安全删除与 GC

- [ ] 建立覆盖 model lookup、Engine 初始化和 Engine 存活期的跨进程 lease 或等价所有权协议。`L2`
- [ ] 活动或已租用模型的删除明确失败；未占用模型经确认后真实释放对应字节且不留下孤立 metadata/hash 文件。`L2`
- [ ] GC 仅处理 catalog 不可达且无 lease 的 generation，并对 publish、rename、delete 与进程终止提供有界恢复。`L2`

## D5：模型自检与稳定脱敏错误

- [ ] 在隔离 provider 中提供至少执行 `Engine.initialize()` 的显式自检，并与正式生成共享资源所有权门，避免同时加载第二份大模型。`L2`
- [ ] catalog 按 model SHA、runtime、backend 保存健康状态和验证时间；版本变化使旧结论失效，重用私有文件前复核完整性。`L1`
- [ ] 使用稳定 `AiError.providerCode` 区分格式、backend 不兼容、初始化、资源和缓存故障；公开错误及 Release 日志不暴露 prompt、生成文本、URI、私有路径或原始 native 消息。`L2`

## D6：Engine 复用、回收与性能

- [ ] 以 model SHA、backend config 和 runtime version 为键复用已初始化 Engine，每个请求仍创建独立 Conversation，错误 key 禁止交叉复用。`L2`
- [ ] cancel、timeout、native error、模型切换、空闲 TTL、内存压力和服务销毁会健康判定或淘汰 Engine，且不产生晚到 callback。`L2`
- [ ] 以脱敏方式记录 cold init、warm TTFT、吞吐、峰值 RSS 与取消延迟，并在固定真实设备/模型上证明 warm 路径的可复现改善。`L3`

## D7：Structured JSON

- [ ] 严格物化并限制 schema/JSON，验证 MIME、UTF-8、长度、SHA-256、深度、节点数、字符串和重复 decoded key；只有实际行为就绪后才声明 capability 并映射到 public `ai.*`。`L2`
- [ ] streaming/non-streaming 保持 aggregate 一致且不回归 `text/plain`，至少一个兼容真实模型完成 structured JSON smoke。`L3`

## D8：GPU/NPU profile

- [ ] 提供显式 CPU/GPU/NPU profile；backend 变化使 health 与 Engine cache 失效，fallback 只在用户明确启用时发生且禁止云端 fallback。`L2`
- [ ] 每个公开 backend 都有正确 native 打包、ABI 检查以及至少一组受支持模型/真实设备证据；实验性能力不得描述为普遍可用。`L3`

## 非阻塞证据债务

以下内容不会阻塞 D0–D8 的普通功能进度；只有相关风险变化或 Release Candidate 明确选中时才升级为门禁。

- 补充真实 clipboard 写入、Activity 重建后复制，以及将复制的 `modelId` 直接替换示例值的 UI device smoke。
- 补充真实模型 public `ai.chat`/`ai.stream`、实机主动 cancel 与 engine teardown；现有 chat/stream 设备证据仅覆盖 fake provider 自然完成。
- 按需补 cross-process reliable-pipe status、no-credit、运行中 package update/uninstall，以及尚未覆盖的 hostile 组合。
- 按 release 风险选择 release host、Android API/ABI/设备矩阵、长时间 soak 与性能回归；不以矩阵规模替代明确产品 outcome。

## 证据索引

- 插件生产基线：`3ef2702`（本地生成/provider）、`fe060ef`（原生 LITERTLM 导入与事务）、`fdea3f2`（稳定 modelId 展示/复制）。
- 宿主 public 路由：ask `e4297a688`/`64db31ea5`，stream `73be5a6aa`/`d635f34ca`，chat `2d256b99f`/`c35f199b8`；插件仓历史记录截至 `dff387a`。
- 真实 public ask：tested-source `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`，QV710AF65F/API 31/arm64-v8a，exact method `OK (1 test)`/7.071s。
- public fake-provider smokes：stream tested-source `50b43d00c`，`OK (1 test)`/1.645s；chat tested-source `b0aa6768484d6046551264f69ecc84b527bbb442`，`OK (1 test)`/1.54s。
- D2 最终 L1 gate：同一 Gradle invocation 的 `:app:testDebugUnitTest :app:assembleDebug` 在 21s 内 `BUILD SUCCESSFUL`，48 tasks（21 executed/27 up-to-date），`version.properties` hash 未变化；独立终审 High/Medium 为 0。
- 详细 Binder、PFD、hostile lifecycle、APK digest、signer 和 reversible cleanup 回执保存在 AutoJs6 的 `docs/dev/ai-plugin-protocol-evaluation.md`、`docs/dev/ai-text-plugin-protocol-v1.md` 与 `docs/dev/compiler-ai-protocol-conformance.md`；本文件不再复制长日志。

## Release Candidate 清单

以下清单按候选发布风险执行，不计入路线图 checkbox 总数，也不阻塞日常阶段推进。

- 运行与候选变更相关的 focused tests、lint、Debug/Release 构建和必要的 L2/L3 验证；不默认扩大到无关矩阵。
- 核对协议、capability、quota、descriptor ownership、credit、唯一终态和 default-off/no-fallback 边界未被绕过。
- 审计 Release 日志、公开错误、权限和数据路径；不得新增未设计的网络或广泛存储权限，也不得泄露敏感内容。
- 生成 10 种语言的 README/插件说明/更新日志，重复生成应保持工作树幂等且不产生意外差异。
- 冻结版本、`VERSION_BUILD`、APK metadata、ABI、签名和 digest，并保存 tested-source 与设备清理回执。
