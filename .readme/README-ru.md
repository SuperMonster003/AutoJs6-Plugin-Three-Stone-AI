<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>Локальный плагин генерации текста ИИ. Потоковая генерация обычного текста на устройстве с LiteRT-LM</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Языки

******

Текущий README.md поддерживает следующие языки:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- Русский [ru] # текущий
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### Введение

******

AI Text Generation является независимым provider на устройстве для версии 1 протокола AI Text Generation в AutoJs6. Он запускает импортированную пользователем модель LiteRT-LM на CPU, принимает историю сообщений в обычном тексте и возвращает текст через управляемый потоковый сеанс.

******

### Функции

******

- Импорт пакета модели `.litertlm` через системный выбор Android и хранение проверенной копии в закрытом хранилище приложения.
- Создание локальных запросов из истории system, user и assistant в обычном тексте.
- Последовательная передача текстовых chunks с обратным давлением credits и публикация ровно одного конечного состояния завершения, ошибки или отмены.
- Вывод текущей импортированной модели и новой generation списка после замены.
- Полностью локальная работа через CPU backend без загрузки моделей и удаленного сервиса вывода.

******

### Форматы модели и данных

******

Версия 1 объявляет только следующий набор моделей и текста:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### Интерфейс плагина

******

Хост обнаруживает и вызывает плагин со следующими идентификаторами:

```text
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

Плагин объявляет выполнение ON_DEVICE и режим credential NONE. Объявлены только возможность `streaming` и ввод и вывод `text/plain`.

Требуется build хоста 5270 или новее. Выпуски включают варианты APK arm64-v8a, x86_64, universal.

******

### Состояние интеграции с хостом

******

> В основном репозитории AutoJs6 пока нет AI Android adapter, provider selector и runtime bridge, а встроенный `ai.*` не перенесен на этот протокол. Одна установка плагина не перенаправляет существующие вызовы `ai.*`. Для сквозной работы нужен будущий adapter хоста или его явное включение хостом вместе с выбором этого provider.

******

### Безопасность и конфиденциальность

******

Плагин не запрашивает разрешения сети или хранилища. Модель читается только через URI от системного выбора, SHA-256 вычисляется при копировании в закрытый каталог `files/models`, затем вызывается fsync и модель активируется атомарной заменой pointer в том же каталоге. Службы также проверяют имя пакета AutoJs6, принадлежность вызывающего UID и совпадение подписей.

******

### Рабочие ограничения

******

- Импорт модели имеет жесткий предел 8 GiB и должен оставить не менее 256 MiB свободного места.
- Координатор одного импорта на уровне приложения сохраняет выполняемую работу при пересоздании Activity. Синхронизированный через fsync pending journal обеспечивает восстановление при холодном запуске и очистку stale временных файлов `.incoming`, `.current` и `.pending`. Восстановление удаляет только destination, созданный текущей попыткой и никогда не опубликованный через current metadata; опубликованные, current и исторические поколения с hash сохраняются.
- Чтобы избежать межпроцессных гонок с изолированным процессом `:provider`, импорт с заменой сохраняет предыдущие поколения моделей с именами по hash SHA-256. Эти файлы продолжают занимать закрытое хранилище приложения.
- В процессе активен не более одного сеанса генерации. Дескрипторы дублируются до асинхронной работы и закрываются по квотам протокола.
- Provider объявляет предел контекста 256 KiB и предел вывода 64 KiB. Запросы и модели могут задавать меньшие пределы.
- Поток использует конечные credits и ограниченные chunks, исключая неограниченный буфер и callbacks без обратного давления.
- Отмена, закрытие сеанса и timeout прекращают публикацию и завершают запрос одним конечным состоянием.

******

### Необъявленные возможности

******

- Reasoning, tools, structured JSON и usage не объявлены.
- Сообщения роли tool, schemas инструментов, tool calls и tool results не принимаются.
- Нет сетевого поиска моделей, загрузки, cloud вывода или процесса credential.
- GPU и NPU backend не объявлены. Одно расширение `.litertlm` не гарантирует загрузку модели текущим runtime LiteRT-LM.

******

### Дорожная карта

******

`R0` остается в работе. Сборочные проверки от 2026-08-10 прошли: 32 tests/0 failures, lint 0 errors, Debug/Release `BUILD SUCCESSFUL` за 3m37s, `VERSION_BUILD`/`BUILD_TIME` не изменились; device smoke с реальным plugin/model, clipboard и примером script еще не выполнялся. `R1` теперь охватывает пять отключенных и не подключенных по умолчанию среза хоста без производственной точки вызова: доступные только для чтения PackageManager `exact-action discovery`/`exact-component reinspection`, metadata-only Binder-привязку к явному компоненту, независимые от транспорта model-list transcript policy/catalog и Android model-list coordinator с Binder transport `IAiModelListCallback`. Старый metadata handshake повторно проверяет достигнутые границы identity и включает fuse только при истечении absolute deadline, когда синхронный Binder RPC для interface descriptor, `getProviderInfo()` или `getCapabilities()` все еще выполняется. Model-list policy строго декодирует ограниченные одно- и многостраничные результаты и ошибки provider, отклоняет изменение generation, replay/cycles token, дублирующиеся model ID, несовместимость capabilities и устаревшие/повторные callbacks, а также допускает только одно победившее terminal state. Только при инициализации listing coordinator повторно проверяет provider metadata через тот же Binder с проверенным descriptor; затем перед каждым начальным или continuation dispatch он использует `AiTextProviderPackageSnapshot.samePackageIdentityAs` для точной проверки identity package/component. Каждый callback проверяет calling UID, размер typed page/error envelope и ограниченный flood slot до единственного копирования payload; ограниченный page-token ledger изолирован для каждой полной стабильной pinned identity. В отличие от старого handshake coordinator сохраняет watchdog для любых принятых `operationsInFlight`, включая exact PackageManager inspect, bind, prepare, dispatch и callback admission, и включает fuse для exact component, если к deadline не произошел unwind. Ни один fuse не может принудительно прервать застрявшую operation; gate coordinator остается `BUSY` до позднего unwind. Пятый срез — отключенная по умолчанию, не подключенная и независимая от транспорта `AiTextProviderSessionPolicy`: она фиксирует plan/request/provider/model/context, удерживает callbacks до явного commit после синхронного возврата `openSession`, проверяет UID, затем границы typed envelope и ownership descriptors, управляет initial 8 credits и ограниченным backpressure на chunk, ограниченно принимает started/chunk/usage/completed/failed/cancelled с единственным terminal-победителем между завершением provider, cancel, timeout и death, ждет settlement descriptors, remote control и cleanup до публикации terminal и fail-closed отклоняет tools. В ней нет Android adapter для `IAiTextCallback`/`openSession`/PFD, reinspection package перед session dispatch, фактического dispatch, интеграции runtime/UI или маршрутизации `ai.*`. Теперь доказательства включают исходные/статические JVM и focused JVM Gradle: standalone-компиляция Kotlin 2.3.21 K2/JDK 21/JVM 17, 40/40 JUnit и 1200/1200 за 30 runs; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` завершился с exit 0, а XML зафиксировал 40 tests/0 skipped/0 failures/0 errors, причем компиляция через fallback осталась успешной после retry Kotlin daemon. По-прежнему нет Android `IAiTextCallback`/`openSession`/PFD adapter, доказательств ADB/device, runtime/UI или `ai.*`; общие пункты R1 и exit gates остаются неотмеченными. Изолированный AutoJs6 Gradle gate от 2026-08-10 прошел coordinator 15/0 и собрал host Debug, androidTest и fake APK без изменения version metadata. На QV710AF65F (API 31, arm64-v8a) metadata и model-list дали по `OK (1 test)` как положительное PARTIAL-доказательство, собрав четыре страницы/четыре fake model при `pageSize=1`; signer/hash, identity до/после, callback вне main и единственный terminal подробно записаны в host evidence. Все три package отсутствовали до установки и снова отсутствовали после cleanup. Это подтверждает только узкий R1 item; общие пункты и exit gates остаются неотмеченными. На QV710AF65F не было реального plugin/model, поэтому R0 остается открытым. `R2`-`R8` остаются в плане. Шестой узкий срез добавляет Android exact-component session coordinator с transport `IAiTextCallback`/PFD, который по умолчанию отключен и не подключен. Standalone K2 прошел 18/18, тот же artifact — 540/540 за 30 прогонов; focused Gradle зарегистрировал 18 tests/0 failures, также успешно собраны три APK. На QV710AF65F (API 31, arm64-v8a) оба test method дали по `OK (1 test)`: request через reliable-pipe PFD и около 18 chunks поверх initial 8 credits, а также descriptor exact/short/trailing/reliable producer error и idempotent close. Это только `PARTIAL`-доказательство: еще отсутствуют cross-process callback completion/tool PFD, wrong UID, hostile death/update, реальные plugin/model, runtime/UI и `ai.*`; поэтому общие пункты R1 и exit gates остаются неотмеченными. Позднее добавлен отмеченный узкий срез hostile Android session conformance, зафиксированный isolated H1 commits `edd10008f`/`06ebc788c` и интеграционными commits `0cbc19d9f`/`72eb4d0c0`. Focused Gradle прошел suites coordinator 18/0 и fake provider 31/0 и успешно собрал три APK. На QV710AF65F (API 31, arm64-v8a) семь точных instrumentation methods дали по `OK (1 test)`: ordinary-pipe completion callback подтвердил межпроцессную передачу ownership PFD, exact length/EOF, SHA-256, UTF-8 materialization и cleanup; tool PFD был принят во владение и затем единожды отклонен с `TOOLS_UNSUPPORTED`; chunk-before-start, sequence-gap и invalid descriptor reference завершились fail-closed; duplicate terminal был ограничен smoke с одним terminal; stall отменен host после `Started` с повторным использованием owner/gate. Этот `[x]` остается узким доказательством: cross-process reliable-pipe status, wrong UID, no-credit, provider death, package update/uninstall, реальные plugin/model, runtime/UI и `ai.*` не покрыты. Общие пункты R1 и exit gates остаются неотмеченными. Актуальное состояние флажков приведено в дорожной карте проекта.

- [Открыть ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### История выпусков

******

# v1.0.0

###### 2026/08/08

* `Функция` Локальный provider протокола AI Text Generation V1 с ID и движком `ai-text-generation`, provider ID `autojs6.local.text` и вариантом `default`
* `Функция` Генерация обычного текста LiteRT-LM только на CPU с историей system, user и assistant и потоком под управлением credits
* `Функция` Импорт `.litertlm` через SAF в закрытое хранилище с пределом 8 GiB, резервом места, SHA-256, fsync и атомарной активацией
* `Функция` Один активный сеанс, ограниченный I/O, квоты дескрипторов, отмена, timeout, одно конечное состояние и проверка вызывающего AutoJs6 с той же подписью
* `Функция` Явное отсутствие возможностей reasoning, tools, structured JSON, usage, сети и credential
* `Функция` APK arm64-v8a, x86_64 и universal с README, changelog, интерфейсом Android и инструкциями плагина на 10 языках
* `Улучшение` Сохранение предыдущих поколений моделей с именами по hash SHA-256 после импорта с заменой для предотвращения межпроцессных гонок с `:provider`, при этом файлы продолжают занимать закрытое хранилище
* `Улучшение` Добавлены координатор одного импорта на уровне приложения и синхронизированный через fsync pending journal для продолжения при пересоздании Activity, восстановления при холодном запуске, очистки stale временных файлов и удаления только destination текущей попытки, которые никогда не публиковались, с сохранением опубликованных, current и исторических поколений с hash
* `Зависимость` Добавлен LiteRT-LM 0.15.0 для генерации текста на CPU устройства

##### Другие выпуски

* [CHANGELOG-ru.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-ru.md)

******

### Сборка

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Релизная сборка:

```powershell
.\gradlew.bat :app:assembleRelease
```

Параметры берутся из `version.properties`. Текущий минимальный SDK 24, целевой SDK 36, требуется JDK 21 или новее.

ABI протокола предоставляется локальными AAR репозитория в `libs`:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-text-generation-api.aar
```

Runtime использует LiteRT-LM 0.15.0 из Maven. Релиз сохраняет классы LiteRT-LM и создает два APK для ABI и один universal APK.

******

### Лицензия

******

Исходный код проекта распространяется по MPL-2.0. LiteRT-LM и другие сторонние компоненты сохраняют свои лицензии.

******

### Структура ресурсов

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` создает README и встроенные changelog на 10 языках из JSON. Строки Android находятся в собственных каталогах ресурсов.

******

### Ссылки

******

- Документация AutoJs6: https://docs.autojs6.com
- Проект LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM
