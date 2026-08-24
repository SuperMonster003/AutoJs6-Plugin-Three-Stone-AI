<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="three-stone-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>本機 AI 外掛. LiteRT-LM 推論始終在本地; 推薦模型下載僅由使用者明確發起</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- 繁體中文 (台灣) [zh-Hant-TW] # 目前
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### 簡介

******

3-Stone AI 是 AutoJs6 的官方本機 AI 文字生成外掛. 它在明確選擇的 CPU 或相容 GPU backend 上執行使用者匯入的 LiteRT-LM 模型, 接收純文字訊息歷史, 並透過受控串流工作階段回傳純文字或受 schema 約束的 JSON 文字. 全部推論在本地完成, 不連網也不上傳任何資料; 只有使用者明確下載推薦模型時才會連網.

******

### 功能

******

- 透過 Android 系統檔案選擇器匯入 `.litertlm` 模型套件, 並將驗證後的副本儲存到應用程式私人儲存空間.
- 將固定版本且無需登入的 LiteRT Community 推薦模型直接下載到使用者選擇的 SAF 位置, 支援進度, 取消, 殘缺檔案清理及精確大小與 SHA-256 驗證.
- 開啟系統檔案選擇器前預檢私人儲存空間, 顯示目前匯入預算與私人副本預計占用, 並在複製前再次檢查所選檔案.
- 使用純文字 system, user 和 assistant 歷史建立本機生成請求.
- 將 AutoJs6 `ai.ask`, `ai.chat` 和 `ai.stream` 的 `temperature`, `topK`, `topP` 與 `maxTokens` 傳遞到 LiteRT-LM.
- 透過 AutoJs6 `structuredJson` 與 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼; 完整結果仍為可供 `JSON.parse` 解析的 JSON 文字.
- 透過 AutoJs6 `ai.chat().usage` 與串流 usage 事件回傳 LiteRT-LM 精確的輸入, 輸出及總 token 數, 以及外掛端生成耗時.
- 透過 AutoJs6 `ai.session` 在同一個 LiteRT-LM 原生 Conversation 保留多輪上下文, 後續輪次只傳送新的使用者提示詞.
- 依模型 SHA-256 重複使用已初始化 Engine, 消除同一模型連續請求的重複冷啟動.
- 可選擇將每個匯入模型初始化一次, 持久保存其"可用/不相容"狀態, 並可在模型管理介面重新檢查.
- 透過 credit 背壓依序傳送文字 chunk, 並只發布一個完成, 錯誤或取消終態.
- 列出, 選取及重新命名已匯入模型, 刪除未選取模型, 並在管理介面一鍵回收未參照模型檔案.
- 透過 AutoJs6 明確選擇 `cpu`, `gpu` 或 `npu` backend; CPU 為預設值, GPU 僅在 OpenCL 載入探測通過後開放, NPU 因未封裝 EAP runtime 而明確回報不可用.

******

### 模型和資料格式

******

版本 1 僅宣告以下模型和文字範圍:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### 外掛介面

******

主程式透過以下識別發現並呼叫外掛:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

外掛宣告 ON_DEVICE 執行位置和 NONE credential 模式. 它宣告 `streaming`, `usage`, `persistent-session` 與 `structured-json` 能力, 接受 `text/plain` 訊息輸入和 `application/json` 回應 schema, 並輸出 `text/plain` 或 `application/json` 文字. 協議 1.3 新增明確 backend profile 與裝置級可用性, 並禁止靜默回退 CPU.

需要主程式建置版本 5276 或更高版本. 發布產物包含 arm64-v8a, x86_64, universal APK.

******

### 主程式整合狀態

******

> AutoJs6 (組建 5276 及以上) 的 `ai.ask`, `ai.chat` 與 `ai.stream` 支援本機外掛路由. `ai.session({ plugin: true })` 可建立持久多輪 Conversation, 後續 `ask`, `chat` 與 `stream` 呼叫只傳送新的使用者提示詞. `ai.ask(messages, { plugin: true })` 會依序保留純文字 `system`, `user` 與 `assistant` 訊息, 且最後一則訊息必須為 `user`. `ai.chat` 會在 `usage` 回傳精確 token 數, 並在 `usage.raw.durationMillis` 回傳實測生成耗時; `ai.stream` 會在完成前傳送同一份累計 usage. 傳入 `plugin: true` 即選擇本外掛, 單模型場景可省略模型 ID; `ai.models({ plugin: true })` 可列舉已匯入模型與 `backendProfiles`. 生成選項接受 `backend: 'cpu' | 'gpu' | 'npu'`; 不可用 profile 會明確失敗且絕不回退 CPU. 外掛未安裝, 未在外掛中心啟用或未匯入模型時, 指令碼會收到明確的錯誤提示. 亦可透過 `plugin: { component, providerId, modelId }` 顯式固定元件. `responseSchema` 會隱式啟用結構化輸出; 僅設定 `structuredJson: true` 時使用預設的物件根 schema. `ai.ask` 和 `ai.chat().text` 仍回傳 JSON 文字, 串流 delta 是未完整的 JSON 片段, 持久工作階段則在所有輪次固定使用同一 schema 與 backend.

******

### 安全性和隱私

******

外掛僅為使用者主動發起的推薦模型下載要求 `INTERNET` 權限, 不要求廣泛儲存權限. 目錄下載使用不可變 HTTPS 版本及固定位元組數與 SHA-256, 僅寫入使用者選擇的 SAF 位置; LiteRT-LM 檔頭, 大小, 摘要, flush 與 fsync 全部通過後才算完成. 匯入仍只讀取系統選擇器授予的 URI, 將驗證副本串流寫入私人 `files/models` 並原子啟用. Provider 服務也會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬及雙方簽章.

******

### 執行限制

******

- 模型匯入硬上限為 8 GiB, 且匯入後至少保留 256 MiB 可用空間.
- 應用程式程序內一次只執行一個模型下載. Activity 重建會保留進度與取消歸屬; 取消或失敗時會刪除新建目標, 不支援刪除時則截斷清空. 程序被終止仍可能留下外部殘缺檔案, 使用者應手動刪除.
- 應用程式層級單一匯入協調器使 Activity 重建不會中斷進行中的匯入. Fsync pending journal 支援冷啟動復原並清理 stale `.incoming`, `.current` 和 `.pending` 暫存檔案. 復原只會刪除本次嘗試新建且從未由 current metadata 發布的 destination, 已發布或 current 模型及歷史 hash 代次均會保留.
- 為避免與獨立 `:provider` 程序發生競態, 匯入時不會自動刪除先前以 SHA-256 hash 命名的模型代次. 模型管理介面可刪除未選取的 catalog 模型, 並回收不再由 catalog 參照的 hash 命名檔案.
- 同一程序最多有一個作用中生成工作階段. 請求描述元會在非同步處理前複製並依協定配額關閉.
- Provider 以模型 SHA-256 與 backend profile 為聯合鍵, 最多快取一個已初始化 Engine. 相同組合的連續請求會重複使用; 任一鍵變更, 閒置 5 分鐘或收到明確記憶體壓力時會安全釋放.
- 模型自檢僅證明 `Engine.initialize()` 能在目前裝置與內建執行階段成功; 它不評估輸出品質, 裝置或執行階段變更後可重新檢查.
- Provider 宣告的內容上限為 256 KiB, 輸出上限為 64 KiB, 請求和模型也可施加更低上限.
- 回應 schema 必須是 JSON 物件且不超過 64 KiB. 可用關鍵字以目前內建 LiteRT-LM/LLGuidance 執行階段為準; 外掛會嚴格解析並驗證完整輸出, 因此應為整個 JSON 值預留足夠的 `maxTokens`.
- `maxTokens` 接受 1 至 2,147,483,647 的整數. 省略時會將輸出 token 數交由模型或引擎預設值決定, 外掛的 64 KiB 輸出安全上限仍然生效. `temperature` 必須為非負有限數, `topK` 必須為正整數, `topP` 必須為 0 至 1 的有限數. 三項取樣參數全部省略時保留模型或引擎預設值; 部分覆寫時, 未設定項使用 LiteRT-LM 基準 `topK: 1`, `topP: 0.95`, `temperature: 1`.
- 串流輸出使用有限 credit 和有界 chunk, 防止無限制緩衝或無背壓回呼.
- Usage token 數直接來自 LiteRT-LM Conversation 的 KV cache 與 decode 計數, 不使用字元數估算. `durationMillis` 只測量外掛生成呼叫, 不包含宿主探索, 綁定, 模型列舉和分派時間.
- 持久 `ai.session` 只允許一個活動輪次, 正常完成後保留原生 Conversation; 取消, 逾時, 生成失敗或顯式關閉後必須重新建立工作階段.
- 取消, 工作階段關閉和逾時會停止結果發布, 並透過唯一終態結束請求.

******

### 未宣告的能力

******

- 不宣告 reasoning 或 tools 能力.
- 不接受 tool 角色訊息, tool schema, tool call 或 tool result.
- 不提供連網模型探索, 任意 URL 下載, 雲端推論或 credential 流程; 僅能下載內建目錄中固定版本的推薦模型.
- 不宣告 NPU 推論可用: profile 可發現但以 `npu-runtime-not-packaged` 標記為 `unavailable`. GPU 僅在 `libOpenCL.so` 可載入時宣告, 且 `.litertlm` 副檔名本身仍不保證模型初始化成功.

******

### 路線圖

******

路線圖按可交付的使用者功能組織, 每項均可單獨勾選與驗收

- [檢視 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.1.0

###### 2026/08/24

* `新增` 外掛品牌與執行階段識別統一為 3-Stone AI, 同步應用程式名稱, 套件名稱, 元件名稱, 探索識別, 建置產物及文件
* `新增` 跨處理程序整合統一採用中性 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` 及 `IAiProvider`/`IAiSession`/`IAiCallback` 識別, 不保留被取代識別的別名
* `新增` 適配 AutoJs6 `ai.ask`/`ai.chat`/`ai.stream` 的 `plugin: true` 簡寫選擇器及 `ai.models` 模型列舉
* `新增` 透過 AI Provider 協定 1.1 將 `temperature`, `topK`, `topP` 與 `maxTokens` 傳遞至 LiteRT-LM 取樣及輸出 token 控制
* `新增` 透過 AutoJs6 `ai.chat().usage` 與串流 usage 事件回傳 LiteRT-LM 精確的輸入, 輸出及總 token 數, 以及外掛實測生成耗時
* `新增` AI Provider 協定 1.2 持久工作階段及 AutoJs6 `ai.session` 多輪 Conversation 重複使用, 後續輪次無需重傳既有歷史
* `新增` 透過 AutoJs6 `structuredJson` 與 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼, 支援單次呼叫, 串流輸出及持久工作階段, 並嚴格驗證完整 JSON
* `新增` 透過協定 1.3 與 AutoJs6 生成選項提供明確 `cpu`, `gpu` 與 `npu` backend profile, 包含裝置相容性報告, 模型/profile 快取隔離及不可用 profile 禁止回退; GPU 僅在 OpenCL 載入探測成功後宣告, NPU 因未封裝 EAP runtime 而維持不可用
* `新增` 將固定版本的 LiteRT Community 推薦模型直接下載到使用者選擇的 SAF 位置, 支援進度, 精確取消, 殘缺檔案清理, LiteRT-LM 檔頭與精確大小/SHA-256 驗證, 以及下載後直接匯入
* `新增` 新增可由啟動器開啟的會話工作區, 支援串流 Markdown, 持久會話記錄, 編輯舊訊息時的分支取代風險提示, 多結果搜尋及軟鍵盤適配輸入
* `新增` 新增主題色, 深色模式, 應用程式語言, 應用程式與開發者資訊及版本記錄等應用程式設定, 可跟隨 AutoJs6 的選項預設均設為跟隨 AutoJs6
* `新增` 新增字型大小, Enter 鍵行為, 無限制或自訂 output token, 以及模型預設或自訂 `temperature`, `topK`, `topP` 等會話設定
* `新增` 支援在串流輸出中渲染內聯 `$\text{...}$` 內容, 並適配常用數學命令, 上標與下標樣式
* `新增` 新增由外掛程式管理的 Android Keystore 憑證儲存庫, 採用 AES-256-GCM, 綁定 profile 的驗證密文, 跨程序原子私有檔案, 僅查詢 configured 狀態及即時清除明文
* `新增` 新增嚴格且不含敏感資料的線上設定檔儲存庫, 僅接受 HTTPS OpenAI Compatible 端點, 使用 canonical UUID 與跨程序原子中繼資料, provider 或 origin 變更時必須明確取代或清除憑證
* `修復` 移除外掛說明可執行範例預設設定的 256 token 與 4 KiB 輸出限制: 省略 `maxTokens` 時改用模型或引擎預設值, raw Binder 範例使用外掛完整的 64 KiB 輸出額度
* `修復` 修復 10 種本地化外掛說明中的底層 Binder 範例仍呼叫協定 1.1 的 14 參數 `AiGenerationOptions` 建構方法, 導致其在協定 1.3 API 下回報 Java 建構方法不存在
* `修復` 修復模型管理介面在系統深色模式下仍使用淺色主題文字, 導致本文, 核取方塊及模型清單與深色背景對比不足
* `修復` 確保輸入框位於軟鍵盤上方, 依目前主題色對比度選擇傳送按鈕文字顏色, 並統一搜尋的上一個, 下一個及關閉控制項
* `優化` 更新外掛描述, 使用說明及 10 種語言的 README, 與宿主 `ai.*` 本機外掛路由的正式化保持一致
* `優化` 重寫 ROADMAP 為可逐項勾選的功能路線圖
* `優化` 將應用程式及產生的本地化文件標點統一為 ASCII, 並增加涵蓋封裝文字與產生文字的迴歸測試
* `優化` 引入共用的 `AiBackend`/`AiTarget`/`AiBackendSession` 層, 讓啟動器聊天與 Binder Provider 共用 `LiteRtLocalBackend` 的目錄, 能力, 工作階段建立, 串流輸出及取消路徑
* `優化` 將本機 `local:*` 與線上 `profile:*` 目標合併至 Application 層統一目錄及分派器; V1 模型清單仍僅公開本機模型, HTTPS 執行傳輸完成前線上目標會如實標記為 unavailable

# v1.0.0

###### 2026/08/08

* `新增` AI Provider 協定 V1 裝置端 provider, 外掛 ID 和引擎為 `three-stone-ai`, provider ID 為 `autojs6.three-stone-ai`, 變體為 `default`
* `新增` CPU-only LiteRT-LM 純文字生成, 支援 system, user 和 assistant 歷史及 credit 背壓串流輸出
* `新增` 透過 SAF 匯入 `.litertlm` 到應用程式私人儲存空間, 包含 8 GiB 上限, 空間預留, SHA-256, fsync 和原子啟用
* `新增` 單一作用中工作階段, 有界 I/O, descriptor 配額, 取消, 逾時, 唯一終態及同簽章 AutoJs6 呼叫端核驗
* `新增` 明確不宣告 reasoning, tools, structured JSON, usage, 網路或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 種語言的 README, 更新日誌, Android 介面和外掛說明
* `新增` 模型管理介面可檢視完整模型目錄和私人儲存空間用量, 並在不複製模型檔案的情況下原子切換目前模型
* `優化` 為避免獨立 `:provider` 程序競態, 取代匯入後保留先前以 SHA-256 hash 命名的模型代次, 保留檔案會繼續占用應用程式私人儲存空間
* `優化` 加入應用程式層級單一匯入協調器和 fsync pending journal, 在 Activity 重建時保持匯入, 支援冷啟動復原和 stale 暫存檔案清理, 刪除僅限本次新建而從未發布的 destination, 並保留已發布, current 和歷史 hash 代次
* `相依性` 附加 LiteRT-LM 0.15.0, 用於裝置端 CPU 文字生成

##### 更多版本

* [CHANGELOG-zh-Hant-TW.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-TW.md)

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
ai-provider-api.aar
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
