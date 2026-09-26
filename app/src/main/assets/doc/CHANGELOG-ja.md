******

### リリース履歴

******

# v1.2.0

###### 2026/09/26

* `ヒント` 未公開の開発候補版です. オンラインツールは AI Provider V2 を使用します. Agent 連携には build 5297+ のホストのネイティブツール仲介機能と対応する Agent が必要です. プラグインは呼び出しをホストへ返し, デバイス操作を直接実行しません.
* `ヒント` 設定 > オンライン AI で構成を編集し, 画像入力に対応するモデルを選択します. 既存の構成は既定で無効です. 画像は選択したサービスにのみ送信されます. LiteRT と永続 ai.session は引き続きテキストのみです. Agent の画面撮影には P9.2 の Agent 統合完了が必要です.
* `機能` オンラインの OpenAI 互換, Anthropic Messages, Gemini GenerateContent でネイティブツール呼び出しに対応. ストリーミング引数, 並列呼び出し, 結果送信後の継続をサポート
* `機能` モデルごとの設定と協商した AI Provider 2.1 によるオンライン JPEG/PNG 入力および画像付きツール結果
* `機能` オンラインのプリセットモデルの自動更新と手動更新, ローカルキャッシュとオフライン時の代替一覧を追加し, 保存済み設定とカスタムモデル ID を維持
* `機能` プリセットモデルを提供元ごとに分類し, OpenRouter の選択肢を Qwen, Kimi, GLM, Grok, Meta, MiniMax などに拡充します. 正確なモデル ID は維持します
* `修正` キャンセルやタイムアウト時に記述子読み取りのスレッドを解放し, 信頼性のあるパイプの生成側エラーを保持
* `改善` 公式カタログに基づきオンラインモデルのプリセットを Claude Fable 5.1 などの現行モデルに更新し, 提供が終了したモデル ID を削除. 既存の設定とカスタムモデルは維持

# v1.1.4

###### 2026/09/19

* `修正` 共有ビルドプラグイン 1.8.3 により, AGP 9.1 での SDK XML v4 解析警告と, JVM 単体テストの組み立て時に APK ネイティブライブラリのアラインメント検証が誤って実行される問題
* `改善` compileSdk に続き targetSdk を 37 (Android 17) に引き上げ, プラグインの動作は新しいターゲットの影響を受けない

# v1.1.3

###### 2026/09/15

* `改善` compileSdk を 37 (Android 17) に引き上げ, targetSdk はターゲット依存の動作を検証するまで 36 のまま

# v1.1.2

###### 2026/09/13

* `修正` ビルド環境の言語にかかわらずプラグインのバージョン日付を英語で表示
* `改善` 多言語リソースの統一, プラグイン有効化の明確化, リリース成果物の検証

# v1.1.1

###### 2026/09/12

* `機能` 選択中のローカルモデルを削除すると残りのモデルを自動選択し, 削除されたモデルを使う会話に案内を表示
* `改善` モデルカタログでインポート済みモデルを示し, ダウンロード確認と保存先の選択を直接提供
* `改善` AutoJs6 に従う設定の概要, テーマ色のコントラスト, 会話履歴の操作を改善
* `改善` 64 ビットのネイティブライブラリの 16 KB ページアラインメントをビルド時に検証, manifest 契約の検査と JSON レポートに対応

# v1.1.0

###### 2026/09/01

* `機能` AutoJs6 公式ローカル AI プラグインのブランドとランタイム識別子を 3-Stone AI に統一
* `機能` プロセス間統合に中立な `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER`, `IAiProvider`/`IAiSession`/`IAiCallback` 識別子を採用し, 置換前の識別子 alias は保持しない
* `機能` 署名権限で保護された export 済み無引数 AI 設定エントリを追加し, AutoJs6 から profile や認証情報を送信せずプラグインの統合設定を起動可能
* `機能` AI Provider V2 のページ分割ターゲットカタログから `local:*` と `profile:*` を直接公開し, 各ターゲットの provider/model/locality, 設定/可用状態, capabilities, limits, controls, HTTPS origins, 正確な `isDefault` マーカーを個別に宣言
* `機能` AI Provider V2 生成リクエストで `temperature`, `topK`, `topP`, `maxTokens` を LiteRT-LM の sampling と出力 token 制御まで伝達
* `機能` LiteRT-LM の正確な入力, 出力, 合計 token 数とプロバイダー実測の生成時間を `ai.chat().usage` とストリーム usage イベントで報告
* `機能` AI Provider V2 の永続セッションと AutoJs6 `ai.session` による複数ターン Conversation 再利用に対応し, 以前の履歴の再送信を不要化
* `機能` AutoJs6 の `structuredJson` と `responseSchema` による LiteRT-LM ネイティブ JSON Schema 制約デコードを追加し, 単発呼び出し, ストリーミング, 永続セッションと完成 JSON の厳密な検証に対応
* `機能` 明示的な `cpu`, `gpu`, `npu` backend profile を AI Provider V2 の任意ターゲット control として提供し, デバイス互換性報告, モデル/profile 単位のキャッシュ分離, 使用不可 profile からのフォールバック禁止に対応; GPU は OpenCL ロード検査成功後のみ宣言し, NPU は EAP runtime 未同梱のため使用不可を維持
* `機能` 固定 LiteRT Community モデルをユーザー選択の SAF 保存先へ直接ダウンロードし, 進捗, 正確なキャンセル, 不完全ファイル削除, LiteRT-LM ヘッダーと正確なサイズ/SHA-256 検証, ダウンロード後の直接インポートに対応
* `機能` ランチャーから開ける会話ワークスペースを追加し, ストリーミング Markdown, 永続履歴, 過去メッセージ編集時の分岐置換警告, 複数結果検索, キーボード対応入力を提供
* `機能` テーマ色, ダークモード, アプリ言語, アプリと開発者情報, リリース履歴のアプリ設定を追加し, 可能な項目では AutoJs6 に従うを既定値に設定
* `機能` フォントサイズ, Enter キー動作, 無制限または任意の output token, モデル既定または任意の `temperature`, `topK`, `topP` を会話設定に追加
* `機能` ストリーミング中にインライン `$\text{...}$` を描画し, 一般的な数式コマンド, 上付き, 下付き表示に対応
* `機能` プラグイン管理の Android Keystore 認証情報ストアを追加し, AES-256-GCM, profile に結び付けた認証済み暗号文, プロセス間で原子的な非公開ファイル, configured 状態のみの照会, 平文の即時消去に対応
* `機能` HTTPS 専用の OpenAI Compatible エンドポイント向けに機密情報を含まない厳格なオンライン profile リポジトリを追加し, canonical UUID, プロセス間で原子的なメタデータ, provider または origin 変更時の認証情報の明示的な置換または消去に対応
* `機能` カスタム baseUrl, 認証情報, モデル profile 用のプラグイン内部 OpenAI Compatible HTTPS 実行 backend を追加し, 上限付き SSE と JSON fallback ストリーミング, 正確なキャンセル, provider usage, 完了ターンのみの永続履歴, JSON Schema リクエスト変換, 機密情報を含まない固定エラーに対応; 設定済み `profile:*` ターゲットから AI Provider V2 経由で直接呼び出し可能
* `機能` ホストのカタログに合わせた OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter のプリセットを追加; 統一オンライン実行層は OpenAI-compatible プロトコルを再利用し, Anthropic Messages と Gemini GenerateContent 固有の認証, リクエスト, SSE 終端, usage, JSON Schema を個別に適応し, プロトコル間またはローカル/オンライン間の fallback は行わない
* `機能` 10 言語のオンラインサービス設定 UI を追加し, profile の追加, 編集, 削除, API キーを表示しない置換と消去, 既定ターゲット選択, 認証情報アクセス前の従量制ネットワーク許可, キャンセル可能な最長 120 秒の明示接続テストに対応; 設定はプロセス間アトミック文書を共有し, V2 ターゲットカタログを動的に更新
* `機能` ランチャーチャットにローカル/クラウド統合ターゲット選択を追加: 各会話は 1 つのターゲットスナップショットを保存し, メッセージのある会話の切り替えでは新規会話を推奨, 既存コンテキストでの続行には明示確認と変更記録が必要
* `機能` 各アシスタント応答に実際の target/provider/model/locality スナップショットを追加; 再生成は記録された応答ターゲットを厳密に再利用し, ID 情報の変更や利用不可時には明示的に失敗し, 現在の会話ターゲットへ暗黙にフォールバックしない
* `機能` ローカルとクラウドの生成失敗を選択済みの境界内に維持; ランチャーチャットは機密情報を含まない限定的な失敗理由を追記し, 境界をまたぐ自動フォールバックがなかったことを明示し, 部分出力を保持したまま手動ターゲット切り替えを提供
* `修正` 実行可能な説明例に設定されていた暗黙の 256 token / 4 KiB 出力上限を削除し, `maxTokens` 省略時はモデルまたは engine の既定値を使用, raw Binder 例は provider の 64 KiB 出力許容量全体を使用するよう修正
* `修正` 10 言語のローカライズ済みプラグイン説明にある低レベル Binder 例を, 最終版 AI Provider V2 リクエストおよびターゲット一覧 API に更新
* `修正` システムのダークモードでもモデル管理画面がライトテーマの文字色を保持し, 本文, チェックボックス, モデル行が暗い背景で読めなくなる問題を修正
* `修正` 入力欄をソフトキーボードの上に維持し, 送信ボタン文字色をテーマ色とのコントラストで選択し, 前へ, 次へ, 閉じる検索操作を統一
* `修正` 生成 listener callback 内から session を close した場合に callback quiescence が自身を待ち続けるデッドロックを修正; 他のスレッドで実行中の callback は引き続き待機
* `修正` Android が信頼済みの `/data/user/0` アプリデータルートを `/data/data` に canonicalize する場合に, アプリ非公開のオンライン profile と認証情報ストレージを誤って拒否する問題を修正; 直接の子リンクと containment 逸脱は引き続き拒否
* `改善` プラグイン説明, 使用手順, 10 言語 README を更新し, ホスト `ai.*` 統一ターゲット経路の正式化に整合
* `改善` ROADMAP を項目ごとにチェック可能な機能ロードマップとして再構成
* `改善` アプリと生成済みローカライズ文書の句読点を ASCII に統一し, パッケージ済み文書と生成文書を対象とする回帰テストを追加
* `改善` 共通の `AiBackend`/`AiTarget`/`AiBackendSession` 層を導入し, ランチャーチャットと Binder provider が `LiteRtLocalBackend` のカタログ, capabilities, セッション作成, streaming, キャンセル経路を共有
* `改善` ローカル `local:*` とオンライン `profile:*` target を Application レベルの統一カタログとディスパッチャーに統合し, AI Provider V2 から両方を直接公開; 現在のカタログから provider locality, credential mode, HTTPS origins を動的に導出し, 認証情報バイトは公開しない
* `改善` README のレイアウトと Gradle プラットフォームのバージョン管理方式を統一
* `改善` 更新ダイアログのリリース履歴ボタンから内蔵のリリース履歴ページを開くように変更

# v1.0.0

###### 2026/08/08

* `機能` Plugin ID と engine が `three-stone-ai`, provider ID が `autojs6.three-stone-ai`, variant が `default` の端末内 AI Provider 基盤
* `機能` System, user, assistant 履歴と credit 制御ストリーミングに対応する CPU-only LiteRT-LM プレーンテキスト生成
* `機能` 8 GiB 上限, 空き容量予約, SHA-256, fsync, 原子的な有効化を備えた `.litertlm` のアプリ専用領域への SAF インポート
* `機能` 単一アクティブセッション, 制限付き I/O, descriptor quota, キャンセル, timeout, 単一終端状態, 同一署名 AutoJs6 呼び出し元検証
* `機能` Reasoning, tools, structured JSON, usage, ネットワーク, credential 機能を明示的に非搭載
* `機能` arm64-v8a, x86_64, universal APK と 10 言語の README, changelog, Android UI, プラグイン説明
* `機能` 完全なモデルカタログとアプリ専用ストレージの使用量を確認でき, モデルファイルをコピーせずに現在のモデルを原子的に切り替えるモデル管理画面
* `改善` `:provider` とのプロセス間競合を避けるため置換インポート後も以前の SHA-256 hash 名モデル世代を保持し, 保持ファイルがアプリ専用ストレージを引き続き使用
* `改善` Activity 再作成中の継続, コールドスタート復旧, stale 一時ファイル cleanup, 現在の試行が作成して未公開の destination だけに限定した削除のため application scope 単一インポート coordinator と fsync 済み pending journal を追加し, 公開済み, current, 履歴 hash 世代を保持
* `依存関係` 端末内 CPU テキスト生成用に LiteRT-LM 0.15.0 を追加
