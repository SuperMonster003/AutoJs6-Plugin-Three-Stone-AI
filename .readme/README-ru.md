<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин локального ИИ. Вывод LiteRT-LM всегда локален; загрузки запускаются явно</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Языки

******

Текущий README.md поддерживает следующие языки:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- Русский [ru] # текущий
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### Введение

******

On-Device AI — официальный плагин AutoJs6 для генерации текста ИИ на устройстве. Он выполняет импортированные пользователем модели LiteRT-LM на явно выбранном CPU или совместимом GPU backend, принимает историю сообщений в виде обычного текста и возвращает обычный текст или текст JSON, ограниченный schema, через управляемую потоковую сессию. Все вычисления выполняются локально, без сети и передачи данных; сеть используется только когда пользователь явно скачивает рекомендуемую модель.

******

### Функции

******

- Импорт пакета модели `.litertlm` через системный выбор Android и хранение проверенной копии в закрытом хранилище приложения.
- Прямая загрузка закреплённой общедоступной модели LiteRT Community в выбранное пользователем место SAF с прогрессом, отменой, очисткой неполного файла и точной проверкой размера и SHA-256.
- Проверка закрытого хранилища до открытия системного выбора, отображение текущего бюджета импорта и ожидаемого размера закрытой копии, а также повторная проверка выбранного файла до копирования.
- Создание локальных запросов из истории system, user и assistant в обычном тексте.
- Передача `temperature`, `topK`, `topP` и `maxTokens` из `ai.ask`, `ai.chat` и `ai.stream` AutoJs6 в LiteRT-LM.
- Нативное ограничение вывода по JSON Schema в LiteRT-LM через `structuredJson` и `responseSchema` AutoJs6; завершенное значение остается текстом JSON для `JSON.parse`.
- Возврат точных количеств входных, выходных и общих токенов LiteRT-LM вместе с длительностью генерации провайдера через `ai.chat().usage` и потоковые события usage.
- Сохранение многоходового контекста в одном нативном Conversation LiteRT-LM через `ai.session` AutoJs6 с передачей только нового пользовательского запроса в следующих ходах.
- Повторное использование инициализированного Engine по SHA-256 модели без повторного холодного запуска для последовательных запросов к той же модели.
- При необходимости однократно инициализировать каждую импортированную модель, сохранять статус Доступна/Несовместима и повторно запускать проверку из менеджера моделей.
- Последовательная передача текстовых chunks с обратным давлением credits и публикация ровно одного конечного состояния завершения, ошибки или отмены.
- Просмотр, выбор и переименование импортированных моделей, удаление невыбранных моделей и очистка файлов моделей без ссылок из менеджера.
- Явный выбор `cpu`, `gpu` или `npu` через AutoJs6: CPU используется по умолчанию, GPU доступен только после проверки загрузки OpenCL, а NPU помечается недоступным, поскольку EAP runtime не включен.

******

### Форматы модели и данных

******

Версия 1 объявляет только следующий набор моделей и текста:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### Интерфейс плагина

******

Хост обнаруживает и вызывает плагин со следующими идентификаторами:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

Плагин объявляет выполнение ON_DEVICE и режим credential NONE. Объявлены возможности `streaming`, `usage`, `persistent-session` и `structured-json`; принимаются сообщения `text/plain` и schema ответа `application/json`, выводится текст `text/plain` или `application/json`. Протокол 1.3 добавляет явные backend profile и доступность на устройстве без скрытого отката на CPU.

Требуется build хоста 5276 или новее. Выпуски включают варианты APK arm64-v8a, x86_64, universal.

******

### Состояние интеграции с хостом

******

> В AutoJs6 (сборка 5276 и новее) `ai.ask`, `ai.chat` и `ai.stream` поддерживают маршрут локального плагина. `ai.session({ plugin: true })` создает постоянный многоходовый Conversation, а последующие вызовы `ask`, `chat` и `stream` передают только новый пользовательский запрос. `ai.ask(messages, { plugin: true })` сохраняет порядок текстовых сообщений с ролями `system`, `user` и `assistant`, причем последнее сообщение должно иметь роль `user`. `ai.chat` возвращает точные количества токенов в `usage` и измеренную длительность в `usage.raw.durationMillis`; `ai.stream` отправляет тот же накопленный usage перед завершением. Передайте `plugin: true`, чтобы выбрать этот плагин; при единственной модели идентификатор модели можно опустить. `ai.models({ plugin: true })` перечисляет модели и их `backendProfiles`. Генерация принимает `backend: 'cpu' | 'gpu' | 'npu'`; недоступный profile завершается явной ошибкой без отката на CPU. Если плагин не установлен, отключен в Центре плагинов или модель не импортирована, скрипт получает понятную ошибку. Поддерживается и явное закрепление через `plugin: { component, providerId, modelId }`. `responseSchema` неявно включает структурированный вывод; `structuredJson: true` без schema использует schema с корневым объектом по умолчанию. `ai.ask` и `ai.chat().text` по-прежнему возвращают текст JSON, потоковые delta являются частичным текстом JSON, а постоянный сеанс использует одну фиксированную schema и backend во всех ходах.

******

### Безопасность и конфиденциальность

******

Плагин запрашивает `INTERNET` только для загрузок рекомендуемых моделей, запущенных пользователем, и не запрашивает широкого доступа к хранилищу. Загрузки используют неизменяемые HTTPS-версии, закреплённые размер и SHA-256 и пишут только в выбранное место SAF; заголовок LiteRT-LM, размер, хеш, flush и fsync должны пройти проверку. Импорт по-прежнему читает только URI выбора, пишет проверенную копию в `files/models` и активирует её атомарно. Службы также проверяют пакет AutoJs6, UID и подписи.

******

### Рабочие ограничения

******

- Импорт модели имеет жесткий предел 8 GiB и должен оставить не менее 256 MiB свободного места.
- В процессе приложения выполняется только одна загрузка. Пересоздание Activity сохраняет прогресс и право отмены; при отмене или ошибке файл удаляется либо обнуляется. Завершение процесса всё же может оставить внешний неполный документ, который нужно удалить вручную.
- Координатор одного импорта на уровне приложения сохраняет выполняемую работу при пересоздании Activity. Синхронизированный через fsync pending journal обеспечивает восстановление при холодном запуске и очистку stale временных файлов `.incoming`, `.current` и `.pending`. Восстановление удаляет только destination, созданный текущей попыткой и никогда не опубликованный через current metadata; опубликованные, current и исторические поколения с hash сохраняются.
- Чтобы избежать межпроцессных гонок с изолированным процессом `:provider`, импорт не удаляет автоматически предыдущие поколения моделей с именами по hash SHA-256. Менеджер позволяет удалить невыбранные модели из каталога и очистить файлы с hash-именами, на которые каталог больше не ссылается.
- В процессе активен не более одного сеанса генерации. Дескрипторы дублируются до асинхронной работы и закрываются по квотам протокола.
- Provider кэширует не более одного Engine по паре SHA-256 модели и backend profile. Запросы с той же парой используют его повторно; изменение любого ключа, пять минут простоя или явное давление памяти безопасно освобождают Engine.
- Проверка модели подтверждает только успешный вызов `Engine.initialize()` на текущем устройстве со встроенной средой выполнения; она не оценивает качество вывода и может быть повторена после смены устройства или среды.
- Provider объявляет предел контекста 256 KiB и предел вывода 64 KiB. Запросы и модели могут задавать меньшие пределы.
- Schema ответа должна быть объектом JSON размером не более 64 KiB. Поддерживаются ключевые слова, реализованные во встроенной среде LiteRT-LM/LLGuidance; завершенный вывод строго разбирается и проверяется, поэтому для всего значения JSON требуется достаточный `maxTokens`.
- `maxTokens` принимает целые числа от 1 до 2 147 483 647. `temperature` должен быть конечным и неотрицательным, `topK` — положительным целым, а `topP` — конечным числом от 0 до 1. Если все три параметра sampling опущены, сохраняются настройки модели или engine; при частичном переопределении пропуски заполняются базовыми значениями LiteRT-LM: `topK: 1`, `topP: 0.95`, `temperature: 1`.
- Поток использует конечные credits и ограниченные chunks, исключая неограниченный буфер и callbacks без обратного давления.
- Количество токенов usage берется непосредственно из счетчиков KV cache и decode объекта Conversation LiteRT-LM без оценки по числу символов. `durationMillis` измеряет только вызов генерации плагина и не включает обнаружение, привязку, перечисление моделей и диспетчеризацию хоста.
- Постоянный `ai.session` допускает один активный ход и сохраняет нативный Conversation после нормального завершения; после отмены, timeout, ошибки генерации или явного закрытия его нужно создать заново.
- Отмена, закрытие сеанса и timeout прекращают публикацию и завершают запрос одним конечным состоянием.

******

### Необъявленные возможности

******

- Reasoning и tools не объявлены.
- Сообщения роли tool, schemas инструментов, tool calls и tool results не принимаются.
- Нет сетевого поиска моделей, загрузки произвольных URL, cloud вывода или процесса credential; доступен только закреплённый встроенный каталог рекомендаций.
- Вывод NPU не объявлен: profile виден как `unavailable` с причиной `npu-runtime-not-packaged`. GPU объявляется только при загрузке `libOpenCL.so`, а расширение `.litertlm` по-прежнему не гарантирует инициализацию модели.

******

### Дорожная карта

******

Дорожная карта организована вокруг доставляемых пользовательских функций, каждая проверяется отдельно

- [Открыть ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### История выпусков

******

# v1.1.0

###### 2026/08/21

* `Функция` Плагин переименован в On-Device AI и позиционируется как официальный локальный ИИ-плагин AutoJs6
* `Функция` Совместимость с сокращенным селектором `plugin: true` для `ai.ask`/`ai.chat`/`ai.stream` и перечислением моделей `ai.models` в AutoJs6
* `Функция` Передача `temperature`, `topK`, `topP` и `maxTokens` через протокол On-Device AI 1.1 в параметры sampling и ограничения выходных tokens LiteRT-LM
* `Функция` Возврат точных количеств входных, выходных и общих токенов LiteRT-LM вместе с измеренной провайдером длительностью генерации через `ai.chat().usage` и потоковые события usage
* `Функция` Постоянные сеансы протокола On-Device AI 1.2 и повторное использование многоходового Conversation через `ai.session` AutoJs6 без повторной передачи предыдущей истории
* `Функция` Нативное декодирование LiteRT-LM с ограничением JSON Schema через `structuredJson` и `responseSchema` AutoJs6 для одиночных вызовов, потокового вывода и постоянных сеансов со строгой проверкой завершенного JSON
* `Функция` Явные backend profile `cpu`, `gpu` и `npu` через протокол 1.3 и параметры генерации AutoJs6, с отчетом о совместимости устройства, раздельным кэшем для модели/profile и без отката с недоступных profile; GPU объявляется только после успешной проверки загрузки OpenCL, а NPU остается недоступным из-за отсутствия EAP runtime в пакете
* `Функция` Прямая загрузка закреплённых моделей LiteRT Community в выбранное место SAF с прогрессом, точной отменой, очисткой неполного файла, проверкой заголовка LiteRT-LM, размера и SHA-256 и последующим прямым импортом
* `Улучшение` Обновлены описание плагина, инструкция и README на 10 языках в соответствии с формализованным маршрутом `ai.*` локального плагина
* `Улучшение` ROADMAP переписан как дорожная карта функций с независимо проверяемыми пунктами

# v1.0.0

###### 2026/08/08

* `Функция` Локальный provider протокола On-Device AI V1 с ID и движком `on-device-ai`, provider ID `autojs6.on-device-ai` и вариантом `default`
* `Функция` Генерация обычного текста LiteRT-LM только на CPU с историей system, user и assistant и потоком под управлением credits
* `Функция` Импорт `.litertlm` через SAF в закрытое хранилище с пределом 8 GiB, резервом места, SHA-256, fsync и атомарной активацией
* `Функция` Один активный сеанс, ограниченный I/O, квоты дескрипторов, отмена, timeout, одно конечное состояние и проверка вызывающего AutoJs6 с той же подписью
* `Функция` Явное отсутствие возможностей reasoning, tools, structured JSON, usage, сети и credential
* `Функция` APK arm64-v8a, x86_64 и universal с README, changelog, интерфейсом Android и инструкциями плагина на 10 языках
* `Функция` Экран управления моделями для просмотра полного каталога и занятого места в закрытом хранилище, с атомарным выбором текущей модели без копирования файлов моделей
* `Улучшение` Сохранение предыдущих поколений моделей с именами по hash SHA-256 после импорта с заменой для предотвращения межпроцессных гонок с `:provider`, при этом файлы продолжают занимать закрытое хранилище
* `Улучшение` Добавлены координатор одного импорта на уровне приложения и синхронизированный через fsync pending journal для продолжения при пересоздании Activity, восстановления при холодном запуске, очистки stale временных файлов и удаления только destination текущей попытки, которые никогда не публиковались, с сохранением опубликованных, current и исторических поколений с hash
* `Зависимость` Добавлен LiteRT-LM 0.15.0 для генерации текста на CPU устройства

##### Другие выпуски

* [CHANGELOG-ru.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-ru.md)

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
on-device-ai-api.aar
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
