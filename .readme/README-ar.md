<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>اضافة ذكاء اصطناعي محلية. توليد نص متدفق على الجهاز عبر LiteRT-LM دون شبكة</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### اللغات

******

يدعم README.md الحالي اللغات التالية:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- العربية [ar] # الحالي

******

### مقدمة

******

On-Device AI هي اضافة AutoJs6 الرسمية لتوليد النص بالذكاء الاصطناعي على الجهاز. تشغل نماذج LiteRT-LM المستوردة من المستخدم على المعالج, وتستقبل سجل رسائل نصية عادية, وتعيد نصا عاديا عبر جلسة بث متحكم بها. تتم جميع العمليات محليا: دون وصول للشبكة ودون رفع اي بيانات.

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
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1
required host build: 5270
```

يعلن الملحق تنفيذ ON_DEVICE ووضع credential من نوع NONE. ولا يعلن إلا قدرة `streaming` وإدخال وإخراج `text/plain`.

يلزم build المضيف 5270 أو أحدث. تتضمن الإصدارات متغيرات APK التالية: arm64-v8a, x86_64, universal.

******

### حالة التكامل مع المضيف

******

> في AutoJs6 (البنية 5276 وما بعدها) تدعم `ai.ask` و `ai.chat` و `ai.stream` مسار الاضافة المحلية: مرر `plugin: true` لاختيار هذه الاضافة, ويمكن حذف معرف النموذج عند وجود نموذج واحد; كما تعدد `ai.models({ plugin: true })` النماذج المستوردة. عند عدم تثبيت الاضافة او تعطيلها في مركز الاضافات او عدم وجود نموذج, تتلقى السكربتات خطا واضحا. كما يدعم المحدد الصريح `plugin: { component, providerId, modelId }`.

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

خارطة الطريق منظمة حول ميزات قابلة للتسليم للمستخدم, وكل بند قابل للتحقق على حدة

- [عرض ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### سجل الإصدارات

******

# v1.1.0

###### 2026/08/20

* `ميزة` اعادة تسمية الاضافة الى On-Device AI وتحديد موقعها كاضافة الذكاء الاصطناعي المحلية الرسمية لـ AutoJs6
* `ميزة` التوافق مع المحدد المختصر `plugin: true` لـ `ai.ask`/`ai.chat`/`ai.stream` وتعداد النماذج `ai.models` في AutoJs6
* `تحسين` تحديث وصف الاضافة والتعليمات و README بعشر لغات بما يتوافق مع اضفاء الطابع الرسمي على مسار الاضافة المحلية `ai.*`
* `تحسين` اعادة كتابة ROADMAP كخارطة طريق للميزات ببنود قابلة للتحقق كل على حدة

# v1.0.0

###### 2026/08/08

* `ميزة` Provider على الجهاز لبروتوكول On-Device AI V1 بمعرف ومحرك `on-device-ai` و provider ID هو `autojs6.on-device-ai` ومتغير `default`
* `ميزة` توليد نص عادي باستخدام LiteRT-LM على CPU فقط مع سجل system و user و assistant و streaming محكوم بواسطة credits
* `ميزة` استيراد `.litertlm` عبر SAF إلى مساحة التطبيق الخاصة مع حد 8 GiB واحتياطي مساحة و SHA-256 و fsync وتفعيل ذري
* `ميزة` جلسة نشطة واحدة و I/O مقيد و quotas للـ descriptors وإلغاء و timeout وحالة نهائية واحدة والتحقق من متصل AutoJs6 ذي التوقيع نفسه
* `ميزة` استبعاد صريح لقدرات reasoning و tools و structured JSON و usage والشبكة و credential
* `ميزة` ملفات APK للـ arm64-v8a و x86_64 و universal مع README و changelog وواجهة Android وتعليمات الملحق بعشر لغات
* `ميزة` شاشة لإدارة النماذج تعرض كتالوج النماذج الكامل والمساحة المستخدمة في التخزين الخاص، مع اختيار ذري للنموذج الحالي من دون نسخ ملفات النماذج
* `تحسين` الاحتفاظ بأجيال النماذج السابقة المسماة حسب hash من نوع SHA-256 بعد استيراد الاستبدال لتجنب سباق العمليات مع `:provider`, مع استمرار الملفات المحتفظ بها في شغل مساحة التخزين الخاصة
* `تحسين` إضافة منسق استيراد واحد على مستوى التطبيق و pending journal متزامن بواسطة fsync لاستمرار العمل عند إعادة إنشاء Activity والاسترداد عند التشغيل البارد وتنظيف الملفات المؤقتة stale وقصر الحذف على destination التي أنشأتها المحاولة الحالية ولم تنشر قط, مع الحفاظ على أجيال hash المنشورة و current والتاريخية
* `تبعية` إضافة LiteRT-LM 0.15.0 لتوليد النص على CPU داخل الجهاز

##### إصدارات أخرى

* [CHANGELOG-ar.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-ar.md)

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
on-device-ai-api.aar
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
