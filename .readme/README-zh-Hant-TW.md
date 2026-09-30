<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
  </p>

  <p>統一 AI 外掛. LiteRT-LM 推論始終在本機; 線上目標始終由使用者明確選取</p>

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

3-Stone AI 是 AutoJs6 的官方 AI 文字生成外掛. 它可在明確選擇的 CPU 或相容 GPU backend 上執行使用者匯入的 LiteRT-LM 模型, 線上生成則使用使用者設定的 profile. 本機與線上執行位置共用一個 AI Provider V2 目標目錄, 受控串流管線及明確選擇邊界. 本機目標絕不連網或上傳資料; 線上目標僅在使用者明確選擇後執行, 且始終綁定外掛管理的憑證及其宣告的 HTTPS origin.

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
- 在應用程式設定管理內建及自訂線上 profile, Android Keystore 憑證, 預設線上目標, 計量網路存取與明確有界連線測試.
- 將每個啟動器對話綁定到一個本機或線上目標快照; 變更已有訊息的對話時預設建議新增對話, 繼續目前對話必須明確確認並記錄變更.
- 為每則助手回覆儲存實際 target/provider/model/locality 快照; 重新產生預設精確沿用原回覆目標, 目標身分變更或無法使用時明確失敗, 不會靜默回退到目前對話目標.
- 本機與雲端生成失敗會始終停留在所選邊界: 啟動器聊天會附加有界且不含敏感資訊的失敗原因, 明確說明未發生跨邊界自動回退, 並在保留部分輸出的同時提供明確的手動目標切換入口.
- 線上 OpenAI 相容, Anthropic Messages 與 Gemini GenerateContent 目標支援原生工具呼叫, 包含串流參數, 並行呼叫與結果續輪.
- 線上模型透過協商 AI Provider 2.1 接收 JPEG/PNG 圖片及工具結果圖片, 支援依個別模型啟用.
- 從 GitHub 專案公開目錄更新線上預設模型. 自動更新預設開啟, 僅在開啟線上 AI 設定時每 24 小時檢查一次, 也可關閉自動更新或手動重新整理. 更新遵循計量付費網路設定, 離線或更新失敗時繼續使用快取或內建清單, 不變更已儲存設定與自訂模型 ID.
- 依廠商分組瀏覽預設模型, 並擴充 OpenRouter 中的 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型選擇, 保留正確的模型 ID.
- 統一獨立設定的平面分組, 列規格與置中圓角對話框. 語言, 夜間模式, 主題色與啟動器圖示均在確定後生效, 取消不改變已儲存的設定. 主題色預設跟隨 AutoJs6, 提供統一色盤, HEX/RGB 輸入與局部預覽; 中性底色保持穩定, 控制項遵循所選主題. 啟動器預設自適應自動, 升級保留明確儲存的選擇.

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

### 外掛介面

******

主程式透過以下識別發現並呼叫外掛:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V2 (2.0 / 2.1)
required host build: 5276
```

AI Provider V2 透過分頁目錄統一公開 `local:*` 與 `profile:*` 目標. 每個目標分別宣告 provider, model, locality, 設定及可用狀態, 能力, 限制, 控制項與 HTTPS origins. 目錄僅含本機目標時宣告 ON_DEVICE/NONE; 存在線上 profile 時宣告 HYBRID/PLUGIN_MANAGED 及其 HTTPS origins 精確聯集. 本機 backend profile 是可選目標控制項, 不可用 profile 或目標絕不靜默回退.

需要主程式建置版本 5276 或更新版本及 Android 7.0 或更新版本. 建置產物包含 armeabi-v7a, arm64-v8a, x86, x86_64, universal APK. x86 和 armeabi-v7a 裝置請使用對應的獨立 APK, 支援應用程式介面, 線上 AI 和主程式外掛整合. LiteRT-LM 本機推論需要 arm64-v8a 或 x86_64 及安裝套件中的對應原生程式庫. universal APK 僅包含 64 位元原生程式庫, 無法安裝到僅支援 32 位元的裝置.

******

### 主程式整合狀態

******

> 在 AutoJs6 (組建 5276 及以上) 中, `ai.catalog()` 將全部已匯入本機模型與已設定線上 profile 作為統一目標目錄回傳, 包含精確 ID, 提供者, 模型, 本機/線上屬性, 設定與可用狀態, 能力, 控制項, 限制, 來源及本機 backend profile. 向 `ai.ask`, `ai.chat`, `ai.stream` 或 `ai.session` 傳入精確 `target`; 僅傳 `target` 會選擇官方 3-Stone AI 外掛, `plugin: true` 則使用其宣告的預設目標. 本機目標可提供 `cpu`, `gpu` 與不可用的 `npu`, 線上目標沒有本機執行 profile. backend 或目標不可用時會直接失敗且不回退, 本機與線上路由也絕不自動切換. 完成及串流回應公開 target, plugin, profile, reasoning, finish reason, 完整 usage 和提供端實測耗時. 穩定錯誤碼可區分提供者缺失或停用, 目標未知, 未設定, 不可用或能力不符, 以及 backend 不可用. `responseSchema` 會隱式啟用結構化輸出; 僅設定 `structuredJson: true` 時使用預設物件根 schema, 持久工作階段在所有輪次固定同一 target, schema 與可選 backend.

******

### 安全性和隱私

******

外掛為使用者主動發起的推薦模型下載, 使用者設定的線上目標請求, 以及線上 AI 設定中從 GitHub 更新公開預設模型目錄使用 `INTERNET` 權限. 目錄更新不需 API Key, 不呼叫推論服務, 外掛啟用及本機生成不會觸發目錄連網. 外掛不要求廣泛儲存權限. 推薦模型檔案下載使用不可變 HTTPS 版本及固定位元組數與 SHA-256, 僅寫入使用者選擇的 SAF 位置; LiteRT-LM 檔頭, 大小, 摘要, flush 與 fsync 全部通過後才算完成. 匯入仍只讀取系統選擇器授予的 URI, 將驗證副本串流寫入私人 `files/models` 並原子啟用. Provider 服務也會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬, 雙方簽章, 目標中繼資料及宣告來源邊界.

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
- 在 設定 > 線上 AI 中編輯設定並選擇支援圖片輸入的模型. 既有設定預設關閉. 圖片僅傳送至所選設定的服務. LiteRT 與持久 ai.session 仍僅支援文字; Agent 截圖要求 Android 11+, 相容的 AutoJs6 與 AI Agent, observe 工具組及為所選精確模型啟用圖片輸入.
- AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通過真實初始圖片及工具結果圖片測試. 合成圖片測試不代表其他目標也支援圖片或已通過完整 Agent 視覺任務.
- 線上失敗僅透過選用的 `providerCode` 欄位提供固定的 `ONLINE_*` 分類. 未知例外不提供分類; 不包含 URL, 憑證, 服務回應或原始例外文字. 失敗代碼與重試策略維持不變.

******

### 未宣告的能力

******

- 不宣告 reasoning 輸出能力. 本機 LiteRT-LM 目標仍不宣告 tools 能力.
- 原生工具最多 16 輪, 同時最多 32 個呼叫. 結果必須完整匹配待處理批次; 上下文/輸出限制, 取消與原始截止時間仍生效. 原生工具暫不與持久 ai.session 輪次組合, 初始歷史不接受 tool 角色.
- 不支援從任意 URL 下載模型檔案. 線上預設模型可從專案公開目錄重新整理, 但不會據此建立設定或查詢帳戶權限. 啟動器聊天與 AI Provider V2 僅公開已匯入本機模型及使用者明確設定的線上 profile, 本機模型下載仍限於內建目錄中固定版本的推薦模型.
- 不宣告 NPU 推論可用: profile 可發現但以 `npu-runtime-not-packaged` 標記為 `unavailable`. GPU 僅在 `libOpenCL.so` 可載入時宣告, 且 `.litertlm` 副檔名本身仍不保證模型初始化成功.

******

### 路線圖

******

路線圖按可交付的使用者功能組織, 每項均可單獨勾選與驗收

- [檢視 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.3.0

###### 2026/09/30

* `新增` 統一獨立設定的平面分組, 列規格與置中圓角對話框. 語言, 夜間模式, 主題色與啟動器圖示均在確定後生效, 取消不改變已儲存的設定. 主題色預設跟隨 AutoJs6, 提供統一色盤, HEX/RGB 輸入與局部預覽; 中性底色保持穩定, 控制項遵循所選主題. 啟動器預設自適應自動, 升級保留明確儲存的選擇.
* `修復` 經 OpenAI 相容閘道呼叫 Claude 或 Gemini 等模型時, 無參數工具的原生呼叫會以空字串而非 "{}" 作為 arguments 串流輸出, 工作階段把它判為無效回應並以 ONLINE_INVALID_RESPONSE 結束 (3-Stove Agent 在 AIGoCode 閘道上的 Claude Code Ex 首個工具輪與 Gemini Ex 的一輪均因此失敗); 現在空白 arguments 按空物件處理, 回放給模型的助理訊息也使用正規化後的形式
* `優化` 關於頁面保留圖示圓角描邊容器, 透明內部透出與容器外側一致的頁面底色; 啟動器圖示選項從頂部顯示並採用較小的說明文字.

# v1.2.1

###### 2026/09/29

* `提示` 32 位元相容性修改為尚未發布的候選版本. 已執行的檢查及未涵蓋的裝置見 docs/dev/32-bit-compatibility-2026-09-29.md.
* `修復` 宿主傳來的工具結果圖片以宿主私有快取中的普通檔案描述符交付時, Provider 經 /proc/self/fd 重新開啟會因無權遍歷宿主目錄而失敗 (EACCES), 整輪工具續輪以 PROTOCOL_VIOLATION 中止; 現在對普通檔案回退為複製描述符直接讀取 (普通檔案不會阻塞讀取), 管道仍使用私有的非阻塞重開描述符. 真機 (Sony XQ-DQ72, AutoJs6 5298, 3-Stove Agent 1.3.0) 上 screen_capture 圖片經原生工具結果送入 Codex / Gemini 模型時曾必現此失敗
* `修復` 原生工具呼叫的每一輪工具結果被接受後, 該次生成的逾時重新計時: 此前整個工具輪 (包括等待工具結果的時間) 共用首個請求的逾時, 超過它的多輪任務一律以 TIMEOUT 結束; 現在只有在未提交結果時才按原逾時到期. 與 AutoJs6 宿主和 3-Stove Agent 的同步改動配合
* `修復` 新增 x86 和 armeabi-v7a 安裝套件以支援線上 AI 及主程式整合; 根據處理程序架構和已安裝的原生程式庫判斷本機推論能力, 並在模型管理頁說明無法使用的原因
* `優化` 統一 Three 系列啟動器圖示為淺色圖案配深色背景, 外掛中心與應用程式內圖案隨應用主題切換並保持透明背景, 避免部分裝置出現啟動器背景套環

# v1.2.0

###### 2026/09/26

* `提示` 開發候選版本, 尚未發布. 線上工具呼叫經 AI Provider V2 提供; Agent 整合需要 build 5297+ 的宿主原生工具代理及支援此能力的 Agent 版本. 外掛向宿主傳回呼叫, 自身不執行裝置操作.
* `提示` 在 設定 > 線上 AI 中編輯設定並選擇支援圖片輸入的模型. 既有設定預設關閉. 圖片僅傳送至所選設定的服務. LiteRT 與持久 ai.session 仍僅支援文字; Agent 截圖要求 Android 11+, 相容的 AutoJs6 與 AI Agent, observe 工具組及為所選精確模型啟用圖片輸入.
* `提示` AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通過真實初始圖片及工具結果圖片測試. 合成圖片測試不代表其他目標也支援圖片或已通過完整 Agent 視覺任務.
* `新增` 線上 OpenAI 相容, Anthropic Messages 與 Gemini GenerateContent 目標支援原生工具呼叫, 包含串流參數, 並行呼叫與結果續輪
* `新增` 線上模型透過協商 AI Provider 2.1 接收 JPEG/PNG 圖片及工具結果圖片, 支援依個別模型啟用
* `新增` 新增線上預設模型自動更新與手動重新整理, 支援本機快取及離線備援清單, 保留已儲存設定與自訂模型 ID
* `新增` 依廠商分組瀏覽預設模型, 並擴充 OpenRouter 中的 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型選擇, 保留正確的模型 ID
* `修復` 描述符讀取在取消或逾時後釋放工作執行緒, 並保留可靠管道的產生端錯誤
* `修復` 透過 AI Provider 回呼保留固定的線上失敗分類, 不揭露請求或回應內容, 不新增自動重試
* `修復` 修復 IntelliJ IDEA F10 執行時 No APK found 錯誤, 按建置變體讀取 AGP 實際 APK 目錄, 保留 16 KB 對齊檢查
* `優化` 依據官方目錄更新線上預設模型, 包含 Claude Fable 5.1 等現行模型並移除已停用的模型 ID, 保留既有設定與自訂模型

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/docs/16kb.md)
