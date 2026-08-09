# AutoJs6 AI 文本生成

此插件通过 Android Storage Access Framework (SAF) 导入一个本地 `.litertlm` 模型包, 将其复制到应用私有存储, 并使用 CPU-only LiteRT-LM 根据纯文本历史生成流式纯文本.

插件需要 AutoJs6 宿主构建版本 5270 或更高版本, 以及 Android API 24 或更高版本.

## 使用示例

下面的完整 Rhino 脚本以 **[gemma-4-E2B-it-litert-lm.litertlm](https://huggingface.co/DummyTesty/gemmaspark-model/resolve/6408692dd1c97b77147a39ac91b002b4013b9163/model.litertlm?download=true)** 模型为例. 此固定版本的 SHA-256 为 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, 因此导入后的模型 ID 为 `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. 下载文件可能名为 `model.litertlm`, 文件名不影响模型 ID. 导入后, 可使用插件说明页右上角的复制操作复制整个脚本. 请将其作为普通 AutoJs6 Rhino 脚本运行, 不要使用 Node.js 模式. 只需修改 `PROMPT` 即可尝试其他请求.

使用其他模型时, 请从模型管理页复制该模型的 Model ID, 并替换脚本中的 `MODEL_ID`. 不要沿用示例值.

```javascript
var PLUGIN_PACKAGE =
    "io.github.supermonster003.autojs6.plugin.ai.text";

var SERVICE_CLASS =
    PLUGIN_PACKAGE + ".provider.AiTextProviderService";

// 对应本次模型 SHA-256 的前 32 位
var MODEL_ID =
    "litertlm.ab7838cdfc8f77e54d8ca45eadceb204";

var PROMPT =
    "请用中文列出三条 Android 自动化脚本执行危险操作前应增加确认步骤的理由。每条一句话。";

var TextApi =
    Packages.org.autojs.plugin.ai.text.api;

var CommonApi =
    Packages.org.autojs.plugin.ai.common.api;

var AiTextCodec = TextApi.AiTextCodec.INSTANCE;
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
    TextApi.IAiTextCallback.Stub,
    {
        onStarted: function (raw) {
            try {
                var started =
                    AiTextCodec.decodeSessionStarted(raw);

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
                    AiTextCodec.decodeTextChunk(raw);

                console.log(
                    "chunk[" + chunk.getSequence() + "]: " +
                    chunk.getTextDelta()
                );

                // 只记录待补 credit，不在 Binder 回调线程反向调用插件
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
        },

        onCompleted: function (raw, fds) {
            try {
                if (!terminalClaimed.compareAndSet(false, true)) {
                    return;
                }

                try {
                    var result =
                        AiTextCodec.decodeCompletionResult(raw);

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
                    TextApi.IAiTextProvider.Stub.asInterface(binder)
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
        throw new Error("无法绑定 AI Text Generation 插件");
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
        false,  // usage
        4096,   // 最大输出字节
        null,   // 不设置 token 上限
        0,      // tool rounds
        300000, // 插件侧超时 5 分钟
        "text/plain",
        null,
        java.util.Collections.singletonList("streaming")
    );

    var request = new TextApi.AiTextRequest(
        java.util.UUID.randomUUID().toString(),
        new CommonApi.AiProtocolVersion(1, 0),
        "autojs6.local.text",
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
        AiTextCodec.encodeTextRequest(request),
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
- Provider 不声明 token 数量上限, 因此不支持设置 `maximumOutputTokens` 的请求.
- 仅声明 streaming 和 `text/plain`. 不支持 reasoning, tools, structured JSON 和 usage.
- 插件不请求网络或存储权限.
- 仅允许同签名 AutoJs6 宿主绑定 provider 服务.
- 为保证跨进程安全, 会保留先前以 SHA-256 hash 命名的模型代际, 它们会继续占用应用私有存储.
