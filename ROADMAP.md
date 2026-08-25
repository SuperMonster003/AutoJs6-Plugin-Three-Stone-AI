# 3-Stone AI 插件路线图

3-Stone AI 是 AutoJs6 的官方 AI 插件. 长期目标: 从本地推理插件升级为 AutoJs6 的统一 AI Provider 与交互中心: 本地模型, 在线服务, 模型目录, 凭据, 会话历史与聊天 UI 均由插件管理; 宿主仅保留稳定的 `ai.*` API, 可信插件发现, 协议协商, 安全策略与路由.

路线图按可交付的功能组织. 每一项可独立勾选, 可独立验收; 勾选标准是 "功能在真机上可用" (更名与协议类条目以 "构建通过 + 全量扫描无产品级旧名残留" 为准).
当前已交付基线以源码, 测试与 CHANGELOG 为准, 不在此重复.

## 命名决策 (P0 记录)

| 场景 | 采用 | 示例 |
|---|---|---|
| 显示名 / 品牌 (全语言统一, 不翻译) | `3-Stone AI` | app_name, 聊天助手名, 文档标题 |
| 仓库 / 目录 / rootProject / APK 产物 | `Three-Stone-AI` / `three-stone-ai` | `AutoJs6-Plugin-Three-Stone-AI`, `autojs6-plugin-three-stone-ai-v1.2.0-arm64-v8a.apk` |
| applicationId / namespace / Kotlin 包 | `threestoneai` (Java 标识符不能以数字开头) | `io.github.supermonster003.autojs6.plugin.threestoneai` |
| 类名前缀 | `ThreeStoneAi` | `ThreeStoneAiProviderService` |
| provider id / engine id / INFO category | `three-stone-ai` 短横线系 | `autojs6.three-stone-ai`, `three-stone-ai` |
| 常量 / 线程名 / User-Agent | `THREE_STONE_AI` / `three-stone-ai` / `AutoJs6-Three-Stone-AI` | `THREE_STONE_AI_THEME_COLOR`, `three-stone-ai-worker` |

本轮按未发布项目处理, 不提供旧身份兼容层或迁移别名. 产品身份全部采用 3-Stone AI 命名; 跨进程协议直接采用中性 AI Provider 命名, 以免 P2 再进行一次破坏性改名. 当前源码, 资源, 文档, CHANGELOG 与构建产物均不得保留被替换身份的文字, 标识符, 路径或二进制依赖.

## P0 最终身份 3-Stone AI

### 插件: 构建与工程

- [x] `rootProject.name` 改为 `autojs6-plugin-three-stone-ai`, APK 产物名随之变更.
- [x] `applicationId` / `namespace` 改为 `io.github.supermonster003.autojs6.plugin.threestoneai` (不保留旧 package ID).
- [x] main/test 源码包目录整体迁移至 `plugin/threestoneai`, 删除历史遗留空包 `plugin/ai/text`.
- [x] 产品类与测试类改用 `ThreeStoneAi*`; 通用协议类改用 `AiProvider*` / `IAi*` 中性命名.
- [x] AndroidManifest: Application/InfoService/ProviderService 组件名同步; INFO category 改为 `three-stone-ai`; 两处 `requiresHostVersion` 由 5270 对齐为 5276; 发现 action 改为 `org.autojs.plugin.AI_PROVIDER`.
- [x] proguard keep 规则同步新包名与新类名.
- [x] 运行时标识: `PROVIDER_ID` = `autojs6.three-stone-ai`, `ENGINE`/plugin id = `three-stone-ai`, User-Agent, 线程名, 日志 TAG, Intent extra key, `THREE_STONE_AI_THEME_COLOR` 全部换新.

### 插件: 资源与文案

- [x] `app_name` = `3-Stone AI` (translatable=false), 新启动图标落位, About 页与 README 徽标引用同步.
- [x] `chat_role_assistant` 统一为品牌名 `3-Stone AI` (移除各语言旧译名覆盖).
- [x] 主题设置资源 key 改为 `app_settings_theme_three_stone_ai`, 10 语言 value 同步为 "3-Stone AI 橙色" 系.
- [x] `plugin_description` 统一使用私有本地模型与用户配置在线服务的最终产品能力描述, 不混入历史品牌词.

### 插件: 文档管线

- [x] `.readme/common.json`: repo_url/repo_slug 改 `Three-Stone-AI`, plugin_id/plugin_engine 改 `three-stone-ai`, protocol_provider_id 改 `autojs6.three-stone-ai`, plugin_action 改 `org.autojs.plugin.AI_PROVIDER`, 本地协议 AAR 改 `ai-provider-api.aar`.
- [x] `.readme/lang_*.json` × 10: 品牌词统一为 `3-Stone AI`, 能力描述统一为 local AI 语义.
- [x] `template_readme.md` 图标路径与 alt 文本更新.
- [x] 运行 `.python/generate_markdown.py` 重新生成 README × 11 与 CHANGELOG × 11, 确认幂等; 未发布 CHANGELOG 直接按最终身份改写.
- [x] `plugin_instruction.md` × 11: 标题, PLUGIN_PACKAGE, provider 组件名, provider id, 绑定失败文案与原始 Binder 示例协议 API 全部换新.

### 宿主 AutoJs6 同步

- [x] `ThreeStoneAiOfficialPlugin` 的 PACKAGE_NAME / CLASS_NAME / PROVIDER_ID 三项常量指向最终身份; `AiControlOptions` 与 `AiSettingsFragment` 引用同步.
- [x] 插件中心将 engine `three-stone-ai` 映射到中性 `AI_PROVIDER` action, 与插件 PluginInfo.id/engine 及 INFO category 保持一致.
- [x] 宿主 main/test/androidTest 的插件身份常量, 类型与测试文件名全部同步.
- [x] `docs/dev` 协议设计与验收文档全部按最终产品及中性协议身份重写.
- [x] 协议模块直接采用 `:plugin-api:ai-provider-api`: Kotlin/AIDL 包为 `org.autojs.plugin.ai.provider.api`, Binder 接口使用 `IAiProvider` / `IAiSession` / `IAiCallback`, TaggedWire schema 域使用 `AP` (`0x4150`), 宿主实现与一致性测试同步采用 `AiProvider*`.
- [x] fake provider 验收应用改为 `:test-apps:ai-provider-conformance`, 包名改为 `org.autojs.plugin.ai.provider.fake`; 不保留旧 module alias, package bridge 或 action filter.

### 验证 (离线优先, 避免外网 5xx)

- [x] 插件 `gradlew --offline :app:testDebugUnitTest :app:assembleDebug` 通过.
- [x] 宿主 `ai-provider-api` 与 `ai-provider-conformance` 单测/构建通过, App AI 路由及设置相关 213 个单测通过.
- [x] 双仓全量扫描: 被替换身份的 CamelCase, kebab-case, package/action, 自然语言品牌词, 文件名, 目录名与 AAR 均为 0 残留, 不设白名单.
- [ ] 真机冒烟: 安装新包名 APK, `ai.ask("...", { plugin: true })` 走通, 宿主 "AI 服务设置" 跳转插件正常, 插件中心识别正常. (需真机, 由维护者执行)

### 发布收尾 (发布时执行)

- [x] 未发布 CHANGELOG 按最终产品与中性协议身份改写, 重新生成文档.
- [ ] 首次发布前确认 GitHub 仓库名为 `AutoJs6-Plugin-Three-Stone-AI`, 按发布清单产出三个 ABI 签名 APK.

## P1 插件内统一 AI Backend (在线 + 本地, 不改宿主默认路由)

先在插件内部形成统一的 "调用目标 (target)" 抽象与聊天产品体验, 最快验证合并价值.

- [x] 引入 `AiBackend` / `AiBackendSession` 通用抽象 (catalog / capabilities / createSession / stream / cancel), 现有 LiteRT 原生会话逻辑收敛到 `LiteRtLocalBackend`, 不重写; 启动器聊天与独立 `:provider` 进程的 Binder Service 共用同一 backend 实现及会话路径, 各进程实例由 Application 持有.
- [x] 建立 `AiTarget` / `AiTargetCatalog` 值模型: 已导入本地模型统一映射为 `local:*` target, 含 targetId, backend/provider/model, locality, configured/available, capabilities, 执行 profile 与上下文/输出上限.
- [x] 在线配置档案领域与目录层: 严格有界且不含凭据的 JSON, canonical UUID, HTTPS-only base URL, provider/origin 变更时强制明确替换或清除凭据, 跨进程锁 + fsync + 原子发布; 档案映射为 `profile:*` REMOTE/PLUGIN_MANAGED target 并与 `local:*` 合入 Application 级统一目录及会话分发. HTTP 执行器落地前在线 target 如实保持 `available=false`, Binder V1 模型列表仍仅公开本地模型.
- [x] 插件自有凭据仓库: Android Keystore AES-256-GCM 主密钥, 与 profile 绑定的认证密文, 应用私有 hash 文件名, fsync + 原子 rename, 进程内互斥 + 跨进程文件锁; 对外仅查询 configured, 插件内部仅在同步回调中短暂解密并在成功或异常后立即清零, 固定错误消息不携带底层敏感原因.
- [ ] 在线档案与凭据真机安全冒烟: 验证默认进程写入后 `:provider` 可读取一致的非敏感档案与凭据状态, 并覆盖跨进程替换/清除, provider/origin 变更时的凭据重录, 进程终止重启, 锁屏重启, 元数据/密文损坏及清除应用数据后的 fail-closed 行为. (需真机, 由维护者执行)
- [x] OpenAI Compatible Backend (自定义 baseUrl + key + 模型名): Application 级执行器通过统一 `AiBackendSession` 提供完成轮次多轮历史, 有界 SSE 与 JSON 回退流式响应, 精确取消, provider usage, JSON Schema 请求映射及固定且不含敏感信息的错误; 仅访问 profile 声明的 HTTPS 来源, 禁止重定向, 自动重试, cookie, cache, authenticator 及请求观察器, 不提供本地/在线自动回退. AI Provider V1 宿主路由仍按设计仅公开本地模型.
- [ ] OpenAI Compatible 真机互通与安全冒烟: 使用维护者控制的 HTTPS 测试 endpoint 验证自定义 baseUrl/key/model, SSE 与 JSON 回退, 长响应取消, 401/403/429/5xx, malformed/oversized response, profile/key 并发替换, 进程终止及网络切换. (需真机与测试凭据, 由维护者执行)
- [x] 预置提供方模板: OpenAI / Anthropic / Gemini / DeepSeek / OpenRouter 与宿主现有在线目录顺序及默认 baseUrl 对齐; 模型 ID 仍由 profile 明确填写. OpenAI/DeepSeek/OpenRouter 复用 OpenAI-compatible 格式, Anthropic Messages 与 Gemini GenerateContent 各自使用原生请求, 认证, SSE 终态, usage 与 JSON Schema 映射; 通用在线执行层不提供协议间或本地/在线自动回退. [开发契约](docs/dev/online-provider-backend.md)
- [x] 在线服务设置页实现与离线验收: 10 语言配置档案添加/编辑/删除, 不回显 Key 的替换与清除, 默认在线目标选择, 实际执行前生效的移动/计量网络开关, 用户确认且可取消的 120 秒有界连接测试; 非敏感设置与档案共用 schema 2 跨进程原子文档, 未发布项目不保留 schema 1 兼容读取. [开发契约](docs/dev/online-provider-backend.md)
- [ ] 在线服务设置页真机冒烟: G8441 / Android 9 已验证空配置初始化, 并修复系统 `/data/user/0` 到 `/data/data` 的可信路径规范化误判; 同一设备已在插件端新增并配置 PoloAPI OpenAI-compatible profile, 连接测试成功且实测耗时不足 10 秒. 仍需覆盖其余提供方, 编辑/删除, provider/origin 变更强制重录 Key, 默认目标跨进程可见, Wi-Fi/移动及计量网络切换, 连接测试取消/超时/错误映射, 旋转与进程重启. (需维护者控制的测试凭据, 由维护者执行)
- [x] 聊天 UI 目标选择器: 每个会话固定默认 target; 切换目标默认建议新会话, 继续当前会话需明确确认并记录目标快照. G8441 / Android 9 已验证统一目录中的 PoloAPI Cloud target 绑定, 费用提示, 当前项勾选与重复选择无副作用; 多 target 和已有消息会话的分支由策略测试覆盖.
- [ ] 会话历史逐条保存实际 target/provider/model/locality 快照; "重新生成" 默认沿用原响应目标. 源码与离线验收已完成: 历史格式直接升级为 version 3, assistant 消息强制保存 backend 实际目标, 精确重新生成允许显示名变更但拒绝 provider/model/locality 漂移, 且不回退到会话默认目标; 待维护者在真机完成一次生成与重新生成后勾选.
- [ ] 会话界面常显目标徽标: Local/Cloud, 提供方, 模型名; 次要信息展示 usage 与耗时, 在线目标标注可能产生费用.
- [ ] 失败不静默跨界: 本地失败绝不自动转在线, 在线失败绝不自动转本地; 均给出明确错误与手动切换入口.
- [ ] 统一流式管线: 在线与本地共用 Markdown 渲染, 取消, 重试, usage 与错误展示; 插件 UI 与 Binder Service 调用同一 `AiBackend` 层.

## P2 通用 AI Provider 协议 V2 (宿主, 中性命名)

- [ ] 将现有中性 `plugin-api/ai-provider-api` 从 V1 模型目录语义扩展到 V2 统一目标语义, 复用 `ai-common-api` 的 locality/credential 定义; 直接升级且不增加旧接口兼容层.
- [ ] 定义 `AiTargetInfo`: targetId, providerId, profileId, modelId, displayName, locality, capabilities, availability, configured, 上下文/输出上限, supportedControls, declaredOrigins.
- [ ] 统一 Catalog 接口: 目标目录分页枚举替代 "仅本地模型列表"; 本地 backend profile 降级为可选扩展字段.
- [ ] 标准化流式事件: text / reasoning / toolCall / usage / finishReason, 与终态语义 (completed / failed / cancelled) 一致化.
- [ ] 放开 `REMOTE` / `HYBRID` provider: 执行器不再仅接受 ON_DEVICE + NONE; 强制 HTTPS 来源声明校验与 `PLUGIN_MANAGED` 凭据模式, 宿主会话层继续拒收任何凭据字节.
- [ ] 协议一致性测试: fake provider 扩展 remote/hybrid 用例, descriptor 与信任校验 fail-closed 行为回归.

## P3 宿主 `ai.*` 全量接通插件

- [ ] `ai.ask/chat/stream/session` 支持统一 `target` 选择器 (`local:*` / `profile:*`); `plugin: true` 直接映射官方默认本地目标.
- [ ] 新增 `ai.catalog()`: 返回本地与在线全部目标 (id, displayName, provider, model, locality, configured, available, capabilities).
- [ ] `ai.models()` / `ai.profiles()` / `ai.providers()` / `ai.isConfigured()` 转为统一 Catalog 的兼容视图.
- [ ] 插件路由响应补齐 reasoning, toolCalls, finishReason, profile 与完整 usage, 与宿主在线路径能力对称.
- [ ] 错误码统一: 插件缺失/禁用/协议不兼容/目标未配置返回稳定错误 (如 `AI_PROVIDER_UNAVAILABLE`, `TARGET_NOT_CONFIGURED`), 不静默改路由.
- [ ] 宿主 d.ts 与 docs.autojs6.com 文档更新 (target 路由完整示例).

## P4 设置入口与配置迁移

- [ ] 宿主 "AI 服务设置" 页改为插件统一设置入口: 已安装跳插件设置, 未安装/被禁用显示安装或启用引导.
- [ ] 在线 API Key 迁移采用 "插件内重新输入" 方案; 不设计宿主到插件的凭据传输通道, 设置公开契约禁止承载凭据.
- [ ] 宿主既有在线配置转只读兼容 (旧脚本 `profile` 调用仍可用), 设置页提供迁移提示.
- [ ] 脚本内裸 `apiKey`/`baseUrl` 用法进入弃用周期: 文档标注, 运行时弃用提示, 推荐 `target`.

## P5 宿主瘦身 (兼容期后)

- [ ] 评估并移除宿主在线提供方 HTTP 实现与请求构造 (`AiRequestFactory` 等), `ai.*` 在线能力完全由插件承载.
- [ ] 移除宿主在线配置编辑 UI 与 `AiProviderVault` 写路径 (保留只读迁移提示至少一个版本).
- [ ] 宿主最终仅保留: `ai.*` API 外观, 插件发现与信任, 协议协商, Binder 生命周期, 错误规范化与兼容层.
- [ ] 插件与宿主切换 V2 后直接移除 V1 协议语义与测试夹具.

## 设计边界 (不做的事)

- 不做任何静默跨界回退: 本地与在线互不自动切换, 隐私边界与费用边界只能由用户跨越.
- 凭据只存在插件进程内 (Keystore 加密); 宿主, 日志, 设置同步契约与协议对象均不得出现 API Key 或授权头.
- 不因合并在线能力改变本地推理承诺: 本地目标推理不联网不上传; `INTERNET` 权限仅用于用户明确发起的模型下载与用户配置的在线目标请求.
- 不做联网模型发现或任意 URL 模型下载; 在线目标仅访问用户配置且声明过的 HTTPS 来源.
- capability 声明与实际行为保持一致, 未验证不声明.

## 发布清单 (每个版本)

1. `.\gradlew.bat --offline :app:testDebugUnitTest :app:assembleDebug` 通过 (网络异常环境优先离线; 依赖变更时才允许在线同步).
2. 更新 `.changelog/lang_*.json` 与 `.readme/lang_*.json`, 运行 `.python/generate_markdown.py`, 确认工作树幂等.
3. `.\gradlew.bat :app:assembleRelease` 产出 arm64-v8a / x86_64 / universal 三个签名 APK.
4. 真机安装, 用插件说明中的快速开始脚本冒烟一次; 涉及宿主协同的版本同时验证宿主设置页跳转与插件中心识别.
