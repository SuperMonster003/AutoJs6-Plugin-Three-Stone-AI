******

### 版本歷史

******

# v1.0.0

###### 2026/08/08

* `新增` AI Text Generation 協定 V1 裝置端 provider, 外掛 ID 和引擎為 `ai-text-generation`, provider ID 為 `autojs6.local.text`, 變體為 `default`
* `新增` CPU-only LiteRT-LM 純文字生成, 支援 system, user 和 assistant 歷史及 credit 背壓串流輸出
* `新增` 透過 SAF 匯入 `.litertlm` 到應用程式私人儲存空間, 包含 8 GiB 上限, 空間預留, SHA-256, fsync 和原子啟用
* `新增` 單一作用中工作階段, 有界 I/O, descriptor 配額, 取消, 逾時, 唯一終態及同簽章 AutoJs6 呼叫端核驗
* `新增` 明確不宣告 reasoning, tools, structured JSON, usage, 網路或 credential 能力
* `新增` arm64-v8a, x86_64 和 universal APK, 以及 10 種語言的 README, 更新日誌, Android 介面和外掛說明
* `新增` 模型管理介面可檢視完整模型目錄和私人儲存空間用量, 並在不複製模型檔案的情況下原子切換目前模型
* `優化` 為避免獨立 `:provider` 程序競態, 取代匯入後保留先前以 SHA-256 hash 命名的模型代次, 保留檔案會繼續占用應用程式私人儲存空間
* `優化` 加入應用程式層級單一匯入協調器和 fsync pending journal, 在 Activity 重建時保持匯入, 支援冷啟動復原和 stale 暫存檔案清理, 刪除僅限本次新建而從未發布的 destination, 並保留已發布, current 和歷史 hash 代次
* `相依性` 附加 LiteRT-LM 0.15.0, 用於裝置端 CPU 文字生成
