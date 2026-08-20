<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>Плагин локального ИИ. Потоковая генерация текста на устройстве через LiteRT-LM, без сети</p>

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

On-Device AI — официальный плагин AutoJs6 для генерации текста ИИ на устройстве. Он выполняет импортированные пользователем модели LiteRT-LM на CPU, принимает историю сообщений в виде обычного текста и возвращает обычный текст через управляемую потоковую сессию. Все вычисления выполняются локально: без доступа к сети и без передачи данных.

******

### Функции

******

- Импорт пакета модели `.litertlm` через системный выбор Android и хранение проверенной копии в закрытом хранилище приложения.
- Проверка закрытого хранилища до открытия системного выбора, отображение текущего бюджета импорта и ожидаемого размера закрытой копии, а также повторная проверка выбранного файла до копирования.
- Создание локальных запросов из истории system, user и assistant в обычном тексте.
- Последовательная передача текстовых chunks с обратным давлением credits и публикация ровно одного конечного состояния завершения, ошибки или отмены.
- Просмотр, выбор и переименование импортированных моделей, удаление невыбранных моделей и очистка файлов моделей без ссылок из менеджера.
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
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1
required host build: 5270
```

Плагин объявляет выполнение ON_DEVICE и режим credential NONE. Объявлены только возможность `streaming` и ввод и вывод `text/plain`.

Требуется build хоста 5270 или новее. Выпуски включают варианты APK arm64-v8a, x86_64, universal.

******

### Состояние интеграции с хостом

******

> В AutoJs6 (сборка 5276 и новее) `ai.ask`, `ai.chat` и `ai.stream` поддерживают маршрут локального плагина: передайте `plugin: true`, чтобы выбрать этот плагин; при единственной модели идентификатор модели можно опустить. `ai.models({ plugin: true })` перечисляет импортированные модели. Если плагин не установлен, отключен в Центре плагинов или модель не импортирована, скрипт получает понятную ошибку. Поддерживается и явное закрепление через `plugin: { component, providerId, modelId }`.

******

### Безопасность и конфиденциальность

******

Плагин не запрашивает разрешения сети или хранилища. Модель читается только через URI от системного выбора, SHA-256 вычисляется при копировании в закрытый каталог `files/models`, затем вызывается fsync и модель активируется атомарной заменой pointer в том же каталоге. Службы также проверяют имя пакета AutoJs6, принадлежность вызывающего UID и совпадение подписей.

******

### Рабочие ограничения

******

- Импорт модели имеет жесткий предел 8 GiB и должен оставить не менее 256 MiB свободного места.
- Координатор одного импорта на уровне приложения сохраняет выполняемую работу при пересоздании Activity. Синхронизированный через fsync pending journal обеспечивает восстановление при холодном запуске и очистку stale временных файлов `.incoming`, `.current` и `.pending`. Восстановление удаляет только destination, созданный текущей попыткой и никогда не опубликованный через current metadata; опубликованные, current и исторические поколения с hash сохраняются.
- Чтобы избежать межпроцессных гонок с изолированным процессом `:provider`, импорт не удаляет автоматически предыдущие поколения моделей с именами по hash SHA-256. Менеджер позволяет удалить невыбранные модели из каталога и очистить файлы с hash-именами, на которые каталог больше не ссылается.
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

Дорожная карта организована вокруг доставляемых пользовательских функций, каждая проверяется отдельно

- [Открыть ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### История выпусков

******

# v1.1.0

###### 2026/08/20

* `Функция` Плагин переименован в On-Device AI и позиционируется как официальный локальный ИИ-плагин AutoJs6
* `Функция` Совместимость с сокращенным селектором `plugin: true` для `ai.ask`/`ai.chat`/`ai.stream` и перечислением моделей `ai.models` в AutoJs6
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
