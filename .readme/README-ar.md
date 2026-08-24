<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="three-stone-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>اضافة ذكاء اصطناعي محلية. يبقى استدلال LiteRT-LM محليا وتبدأ التنزيلات باختيار صريح</p>

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

3-Stone AI هي اضافة AutoJs6 الرسمية لتوليد النص بالذكاء الاصطناعي على الجهاز. تشغل نماذج LiteRT-LM المستوردة من المستخدم على backend للمعالج محدد صراحة أو GPU متوافق, وتستقبل سجل رسائل نصية عادية, وتعيد نصا عاديا او نص JSON مقيدا بواسطة schema عبر جلسة بث متحكم بها. يتم كل الاستدلال محليا دون شبكة أو رفع بيانات; ولا تستخدم الشبكة إلا عندما يختار المستخدم صراحة تنزيل نموذج موصى به.

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
protocol: V1.2-V1.3
required host build: 5276
```

يعلن الملحق تنفيذ ON_DEVICE ووضع credential من نوع NONE. ويعلن قدرات `streaming` و `usage` و `persistent-session` و `structured-json`, ويقبل رسائل `text/plain` و schema استجابة `application/json`, ويخرج نص `text/plain` او `application/json`. يضيف البروتوكول 1.3 backend profile صريحا وتوافره على الجهاز من دون رجوع صامت إلى CPU.

يلزم build المضيف 5276 أو أحدث. تتضمن الإصدارات متغيرات APK التالية: arm64-v8a, x86_64, universal.

******

### حالة التكامل مع المضيف

******

> في AutoJs6 (البنية 5276 وما بعدها) تدعم `ai.ask` و `ai.chat` و `ai.stream` مسار الاضافة المحلية. ينشئ `ai.session({ plugin: true })` جلسة Conversation مستمرة متعددة الجولات, وترسل استدعاءات `ask` و `chat` و `stream` اللاحقة طلب المستخدم الجديد فقط. يحافظ `ai.ask(messages, { plugin: true })` على ترتيب رسائل النص العادي بادوار `system` و `user` و `assistant`, ويجب ان تكون الرسالة الاخيرة بدور `user`. يعيد `ai.chat` أعداد الرموز الدقيقة في `usage` والمدة المقاسة في `usage.raw.durationMillis`; ويرسل `ai.stream` usage التراكمي نفسه قبل الاكتمال. مرر `plugin: true` لاختيار هذه الاضافة, ويمكن حذف معرف النموذج عند وجود نموذج واحد; كما تعدد `ai.models({ plugin: true })` النماذج و `backendProfiles`. يقبل التوليد `backend: 'cpu' | 'gpu' | 'npu'`; يفشل profile غير المتاح صراحة ولا يرجع إلى CPU. عند عدم تثبيت الاضافة او تعطيلها في مركز الاضافات او عدم وجود نموذج, تتلقى السكربتات خطا واضحا. كما يدعم المحدد الصريح `plugin: { component, providerId, modelId }`. يقوم `responseSchema` بتمكين الإخراج المنظم ضمنيا; ويستخدم `structuredJson: true` من دون schema مخططا افتراضيا جذره object. يظل `ai.ask` و `ai.chat().text` يعيدان نص JSON, وتكون delta للبث نص JSON جزئيا, وتستخدم الجلسة المستمرة schema و backend ثابتين في كل الجولات.

******

### الأمان والخصوصية

******

يطلب الملحق إذن `INTERNET` فقط لتنزيل النماذج الموصى بها الذي يبدأه المستخدم, ولا يطلب إذن تخزين واسع. تستخدم التنزيلات إصدارات HTTPS غير قابلة للتغيير وحجما و SHA-256 مثبتين, ولا تكتب إلا إلى موقع SAF المختار; ويجب أن ينجح فحص ترويسة LiteRT-LM والحجم والبصمة و flush و fsync. يظل الاستيراد يقرأ URI المنتقي فقط ويكتب نسخة متحققا منها إلى `files/models` ويفعلها ذريًا. تتحقق الخدمات أيضا من حزمة AutoJs6 و UID والتوقيعات.

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

- لا يعلن reasoning أو tools.
- لا تقبل رسائل دور tool أو tool schemas أو tool calls أو tool results.
- لا يوجد اكتشاف نماذج عبر الشبكة أو تنزيل من URL عشوائي أو استدلال cloud أو تدفق credential; ولا يمكن تنزيل سوى كتالوج التوصيات المدمج والمثبت.
- لا يعلن استدلال NPU: يظهر profile كـ `unavailable` بسبب `npu-runtime-not-packaged`. يعلن GPU فقط إذا أمكن تحميل `libOpenCL.so`, ولا يضمن امتداد `.litertlm` وحده تهيئة النموذج.

******

### خارطة الطريق

******

خارطة الطريق منظمة حول ميزات قابلة للتسليم للمستخدم, وكل بند قابل للتحقق على حدة

- [عرض ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### سجل الإصدارات

******

# v1.1.0

###### 2026/08/24

* `ميزة` اعتماد 3-Stone AI كهوية نهائية للعلامة التجارية ووقت التشغيل لاضافة الذكاء الاصطناعي المحلية الرسمية في AutoJs6
* `ميزة` يستخدم التكامل بين العمليات هويات `ai-provider-api` و `org.autojs.plugin.ai.provider.api` و `org.autojs.plugin.AI_PROVIDER` و `IAiProvider`/`IAiSession`/`IAiCallback` المحايدة من دون الاحتفاظ باسماء بديلة للهويات المستبدلة
* `ميزة` التوافق مع المحدد المختصر `plugin: true` لـ `ai.ask`/`ai.chat`/`ai.stream` وتعداد النماذج `ai.models` في AutoJs6
* `ميزة` تمرير `temperature` و `topK` و `topP` و `maxTokens` عبر بروتوكول AI Provider 1.1 إلى عناصر تحكم sampling و output tokens في LiteRT-LM
* `ميزة` إرجاع أعداد رموز الإدخال والإخراج والمجموع الدقيقة من LiteRT-LM مع مدة التوليد المقاسة لدى المزود عبر `ai.chat().usage` وأحداث usage للبث
* `ميزة` إضافة جلسات مستمرة في بروتوكول AI Provider 1.2 وإعادة استخدام Conversation متعددة الجولات عبر `ai.session` في AutoJs6 دون إعادة إرسال السجل السابق
* `ميزة` إضافة فك ترميز LiteRT-LM أصلي مقيد بواسطة JSON Schema عبر `structuredJson` و `responseSchema` في AutoJs6, مع دعم الاستدعاء الواحد والبث والجلسات المستمرة والتحقق الصارم من JSON المكتمل
* `ميزة` ملفات backend profile صريحة من `cpu` و `gpu` و `npu` عبر البروتوكول 1.3 وخيارات توليد AutoJs6, مع تقرير توافق الجهاز وعزل cache لكل نموذج/profile ومنع الرجوع من profile غير المتاح; لا يعلن GPU إلا بعد نجاح فحص تحميل OpenCL, ويبقى NPU غير متاح لأن EAP runtime غير مضمن
* `ميزة` تنزيل مباشر لنماذج LiteRT Community المثبتة إلى موقع SAF يختاره المستخدم, مع التقدم والإلغاء الدقيق وتنظيف الملف الناقص والتحقق من ترويسة LiteRT-LM والحجم و SHA-256 والاستيراد المباشر بعد التنزيل
* `ميزة` إضافة مساحة محادثة يمكن تشغيلها مباشرة, مع Markdown متدفق, وسجل دائم, وتحذير عند استبدال فرع بعد تعديل رسالة سابقة, وبحث متعدد النتائج, وإدخال يتكيف مع لوحة المفاتيح
* `ميزة` إضافة إعدادات للتطبيق تشمل لون السمة, والوضع الداكن, ولغة التطبيق, ومعلومات التطبيق والمطور, وسجل الإصدارات, مع جعل اتباع AutoJs6 الخيار الافتراضي حيثما أمكن
* `ميزة` إضافة إعدادات للمحادثة تشمل حجم الخط, وسلوك مفتاح Enter, وعدد output token غير محدود أو مخصص, وإعدادات `temperature` و `topK` و `topP` الافتراضية للنموذج أو المخصصة
* `ميزة` عرض محتوى `$\text{...}$` المضمن أثناء البث, مع أوامر رياضية شائعة وتنسيق الأس والرمز السفلي
* `ميزة` إضافة مخزن بيانات اعتماد يديره الملحق باستخدام Android Keystore و AES-256-GCM ونص مشفر موثق مرتبط بـ profile وملفات خاصة ذرية بين العمليات والاستعلام عن حالة configured فقط والتصفير الفوري للنص الصريح
* `ميزة` إضافة مستودع صارم لملفات التعريف على الإنترنت من دون أسرار لنقاط OpenAI Compatible التي تستخدم HTTPS فقط, مع UUID قانوني وبيانات وصفية ذرية بين العمليات وفرض استبدال بيانات الاعتماد أو مسحها صراحة عند تغيير provider أو origin
* `إصلاح` إزالة حدود الإخراج الضمنية البالغة 256 رمزًا و4 KiB من أمثلة التعليمات القابلة للتشغيل: يؤدي حذف `maxTokens` الآن إلى استخدام القيمة الافتراضية للنموذج أو المحرك, ويستخدم مثال Binder المباشر سعة إخراج المزود الكاملة البالغة 64 KiB
* `إصلاح` إصلاح مثال Binder منخفض المستوى في تعليمات الملحق المترجمة إلى 10 لغات, إذ كان لا يزال يستدعي منشئ `AiGenerationOptions` ذا 14 وسيطًا لبروتوكول 1.1, مما أدى إلى فشله مع API بروتوكول 1.3
* `إصلاح` إصلاح احتفاظ مدير النماذج بألوان نص السمة الفاتحة عند تفعيل الوضع الداكن للنظام, مما كان يجعل النص ومربعات الاختيار وصفوف النماذج غير مقروءة على الخلفية الداكنة
* `إصلاح` إبقاء محرر الرسالة ظاهرا فوق لوحة المفاتيح البرمجية, واختيار لون نص زر الإرسال حسب تباين لون السمة, وتوحيد أزرار البحث السابق والتالي والإغلاق
* `تحسين` تحديث وصف الاضافة والتعليمات و README بعشر لغات بما يتوافق مع اضفاء الطابع الرسمي على مسار الاضافة المحلية `ai.*`
* `تحسين` اعادة كتابة ROADMAP كخارطة طريق للميزات ببنود قابلة للتحقق كل على حدة
* `تحسين` توحيد علامات الترقيم في نصوص التطبيق والوثائق المحلية المولدة باستخدام ASCII, مع اختبار يمنع تكرار المشكلة
* `تحسين` إضافة طبقة مشتركة `AiBackend`/`AiTarget`/`AiBackendSession` ليستخدم حوار المشغل و Binder provider مسار `LiteRtLocalBackend` نفسه للفهرس والقدرات وإنشاء الجلسات والبث والإلغاء
* `تحسين` دمج أهداف `local:*` المحلية وأهداف `profile:*` على الإنترنت في فهرس وموزع موحدين على مستوى Application; تبقى قائمة نماذج V1 محلية فقط وتظهر الأهداف على الإنترنت بالحالة unavailable إلى أن يتم تنفيذ نقل HTTPS

# v1.0.0

###### 2026/08/08

* `ميزة` Provider على الجهاز لبروتوكول AI Provider V1 بمعرف ومحرك `three-stone-ai` و provider ID هو `autojs6.three-stone-ai` ومتغير `default`
* `ميزة` توليد نص عادي باستخدام LiteRT-LM على CPU فقط مع سجل system و user و assistant و streaming محكوم بواسطة credits
* `ميزة` استيراد `.litertlm` عبر SAF إلى مساحة التطبيق الخاصة مع حد 8 GiB واحتياطي مساحة و SHA-256 و fsync وتفعيل ذري
* `ميزة` جلسة نشطة واحدة و I/O مقيد و quotas للـ descriptors وإلغاء و timeout وحالة نهائية واحدة والتحقق من متصل AutoJs6 ذي التوقيع نفسه
* `ميزة` استبعاد صريح لقدرات reasoning و tools و structured JSON و usage والشبكة و credential
* `ميزة` ملفات APK للـ arm64-v8a و x86_64 و universal مع README و changelog وواجهة Android وتعليمات الملحق بعشر لغات
* `ميزة` شاشة لإدارة النماذج تعرض كتالوج النماذج الكامل والمساحة المستخدمة في التخزين الخاص, مع اختيار ذري للنموذج الحالي من دون نسخ ملفات النماذج
* `تحسين` الاحتفاظ بأجيال النماذج السابقة المسماة حسب hash من نوع SHA-256 بعد استيراد الاستبدال لتجنب سباق العمليات مع `:provider`, مع استمرار الملفات المحتفظ بها في شغل مساحة التخزين الخاصة
* `تحسين` إضافة منسق استيراد واحد على مستوى التطبيق و pending journal متزامن بواسطة fsync لاستمرار العمل عند إعادة إنشاء Activity والاسترداد عند التشغيل البارد وتنظيف الملفات المؤقتة stale وقصر الحذف على destination التي أنشأتها المحاولة الحالية ولم تنشر قط, مع الحفاظ على أجيال hash المنشورة و current والتاريخية
* `تبعية` إضافة LiteRT-LM 0.15.0 لتوليد النص على CPU داخل الجهاز

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
