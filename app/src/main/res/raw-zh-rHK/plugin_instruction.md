3-Stone AI 為 AutoJs6 提供 AI 對話與模型管理: 本機模型在手機上離線運行, 網上服務 (例如 OpenAI, Anthropic, Gemini) 使用你自己的 API Key. 插件自帶聊天介面, 同時為 AutoJs6 腳本的 `ai` 模組提供模型.

### 開始使用

1. 從桌面圖示開啟 3-Stone AI, 在聊天頁的選單中點按 "設定", 再點按 "模型與服務".
2. 使用本機模型: 在 "本機模型" 段點按 "下載模型" 並選擇推薦模型, 下載並校驗後自動匯入; 已有 `.litertlm` 檔案時點按 "匯入模型檔案".
3. 使用網上服務: 在 "網上服務" 段點按 "新增服務", 選擇供應商, 填寫模型 ID 與 API Key 後儲存, 然後可以在服務選單中選擇 "測試連線".
4. 返回聊天頁, 點按頂部的模型名稱選擇模型, 輸入問題後傳送.

### 主要功能

- **聊天**: 對話自動儲存, 可以查看模型的思考過程; 重新生成或編輯訊息後保留各個版本, 可以來回切換.
- **附件**: 點按輸入框左側的 "+" 加入圖片, 文字檔案或 PDF. 圖片傳送給已開啟 "圖片輸入" 的模型, PDF 只傳送給 Anthropic 與 Gemini 服務; 本機模型暫不支援附件.
- **切換模型**: 聊天時隨時切換模型; 腳本預設使用 "模型與服務" 頁中的 "預設目標".
- **模型管理**: 在 "本機模型" 段檢查, 重新命名或刪除模型. API Key 只儲存在本機, 匯出服務時不包含 API Key.
- **請求標頭**: 在服務詳情頁的 "請求標頭" 中新增自訂請求標頭, 例如 OpenCode Go 需要的會話標頭; 保密值只儲存在本機, 匯出時不包含.
- **語音**: 點按麥克風聽寫, 長按開始語音對話, 長按回覆可以 "朗讀"; 安裝了 3-Macaw Speech 時聽寫與朗讀離線完成.
- **匯出與分享**: 在聊天頁的選單中選擇 "匯出為 Markdown" 或 "分享為長圖".

### 在腳本中使用

在 AutoJs6 腳本中透過 `ai` 模組調用本插件, 不指定目標時使用 "預設目標":

```js
ai.ask('Introduce AutoJs6 in one sentence.').then((text) => {
    console.log(text);
});
```

要固定使用某個模型, 先用 `ai.catalog()` 找到目標, 再把它的 `id` 作為 `target` 傳入. 以下示例使用第一個可用的本機模型:

```js
ai.catalog().then((catalog) => {
    let target = catalog.targets.find((item) => item.available && item.locality === 'local');
    if (!target) throw new Error('No available local model');
    return ai.ask('Reply with OK.', { target: target.id, temperature: 0.7 });
}).then((text) => console.log(text));
```

多輪對話使用 `ai.session`, 每輪只傳送新的提示詞, 用完後調用 `session.close()`:

```js
ai.session({ system: 'Keep every answer short.' }).then((session) => {
    return session.ask('Remember this code: Orion.')
        .then(() => session.ask('What code should you remember?'))
        .then((text) => {
            console.log(text);
            session.close();
        }, (error) => {
            session.close();
            throw error;
        });
});
```

- `ai.chat` 返回包含用量的完整回應, `ai.stream` 邊生成邊返回文字.
- `temperature`, `topK`, `topP` 與 `maxTokens` 控制取樣與輸出長度; 目標支援結構化輸出時, 傳入 `responseSchema` 可以得到符合該 JSON Schema 的文字.
- 目標不可用或未設定時請求直接失敗 (例如 `TARGET_UNAVAILABLE`), 不會自動改用其他模型.

### 運行要求

- Android 7.0 或更高版本, AutoJs6 6.8.0 或更高版本.
- 本機模型需要 64 位元裝置 (arm64-v8a 或 x86_64) 與足夠的儲存空間, 下載確認框會顯示所需的最低記憶體; 32 位元裝置只能使用網上服務.
- 網上服務需要你自己的 API Key, 服務商可能按用量收費.
- 網絡權限用於下載模型, 存取網上服務與更新預置模型清單; 本機推理不連網. 插件不要求儲存空間與麥克風權限.

### 常見問題

**本機模型與網上服務的分別**

本機模型在手機上運行, 對話內容不會離開裝置, 速度與效果取決於手機效能. 網上服務把對話傳送給所選的服務商, 需要網絡與 API Key.

**生成失敗時不自動改用其他模型的原因**

請求只使用你選定的模型, 以免對話內容在你不知情時傳送到其他服務. 失敗時到 "模型與服務" 檢查該模型, 或手動切換.

**本機模型無法運行時的處理**

在 "本機模型" 段開啟該模型的選單, 點按 "檢查模型" 查看原因; 記憶體不足時改用較小的推薦模型.

**其他應用程式要求使用 AI 的情形**

以 AutoJs6 打包並獨立簽署的應用程式使用 AI 前, 3-Stone AI 會請你確認授權. 授權後該應用程式可以使用本機模型與已儲存的網上服務, 可能消耗付費額度; 可在 "設定 > 私隱與權限 > 已授權的應用程式" 中移除授權.

**OpenCode Go 傳回 MissingSessionID 的處理**

OpenCode Go 要求每個請求帶有會話標頭 x-opencode-session. 在錯誤說明中點按 "新增會話標頭", 或在服務詳情頁的 "請求標頭" 中新增 "OpenCode Go 會話" 預設, 儲存後重試.

全部選項, 事件與錯誤碼見 AutoJs6 文檔的 [人工智能 (AI)](https://docs.autojs6.com/#/ai) 章節, 私隱與運行限制見 [項目主頁](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI).
