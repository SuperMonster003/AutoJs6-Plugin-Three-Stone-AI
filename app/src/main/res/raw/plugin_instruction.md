# AutoJs6 3-Stone AI

This plugin imports one local `.litertlm` model package through the Android Storage Access Framework (SAF), copies it into app-private storage, and runs LiteRT-LM generation on an explicitly selected CPU or compatible GPU backend with plain-text history plus streaming plain-text or schema-constrained JSON output.

The plugin requires AutoJs6 host build 5276 or later and Android API 24 or later.

## Get a model

Open the plugin model manager and tap **Browse LiteRT-LM models** to inspect the available catalog and useful runtime details. Entries marked **Verified download** can be saved to any writable SAF location. The plugin shows progress and verifies the LiteRT-LM header, exact byte count, and SHA-256 before enabling **Import downloaded model**. Local inference never uses the network. The downloaded external file and its imported app-private copy occupy space separately; if Android terminates the process mid-download, delete any partial external document manually.

## Quick start (recommended)

With AutoJs6 build 5276 or later, call the plugin directly through the global `ai` module. `plugin: true` selects this plugin; when exactly one model is imported, the model ID may be omitted.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

For multi-turn context, create one persistent Conversation with `ai.session`. Later turns send only the new user prompt:

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

Only one turn may be active in a session. Always call `session.close()` when finished; cancellation, timeout, or generation failure also closes the entire session.

<!-- persistent-context-policy: successful-turns-only; transparent-step-compaction; 512-KiB-transcript-guard; no-hidden-summary; fail-closed -->
A persistent session retains only successfully completed user/assistant turns. At its token watermark or 512 KiB transcript guard, the plugin transparently rebuilds the backend from the original SYSTEM messages and a recent complete-turn suffix; it never makes a hidden summarization request. The Binder session ID, fixed generation options, and stream/usage/completion order remain unchanged. If the SYSTEM messages, current prompt, and minimum recent suffix still exceed the absolute safety limit, the session fails closed with the existing invalid-request error.

Sampling and output length can be controlled from the same options object:

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

Pass `responseSchema` to enable native structured generation (`structuredJson: true` without a schema uses a default object-root schema). The promise still resolves to JSON text, so parse only a completed `ai.ask` or `ai.chat().text` result; `ai.stream` deltas are partial JSON text. A persistent `ai.session` keeps one fixed schema for all turns:

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

List every local and online target, then pin one with its exact `target` ID:

```javascript
ai.catalog({ plugin: true }).then((catalog) => {
    console.log("default:", catalog.defaultTarget);
    catalog.targets.forEach((target) => {
        console.log(target.id, target.displayName, target.locality);
        console.log(target.backendProfiles);
    });
});
```

Local targets expose their execution profiles; online targets return an empty `backendProfiles` array. To request GPU explicitly, select a local target whose `gpu` profile is `available`. An unavailable profile is rejected and never falls back:

```javascript
ai.catalog({ plugin: true }).then((catalog) => {
    let target = catalog.targets.find((item) => item.backendProfiles.some((profile) => {
        return profile.id === "gpu" && profile.availability === "available";
    }));
    if (!target) throw new Error("No compatible GPU backend is available");
    return ai.ask("Hello", {
        target: target.id,
        backend: "gpu",
    });
}).then((text) => console.log(text));
```

`available` confirms ABI and runtime-library prerequisites, not that every model can initialize on every driver. The official plugin exposes `npu` as `unavailable` with reason `npu-runtime-not-packaged`; it does not package or declare LiteRT-LM 0.15.0 EAP NPU inference.

`ai.ask`, `ai.chat`, `ai.stream`, and `ai.session` accept the same exact `target`; `plugin: true` alone uses the plugin-declared default target. Missing or disabled plugins and unconfigured or unavailable targets reject with stable codes such as `AI_PROVIDER_UNAVAILABLE`, `AI_PROVIDER_DISABLED`, `TARGET_NOT_CONFIGURED`, or `TARGET_UNAVAILABLE`. The route never changes automatically.

## Advanced: raw Binder access

The remaining sections show the low-level protocol path without the `ai` module. Most scripts do not need this.

## Usage example

The complete Rhino script below uses the **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)** model as its example. This pinned download has SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, so its imported model ID is `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. The downloaded file may be named `gemma-4-E2B-it.litertlm`; its file name does not affect the model ID. After importing it, use the copy action in the upper-right corner of the plugin instructions to copy the entire script. Run it as a regular AutoJs6 Rhino script, not in Node.js mode. Change only `PROMPT` to try another request.

When using another model, copy that model's Model ID from the model management screen and replace `MODEL_ID` in the script. Do not reuse the example value.

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

Safety and operational limits:

- Model import is limited to 8 GiB and must leave at least 256 MiB of free space.
- Context is limited to 256 KiB, output is limited to 64 KiB, and only one generation session may be active.
- `maxTokens` (or raw-protocol `maximumOutputTokens`) accepts 1 through 2,147,483,647 and is enforced without requiring usage reporting. `temperature` must be finite and non-negative, `topK` positive, and `topP` finite from 0 through 1.
- Omit `maxTokens` (or pass raw-protocol `maximumOutputTokens` as `null`) to use the model or engine default; the provider's 64 KiB output safety limit still applies.
- Streaming, usage, persistent sessions, structured JSON, `text/plain`, and `application/json` are declared. Reasoning and tools are unsupported.
- CPU, GPU, and NPU backend profiles are explicit. GPU is available only when the plugin process can load system OpenCL; NPU is reported unavailable because its EAP runtime is not packaged. No unavailable profile falls back to CPU.
- A response schema must be a JSON object no larger than 64 KiB; supported keywords follow the bundled LiteRT-LM/LLGuidance runtime. Completed structured output is parsed and validated strictly, so provide enough `maxTokens` for the entire JSON value.
- Usage token counts come from LiteRT-LM Conversation KV-cache and decode counters without character-based estimation. `durationMillis` measures the provider generation call and excludes host discovery, binding, target catalog discovery, and dispatch time.
- The plugin requests `INTERNET` for user-triggered recommended-model downloads, requests to a user-configured online target, and public model-preset catalog updates from GitHub in Online AI settings. Catalog updates need no API key, make no inference calls, and never start during plugin activation or local generation. Local inference never uses the network. No broad storage permission is requested; SAF grants access only to the user-selected source or destination.
- Only the same-signature AutoJs6 host may bind the provider service.
- Previous model generations named by SHA-256 hash are retained for cross-process safety and continue to occupy app-private storage.
