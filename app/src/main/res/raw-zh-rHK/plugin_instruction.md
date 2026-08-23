# AutoJs6 裝置端 AI

此插件透過 Android Storage Access Framework (SAF) 匯入一個本地 `.litertlm` 模型套件, 將其複製到應用程式私人儲存空間, 並使用 CPU-only LiteRT-LM 根據純文字歷史產生串流純文字或受 schema 約束嘅 JSON 文本.

插件需要 AutoJs6 主程式構建版本 5276 或更高版本, 以及 Android API 24 或更高版本.

## 取得模型

打開插件模型管理頁並點擊 **下載建議模型**, 即可選擇固定版本嘅 LiteRT Community 模型同任何可寫入 SAF 儲存位置. 插件會顯示進度, 並喺開放 **匯入已下載模型** 前驗證 LiteRT-LM 檔案頭, 精確字節數同 SHA-256. 推理從來唔使用網絡. 下載嘅外部檔案同匯入後嘅應用程式私人副本會分別佔用空間; 如果 Android 喺下載途中終止進程, 請手動刪除可能殘留嘅外部殘缺檔案.

## 快速開始 (推薦)

喺 AutoJs6 構建 5276 及以上版本中, 可直接通過全局 `ai` 模塊調用本插件. `plugin: true` 即選擇本插件; 只導入一個模型時可省略模型 ID.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

需要多輪上下文時, 使用 `ai.session` 建立一個持久 Conversation. 後續輪次只發送新嘅用戶提示詞:

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

同一會話一次只允許一個活動輪次. 使用完畢後應調用 `session.close()`; 取消, 逾時或生成失敗亦會關閉整個會話.

可喺同一個 options 對象控制採樣同輸出長度:

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

傳入 `responseSchema` 就可以啟用原生結構化生成 (`structuredJson: true` 未提供 schema 時使用預設嘅對象根 schema). Promise 仍然解析為 JSON 文本, 所以只可以解析完整嘅 `ai.ask` 或 `ai.chat().text` 結果; `ai.stream` 嘅 delta 係未完整嘅 JSON 文本. 持久 `ai.session` 會喺所有輪次固定使用同一 schema:

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

枚舉已導入模型, 或通過 `plugin: { modelId: "..." }` 顯式固定模型:

```javascript
ai.models({ plugin: true }).then((models) => {
    models.forEach((m) => console.log(m.modelId, m.displayName));
});
```

`ai.chat` 與 `ai.stream` 接受相同嘅 `plugin` 選項. 插件未安裝, 未喺插件中心啟用或未導入模型時, Promise 會以明確嘅錯誤碼拒絕, 如 `PROVIDER_NOT_FOUND`, `PROVIDER_DISABLED`, `MODEL_NOT_FOUND` 或 `MODEL_AMBIGUOUS`.

## 高級: 原始 Binder 訪問

以下章節展示唔經過 `ai` 模塊嘅底層協議路徑. 絕大多數腳本無需使用.

## 使用範例

以下完整 Rhino 腳本以 **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)** 模型為例. 此固定版本的 SHA-256 為 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, 因此匯入後的模型 ID 為 `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. 下載檔案可能名為 `gemma-4-E2B-it.litertlm`, 檔案名稱不影響模型 ID. 匯入後, 可使用插件說明頁右上角的複製操作複製整個腳本. 請將其作為一般 AutoJs6 Rhino 腳本執行, 不要使用 Node.js 模式. 只需修改 `PROMPT` 即可嘗試其他請求.

使用其他模型時, 請從模型管理頁複製該模型的 Model ID, 並替換腳本中的 `MODEL_ID`. 請勿沿用範例值.

```javascript
var PLUGIN_PACKAGE =
    "io.github.supermonster003.autojs6.plugin.ondeviceai";

var SERVICE_CLASS =
    PLUGIN_PACKAGE + ".provider.OnDeviceAiProviderService";

// 对应本次模型 SHA-256 的前 32 位
var MODEL_ID =
    "litertlm.ab7838cdfc8f77e54d8ca45eadceb204";

var PROMPT =
    "请用中文列出三条 Android 自动化脚本执行危险操作前应增加确认步骤的理由. 每条一句话.";

var TextApi =
    Packages.org.autojs.plugin.ondeviceai.api;

var CommonApi =
    Packages.org.autojs.plugin.ai.common.api;

var OnDeviceAiCodec = TextApi.OnDeviceAiCodec.INSTANCE;
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
    TextApi.IOnDeviceAiCallback.Stub,
    {
        onStarted: function (raw) {
            try {
                var started =
                    OnDeviceAiCodec.decodeSessionStarted(raw);

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
                    OnDeviceAiCodec.decodeTextChunk(raw);

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
                        OnDeviceAiCodec.decodeCompletionResult(raw);

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
                    TextApi.IOnDeviceAiProvider.Stub.asInterface(binder)
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
        throw new Error("无法绑定 On-Device AI 插件");
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

    var request = new TextApi.OnDeviceAiRequest(
        java.util.UUID.randomUUID().toString(),
        new CommonApi.AiProtocolVersion(1, 3),
        "autojs6.on-device-ai",
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
        OnDeviceAiCodec.encodeTextRequest(request),
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

安全性和執行限制:

- 模型匯入上限為 8 GiB, 完成後必須至少保留 256 MiB 可用空間.
- 內容上限為 256 KiB, 輸出上限為 64 KiB, 同一時間僅允許一個生成工作階段.
- `maxTokens` (原始協議欄位為 `maximumOutputTokens`) 接受 1 至 2,147,483,647, 且毋須 usage 上報即可執行. `temperature` 必須係非負有限數, `topK` 必須係正數, `topP` 必須係 0 至 1 嘅有限數.
- 省略 `maxTokens` (或者喺原始協議將 `maximumOutputTokens` 傳做 `null`) 時會使用模型或引擎預設值; 插件嘅 64 KiB 輸出安全上限仍然生效.
- 宣告 streaming, usage, persistent session, structured JSON, `text/plain` 同 `application/json`. 唔支援 reasoning 同 tools.
- 響應 schema 必須係 JSON 對象且唔超過 64 KiB; 可用關鍵字以目前內置 LiteRT-LM/LLGuidance 運行時為準. 插件會嚴格解析同驗證完整結構化輸出, 因此應為整個 JSON 值預留足夠嘅 `maxTokens`.
- Usage token 數來自 LiteRT-LM Conversation 嘅 KV cache 同 decode 計數, 唔會用字符數估算. `durationMillis` 只量度插件生成調用, 唔包括宿主發現, 綁定, 模型枚舉同分發時間.
- 插件只為用戶明確發起嘅建議模型下載要求 `INTERNET` 權限, 唔要求廣泛儲存權限. 推理從來唔使用網絡; SAF 只授予對用戶所選來源或目標嘅存取.
- 僅允許同簽名 AutoJs6 主程式綁定 provider 服務.
- 為保證跨進程安全, 會保留先前以 SHA-256 hash 命名的模型代次, 它們會繼續佔用應用程式私人儲存空間.
