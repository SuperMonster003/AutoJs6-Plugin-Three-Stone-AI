# AutoJs6 3-Stone AI

Этот плагин импортирует один локальный пакет модели `.litertlm` через Android Storage Access Framework (SAF), копирует его в закрытое хранилище приложения и выполняет генерацию LiteRT-LM только на CPU с историей в обычном тексте и потоковым выводом обычного текста или JSON с ограничением schema.

Плагину требуется build хоста AutoJs6 5276 или новее и Android API 24 или новее.

## Получение модели

На странице моделей нажмите **Обзор моделей LiteRT-LM**, чтобы изучить доступный каталог и полезные сведения о запуске. Элементы с отметкой **Проверенная загрузка** можно сохранить в любое доступное для записи место SAF. Плагин показывает прогресс и проверяет заголовок LiteRT-LM, точное число байтов и SHA-256 перед включением **Импортировать скачанную модель**. Вывод никогда не использует сеть. Внешний файл и его импортированная закрытая копия занимают место отдельно; если Android завершит процесс во время загрузки, удалите неполный внешний документ вручную.

## Быстрый старт (рекомендуется)

В AutoJs6 сборки 5276 и новее плагин можно вызывать напрямую через глобальный модуль `ai`. `plugin: true` выбирает этот плагин; при единственной импортированной модели идентификатор модели можно опустить.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

Для сохранения многоходового контекста создайте постоянный Conversation через `ai.session`. Следующие ходы передают только новый пользовательский запрос:

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

В сеансе может быть активен только один ход. После использования всегда вызывайте `session.close()`; отмена, timeout или ошибка генерации также закрывают весь сеанс.

<!-- persistent-context-policy: successful-turns-only; transparent-step-compaction; 512-KiB-transcript-guard; no-hidden-summary; fail-closed -->
Постоянный сеанс хранит только успешно завершенные ходы user/assistant. При достижении порога token или ограничения транскрипта 512 KiB плагин прозрачно пересоздает backend из исходных сообщений SYSTEM и недавнего суффикса полных ходов; скрытый запрос на суммаризацию не выполняется. Binder session ID, фиксированные параметры генерации и порядок stream/usage/completion не меняются. Если сообщения SYSTEM, текущий запрос и минимальный недавний суффикс по-прежнему превышают абсолютный предел безопасности, сеанс завершается по принципу fail-closed с существующей ошибкой invalid-request.

Параметры sampling и длина вывода задаются в том же объекте options:

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

Передайте `responseSchema`, чтобы включить нативную структурированную генерацию (`structuredJson: true` без schema использует schema с корневым объектом по умолчанию). Promise по-прежнему возвращает текст JSON, поэтому разбирайте только завершенный результат `ai.ask` или `ai.chat().text`; delta из `ai.stream` являются частичным текстом JSON. Постоянный `ai.session` использует одну фиксированную schema во всех ходах:

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

Перечислите все локальные и сетевые цели, затем закрепите одну по точному ID `target`:

```javascript
ai.catalog({ plugin: true }).then((catalog) => {
    console.log("default:", catalog.defaultTarget);
    catalog.targets.forEach((target) => {
        console.log(target.id, target.displayName, target.locality);
        console.log(target.backendProfiles);
    });
});
```

`ai.ask`, `ai.chat`, `ai.stream` и `ai.session` принимают одну и ту же точную цель `target`; один `plugin: true` использует цель по умолчанию, объявленную плагином. Отсутствующий или отключенный плагин и ненастроенная или недоступная цель отклоняются со стабильными кодами `AI_PROVIDER_UNAVAILABLE`, `AI_PROVIDER_DISABLED`, `TARGET_NOT_CONFIGURED` или `TARGET_UNAVAILABLE`. Маршрут никогда не меняется автоматически.

## Продвинутый уровень: прямой доступ к Binder

Следующие разделы показывают низкоуровневый протокольный путь без модуля `ai`. Большинству скриптов он не нужен.

## Пример использования

Полный скрипт Rhino ниже использует в качестве примера модель **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)**. Закреплённая загрузка имеет SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, поэтому ID импортированной модели - `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. Загруженный файл может называться `gemma-4-E2B-it.litertlm`; имя файла не влияет на ID модели. После импорта используйте действие копирования в правом верхнем углу инструкции плагина, чтобы скопировать скрипт целиком. Запускайте его как обычный скрипт Rhino в AutoJs6, а не в режиме Node.js. Для другого запроса измените только `PROMPT`.

При использовании другой модели скопируйте её Model ID на экране управления моделями и замените `MODEL_ID` в скрипте. Не используйте значение из примера.

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

Безопасность и рабочие ограничения:

- Импорт модели ограничен 8 GiB и должен оставить не менее 256 MiB свободного места.
- Контекст ограничен 256 KiB, вывод 64 KiB, одновременно активен только один сеанс генерации.
- `maxTokens` (или `maximumOutputTokens` в прямом протоколе) принимает значения от 1 до 2 147 483 647 и применяется без обязательного отчёта usage. `temperature` должен быть конечным и неотрицательным, `topK` положительным, а `topP` конечным от 0 до 1.
- Если опустить `maxTokens` (или передать `maximumOutputTokens` как `null` в прямом протоколе), используется значение по умолчанию модели или движка; защитный предел вывода провайдера 64 KiB продолжает действовать.
- Объявлены streaming, usage, постоянные сеансы, structured JSON, `text/plain` и `application/json`. Reasoning и tools не поддерживаются.
- Schema ответа должна быть объектом JSON размером не более 64 KiB; поддерживаются ключевые слова, реализованные во встроенной среде LiteRT-LM/LLGuidance. Завершенный структурированный вывод строго разбирается и проверяется, поэтому для всего значения JSON требуется достаточный `maxTokens`.
- Количество токенов usage берется из счетчиков KV cache и decode объекта Conversation LiteRT-LM без оценки по символам. `durationMillis` измеряет генерацию провайдера и не включает обнаружение, привязку, перечисление моделей и диспетчеризацию хоста.
- Плагин запрашивает `INTERNET` только для явно выбранных загрузок проверенных моделей и не запрашивает широкого доступа к хранилищу. Вывод не использует сеть; SAF открывает только выбранный источник или назначение.
- Только хост AutoJs6 с той же подписью может привязаться к службе provider.
- Предыдущие поколения моделей с именами по hash SHA-256 сохраняются для межпроцессной безопасности и продолжают занимать закрытое хранилище.
