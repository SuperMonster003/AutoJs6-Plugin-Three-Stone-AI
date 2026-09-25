<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>اضافة ذكاء اصطناعي موحدة. يبقى LiteRT-LM محليا وتختار الأهداف عبر الإنترنت صراحة دائما</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### اللغات

******

يدعم README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- العربية [ar] # الحالي

******

### مقدمة

******

3-Stone AI هي اضافة AutoJs6 الرسمية لتوليد النص بالذكاء الاصطناعي. تشغل نماذج LiteRT-LM المستوردة من المستخدم على backend للمعالج محدد صراحة أو GPU متوافق, كما تستدعي profile لـ OpenAI و Anthropic و Gemini و DeepSeek و OpenRouter و OpenAI Compatible يضبطه المستخدم. يعرض AI Provider V2 الأهداف المحلية وعبر الإنترنت مباشرة في كتالوج موحد; ويحدد كل طلب هدفا صريحا, بينما تعمل الأهداف المحلية من دون وصول إلى الشبكة أو رفع بيانات. تدير إعدادات الملحق بيانات الاعتماد المشفرة والهدف الافتراضي عبر الإنترنت والشبكات المحسوبة واختبارات الاتصال الصريحة.

******

### الميزات

******

- استيراد حزمة نموذج `.litertlm` عبر منتقي نظام Android وحفظ نسخة تم التحقق منها في مساحة التطبيق الخاصة.
- تنزيل نموذج LiteRT Community مثبت الإصدار ولا يتطلب تسجيل الدخول مباشرة إلى موقع SAF يختاره المستخدم, مع التقدم والإلغاء وتنظيف الملف الناقص والتحقق الدقيق من الحجم و SHA-256.
- فحص مساحة التطبيق الخاصة قبل فتح منتقي النظام, وعرض ميزانية الاستيراد الحالية والمساحة المتوقعة للنسخة الخاصة, وإعادة فحص الملف المحدد قبل نسخه.
- إنشاء طلبات توليد محلية من سجل system و user و assistant بنص عادي.
- تمرير `temperature` و `topK` و `topP` و `maxTokens` من `ai.ask` و `ai.chat` و `ai.stream` في AutoJs6 إلى LiteRT-LM.
- تقييد الإخراج أصليا باستخدام JSON Schema في LiteRT-LM عبر `structuredJson` و `responseSchema` في AutoJs6; وتظل القيم المكتملة نص JSON صالحا لـ `JSON.parse`.
- إرجاع أعداد رموز الإدخال والإخراج والمجموع الدقيقة من LiteRT-LM مع مدة التوليد لدى المزود عبر `ai.chat().usage` وأحداث usage للبث.
- الاحتفاظ بسياق متعدد الجولات داخل Conversation أصلي واحد في LiteRT-LM عبر `ai.session` في AutoJs6, مع إرسال طلب المستخدم الجديد فقط في الجولات اللاحقة.
- إعادة استخدام Engine المهيأ بحسب SHA-256 للنموذج لتجنب تكرار بدء التشغيل البارد في الطلبات المتتالية للنموذج نفسه.
- تهيئة كل نموذج مستورد مرة واحدة اختياريًا وحفظ حالة متاح/غير متوافق وإعادة الفحص من مدير النماذج.
- تسليم chunks النص بالترتيب مع ضغط عكسي بواسطة credits ونشر حالة نهائية واحدة فقط من الاكتمال أو الفشل أو الإلغاء.
- إدراج النماذج المستوردة واختيارها وإعادة تسميتها وحذف النماذج غير المحددة واستعادة ملفات النماذج غير المرجعية من شاشة الإدارة.
- اختيار backend من `cpu` أو `gpu` أو `npu` صراحة عبر AutoJs6; CPU هو الافتراضي, ولا يظهر GPU إلا بعد نجاح فحص تحميل OpenCL, ويبلغ عن NPU كغير متاح لأن EAP runtime غير مضمن.
- إدارة profile مدمج ومخصص عبر الإنترنت, وبيانات اعتماد Android Keystore, والهدف الافتراضي, والشبكات المحسوبة, واختبارات اتصال صريحة ومحدودة من إعدادات التطبيق.
- ربط كل محادثة في المشغل بلقطة هدف محلي أو عبر الإنترنت; عند تغيير محادثة تحتوي رسائل يوصى بمحادثة جديدة, وتتطلب المتابعة تأكيدا صريحا وتسجل التغيير.
- حفظ لقطة target/provider/model/locality الفعلية لكل رد من المساعد; تعيد إعادة التوليد استخدام الهدف الأصلي المسجل بدقة, وتفشل بوضوح إذا تغيرت هويته أو أصبح غير متاح, ولا ترجع بصمت إلى هدف المحادثة الحالي.
- إبقاء فشل التوليد المحلي والسحابي ضمن الهدف المحدد: يضيف chat المشغل سببا محدودا بلا بيانات حساسة, ويوضح عدم حدوث رجوع تلقائي عبر الحدود, ويوفر اختيارا يدويا صريحا لهدف آخر مع الاحتفاظ بالمخرجات الجزئية.
- استدعاء أدوات أصلي للأهداف المتصلة المتوافقة مع OpenAI و Anthropic Messages و Gemini GenerateContent, مع معاملات متدفقة واستدعاءات متوازية ومتابعة بعد النتائج.

******

### صيغ النموذج والبيانات

******

يعلن الإصدار 1 نطاق النموذج والنص التالي فقط:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### واجهة الملحق

******

يكتشف المضيف الملحق ويستدعيه بالهويات التالية:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V2
required host build: 5276
```

يعرض AI Provider V2 كتالوجا واحدا مقسما إلى صفحات لأهداف `local:*` و `profile:*`. يعلن كل هدف provider وmodel وlocality وحالتي الإعداد والتوافر والقدرات والحدود وعناصر التحكم وHTTPS origins بصورة مستقلة. يعلن الكتالوج المحلي فقط ON_DEVICE/NONE; وعند وجود profile عبر الإنترنت يعلن HYBRID/PLUGIN_MANAGED والاتحاد الدقيق لـ HTTPS origins. يظل backend profile المحلي عنصر تحكم اختياريا في الهدف, ولا يحدث رجوع صامت من profile أو هدف غير متاح.

يلزم build المضيف 5276 أو أحدث. تتضمن الإصدارات متغيرات APK التالية: arm64-v8a, x86_64, universal.

******

### حالة التكامل مع المضيف

******

> في AutoJs6 (البنية 5276 وما بعدها) تعيد `ai.catalog()` كل نموذج محلي مستورد وكل profile متصل مضبوط ضمن كتالوج اهداف موحد يضم المعرف الدقيق وprovider والنموذج والمحلية وحالة الضبط والتوافر والقدرات وعناصر التحكم والحدود والاصل وbackend profile المحلي. مرر `target` دقيقا الى `ai.ask` او `ai.chat` او `ai.stream` او `ai.session`; يختار `target` وحده اضافة 3-Stone AI الرسمية, بينما يستخدم `plugin: true` هدفها الافتراضي المعلن. قد تعرض الاهداف المحلية `cpu` و`gpu` و`npu` غير المتاح, اما الاهداف المتصلة فلا تملك profile تنفيذ محليا. يفشل backend او الهدف غير المتاح دون fallback ولا يتبدل المسار المحلي والمتصل تلقائيا. تعرض الاستجابات المكتملة والمتدفقة target وplugin وprofile وreasoning وfinish reason وusage الكامل والمدة المقاسة لدى provider. تميز الاخطاء الثابتة provider المفقود او المعطل والهدف المجهول او غير المضبوط او غير المتاح او غير المتوافق في القدرات وbackend غير المتاح. يفعل `responseSchema` الاخراج المنظم; ويستخدم `structuredJson: true` دون schema جذرا افتراضيا من نوع object, وتثبت الجلسات المستمرة target وschema وbackend الاختياري نفسه لكل الجولات.

******

### الأمان والخصوصية

******

يطلب الملحق إذن `INTERNET` لتنزيل النماذج الموصى بها الذي يبدأه المستخدم وللطلبات إلى هدف عبر الإنترنت مضبوط صراحة; ولا تستخدم الأهداف المحلية الشبكة. تبقى بيانات الاعتماد في التخزين الخاص المشفر للملحق ولا تعبر Binder أو كتالوج الأهداف أبدا. ولا يطلب إذن تخزين واسع. تستخدم التنزيلات إصدارات HTTPS غير قابلة للتغيير وحجما وSHA-256 مثبتين, ولا تكتب إلا إلى موقع SAF المختار; ويجب أن ينجح فحص ترويسة LiteRT-LM والحجم والبصمة وflush وfsync. تتحقق الخدمات أيضا من حزمة AutoJs6 وUID والتوقيعات.

******

### حدود التشغيل

******

- لاستعادة النموذج حد صارم يبلغ 8 GiB ويجب أن تترك 256 MiB على الأقل من المساحة الحرة.
- يعمل تنزيل نموذج واحد فقط في عملية التطبيق. تحافظ إعادة إنشاء Activity على التقدم وملكية الإلغاء; ويحذف الملف أو يفرغ عند الإلغاء أو الفشل. قد يترك إنهاء العملية مستندا خارجيا ناقصا يجب حذفه يدويا.
- يحافظ منسق استيراد واحد على مستوى التطبيق على العمل الجاري عند إعادة إنشاء Activity. يتيح pending journal المتزامن بواسطة fsync الاسترداد عند التشغيل البارد وتنظيف ملفات `.incoming` و `.current` و `.pending` المؤقتة stale. لا يحذف الاسترداد إلا destination أنشأتها المحاولة الحالية ولم تنشر قط عبر current metadata; وتبقى أجيال hash المنشورة و current والتاريخية محفوظة.
- لتجنب السباق بين العمليات مع عملية `:provider` المعزولة, لا يحذف الاستيراد تلقائيا أجيال النماذج السابقة المسماة حسب hash من نوع SHA-256. تتيح شاشة الإدارة حذف نماذج catalog غير المحددة واستعادة الملفات ذات أسماء hash التي لم يعد catalog يشير إليها.
- تنشط جلسة توليد واحدة فقط في العملية. تنسخ descriptors قبل العمل غير المتزامن وتغلق وفق quotas البروتوكول.
- يحتفظ provider بمحرك Engine مهيأ واحد كحد أقصى بمفتاح يجمع SHA-256 للنموذج و backend profile. تعيد الطلبات ذات الزوج نفسه استخدامه; ويحرر بأمان عند تغير أي مفتاح أو بعد خمس دقائق من الخمول أو ضغط ذاكرة صريح.
- يثبت فحص النموذج فقط نجاح `Engine.initialize()` على الجهاز الحالي وبيئة التشغيل المضمنة; ولا يقيّم جودة المخرجات ويمكن تكراره بعد تغيير الجهاز أو بيئة التشغيل.
- يعلن provider حد سياق يبلغ 256 KiB وحد إخراج يبلغ 64 KiB. يمكن للطلبات والنماذج فرض حدود أقل.
- يجب ان تكون schema الاستجابة كائن JSON لا يتجاوز 64 KiB. الكلمات المفتاحية المدعومة هي التي ينفذها runtime LiteRT-LM/LLGuidance المضمن; ويجري تحليل الإخراج المكتمل والتحقق منه بصرامة, لذا يجب تخصيص `maxTokens` كاف للقيمة JSON كاملة.
- يقبل `maxTokens` أعدادا صحيحة من 1 إلى 2,147,483,647. يؤدي حذفه إلى ترك عدد رموز الإخراج للقيمة الافتراضية للنموذج أو المحرك, مع استمرار حد أمان إخراج المزود البالغ 64 KiB. يجب أن تكون `temperature` محدودة وغير سالبة, وأن يكون `topK` عددا صحيحا موجبا, وأن تكون `topP` محدودة بين 0 و1. يؤدي ترك إعدادات sampling الثلاثة دون ضبط إلى الحفاظ على قيم النموذج أو المحرك; أما الضبط الجزئي فيملأ القيم المحذوفة بخط أساس LiteRT-LM: `topK: 1` و `topP: 0.95` و `temperature: 1`.
- يستخدم streaming credits محدودة و chunks مقيدة لمنع buffer غير المحدود أو callbacks بلا ضغط عكسي.
- تأتي أعداد رموز usage مباشرة من عدادات KV cache و decode في Conversation ضمن LiteRT-LM دون تقدير قائم على عدد المحارف. يقيس `durationMillis` استدعاء التوليد في الملحق فقط ولا يشمل اكتشاف المضيف أو الربط أو تعداد النماذج أو التوزيع.
- تسمح جلسة `ai.session` المستمرة بجولة نشطة واحدة وتحتفظ بالـ Conversation الأصلي بعد الاكتمال الطبيعي, ويجب إنشاؤها من جديد بعد الإلغاء أو timeout أو فشل التوليد أو الإغلاق الصريح.
- يوقف الإلغاء وإغلاق الجلسة و timeout نشر النتائج وينهي الطلب بحالة نهائية واحدة.

******

### قدرات غير معلنة

******

- لا يعلن إخراج reasoning. وما زالت أهداف LiteRT-LM المحلية لا تعلن tools.
- تسمح الأدوات الأصلية بحد أقصى 16 جولة و 32 استدعاء معلقا. يجب أن تطابق النتائج الدفعة المعلقة تماما; وتظل حدود السياق/الإخراج والإلغاء والمهلة الأصلية سارية. لا يمكن دمجها مع جولات ai.session الدائمة, ولا يقبل السجل الأولي دور tool.
- لا يوجد اكتشاف نماذج عبر الشبكة أو تنزيل نماذج من URL عشوائي. لا يعرض كتالوج الأهداف سوى النماذج المحلية المستوردة وprofile المعد صراحة عبر الإنترنت; ولا يمكن تنزيل سوى كتالوج التوصيات المدمج والمثبت.
- لا يعلن استدلال NPU: يظهر profile كـ `unavailable` بسبب `npu-runtime-not-packaged`. يعلن GPU فقط إذا أمكن تحميل `libOpenCL.so`, ولا يضمن امتداد `.litertlm` وحده تهيئة النموذج.

******

### خارطة الطريق

******

خارطة الطريق منظمة حول ميزات قابلة للتسليم للمستخدم, وكل بند قابل للتحقق على حدة

- [عرض ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### سجل الإصدارات

******

# v1.2.0

###### 2026/09/25

* `تلميح` نسخة مرشحة للتطوير لم تنشر بعد. تستخدم الأدوات المتصلة AI Provider V2; ويتطلب تكامل Agent وسيط الأدوات الأصلي في المضيف build 5297+ وإصدار Agent متوافقا. تعيد الإضافة الاستدعاءات إلى المضيف ولا تنفذ إجراءات الجهاز بنفسها.
* `ميزة` استدعاء أدوات أصلي للأهداف المتصلة المتوافقة مع OpenAI و Anthropic Messages و Gemini GenerateContent, مع معاملات متدفقة واستدعاءات متوازية ومتابعة بعد النتائج

# v1.1.4

###### 2026/09/19

* `إصلاح` تحذيرات قراءة SDK XML v4 مع AGP 9.1 وتشغيل فحص محاذاة مكتبات APK الأصلية خطأ عند تجميع اختبارات JVM, باستخدام إضافات البناء المشتركة 1.8.3
* `تحسين` بعد compileSdk, رفع targetSdk إلى 37 (Android 17), لا يعتمد سلوك المكون الإضافي على الهدف الجديد

# v1.1.3

###### 2026/09/15

* `تحسين` رفع compileSdk إلى 37 (Android 17), يبقى targetSdk عند 36 حتى يتم التحقق من السلوك المعتمد على الهدف

##### إصدارات أخرى

* [CHANGELOG-ar.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-ar.md)

******

### البناء

******

```powershell
.\gradlew.bat :app:assembleDebug
```

بناء الإصدار:

```powershell
.\gradlew.bat :app:assembleRelease
```

تأتي المعلمات من `version.properties`. الحد الأدنى الحالي SDK هو 24 والهدف SDK هو 36 ويلزم JDK 21 أو أحدث.

تتوفر ABI الخاصة بالبروتوكول من ملفات AAR المحلية في `libs`:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-provider-api.aar
```

يستخدم runtime الحزمة LiteRT-LM 0.15.0 من Maven. تحتفظ بنى الإصدار بفئات LiteRT-LM وتنتج ملفي APK للـ ABI وملف universal APK واحدا.

******

### الترخيص

******

ينشر مصدر المشروع بموجب MPL-2.0. تبقى LiteRT-LM والمكونات الخارجية الأخرى خاضعة لتراخيصها.

******

### تخطيط الموارد

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

ينشئ `.python/generate_markdown.py` ملفات README وسجلات changelog داخل التطبيق بعشر لغات من مصادر JSON. تدار سلاسل Android في مجلدات الموارد الخاصة بها.

******

### الروابط

******

- توثيق AutoJs6: https://docs.autojs6.com
- مشروع LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/ui-redesign/docs/16kb.md)
