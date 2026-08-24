******

### リリース履歴

******

# v1.1.0

###### 2026/08/24

* `機能` AutoJs6 公式ローカル AI プラグインのブランドとランタイム識別子を 3-Stone AI に統一
* `機能` プロセス間統合に中立な `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, `IAiProvider`/`IAiSession`/`IAiCallback` 識別子を採用し, 置換前の識別子 alias は保持しない
* `機能` AutoJs6 の `ai.ask`/`ai.chat`/`ai.stream` における `plugin: true` 短縮セレクターと `ai.models` モデル列挙に対応
* `機能` AI Provider プロトコル 1.1 により `temperature`, `topK`, `topP`, `maxTokens` を LiteRT-LM の sampling と出力 token 制御まで伝達
* `機能` LiteRT-LM の正確な入力, 出力, 合計 token 数とプロバイダー実測の生成時間を `ai.chat().usage` とストリーム usage イベントで報告
* `機能` AI Provider プロトコル 1.2 の永続セッションと AutoJs6 `ai.session` による複数ターン Conversation 再利用に対応し, 以前の履歴の再送信を不要化
* `機能` AutoJs6 の `structuredJson` と `responseSchema` による LiteRT-LM ネイティブ JSON Schema 制約デコードを追加し, 単発呼び出し, ストリーミング, 永続セッションと完成 JSON の厳密な検証に対応
* `機能` プロトコル 1.3 と AutoJs6 生成オプションで明示的な `cpu`, `gpu`, `npu` backend profile を提供し, デバイス互換性報告, モデル/profile 単位のキャッシュ分離, 使用不可 profile からのフォールバック禁止に対応; GPU は OpenCL ロード検査成功後のみ宣言し, NPU は EAP runtime 未同梱のため使用不可を維持
* `機能` 固定 LiteRT Community モデルをユーザー選択の SAF 保存先へ直接ダウンロードし, 進捗, 正確なキャンセル, 不完全ファイル削除, LiteRT-LM ヘッダーと正確なサイズ/SHA-256 検証, ダウンロード後の直接インポートに対応
* `機能` ランチャーから開ける会話ワークスペースを追加し, ストリーミング Markdown, 永続履歴, 過去メッセージ編集時の分岐置換警告, 複数結果検索, キーボード対応入力を提供
* `機能` テーマ色, ダークモード, アプリ言語, アプリと開発者情報, リリース履歴のアプリ設定を追加し, 可能な項目では AutoJs6 に従うを既定値に設定
* `機能` フォントサイズ, Enter キー動作, 無制限または任意の output token, モデル既定または任意の `temperature`, `topK`, `topP` を会話設定に追加
* `機能` ストリーミング中にインライン `$\text{...}$` を描画し, 一般的な数式コマンド, 上付き, 下付き表示に対応
* `機能` プラグイン管理の Android Keystore 認証情報ストアを追加し, AES-256-GCM, profile に結び付けた認証済み暗号文, プロセス間で原子的な非公開ファイル, configured 状態のみの照会, 平文の即時消去に対応
* `機能` HTTPS 専用の OpenAI Compatible エンドポイント向けに機密情報を含まない厳格なオンライン profile リポジトリを追加し, canonical UUID, プロセス間で原子的なメタデータ, provider または origin 変更時の認証情報の明示的な置換または消去に対応
* `機能` カスタム baseUrl, 認証情報, モデル profile 用のプラグイン内部 OpenAI Compatible HTTPS 実行 backend を追加し, 上限付き SSE と JSON fallback ストリーミング, 正確なキャンセル, provider usage, 完了ターンのみの永続履歴, JSON Schema リクエスト変換, 機密情報を含まない固定エラーに対応; AI Provider V1 のホストルーティングは引き続きローカル target のみを公開
* `修正` 実行可能な説明例に設定されていた暗黙の 256 token / 4 KiB 出力上限を削除し, `maxTokens` 省略時はモデルまたは engine の既定値を使用, raw Binder 例は provider の 64 KiB 出力許容量全体を使用するよう修正
* `修正` 10 言語のローカライズ済みプラグイン説明にある低レベル Binder 例が, プロトコル 1.1 の 14 引数 `AiGenerationOptions` コンストラクターを呼び続け, プロトコル 1.3 API で失敗する問題を修正
* `修正` システムのダークモードでもモデル管理画面がライトテーマの文字色を保持し, 本文, チェックボックス, モデル行が暗い背景で読めなくなる問題を修正
* `修正` 入力欄をソフトキーボードの上に維持し, 送信ボタン文字色をテーマ色とのコントラストで選択し, 前へ, 次へ, 閉じる検索操作を統一
* `修正` 生成 listener callback 内から session を close した場合に callback quiescence が自身を待ち続けるデッドロックを修正; 他のスレッドで実行中の callback は引き続き待機
* `改善` プラグイン説明, 使用手順, 10 言語 README を更新し, ホスト `ai.*` ローカルプラグイン経路の正式化に整合
* `改善` ROADMAP を項目ごとにチェック可能な機能ロードマップとして再構成
* `改善` アプリと生成済みローカライズ文書の句読点を ASCII に統一し, パッケージ済み文書と生成文書を対象とする回帰テストを追加
* `改善` 共通の `AiBackend`/`AiTarget`/`AiBackendSession` 層を導入し, ランチャーチャットと Binder provider が `LiteRtLocalBackend` のカタログ, capabilities, セッション作成, streaming, キャンセル経路を共有
* `改善` ローカル `local:*` とオンライン `profile:*` target を Application レベルの統一カタログとディスパッチャーに統合; V1 モデル一覧はローカル専用のままとし, HTTPS 実行 transport の実装まではオンライン target を unavailable として正確に報告

# v1.0.0

###### 2026/08/08

* `機能` Plugin ID と engine が `three-stone-ai`, provider ID が `autojs6.three-stone-ai`, variant が `default` の端末内 AI Provider プロトコル V1 provider
* `機能` System, user, assistant 履歴と credit 制御ストリーミングに対応する CPU-only LiteRT-LM プレーンテキスト生成
* `機能` 8 GiB 上限, 空き容量予約, SHA-256, fsync, 原子的な有効化を備えた `.litertlm` のアプリ専用領域への SAF インポート
* `機能` 単一アクティブセッション, 制限付き I/O, descriptor quota, キャンセル, timeout, 単一終端状態, 同一署名 AutoJs6 呼び出し元検証
* `機能` Reasoning, tools, structured JSON, usage, ネットワーク, credential 機能を明示的に非搭載
* `機能` arm64-v8a, x86_64, universal APK と 10 言語の README, changelog, Android UI, プラグイン説明
* `機能` 完全なモデルカタログとアプリ専用ストレージの使用量を確認でき, モデルファイルをコピーせずに現在のモデルを原子的に切り替えるモデル管理画面
* `改善` `:provider` とのプロセス間競合を避けるため置換インポート後も以前の SHA-256 hash 名モデル世代を保持し, 保持ファイルがアプリ専用ストレージを引き続き使用
* `改善` Activity 再作成中の継続, コールドスタート復旧, stale 一時ファイル cleanup, 現在の試行が作成して未公開の destination だけに限定した削除のため application scope 単一インポート coordinator と fsync 済み pending journal を追加し, 公開済み, current, 履歴 hash 世代を保持
* `依存関係` 端末内 CPU テキスト生成用に LiteRT-LM 0.15.0 を追加
