<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>統一 AI 插件. LiteRT-LM 推理始終喺本機; 網上目標始終由用戶明確選擇</p>

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
- 繁體中文 (香港) [zh-Hant-HK] # 目前
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
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

3-Stone AI 係 AutoJs6 嘅官方 AI 文本生成插件. 佢可以喺顯式選擇嘅 CPU 或相容 GPU backend 上運行用戶導入嘅 LiteRT-LM 模型, 網上生成就使用用戶設定嘅 profile. 本機同網上執行位置共用一個 AI Provider V2 目標目錄, 受控串流管線同明確選擇邊界. 本機目標絕不聯網或上傳數據; 網上目標只會喺用戶明確選擇後執行, 並始終綁定插件管理嘅憑證同聲明嘅 HTTPS origin.

******

### 功能

******

- 透過 Android 系統檔案選擇器匯入 `.litertlm` 模型套件, 並將驗證後的副本儲存到應用程式私人儲存空間.
- 將固定版本且毋須登入嘅 LiteRT Community 建議模型直接下載到用戶選擇嘅 SAF 位置, 支援進度, 取消, 殘缺檔案清理及精確大小同 SHA-256 驗證.
- 開啟系統檔案選擇器前預檢私人儲存空間, 顯示目前匯入預算同私人副本預計佔用, 並喺複製前再次檢查所選檔案.
- 使用純文字 system, user 和 assistant 歷史建立本地生成請求.
- 將 AutoJs6 `ai.ask`, `ai.chat` 和 `ai.stream` 嘅 `temperature`, `topK`, `topP` 同 `maxTokens` 傳遞到 LiteRT-LM.
- 通過 AutoJs6 `structuredJson` 同 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼; 完整結果仍然係可供 `JSON.parse` 解析嘅 JSON 文本.
- 透過 AutoJs6 `ai.chat().usage` 同串流 usage 事件回傳 LiteRT-LM 精確嘅輸入, 輸出及總 token 數, 以及插件端生成耗時.
- 透過 AutoJs6 `ai.session` 喺同一個 LiteRT-LM 原生 Conversation 保留多輪上下文, 後續輪次只發送新嘅用戶提示詞.
- 按模型 SHA-256 重用已初始化 Engine, 消除同一模型連續請求嘅重複冷啟動.
- 可選擇將每個匯入模型初始化一次, 持久保存其"可用/不相容"狀態, 並可喺模型管理介面重新檢查.
- 透過 credit 背壓按序傳送文字 chunk, 並只發佈一個完成, 錯誤或取消終態.
- 列出, 選擇同重新命名已匯入模型, 刪除未選取模型, 並喺管理介面一鍵回收未引用模型檔案.
- 透過 AutoJs6 顯式選擇 `cpu`, `gpu` 或 `npu` backend; CPU 為預設值, GPU 只會喺 OpenCL 載入探測通過後開放, NPU 因未封裝 EAP runtime 而明確回報不可用.
- 喺應用設定管理內置及自訂網上 profile, Android Keystore 憑證, 預設網上目標, 計量網絡存取同顯式有界連線測試.
- 將每個啟動器對話綁定到一個本機或網上目標快照; 更改已有訊息嘅對話時預設建議新增對話, 繼續目前對話必須明確確認並記錄變更.
- 為每則助手回覆儲存實際 target/provider/model/locality 快照; 重新產生預設精確沿用原回覆目標, 目標身份更改或不可用時明確失敗, 唔會靜默回退到目前對話目標.
- 本機同雲端生成失敗會一直留喺所選邊界: 啟動器聊天會附加有界且不含敏感資料嘅失敗原因, 明確說明未發生跨邊界自動回退, 並喺保留部分輸出嘅同時提供顯式手動目標切換入口.
- 線上 OpenAI 相容, Anthropic Messages 與 Gemini GenerateContent 目標支援原生工具呼叫, 包含串流參數, 並行呼叫與結果續輪.
- 線上模型透過協商 AI Provider 2.1 接收 JPEG/PNG 圖片及工具結果圖片, 支援按個別模型啟用.
- 從 GitHub 專案公開目錄更新網上預設模型. 自動更新預設開啟, 只會喺開啟網上 AI 設定時每 24 小時檢查一次, 亦可以關閉自動更新或手動重新整理. 更新會遵守計量網絡設定, 離線或更新失敗時繼續使用快取或內置清單, 唔會改動已儲存設定同自訂模型 ID.
- 按廠商分組瀏覽預設模型, 並擴充 OpenRouter 中嘅 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型選擇, 保留準確嘅模型 ID.

******

### 模型和資料格式

******

支援的模型與輸入格式:

```text
model package: .litertlm
input: text/plain history + application/json schema; negotiated 2.1: image/jpeg and image/png descriptors
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### 插件介面

******

主程式透過以下標識發現並呼叫插件:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V2 (2.0 / 2.1)
required host build: 5276
```

AI Provider V2 透過分頁目錄統一公開 `local:*` 同 `profile:*` 目標. 每個目標分別聲明 provider, model, locality, 設定及可用狀態, 能力, 限制, 控件同 HTTPS origins. 目錄只有本機目標時聲明 ON_DEVICE/NONE; 存在網上 profile 時聲明 HYBRID/PLUGIN_MANAGED 同其 HTTPS origins 精確並集. 本機 backend profile 係可選目標控件, 不可用 profile 或目標絕不靜默回退.

需要主程式構建版本 5276 或更高版本及 Android 7.0 或更高版本. 構建產物包含 armeabi-v7a, arm64-v8a, x86, x86_64, universal APK. x86 和 armeabi-v7a 裝置請使用對應的獨立 APK, 支援應用程式介面, 線上 AI 和主程式插件整合. LiteRT-LM 本地推理需要 arm64-v8a 或 x86_64 及安裝包中的對應原生程式庫. universal APK 僅包含 64 位元原生程式庫, 無法安裝到僅支援 32 位元的裝置.

******

### 主程式整合狀態

******

> 喺 AutoJs6 (構建 5276 及以上) 中, `ai.catalog()` 將全部已導入本機模型同已設定在線 profile 作為統一目標目錄返回, 包含精確 ID, 提供方, 模型, 本機/在線屬性, 設定同可用狀態, 能力, 控制項, 限制, 來源及本機 backend profile. 向 `ai.ask`, `ai.chat`, `ai.stream` 或 `ai.session` 傳入精確 `target`; 只傳 `target` 會選擇官方 3-Stone AI 插件, `plugin: true` 就使用其聲明嘅預設目標. 本機目標可提供 `cpu`, `gpu` 同不可用嘅 `npu`, 在線目標冇本機執行 profile. backend 或目標不可用時會直接失敗且唔回退, 本機同在線路由亦絕唔自動切換. 完成同串流回應公開 target, plugin, profile, reasoning, finish reason, 完整 usage 同提供端實測耗時. 穩定錯誤碼可區分提供方缺失或停用, 目標未知, 未設定, 不可用或能力唔匹配, 以及 backend 不可用. `responseSchema` 會隱式啟用結構化輸出; 只設定 `structuredJson: true` 時使用預設物件根 schema, 持久會話喺所有輪次固定同一 target, schema 同可選 backend.

******

### 安全性和私隱

******

插件為用戶主動發起嘅建議模型下載, 用戶設定嘅網上目標請求, 同網上 AI 設定中從 GitHub 更新公開預設模型目錄使用 `INTERNET` 權限. 目錄更新唔需要 API Key, 唔會呼叫推理服務, 插件啟用同本機生成唔會觸發目錄連網. 插件唔要求廣泛儲存權限. 建議模型檔案下載使用不可變 HTTPS 版本及固定字節數同 SHA-256, 只寫入用戶選擇嘅 SAF 位置; LiteRT-LM 檔案頭, 大小, 摘要, flush 同 fsync 全部通過後先算完成. 匯入仍只讀取系統選擇器授予嘅 URI, 將驗證副本串流寫入私人 `files/models` 並原子啟用. Provider 服務亦會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬, 雙方簽名, 目標元數據同聲明來源邊界.

******

### 執行限制

******

- 模型匯入硬上限為 8 GiB, 且匯入後至少保留 256 MiB 可用空間.
- 應用程式進程內一次只運行一個模型下載. Activity 重建會保留進度同取消歸屬; 取消或失敗會刪除新建目標, 唔支援刪除時就截斷清空. 進程被終止仍可能留下外部殘缺檔案, 用戶應手動刪除.
- 應用程式級單匯入協調器使 Activity 重建不會中斷正在進行的匯入. Fsync pending journal 支援冷啟動復原並清理 stale `.incoming`, `.current` 和 `.pending` 暫存檔案. 復原只會刪除本次嘗試新建且從未由 current metadata 發佈的 destination, 已發佈或 current 模型及歷史 hash 代次均會保留.
- 為避免與獨立 `:provider` 進程發生競態, 匯入時唔會自動刪除先前以 SHA-256 hash 命名嘅模型代次. 模型管理介面可刪除未選取嘅 catalog 模型, 並回收唔再由 catalog 引用嘅 hash 命名檔案.
- 同一進程最多有一個活動生成工作階段. 請求描述符會在非同步處理前複製並按協議配額關閉.
- Provider 以模型 SHA-256 同 backend profile 作聯合鍵, 最多快取一個已初始化 Engine. 相同組合嘅連續請求會重用; 任一鍵改變, 閒置 5 分鐘或收到明確記憶體壓力時會安全釋放.
- 模型自檢只證明 `Engine.initialize()` 能喺目前裝置同內置運行環境成功; 佢唔評估輸出質素, 裝置或運行環境變更後可以重新檢查.
- Provider 宣告的內容上限為 256 KiB, 輸出上限為 64 KiB, 請求和模型亦可施加更低上限.
- 響應 schema 必須係 JSON 對象且唔超過 64 KiB. 可用關鍵字以目前內置 LiteRT-LM/LLGuidance 運行時為準; 插件會嚴格解析同驗證完整輸出, 因此應為整個 JSON 值預留足夠嘅 `maxTokens`.
- `maxTokens` 接受 1 至 2,147,483,647 嘅整數. 省略時會將輸出 token 數交畀模型或引擎預設值決定, 插件嘅 64 KiB 輸出安全上限仍然生效. `temperature` 必須係非負有限數, `topK` 必須係正整數, `topP` 必須係 0 至 1 嘅有限數. 三項採樣參數全部省略時保留模型或引擎預設值; 部分覆蓋時, 未設定項使用 LiteRT-LM 基線 `topK: 1`, `topP: 0.95`, `temperature: 1`.
- 串流輸出使用有限 credit 和有界 chunk, 防止無限制緩衝或無背壓回呼.
- Usage token 數直接來自 LiteRT-LM Conversation 嘅 KV cache 同 decode 計數, 唔會用字符數估算. `durationMillis` 只量度插件生成調用, 唔包括宿主發現, 綁定, 模型枚舉同分發時間.
- 持久 `ai.session` 只允許一個活動輪次, 正常完成後保留原生 Conversation; 取消, 逾時, 生成失敗或顯式關閉後必須重新建立會話.
- 取消, 工作階段關閉和逾時會停止結果發佈, 並透過唯一終態結束請求.
- 在 設定 > 線上 AI 中編輯設定並選擇支援圖片輸入的模型. 現有設定預設關閉. 圖片只傳送至所選設定的服務. LiteRT 與持久 ai.session 仍只支援文字; Agent 截圖要求 Android 11+, 相容的 AutoJs6 及 AI Agent, observe 工具組及為所選精確模型啟用圖片輸入.
- AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通過真實初始圖片及工具結果圖片測試. 合成圖片測試不代表其他目標也支援圖片或已通過完整 Agent 視覺任務.
- 在線失敗僅透過可選的 `providerCode` 欄位提供固定的 `ONLINE_*` 分類. 未知異常不提供分類; 不包含 URL, 憑據, 服務回應或原始異常文字. 失敗代碼同重試策略保持不變.

******

### 未宣告的能力

******

- 不宣告 reasoning 輸出能力. 本地 LiteRT-LM 目標仍不宣告 tools 能力.
- 原生工具最多 16 輪, 同時最多 32 個呼叫. 結果必須完整匹配待處理批次; 上下文/輸出限制, 取消與原始截止時間仍生效. 原生工具暫不與持久 ai.session 輪次組合, 初始歷史不接受 tool 角色.
- 唔支援從任意 URL 下載模型檔案. 網上預設模型可以從專案公開目錄重新整理, 但唔會因此建立設定或查詢帳戶權限. 啟動器聊天同 AI Provider V2 只公開已導入本機模型及用戶明確設定嘅網上 profile, 本機模型下載仍限於內置目錄中固定版本嘅建議模型.
- 不宣告 NPU 推理可用: profile 可發現但以 `npu-runtime-not-packaged` 標記為 `unavailable`. GPU 只喺 `libOpenCL.so` 可載入時宣告, 而 `.litertlm` 副檔名本身仍不保證模型初始化成功.

******

### 路線圖

******

路線圖按可交付嘅用戶功能組織, 每項均可單獨勾選同驗收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.2.1

###### 2026/09/29

* `提示` 32 位元相容性修改為尚未發佈的候選版本. 已執行的檢查及未涵蓋的裝置見 docs/dev/32-bit-compatibility-2026-09-29.md.
* `修復` 宿主傳來的工具結果圖片以宿主私有快取中的普通檔案描述符交付時, Provider 經 /proc/self/fd 重新開啟會因無權遍歷宿主目錄而失敗 (EACCES), 整輪工具續輪以 PROTOCOL_VIOLATION 中止; 現在對普通檔案回退為複製描述符直接讀取 (普通檔案不會阻塞讀取), 管道仍使用私有的非阻塞重開描述符. 真機 (Sony XQ-DQ72, AutoJs6 5298, 3-Stove Agent 1.3.0) 上 screen_capture 圖片經原生工具結果送入 Codex / Gemini 模型時曾必現此失敗
* `修復` 原生工具呼叫的每一輪工具結果被接受後, 該次生成的逾時重新計時: 此前整個工具輪 (包括等待工具結果的時間) 共用首個請求的逾時, 超過它的多輪任務一律以 TIMEOUT 結束; 現在只有在未提交結果時才按原逾時到期. 與 AutoJs6 宿主和 3-Stove Agent 的同步改動配合
* `修復` 新增 x86 和 armeabi-v7a 安裝包以支援線上 AI 及主程式整合; 根據處理程序架構和已安裝的原生程式庫判斷本地推理能力, 並在模型管理頁說明不可用原因
* `優化` 統一 Three 系列啟動器圖示為淺色圖案配深色背景, 插件中心與應用程式內圖案隨應用主題切換並保持透明背景, 避免部分裝置出現啟動器背景套環

# v1.2.0

###### 2026/09/26

* `提示` 開發候選版本, 尚未發佈. 線上工具呼叫經 AI Provider V2 提供; Agent 整合需要 build 5297+ 的宿主原生工具代理及支援此能力的 Agent 版本. 插件向宿主傳回呼叫, 自身不執行裝置操作.
* `提示` 在 設定 > 線上 AI 中編輯設定並選擇支援圖片輸入的模型. 現有設定預設關閉. 圖片只傳送至所選設定的服務. LiteRT 與持久 ai.session 仍只支援文字; Agent 截圖要求 Android 11+, 相容的 AutoJs6 及 AI Agent, observe 工具組及為所選精確模型啟用圖片輸入.
* `提示` AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通過真實初始圖片及工具結果圖片測試. 合成圖片測試不代表其他目標也支援圖片或已通過完整 Agent 視覺任務.
* `新增` 線上 OpenAI 相容, Anthropic Messages 與 Gemini GenerateContent 目標支援原生工具呼叫, 包含串流參數, 並行呼叫與結果續輪
* `新增` 線上模型透過協商 AI Provider 2.1 接收 JPEG/PNG 圖片及工具結果圖片, 支援按個別模型啟用
* `新增` 新增網上預設模型自動更新同手動重新整理, 支援本機快取同離線後備清單, 保留已儲存設定同自訂模型 ID
* `新增` 按廠商分組瀏覽預設模型, 並擴充 OpenRouter 中嘅 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型選擇, 保留準確嘅模型 ID
* `修復` 描述符讀取在取消或逾時後釋放工作執行緒, 並保留可靠管道的產生端錯誤
* `修復` 透過 AI Provider 回調保留固定的在線失敗分類, 不暴露請求或回應內容, 不增加自動重試
* `修復` 修復 IntelliJ IDEA F10 執行時 No APK found 錯誤, 按建置變體讀取 AGP 實際 APK 目錄, 保留 16 KB 對齊檢查
* `優化` 根據官方目錄更新線上預設模型, 包含 Claude Fable 5.1 等現行模型並移除已停用的模型 ID, 保留現有設定與自訂模型

# v1.1.4

###### 2026/09/19

* `修復` AGP 9.1 構建時的 SDK XML v4 解析警告及 JVM 單元測試組裝任務誤觸發 APK 原生程式庫對齊檢查的問題 (共用構建外掛 1.8.3)
* `優化` 繼 compileSdk 之後將 targetSdk 提升到 37 (Android 17), 插件行為不受新目標版本影響

##### 更多版本

* [CHANGELOG-zh-Hant-HK.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-HK.md)

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
ai-provider-api.aar
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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/docs/16kb.md)
