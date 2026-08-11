<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>ملحق محلي لتوليد النص بالذكاء الاصطناعي. بث نص عادي على الجهاز باستخدام LiteRT-LM</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### اللغات

******

يدعم README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- العربية [ar] # الحالي

******

### مقدمة

******

AI Text Generation هو provider مستقل على الجهاز للإصدار 1 من بروتوكول AI Text Generation في AutoJs6. يشغل نموذج LiteRT-LM استورده المستخدم على CPU, ويقبل سجل رسائل بنص عادي, ويعيد نصا عاديا عبر جلسة streaming مضبوطة.

******

### الميزات

******

- استيراد حزمة نموذج `.litertlm` عبر منتقي نظام Android وحفظ نسخة تم التحقق منها في مساحة التطبيق الخاصة.
- إنشاء طلبات توليد محلية من سجل system و user و assistant بنص عادي.
- تسليم chunks النص بالترتيب مع ضغط عكسي بواسطة credits ونشر حالة نهائية واحدة فقط من الاكتمال أو الفشل أو الإلغاء.
- إدراج النموذج المستورد حاليا وإتاحة generation جديدة للقائمة بعد الاستبدال.
- العمل بالكامل على الجهاز باستخدام CPU backend من دون تنزيل نماذج أو استدعاء خدمة استدلال بعيدة.

******

### صيغ النموذج والبيانات

******

يعلن الإصدار 1 نطاق النموذج والنص التالي فقط:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### واجهة الملحق

******

يكتشف المضيف الملحق ويستدعيه بالهويات التالية:

```text
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

يعلن الملحق تنفيذ ON_DEVICE ووضع credential من نوع NONE. ولا يعلن إلا قدرة `streaming` وإدخال وإخراج `text/plain`.

يلزم build المضيف 5270 أو أحدث. تتضمن الإصدارات متغيرات APK التالية: arm64-v8a, x86_64, universal.

******

### حالة التكامل مع المضيف

******

> يوفر AutoJs6 الآن مساري production عامين وصريحين عبر `ai.ask(..., { plugin: ... })` و`ai.stream(..., { plugin: ... })`، مع اختيار exact component/provider/model ومن دون cloud fallback. نجح one-shot ask حقيقي مع release plugin/model على QV710AF65F (API 31, arm64-v8a)، كما نجح public stream smoke حتمي مع fake provider على الجهاز نفسه، مكتملًا طبيعيًا عبر أكثر من 8 chunks ونافذة initial 8-credit. لا يزال تثبيت plugin وحده لا يعيد توجيه legacy `ai.*`؛ يجب أن يمرر script اختيار plugin الصريح ويستخدم modelId مستوردًا. ما زال مثال JavaAdapter/raw Binder القديم وclipboard وUI و`chat` وreal-model streaming وdevice active cancel بلا تحقق.

******

### الأمان والخصوصية

******

لا يطلب الملحق إذن الشبكة أو التخزين. يقرأ النموذج فقط عبر URI منحه منتقي النظام, ويحسب SHA-256 أثناء نسخه إلى مجلد التطبيق الخاص `files/models`, ثم ينفذ fsync ويفعله باستبدال ذري للـ pointer في المجلد نفسه. تتحقق الخدمات أيضا من اسم حزمة AutoJs6 وملكية UID المتصل وتطابق التوقيعات.

******

### حدود التشغيل

******

- لاستعادة النموذج حد صارم يبلغ 8 GiB ويجب أن تترك 256 MiB على الأقل من المساحة الحرة.
- يحافظ منسق استيراد واحد على مستوى التطبيق على العمل الجاري عند إعادة إنشاء Activity. يتيح pending journal المتزامن بواسطة fsync الاسترداد عند التشغيل البارد وتنظيف ملفات `.incoming` و `.current` و `.pending` المؤقتة stale. لا يحذف الاسترداد إلا destination أنشأتها المحاولة الحالية ولم تنشر قط عبر current metadata; وتبقى أجيال hash المنشورة و current والتاريخية محفوظة.
- لتجنب سباق بين العمليات مع عملية `:provider` المعزولة, يحتفظ استيراد الاستبدال بأجيال النماذج السابقة المسماة حسب hash من نوع SHA-256. تستمر هذه الملفات في شغل مساحة التخزين الخاصة بالتطبيق.
- تنشط جلسة توليد واحدة فقط في العملية. تنسخ descriptors قبل العمل غير المتزامن وتغلق وفق quotas البروتوكول.
- يعلن provider حد سياق يبلغ 256 KiB وحد إخراج يبلغ 64 KiB. يمكن للطلبات والنماذج فرض حدود أقل.
- يستخدم streaming credits محدودة و chunks مقيدة لمنع buffer غير المحدود أو callbacks بلا ضغط عكسي.
- يوقف الإلغاء وإغلاق الجلسة و timeout نشر النتائج وينهي الطلب بحالة نهائية واحدة.

******

### قدرات غير معلنة

******

- لا يعلن reasoning أو tools أو structured JSON أو usage.
- لا تقبل رسائل دور tool أو tool schemas أو tool calls أو tool results.
- لا يوجد اكتشاف نماذج عبر الشبكة أو تنزيل أو استدلال cloud أو تدفق credential.
- لا يعلن GPU أو NPU backend. امتداد `.litertlm` وحده لا يضمن أن runtime الحالي لـ LiteRT-LM يستطيع تحميل النموذج.

******

### خارطة الطريق

******

`R0` ما زال قيد التنفيذ. نجحت بوابات البناء بتاريخ 2026-08-10: 32 tests/0 failures, وlint مع 0 error, وDebug/Release بنتيجة `BUILD SUCCESSFUL` خلال 3m37s, من دون تغيير `VERSION_BUILD`/`BUILD_TIME`; ونجح الآن device smoke بـ plugin/model حقيقيين عبر public `ai.ask`, بينما لا يزال الكتب إلى clipboard والنسخ بعد إعادة إنشاء Activity معلقين. يغطي `R1` الان خمس شرائح للمضيف معطلة وغير موصلة افتراضيا ولا تملك نقطة استدعاء انتاجية: PackageManager `exact-action discovery`/`exact-component reinspection` للقراءة فقط, وربط Binder metadata-only بمكون صريح, وmodel-list transcript policy/catalog مستقلة عن transport, وAndroid model-list coordinator مع Binder transport من نوع `IAiModelListCallback`. يعيد metadata handshake القديم فحص حدود identity التي يصل اليها, ولا يفعل fuse الا اذا انتهت absolute deadline بينما لا يزال Binder RPC المتزامن الخاص بـ interface descriptor او `getProviderInfo()` او `getCapabilities()` قيد التنفيذ. تفك model-list policy بصرامة وضمن حدود محددة نتائج الصفحة الواحدة او الصفحات المتعددة واخطاء provider, وترفض تغير generation واعادة استخدام token او دوراته وmodel ID المكرر وعدم توافق capabilities وcallbacks القديمة او المكررة, وتسمح بفائز واحد فقط بين terminal states المتنافسة. عند تهيئة listing فقط, يعيد coordinator التحقق من provider metadata عبر Binder نفسه بعد التحقق من descriptor; ثم يستخدم `AiTextProviderPackageSnapshot.samePackageIdentityAs` لاعادة فحص هوية package/component بدقة قبل كل initial او continuation dispatch. يتحقق كل callback من calling UID وحجم typed page/error envelope وflood slot محدود قبل النسخة الوحيدة من payload; ويعزل page-token ledger محدود لكل pinned identity كاملة وثابتة. بخلاف handshake القديم, يحتفظ coordinator بالـ watchdog لكل `operationsInFlight` مقبولة, بما فيها exact PackageManager inspect وbind وprepare وdispatch وcallback admission, ويفعل fuse للـ exact component اذا انتهت deadline قبل unwind. لا يستطيع اي fuse ايقاف operation عالقة بالقوة; وتبقى gate الخاصة بالـ coordinator في حالة `BUSY` حتى late unwind. الشريحة الخامسة هي `AiTextProviderSessionPolicy` معطلة افتراضيا وغير موصلة ومستقلة عن transport: تثبت plan/request/provider/model/context, وتحتفظ بالـ callbacks الواصلة قبل رجوع `openSession` المتزامن خلف commit gate صريحة, وتتحقق بالترتيب من UID وحدود typed envelope وملكية descriptors, وتدير initial 8 credits وbackpressure محدودا لكل chunk. تقبل ضمن حدود started/chunk/usage/completed/failed/cancelled مع terminal فائز واحد بين اكتمال provider وcancel وtimeout وdeath, ولا تنشره قبل settlement كامل للـ descriptors وremote control وcleanup, وترفض tools بطريقة fail-closed. لا تتضمن Android adapter لـ `IAiTextCallback`/`openSession`/PFD, ولا package reinspection قبل session dispatch, ولا dispatch فعليا او تكامل runtime/UI او توجيه `ai.*`. يشمل دليلها الآن source/static وfocused JVM Gradle: نجح standalone compile عبر Kotlin 2.3.21 K2/JDK 21/JVM 17 و40/40 JUnit و1200/1200 عبر 30 runs; وأنهى focused AutoJs6 Gradle `:app:testAppDebugUnitTest` بالنتيجة exit 0 وسجل XML 40 tests/0 skipped/0 failures/0 errors، مع استمرار نجاح compile عبر fallback بعد retry لـ Kotlin daemon. ولا يزال لا يتضمن Android `IAiTextCallback`/`openSession`/PFD adapter أو ADB/device أو runtime/UI أو توجيه `ai.*`; وتبقى عناصر R1 العامة وبوابات الخروج غير مؤشرة. اجتازت بوابة AutoJs6 Gradle المعزولة بتاريخ 2026-08-10 اختبارات coordinator بنتيجة 15/0, وبنت host Debug وandroidTest وfake APK دون تغيير version metadata. على QV710AF65F (API 31, arm64-v8a), حقق metadata وmodel-list كل منهما `OK (1 test)` كدليل positive PARTIAL, مع اربع صفحات/اربعة fake model عند `pageSize=1`; وتفاصيل signer/hash وidentity قبل/بعد وcallback خارج main وterminal الوحيد مسجلة في host evidence. لم تكن الحزم الثلاث موجودة قبل التثبيت وعادت كلها الى حالة عدم الوجود بعد cleanup. يدعم هذا الدليل عنصر R1 الضيق فقط; وتبقى العناصر العامة وبوابات الخروج غير مؤشرة. لم يكن على QV710AF65F plugin/model حقيقيان, لذلك يظل R0 مفتوحا. تبقى `R2` الى `R8` عناصر مخططا لها. تضيف الشريحة السادسة الضيقة Android exact-component session coordinator مع transport من `IAiTextCallback`/PFD، وهي معطلة افتراضيا وغير موصلة. اجتاز standalone K2 عدد 18/18، واجتاز الـ artifact نفسه 540/540 عبر 30 جولة؛ وسجل focused Gradle عدد 18 tests/0 failures، كما نجح assemble لثلاثة APKs. على QV710AF65F (API 31, arm64-v8a) أعادت طريقتا الاختبار كل منهما `OK (1 test)`: حمل request عبر reliable-pipe PFD ونحو 18 chunks متجاوزة initial 8 credits، وغطى descriptor exact/short/trailing/reliable producer error وidempotent close. هذا دليل `PARTIAL` فقط: لا يزال ينقص callback completion/tool PFD عبر العمليات، وwrong UID، وhostile death/update، وplugin/model حقيقيان، وruntime/UI/`ai.*`؛ لذلك تبقى عناصر R1 العامة وبوابات الخروج غير مؤشرة. أضيفت لاحقا شريحة ضيقة ومؤشرة لاختبارات hostile Android session conformance، وسجلتها isolated H1 commits `edd10008f`/`06ebc788c` وcommits الدمج `0cbc19d9f`/`72eb4d0c0`. اجتاز focused Gradle اختبارات coordinator بنتيجة 18/0 واختبارات fake provider بنتيجة 31/0، ونجح assemble للحزم الثلاث. على QV710AF65F (API 31, arm64-v8a)، أعادت سبع طرق instrumentation دقيقة كل منها `OK (1 test)`: أثبت callback completion عبر ordinary pipe نقل ownership للـ PFD بين العمليات وexact length/EOF وSHA-256 وUTF-8 materialization وcleanup؛ وتم امتلاك tool PFD ثم رفضه مرة واحدة بـ `TOOLS_UNSUPPORTED`؛ وانتهت chunk-before-start وsequence-gap وinvalid descriptor reference بأسلوب fail-closed؛ وبقي duplicate terminal محدودا إلى smoke ذي terminal واحد؛ وألغي stall بعد `Started` مع إمكان إعادة استخدام owner/gate. يظل هذا العنصر `[x]` دليلا ضيقا: لا يغطي cross-process reliable-pipe status أو wrong UID أو no-credit أو provider death أو package update/uninstall أو plugin/model حقيقيين أو runtime/UI أو `ai.*`. وتبقى عناصر R1 العامة وبوابات الخروج غير مؤشرة. حالة التأشير المعتمدة موجودة في خارطة طريق المشروع. تسجل شريحة دورة حياة H2 ضيقة ومؤشرة اضافية في integrated commits `e5bd92b16`/`10dad3e39`/`0b9a94742`. اجتاز focused Gradle اختبارات coordinator بنتيجة 18/0 وfake provider بنتيجة 31/0، ونجح assemble للحزم الثلاث. على QV710AF65F (API 31, arm64-v8a)، اعادت الطريقتان `callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner` و`providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner` كل منهما `OK (1 test)`: ترفض الاولى callback من isolated-process قبل نشر اي transcript مع terminal وحيد `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH` ثم تعيد استخدام owner/gate؛ وترصد الثانية `Started` وchunk صالحا بتسلسل 0 وacknowledgement لاعادة credit قبل موت provider، ثم تنشر `BinderDied` وحيدا وتعيد استخدام owner/gate. تطابقت قيم SHA-256 بين local/device بدقة للحزم host `0685CE99C0F9E8F9056BE5F3A8EEBC2C7EA5FFCA2D422E21EAC69D0CB3364629` وandroidTest `7C91A0AF651E2098AD124FF8A89AE3AC3018E0F1D0DEC068367595E964739178` وfake provider `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`؛ وكانت قيمة SHA-256 لشهادة v2 signer للحزم الثلاث `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`. كانت حزم host وandroidTest وfake غائبة قبل التثبيت وعادت غائبة بعد cleanup. هذا دليل Android من نوع PARTIAL لطريقتي wrong-UID/provider-death فقط. يبقى no-credit ضمن JVM-only؛ وتبقى اشكال update/uninstall ضمن حالات final-reinspection في JVM (`lastUpdateTime` drift و`Completed(emptyList())`) من دون تغيير حزم الجهاز. لا تزال cross-process no-credit وupdate/uninstall الفعليان وplugin/model حقيقيان وruntime/UI و`ai.*` غير مغطاة؛ وتبقى عناصر R1 العامة وبوابات الخروج غير مؤشرة. تضيف شريحة ضيقة أخرى ومؤشرة، تسجلها commits `e4297a688`/`64db31ea5` في مستودع AutoJs6 الرئيسي، مسار production source صريحا من خلال `ai.ask(..., { plugin: ... })`: يثبت selector بدقة exact component/provider/model؛ وعند غياب `plugin` يبقى سلوك cloud القديم، أما عند وجوده فلا يقرأ vault ولا يدخل HTTP/cloud ولا ينفذ fallback؛ ويقبل المسار حاليا رسالة user واحدة بنص plain-text وفي وضع non-stream، وينقل engine close إلى teardown للـ session البعيدة. نجح standalone K2 بنتيجة 15/15 وfocused Gradle بنتيجة 19/19 وAndroid main compile، وكلها أدلة source-only. لا تشمل هذه الشريحة تشغيلا فعليا مع plugin/model/device ولا UI ولا مسار `chat` أو `stream`؛ وتبقى عناصر R1 العامة وبوابات الخروج غير مؤشرة. بتاريخ 2026-08-11 جاءت ملفات APK المختبرة وتشغيل الجهاز مباشرة من commit شجرة العزل `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`؛ ثم دمج test blob نفسه باسم `7ce26ceea` فوق main parent الحالي مع app tree مطابق، لكن integration commit هذا لم يكن مصدر بناء APK المباشر. QV710AF65F (API 31, arm64-v8a) طلب one-shot حقيقيا من Rhino global `ai.ask(..., { plugin: ... })` عبر release plugin وLiteRT-LM بحجم 2,583,085,056 bytes. اشتق modelId `litertlm.ab7838cdfc8f77e54d8ca45eadceb204` من SHA-256 للنموذج `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`؛ وأعاد `AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin` النتيجة `OK (1 test)` خلال 7.071s. تطابقت قيم local/device APK SHA-256 لـ host `b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561` وandroidTest `09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf` وplugin `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`؛ واشتركت الحزم الثلاث في v2 signer certificate SHA-256 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`، وطابق كل device base ملف APK المرشح. نجحت بوابات plugin test/lint/Debug/Release وhost focused 19/19 tests/assemble؛ وكانت حزم host/test/plugin الدقيقة ومسارات staging/UI-dump المحجوزة absent للمستخدمين 0/10 قبل الاختبار وبعده. لا يؤشر هذا عنصر baseline في R0 لمثال raw Binder/JavaAdapter؛ ويؤشر فقط بوابتي R6 الضيقتين optional real-model smoke/local-entry، لكن الدليل يظل محصورا في device واحد وmodel واحد وnon-stream one-shot وhost من نوع appDebug. لا يغطي clipboard أو streaming أو chat أو tools أو structured output أو usage أو release-host/API matrix أو update/uninstall حقيقيا أو performance أو soak. لم يمكن استعادة DocumentsUI last-location state بلا فقد. تبقى عناصر R1 العامة وبوابات الخروج غير مؤشرة. تسجل AutoJs6 commits `2ea3360a2`/`50b43d00c` شريحة public plugin stream ضيقة ومؤشرة من نوع PARTIAL: نجح focused Gradle بنتيجة 25/25، وأعاد exact method `AiTextPluginPublicStreamSmokeTest#publicRhinoAiStreamCompletesThroughExactFakeProvider` على QV710AF65F (API 31, arm64-v8a) النتيجة `OK (1 test)` خلال 1.645s. أنتج fake provider (`fake.local`/`fake.stream`) أكثر من 8 chunks عبر initial 8 credits، ثم اكتمل owned Rhino execution طبيعيًا. public cancel وengine teardown هما JVM-only؛ لم يختبر real-model streaming أو device active cancel أو release/API matrix، وتبقى عناصر R1 العامة وبوابات الخروج غير مؤشرة.

- [عرض ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### سجل الإصدارات

******

# v1.0.0

###### 2026/08/08

* `ميزة` Provider على الجهاز لبروتوكول AI Text Generation V1 بمعرف ومحرك `ai-text-generation` و provider ID هو `autojs6.local.text` ومتغير `default`
* `ميزة` توليد نص عادي باستخدام LiteRT-LM على CPU فقط مع سجل system و user و assistant و streaming محكوم بواسطة credits
* `ميزة` استيراد `.litertlm` عبر SAF إلى مساحة التطبيق الخاصة مع حد 8 GiB واحتياطي مساحة و SHA-256 و fsync وتفعيل ذري
* `ميزة` جلسة نشطة واحدة و I/O مقيد و quotas للـ descriptors وإلغاء و timeout وحالة نهائية واحدة والتحقق من متصل AutoJs6 ذي التوقيع نفسه
* `ميزة` استبعاد صريح لقدرات reasoning و tools و structured JSON و usage والشبكة و credential
* `ميزة` ملفات APK للـ arm64-v8a و x86_64 و universal مع README و changelog وواجهة Android وتعليمات الملحق بعشر لغات
* `تحسين` الاحتفاظ بأجيال النماذج السابقة المسماة حسب hash من نوع SHA-256 بعد استيراد الاستبدال لتجنب سباق العمليات مع `:provider`, مع استمرار الملفات المحتفظ بها في شغل مساحة التخزين الخاصة
* `تحسين` إضافة منسق استيراد واحد على مستوى التطبيق و pending journal متزامن بواسطة fsync لاستمرار العمل عند إعادة إنشاء Activity والاسترداد عند التشغيل البارد وتنظيف الملفات المؤقتة stale وقصر الحذف على destination التي أنشأتها المحاولة الحالية ولم تنشر قط, مع الحفاظ على أجيال hash المنشورة و current والتاريخية
* `تبعية` إضافة LiteRT-LM 0.15.0 لتوليد النص على CPU داخل الجهاز

##### إصدارات أخرى

* [CHANGELOG-ar.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-ar.md)

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
ai-text-generation-api.aar
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
