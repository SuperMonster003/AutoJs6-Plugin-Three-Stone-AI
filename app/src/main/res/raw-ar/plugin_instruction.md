# AutoJs6 3-Stone AI

يستورد هذا الملحق حزمة نموذج `.litertlm` محلية واحدة عبر Android Storage Access Framework (SAF), وينسخها إلى مساحة التطبيق الخاصة, ويشغل توليد LiteRT-LM على CPU فقط مع سجل بنص عادي وإخراج نص عادي او JSON مقيد بواسطة schema عبر streaming.

يتطلب الملحق build 5276 أو أحدث من مضيف AutoJs6 و Android API 24 أو أحدث.

يلزم build المضيف 5276 أو أحدث و Android 7.0 أو أحدث. ينتج البناء ملفات APK التالية: armeabi-v7a, arm64-v8a, x86, x86_64, universal. على أجهزة x86 و armeabi-v7a استخدم ملف APK المستقل المطابق للواجهة والذكاء الاصطناعي عبر الإنترنت والتكامل مع المضيف. يتطلب الاستدلال المحلي LiteRT-LM معمارية arm64-v8a أو x86_64 ومكتبتها الأصلية المضمنة في APK. يحتوي ملف universal على مكتبات 64 بت فقط ولا يمكن تثبيته على أجهزة تدعم 32 بت فقط.

## الحصول على نموذج

افتح صفحة النماذج واضغط **استعراض نماذج LiteRT-LM** لمراجعة الدليل المتاح ومعلومات التشغيل المفيدة. يمكن حفظ العناصر التي تحمل علامة **تنزيل متحقق منه** في أي موقع SAF قابل للكتابة. يعرض الملحق التقدم ويتحقق من ترويسة LiteRT-LM وعدد البايتات الدقيق و SHA-256 قبل تمكين **استيراد النموذج المنزل**. لا يستخدم الاستدلال المحلي الشبكة مطلقا. يشغل الملف الخارجي ونسخته الخاصة بعد الاستيراد مساحة منفصلة; وإذا أنهى Android العملية أثناء التنزيل فاحذف أي مستند خارجي ناقص يدويا.

## بداية سريعة (موصى بها)

مع AutoJs6 بنية 5276 او احدث, استدع الاضافة مباشرة عبر الوحدة العامة `ai`. يختار `plugin: true` هذه الاضافة; وعند وجود نموذج واحد مستورد يمكن حذف معرف النموذج.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

للاحتفاظ بسياق متعدد الجولات, أنشئ Conversation مستمرة عبر `ai.session`. ترسل الجولات اللاحقة طلب المستخدم الجديد فقط:

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

تسمح الجلسة بجولة نشطة واحدة فقط. استدع `session.close()` عند الانتهاء; كما يؤدي الإلغاء أو timeout أو فشل التوليد إلى إغلاق الجلسة كاملة.

<!-- persistent-context-policy: successful-turns-only; transparent-step-compaction; 512-KiB-transcript-guard; no-hidden-summary; fail-closed -->
تحتفظ الجلسة المستمرة فقط بجولات user/assistant التي اكتملت بنجاح. عند بلوغ حد token أو حاجز نص المحادثة البالغ 512 KiB, تعيد الإضافة إنشاء backend بشفافية من رسائل SYSTEM الأصلية ولاحقة حديثة من الجولات الكاملة; ولا تنفذ طلب تلخيص مخفيا. يبقى Binder session ID وخيارات التوليد الثابتة وترتيب stream/usage/completion بلا تغيير. إذا ظلت رسائل SYSTEM والطلب الحالي واللاحقة الحديثة الدنيا تتجاوز حد الأمان المطلق, تفشل الجلسة وفق fail-closed باستخدام خطأ invalid-request الموجود.

يمكن التحكم في sampling وطول الإخراج من كائن options نفسه:

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

مرر `responseSchema` لتمكين التوليد المنظم الأصلي (`structuredJson: true` من دون schema يستخدم مخططا افتراضيا جذره object). يظل Promise يعيد نص JSON, لذلك لا تحلل إلا نتيجة `ai.ask` او `ai.chat().text` مكتملة; اما delta من `ai.stream` فهي نص JSON جزئي. تستخدم `ai.session` المستمرة schema واحدة ثابتة في كل الجولات:

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

اعرض جميع الاهداف المحلية والمتصلة ثم ثبت احدها عبر معرف `target` الدقيق:

```javascript
ai.catalog({ plugin: true }).then((catalog) => {
    console.log("default:", catalog.defaultTarget);
    catalog.targets.forEach((target) => {
        console.log(target.id, target.displayName, target.locality);
        console.log(target.backendProfiles);
    });
});
```

تقبل `ai.ask` و `ai.chat` و `ai.stream` و `ai.session` قيمة `target` الدقيقة نفسها; ويستخدم `plugin: true` وحده الهدف الافتراضي المعلن من الاضافة. ترفض الاضافة المفقودة او المعطلة والاهداف غير المضبوطة او غير المتاحة برموز ثابتة مثل `AI_PROVIDER_UNAVAILABLE` و `AI_PROVIDER_DISABLED` و `TARGET_NOT_CONFIGURED` و `TARGET_UNAVAILABLE`. لا يتغير المسار تلقائيا ابدا.

## متقدم: الوصول المباشر الى Binder

تعرض الاقسام التالية مسار البروتوكول منخفض المستوى دون وحدة `ai`. معظم السكربتات لا تحتاجه.

## مثال الاستخدام

يستخدم سكربت Rhino الكامل أدناه نموذج **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)** كمثال. لهذه النسخة المثبتة SHA-256 بالقيمة `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`, ولذلك يكون معرف النموذج بعد الاستيراد `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. قد يكون اسم الملف المحمل `gemma-4-E2B-it.litertlm`; ولا يؤثر اسم الملف في معرف النموذج. بعد استيراده, استخدم إجراء النسخ في الزاوية العلوية اليمنى من تعليمات الملحق لنسخ السكربت كاملا. شغله كسكربت AutoJs6 Rhino عادي, وليس في وضع Node.js. غير `PROMPT` فقط لتجربة طلب آخر.

عند استخدام نموذج آخر, انسخ Model ID الخاص به من شاشة إدارة النماذج واستبدل `MODEL_ID` في السكربت. لا تعاود استخدام قيمة المثال.

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

الأمان وحدود التشغيل:

- يقتصر استيراد النموذج على 8 GiB ويجب أن يترك 256 MiB على الأقل من المساحة الحرة.
- يقتصر السياق على 256 KiB والإخراج على 64 KiB ويمكن تنشيط جلسة توليد واحدة فقط.
- يقبل `maxTokens` (او `maximumOutputTokens` في البروتوكول المباشر) من 1 إلى 2,147,483,647 ويطبق دون اشتراط تقارير usage. يجب أن تكون `temperature` محدودة وغير سالبة و `topK` موجبة و `topP` محدودة بين 0 و1.
- يؤدي حذف `maxTokens` (أو تمرير `maximumOutputTokens` بالقيمة `null` في البروتوكول المباشر) إلى استخدام القيمة الافتراضية للنموذج أو المحرك, مع استمرار حد أمان إخراج المزود البالغ 64 KiB.
- يعلن streaming و usage والجلسات المستمرة و structured JSON و `text/plain` و `application/json`. لا يدعم reasoning او tools.
- يجب ان تكون schema الاستجابة كائن JSON لا يتجاوز 64 KiB; الكلمات المفتاحية المدعومة هي التي ينفذها runtime LiteRT-LM/LLGuidance المضمن. يجري تحليل الإخراج المنظم المكتمل والتحقق منه بصرامة, لذا يجب تخصيص `maxTokens` كاف للقيمة JSON كاملة.
- تأتي أعداد رموز usage من عدادات KV cache و decode في Conversation ضمن LiteRT-LM دون تقدير بالمحارف. يقيس `durationMillis` توليد المزود ولا يشمل اكتشاف المضيف أو الربط أو تعداد النماذج أو التوزيع.
- يطلب الملحق إذن `INTERNET` لتنزيل النماذج الموصى بها بطلب المستخدم, ولطلبات الأهداف عبر الإنترنت التي يضبطها المستخدم, ولتحديث الدليل العام للنماذج المعدة مسبقا من GitHub في إعدادات الذكاء الاصطناعي عبر الإنترنت. لا تتطلب تحديثات الدليل مفتاح API ولا تستدعي الاستدلال, ولا تبدأ عند تفعيل الملحق أو أثناء التوليد المحلي. لا يستخدم الاستدلال المحلي الشبكة. ولا يطلب الملحق إذن تخزين واسع; ويقصر SAF الوصول على المصدر أو الوجهة المختارة.
- لا يمكن ربط خدمة provider إلا من مضيف AutoJs6 ذي التوقيع نفسه.
- يحتفظ بأجيال النماذج السابقة المسماة حسب hash من نوع SHA-256 لضمان الأمان بين العمليات, وتستمر في شغل مساحة التخزين الخاصة.
