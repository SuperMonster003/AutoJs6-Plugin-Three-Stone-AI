******

### 版本歷史

******

# v1.1.0

###### 2026/08/24

* `新增` 插件品牌與運行時標識統一為 3-Stone AI, 同步應用名, 包名, 組件名, 發現標識, 構建產物及文檔
* `新增` 跨進程集成統一採用中性 `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` 及 `IAiProvider`/`IAiSession`/`IAiCallback` 身份, 不保留被取代身份的別名
* `新增` 適配 AutoJs6 `ai.ask`/`ai.chat`/`ai.stream` 嘅 `plugin: true` 簡寫選擇器及 `ai.models` 模型枚舉
* `新增` 經 AI Provider 協議 1.1 將 `temperature`, `topK`, `topP` 同 `maxTokens` 傳遞至 LiteRT-LM 採樣及輸出 token 控制
* `新增` 透過 AutoJs6 `ai.chat().usage` 同串流 usage 事件回傳 LiteRT-LM 精確嘅輸入, 輸出及總 token 數, 以及插件實測生成耗時
* `新增` AI Provider 協議 1.2 持久會話及 AutoJs6 `ai.session` 多輪 Conversation 重用, 後續輪次無需重傳既有歷史
* `新增` 通過 AutoJs6 `structuredJson` 同 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼, 支援單次調用, 串流輸出同持久會話, 並嚴格驗證完整 JSON
* `新增` 通過協議 1.3 同 AutoJs6 生成選項提供明確 `cpu`, `gpu` 同 `npu` backend profile, 包含裝置兼容性報告, 模型/profile 快取隔離及不可用 profile 禁止回退; GPU 只會在 OpenCL 載入探測成功後聲明, NPU 因未封裝 EAP runtime 而維持不可用
* `新增` 將固定版本嘅 LiteRT Community 建議模型直接下載到用戶選擇嘅 SAF 位置, 支援進度, 精確取消, 殘缺檔案清理, LiteRT-LM 檔案頭同精確大小/SHA-256 驗證, 以及下載後直接匯入
* `新增` 新增可由啟動器開啟嘅會話工作區, 支援串流 Markdown, 持久會話記錄, 編輯舊訊息時嘅分支取代風險提示, 多結果搜尋及軟鍵盤適配輸入
* `新增` 新增主題色, 深色模式, 應用語言, 應用與開發者資訊及版本記錄等應用設定, 可跟隨 AutoJs6 嘅選項預設均設為跟隨 AutoJs6
* `新增` 新增字體大小, Enter 鍵行為, 無限制或自訂 output token, 以及模型預設或自訂 `temperature`, `topK`, `topP` 等會話設定
* `新增` 支援喺串流輸出中渲染內聯 `$\text{...}$` 內容, 並適配常用數學命令, 上標及下標樣式
* `新增` 新增由插件管理嘅 Android Keystore 憑據儲存庫, 使用 AES-256-GCM, 綁定 profile 嘅認證密文, 跨進程原子私人檔案, 僅查詢 configured 狀態及即時清除明文
* `新增` 新增嚴格且不含敏感資料嘅網上設定檔案庫, 只接受 HTTPS OpenAI Compatible 端點, 使用 canonical UUID 及跨進程原子元資料, provider 或 origin 變更時必須明確取代或清除憑據
* `新增` 新增插件內部 OpenAI Compatible HTTPS 執行 backend, 支援自訂 baseUrl, 憑據及模型名稱, 有界 SSE 同 JSON fallback 串流回應, 精確取消, provider usage, 完成輪次多輪歷史, JSON Schema 請求映射及不含敏感資料嘅固定錯誤; AI Provider V1 宿主路由仍只公開本地目標
* `新增` 新增同宿主目錄對齊嘅 OpenAI, Anthropic, Gemini, DeepSeek 同 OpenRouter 預設模板; 統一在線執行層重用 OpenAI-compatible 協議, 並分別適配 Anthropic Messages 同 Gemini GenerateContent 嘅原生認證, 請求, SSE 終態, usage 同 JSON Schema, 唔提供協議之間或本地/在線自動 fallback
* `修復` 移除插件說明可執行範例預設設定嘅 256 token 同 4 KiB 輸出限制: 省略 `maxTokens` 時改用模型或引擎預設值, raw Binder 範例使用插件完整嘅 64 KiB 輸出額度
* `修復` 修復 10 種本地化插件說明中的底層 Binder 範例仍呼叫協議 1.1 的 14 參數 `AiGenerationOptions` 建構方法, 導致喺協議 1.3 API 下報告 Java 建構方法不存在
* `修復` 修復模型管理介面在系統深色模式下仍使用淺色主題文字, 導致正文, 核取方塊及模型清單與深色背景對比不足
* `修復` 確保輸入框位於軟鍵盤上方, 按目前主題色對比度選擇傳送按鈕文字顏色, 並統一搜尋嘅上一個, 下一個及關閉控制項
* `修復` 修復喺生成 listener callback 內關閉 session 時 callback quiescence 等待自身而死鎖; 關閉仍會等待其他執行緒中已開始嘅 callback
* `優化` 更新插件描述, 使用說明及 10 種語言嘅 README, 與宿主 `ai.*` 本機插件路由嘅正式化保持一致
* `優化` 重寫 ROADMAP 為可逐項勾選嘅功能路線圖
* `優化` 將應用及生成嘅本地化文檔標點統一為 ASCII, 並增加覆蓋打包文字及生成文字嘅回歸測試
* `優化` 引入共用嘅 `AiBackend`/`AiTarget`/`AiBackendSession` 層, 令啟動器聊天同 Binder Provider 共用 `LiteRtLocalBackend` 嘅目錄, 能力, 會話建立, 串流輸出同取消路徑
* `優化` 將本機 `local:*` 同網上 `profile:*` 目標合併到 Application 層統一目錄及分派器; V1 模型列表仍只公開本機模型, HTTPS 執行傳輸完成前網上目標會如實標記為 unavailable

# v1.0.0

###### 2026/08/08

* `新增` AI Provider 協議 V1 裝置端 provider, 插件 ID 和引擎為 `three-stone-ai`, provider ID 為 `autojs6.three-stone-ai`, 變體為 `default`
* `新增` CPU-only LiteRT-LM 純文字生成, 支援 system, user 和 assistant 歷史及 credit 背壓串流輸出
* `新增` 透過 SAF 匯入 `.litertlm` 到應用程式私人儲存空間, 包含 8 GiB 上限, 空間預留, SHA-256, fsync 和原子啟用
* `新增` 單活動工作階段, 有界 I/O, descriptor 配額, 取消, 逾時, 唯一終態及同簽名 AutoJs6 呼叫方核驗
* `新增` 明確不宣告 reasoning, tools, structured JSON, usage, 網絡或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 種語言的 README, 更新日誌, Android 介面和插件說明
* `新增` 模型管理介面可查看完整模型目錄和私人儲存空間佔用, 並在不複製模型檔案的情況下原子切換目前模型
* `優化` 為避免獨立 `:provider` 進程競態, 替換匯入後保留先前以 SHA-256 hash 命名的模型代次, 保留檔案會繼續佔用應用程式私人儲存空間
* `優化` 加入應用程式級單匯入協調器和 fsync pending journal, 在 Activity 重建時保持匯入, 支援冷啟動復原和 stale 暫存檔案清理, 刪除僅限本次新建而從未發佈的 destination, 並保留已發佈, current 和歷史 hash 代次
* `依賴` 附加 LiteRT-LM 0.15.0, 用於裝置端 CPU 文字生成
