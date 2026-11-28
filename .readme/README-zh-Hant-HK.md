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

3-Stone AI 係 AutoJs6 嘅官方 AI 文本生成插件. 佢可以喺顯式選擇嘅 CPU 或相容 GPU backend 上運行用戶導入嘅 LiteRT-LM 模型, 網上生成就使用用戶設定嘅網上服務. 本機同網上執行位置共用一個 AI Provider V2 目標目錄, 受控串流管線同明確選擇邊界. 本機目標絕不聯網或上傳數據; 網上目標只會喺用戶明確選擇後執行, 並始終綁定插件管理嘅憑證同聲明嘅 HTTPS origin.

******

### 功能

******

- 透過 Android 系統檔案選擇器匯入 `.litertlm` 模型套件, 並將驗證後的副本儲存到應用程式私人儲存空間.
- 由內置目錄一步下載固定版本且毋須登入嘅 LiteRT Community 建議模型: 直接寫入應用程式私有儲存空間, 驗證精確大小同 SHA-256 後自動匯入. 下載中嘅模型會以卡片顯示喺 "本機模型" 段, 支援進度, 暫停, 取消同失敗重試, 中斷後由已下載嘅部分繼續; 下載確認框會顯示該模型嘅最低記憶體要求, 本機記憶體不足時會提醒.
- 開啟系統檔案選擇器前預檢私人儲存空間, 顯示目前匯入預算同私人副本預計佔用, 並喺複製前再次檢查所選檔案.
- 使用純文字 system, user 和 assistant 歷史建立本地生成請求.
- 將 AutoJs6 `ai.ask`, `ai.chat` 和 `ai.stream` 嘅 `temperature`, `topK`, `topP` 同 `maxTokens` 傳遞到 LiteRT-LM.
- 通過 AutoJs6 `structuredJson` 同 `responseSchema` 啟用 LiteRT-LM 原生 JSON Schema 約束解碼; 完整結果仍然係可供 `JSON.parse` 解析嘅 JSON 文本.
- 透過 AutoJs6 `ai.chat().usage` 同串流 usage 事件回傳 LiteRT-LM 精確嘅輸入, 輸出及總 token 數, 以及插件端生成耗時.
- 透過 AutoJs6 `ai.session` 喺同一個 LiteRT-LM 原生 Conversation 保留多輪上下文, 後續輪次只發送新嘅用戶提示詞.
- 按模型 SHA-256 重用已初始化 Engine, 消除同一模型連續請求嘅重複冷啟動.
- 可選擇將每個匯入模型初始化一次, 持久保存其"可用/不相容"狀態, 並可喺模型管理介面重新檢查.
- 透過 credit 背壓按序傳送文字 chunk, 並只發佈一個完成, 錯誤或取消終態.
- 喺 "本機模型" 段列出, 重新命名, 檢查同刪除已匯入模型, 卡片會顯示每個模型佔用嘅空間; 刪除模型時會一併刪除佢首次載入時產生嘅 LiteRT-LM 引擎快取. "清理未引用檔案" 可以回收未引用嘅模型檔案, 遺留嘅引擎快取同不再使用嘅附件.
- 透過 AutoJs6 顯式選擇 `cpu`, `gpu` 或 `npu` backend; CPU 為預設值, GPU 只會喺 OpenCL 載入探測通過後開放, NPU 因未封裝 EAP runtime 而明確回報不可用.
- 喺 設定 > 模型與服務 管理內置及自訂網上服務, Android Keystore 憑證, 唯一嘅預設目標 (網上模型或本機模型都得), 計量網絡存取同顯式有界連線測試. 服務喺全螢幕嘅 "服務詳情" 頁編輯: 每個服務可以加入多個模型, 拖動排序並設定首選模型, 撳剔號先會儲存.
- 在服務詳情頁的 "請求標頭" 中為每個服務設定最多 16 項自訂請求標頭, 取值方式為固定值, 保密值或 "每個對話自動生成" (按服務與對話衍生, 同一對話內不變). 提供 OpenCode Go 會話, OpenRouter 署名, Helicone 與 Cloudflare AI Gateway 預設; 服務地址在 opencode.ai 而缺少會話標頭, 或服務傳回 MissingSessionID 時提供新增入口. 插件自行傳送或由 HTTP 連線管理的標頭不可自訂; User-Agent 取代預設值, anthropic-beta 與插件的值合併. 請求標頭隨服務設定自動生效, 不向腳本開放.
- 將每個啟動器對話綁定到一個本機或網上目標快照; 更改已有訊息嘅對話時預設建議新增對話, 繼續目前對話必須明確確認並記錄變更.
- 為每則助手回覆儲存實際 target/provider/model/locality 快照; 重新產生預設精確沿用原回覆目標, 目標身份更改或不可用時明確失敗, 唔會靜默回退到目前對話目標.
- 本機同雲端生成失敗會一直留喺所選邊界: 啟動器聊天會附加有界且不含敏感資料嘅失敗原因, 明確說明未發生跨邊界自動回退, 並喺保留部分輸出嘅同時提供顯式手動目標切換入口.
- 線上 OpenAI 相容, Anthropic Messages 與 Gemini GenerateContent 目標支援原生工具呼叫, 包含串流參數, 並行呼叫與結果續輪.
- 線上模型透過協商 AI Provider 2.1 接收 JPEG/PNG 圖片及工具結果圖片, 支援按個別模型啟用.
- 從 GitHub 專案公開目錄更新網上預設模型. 自動更新預設開啟, 只會喺開啟 "模型與服務" 頁時每 24 小時檢查一次, 亦可以喺 "網上服務" 段嘅選單關閉自動更新或手動重新整理. 更新會遵守計量網絡設定, 離線或更新失敗時繼續使用快取或內置清單, 唔會改動已儲存嘅服務同自訂模型 ID.
- 按廠商分組瀏覽預設模型, 並擴充 OpenRouter 中嘅 Qwen, Kimi, GLM, Grok, Meta, MiniMax 等模型選擇, 保留準確嘅模型 ID.
- 統一獨立設定的平面分組, 列規格與置中圓角對話框. 語言, 夜間模式, 主題色與啟動器圖示均在確定後生效, 取消不改變已儲存的設定. 主題色預設跟隨 AutoJs6, 提供統一色盤, HEX/RGB 輸入與局部預覽; 中性底色保持穩定, 控件遵循所選主題. 啟動器預設自適應自動, 升級保留明確儲存的選擇.
- 設定首頁只保留外觀設定與 "會話", "模型與服務", "語音", "私隱與權限", "更新與關於" 5 個入口, 每項設定只出現在一處, 版本記錄在 "更新與關於" 中. 聊天頁的選單與側邊欄經 "設定" 進入模型設定; 在 AutoJs6 中開啟本插件的設定會直接進入 "模型與服務", 按返回鍵回到設定首頁.
- 聊天回覆的生成不依附於聊天頁: 旋轉螢幕, 切換深色模式, 變更語言或訊息字體大小時會繼續生成, 離開聊天頁期間完成的回覆回來時會完整顯示 (應用程式進程被系統結束時仍會中斷). 按 "停止", 新增或切換對話, 切換模型及刪除訊息時會在 1 秒內中斷網上請求, 停止後不再繼續產生用量.
- 網上服務可設定預設推理強度 (預設, 低, 中, 高, 超高, 最高; "預設" 由模型自行決定), Anthropic 服務亦可預設開啟快速模式 (回覆更快, 價格更高); 聊天時可只為目前的對話調整, 調整會隨對話儲存. 模型不支援所選檔位時改用最接近的較低檔位. 腳本透過插件介面呼叫 AI 時不使用這兩項.
- 切換模型使用從底部彈出的面板: 收藏嘅模型喺頂部 "收藏" 組, 其餘按服務分組, 每個模型只出現一次; 搜尋結果唔分組; 每行下方顯示所屬服務 (本機模型顯示檔案大小), 並標出圖片輸入, 推理及快速模式; 面板內可直接調整目前對話的推理強度及快速模式, 底部的 "管理模型及服務" 直達模型設定. 聊天頁頂部會顯示目前對話的推理強度及快速模式.
- 顯示模型的思考過程 (Claude 5 及更新的模型, Gemini 2.5 及更新的模型, 以及會傳回思考內容的 OpenAI 相容服務, 例如 DeepSeek): 生成時在回覆上方的卡片中即時顯示, 完成後摺疊成 "思考了 N 秒", 點按可展開或收起; 設定中的 "生成時顯示思考內容" 及 "完成後自動摺疊" 預設開啟. 思考內容隨對話儲存, 不會被朗讀, 不參與訊息搜尋及複製, 亦不會傳回給腳本.
- 聊天時點按輸入框左側的 "+" 加入附件, 每則訊息最多 20 個: 圖片經系統相片挑選器選擇 (過大的圖片會自動縮小), 傳送給已開啟 "圖片輸入" 的模型; UTF-8 文字檔案 (例如 txt, md, json, csv 及程式碼檔案) 以文字傳送; PDF 傳送給供應商為 Anthropic 或 Gemini 的服務. 本機模型暫不支援附件, 目前模型不支援的附件會變淡且不可傳送. 附件隨訊息儲存, 訊息中顯示縮圖或檔案名稱, 點按可以用其他應用程式開啟; 之後繼續對話時, 會在上下文容許的範圍內一併重新傳送, 放不下的只告訴模型檔案名稱.
- 重新產生回覆或編輯後重新傳送訊息時, 為這一輪對話新增一個版本, 不會刪除之後的對話: 回覆下方的 "< 2/3 >" 可切換版本, 之後的訊息亦會隨之切換, 重新開啟對話後仍顯示上次選擇的版本. 搜尋, 匯出, 朗讀, 用量統計及繼續對話都只使用目前顯示的版本.
- 對話可以 "匯出為 Markdown" (由你在系統儲存對話框中選擇位置, 其他 Markdown 閱讀器亦能正常顯示) 或 "分享為長圖" (按聊天頁的外觀生成, 經系統分享面板分享): 入口在聊天頁的選單及側邊欄長按對話的選單中. 只匯出目前顯示的版本; 對話有思考內容時會先詢問是否一併匯出, 預設不包括.
- 會話記錄使用 v6 格式, 另外儲存每個對話的推理強度及快速模式, 回覆的思考內容及用時, 以及訊息的附件資料及其他版本; 仍可讀取 1.5.0 及更早版本儲存的會話. 退回 1.6.0 或更早的版本後只能看到當時顯示的版本, 舊版開啟聊天頁時會捨棄所有對話的其他版本, 再升級回來亦無法恢復.
- 輸入框內的麥克風: 短按說話, 辨識出的文字插入游標處, 由你核對後傳送; 安裝了 3-Macaw Speech 時直接在輸入框內即時聽寫, 灰色文字隨說隨出, 每句定稿後繼續聽, 再按一次結束. 長按進入語音對話, 說完自動傳送, 回覆讀完後自動再聽; 安裝了 3-Macaw Speech 時每一輪都不再彈出辨識介面, 麥克風被佔用或缺少辨識模型時這一輪改用辨識視窗.
- 回覆可朗讀: 長按回覆選擇 "朗讀", 或在 設定 > 語音 中開啟 "自動朗讀回覆" 邊生成邊讀; 標題和清單等記號不讀, 程式碼區塊和表格各讀一句提示.
- 應答 3-Macaw Speech 數碼助理的 `VOICE_COMMAND`: 長按電源鍵說話後按 "3-Stone AI", 在新對話中立即傳送並讀出回答.

******

### 語音

******

聽寫與朗讀使用 Android 系統介面 (`RecognizerIntent` 與 `TextToSpeech`), 安裝了 3-Macaw Speech 時還會直接使用它的聽寫會話. 3-Stone AI 本身不錄音, 也不申請麥克風權限. 安裝了 3-Macaw Speech 時, 設定 > 語音 中的 "優先使用 3-Macaw Speech" 預設開啟: 輕按麥克風即可直接在輸入框內即時聽寫, 尚未定稿的文字顯示為灰色, 每句停頓約 1 秒後定稿並繼續聽, 可以連續說幾句; 再輕按一次麥克風 (此時顯示為停止按鈕), 15 秒沒有說話或滿 5 分鐘時結束. 長按麥克風進入語音對話, 每一輪都不再彈出辨識介面: 你說的話以灰色顯示在輸入框末尾, 說完一句自動傳送, 回答讀完後繼續聽, 8 秒沒有說話時退出; 聆聽期間輕按麥克風可以立即傳送已說的部分. 離開聊天頁, 傳送或切換對話時聽寫隨即結束, 未傳送的文字會留在輸入框. 麥克風正被其他應用程式佔用 (例如 3-Stove Agent 的常駐聆聽) 或尚未下載辨識模型時, 這一次改為開啟 3-Macaw Speech 的辨識視窗; 它尚未取得麥克風權限時會開啟它的授權頁面. 朗讀使用它的離線語音. 未安裝或關閉該選項時, 使用手機上已有的辨識應用程式和系統預設的語音合成引擎, 聽寫與語音對話的每一輪都會開啟辨識介面. 朗讀時輕按輸入框上方的狀態列可打斷並立即說下一句, 狀態列上的按鈕可退出.

- **手機上有多個語音辨識應用程式, 每次都要選擇?** 安裝了 3-Macaw Speech 時保持 "優先使用 3-Macaw Speech" 開啟即可直接使用它. 關閉後由系統決定, 可以在系統的選擇介面中設為預設.
- **低階手機朗讀時句與句之間有停頓?** 部分語音 (如 MeloTTS, Kokoro) 在較慢的手機上合成速度跟不上朗讀速度. 在 3-Macaw Speech 中把預設語音換成 AISHELL-3, Piper 或 Matcha 即可.
- **按麥克風後提示沒有辨識模型?** 3-Macaw Speech 的辨識視窗會引導下載模型; 在此之前 3-Stone AI 不會自動改用其他辨識應用程式. 需要暫時使用其他應用程式時, 關閉 "優先使用 3-Macaw Speech".
- **安裝了 3-Macaw Speech, 為甚麼有時仍會彈出辨識介面?** 麥克風正被其他應用程式佔用 (例如 3-Stove Agent 的常駐聆聽), 尚未下載辨識模型, 或暫時連接不到 3-Macaw Speech 時, 這一次改用它的辨識視窗: 視窗會暫停其他應用程式的聆聽, 也會引導下載模型; 下一次仍會先嘗試即時聽寫. 即時聽寫需要手機上已安裝 AutoJs6; 能否即時聽寫及原因顯示在 設定 > 語音 中.
- **即時聽寫與語音對話會在背景繼續嗎?** 不會. 它們只在聊天頁顯示時進行, 切換到其他應用程式, 鎖屏或來電時結束, 已顯示的文字會留在輸入框; 回到聊天頁後再輕按或長按麥克風即可繼續.
- **怎樣經 3-Macaw Speech 的數碼助理提問?** 在 3-Macaw Speech 中把它設為預設數碼助理, 長按電源鍵 (或主頁鍵) 說話, 然後在結果卡片上按 "3-Stone AI". 3-Stone AI 會開啟新對話, 立即傳送並讀出回答; 鎖屏時須先解鎖. 距上一次這樣提問不足 10 秒時, 文字只會填入輸入框, 不會傳送.
- **辨識出的文字會傳送到哪裡?** 與打字一樣傳送給目前所選的模型; 線上模型會把它傳送給服務商. 3-Macaw Speech 全程離線, 其他辨識或朗讀應用程式可能連網.

腳本可以用 AutoJs6 的 `speech` 模組 (由 3-Macaw Speech 提供) 與 `ai` 模組組合出同樣的語音問答, 毋須 3-Stone AI 的介面: `speech.listen` 聽一句, `ai.chat` 或 `ai.stream` 生成回答, `speech.speak` 讀出來. 以下例子聽到第一句話後便停止聽寫:

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

獨立簽章應用程式可在外掛中請求 AI 存取授權, 綁定應用程式身分與安裝紀錄, 支援在設定中撤銷授權並停止執行中的請求. 此授權涵蓋本機模型及已儲存的線上帳戶, 線上呼叫可能消耗付費額度. Node.js, Bun 和 Worker 使用 ai.hasAccess() 與 ai.requestAccess().

******

### 安全性和私隱

******

插件為用戶主動發起嘅建議模型下載, 用戶設定嘅網上目標請求, 同喺 "模型與服務" 頁從 GitHub 更新公開預設模型目錄使用 `INTERNET` 權限. 目錄更新唔需要 API Key, 唔會呼叫推理服務, 插件啟用同本機生成唔會觸發目錄連網. 插件唔要求廣泛儲存權限. 建議模型檔案下載使用不可變 HTTPS 版本及固定字節數同 SHA-256, 只寫入應用程式私有 `files/model-downloads` 入面嘅部分檔案 (中斷後會自動重試同續傳); LiteRT-LM 檔案頭, 大小, 摘要, flush 同 fsync 全部通過後先移入 `files/models` 交俾匯入流程原子啟用. 匯入仍只讀取系統選擇器授予嘅 URI, 將驗證副本串流寫入私人 `files/models` 並原子啟用. 原宿主 Provider 服務亦會核驗 AutoJs6 套件名稱, 呼叫 UID 歸屬, 雙方簽名, 目標元數據同聲明來源邊界. 語音功能不申請麥克風權限, 錄音由辨識應用程式或 3-Macaw Speech 完成: 即時聽寫與不彈出辨識介面的語音對話直接連接 3-Macaw Speech 的語音服務 (`org.autojs.plugin.SPEECH`, 只有持有 AutoJs6 插件權限的應用程式可以連接), 3-Stone AI 只會收到辨識出的文字, 不會接觸音訊; 辨識出的文字與朗讀的文字不寫入日誌, 語音輸入的訊息在會話記錄中與打字訊息相同. 模型傳回的思考內容只儲存在本機的會話記錄中, 不會在後續輪次送回網上服務, 亦不會經插件介面傳回給腳本. 聊天附件經系統選擇器讀取, 不需要儲存或相片權限; 選中的檔案會複製到應用程式私有 `files/attachments`, 只傳送給所選網上模型所屬的服務, 刪除對話時一併刪除. 用其他應用程式開啟附件時, 經只公開 `files/attachments` 的 FileProvider 臨時授予讀取權限. 分享長圖時圖片寫入應用程式快取 `cache/share`, 由另一個只公開該目錄的 FileProvider 交給分享面板, 每次生成前刪除超過 24 小時的舊圖, 最多保留 3 張; 匯出的 Markdown 只寫入你在系統儲存對話框中選擇的位置. 服務的自訂請求標頭中, 保密值與 API Key 一樣經 Android Keystore 加密儲存在本機, 介面不會顯示, 不隨匯出帶出, 匯入, 複製或更改供應商或 HTTPS 來源後需要重新填寫; 服務供應商錯誤詳情中出現的 API Key 與保密值會替換為 `[redacted]`. 固定值以明文儲存並隨匯出帶出, 請勿把金鑰填成固定值.

******

### 執行限制

******

- 模型匯入硬上限為 8 GiB, 且匯入後至少保留 256 MiB 可用空間.
- 一次只保留一個未完成嘅模型下載, 開始下載另一個模型會捨棄佢 (確認框會寫明). 下載經 AutoJs6 官方插件共用嘅下載庫寫入應用程式私有嘅 `files/model-downloads/<sha256>/model.litertlm.part`: 網絡中斷時會自動重試兩次 (約 2 秒同 5 秒後), 每次都用 HTTP Range 由中斷處續傳; 暫停, 應用程式被關閉或進程結束都會保留呢個檔案, 繼續或重試時會先重新驗證已下載嘅部分再續傳; 取消, 內容無效或驗證不符時會刪除. 未完成嘅下載唔會因為放置時間長而被刪除. 驗證通過嘅檔案會移到 `files/models/.download-<sha256>.part` 交俾匯入流程. 冷啟動時會將最新而且未匯入嘅建議模型下載還原為暫停狀態, 其餘刪除; 1.7.0 留喺 `files/models/.download-<sha256>.part` 嘅未完成檔案會移入新位置, 由已下載嘅位元組繼續.
- 下載同匯入嘅本機模型儲存喺應用程式私有數據 (`files/models`) 入面, 佔用空間會顯示喺 "本機模型" 段嘅卡片同切換模型嘅面板. 解除安裝插件或清除佢嘅應用程式數據會一併刪除呢啲模型; 以前下載到其他位置嘅模型檔案可以用 "匯入模型檔案" 匯入.
- 應用程式級單匯入協調器使 Activity 重建不會中斷正在進行的匯入. Fsync pending journal 支援冷啟動復原並清理 stale `.incoming`, `.current` 和 `.pending` 暫存檔案. 復原只會刪除本次嘗試新建且從未由 current metadata 發佈的 destination, 已發佈或 current 模型及歷史 hash 代次均會保留.
- 為避免與獨立 `:provider` 進程發生競態, 匯入時唔會自動刪除先前以 SHA-256 hash 命名嘅模型代次. "本機模型" 段可刪除 catalog 模型, 並回收唔再由 catalog 引用嘅 hash 命名檔案.
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
- 喺 設定 > 模型與服務 開啟服務嘅 "服務詳情", 喺模型列表嘅行選單為支援圖片輸入嘅模型開啟 "圖片輸入". 現有服務預設關閉. 圖片只傳送至該服務. LiteRT 與持久 ai.session 仍只支援文字; Agent 截圖要求 Android 11+, 相容的 AutoJs6 及 AI Agent, observe 工具組及為所選精確模型啟用圖片輸入.
- 附件上限: 每張圖片 5 MB, 文字檔案 256 KB 且必須是有效的 UTF-8, PDF 32 MB 且不超過 100 頁 (加密的 PDF 會被拒收), 每則訊息 20 個, 每個對話合共 48 MB. 每次請求的附件原始字節: Anthropic 及 OpenAI 相容服務 32 MB, Gemini 20 MB; OpenAI 相容服務不接收 PDF. 超出所選目標的上限時會拒絕傳送並寫明上限.
- 每輪對話最多 8 個版本; 一個對話中未顯示的版本合共超過約 2 MiB 時, 會先捨棄最早的版本. 刪除有多個版本的訊息時, 只會刪除目前顯示的版本.
- 長圖高度超過 16384 像素時會按比例縮小; 記憶體不足時把比例減半重試, 仍然失敗就提示改為匯出 Markdown. 回覆仍在生成的對話不可匯出.
- AiGoCode gpt-5.6-sol 已在 Provider 1.2.0 / build 218 通過真實初始圖片及工具結果圖片測試. 合成圖片測試不代表其他目標也支援圖片或已通過完整 Agent 視覺任務.
- 在線失敗僅透過可選的 `providerCode` 欄位提供固定的 `ONLINE_*` 分類. 未知異常不提供分類; 不包含 URL, 憑據, 服務回應或原始異常文字. 失敗代碼同重試策略保持不變.
- 每個服務最多 16 項自訂請求標頭: 名稱 1 - 64 位元組, 只可使用 RFC 9110 token 字元, 不區分大小寫唯一; 固定值與保密值 1 - 1024 位元組, 只可使用可見 ASCII 字元與空格; 全部請求標頭合計不超過 8192 位元組 ("每個對話自動生成" 每項按 36 位元組計). 22 個由插件傳送或由 HTTP 連線管理的標頭不可自訂, 匯入時同樣拒絕. 從未設定值的保密標頭不會傳送; 保密值記錄讀取失敗時整次請求失敗, 不會在缺少標頭的情況下發出.

******

### 未宣告的能力

******

- 插件介面不宣告 reasoning 輸出能力, 腳本無法取得思考內容 (思考過程只在啟動器聊天中顯示). 本地 LiteRT-LM 目標仍不宣告 tools 能力.
- 原生工具最多 16 輪, 同時最多 32 個呼叫. 結果必須完整匹配待處理批次; 上下文/輸出限制, 取消與原始截止時間仍生效. 原生工具暫不與持久 ai.session 輪次組合, 初始歷史不接受 tool 角色.
- 唔支援從任意 URL 下載模型檔案. 網上預設模型可以從專案公開目錄重新整理, 但唔會因此建立服務或查詢帳戶權限. 啟動器聊天同 AI Provider V2 只公開已導入本機模型及用戶明確設定嘅網上服務, 本機模型下載仍限於內置目錄中固定版本嘅建議模型.
- 不宣告 NPU 推理可用: profile 可發現但以 `npu-runtime-not-packaged` 標記為 `unavailable`. GPU 只喺 `libOpenCL.so` 可載入時宣告, 而 `.litertlm` 副檔名本身仍不保證模型初始化成功.

******

### 路線圖

******

路線圖按可交付嘅用戶功能組織, 每項均可單獨勾選同驗收

- [查看 ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### 版本歷史

******

# v1.9.0

###### 2026/11/28

* `新增` 安裝了 3-Macaw Speech 時, 輕按麥克風即可直接在輸入框內即時聽寫, 不再彈出辨識介面: 尚未定稿的文字顯示為灰色, 每句停頓後定稿並繼續聽, 可以連續說幾句; 再輕按一次麥克風 (此時顯示為停止按鈕), 15 秒沒有說話或滿 5 分鐘時結束. 離開聊天頁, 傳送或切換對話時聽寫隨即結束, 已顯示的文字會留在輸入框
* `新增` 安裝了 3-Macaw Speech 時, 長按麥克風進入的語音對話也不再彈出辨識介面: 你說的話以灰色顯示在輸入框末尾, 說完一句自動傳送, 回答讀完後繼續聽; 8 秒沒有說話, 輕按狀態列上的退出按鈕或離開聊天頁時結束, 聆聽期間輕按麥克風可以立即傳送已說的部分
* `修復` 用本機模型連續對話時 (語音對話中尤其常見), 下一輪有時會出現 "Resource cache already has an active lease" 並中斷: 上一輪尚未交還模型, 下一輪便來取用. 現在下一輪會等上一輪交還 (通常不足一秒); 模型確實被另一項工作佔用超過一分鐘時, 會顯示 "裝置端模型仲被另一項工作佔用緊", 不再顯示內部錯誤
* `修復` 使用本機模型的長對話中, 較早的內容一直未能整理成摘要 (每次整理都會失敗), 模型因而較容易忘記較早的對話內容; 現在本機模型會在對話停頓 30 秒後才整理, 整理期間開始新一輪時會先讓給新一輪
* `修復` 先用本機模型聊天, 再讓 AutoJs6 腳本呼叫本機模型時 (或者反過來), 插件的聊天與腳本服務會各自載入一份模型, 記憶體佔用倍增 (Gemma 4 E2B 約多 1.5 GB), 記憶體緊張的手機上腳本的呼叫可能會被系統結束 ("BINDER_DIED"). 現在載入模型前會先請另一邊交還閒置的那一份, 只有兩邊同時在生成回答時才會各佔一份
* `修復` 插件外觀與系統深淺色不同時 (例如系統是淺色, 插件跟隨 AutoJs6 使用深色), 冷啟動時會先按系統的深淺色及語言顯示, 開啟後再跳成插件的外觀. 現在插件會記住上次 AutoJs6 的語言, 深淺色及主題色, 冷啟動的第一個畫面就按它顯示; 在 Android 12 或以上還會把外觀告訴系統, 由安裝後第二次啟動起, 啟動畫面亦直接使用插件的外觀 (Android 11 或以下的啟動畫面仍按系統深淺色)
* `修復` 語音對話與輸入框內的即時聽寫在麥克風真正開始收音之前就顯示 "正在聆聽", 這時說的開頭幾個字會遺失; 現在會先顯示 "正在準備麥克風", 開始收音後才顯示 "正在聆聽"
* `優化` 麥克風正被其他應用程式佔用 (例如 3-Stove Agent 的常駐聆聽) 或 3-Macaw Speech 尚未下載辨識模型時, 聽寫或語音對話的這一次改為開啟它的辨識視窗 (視窗會暫停其他應用程式的聆聽, 也會引導下載模型); 3-Macaw Speech 沒有麥克風權限時會開啟它的授權頁面. 設定 > 語音 頁會顯示能否在輸入框內即時聽寫, 不能時說明原因. 未安裝 3-Macaw Speech 或關閉 "優先使用 3-Macaw Speech" 時與之前相同
* `依賴` 附加 AutoJs6 語音插件介面 speech-api 契約 v3 (輸入框內即時聽寫與語音對話)

# v1.8.0

###### 2026/11/27

* `提示` 為服務新增請求標頭後, 服務設定會以新格式儲存 (含請求標頭的匯出檔案亦然), 1.7.2 及更早的版本無法讀取. 如需退回舊版本, 請先刪除所有服務的請求標頭並儲存
* `新增` 網上服務可以新增自訂請求標頭: 在 "模型與服務" 頁開啟一個服務, 點按 "連線" 中的 "請求標頭" 即可新增, 編輯或刪除, 每個服務最多 16 項. 取值方式有三種: 固定值; 保密值 (勾選 "保密", 名稱包含 auth, key, token 或 secret 時預設勾選); "每個對話自動生成" (同一對話內不變, 改用這個服務的其他模型後亦不變). 6 種供應商的服務都可以使用. 請求標頭隨服務設定自動生效, 腳本毋須修改, 腳本的持久會話 (ai.session) 同樣使用固定的會話編號
* `新增` 保密值加密儲存在本機, 介面上只顯示 "已設定", 編輯時不會顯示, 亦不會隨匯出帶出. 匯入或複製服務後保密值為 "未設定", 服務卡片上會標示 "請求標頭待填寫", 重新填寫前不會傳送這個請求標頭; 更改供應商或 HTTPS 來源並儲存時, 已儲存的保密值會與 API Key 一併清除. 服務供應商傳回的錯誤詳情中如果出現 API Key 或保密值, 會替換為 [redacted]
* `新增` 插件自行傳送的標頭 (例如 Authorization, x-api-key, Content-Type) 與由 HTTP 連線管理的標頭 (例如 Host, Content-Length) 不可自訂, 對話框會寫明原因; User-Agent 可以自訂 (取代插件的預設值), anthropic-beta 會與插件為這次請求計算的值合併. 名稱或值的格式, 長度不符合要求, 或全部請求標頭合計超過 8192 位元組時, 對話框同樣會寫明原因
* `新增` 新增請求標頭時可以展開 "常用預設": 選擇 OpenCode Go 會話或 OpenRouter 署名即可新增對應的請求標頭, 選擇 Helicone 或 Cloudflare AI Gateway 會填好名稱, 只需輸入金鑰. 服務地址在 opencode.ai 而缺少會話標頭 x-opencode-session 時, 服務詳情頁會顯示提示並提供 "新增" 按鈕; 測試連線或聊天時收到 MissingSessionID 錯誤, 錯誤提示會說明原因並提供 "新增會話標頭" 按鈕
* `修復` 插件語言與系統語言的書寫方向不同時 (例如在中文系統上把插件切換為阿拉伯語), 對話框底部的 "取消", "確定" 等按鈕現在也隨介面一起鏡像, 不再停留在系統語言的一側

# v1.7.2

###### 2026/11/20

* `優化` 發行歷史頁改為按版本展開或收起: 收起時顯示日期與各類別的項目數, 展開後每個類別只標示一次, 可一次展開或收起全部, 開啟時展開最新版本; 副標題為插件名稱; 頁面與 "更新與關於" 中的入口由 "版本記錄" 改稱 "發行歷史"
* `優化` 插件中心中的使用說明重新編寫, 分為開始使用, 主要功能, 在腳本中使用, 運行要求與常見問題五部分
* `依賴` 附加 Jetpack Compose BOM 2026.09.00 (發行歷史頁)
* `依賴` 升級 AndroidX Core 至 1.16.0, AndroidX Activity 至 1.12.2, AndroidX Lifecycle 至 2.9.4 (Jetpack Compose 所需)
* `依賴` 升級共用構建插件版本 1.9.0 -> 1.9.7

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

構建參數來自 `version.properties`. 目前最低 SDK 為 24, 目標 SDK 為 37, 構建 JDK 為 21 或更高版本.

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
