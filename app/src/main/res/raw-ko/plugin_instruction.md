# AutoJs6 온디바이스 AI

이 플러그인은 Android Storage Access Framework (SAF)로 로컬 `.litertlm` 모델 패키지를 가져와 앱 전용 저장소에 복사합니다. CPU-only LiteRT-LM으로 일반 텍스트 기록을 처리하고 텍스트를 스트리밍 출력합니다.

AutoJs6 호스트 build 5270 이상과 Android API 24 이상이 필요합니다.

## 빠른 시작 (권장)

AutoJs6 빌드 5276 이상에서는 전역 `ai` 모듈을 통해 이 플러그인을 직접 호출할 수 있습니다. `plugin: true`로 이 플러그인을 선택하며, 모델이 하나만 있으면 모델 ID를 생략할 수 있습니다.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

가져온 모델을 열거하거나 `plugin: { modelId: "..." }`로 명시적으로 고정할 수 있습니다:

```javascript
ai.models({ plugin: true }).then((models) => {
    models.forEach((m) => console.log(m.modelId, m.displayName));
});
```

`ai.chat`과 `ai.stream`도 동일한 `plugin` 옵션을 받습니다. 플러그인이 설치되지 않았거나 플러그인 센터에서 비활성화되었거나 모델이 없으면 Promise는 `PROVIDER_NOT_FOUND`, `PROVIDER_DISABLED`, `MODEL_NOT_FOUND`, `MODEL_AMBIGUOUS` 같은 명확한 오류 코드로 거부됩니다.

## 고급: 원시 Binder 접근

다음 섹션은 `ai` 모듈을 거치지 않는 저수준 프로토콜 경로를 보여줍니다. 대부분의 스크립트에는 필요하지 않습니다.

## 사용 예제

아래의 전체 Rhino 스크립트는 **[gemma-4-E2B-it-litert-lm.litertlm](https://huggingface.co/DummyTesty/gemmaspark-model/resolve/6408692dd1c97b77147a39ac91b002b4013b9163/model.litertlm?download=true)** 모델을 예제로 사용합니다. 이 고정 다운로드의 SHA-256은 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`이므로 가져온 모델 ID는 `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`입니다. 다운로드 파일 이름이 `model.litertlm`이어도 모델 ID에는 영향을 주지 않습니다. 모델을 가져온 뒤 플러그인 설명 화면 오른쪽 위의 복사 작업으로 전체 스크립트를 복사하십시오. Node.js 모드가 아닌 일반 AutoJs6 Rhino 스크립트로 실행하십시오. 다른 요청을 시험하려면 `PROMPT`만 변경하십시오.

다른 모델을 사용할 때는 모델 관리 화면에서 해당 모델의 Model ID를 복사하여 스크립트의 `MODEL_ID`를 바꾸십시오. 예제 값을 그대로 사용하지 마십시오.

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

보안 및 운영 제한:

- 모델 가져오기 상한은 8 GiB이며 완료 후 최소 256 MiB의 여유 공간이 필요합니다.
- 컨텍스트 상한은 256 KiB, 출력 상한은 64 KiB이며 동시에 활성화할 수 있는 생성 세션은 1개입니다.
- Provider는 token 수 상한을 선언하지 않습니다. `maximumOutputTokens`를 설정하는 요청은 지원하지 않습니다.
- Streaming과 `text/plain`만 선언합니다. Reasoning, tools, structured JSON 및 usage는 지원하지 않습니다.
- 네트워크 또는 저장소 권한을 요청하지 않습니다.
- 동일한 서명의 AutoJs6 호스트만 provider 서비스에 bind할 수 있습니다.
- 프로세스 간 안전을 위해 이전 SHA-256 hash 이름 모델 세대를 보존합니다. 이 파일들은 앱 전용 저장소를 계속 사용합니다.
