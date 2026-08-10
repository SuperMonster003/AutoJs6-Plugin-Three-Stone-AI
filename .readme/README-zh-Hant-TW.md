<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>本機 AI 文字生成外掛. 使用 LiteRT-LM 在裝置端串流生成純文字</p>

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
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- 繁體中文 (台灣) [zh-Hant-TW] # 目前
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

AI Text Generation 是 AutoJs6 的獨立 AI Text Generation 協定 V1 裝置端 provider. 它在 CPU 上執行使用者匯入的 LiteRT-LM 模型, 接收純文字訊息歷史, 並透過受控串流工作階段回傳純文字.

******

### 功能

******

- 透過 Android 系統檔案選擇器匯入 `.litertlm` 模型套件, 並將驗證後的副本儲存到應用程式私人儲存空間.
- 使用純文字 system, user 和 assistant 歷史建立本機生成請求.
- 透過 credit 背壓依序傳送文字 chunk, 並只發布一個完成, 錯誤或取消終態.
- 列出目前已匯入模型, 並在匯入取代後為主程式提供新的模型清單 generation.
- 完全在裝置端以 CPU backend 執行, 不下載模型, 不呼叫遠端推論服務.

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

### 外掛介面

******

主程式透過以下識別發現並呼叫外掛:

```text
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

外掛宣告 ON_DEVICE 執行位置和 NONE credential 模式. 它只宣告 `streaming` 能力及 `text/plain` 輸入輸出.

需要主程式建置版本 5270 或更高版本. 發布產物包含 arm64-v8a, x86_64, universal APK.

******

### 主程式整合狀態

******

> 目前 AutoJs6 主儲存庫尚未提供 AI Android adapter, provider selector 或 runtime bridge, 內建 `ai.*` 也尚未遷移到此協定. 僅安裝本外掛不會切換現有 `ai.*` 呼叫. 端到端使用仍需未來提供或由主程式明確啟用 adapter 並選擇此 provider.

******

### 安全性和隱私

******

外掛不要求網路或儲存權限. 模型只透過系統檔案選擇器授予的 URI 讀取, 以串流 SHA-256 校驗和 fsync 寫入應用程式私人 `files/models` 目錄, 再透過同目錄原子 pointer 取代啟用. Provider 服務也會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬及雙方簽章.

******

### 執行限制

******

- 模型匯入硬上限為 8 GiB, 且匯入後至少保留 256 MiB 可用空間.
- 應用程式層級單一匯入協調器使 Activity 重建不會中斷進行中的匯入. Fsync pending journal 支援冷啟動復原並清理 stale `.incoming`, `.current` 和 `.pending` 暫存檔案. 復原只會刪除本次嘗試新建且從未由 current metadata 發布的 destination, 已發布或 current 模型及歷史 hash 代次均會保留.
- 為避免與獨立 `:provider` 程序發生競態, 取代匯入後仍會保留先前以 SHA-256 hash 命名的模型代次. 這些檔案會繼續占用應用程式私人儲存空間.
- 同一程序最多有一個作用中生成工作階段. 請求描述元會在非同步處理前複製並依協定配額關閉.
- Provider 宣告的內容上限為 256 KiB, 輸出上限為 64 KiB, 請求和模型也可施加更低上限.
- 串流輸出使用有限 credit 和有界 chunk, 防止無限制緩衝或無背壓回呼.
- 取消, 工作階段關閉和逾時會停止結果發布, 並透過唯一終態結束請求.

******

### 未宣告的能力

******

- 不宣告 reasoning, tools, structured JSON 或 usage 能力.
- 不接受 tool 角色訊息, tool schema, tool call 或 tool result.
- 不提供連網模型探索, 模型下載, 雲端推論或 credential 流程.
- 不宣告 GPU 或 NPU backend. `.litertlm` 副檔名本身不保證模型可由目前 LiteRT-LM runtime 載入.

******

### 路線圖

******

`R0` 仍在進行中. 2026-08-10 的建置門檻已通過: 32 tests/0 failures, lint 0 error, Debug/Release `BUILD SUCCESSFUL` (3m37s), 且 `VERSION_BUILD`/`BUILD_TIME` 未變更; 真實模型、clipboard 與範例腳本實機 smoke 仍待執行. `R1` 現已涵蓋四個沒有生產呼叫點的預設關閉且未接線宿主切片: 唯讀 PackageManager `exact-action discovery`/`exact-component reinspection`, 明確元件 metadata-only Binder 繫結, transport-independent model-list transcript policy/catalog, 以及 Android model-list coordinator 與 `IAiModelListCallback` Binder transport. 舊 metadata handshake 複查實際到達的身分邊界, 且僅在 absolute deadline 到期時 interface descriptor、`getProviderInfo()` 或 `getCapabilities()` 同步 Binder RPC 仍在執行才熔斷. model-list policy 有界嚴格解碼單頁或多頁結果及 provider error, 拒絕 listing generation 漂移、token 重放/循環、重複 model ID、能力不相容及過期/重複 callback, 並讓互相競爭的終態只有一個勝出. coordinator 僅在 listing 初始化階段於同一個完成 descriptor 驗證的 Binder 上複驗 provider metadata, 隨後在每次 initial 或 continuation dispatch 前以 `AiTextProviderPackageSnapshot.samePackageIdentityAs` 精確複查 package/component identity. 每個 callback 在唯一一次 payload copy 前依序核驗呼叫 UID、typed page/error envelope 大小和有界 flood slot; page-token ledger 按完整且穩定的 pinned identity 隔離並有界持有. 與舊 handshake 不同, coordinator 對任何已接納的 `operationsInFlight` 保留 watchdog, 包括 exact PackageManager inspect、bind、prepare、dispatch 與 callback admission; deadline 到期仍未 unwind 時熔斷 exact component. 兩種 fuse 均無法強制中止卡住的 operation; coordinator gate 保持 `BUSY` 直到延遲 unwind. 該切片不含 session/`openSession`、PFD、credit、`IAiTextCallback`、session dispatch、runtime/UI 整合或 `ai.*` 路由. 2026-08-10 isolated AutoJs6 Gradle gate 通過 coordinator 15/0, 並成功 assemble host Debug、androidTest 與 fake APK, version metadata 未變更. QV710AF65F (API 31, arm64-v8a) 的 metadata handshake 與 model-list 正向 PARTIAL 各為 `OK (1 test)`, `pageSize=1` 收集四頁/四個 fake model; signer/hash、前後 identity、非 main callback 與唯一終態詳見主倉 evidence. 三包安裝前均不存在, 清理後恢復為均不存在. 該證據只支援窄 R1 item, 廣義工作項與退出門檻保持未勾選. QV710AF65F 沒有真實插件/模型, 因此 R0 實機覆蓋仍未完成. `R2` 至 `R8` 仍為規劃項目. 勾選狀態以專案路線圖為準.

- [檢視 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.0.0

###### 2026/08/08

* `新增` AI Text Generation 協定 V1 裝置端 provider, 外掛 ID 和引擎為 `ai-text-generation`, provider ID 為 `autojs6.local.text`, 變體為 `default`
* `新增` CPU-only LiteRT-LM 純文字生成, 支援 system, user 和 assistant 歷史及 credit 背壓串流輸出
* `新增` 透過 SAF 匯入 `.litertlm` 到應用程式私人儲存空間, 包含 8 GiB 上限, 空間預留, SHA-256, fsync 和原子啟用
* `新增` 單一作用中工作階段, 有界 I/O, descriptor 配額, 取消, 逾時, 唯一終態及同簽章 AutoJs6 呼叫端核驗
* `新增` 明確不宣告 reasoning, tools, structured JSON, usage, 網路或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 種語言的 README, 更新日誌, Android 介面和外掛說明
* `優化` 為避免獨立 `:provider` 程序競態, 取代匯入後保留先前以 SHA-256 hash 命名的模型代次, 保留檔案會繼續占用應用程式私人儲存空間
* `優化` 加入應用程式層級單一匯入協調器和 fsync pending journal, 在 Activity 重建時保持匯入, 支援冷啟動復原和 stale 暫存檔案清理, 刪除僅限本次新建而從未發布的 destination, 並保留已發布, current 和歷史 hash 代次
* `相依性` 附加 LiteRT-LM 0.15.0, 用於裝置端 CPU 文字生成

##### 更多版本

* [CHANGELOG-zh-Hant-TW.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-TW.md)

******

### 建置

******

```powershell
.\gradlew.bat :app:assembleDebug
```

發布建置:

```powershell
.\gradlew.bat :app:assembleRelease
```

建置參數來自 `version.properties`. 目前最低 SDK 為 24, 目標 SDK 為 36, 建置 JDK 為 21 或更高版本.

協定 ABI 由儲存庫 `libs` 目錄中的本機 AAR 提供:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-text-generation-api.aar
```

執行階段透過 Maven 使用 LiteRT-LM 0.15.0. 發布建置保留 LiteRT-LM runtime 類別, 並生成兩個 ABI APK 和一個 universal APK.

******

### 授權

******

專案原始碼使用 MPL-2.0. LiteRT-LM 和其他第三方元件繼續適用各自的授權.

******

### 資源配置

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
