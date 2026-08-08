# AutoJs6 AI テキスト生成

このプラグインは Android Storage Access Framework (SAF) からローカル `.litertlm` モデルパッケージをインポートし, アプリ専用ストレージへコピーします. CPU-only LiteRT-LM でプレーンテキスト履歴を処理し, テキストをストリーミング出力します.

AutoJs6 ホスト build 5270 以降と Android API 24 以降が必要です.

セキュリティと動作制限:

- モデルのインポート上限は 8 GiB で, 完了後に 256 MiB 以上の空き容量が必要です.
- コンテキスト上限は 256 KiB, 出力上限は 64 KiB で, 同時に有効な生成セッションは 1 つだけです.
- Provider は token 数の上限を宣言しません. `maximumOutputTokens` を設定するリクエストは非対応です.
- 宣言する機能は streaming と `text/plain` だけです. Reasoning, tools, structured JSON, usage は非対応です.
- ネットワーク権限とストレージ権限は要求しません.
- 同じ署名の AutoJs6 ホストだけが provider サービスを bind できます.
- プロセス間の安全性のため, 以前の SHA-256 hash 名モデル世代を保持します. これらはアプリ専用ストレージを引き続き使用します.
