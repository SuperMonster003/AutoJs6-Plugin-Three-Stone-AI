# AutoJs6 AI 文字生成

此外掛透過 Android Storage Access Framework (SAF) 匯入一個本機 `.litertlm` 模型套件, 將其複製到應用程式私人儲存空間, 並使用 CPU-only LiteRT-LM 根據純文字歷史產生串流純文字.

外掛需要 AutoJs6 主程式建置版本 5270 或更高版本, 以及 Android API 24 或更高版本.

安全性和執行限制:

- 模型匯入上限為 8 GiB, 完成後必須至少保留 256 MiB 可用空間.
- 內容上限為 256 KiB, 輸出上限為 64 KiB, 同一時間僅允許一個生成工作階段.
- Provider 不宣告 token 數量上限, 因此不支援設定 `maximumOutputTokens` 的請求.
- 僅宣告 streaming 和 `text/plain`. 不支援 reasoning, tools, structured JSON 和 usage.
- 外掛不要求網路或儲存權限.
- 僅允許同簽章 AutoJs6 主程式綁定 provider 服務.
- 為保證跨程序安全, 會保留先前以 SHA-256 hash 命名的模型代次, 它們會繼續占用應用程式私人儲存空間.
