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

> AutoJs6 теперь предоставляет явные публичные production-маршруты `ai.ask(..., { plugin: ... })`, prompt-only `ai.chat(..., { plugin: ... })` и `ai.stream(..., { plugin: ... })` с точным выбором component/provider/model и без cloud fallback. Public ask с реальным plugin/model имеет L3-подтверждение на устройстве; stream и chat с детерминированным fake provider имеют Android-подтверждение L2. Одна лишь установка plugin по-прежнему не перенаправляет прежний `ai.*`: script должен передать явный plugin selector и импортированный modelId. Chat/stream с реальной моделью и активная отмена на устройстве остаются неблокирующим долгом по доказательствам.

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

Roadmap организован вокруг 28 проверяемых продуктовых результатов. D0, D1 и D2 завершены; D3 — каталог нескольких моделей и выбор — остаётся текущим этапом, а его результат по стабильным metadata модели, состоянию выбора и идемпотентному повторному импорту того же SHA-256 достиг L1. Production — `1f92414`, focused tests — `c38a016`; один запуск `:app:testDebugUnitTest :app:assembleDebug` завершился `BUILD SUCCESSFUL` за 35 с: 48 задач, 16 XML-отчётов и 44 tests с 0 failures/errors/skipped, включая 5 случаев `ModelCatalogTest` и 4 `PendingModelTransactionPolicyTest`, при неизменном hash версии. В этом slice не запускались ADB и fault injection; migration L2, pager/listing и UI остаются открытыми. L1 означает реализацию, focused tests и затронутый build; L2 — интеграцию Android/Binder; L3 — подтверждение на устройстве с подписанным реальным plugin/model, а отсутствие доказательства более высокого уровня не переоткрывает завершённый результат.

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
