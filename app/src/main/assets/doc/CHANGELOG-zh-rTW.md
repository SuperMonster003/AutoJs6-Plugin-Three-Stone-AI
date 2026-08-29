******

### 版本歷史

******

# v1.1.0

###### 2026/08/26

* `新增` 外掛品牌與執行階段識別統一為 3-Stone AI, 同步應用程式名稱, 套件名稱, 元件名稱, 探索識別, 建置產物及文件
* `新增` 跨處理程序整合統一採用中性 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` 及 `IAiProvider`/`IAiSession`/`IAiCallback` 識別, 不保留被取代識別的別名
* `新增` 增加受簽章權限保護, 可匯出且無參數的 AI 設定入口, AutoJs6 可直接開啟外掛統一設定且不傳送設定檔或憑證資料
* `新增` 透過 AI Provider V2 分頁目標目錄直接公開 `local:*` 與 `profile:*`, 每個目標獨立宣告 provider/model/locality, 設定與可用狀態, capabilities, limits, controls, HTTPS origins 及精確的 `isDefault` 標記
* `新增` 透過 AI Provider V2 生成請求將 `temperature`, `topK`, `topP` 與 `maxTokens` 傳遞至 LiteRT-LM 取樣及輸出 token 控制
* `新增` 透過 AutoJs6 `ai.chat().usage` 與串流 usage 事件回傳 LiteRT-LM 精確的輸入, 輸出及總 token 數, 以及外掛實測生成耗時
* `新增` AI Provider V2 持久工作階段及 AutoJs6 `ai.session` 多輪 Conversation 重複使用, 後續輪次無需重傳既有歷史
* `新增` 透過 AutoJs6 `structuredJson` 與 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼, 支援單次呼叫, 串流輸出及持久工作階段, 並嚴格驗證完整 JSON
* `新增` 將明確 `cpu`, `gpu` 與 `npu` backend profile 作為 AI Provider V2 可選目標控制, 包含裝置相容性報告, 模型/profile 快取隔離及不可用 profile 禁止回退; GPU 僅在 OpenCL 載入探測成功後宣告, NPU 因未封裝 EAP runtime 而維持不可用
* `新增` 將固定版本的 LiteRT Community 推薦模型直接下載到使用者選擇的 SAF 位置, 支援進度, 精確取消, 殘缺檔案清理, LiteRT-LM 檔頭與精確大小/SHA-256 驗證, 以及下載後直接匯入
* `新增` 新增可由啟動器開啟的會話工作區, 支援串流 Markdown, 持久會話記錄, 編輯舊訊息時的分支取代風險提示, 多結果搜尋及軟鍵盤適配輸入
* `新增` 新增主題色, 深色模式, 應用程式語言, 應用程式與開發者資訊及版本記錄等應用程式設定, 可跟隨 AutoJs6 的選項預設均設為跟隨 AutoJs6
* `新增` 新增字型大小, Enter 鍵行為, 無限制或自訂 output token, 以及模型預設或自訂 `temperature`, `topK`, `topP` 等會話設定
* `新增` 支援在串流輸出中渲染內聯 `$\text{...}$` 內容, 並適配常用數學命令, 上標與下標樣式
* `新增` 新增由外掛程式管理的 Android Keystore 憑證儲存庫, 採用 AES-256-GCM, 綁定 profile 的驗證密文, 跨程序原子私有檔案, 僅查詢 configured 狀態及即時清除明文
* `新增` 新增嚴格且不含敏感資料的線上設定檔儲存庫, 僅接受 HTTPS OpenAI-compatible 端點, 使用 canonical UUID 與跨程序原子中繼資料, provider 或 origin 變更時必須明確取代或清除憑證
* `新增` 新增外掛程式內部 OpenAI-compatible HTTPS 執行 backend, 支援自訂 baseUrl, 憑證及模型名稱, 有界 SSE 與 JSON fallback 串流回應, 精確取消, provider usage, 完成輪次多輪歷史, JSON Schema 請求映射及不含敏感資料的固定錯誤; 已設定的 `profile:*` 目標可透過 AI Provider V2 直接呼叫
* `新增` 新增與宿主目錄對齊的 OpenAI, Anthropic, Gemini, DeepSeek 與 OpenRouter 預設範本; 統一線上執行層重用 OpenAI-compatible 協定, 並分別適配 Anthropic Messages 與 Gemini GenerateContent 的原生驗證, 請求, SSE 終態, usage 與 JSON Schema, 不提供協定間或本機/線上自動 fallback
* `新增` 新增 10 種語言線上服務設定 UI, 支援設定檔新增, 編輯, 刪除, 不回顯的 API Key 取代與清除, 預設目標選擇, 在讀取憑證前強制執行的計量網路開關, 以及可取消且最長 120 秒的明確連線測試; 設定與檔案共用跨處理程序原子文件並動態重新整理 V2 目標目錄
* `新增` 啟動器聊天新增統一本機/雲端目標選擇器: 每個對話持久化一個目標快照, 有訊息的對話切換時預設建議新增對話, 攜帶既有上下文繼續目前對話必須明確確認並記錄變更
* `新增` 對話歷史為每則助手回覆儲存實際 target/provider/model/locality 快照; 重新產生預設精確沿用原回覆目標, 目標身分變更或無法使用時明確失敗, 不會靜默回退到目前對話目標
* `新增` 本機與雲端生成失敗會始終停留在所選邊界: 啟動器聊天會附加有界且不含敏感資訊的失敗原因, 明確說明未發生跨邊界自動回退, 並在保留部分輸出的同時提供明確的手動目標切換入口
* `修復` 移除外掛說明可執行範例預設設定的 256 token 與 4 KiB 輸出限制: 省略 `maxTokens` 時改用模型或引擎預設值, raw Binder 範例使用外掛完整的 64 KiB 輸出額度
* `修復` 將 10 種本地化外掛說明中的底層 Binder 範例更新為最終 AI Provider V2 請求及目標目錄 API
* `修復` 修復模型管理介面在系統深色模式下仍使用淺色主題文字, 導致本文, 核取方塊及模型清單與深色背景對比不足
* `修復` 確保輸入框位於軟鍵盤上方, 依目前主題色對比度選擇傳送按鈕文字顏色, 並統一搜尋的上一個, 下一個及關閉控制項
* `修復` 修正於生成 listener callback 內關閉 session 時 callback quiescence 等待自身而死鎖; 關閉仍會等待其他執行緒中已開始的 callback
* `修復` 修復 Android 將可信的 `/data/user/0` 應用程式資料根規範化為 `/data/data` 時誤拒絕應用程式私人線上設定檔與憑證儲存的問題; 仍會拒絕直接子項符號連結及目錄逸出
* `優化` 更新外掛描述, 使用說明及 10 種語言的 README, 與宿主 `ai.*` 統一目標路由的正式化保持一致
* `優化` 重寫 ROADMAP 為可逐項勾選的功能路線圖
* `優化` 將應用程式及產生的本地化文件標點統一為 ASCII, 並增加涵蓋封裝文字與產生文字的迴歸測試
* `優化` 引入共用的 `AiBackend`/`AiTarget`/`AiBackendSession` 層, 讓啟動器聊天與 Binder Provider 共用 `LiteRtLocalBackend` 的目錄, 能力, 工作階段建立, 串流輸出及取消路徑
* `優化` 將本機 `local:*` 與線上 `profile:*` 目標合併至 Application 層統一目錄及分派器, 透過 AI Provider V2 直接公開兩者, 並由目前目錄動態推導 provider locality, credential mode 與 HTTPS origins, 不公開憑證位元組

# v1.0.0

###### 2026/08/08

* `新增` 裝置端 AI Provider 基礎實作, 外掛 ID 和引擎為 `three-stone-ai`, provider ID 為 `autojs6.three-stone-ai`, 變體為 `default`
* `新增` CPU-only LiteRT-LM 純文字生成, 支援 system, user 和 assistant 歷史及 credit 背壓串流輸出
* `新增` 透過 SAF 匯入 `.litertlm` 到應用程式私人儲存空間, 包含 8 GiB 上限, 空間預留, SHA-256, fsync 和原子啟用
* `新增` 單一作用中工作階段, 有界 I/O, descriptor 配額, 取消, 逾時, 唯一終態及同簽章 AutoJs6 呼叫端核驗
* `新增` 明確不宣告 reasoning, tools, structured JSON, usage, 網路或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 種語言的 README, 更新日誌, Android 介面和外掛說明
* `新增` 模型管理介面可檢視完整模型目錄和私人儲存空間用量, 並在不複製模型檔案的情況下原子切換目前模型
* `優化` 為避免獨立 `:provider` 程序競態, 取代匯入後保留先前以 SHA-256 hash 命名的模型代次, 保留檔案會繼續占用應用程式私人儲存空間
* `優化` 加入應用程式層級單一匯入協調器和 fsync pending journal, 在 Activity 重建時保持匯入, 支援冷啟動復原和 stale 暫存檔案清理, 刪除僅限本次新建而從未發布的 destination, 並保留已發布, current 和歷史 hash 代次
* `相依性` 附加 LiteRT-LM 0.15.0, 用於裝置端 CPU 文字生成
