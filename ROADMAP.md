# On-Device AI 插件路线图

本插件是 AutoJs6 的官方设备端 AI 插件 (原名 AI Text Generation), 目标是把设备端 AI 推理能力以最低的脚本使用成本融入 AutoJs6:
脚本一行 `ai.ask("...", { plugin: true })` 即可在本地模型上完成推理, 不联网, 不上传数据.

路线图按可交付的用户功能组织. 每一项都是一个可独立勾选, 可独立验收的功能; 勾选标准就是 "功能在真机上可用".
不再为条目附加证据等级或测试矩阵要求; 功能出现问题时针对问题修复, 而不是预先堆砌验证.

## 已交付

### 插件本体 (v1.0.0)

- [x] AI Text Generation 协议 V1 设备端 provider: 独立 `:provider` 进程, CPU-only LiteRT-LM 0.15.0, `text/plain` 输入输出, credit 背压流式.
- [x] SAF 导入 `.litertlm` 模型: 流式 SHA-256 校验, 8 GiB 上限, 原子发布, 断点恢复, 导入进度与精确取消.
- [x] 多模型 catalog: 稳定 modelId (SHA 派生), 幂等重复导入, 分页 `listModels`, 模型管理界面查看/选择/复制 ID.
- [x] 会话安全: 单活动会话, 超时, 取消, Binder death 处理, 宿主包名/UID/签名核验.

### 宿主集成 (AutoJs6 build 5276+, v1.1.0)

- [x] `ai.ask` / `ai.chat` / `ai.stream` 本地插件路由: 显式 `plugin: { component, providerId, modelId }` 完整选择器.
- [x] `plugin: true` 简写选择器: 默认选中官方插件与 provider, 无需在脚本里硬编码组件名.
- [x] 单模型场景省略 `modelId`: 只导入一个模型时自动解析; 多模型时返回 `MODEL_AMBIGUOUS` 并提示用 `ai.models()`.
- [x] `ai.models({ plugin: true })`: 脚本枚举已导入模型 (modelId, displayName, 容量上限).
- [x] 插件未安装 / 未启用 / 无模型时的明确错误: `PROVIDER_NOT_FOUND` / `PROVIDER_DISABLED` / `MODEL_NOT_FOUND`, 错误消息附带解决指引.
- [x] 插件路由接入插件中心启用开关: 在插件中心停用插件后, 脚本调用收到 `PROVIDER_DISABLED` 而不是静默绑定.
- [x] 宿主 "AI 服务设置" 页提供本地 AI 插件入口: 已安装时跳插件模型管理页, 未安装时跳插件中心.
- [x] 插件中心识别本插件: INFO 服务入列, engine `ai-text-generation` 关联 provider action, 插件设置页可跳转插件启动页.

### 品牌与文档 (v1.1.0)

- [x] 插件更名为 On-Device AI (设备端 AI), 更新 11 语言应用名与描述.
- [x] 插件说明改为 "快速开始 (ai 模块) + 高级 (原始 Binder)" 双层结构, 10 语言同步.
- [x] README/CHANGELOG 与宿主文档同步更新, 移除 "实验性" 表述.

## 下一步 (v1.2)

围绕 "模型即资源" 补齐管理闭环, 全部在插件仓完成:

- [ ] 模型删除: 模型管理界面支持删除未选中的模型并真实释放存储; 删除当前选中模型时给出明确阻止提示.
- [ ] 存储回收: 清理历史保留的旧模型代际 (替换导入后遗留的 hash 命名文件), 提供 "一键清理未引用文件" 操作.
- [ ] 模型重命名: 允许修改 displayName 并同步到 `listModels` 与模型管理界面 (modelId 保持不变).
- [ ] 导入前预检: 打开系统选择器前检查可用空间, 并在界面上显示预计占用, 避免复制到一半才失败.
- [ ] 生成参数透传: 支持 temperature / topK / topP / maxTokens 等基础采样参数, 从 `ai.ask` options 一路传到 LiteRT-LM Engine.

## 中期 (v1.3+)

提升推理体验与性能:

- [ ] Engine 复用: 以 model SHA 为键缓存已初始化 Engine, 消除同模型连续请求的重复冷启动; 模型切换/内存压力/空闲超时时释放.
- [ ] 模型自检: 导入完成后可选执行一次 `Engine.initialize()` 健康检查, 在模型管理界面标记 "可用/不兼容", 避免脚本调用时才发现模型加载失败.
- [ ] system prompt 支持: 插件协议已接受 system 角色, 打通宿主 `ai.ask(messages, { plugin })` 的多消息传入 (system + user).
- [ ] 生成统计: 返回 tokens/耗时等基础 usage 信息, 填充 `ai.chat` 响应的 `usage` 字段.
- [ ] 宿主 d.ts 与文档: 为 `ai.*` 补充 TypeScript 声明与 docs.autojs6.com 文档页 (含 plugin 路由完整示例).

## 远期

- [ ] 多轮对话会话: 复用 Conversation 保持上下文, 提供 `ai.session()` 风格 API, 避免每轮重传全部历史.
- [ ] Structured JSON 输出: 协议已预留 structuredJson 能力位, 在真实模型上验证后开放.
- [ ] GPU/NPU backend: 提供显式 backend profile 与设备兼容性检测; 仅在真实设备验证通过后声明.
- [ ] 模型下载协助: 在插件内提供常用 `.litertlm` 模型的下载指引 (跳转浏览器, 不内置下载器).

## 设计边界 (不做的事)

- 不做云端推理, 不做联网模型下载, 不申请网络权限: 云路由由宿主 `ai.*` 的 OpenAI/Anthropic/Gemini/DeepSeek/OpenRouter/兼容服务商能力承担.
- 不做 tools / function calling: 工具调用属于云路由能力; 设备端小模型以文本生成为主.
- 不在未验证的情况下声明能力: capability 声明与实际行为保持一致.

## 发布清单 (每个版本)

1. `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug` 通过.
2. 更新 `.changelog/lang_*.json` 与 `.readme/lang_*.json`, 运行 `.python/generate_markdown.py`, 确认工作树幂等.
3. `.\gradlew.bat :app:assembleRelease` 产出 arm64-v8a / x86_64 / universal 三个签名 APK.
4. 真机安装, 用插件说明中的快速开始脚本冒烟一次.
