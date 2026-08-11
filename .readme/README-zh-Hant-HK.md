<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>本地 AI 文字生成插件. 使用 LiteRT-LM 在裝置端串流生成純文字</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- 繁體中文 (香港) [zh-Hant-HK] # 目前
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### 簡介

******

AI Text Generation 是 AutoJs6 的獨立 AI Text Generation 協議 V1 裝置端 provider. 它在 CPU 上執行使用者匯入的 LiteRT-LM 模型, 接收純文字訊息歷史, 並透過受控串流工作階段傳回純文字.

******

### 功能

******

- 透過 Android 系統檔案選擇器匯入 `.litertlm` 模型套件, 並將驗證後的副本儲存到應用程式私人儲存空間.
- 使用純文字 system, user 和 assistant 歷史建立本地生成請求.
- 透過 credit 背壓按序傳送文字 chunk, 並只發佈一個完成, 錯誤或取消終態.
- 列出目前已匯入模型, 並在匯入替換後為主程式提供新的模型清單 generation.
- 完全在裝置端以 CPU backend 執行, 不下載模型, 不呼叫遠端推理服務.

******

### 模型和資料格式

******

版本 1 僅宣告以下模型和文字範圍:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### 插件介面

******

主程式透過以下標識發現並呼叫插件:

```text
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

插件宣告 ON_DEVICE 執行位置和 NONE credential 模式. 它只宣告 `streaming` 能力及 `text/plain` 輸入輸出.

需要主程式構建版本 5270 或更高版本. 發佈產物包含 arm64-v8a, x86_64, universal APK.

******

### 主程式整合狀態

******

> AutoJs6 現已提供明確 public `ai.ask(..., { plugin: ... })` production route, 目前支援單條 plain-text user message 和 non-stream 模式, 嚴格選擇 provider/model 且不 cloud fallback. 真實 release plugin/model smoke 已在 QV710AF65F (API 31, arm64-v8a) 通過. 僅安裝 plugin 仍不會切換 legacy `ai.*`; script 必須傳入明確 plugin selector 並使用已匯入的 modelId. 舊 JavaAdapter/raw Binder 示例、clipboard 流程、UI、`chat` 和 `stream` 仍待驗證.

******

### 安全性和私隱

******

插件不要求網絡或儲存權限. 模型只透過系統檔案選擇器授予的 URI 讀取, 以串流 SHA-256 校驗和 fsync 寫入應用程式私人 `files/models` 目錄, 再透過同目錄原子 pointer 替換啟用. Provider 服務亦會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬及雙方簽名.

******

### 執行限制

******

- 模型匯入硬上限為 8 GiB, 且匯入後至少保留 256 MiB 可用空間.
- 應用程式級單匯入協調器使 Activity 重建不會中斷正在進行的匯入. Fsync pending journal 支援冷啟動復原並清理 stale `.incoming`, `.current` 和 `.pending` 暫存檔案. 復原只會刪除本次嘗試新建且從未由 current metadata 發佈的 destination, 已發佈或 current 模型及歷史 hash 代次均會保留.
- 為避免與獨立 `:provider` 進程發生競態, 替換匯入後仍會保留先前以 SHA-256 hash 命名的模型代次. 這些檔案會繼續佔用應用程式私人儲存空間.
- 同一進程最多有一個活動生成工作階段. 請求描述符會在非同步處理前複製並按協議配額關閉.
- Provider 宣告的內容上限為 256 KiB, 輸出上限為 64 KiB, 請求和模型亦可施加更低上限.
- 串流輸出使用有限 credit 和有界 chunk, 防止無限制緩衝或無背壓回呼.
- 取消, 工作階段關閉和逾時會停止結果發佈, 並透過唯一終態結束請求.

******

### 未宣告的能力

******

- 不宣告 reasoning, tools, structured JSON 或 usage 能力.
- 不接受 tool 角色訊息, tool schema, tool call 或 tool result.
- 不提供連網模型發現, 模型下載, 雲端推理或 credential 流程.
- 不宣告 GPU 或 NPU backend. `.litertlm` 副檔名本身不保證模型可由目前 LiteRT-LM runtime 載入.

******

### 路線圖

******

`R0` 仍在進行中. 2026-08-10 的構建門檻已通過: 32 tests/0 failures, lint 0 error, Debug/Release `BUILD SUCCESSFUL` (3m37s), 且 `VERSION_BUILD`/`BUILD_TIME` 未變化; 真實 plugin/model 的 public `ai.ask` 實機 smoke 現已通過, clipboard 寫入與 Activity 重建後複製仍待執行. `R1` 現已覆蓋五個沒有生產調用點的默認關閉且未接線宿主切片: 只讀 PackageManager `exact-action discovery`/`exact-component reinspection`, 顯式組件 metadata-only Binder 綁定, transport-independent model-list transcript policy/catalog, 以及 Android model-list coordinator 與 `IAiModelListCallback` Binder transport. 舊 metadata handshake 複查實際到達的身份邊界, 且僅在 absolute deadline 到期時 interface descriptor、`getProviderInfo()` 或 `getCapabilities()` 同步 Binder RPC 仍在執行才熔斷. model-list policy 有界嚴格解碼單頁或多頁結果及 provider error, 拒絕 listing generation 漂移、token 重放/循環、重複 model ID、能力不相容及過期/重複 callback, 並讓互相競爭的終態只有一個勝出. coordinator 僅在 listing 初始化階段於同一個完成 descriptor 驗證的 Binder 上複驗 provider metadata, 隨後在每次 initial 或 continuation dispatch 前以 `AiTextProviderPackageSnapshot.samePackageIdentityAs` 精確複查 package/component identity. 每個 callback 在唯一一次 payload copy 前依次核驗調用 UID、typed page/error envelope 大小和有界 flood slot; page-token ledger 按完整且穩定的 pinned identity 隔離並有界持有. 與舊 handshake 不同, coordinator 對任何已接納的 `operationsInFlight` 保留 watchdog, 包括 exact PackageManager inspect、bind、prepare、dispatch 與 callback admission; deadline 到期仍未 unwind 時熔斷 exact component. 兩種 fuse 均不能強制中止卡住的 operation; coordinator gate 保持 `BUSY` 直到延遲 unwind. 第五個切片是預設關閉、未接線且 transport-independent 的 `AiTextProviderSessionPolicy`: 它固定 plan/request/provider/model/context, 將同步 `openSession` 返回前收到的 callback 保留在明確 commit gate 之後, 依次核驗 UID、typed envelope 邊界與 descriptor ownership, 自動管理 initial 8 credits 和有界逐 chunk backpressure, 有界接納 started/chunk/usage/completed/failed/cancelled, 讓 provider completion、cancel、timeout 與 death 只有一個終態勝出, 並等待 descriptor、remote control 及 cleanup 全部 settled 後才發佈終態; tools 一律 fail-closed. 它不含 Android `IAiTextCallback`/`openSession`/PFD adapter, 不在 session dispatch 前重新檢查 package identity, 也不含實際 session dispatch、runtime/UI 或 `ai.*` 路由. 其證據現包含原始碼/靜態與 focused JVM Gradle: standalone Kotlin 2.3.21 K2/JDK 21/JVM 17 編譯、40/40 JUnit 以及同一產物 30 輪 1200/1200; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` 程序 exit 0, XML 記錄 40 tests/0 skipped/0 failures/0 errors, Kotlin daemon 重試後透過 fallback 編譯仍成功. 它仍不含 Android `IAiTextCallback`/`openSession`/PFD adapter、ADB/device、runtime/UI 或 `ai.*` 證據; 廣義 R1 與退出門檻保持未勾選. 2026-08-10 isolated AutoJs6 Gradle gate 通過 coordinator 15/0, 並成功 assemble host Debug、androidTest 與 fake APK, version metadata 未變化. QV710AF65F (API 31, arm64-v8a) 的 metadata handshake 與 model-list 正向 PARTIAL 各為 `OK (1 test)`, `pageSize=1` 收集四頁/四個 fake model; signer/hash、前後 identity、非 main callback 與唯一終態詳見主倉 evidence. 三包安裝前均不存在, 清理後恢復為均不存在. 該證據只支持窄 R1 item, 廣義工作項與退出門檻保持未勾選. QV710AF65F 沒有真實插件/模型, 因此 R0 實機覆蓋仍未完成. `R2` 至 `R8` 仍為規劃項目. 第六個窄切片加入預設停用且未接線的 Android exact-component session coordinator 與 `IAiTextCallback`/PFD transport. standalone K2 通過 18/18, 同一 artifact 連續 30 rounds 為 540/540; focused Gradle 記錄 18 tests/0 failures, 三個 APK 亦成功 assemble. 在 QV710AF65F (API 31, arm64-v8a) 上, 兩個 test method 各為 `OK (1 test)`: request 使用 reliable-pipe PFD, 約 18 chunks 跨越 initial 8 credits, 並涵蓋 descriptor exact/short/trailing/reliable producer error 與 idempotent close. 此證據僅為 `PARTIAL`: 尚缺 callback completion/tool PFD 跨程序、wrong UID、hostile death/update、真實插件/模型、runtime/UI/`ai.*`; 因此廣義 R1 工作項與退出門檻保持未勾選. 後續新增一個已勾選的 hostile Android session conformance 窄切片, 由 isolated H1 commits `edd10008f`/`06ebc788c` 與主倉整合 commits `0cbc19d9f`/`72eb4d0c0` 記錄. focused Gradle 通過 coordinator 18/0 與 fake provider 31/0, 並成功 assemble 三個 APK. 在 QV710AF65F (API 31, arm64-v8a) 上, 七個 exact instrumentation method 各為 `OK (1 test)`: ordinary-pipe completion callback 驗證跨程序 PFD ownership 轉移、exact length/EOF、SHA-256、UTF-8 物化與 cleanup; tool PFD 被接管後唯一拒絕為 `TOOLS_UNSUPPORTED`; chunk-before-start、sequence-gap 與非法 descriptor reference 均 fail-closed; duplicate terminal 僅取得有界 single-terminal smoke 結果; stall 在 `Started` 後由 host cancel, 隨後 owner/gate 可重用. 此 `[x]` 仍是窄範圍證據: 未覆蓋 cross-process reliable-pipe status、wrong UID、no-credit、provider death、package update/uninstall、真實插件/模型、runtime/UI 或 `ai.*`. 廣義 R1 工作項與退出門檻保持未勾選. 勾選狀態以專案路線圖為準. 後續新增一個已勾選的 H2 生命週期窄切片, 由主倉整合 commits `e5bd92b16`/`10dad3e39`/`0b9a94742` 記錄. focused Gradle 通過 coordinator 18/0 與 fake provider 31/0, 三個 APK 均成功 assemble. 在 QV710AF65F (API 31, arm64-v8a) 上, `callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner` 與 `providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner` 各返回 `OK (1 test)`: 前者在發佈任何 transcript 前將 isolated-process callback 唯一拒絕為 `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH`, 隨後 owner/gate 可重用; 後者在收到 `Started`、一個 sequence 0 合法 chunk 及 credit replenishment acknowledgement 後結束 provider 程序, host 僅發佈一個 `BinderDied`, 隨後 owner/gate 可重用. local/device SHA-256 精確一致: host `0685CE99C0F9E8F9056BE5F3A8EEBC2C7EA5FFCA2D422E21EAC69D0CB3364629`, androidTest `7C91A0AF651E2098AD124FF8A89AE3AC3018E0F1D0DEC068367595E964739178`, fake provider `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`; 三者 v2 signer certificate SHA-256 均為 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`. host、androidTest 與 fake 三個套件在安裝前均為 absent, cleanup 後再次均為 absent. 該 Android 證據僅是 wrong-UID/provider-death 的 PARTIAL. no-credit 仍只有 JVM 證據; update/uninstall 形狀仍僅由 final-reinspection JVM 用例 (`lastUpdateTime` 漂移與 `Completed(emptyList())`) 覆蓋, 未執行實機 package 變更. cross-process no-credit、實機 update/uninstall、真實插件/模型、runtime/UI 與 `ai.*` 仍未覆蓋; 廣義 R1 工作項與退出門檻保持未勾選. 新增的已勾選窄切片由 AutoJs6 主倉 commits `e4297a688`/`64db31ea5` 記錄, 接入明確 `ai.ask(..., { plugin: ... })` production source route: selector 嚴格固定 exact component/provider/model; `plugin` 缺席時保留 legacy cloud 行為, 一旦出現則不讀取 vault、不進入 HTTP/cloud、也不 fallback; 目前只接納一條 plain-text user message 與 non-stream request, engine close 會傳播 remote session teardown. standalone K2 為 15/15, focused Gradle 為 19/19 且 Android main compile 通過, 均為 source-only 證據. 此切片不含真實 plugin/model/device 執行, 也不含 UI、`chat` 或 `stream` route; 廣義 R1 工作項與退出門檻維持未勾選. 2026-08-11, 實測 APK 與設備運行直接來源為隔離樹 commit `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`; 同一 test blob 後續在當前 main parent 上集成為 `7ce26ceea`, 兩者 app tree 一致, 但後者不是 APK 直接構建來源. QV710AF65F (API 31, arm64-v8a) 以真實 Rhino global `ai.ask(..., { plugin: ... })` one-shot 經 release plugin 運行 2,583,085,056-byte LiteRT-LM. model SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42` 派生 modelId `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`; `AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin` 返回 `OK (1 test)`/7.071s. local/device APK SHA-256 精確一致: host `b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`, androidTest `09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf`, plugin `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`; 三者 v2 signer certificate SHA-256 均為 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`, device base 與候選 APK 一致. plugin test/lint/Debug/Release 門檻與 host focused 19/19 tests/assemble 均通過; users 0/10 的 host/test/plugin 精確 package 與專用 staging/UI-dump 路徑在驗證前後均為 absent. 這不勾選 R0 raw Binder/JavaAdapter 示例基線項; 它僅勾選 R6 窄範圍的可選真實模型 smoke/本地入口門檻, 但仍僅是單設備、單模型、non-stream one-shot 與 appDebug host 證據. 它不覆蓋 clipboard、stream/`chat`、tools、structured output、usage、release host/API 矩陣、實機 update/uninstall、性能或 soak. DocumentsUI last-location 狀態無法無損還原. 廣義 R1 工作項與退出門檻仍未勾選.

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.0.0

###### 2026/08/08

* `新增` AI Text Generation 協議 V1 裝置端 provider, 插件 ID 和引擎為 `ai-text-generation`, provider ID 為 `autojs6.local.text`, 變體為 `default`
* `新增` CPU-only LiteRT-LM 純文字生成, 支援 system, user 和 assistant 歷史及 credit 背壓串流輸出
* `新增` 透過 SAF 匯入 `.litertlm` 到應用程式私人儲存空間, 包含 8 GiB 上限, 空間預留, SHA-256, fsync 和原子啟用
* `新增` 單活動工作階段, 有界 I/O, descriptor 配額, 取消, 逾時, 唯一終態及同簽名 AutoJs6 呼叫方核驗
* `新增` 明確不宣告 reasoning, tools, structured JSON, usage, 網絡或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 種語言的 README, 更新日誌, Android 介面和插件說明
* `優化` 為避免獨立 `:provider` 進程競態, 替換匯入後保留先前以 SHA-256 hash 命名的模型代次, 保留檔案會繼續佔用應用程式私人儲存空間
* `優化` 加入應用程式級單匯入協調器和 fsync pending journal, 在 Activity 重建時保持匯入, 支援冷啟動復原和 stale 暫存檔案清理, 刪除僅限本次新建而從未發佈的 destination, 並保留已發佈, current 和歷史 hash 代次
* `依賴` 附加 LiteRT-LM 0.15.0, 用於裝置端 CPU 文字生成

##### 更多版本

* [CHANGELOG-zh-Hant-HK.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-HK.md)

******

### 構建

******

```powershell
.\gradlew.bat :app:assembleDebug
```

發佈構建:

```powershell
.\gradlew.bat :app:assembleRelease
```

構建參數來自 `version.properties`. 目前最低 SDK 為 24, 目標 SDK 為 36, 構建 JDK 為 21 或更高版本.

協議 ABI 由儲存庫 `libs` 目錄中的本地 AAR 提供:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-text-generation-api.aar
```

執行階段透過 Maven 使用 LiteRT-LM 0.15.0. 發佈構建保留 LiteRT-LM runtime 類別, 並生成兩個 ABI APK 和一個 universal APK.

******

### 授權條款

******

專案原始碼使用 MPL-2.0. LiteRT-LM 和其他第三方元件繼續適用各自的授權條款.

******

### 資源佈局

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` 從 JSON 來源產生 10 種語言的 README 和應用程式內更新日誌. Android 字串由各自資源目錄管理.

******

### 連結

******

- AutoJs6 文件: https://docs.autojs6.com
- LiteRT-LM 專案: https://github.com/google-ai-edge/LiteRT-LM
