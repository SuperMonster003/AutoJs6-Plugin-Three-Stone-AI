# AutoJs6 3-Stone AI

此插件通过 Android Storage Access Framework (SAF) 导入一个本地 `.litertlm` 模型包, 将其复制到应用私有存储, 并使用 CPU-only LiteRT-LM 根据纯文本历史生成流式纯文本或受 schema 约束的 JSON 文本.

插件需要 AutoJs6 宿主构建版本 5276 或更高版本, 以及 Android API 24 或更高版本.

## 获取模型

打开插件模型管理页并点击 **下载推荐模型**, 即可选择固定版本的 LiteRT Community 模型及任意可写 SAF 保存位置. 插件显示进度, 并在开放 **导入已下载模型** 前校验 LiteRT-LM 文件头, 精确字节数和 SHA-256. 推理从不使用网络. 下载的外部文件与导入后的应用私有副本会分别占用空间; 如果 Android 在下载途中终止进程, 请手动删除可能残留的外部残缺文件.

## 快速开始 (推荐)

在 AutoJs6 构建 5276 及以上版本中, 可直接通过全局 `ai` 模块调用本插件. `plugin: true` 即选择本插件; 只导入一个模型时可省略模型 ID.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

需要多轮上下文时, 使用 `ai.session` 创建一个持久 Conversation. 后续轮次只发送新的用户提示词:

```javascript
ai.session({
    plugin: true,
    system: "Keep every answer short.",
}).then((session) => {
    return session.ask("Remember this code: Orion")
        .then(() => session.chat("What code should you remember?"))
        .then((response) => {
            console.log(response.text);
            session.close();
        }, (error) => {
            session.close();
            throw error;
        });
});
```

同一会话一次只允许一个活动轮次. 使用完毕后应调用 `session.close()`; 取消, 超时或生成失败也会关闭整个会话.

可在同一个 options 对象中控制采样和输出长度:

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

传入 `responseSchema` 即可启用原生结构化生成 (`structuredJson: true` 未提供 schema 时使用默认的对象根 schema). Promise 仍解析为 JSON 文本, 因此只能对完整的 `ai.ask` 或 `ai.chat().text` 结果执行解析; `ai.stream` 的 delta 是不完整的 JSON 文本. 持久 `ai.session` 会在所有轮次固定使用同一 schema:

```javascript
let schema = {
    type: "object",
    properties: {
        answer: { type: "string" },
        ok: { type: "boolean" },
    },
    required: [ "answer", "ok" ],
};

ai.ask("Return answer as OK and ok as true.", {
    plugin: true,
    responseSchema: schema,
}).then((text) => {
    let value = JSON.parse(text);
    console.log(value.answer, value.ok);
});
```

枚举已导入模型, 或通过 `plugin: { modelId: "..." }` 显式固定模型:

```javascript
ai.models({ plugin: true }).then((models) => {
    models.forEach((m) => console.log(m.modelId, m.displayName));
});
```

`ai.chat` 与 `ai.stream` 接受相同的 `plugin` 选项. 插件未安装, 未在插件中心启用或未导入模型时, Promise 会以明确的错误码拒绝, 如 `PROVIDER_NOT_FOUND`, `PROVIDER_DISABLED`, `MODEL_NOT_FOUND` 或 `MODEL_AMBIGUOUS`.

## 高级: 原始 Binder 访问

以下章节展示不经过 `ai` 模块的底层协议路径. 绝大多数脚本无需使用.

## 使用示例

下面的完整 Rhino 脚本以 **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)** 模型为例. 此固定版本的 SHA-256 为 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, 因此导入后的模型 ID 为 `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. 下载文件可能名为 `gemma-4-E2B-it.litertlm`, 文件名不影响模型 ID. 导入后, 可使用插件说明页右上角的复制操作复制整个脚本. 请将其作为普通 AutoJs6 Rhino 脚本运行, 不要使用 Node.js 模式. 只需修改 `PROMPT` 即可尝试其他请求.

使用其他模型时, 请从模型管理页复制该模型的 Model ID, 并替换脚本中的 `MODEL_ID`. 不要沿用示例值.

```javascript
var PLUGIN_PACKAGE =
    "io.github.supermonster003.autojs6.plugin.threestoneai";

var SERVICE_CLASS =
    PLUGIN_PACKAGE + ".provider.ThreeStoneAiProviderService";

// 对应本次模型 SHA-256 的前 32 位
var MODEL_ID =
    "litertlm.ab7838cdfc8f77e54d8ca45eadceb204";

var PROMPT =
    "请用中文列出三条 Android 自动化脚本执行危险操作前应增加确认步骤的理由. 每条一句话.";

var TextApi =
    Packages.org.autojs.plugin.ai.provider.api;

var CommonApi =
    Packages.org.autojs.plugin.ai.common.api;

var AiProviderCodec = TextApi.AiProviderCodec.INSTANCE;
var AiCommonCodec = CommonApi.AiCommonCodec.INSTANCE;

var TimeUnit = java.util.concurrent.TimeUnit;
var CountDownLatch = java.util.concurrent.CountDownLatch;
var AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean;
var AtomicInteger = java.util.concurrent.atomic.AtomicInteger;
var AtomicReference = java.util.concurrent.atomic.AtomicReference;

var connected = new CountDownLatch(1);
var finished = new CountDownLatch(1);

var providerRef = new AtomicReference(null);
var bindError = new AtomicReference(null);
var terminalError = new AtomicReference(null);
var finalText = new AtomicReference(null);
var usageRef = new AtomicReference(null);

var terminalClaimed = new AtomicBoolean(false);
var returnedCredits = new AtomicInteger(0);

var session = null;
var bound = false;

function javaString(value) {
    return new java.lang.String(String(value));
}

function failTerminal(message) {
    if (terminalClaimed.compareAndSet(false, true)) {
        terminalError.set(javaString(message));
        finished.countDown();
    }
}

function describeProtocolError(raw) {
    try {
        var error = AiCommonCodec.decodeError(raw);
        return "AI 错误 code=" + error.getCode() +
            (error.getMessage() == null
                ? ""
                : ": " + error.getMessage());
    } catch (e) {
        return "无法解析插件错误: " + e;
    }
}

function closeDescriptors(fds) {
    if (fds == null) return;

    for (var i = 0; i < fds.length; i++) {
        try {
            if (fds[i] != null) fds[i].close();
        } catch (_) {
        }
    }
}

var textCallback = new JavaAdapter(
    TextApi.IAiCallback.Stub,
    {
        onStarted: function (raw) {
            try {
                var started =
                    AiProviderCodec.decodeSessionStarted(raw);

                console.log(
                    "会话已启动: " + started.getSessionId()
                );
            } catch (e) {
                failTerminal("无法解析会话信息: " + e);
            }
        },

        onChunk: function (raw) {
            try {
                var chunk =
                    AiProviderCodec.decodeTextChunk(raw);

                console.log(
                    "chunk[" + chunk.getSequence() + "]: " +
                    chunk.getTextDelta()
                );

                // 只记录待补 credit, 不在 Binder 回调线程反向调用插件
                returnedCredits.incrementAndGet();
            } catch (e) {
                failTerminal("无法解析流式文本: " + e);
            }
        },

        onToolCalls: function (raw, fds) {
            closeDescriptors(fds);
            failTerminal("插件返回了本示例未启用的工具调用");
        },

        onUsage: function (raw) {
            try {
                usageRef.set(AiCommonCodec.decodeUsage(raw));
            } catch (e) {
                failTerminal("无法解析用量信息: " + e);
            }
        },

        onCompleted: function (raw, fds) {
            try {
                if (!terminalClaimed.compareAndSet(false, true)) {
                    return;
                }

                try {
                    var result =
                        AiProviderCodec.decodeCompletionResult(raw);

                    var bytes =
                        result.getOutput().getInlineBytes();

                    if (bytes == null) {
                        throw new Error("完成结果不是内联文本");
                    }

                    finalText.set(
                        new java.lang.String(
                            bytes,
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    );
                } catch (e) {
                    terminalError.set(
                        javaString("无法解析完成结果: " + e)
                    );
                } finally {
                    finished.countDown();
                }
            } finally {
                closeDescriptors(fds);
            }
        },

        onFailed: function (raw) {
            failTerminal(describeProtocolError(raw));
        },

        onCancelled: function () {
            failTerminal("生成已取消");
        }
    }
);

var connection = new JavaAdapter(
    android.content.ServiceConnection,
    {
        onServiceConnected: function (name, binder) {
            try {
                providerRef.set(
                    TextApi.IAiProvider.Stub.asInterface(binder)
                );
            } catch (e) {
                bindError.set(
                    javaString("无法创建 provider 接口: " + e)
                );
            } finally {
                connected.countDown();
            }
        },

        onServiceDisconnected: function (name) {
            if (providerRef.get() == null) {
                bindError.compareAndSet(
                    null,
                    javaString("AI provider 已断开")
                );
                connected.countDown();
            } else {
                failTerminal("AI provider 已断开");
            }
        },

        onNullBinding: function (name) {
            bindError.compareAndSet(
                null,
                javaString("AI provider 返回了空 Binder")
            );
            connected.countDown();
        },

        onBindingDied: function (name) {
            bindError.compareAndSet(
                null,
                javaString("AI provider 绑定已失效")
            );
            connected.countDown();
            failTerminal("AI provider 绑定已失效");
        }
    }
);

try {
    var intent = new android.content.Intent();

    intent.setComponent(
        new android.content.ComponentName(
            PLUGIN_PACKAGE,
            SERVICE_CLASS
        )
    );

    bound = context.bindService(
        intent,
        connection,
        android.content.Context.BIND_AUTO_CREATE
    );

    if (!bound) {
        throw new Error("无法绑定 3-Stone AI 插件");
    }

    if (!connected.await(10, TimeUnit.SECONDS)) {
        throw new Error("绑定 AI provider 超时");
    }

    if (bindError.get() != null) {
        throw new Error(String(bindError.get()));
    }

    var provider = providerRef.get();

    if (provider == null) {
        throw new Error("AI provider 不可用");
    }

    // 构造 UTF-8 用户消息
    var utf8 = new java.lang.String(PROMPT).getBytes(
        java.nio.charset.StandardCharsets.UTF_8
    );

    var payload = new CommonApi.AiPayloadReference(
        "text/plain",
        utf8.length,
        null,
        utf8,
        null,
        "utf-8"
    );

    var part = new TextApi.AiContentPart(payload);

    // role=2 表示 USER
    var message = new TextApi.AiMessage(
        2,
        java.util.Collections.singletonList(part),
        null
    );

    var options = new TextApi.AiGenerationOptions(
        true,   // stream
        false,  // reasoning
        false,  // structured JSON
        true,   // usage
        65536,  // 插件输出安全上限 64 KiB
        null,   // 不额外限制 token, 使用模型/引擎默认值
        0,      // tool rounds
        300000, // 插件侧超时 5 分钟
        "text/plain",
        null,
        java.util.Arrays.asList("streaming", "usage"),
        java.lang.Double.valueOf("0.7"), // temperature
        java.lang.Integer.valueOf("40"), // topK
        java.lang.Double.valueOf("0.9"), // topP
        false,  // persistent session
        "cpu"   // explicit backend profile
    );

    var request = new TextApi.AiProviderRequest(
        java.util.UUID.randomUUID().toString(),
        new CommonApi.AiProtocolVersion(2, 0),
        "autojs6.three-stone-ai",
        MODEL_ID,
        java.util.Collections.singletonList(message),
        options,
        java.util.Collections.emptyList()
    );

    var noDescriptors =
        java.lang.reflect.Array.newInstance(
            android.os.ParcelFileDescriptor,
            0
        );

    // openSession 会立即开始模型生成
    session = provider.openSession(
        AiProviderCodec.encodeTextRequest(request),
        noDescriptors,
        textCallback
    );

    // 建立初始流式窗口
    session.grantCredits(8);

    var deadline =
        android.os.SystemClock.elapsedRealtime() + 330000;

    while (
        finished.getCount() > 0 &&
        android.os.SystemClock.elapsedRealtime() < deadline
    ) {
        finished.await(100, TimeUnit.MILLISECONDS);

        // 在普通脚本线程补回已经消费的 credit
        var credits = returnedCredits.getAndSet(0);

        if (credits > 0 && finished.getCount() > 0) {
            session.grantCredits(credits);
        }
    }

    if (finished.getCount() > 0) {
        session.cancel();
        throw new Error("等待模型生成超时");
    }

    if (terminalError.get() != null) {
        throw new Error(String(terminalError.get()));
    }

    var usage = usageRef.get();

    if (usage == null) {
        throw new Error("插件未返回用量信息");
    }

    console.log(
        "usage: input=" + usage.getInputTokens() +
        ", output=" + usage.getOutputTokens() +
        ", total=" + usage.getTotalTokens() +
        ", durationMillis=" + usage.getDurationMillis()
    );

    console.log(
        "\n===== 完整结果 =====\n" + finalText.get()
    );

    toastLog("模型生成完成");
} finally {
    try {
        if (session != null) session.close();
    } catch (_) {
    }

    try {
        if (bound) context.unbindService(connection);
    } catch (_) {
    }
}
```

安全性和运行限制:

- 模型导入上限为 8 GiB, 完成后必须至少保留 256 MiB 可用空间.
- 上下文上限为 256 KiB, 输出上限为 64 KiB, 同一时间仅允许一个生成会话.
- `maxTokens` (原始协议字段为 `maximumOutputTokens`) 接受 1 至 2,147,483,647, 且无需 usage 上报即可执行. `temperature` 必须为非负有限数, `topK` 必须为正数, `topP` 必须为 0 至 1 的有限数.
- 省略 `maxTokens` (或在原始协议中将 `maximumOutputTokens` 传为 `null`) 时使用模型或引擎默认值; 插件的 64 KiB 输出安全上限仍然生效.
- 声明 streaming, usage, persistent session, structured JSON, `text/plain` 和 `application/json`. 不支持 reasoning 与 tools.
- 响应 schema 必须是 JSON 对象且不超过 64 KiB; 可用关键字以当前内置 LiteRT-LM/LLGuidance 运行时为准. 插件会严格解析并验证完整结构化输出, 因此应为整个 JSON 值预留足够的 `maxTokens`.
- Usage token 数来自 LiteRT-LM Conversation 的 KV cache 与 decode 计数, 不做字符数估算. `durationMillis` 只测量插件生成调用, 不包含宿主发现, 绑定, 模型枚举和分发时间.
- 插件仅为用户明确发起的推荐模型下载请求 `INTERNET` 权限, 不请求广泛存储权限. 推理从不使用网络; SAF 仅授予对用户所选来源或目标的访问.
- 仅允许同签名 AutoJs6 宿主绑定 provider 服务.
- 为保证跨进程安全, 会保留先前以 SHA-256 hash 命名的模型代际, 它们会继续占用应用私有存储.
