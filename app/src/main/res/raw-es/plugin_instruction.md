# AutoJs6 3-Stone AI

Este plugin importa un paquete de modelo `.litertlm` local mediante Android Storage Access Framework (SAF), lo copia al almacenamiento privado de la aplicación y ejecuta generación LiteRT-LM solo con CPU, historial de texto sin formato y salida por streaming de texto plano o JSON restringido por un schema.

El plugin requiere la build 5276 o posterior del host AutoJs6 y Android API 24 o posterior.

## Obtener un modelo

En el gestor de modelos, pulse **Descargar modelo recomendado** y elija un modelo LiteRT Community fijado y cualquier ubicación SAF escribible. Se muestra el progreso y se verifican la cabecera LiteRT-LM, el número exacto de bytes y SHA-256 antes de habilitar **Importar modelo descargado**. La inferencia nunca usa la red. El archivo externo y su copia privada importada ocupan espacio por separado; si Android termina el proceso durante la descarga, elimine manualmente cualquier documento externo parcial.

## Inicio rápido (recomendado)

Con AutoJs6 compilación 5276 o posterior, llame al plugin directamente mediante el módulo global `ai`. `plugin: true` selecciona este plugin; si solo hay un modelo importado, el ID de modelo puede omitirse.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

Para conservar el contexto de varios turnos, cree una Conversation persistente con `ai.session`. Los turnos posteriores solo envían el nuevo prompt de usuario:

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

Solo puede haber un turno activo por sesión. Llame siempre a `session.close()` al terminar; una cancelación, timeout o error de generación también cierra toda la sesión.

El muestreo y la longitud de salida se controlan desde el mismo objeto de opciones:

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

Pase `responseSchema` para activar la generación estructurada nativa (`structuredJson: true` sin schema usa uno predeterminado con raíz de objeto). La promesa sigue resolviéndose como texto JSON, por lo que solo debe analizar un resultado completo de `ai.ask` o `ai.chat().text`; los deltas de `ai.stream` son texto JSON parcial. Una `ai.session` persistente conserva un único schema fijo en todos sus turnos:

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

Enumere los modelos importados o fije uno explícitamente con `plugin: { modelId: "..." }`:

```javascript
ai.models({ plugin: true }).then((models) => {
    models.forEach((m) => console.log(m.modelId, m.displayName));
});
```

`ai.chat` y `ai.stream` aceptan la misma opción `plugin`. Si el plugin no está instalado, no está habilitado en el Centro de plugins o no tiene modelo, la promesa se rechaza con un código claro como `PROVIDER_NOT_FOUND`, `PROVIDER_DISABLED`, `MODEL_NOT_FOUND` o `MODEL_AMBIGUOUS`.

## Avanzado: acceso Binder de bajo nivel

Las secciones siguientes muestran la ruta de protocolo de bajo nivel sin el módulo `ai`. La mayoría de los scripts no la necesitan.

## Ejemplo de uso

El script Rhino completo siguiente usa como ejemplo el modelo **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)**. Esta descarga fijada tiene el SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, por lo que su ID tras importarlo es `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. El archivo descargado puede llamarse `gemma-4-E2B-it.litertlm`; el nombre no afecta al ID del modelo. Después de importarlo, use la acción de copiar de la esquina superior derecha de las instrucciones del plugin para copiar el script completo. Ejecútelo como un script Rhino normal de AutoJs6, no en modo Node.js. Cambie únicamente `PROMPT` para probar otra solicitud.

Para usar otro modelo, copie su Model ID desde la pantalla de gestión de modelos y sustituya `MODEL_ID` en el script. No reutilice el valor del ejemplo.

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

Seguridad y límites operativos:

- La importación de un modelo se limita a 8 GiB y debe dejar al menos 256 MiB libres.
- El contexto se limita a 256 KiB, la salida a 64 KiB y solo puede estar activa una sesión de generación.
- `maxTokens` (o `maximumOutputTokens` en el protocolo directo) admite de 1 a 2.147.483.647 y se aplica sin requerir informes de usage. `temperature` debe ser finito y no negativo, `topK` positivo y `topP` finito entre 0 y 1.
- Si se omite `maxTokens` (o se pasa `maximumOutputTokens` como `null` en el protocolo directo), se usa el valor predeterminado del modelo o motor; el límite de seguridad de salida de 64 KiB del proveedor sigue vigente.
- Se declaran streaming, usage, sesiones persistentes, structured JSON, `text/plain` y `application/json`. No se admiten reasoning ni tools.
- El schema de respuesta debe ser un objeto JSON de no más de 64 KiB; las palabras clave admitidas son las implementadas por el runtime LiteRT-LM/LLGuidance incluido. La salida estructurada completa se analiza y valida estrictamente, por lo que debe reservarse suficiente `maxTokens` para todo el valor JSON.
- Los tokens de usage proceden de los contadores de caché KV y decode de Conversation en LiteRT-LM, sin estimaciones por caracteres. `durationMillis` mide la generación del proveedor y excluye descubrimiento, enlace, listado de modelos y despacho del host.
- El plugin solicita `INTERNET` solo para descargas explícitas de modelos recomendados y ningún permiso general de almacenamiento. La inferencia nunca usa la red; SAF limita el acceso al origen o destino elegido.
- Solo el host AutoJs6 con la misma firma puede enlazar el servicio provider.
- Las generaciones anteriores con nombre de hash SHA-256 se conservan por seguridad entre procesos y siguen ocupando almacenamiento privado.
