<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>裝置端 AI 插件. LiteRT-LM 推理始終喺本地; 建議模型下載只由用戶明確發起</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 語言

******

目前 README.md 支援以下語言:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- 繁體中文 (香港) [zh-Hant-HK] # 目前
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### 簡介

******

On-Device AI (裝置端 AI) 係 AutoJs6 嘅官方裝置端 AI 文本生成插件. 佢喺顯式選擇嘅 CPU 或相容 GPU backend 上運行用戶導入嘅 LiteRT-LM 模型, 接收純文本消息歷史, 並通過受控串流會話返回純文本或受 schema 約束嘅 JSON 文本. 全部推理喺本地完成, 唔聯網亦唔上傳任何數據; 只有用戶明確下載建議模型時先會連網.

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

### 插件介面

******

主程式透過以下標識發現並呼叫插件:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

插件宣告 ON_DEVICE 執行位置和 NONE credential 模式. 它宣告 `streaming`, `usage`, `persistent-session` 同 `structured-json` 能力, 接受 `text/plain` 消息輸入同 `application/json` 響應 schema, 並輸出 `text/plain` 或 `application/json` 文本. 協議 1.3 加入顯式 backend profile 同裝置級可用性, 並禁止靜默回退 CPU.

需要主程式構建版本 5276 或更高版本. 發佈產物包含 arm64-v8a, x86_64, universal APK.

******

### 主程式整合狀態

******

> AutoJs6 (構建 5276 及以上) 嘅 `ai.ask`, `ai.chat` 與 `ai.stream` 支持本機插件路由. `ai.session({ plugin: true })` 可建立持久多輪 Conversation, 後續 `ask`, `chat` 同 `stream` 調用只發送新嘅用戶提示詞. `ai.ask(messages, { plugin: true })` 會按順序保留純文字 `system`, `user` 同 `assistant` 消息, 而最後一條消息必須係 `user`. `ai.chat` 會喺 `usage` 回傳精確 token 數, 並喺 `usage.raw.durationMillis` 回傳實測生成耗時; `ai.stream` 會喺完成前發送同一份累計 usage. 傳入 `plugin: true` 即選擇本插件, 單模型場景可省略模型 ID; `ai.models({ plugin: true })` 可枚舉已導入模型同 `backendProfiles`. 生成選項接受 `backend: 'cpu' | 'gpu' | 'npu'`; 不可用 profile 會明確失敗, 絕不回退 CPU. 插件未安裝, 未喺插件中心啟用或未導入模型時, 腳本會收到明確嘅錯誤提示. 亦可通過 `plugin: { component, providerId, modelId }` 顯式固定組件. `responseSchema` 會隱式啟用結構化輸出; 只設定 `structuredJson: true` 時使用預設嘅對象根 schema. `ai.ask` 同 `ai.chat().text` 仍然返回 JSON 文本, 串流 delta 係未完整嘅 JSON 片段, 持久會話就會喺所有輪次固定使用同一 schema 同 backend.

******

### 安全性和私隱

******

插件只為用戶主動發起嘅建議模型下載要求 `INTERNET` 權限, 唔要求廣泛儲存權限. 目錄下載使用不可變 HTTPS 版本及固定字節數同 SHA-256, 只寫入用戶選擇嘅 SAF 位置; LiteRT-LM 檔案頭, 大小, 摘要, flush 同 fsync 全部通過後先算完成. 匯入仍只讀取系統選擇器授予嘅 URI, 將驗證副本串流寫入私人 `files/models` 並原子啟用. Provider 服務亦會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬及雙方簽名.

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

******

### 未宣告的能力

******

- 不宣告 reasoning 或 tools 能力.
- 不接受 tool 角色訊息, tool schema, tool call 或 tool result.
- 不提供連網模型發現, 任意 URL 下載, 雲端推理或 credential 流程; 只可下載內置目錄中固定版本嘅建議模型.
- 不宣告 NPU 推理可用: profile 可發現但以 `npu-runtime-not-packaged` 標記為 `unavailable`. GPU 只喺 `libOpenCL.so` 可載入時宣告, 而 `.litertlm` 副檔名本身仍不保證模型初始化成功.

******

### 路線圖

******

路線圖按可交付嘅用戶功能組織, 每項均可單獨勾選同驗收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.1.0

###### 2026/08/23

* `新增` 插件品牌與運行時標識統一為 On-Device AI (裝置端 AI), 同步應用名, 包名, 組件名, 發現標識, 協議 API, 構建產物及文檔
* `新增` 適配 AutoJs6 `ai.ask`/`ai.chat`/`ai.stream` 嘅 `plugin: true` 簡寫選擇器及 `ai.models` 模型枚舉
* `新增` 經 On-Device AI 協議 1.1 將 `temperature`, `topK`, `topP` 同 `maxTokens` 傳遞至 LiteRT-LM 採樣及輸出 token 控制
* `新增` 透過 AutoJs6 `ai.chat().usage` 同串流 usage 事件回傳 LiteRT-LM 精確嘅輸入, 輸出及總 token 數, 以及插件實測生成耗時
* `新增` On-Device AI 協議 1.2 持久會話及 AutoJs6 `ai.session` 多輪 Conversation 重用, 後續輪次無需重傳既有歷史
* `新增` 通過 AutoJs6 `structuredJson` 同 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼, 支援單次調用, 串流輸出同持久會話, 並嚴格驗證完整 JSON
* `新增` 通過協議 1.3 同 AutoJs6 生成選項提供明確 `cpu`, `gpu` 同 `npu` backend profile, 包含裝置兼容性報告, 模型/profile 快取隔離及不可用 profile 禁止回退; GPU 只會在 OpenCL 載入探測成功後聲明, NPU 因未封裝 EAP runtime 而維持不可用
* `新增` 將固定版本嘅 LiteRT Community 建議模型直接下載到用戶選擇嘅 SAF 位置, 支援進度, 精確取消, 殘缺檔案清理, LiteRT-LM 檔案頭同精確大小/SHA-256 驗證, 以及下載後直接匯入
* `新增` 新增可由啟動器開啟嘅會話工作區, 支援串流 Markdown, 持久會話記錄, 編輯舊訊息時嘅分支取代風險提示, 多結果搜尋及軟鍵盤適配輸入
* `新增` 新增主題色, 深色模式, 應用語言, 應用與開發者資訊及版本記錄等應用設定, 可跟隨 AutoJs6 嘅選項預設均設為跟隨 AutoJs6
* `新增` 新增字體大小, Enter 鍵行為, 無限制或自訂 output token, 以及模型預設或自訂 `temperature`, `topK`, `topP` 等會話設定
* `新增` 支援喺串流輸出中渲染內聯 `$\text{...}$` 內容, 並適配常用數學命令, 上標及下標樣式
* `修復` 移除插件說明可執行範例預設設定嘅 256 token 同 4 KiB 輸出限制: 省略 `maxTokens` 時改用模型或引擎預設值, raw Binder 範例使用插件完整嘅 64 KiB 輸出額度
* `修復` 修復 10 種本地化插件說明中的底層 Binder 範例仍呼叫協議 1.1 的 14 參數 `AiGenerationOptions` 建構方法, 導致喺協議 1.3 API 下報告 Java 建構方法不存在
* `修復` 修復模型管理介面在系統深色模式下仍使用淺色主題文字, 導致正文, 核取方塊及模型清單與深色背景對比不足
* `修復` 確保輸入框位於軟鍵盤上方, 按目前主題色對比度選擇傳送按鈕文字顏色, 並統一搜尋嘅上一個, 下一個及關閉控制項
* `優化` 更新插件描述, 使用說明及 10 種語言嘅 README, 與宿主 `ai.*` 本機插件路由嘅正式化保持一致
* `優化` 重寫 ROADMAP 為可逐項勾選嘅功能路線圖
* `優化` 將應用及生成嘅本地化文檔標點統一為 ASCII, 並增加覆蓋打包文字及生成文字嘅回歸測試

# v1.0.0

###### 2026/08/08

* `新增` On-Device AI 協議 V1 裝置端 provider, 插件 ID 和引擎為 `on-device-ai`, provider ID 為 `autojs6.on-device-ai`, 變體為 `default`
* `新增` CPU-only LiteRT-LM 純文字生成, 支援 system, user 和 assistant 歷史及 credit 背壓串流輸出
* `新增` 透過 SAF 匯入 `.litertlm` 到應用程式私人儲存空間, 包含 8 GiB 上限, 空間預留, SHA-256, fsync 和原子啟用
* `新增` 單活動工作階段, 有界 I/O, descriptor 配額, 取消, 逾時, 唯一終態及同簽名 AutoJs6 呼叫方核驗
* `新增` 明確不宣告 reasoning, tools, structured JSON, usage, 網絡或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 種語言的 README, 更新日誌, Android 介面和插件說明
* `新增` 模型管理介面可查看完整模型目錄和私人儲存空間佔用, 並在不複製模型檔案的情況下原子切換目前模型
* `優化` 為避免獨立 `:provider` 進程競態, 替換匯入後保留先前以 SHA-256 hash 命名的模型代次, 保留檔案會繼續佔用應用程式私人儲存空間
* `優化` 加入應用程式級單匯入協調器和 fsync pending journal, 在 Activity 重建時保持匯入, 支援冷啟動復原和 stale 暫存檔案清理, 刪除僅限本次新建而從未發佈的 destination, 並保留已發佈, current 和歷史 hash 代次
* `依賴` 附加 LiteRT-LM 0.15.0, 用於裝置端 CPU 文字生成

##### 更多版本

* [CHANGELOG-zh-Hant-HK.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-zh-Hant-HK.md)

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
on-device-ai-api.aar
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
