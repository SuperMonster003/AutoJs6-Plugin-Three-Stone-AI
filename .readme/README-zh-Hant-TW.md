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

3-Stone AI 是 AutoJs6 的官方 AI 文字生成外掛. 它可在明確選擇的 CPU 或相容 GPU backend 上執行使用者匯入的 LiteRT-LM 模型, 線上生成則使用使用者設定的線上服務. 本機與線上執行位置共用一個 AI Provider V2 目標目錄, 受控串流管線及明確選擇邊界. 本機目標絕不連網或上傳資料; 線上目標僅在使用者明確選擇後執行, 且始終綁定外掛管理的憑證及其宣告的 HTTPS origin.

******

### 功能

******

- 透過 Android 系統檔案選擇器匯入 `.litertlm` 模型套件, 並將驗證後的副本儲存到應用程式私人儲存空間.
- 從內建目錄一步下載固定版本且無需登入的 LiteRT Community 推薦模型: 直接寫入應用程式私有儲存空間, 驗證精確大小與 SHA-256 後自動匯入. 下載中的模型以卡片顯示在 "本機模型" 區段, 支援進度, 暫停, 取消與失敗重試, 中斷後從已下載的部分繼續; 下載確認框會顯示該模型的最低記憶體需求, 本機記憶體不足時提醒.
- 開啟系統檔案選擇器前預檢私人儲存空間, 顯示目前匯入預算與私人副本預計占用, 並在複製前再次檢查所選檔案.
- 使用純文字 system, user 和 assistant 歷史建立本機生成請求.
- 將 AutoJs6 `ai.ask`, `ai.chat` 和 `ai.stream` 的 `temperature`, `topK`, `topP` 與 `maxTokens` 傳遞到 LiteRT-LM.
- 透過 AutoJs6 `structuredJson` 與 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼; 完整結果仍為可供 `JSON.parse` 解析的 JSON 文字.
- 透過 AutoJs6 `ai.chat().usage` 與串流 usage 事件回傳 LiteRT-LM 精確的輸入, 輸出及總 token 數, 以及外掛端生成耗時.
- 透過 AutoJs6 `ai.session` 在同一個 LiteRT-LM 原生 Conversation 保留多輪上下文, 後續輪次只傳送新的使用者提示詞.
- 依模型 SHA-256 重複使用已初始化 Engine, 消除同一模型連續請求的重複冷啟動.
- 可選擇將每個匯入模型初始化一次, 持久保存其"可用/不相容"狀態, 並可在模型管理介面重新檢查.
- 透過 credit 背壓依序傳送文字 chunk, 並只發布一個完成, 錯誤或取消終態.
- 在 "本機模型" 區段列出, 重新命名, 檢查及刪除已匯入模型, 卡片顯示每個模型佔用的空間; 刪除模型時會一併刪除它首次載入時產生的 LiteRT-LM 引擎快取. "清理未參照檔案" 可回收未參照的模型檔案, 遺留的引擎快取與不再使用的附件.
- 透過 AutoJs6 明確選擇 `cpu`, `gpu` 或 `npu` backend; CPU 為預設值, GPU 僅在 OpenCL 載入探測通過後開放, NPU 因未封裝 EAP runtime 而明確回報不可用.
- 在 設定 > 模型與服務 中管理內建及自訂線上服務, Android Keystore 憑證, 唯一的預設目標 (線上模型或本機模型皆可), 計量付費網路存取與明確有界連線測試. 服務在全螢幕的 "服務詳細資料" 頁編輯: 每個服務可新增多個模型, 拖曳排序並設定首選模型, 點選勾號才儲存.
- 在服務詳細資料頁的 "請求標頭" 中為每個服務設定最多 16 筆自訂請求標頭, 取值方式為固定值, 保密值或 "每個對話自動產生" (依服務與對話衍生, 同一對話內不變). 提供 OpenCode Go 工作階段, OpenRouter 署名, Helicone 與 Cloudflare AI Gateway 預設集; 服務位址在 opencode.ai 而缺少工作階段標頭, 或服務傳回 MissingSessionID 時提供新增入口. 外掛自行傳送或由 HTTP 連線管理的標頭無法自訂; User-Agent 取代預設值, anthropic-beta 與外掛的值合併. 請求標頭隨服務設定自動生效, 不開放給腳本.
- 將每個啟動器對話綁定到一個本機或線上目標快照; 變更已有訊息的對話時預設建議新增對話, 繼續目前對話必須明確確認並記錄變更.
- 為每則助手回覆儲存實際 target/provider/model/locality 快照; 重新產生預設精確沿用原回覆目標, 目標身分變更或無法使用時明確失敗, 不會靜默回退到目前對話目標.
- 本機與雲端生成失敗會始終停留在所選邊界: 啟動器聊天會附加有界且不含敏感資訊的失敗原因, 明確說明未發生跨邊界自動回退, 並在保留部分輸出的同時提供明確的手動目標切換入口.
- 線上 OpenAI 相容, Anthropic Messages 與 Gemini GenerateContent 目標支援原生工具呼叫, 包含串流參數, 並行呼叫與結果續輪.
- 線上模型透過協商 AI Provider 2.1 接收 JPEG/PNG 圖片及工具結果圖片, 支援依個別模型啟用.
- 從 GitHub 專案公開目錄更新線上預設模型. 自動更新預設開啟, 僅在開啟 "模型與服務" 頁時每 24 小時檢查一次, 也可在 "線上服務" 區段的選單中關閉自動更新或手動重新整理. 更新遵循計量付費網路設定, 離線或更新失敗時繼續使用快取或內建清單, 不變更已儲存的服務與自訂模型 ID.
- 依廠商分組瀏覽預設模型, 並擴充 OpenRouter 中的 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型選擇, 保留正確的模型 ID.
- 統一獨立設定的平面分組, 列規格與置中圓角對話框. 語言, 夜間模式, 主題色與啟動器圖示均在確定後生效, 取消不改變已儲存的設定. 主題色預設跟隨 AutoJs6, 提供統一色盤, HEX/RGB 輸入與局部預覽; 中性底色保持穩定, 控制項遵循所選主題. 啟動器預設自適應自動, 升級保留明確儲存的選擇.
- 設定首頁只保留外觀設定與 "會話", "模型與服務", "語音", "隱私權與權限", "更新與關於" 5 個入口, 每項設定只出現在一處, 版本記錄在 "更新與關於" 中. 聊天頁的選單與側邊欄透過 "設定" 進入模型設定; 在 AutoJs6 中開啟本外掛的設定會直接進入 "模型與服務", 按返回鍵回到設定首頁.
- 聊天回覆的生成不依附於聊天頁: 旋轉螢幕, 切換深色模式, 變更語言或訊息字型大小時會繼續生成, 離開聊天頁期間完成的回覆回來時會完整顯示 (應用程式程序被系統結束時仍會中斷). 點 "停止", 新增或切換對話, 切換模型與刪除訊息時會在 1 秒內中斷線上請求, 停止後不再繼續產生用量.
- 線上服務可設定預設推理強度 (預設, 低, 中, 高, 超高, 最高; "預設" 由模型自行決定), Anthropic 服務還可預設開啟快速模式 (回覆更快, 價格更高); 聊天時可只為目前的對話調整, 調整會隨對話儲存. 模型不支援所選檔位時改用最接近的較低檔位. 腳本透過外掛介面呼叫 AI 時不使用這兩項.
- 切換模型使用從底部彈出的面板: 收藏的模型在頂部 "收藏" 群組, 其餘依服務分組, 每個模型只出現一次; 搜尋結果不分組; 每列下方顯示所屬服務 (本機模型顯示檔案大小), 並標出圖片輸入, 推理與快速模式; 面板中可直接調整目前對話的推理強度與快速模式, 底部的 "管理模型與服務" 直達模型設定. 聊天頁頂部會顯示目前對話的推理強度與快速模式.
- 顯示模型的思考過程 (Claude 5 及更新的模型, Gemini 2.5 及更新的模型, 以及會傳回思考內容的 OpenAI 相容服務, 例如 DeepSeek): 生成時在回覆上方的卡片中即時顯示, 完成後摺疊成 "思考了 N 秒", 點按可展開或收合; 設定中的 "生成時顯示思考內容" 與 "結束後自動摺疊" 預設開啟. 思考內容隨對話儲存, 不會被朗讀, 不參與訊息搜尋與複製, 也不會傳回給腳本.
- 聊天時輕觸輸入框左側的 "+" 新增附件, 每則訊息最多 20 個: 圖片透過系統相片挑選器選取 (過大的圖片會自動縮小), 傳送給已開啟 "圖片輸入" 的模型; UTF-8 文字檔 (例如 txt, md, json, csv 與程式碼檔案) 以文字傳送; PDF 傳送給供應商為 Anthropic 或 Gemini 的服務. 本機模型暫不支援附件, 目前模型不支援的附件會變淡且無法傳送. 附件隨訊息儲存, 訊息中顯示縮圖或檔案名稱, 輕觸即可用其他應用程式開啟; 之後繼續對話時, 會在上下文允許的範圍內一併重新傳送, 放不下的只告訴模型檔案名稱.
- 重新產生回覆或編輯後重新傳送訊息時, 為這一輪對話建立新版本, 不會刪除後續的對話: 回覆下方的 "< 2/3 >" 可切換版本, 後續的訊息也會跟著切換, 重新開啟對話後仍顯示上次選擇的版本. 搜尋, 匯出, 朗讀, 用量統計和繼續對話都只使用目前顯示的版本.
- 對話可以 "匯出為 Markdown" (由你在系統儲存對話方塊中選擇位置, 其他 Markdown 閱讀器也能正常顯示) 或 "分享為長圖" (依聊天頁的外觀產生, 透過系統分享面板分享): 入口在聊天頁的選單與側邊欄長按對話的選單中. 只匯出目前顯示的版本; 對話有思考內容時會先詢問是否一併匯出, 預設不包含.
- 會話記錄使用 v6 格式, 另外儲存每個對話的推理強度與快速模式, 回覆的思考內容與用時, 以及訊息的附件資訊與其他版本; 仍可讀取 1.5.0 及更早版本儲存的會話. 退回 1.6.0 或更早的版本後只能看到當時顯示的版本, 舊版開啟聊天頁時會捨棄所有對話的其他版本, 再升級回來也無法還原.
- 輸入框內的麥克風: 輕觸說話, 辨識出的文字插入游標處, 由你核對後傳送; 安裝了 3-Macaw Speech 時直接在輸入框內即時聽寫, 灰色文字隨說隨出, 每句定稿後繼續聽, 再輕觸一次結束. 長按進入語音對話, 說完自動傳送, 回覆讀完後自動再聽; 安裝了 3-Macaw Speech 時每一輪都不再跳出辨識畫面, 麥克風被占用或缺少辨識模型時這一輪改用辨識視窗.
- 回覆可朗讀: 長按回覆選擇 "朗讀", 或在 設定 > 語音 中開啟 "自動朗讀回覆" 邊生成邊讀; 標題和清單等記號不讀, 程式碼區塊和表格各讀一句提示.
- 回應 3-Macaw Speech 數位助理的 `VOICE_COMMAND`: 長按電源鍵說話後輕觸 "3-Stone AI", 在新對話中立即傳送並讀出回答.

******

### 語音

******

聽寫與朗讀使用 Android 系統介面 (`RecognizerIntent` 與 `TextToSpeech`), 安裝了 3-Macaw Speech 時還會直接使用它的聽寫工作階段. 3-Stone AI 本身不錄音, 也不要求麥克風權限. 安裝了 3-Macaw Speech 時, 設定 > 語音 中的 "優先使用 3-Macaw Speech" 預設開啟: 輕觸麥克風即可直接在輸入框內即時聽寫, 尚未定稿的文字顯示為灰色, 每句停頓約 1 秒後定稿並繼續聽, 可以連續說幾句; 再輕觸一次麥克風 (此時顯示為停止按鈕), 15 秒沒有說話或滿 5 分鐘時結束. 長按麥克風進入語音對話, 每一輪都不再跳出辨識畫面: 你說的話以灰色顯示在輸入框末尾, 說完一句自動傳送, 回答讀完後繼續聽, 8 秒沒有說話時結束; 聆聽期間輕觸麥克風可以立即傳送已說的部分. 離開聊天頁, 傳送或切換對話時聽寫隨即結束, 未傳送的文字會留在輸入框. 麥克風正被其他應用程式占用 (例如 3-Stove Agent 的常駐聆聽) 或尚未下載辨識模型時, 這一次改為開啟 3-Macaw Speech 的辨識視窗; 它尚未取得麥克風權限時會開啟它的授權頁面. 朗讀使用它的離線語音. 未安裝或關閉該選項時, 使用手機上已有的辨識應用程式和系統預設的語音合成引擎, 聽寫與語音對話的每一輪都會開啟辨識畫面. 朗讀時輕觸輸入框上方的狀態列可打斷並立即說下一句, 狀態列上的按鈕可結束.

- **手機上有多個語音辨識應用程式, 每次都要選擇?** 安裝了 3-Macaw Speech 時保持 "優先使用 3-Macaw Speech" 開啟即可直接使用它. 關閉後由系統決定, 可以在系統的選擇畫面中設為預設.
- **低階手機朗讀時句與句之間有停頓?** 部分語音 (如 MeloTTS, Kokoro) 在較慢的手機上合成速度跟不上朗讀速度. 在 3-Macaw Speech 中把預設語音換成 AISHELL-3, Piper 或 Matcha 即可.
- **輕觸麥克風後提示沒有辨識模型?** 3-Macaw Speech 的辨識視窗會引導下載模型; 在此之前 3-Stone AI 不會自動改用其他辨識應用程式. 需要暫時使用其他應用程式時, 關閉 "優先使用 3-Macaw Speech".
- **安裝了 3-Macaw Speech, 為什麼有時仍會跳出辨識畫面?** 麥克風正被其他應用程式占用 (例如 3-Stove Agent 的常駐聆聽), 尚未下載辨識模型, 或暫時連不上 3-Macaw Speech 時, 這一次改用它的辨識視窗: 視窗會暫停其他應用程式的聆聽, 也會引導下載模型; 下一次仍會先嘗試即時聽寫. 即時聽寫需要手機上已安裝 AutoJs6; 能否即時聽寫及原因顯示在 設定 > 語音 中.
- **即時聽寫與語音對話會在背景繼續嗎?** 不會. 它們只在聊天頁顯示時進行, 切換到其他應用程式, 鎖定螢幕或來電時結束, 已顯示的文字會留在輸入框; 回到聊天頁後再輕觸或長按麥克風即可繼續.
- **怎樣透過 3-Macaw Speech 的數位助理提問?** 在 3-Macaw Speech 中把它設為預設數位助理, 長按電源鍵 (或主畫面鍵) 說話, 然後在結果卡片上輕觸 "3-Stone AI". 3-Stone AI 會開啟新對話, 立即傳送並讀出回答; 鎖定時須先解鎖. 距上一次這樣提問不到 10 秒時, 文字只會填入輸入框, 不會傳送.
- **辨識出的文字會傳送到哪裡?** 與打字一樣傳送給目前所選的模型; 線上模型會把它傳送給服務供應商. 3-Macaw Speech 全程離線, 其他辨識或朗讀應用程式可能連網.

腳本可以用 AutoJs6 的 `speech` 模組 (由 3-Macaw Speech 提供) 與 `ai` 模組組合出同樣的語音問答, 不需要 3-Stone AI 的介面: `speech.listen` 聽一句, `ai.chat` 或 `ai.stream` 產生回答, `speech.speak` 讀出來. 下面的範例聽到第一句話後就停止聽寫:

```js
let session = speech.listen({ timeoutMillis: 8000 });
session.on('final', () => session.stop());
session.on('end', () => {
    let question = session.result();
    console.log('Heard:', question);
    if (!question) return;
    ai.chat(question, { maxTokens: 256 })
        .then((response) => {
            console.log('Answer:', response.text);
            return speech.speak(response.text);
        })
        .catch((error) => console.error(error.code, error.message));
});
```

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

獨立簽章應用程式可在外掛中請求 AI 存取授權, 綁定應用程式身分與安裝紀錄, 支援在設定中撤銷授權並停止執行中的請求. 此授權涵蓋本機模型及已儲存的線上帳戶, 線上呼叫可能消耗付費額度. Node.js, Bun 和 Worker 使用 ai.hasAccess() 與 ai.requestAccess().

******

### 安全性和隱私

******

外掛為使用者主動發起的推薦模型下載, 使用者設定的線上目標請求, 以及在 "模型與服務" 頁從 GitHub 更新公開預設模型目錄使用 `INTERNET` 權限. 目錄更新不需 API Key, 不呼叫推論服務, 外掛啟用及本機生成不會觸發目錄連網. 外掛不要求廣泛儲存權限. 推薦模型檔案下載使用不可變 HTTPS 版本及固定位元組數與 SHA-256, 僅寫入應用程式私有 `files/model-downloads` 中的部分檔案 (中斷後自動重試並續傳); LiteRT-LM 檔頭, 大小, 摘要, flush 與 fsync 全部通過後才移入 `files/models` 交給匯入流程原子啟用. 匯入仍只讀取系統選擇器授予的 URI, 將驗證副本串流寫入私人 `files/models` 並原子啟用. 原宿主 Provider 服務也會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬, 雙方簽章, 目標中繼資料及宣告來源邊界. 語音功能不要求麥克風權限, 錄音由辨識應用程式或 3-Macaw Speech 完成: 即時聽寫與不跳出辨識畫面的語音對話直接繫結 3-Macaw Speech 的語音服務 (`org.autojs.plugin.SPEECH`, 只有具備 AutoJs6 外掛權限的應用程式可以繫結), 3-Stone AI 只會收到辨識出的文字, 不會接觸音訊; 辨識出的文字與朗讀的文字不寫入記錄檔, 語音輸入的訊息在會話記錄中與打字訊息相同. 模型傳回的思考內容只儲存在本機的會話記錄中, 不會在後續輪次送回線上服務, 也不會經外掛介面傳回給腳本. 聊天附件透過系統選擇器讀取, 不需要儲存空間或相片權限; 選取的檔案會複製到應用程式私有 `files/attachments`, 只傳送給所選線上模型所屬的服務, 刪除對話時一併刪除. 以其他應用程式開啟附件時, 透過只公開 `files/attachments` 的 FileProvider 暫時授予讀取權限. 分享長圖時圖片寫入應用程式快取 `cache/share`, 由另一個只公開該目錄的 FileProvider 交給分享面板, 每次產生前刪除超過 24 小時的舊圖, 最多保留 3 張; 匯出的 Markdown 只寫入你在系統儲存對話方塊中選擇的位置. 服務的自訂請求標頭中, 保密值與 API Key 一樣經 Android Keystore 加密儲存在本機, 介面不會顯示, 不隨匯出帶出, 匯入, 複製或變更供應商或 HTTPS 來源後需要重新輸入; 服務供應商錯誤詳細資料中出現的 API Key 與保密值會替換為 `[redacted]`. 固定值以明文儲存並隨匯出帶出, 請勿將金鑰填成固定值.

******

### 執行限制

******

- 模型匯入硬上限為 8 GiB, 且匯入後至少保留 256 MiB 可用空間.
- 一次只保留一個未完成的模型下載, 開始下載另一個模型會捨棄它 (確認框會寫明). 下載經 AutoJs6 官方外掛共用的下載函式庫寫入應用程式私有的 `files/model-downloads/<sha256>/model.litertlm.part`: 網路中斷時自動重試兩次 (約 2 秒與 5 秒後), 每次都以 HTTP Range 從中斷處續傳; 暫停, 應用程式被關閉或程序結束都保留該檔案, 繼續或重試時先重新驗證已下載的部分再續傳; 取消, 內容無效或驗證不符時刪除. 未完成的下載不會因為放置時間長而被刪除. 驗證通過的檔案移到 `files/models/.download-<sha256>.part` 交給匯入流程. 冷啟動時將最新且尚未匯入的推薦模型下載還原為暫停狀態, 其餘刪除; 1.7.0 留在 `files/models/.download-<sha256>.part` 的未完成檔案會移入新位置, 從已下載的位元組繼續.
- 下載與匯入的本機模型儲存在應用程式私有資料 (`files/models`) 中, 佔用空間顯示在 "本機模型" 區段的卡片與切換模型的面板中. 解除安裝外掛或清除其應用程式資料會一併刪除這些模型; 以前下載到其他位置的模型檔案可以用 "匯入模型檔案" 匯入.
- 應用程式層級單一匯入協調器使 Activity 重建不會中斷進行中的匯入. Fsync pending journal 支援冷啟動復原並清理 stale `.incoming`, `.current` 和 `.pending` 暫存檔案. 復原只會刪除本次嘗試新建且從未由 current metadata 發布的 destination, 已發布或 current 模型及歷史 hash 代次均會保留.
- 為避免與獨立 `:provider` 程序發生競態, 匯入時不會自動刪除先前以 SHA-256 hash 命名的模型代次. "本機模型" 區段可刪除 catalog 模型, 並回收不再由 catalog 參照的 hash 命名檔案.
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
- 在 設定 > 模型與服務 中開啟服務的 "服務詳細資料", 在模型清單的列選單中為支援圖片輸入的模型開啟 "圖片輸入". 既有服務預設關閉. 圖片僅傳送至該服務. LiteRT 與持久 ai.session 仍僅支援文字; Agent 截圖要求 Android 11+, 相容的 AutoJs6 與 AI Agent, observe 工具組及為所選精確模型啟用圖片輸入.
- 附件上限: 每張圖片 5 MB, 文字檔 256 KB 且必須是有效的 UTF-8, PDF 32 MB 且不超過 100 頁 (加密的 PDF 會被拒絕), 每則訊息 20 個, 每個對話總計 48 MB. 每次請求的附件原始位元組: Anthropic 與 OpenAI 相容服務 32 MB, Gemini 20 MB; OpenAI 相容服務不接受 PDF. 超出所選目標的上限時會拒絕傳送並註明上限.
- 每輪對話最多 8 個版本; 一個對話中未顯示的版本合計超過約 2 MiB 時, 會先捨棄最早的版本. 刪除有多個版本的訊息時, 只會刪除目前顯示的版本.
- 長圖高度超過 16384 像素時會等比例縮小; 記憶體不足時把比例減半重試, 仍然失敗就提示改為匯出 Markdown. 回覆仍在產生的對話無法匯出.
- AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通過真實初始圖片及工具結果圖片測試. 合成圖片測試不代表其他目標也支援圖片或已通過完整 Agent 視覺任務.
- 線上失敗僅透過選用的 `providerCode` 欄位提供固定的 `ONLINE_*` 分類. 未知例外不提供分類; 不包含 URL, 憑證, 服務回應或原始例外文字. 失敗代碼與重試策略維持不變.
- 每個服務最多 16 筆自訂請求標頭: 名稱 1 - 64 位元組, 只能使用 RFC 9110 token 字元, 不分大小寫唯一; 固定值與保密值 1 - 1024 位元組, 只能使用可見 ASCII 字元與空格; 全部請求標頭合計不超過 8192 位元組 ("每個對話自動產生" 每筆以 36 位元組計). 22 個由外掛傳送或由 HTTP 連線管理的標頭無法自訂, 匯入時同樣拒絕. 從未設定值的保密標頭不會傳送; 保密值記錄無法讀取時整個請求失敗, 不會在缺少標頭的情況下送出.

******

### 未宣告的能力

******

- 外掛介面不宣告 reasoning 輸出能力, 腳本無法取得思考內容 (思考過程只在啟動器聊天中顯示). 本機 LiteRT-LM 目標仍不宣告 tools 能力.
- 原生工具最多 16 輪, 同時最多 32 個呼叫. 結果必須完整匹配待處理批次; 上下文/輸出限制, 取消與原始截止時間仍生效. 原生工具暫不與持久 ai.session 輪次組合, 初始歷史不接受 tool 角色.
- 不支援從任意 URL 下載模型檔案. 線上預設模型可從專案公開目錄重新整理, 但不會據此建立服務或查詢帳戶權限. 啟動器聊天與 AI Provider V2 僅公開已匯入本機模型及使用者明確設定的線上服務, 本機模型下載仍限於內建目錄中固定版本的推薦模型.
- 不宣告 NPU 推論可用: profile 可發現但以 `npu-runtime-not-packaged` 標記為 `unavailable`. GPU 僅在 `libOpenCL.so` 可載入時宣告, 且 `.litertlm` 副檔名本身仍不保證模型初始化成功.

******

### 路線圖

******

路線圖按可交付的使用者功能組織, 每項均可單獨勾選與驗收

- [檢視 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.9.0

###### 2026/11/28

* `新增` 安裝了 3-Macaw Speech 時, 輕觸麥克風即可直接在輸入框內即時聽寫, 不再跳出辨識畫面: 尚未定稿的文字顯示為灰色, 每句停頓後定稿並繼續聽, 可以連續說幾句; 再輕觸一次麥克風 (此時顯示為停止按鈕), 15 秒沒有說話或滿 5 分鐘時結束. 離開聊天頁, 傳送或切換對話時聽寫隨即結束, 已顯示的文字會留在輸入框
* `新增` 安裝了 3-Macaw Speech 時, 長按麥克風進入的語音對話也不再跳出辨識畫面: 你說的話以灰色顯示在輸入框末尾, 說完一句自動傳送, 回答讀完後繼續聽; 8 秒沒有說話, 輕觸狀態列上的結束按鈕或離開聊天頁時結束, 聆聽期間輕觸麥克風可以立即傳送已說的部分
* `修復` 用本機模型連續對話時 (語音對話中尤其常見), 下一輪有時會出現 "Resource cache already has an active lease" 並中斷: 上一輪還沒交還模型, 下一輪就來取用. 現在下一輪會等上一輪交還 (通常不到一秒); 模型確實被另一項工作佔用超過一分鐘時, 會顯示 "裝置端模型仍被另一項工作佔用", 不再顯示內部錯誤
* `修復` 使用本機模型的長對話中, 較早的內容一直沒能整理成摘要 (每次整理都會失敗), 模型因此比較容易忘記較早的對話內容; 現在本機模型會在對話停頓 30 秒後才整理, 整理期間開始新一輪時會先讓給新一輪
* `修復` 先用本機模型聊天, 再讓 AutoJs6 腳本呼叫本機模型時 (或者反過來), 外掛的聊天與腳本服務會各自載入一份模型, 記憶體用量加倍 (Gemma 4 E2B 約多 1.5 GB), 記憶體吃緊的手機上腳本的呼叫可能會被系統結束 ("BINDER_DIED"). 現在載入模型前會先請另一邊交還閒置的那一份, 只有兩邊同時在產生回答時才會各佔一份
* `修復` 外掛外觀與系統深淺色不同時 (例如系統是淺色, 外掛跟隨 AutoJs6 使用深色), 冷啟動時會先按系統的深淺色與語言顯示, 開啟後再跳成外掛的外觀. 現在外掛會記住上次 AutoJs6 的語言, 深淺色與主題色, 冷啟動的第一個畫面就按它顯示; 在 Android 12 以上還會把外觀告訴系統, 從安裝後第二次啟動起, 啟動畫面也直接使用外掛的外觀 (Android 11 以下的啟動畫面仍按系統深淺色)
* `修復` 語音對話與輸入框內的即時聽寫在麥克風真正開始收音之前就顯示 "正在聆聽", 這時說的開頭幾個字會遺失; 現在會先顯示 "正在準備麥克風", 開始收音後才顯示 "正在聆聽"
* `優化` 麥克風正被其他應用程式占用 (例如 3-Stove Agent 的常駐聆聽) 或 3-Macaw Speech 尚未下載辨識模型時, 聽寫或語音對話的這一次改為開啟它的辨識視窗 (視窗會暫停其他應用程式的聆聽, 也會引導下載模型); 3-Macaw Speech 沒有麥克風權限時會開啟它的授權頁面. 設定 > 語音 頁會顯示能否在輸入框內即時聽寫, 不能時說明原因. 未安裝 3-Macaw Speech 或關閉 "優先使用 3-Macaw Speech" 時與先前相同
* `相依性` 附加 AutoJs6 語音外掛介面 speech-api 契約 v3 (輸入框內即時聽寫與語音對話)

# v1.8.0

###### 2026/11/27

* `提示` 為服務新增請求標頭後, 服務設定會以新格式儲存 (含請求標頭的匯出檔案也是如此), 1.7.2 及更早的版本無法讀取. 如需退回舊版本, 請先刪除所有服務的請求標頭並儲存
* `新增` 線上服務可以新增自訂請求標頭: 在 "模型與服務" 頁開啟一個服務, 輕觸 "連線" 中的 "請求標頭" 即可新增, 編輯或刪除, 每個服務最多 16 筆. 取值方式有三種: 固定值; 保密值 (勾選 "保密", 名稱包含 auth, key, token 或 secret 時預設勾選); "每個對話自動產生" (同一對話內不變, 改用這個服務的其他模型後也不變). 6 種供應商的服務都可以使用. 請求標頭隨服務設定自動生效, 腳本不必修改, 腳本的持久工作階段 (ai.session) 同樣使用固定的工作階段 ID
* `新增` 保密值加密儲存在本機, 介面上只顯示 "已設定", 編輯時不會顯示, 也不會隨匯出帶出. 匯入或複製服務後保密值為 "未設定", 服務卡片上會標示 "請求標頭待輸入", 重新輸入前不會傳送這個請求標頭; 變更供應商或 HTTPS 來源並儲存時, 已儲存的保密值會與 API Key 一併清除. 服務供應商傳回的錯誤詳細資料中如果出現 API Key 或保密值, 會替換為 [redacted]
* `新增` 外掛自行傳送的標頭 (例如 Authorization, x-api-key, Content-Type) 與由 HTTP 連線管理的標頭 (例如 Host, Content-Length) 無法自訂, 對話框會寫明原因; User-Agent 可以自訂 (取代外掛的預設值), anthropic-beta 會與外掛為這次請求計算的值合併. 名稱或值的格式, 長度不符合要求, 或全部請求標頭合計超過 8192 位元組時, 對話框同樣會寫明原因
* `新增` 新增請求標頭時可以展開 "常用預設集": 選擇 OpenCode Go 工作階段或 OpenRouter 署名即可新增對應的請求標頭, 選擇 Helicone 或 Cloudflare AI Gateway 會填好名稱, 只需輸入金鑰. 服務位址在 opencode.ai 而缺少工作階段標頭 x-opencode-session 時, 服務詳細資料頁會顯示提示並提供 "新增" 按鈕; 測試連線或聊天時收到 MissingSessionID 錯誤, 錯誤提示會說明原因並提供 "新增工作階段標頭" 按鈕
* `修復` 外掛語言與系統語言的書寫方向不同時 (例如在中文系統上把外掛切換為阿拉伯語), 對話框底部的 "取消", "確定" 等按鈕現在也隨介面一起鏡像, 不再停留在系統語言的一側

# v1.7.2

###### 2026/11/20

* `優化` 發行歷史頁改為按版本展開或收起: 收起時顯示日期與各類別的項目數, 展開後每個類別只標示一次, 可一次展開或收起全部, 開啟時展開最新版本; 副標題為外掛名稱; 頁面與 "更新與關於" 中的入口由 "版本記錄" 改稱 "發行歷史"
* `優化` 外掛中心中的使用說明重新撰寫, 分為開始使用, 主要功能, 在指令碼中使用, 執行需求與常見問題五部分
* `相依性` 附加 Jetpack Compose BOM 2026.09.00 (發行歷史頁)
* `相依性` 升級 AndroidX Core 至 1.16.0, AndroidX Activity 至 1.12.2, AndroidX Lifecycle 至 2.9.4 (Jetpack Compose 所需)
* `相依性` 升級共用建置外掛版本 1.9.0 -> 1.9.7

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

建置參數來自 `version.properties`. 目前最低 SDK 為 24, 目標 SDK 為 37, 建置 JDK 為 21 或更高版本.

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
