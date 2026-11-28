<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>统一 AI 插件. LiteRT-LM 推理始终在本地; 在线目标始终由用户明确选择</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 语言

******

当前 README.md 支持以下语言:

- 简体中文 [zh-Hans] # 当前
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### 简介

******

3-Stone AI 是 AutoJs6 的官方 AI 文本生成插件. 它可在显式选择的 CPU 或兼容 GPU backend 上运行用户导入的 LiteRT-LM 模型, 在线生成则使用用户配置的在线服务. 本地与在线执行位置共用一个 AI Provider V2 目标目录, 受控流式管线及明确选择边界. 本地目标绝不联网或上传数据; 在线目标仅在用户明确选择后执行, 且始终绑定插件管理的凭据及其声明的 HTTPS origin.

******

### 功能

******

- 通过 Android 系统文件选择器导入 `.litertlm` 模型包, 并将验证后的副本保存到应用私有存储.
- 从内置目录一步下载固定版本且无需登录的 LiteRT Community 推荐模型: 直接写入应用私有存储, 校验精确大小与 SHA-256 后自动导入. 下载中的模型以卡片显示在 "本地模型" 段, 支持进度, 暂停, 取消与失败重试, 中断后从已下载的部分继续; 下载确认框显示该模型的最低内存要求, 本机内存不足时提醒.
- 打开系统文件选择器前预检私有存储空间, 显示当前导入预算和私有副本预计占用, 并在复制前再次检查所选文件.
- 使用纯文本 system, user 和 assistant 历史创建本地生成请求.
- 将 AutoJs6 `ai.ask`, `ai.chat` 和 `ai.stream` 的 `temperature`, `topK`, `topP` 与 `maxTokens` 透传到 LiteRT-LM.
- 通过 AutoJs6 `structuredJson` 和 `responseSchema` 启用 LiteRT-LM 原生 JSON Schema 约束解码; 完整结果仍为可供 `JSON.parse` 解析的 JSON 文本.
- 通过 AutoJs6 `ai.chat().usage` 和流式 usage 事件返回 LiteRT-LM 精确的输入, 输出及总 token 数, 以及插件侧生成耗时.
- 通过 AutoJs6 `ai.session` 在同一个 LiteRT-LM 原生 Conversation 中保留多轮上下文, 后续轮次只发送新的用户提示词.
- 按模型 SHA-256 复用已初始化 Engine, 消除同模型连续请求的重复冷启动.
- 可选地将每个导入模型初始化一次, 持久化其"可用/不兼容"状态, 并可在模型管理界面重新检查.
- 通过 credit 背压按序传送文本 chunk, 并只发布一个完成, 错误或取消终态.
- 在 "本地模型" 段列出, 重命名, 检查和删除已导入模型, 卡片显示每个模型的占用体积; 删除模型时一并删除它首次加载时生成的 LiteRT-LM 引擎缓存. "清理未引用文件" 可回收未引用的模型文件, 遗留的引擎缓存和不再使用的附件.
- 通过 AutoJs6 显式选择 `cpu`, `gpu` 或 `npu` backend; CPU 为默认值, GPU 仅在 OpenCL 加载探测通过后开放, NPU 因未打包 EAP 运行时而明确报告不可用.
- 在 设置 > 模型与服务 中管理内置及自定义在线服务, Android Keystore 凭据, 唯一的默认目标 (在线模型或本地模型均可), 计量网络访问和显式有界连接测试. 服务在全屏的 "服务详情" 页编辑: 每个服务可添加多个模型, 拖动排序并设置首选模型, 点对勾才保存.
- 在服务详情页的 "请求头" 中为每个服务设置最多 16 条自定义请求头, 取值方式为固定值, 保密值或 "每个会话自动生成" (按服务与会话派生, 同一会话内不变). 提供 OpenCode Go 会话, OpenRouter 署名, Helicone 与 Cloudflare AI Gateway 预设; 服务地址在 opencode.ai 而缺少会话头, 或服务返回 MissingSessionID 时给出添加入口. 插件自己发送或由 HTTP 连接管理的头不能自定义; User-Agent 替换默认值, anthropic-beta 与插件的值合并. 请求头随服务配置自动生效, 不对脚本开放.
- 将每个启动器会话绑定到一个本地或在线目标快照; 更改有消息的会话时默认建议新建会话, 继续当前会话必须明确确认并记录变更.
- 为每条助手回复记录实际 target/provider/model/locality 快照; 重新生成沿用该已记录目标, 身份变化或不可用时明确拒绝, 绝不静默回退到会话默认目标.
- 本地和云端生成失败始终停留在所选边界: 启动器聊天追加有界且不含敏感信息的失败原因, 明确说明未发生跨边界自动回退, 并在保留部分输出的同时提供显式手动目标切换入口.
- 在线 OpenAI 兼容, Anthropic Messages 与 Gemini GenerateContent 目标支持原生工具调用, 包含流式参数, 并行调用与结果续轮.
- 在线模型通过协商 AI Provider 2.1 接收 JPEG/PNG 图片及工具结果图片, 支持按具体模型启用.
- 从 GitHub 项目公开目录更新在线预置模型. 自动更新默认开启, 仅在打开 "模型与服务" 页时每 24 小时检查一次, 也可在 "在线服务" 段的菜单中关闭自动更新或手动刷新. 更新遵循计量网络设置, 离线或更新失败时继续使用缓存或内置列表, 不改动已保存的服务与自定义模型 ID.
- 按厂商分组浏览预置模型, 并扩充 OpenRouter 中的 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型选择, 保留准确的模型 ID.
- 统一独立设置的平面分组, 行规格与居中圆角对话框. 语言, 夜间模式, 主题色与启动器图标均在确定后生效, 取消不改变已保存的设置. 主题色默认跟随 AutoJs6, 提供统一色板, HEX/RGB 输入与局部预览; 中性底色保持稳定, 控件遵循所选主题. 启动器默认自适应自动, 升级保留明确保存的选择.
- 设置首页只保留外观设置与 "会话", "模型与服务", "语音", "隐私与权限", "更新与关于" 5 个入口, 每项设置只出现在一处, 版本历史在 "更新与关于" 中. 聊天页的菜单与侧边栏经 "设置" 进入模型设置; 在 AutoJs6 里打开本插件的设置会直接进入 "模型与服务", 按返回键回到设置首页.
- 聊天回复的生成不依附于聊天页: 旋转屏幕, 切换夜间模式, 更改语言或消息字体大小时继续生成, 离开聊天页期间完成的回复回来时完整显示 (应用进程被系统结束时仍会中断). 点 "停止", 新建或切换会话, 切换模型和删除消息时在 1 秒内断开在线请求, 停止后不再继续产生用量.
- 在线服务可设置默认推理强度 (默认, 低, 中, 高, 超高, 最高; "默认" 由模型自行决定), Anthropic 服务还可默认开启快速模式 (回复更快, 价格更高); 聊天时可只为当前会话调整, 调整随会话保存. 模型不支持所选档位时改用最接近的较低档位. 脚本经插件接口调用 AI 时不使用这两项.
- 切换模型使用从底部弹出的面板: 收藏的模型在顶部 "收藏" 组, 其余按服务分组, 每个模型只出现一次; 搜索结果不分组; 每行下方显示所属服务 (本地模型显示文件大小), 并标出图片输入, 推理与快速模式; 面板中可直接调整本会话的推理强度与快速模式, 底部的 "管理模型与服务" 直达模型设置. 聊天页顶部显示本会话的推理强度与快速模式.
- 显示模型的思考过程 (Claude 5 及更新的模型, Gemini 2.5 及更新的模型, 以及会返回思考内容的 OpenAI 兼容服务, 例如 DeepSeek): 生成时在回复上方的卡片中实时显示, 完成后折叠为 "思考了 N 秒", 点按展开或收起; 设置中的 "生成中显示思考内容" 与 "结束后自动折叠" 默认开启. 思考内容随会话保存, 不朗读, 不参与消息搜索与复制, 也不返回给脚本.
- 聊天时点输入框左侧的 "+" 添加附件, 每条消息最多 20 个: 图片经系统照片选择器选择 (过大的图片会自动缩小), 发给开启了 "图片输入" 的模型; UTF-8 文本文件 (例如 txt, md, json, csv 与代码文件) 以文字发送; PDF 发给提供方为 Anthropic 或 Gemini 的服务. 本地模型暂不支持附件, 当前模型不支持的附件会变淡且不能发送. 附件随消息保存, 消息中显示缩略图或文件名, 点按可以用其他应用打开; 之后继续对话时, 会在上下文允许的范围内一并重新发送, 放不下的只告诉模型文件名.
- 重新生成回复或编辑并重新发送消息时, 为这一轮对话新建一个版本, 不删除之后的对话: 回复下方的 "< 2/3 >" 可切换版本, 之后的消息随之切换, 重新打开会话后仍显示上次选择的版本. 搜索, 导出, 朗读, 用量统计和继续对话都只使用当前显示的版本.
- 会话可以 "导出为 Markdown" (由你在系统保存对话框中选择位置, 其他 Markdown 阅读器也能正常显示) 或 "分享为长图" (按聊天页的外观生成, 经系统分享面板分享): 入口在聊天页的菜单与侧边栏长按会话的菜单中. 只导出当前显示的版本; 会话有思考内容时先询问是否一并导出, 默认不包含.
- 会话记录使用 v6 格式, 额外保存每个会话的推理强度与快速模式, 回复的思考内容与用时, 以及消息的附件信息和其他版本; 仍可读取 1.5.0 及更早版本保存的会话. 退回 1.6.0 及更早的版本后只能看到当时显示的版本, 旧版打开聊天页时会丢弃所有会话的其他版本, 再升级回来也无法恢复.
- 输入框内的麦克风: 短按说话, 识别出的文字插入光标处, 由你核对后发送; 装有 3-Macaw Speech 时直接在输入框里实时听写, 灰色文字随说随出, 每句定稿后继续听, 再按一次结束. 长按进入语音对话, 说完自动发送, 回复读完后自动再听; 装有 3-Macaw Speech 时每一轮都不再弹出识别界面, 麦克风被占用或缺少识别模型时这一轮改用弹层.
- 回复可朗读: 长按回复选择 "朗读", 或在 设置 > 语音 中开启 "自动朗读回复" 边生成边读; 标题和列表等记号不读, 代码块和表格各读一句提示.
- 应答 3-Macaw Speech 数字助理的 `VOICE_COMMAND`: 长按电源键说话后点 "3-Stone AI", 在新对话中立即发送并读出回答.

******

### 语音

******

听写与朗读使用 Android 系统接口 (`RecognizerIntent` 与 `TextToSpeech`), 装了 3-Macaw Speech 时还会直接使用它的听写会话. 3-Stone AI 自己不录音, 也不申请麦克风权限. 装了 3-Macaw Speech 时, 设置 > 语音 中的 "优先使用 3-Macaw Speech" 默认开启: 点麦克风直接在输入框里实时听写, 还没定稿的文字显示为灰色, 每句停顿约 1 秒后定稿并继续听, 可以连着说几句; 再点一次麦克风 (这时显示为停止按钮), 15 秒没有说话或满 5 分钟时结束. 长按麦克风进入语音对话, 每一轮都不再弹出识别界面: 你说的话以灰色显示在输入框末尾, 说完一句自动发送, 回答读完后接着听, 8 秒没有说话时退出; 听的时候点麦克风可以立刻发送已说的部分. 离开聊天页, 发送或切换会话时听写随即结束, 没有发出的文字留在输入框. 麦克风正被其他应用占用 (例如 3-Stove Agent 的常驻监听) 或还没有下载识别模型时, 这一次改为打开 3-Macaw Speech 的识别弹层; 它还没有麦克风权限时打开它的授权页面. 朗读使用它的离线语音. 没装或关闭该选项时, 使用手机上已有的识别应用和系统默认的语音合成引擎, 听写与语音对话的每一轮都会打开识别界面. 朗读时点输入框上方的状态条可打断并立刻说下一句, 状态条上的按钮可退出.

- **手机上有多个语音识别应用, 每次都要选?** 装了 3-Macaw Speech 时保持 "优先使用 3-Macaw Speech" 开启即可直接使用它. 关闭后由系统决定, 可以在系统的选择界面中设为默认.
- **低端手机朗读时句与句之间有停顿?** 部分语音 (如 MeloTTS, Kokoro) 在较慢的手机上合成速度跟不上朗读速度. 在 3-Macaw Speech 中把默认语音换成 AISHELL-3, Piper 或 Matcha 即可.
- **点麦克风后提示没有识别模型?** 3-Macaw Speech 的识别弹层会引导下载模型; 在此之前 3-Stone AI 不会自动改用其他识别应用. 需要临时使用其他应用时, 关闭 "优先使用 3-Macaw Speech".
- **装了 3-Macaw Speech, 为什么有时仍会弹出识别界面?** 麦克风正被其他应用占用 (例如 3-Stove Agent 的常驻监听), 还没有下载识别模型, 或暂时连接不上 3-Macaw Speech 时, 这一次改用它的识别弹层: 弹层会暂停其他应用的监听, 也会引导下载模型; 下一次仍先尝试实时听写. 实时听写需要手机上装有 AutoJs6; 能否实时听写以及原因显示在 设置 > 语音 中.
- **实时听写与语音对话会在后台继续吗?** 不会. 它们只在聊天页显示时进行, 切到其他应用, 锁屏或来电时结束, 已显示的文字留在输入框; 回到聊天页后再点或长按麦克风即可继续.
- **怎样经 3-Macaw Speech 的数字助理提问?** 在 3-Macaw Speech 中把它设为默认数字助理, 长按电源键 (或 Home 键) 说话, 然后在结果卡片上点 "3-Stone AI". 3-Stone AI 会打开新对话, 立即发送并读出回答; 锁屏时先解锁. 距上一次这样提问不到 10 秒时, 文字只填入输入框, 不会发送.
- **识别出的文字会发到哪里?** 和打字一样发给当前所选的模型; 在线模型会把它发给服务商. 3-Macaw Speech 全程离线, 其他识别或朗读应用可能联网.

脚本可以用 AutoJs6 的 `speech` 模块 (由 3-Macaw Speech 提供) 与 `ai` 模块组合出同样的语音问答, 不需要 3-Stone AI 的界面: `speech.listen` 听一句, `ai.chat` 或 `ai.stream` 生成回答, `speech.speak` 读出来. 下面的例子听到第一句话后就停止听写:

```js
let session = speech.listen({ timeoutMillis: 8000 });
session.on('final', () => session.stop());
session.on('end', () => {
    let question = session.result();
    if (!question) return;
    ai.chat(question, { maxTokens: 256 })
        .then((response) => speech.speak(response.text))
        .catch((error) => console.error(error.code, error.message));
});
```

******

### 模型和数据格式

******

支持的模型与输入格式:

```text
model package: .litertlm
input: text/plain history + application/json schema; negotiated 2.1: image/jpeg and image/png descriptors
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### 插件接口

******

宿主通过以下标识发现并调用插件:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V2 (2.0 / 2.1)
required host build: 5276
```

AI Provider V2 通过分页目录统一公开 `local:*` 与 `profile:*` 目标. 每个目标分别声明 provider, model, locality, 配置及可用状态, 能力, 限制, 控件与 HTTPS origins. 目录仅含本地目标时声明 ON_DEVICE/NONE; 存在在线 profile 时声明 HYBRID/PLUGIN_MANAGED 及其 HTTPS origins 精确并集. 本地 backend profile 是可选目标控件, 不可用 profile 或目标绝不静默回退.

需要宿主构建版本 5276 或更高版本及 Android 7.0 或更高版本. 构建产物包含 armeabi-v7a, arm64-v8a, x86, x86_64, universal APK. x86 和 armeabi-v7a 设备请使用对应的独立 APK, 支持应用界面, 在线 AI 和宿主插件集成. LiteRT-LM 本地推理需要 arm64-v8a 或 x86_64 及安装包中的对应原生库. universal APK 仅包含 64 位原生库, 无法安装到仅支持 32 位的设备.

******

### 宿主集成状态

******

> 在 AutoJs6 (构建 5276 及以上) 中, `ai.catalog()` 将全部已导入本机模型与已配置在线 profile 作为统一目标目录返回, 包含精确 ID, 提供方, 模型, 本机/在线属性, 配置与可用状态, 能力, 控制项, 限制, 来源及本机 backend profile. 向 `ai.ask`, `ai.chat`, `ai.stream` 或 `ai.session` 传入精确 `target`; 仅传 `target` 会选择官方 3-Stone AI 插件, `plugin: true` 则使用其声明的默认目标. 本机目标可提供 `cpu`, `gpu` 与不可用的 `npu`, 在线目标没有本机执行 profile. backend 或目标不可用时会直接失败且不回退, 本机与在线路由也绝不自动切换. 完成及流式响应公开 target, plugin, profile, reasoning, finish reason, 完整 usage 和提供端实测耗时. 稳定错误码可区分提供方缺失或停用, 目标未知, 未配置, 不可用或能力不匹配, 以及 backend 不可用. `responseSchema` 会隐式启用结构化输出; 仅设置 `structuredJson: true` 时使用默认对象根 schema, 持久会话在所有轮次固定同一 target, schema 与可选 backend.

独立签名应用可在插件中请求 AI 访问授权, 绑定应用身份与安装记录, 支持在设置中撤销授权并停止运行中的请求. 该授权涵盖本地模型与已保存的在线账户, 在线调用可能消耗付费额度. Node.js, Bun 和 Worker 使用 ai.hasAccess() 与 ai.requestAccess().

******

### 安全性和隐私

******

插件为用户主动发起的推荐模型下载, 用户配置的在线目标请求, 以及在 "模型与服务" 页从 GitHub 更新公开预置模型目录使用 `INTERNET` 权限. 目录更新无需 API Key, 不调用推理服务, 插件激活及本地生成不会触发目录联网. 插件不请求广泛存储权限. 推荐模型文件下载使用不可变 HTTPS 版本及固定字节数和 SHA-256, 只写入应用私有 `files/model-downloads` 中的部分文件 (中断后自动重试并续传); LiteRT-LM 文件头, 大小, 摘要, flush 与 fsync 全部通过后才移入 `files/models` 交给导入流程原子激活. 导入仍只读取系统选择器授予的 URI, 将验证副本流式写入应用私有 `files/models` 并原子激活. 原宿主 Provider 服务还会核验 AutoJs6 包名, 调用 UID 归属, 双方签名, 目标元数据及声明来源边界. 语音功能不申请麦克风权限, 录音由识别应用或 3-Macaw Speech 完成: 实时听写与不弹出识别界面的语音对话直接绑定 3-Macaw Speech 的语音服务 (`org.autojs.plugin.SPEECH`, 只有持有 AutoJs6 插件权限的应用可以绑定), 3-Stone AI 只收到识别出的文字, 不接触音频; 识别出的文字和朗读的文字不写入日志, 语音输入的消息在会话记录中与打字消息相同. 模型返回的思考内容只保存在本机会话记录中, 不会在后续轮次发回在线服务, 也不会经插件接口返回给脚本. 聊天附件经系统选择器读取, 不需要存储或照片权限; 选中的文件复制到应用私有 `files/attachments`, 只发给所选在线模型所在的服务, 删除会话时一并删除. 用其他应用打开附件时, 经只暴露 `files/attachments` 的 FileProvider 临时授予读取权限. 分享长图时图片写入应用缓存 `cache/share`, 由另一个只暴露该目录的 FileProvider 交给分享面板, 每次生成前删除超过 24 小时的旧图, 最多保留 3 张; 导出的 Markdown 只写入你在系统保存对话框中选择的位置. 服务的自定义请求头中, 保密值与 API Key 一样经 Android Keystore 加密保存在本机, 界面不回显, 不随导出带出, 导入, 克隆或更换提供方或 HTTPS 来源后需要重新填写; 服务商错误详情中出现的 API Key 与保密值替换为 `[redacted]`. 固定值明文保存并随导出带出, 不要把密钥填成固定值.

******

### 运行限制

******

- 模型导入硬上限为 8 GiB, 且导入后至少保留 256 MiB 可用空间.
- 一次只保留一个未完成的模型下载, 开始下载另一个模型会丢弃它 (确认框会写明). 下载经 AutoJs6 官方插件共用的下载库写入应用私有的 `files/model-downloads/<sha256>/model.litertlm.part`: 网络中断时自动重试两次 (约 2 秒与 5 秒后), 每次都用 HTTP Range 从中断处续传; 暂停, 应用被关闭或进程结束都保留该文件, 继续或重试时先重新校验已下载的部分再续传; 取消, 内容无效或校验不符时删除. 未完成的下载不会因为放置时间长而被删除. 校验通过的文件移到 `files/models/.download-<sha256>.part` 交给导入流程. 冷启动时把最新且尚未导入的推荐模型的下载恢复为暂停状态, 其余删除; 1.7.0 留在 `files/models/.download-<sha256>.part` 的未完成文件会移入新位置, 从已下载的字节继续.
- 下载和导入的本地模型保存在应用私有数据 (`files/models`) 中, 占用体积显示在 "本地模型" 段的卡片和切换模型的面板里. 卸载插件或清除其应用数据会一并删除这些模型; 以前下载到其他位置的模型文件可以用 "导入模型文件" 导入.
- 应用级单导入协调器使 Activity 重建不会中断正在进行的导入. Fsync pending journal 支持冷启动恢复并清理 stale `.incoming`, `.current` 和 `.pending` 临时文件. 恢复只会删除本次尝试新建且从未由 current metadata 发布的 destination, 已发布或 current 模型及历史 hash 代际均会保留.
- 为避免与独立 `:provider` 进程发生竞态, 导入时不会自动删除先前以 SHA-256 hash 命名的模型代际. "本地模型" 段可删除 catalog 模型, 并回收不再由 catalog 引用的 hash 命名文件.
- 同一进程最多有一个活动生成会话. 请求描述符会在异步处理前复制并按协议配额关闭.
- Provider 以模型 SHA-256 和 backend profile 为联合键, 最多缓存一个已初始化 Engine. 相同组合的连续请求会复用它; 任一键变化, 空闲 5 分钟或收到系统明确内存压力通知时会安全释放.
- 模型自检仅证明 `Engine.initialize()` 能在当前设备和内置运行时中成功; 它不评估输出质量, 设备或运行时变化后可重新检查.
- Provider 声明的上下文上限为 256 KiB, 输出上限为 64 KiB, 请求和模型还可施加更低上限.
- 响应 schema 必须是 JSON 对象且不超过 64 KiB. 可用关键字以当前内置 LiteRT-LM/LLGuidance 运行时为准; 插件会严格解析并验证完整输出, 因此应为整个 JSON 值预留足够的 `maxTokens`.
- `maxTokens` 接受 1 至 2,147,483,647 的整数. 省略时将输出 token 数交由模型或引擎默认值决定, 插件的 64 KiB 输出安全上限仍然生效. `temperature` 必须为非负有限数, `topK` 必须为正整数, `topP` 必须为 0 至 1 的有限数. 三项采样参数全部省略时保留模型或引擎默认值; 部分覆盖时, 未设置项使用 LiteRT-LM 基线 `topK: 1`, `topP: 0.95`, `temperature: 1`.
- 流式输出使用有限 credit 和有界 chunk, 防止无限制缓冲或无背压回调.
- Usage token 数直接来自 LiteRT-LM Conversation 的 KV cache 与 decode 计数, 不做字符数估算. `durationMillis` 只测量插件生成调用, 不包含宿主发现, 绑定, 模型枚举和分发时间.
- 持久 `ai.session` 只允许一个活动轮次, 正常完成后保留原生 Conversation; 取消, 超时, 生成失败或显式关闭后必须重新创建会话.
- 取消, 会话关闭和超时会停止结果发布, 并通过唯一终态结束请求.
- 在 设置 > 模型与服务 中打开服务的 "服务详情", 在模型列表的行菜单中为支持图片输入的模型开启 "图片输入". 已有服务默认关闭. 图片仅发送给该服务. LiteRT 与持久 ai.session 仍仅支持文本; Agent 截图要求 Android 11+, 兼容的 AutoJs6 与 AI Agent, observe 工具组及为所选精确模型启用图片输入.
- 附件上限: 单张图片 5 MB, 文本文件 256 KB 且必须是有效的 UTF-8, PDF 32 MB 且不超过 100 页 (加密的 PDF 会被拒收), 每条消息 20 个, 每个会话合计 48 MB. 一次请求的附件原始字节: Anthropic 与 OpenAI 兼容服务 32 MB, Gemini 20 MB; OpenAI 兼容服务不接收 PDF. 超出所选目标的上限时拒绝发送并写明上限.
- 每轮对话最多 8 个版本; 一个会话中未显示的版本合计超过约 2 MiB 时, 先丢弃最早的版本. 删除有多个版本的消息时只删除当前显示的版本.
- 长图高度超过 16384 像素时按比例缩小; 内存不足时把比例减半重试, 仍然失败则提示改为导出 Markdown. 回复仍在生成的会话不能导出.
- AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通过真实初始图片与工具结果图片探针. 合成图片探针不代表其他目标也支持图片或已通过完整 Agent 视觉任务.
- 在线失败仅通过可选的 `providerCode` 字段提供固定的 `ONLINE_*` 分类. 未知异常不提供分类; 不包含 URL, 凭据, 服务响应或原始异常文本. 失败代码与重试策略保持不变.
- 每个服务最多 16 条自定义请求头: 名称 1 - 64 字节, 只能使用 RFC 9110 token 字符, 不区分大小写唯一; 固定值与保密值 1 - 1024 字节, 只能使用可见 ASCII 字符与空格; 全部请求头合计不超过 8192 字节 ("每个会话自动生成" 每条按 36 字节计). 22 个由插件发送或由 HTTP 连接管理的头不能自定义, 导入时同样拒绝. 从未设置值的保密头不发送; 保密值记录读不出时整次请求失败, 不在缺头的情况下发出.

******

### 未声明的能力

******

- 插件接口不声明 reasoning 输出能力, 脚本拿不到思考内容 (思考过程只在启动器聊天中显示). 本地 LiteRT-LM 目标仍不声明 tools 能力.
- 原生工具最多 16 轮, 同时最多 32 个调用. 结果必须完整匹配待处理批次; 上下文/输出限制, 取消与原始截止时间仍生效. 原生工具暂不与持久 ai.session 轮次组合, 初始历史不接受 tool 角色.
- 不支持从任意 URL 下载模型文件. 在线预置模型可从项目公开目录刷新, 但不会据此创建服务或查询账户权限. 启动器聊天与 AI Provider V2 只公开已导入本地模型及用户明确配置的在线服务, 本地模型下载仍限于内置目录中固定版本的推荐模型.
- 不声明 NPU 推理可用: profile 可发现但以 `npu-runtime-not-packaged` 标记为 `unavailable`. GPU 仅在 `libOpenCL.so` 可加载时声明, 且 `.litertlm` 扩展名本身仍不保证模型初始化成功.

******

### 路线图

******

路线图按可交付的用户功能组织, 每项均可单独勾选与验收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本历史

******

# v1.9.0

###### 2026/11/28

* `新增` 装了 3-Macaw Speech 时, 点麦克风直接在输入框里实时听写, 不再弹出识别界面: 还没定稿的文字显示为灰色, 每句停顿后定稿并继续听, 可以连着说几句; 再点一次麦克风 (这时显示为停止按钮), 15 秒没有说话或满 5 分钟时结束. 离开聊天页, 发送或切换会话时听写随即结束, 已显示的文字留在输入框
* `新增` 长按麦克风进入的语音对话在装了 3-Macaw Speech 时也不再弹出识别界面: 你说的话以灰色显示在输入框末尾, 说完一句自动发送, 回答读完后接着听; 8 秒没有说话, 点状态条上的退出按钮或离开聊天页时结束, 听的时候点麦克风可以立刻发送已说的部分
* `优化` 麦克风正被其他应用占用 (例如 3-Stove Agent 的常驻监听) 或 3-Macaw Speech 还没有下载识别模型时, 听写或语音对话的这一次改为打开它的识别弹层 (弹层会暂停其他应用的监听, 也会引导下载模型); 3-Macaw Speech 没有麦克风权限时打开它的授权页面. 设置 > 语音 页显示能否在输入框内实时听写, 不能时说明原因. 没装 3-Macaw Speech 或关闭 "优先使用 3-Macaw Speech" 时与之前相同
* `依赖` 附加 AutoJs6 语音插件接口 speech-api 契约 v3 (输入框内实时听写与语音对话)

# v1.8.0

###### 2026/11/27

* `提示` 给服务添加请求头后, 服务配置会以新的格式保存 (含请求头的导出文件也是如此), 1.7.2 及更早的版本无法读取. 如需退回旧版本, 请先删除所有服务的请求头并保存
* `新增` 在线服务可以添加自定义请求头: 在 "模型与服务" 页打开一个服务, 点按 "连接" 中的 "请求头" 即可添加, 编辑或删除, 每个服务最多 16 条. 取值方式有三种: 固定值; 保密值 (勾选 "保密", 名称含 auth, key, token 或 secret 时默认勾选); "每个会话自动生成" (同一会话内不变, 换用这个服务的其他模型后也不变). 6 种提供方的服务都可以使用. 请求头随服务配置自动生效, 脚本不用改动, 脚本的持久会话 (ai.session) 同样使用固定的会话号
* `新增` 保密值加密保存在本机, 界面上只显示 "已设置", 编辑时不回显, 也不随导出带出. 导入或克隆服务后保密值为 "未设置", 服务卡片上会标出 "请求头待填写", 重新填写前不会发送这个请求头; 更换提供方或 HTTPS 来源并保存时, 已存的保密值与 API Key 一起清除. 服务商返回的错误详情里如果出现了 API Key 或保密值, 会替换为 [redacted]
* `新增` 插件自己发送的头 (例如 Authorization, x-api-key, Content-Type) 与由 HTTP 连接管理的头 (例如 Host, Content-Length) 不能自定义, 对话框会写明原因; User-Agent 可以自定义 (替换插件的默认值), anthropic-beta 会与插件为这次请求计算的值合并. 名称或值的格式, 长度不符合要求, 或全部请求头合计超过 8192 字节时, 对话框同样会写明原因
* `新增` 添加请求头时可以展开 "常用预设": 选择 OpenCode Go 会话或 OpenRouter 署名即可添加对应的请求头, 选择 Helicone 或 Cloudflare AI Gateway 会填好名称, 只需输入密钥. 服务地址在 opencode.ai 而缺少会话头 x-opencode-session 时, 服务详情页会提示并提供 "添加" 按钮; 测试连接或聊天时收到 MissingSessionID 错误, 错误提示会说明原因并提供 "添加会话头" 按钮
* `修复` 插件语言与系统语言的书写方向不同时 (例如在中文系统上把插件切换为阿拉伯语), 对话框底部的 "取消", "确定" 等按钮现在也随界面一起镜像, 不再停留在系统语言的一侧

# v1.7.2

###### 2026/11/20

* `优化` 发行历史页改为按版本展开或收起: 收起时显示日期与各类别的条目数, 展开后每个类别只标注一次, 可一次展开或收起全部, 打开时展开最新版本; 子标题为插件名称; 页面与 "更新与关于" 中的入口由 "版本历史" 改称 "发行历史"
* `优化` 插件中心中的使用说明重新编写, 分为开始使用, 主要功能, 在脚本中使用, 运行要求与常见问题五部分
* `依赖` 附加 Jetpack Compose BOM 版本 2026.09.00 (发行历史页)
* `依赖` 升级 AndroidX Core 至 1.16.0, AndroidX Activity 至 1.12.2, AndroidX Lifecycle 至 2.9.4 (Jetpack Compose 所需)
* `依赖` 升级共享构建插件版本 1.9.0 -> 1.9.7

##### 更多版本

* [CHANGELOG-zh-Hans.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hans.md)

******

### 构建

******

```powershell
.\gradlew.bat :app:assembleDebug
```

发布构建:

```powershell
.\gradlew.bat :app:assembleRelease
```

构建参数来自 `version.properties`. 当前最低 SDK 为 24, 目标 SDK 为 37, 构建 JDK 为 21 或更高版本.

协议 ABI 由仓库 `libs` 目录中的本地 AAR 提供:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-provider-api.aar
```

运行时通过 Maven 使用 LiteRT-LM 0.15.0. 发布构建保留 LiteRT-LM runtime 类, 并生成两个 ABI APK 和一个 universal APK.

******

### 许可证

******

项目源码使用 MPL-2.0. LiteRT-LM 和其他第三方组件继续适用各自的许可证.

******

### 资源布局

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` 从 JSON 源生成 10 种语言的 README 和应用内更新日志. Android 字符串由各自资源目录管理.

******

### 链接

******

- AutoJs6 文档: https://docs.autojs6.com
- LiteRT-LM 项目: https://github.com/google-ai-edge/LiteRT-LM


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/docs/16kb.md)
