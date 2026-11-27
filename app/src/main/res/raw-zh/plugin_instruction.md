3-Stone AI 为 AutoJs6 提供 AI 对话与模型管理: 本地模型在手机上离线运行, 在线服务 (例如 OpenAI, Anthropic, Gemini) 使用你自己的 API Key. 插件自带聊天界面, 同时为 AutoJs6 脚本的 `ai` 模块提供模型.

### 开始使用

1. 从桌面图标打开 3-Stone AI, 在聊天页的菜单中点按 "设置", 再点按 "模型与服务".
2. 使用本地模型: 在 "本地模型" 段点按 "下载模型" 并选择推荐模型, 下载并校验后自动导入; 已有 `.litertlm` 文件时点按 "导入模型文件".
3. 使用在线服务: 在 "在线服务" 段点按 "添加服务", 选择提供方, 填写模型 ID 与 API Key 后保存, 然后可以在服务菜单中选择 "测试连接".
4. 回到聊天页, 点按顶部的模型名称选择模型, 输入问题后发送.

### 主要功能

- **聊天**: 会话自动保存, 可以查看模型的思考过程; 重新生成或编辑消息后保留各个版本, 可以来回切换.
- **附件**: 点按输入框左侧的 "+" 添加图片, 文本文件或 PDF. 图片发给开启了 "图片输入" 的模型, PDF 只发给 Anthropic 与 Gemini 服务; 本地模型暂不支持附件.
- **切换模型**: 聊天时随时切换模型; 脚本默认使用 "模型与服务" 页中的 "默认目标".
- **模型管理**: 在 "本地模型" 段检查, 重命名或删除模型. API Key 只保存在本机, 导出服务时不包含 API Key.
- **请求头**: 在服务详情页的 "请求头" 中添加自定义请求头, 例如 OpenCode Go 需要的会话头; 保密值只保存在本机, 导出时不包含.
- **语音**: 点按麦克风听写, 长按开始语音对话, 长按回复可以 "朗读"; 装有 3-Macaw Speech 时听写与朗读离线完成.
- **导出与分享**: 在聊天页的菜单中选择 "导出为 Markdown" 或 "分享为长图".

### 在脚本中使用

在 AutoJs6 脚本中通过 `ai` 模块调用本插件, 不指定目标时使用 "默认目标":

```js
ai.ask('Introduce AutoJs6 in one sentence.').then((text) => {
    console.log(text);
});
```

要固定使用某个模型, 先用 `ai.catalog()` 找到目标, 再把它的 `id` 作为 `target` 传入. 下面的示例使用第一个可用的本地模型:

```js
ai.catalog().then((catalog) => {
    let target = catalog.targets.find((item) => item.available && item.locality === 'local');
    if (!target) throw new Error('No available local model');
    return ai.ask('Reply with OK.', { target: target.id, temperature: 0.7 });
}).then((text) => console.log(text));
```

多轮对话使用 `ai.session`, 每轮只发送新的提示词, 用完后调用 `session.close()`:

```js
ai.session({ system: 'Keep every answer short.' }).then((session) => {
    return session.ask('Remember this code: Orion.')
        .then(() => session.ask('What code should you remember?'))
        .then((text) => {
            console.log(text);
            session.close();
        }, (error) => {
            session.close();
            throw error;
        });
});
```

- `ai.chat` 返回包含用量的完整响应, `ai.stream` 边生成边返回文本.
- `temperature`, `topK`, `topP` 与 `maxTokens` 控制采样与输出长度; 目标支持结构化输出时, 传入 `responseSchema` 可以得到符合该 JSON Schema 的文本.
- 目标不可用或未配置时请求直接失败 (例如 `TARGET_UNAVAILABLE`), 不会自动换用其他模型.

### 运行要求

- Android 7.0 或更高版本, AutoJs6 6.8.0 或更高版本.
- 本地模型需要 64 位设备 (arm64-v8a 或 x86_64) 与足够的存储空间, 下载确认框会显示所需的最低内存; 32 位设备只能使用在线服务.
- 在线服务需要你自己的 API Key, 服务商可能按用量收费.
- 网络权限用于下载模型, 访问在线服务与更新预置模型列表; 本地推理不联网. 插件不申请存储与麦克风权限.

### 常见问题

**本地模型与在线服务的区别**

本地模型在手机上运行, 对话内容不离开设备, 速度与效果取决于手机性能. 在线服务把对话发给所选的服务商, 需要网络与 API Key.

**生成失败时不自动换用其他模型的原因**

请求只使用你选定的模型, 以免对话内容在你不知情时发往其他服务. 失败时到 "模型与服务" 检查该模型, 或手动切换.

**本地模型无法运行时的处理**

在 "本地模型" 段打开该模型的菜单, 点按 "检查模型" 查看原因; 内存不足时换用更小的推荐模型.

**其他应用请求使用 AI 的情形**

用 AutoJs6 打包并单独签名的应用使用 AI 前, 3-Stone AI 会请你确认授权. 授权后该应用可以使用本地模型与已保存的在线服务, 可能消耗付费额度; 可在 "设置 > 隐私与权限 > 获授权的应用" 中移除授权.

**OpenCode Go 返回 MissingSessionID 的处理**

OpenCode Go 要求每个请求带会话头 x-opencode-session. 在错误说明中点按 "添加会话头", 或在服务详情页的 "请求头" 中添加 "OpenCode Go 会话" 预设, 保存后重试.

全部选项, 事件与错误码见 AutoJs6 文档的 [人工智能 (AI)](https://docs.autojs6.com/#/ai) 章节, 隐私与运行限制见 [项目主页](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI).
