<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="three-stone-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>統合 AI プラグイン. LiteRT-LM は常にローカルで, オンラインターゲットは必ず明示選択</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 言語

******

現在の README.md は次の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### 概要

******

3-Stone AI は AutoJs6 の公式 AI テキスト生成プラグインです. ユーザーがインポートした LiteRT-LM モデルを明示選択した CPU または互換 GPU backend 上で実行し, プレーンテキストのメッセージ履歴を受け取り, 制御されたストリーミングセッションでプレーンテキストまたは schema によって制約された JSON テキストを返します. 現在の AI Provider V1 ホスト呼び出しはこれらのローカルターゲットを使用し, ネットワークアクセスもデータ送信も行いません. プラグイン設定では OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter とカスタム OpenAI Compatible profile, 暗号化認証情報, 既定オンラインターゲット, 従量制ネットワーク, 明示接続テストを管理できます. ランチャーチャットは各会話をローカルまたは設定済みオンラインターゲットへ明示的に固定でき, AI Provider V1 ホスト API は引き続きローカル専用です.

******

### 機能

******

- Android システムピッカーから `.litertlm` モデルパッケージをインポートし, 検証済みコピーをアプリ専用ストレージに保存します.
- 固定バージョンで認証不要の LiteRT Community モデルをユーザー選択の SAF 保存先へ直接ダウンロードし, 進捗, キャンセル, 不完全ファイルの削除, 正確なサイズと SHA-256 検証を行います.
- システムピッカーを開く前にプライベートストレージを事前確認し, 現在のインポート予算とプライベートコピーの推定使用量を表示し, コピー前に選択ファイルを再確認します.
- プレーンテキストの system, user, assistant 履歴からローカル生成リクエストを作成します.
- AutoJs6 の `ai.ask`, `ai.chat`, `ai.stream` から `temperature`, `topK`, `topP`, `maxTokens` を LiteRT-LM まで渡します.
- AutoJs6 の `structuredJson` と `responseSchema` により LiteRT-LM ネイティブ JSON Schema 制約デコードを使用し, 完成した値は `JSON.parse` 用の JSON テキストとして返します.
- LiteRT-LM の正確な入力, 出力, 合計 token 数とプロバイダー側の生成時間を, AutoJs6 の `ai.chat().usage` とストリーム usage イベントで返します.
- AutoJs6 の `ai.session` で 1 つの LiteRT-LM ネイティブ Conversation に複数ターンのコンテキストを保持し, 2 ターン目以降は新しいユーザープロンプトだけを送信します.
- モデルの SHA-256 をキーに初期化済み Engine を再利用し, 同じモデルへの連続リクエストで繰り返すコールドスタートをなくします.
- インポートした各モデルを任意で一度初期化し, "利用可能/互換性なし"の状態を保存して, モデル管理画面から再チェックできます.
- credit バックプレッシャーでテキスト chunk を順番に配信し, 完了, 失敗, キャンセルのいずれか 1 つの終端状態だけを公開します.
- インポート済みモデルの一覧表示, 選択, 名前変更, 未選択モデルの削除, 管理画面からの未参照モデルファイルの回収を行います.
- AutoJs6 から `cpu`, `gpu`, `npu` backend を明示選択できます. CPU が既定で, GPU は OpenCL 読み込み検査の成功時だけ公開し, NPU は EAP runtime 未同梱のため利用不可と明示します.
- アプリ設定で組み込みおよびカスタムのオンライン profile, Android Keystore 認証情報, 既定オンラインターゲット, 従量制ネットワーク, 明示的で上限付きの接続テストを管理します.
- 各ランチャー会話をローカルまたはオンラインターゲットのスナップショットへ固定します. メッセージのある会話で変更すると新規会話を推奨し, 続行には明示確認と変更記録が必要です.
- 各アシスタント応答に実際の target/provider/model/locality スナップショットを保存します. 再生成は記録された元のターゲットを厳密に再利用し, ID 情報の変更や利用不可時には明示的に失敗し, 現在の会話ターゲットへ暗黙にフォールバックしません.
- ローカルとクラウドの生成失敗を選択済みの境界内に維持します. ランチャーチャットは機密情報を含まない限定的な失敗理由を追記し, 境界をまたぐ自動フォールバックがなかったことを明示し, 部分出力を保持したまま手動ターゲット切り替えを提供します.

******

### モデルとデータ形式

******

バージョン 1 は次のモデルとテキスト範囲だけを宣言します:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### プラグインインターフェース

******

ホストは次の識別情報でプラグインを検出して呼び出します:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

公開 AI Provider V1 サーフェスは ON_DEVICE 実行と NONE credential モードを宣言します. `streaming`, `usage`, `persistent-session`, `structured-json` 機能を宣言し, `text/plain` メッセージと `application/json` 応答 schema を受け取り, `text/plain` または `application/json` テキストを出力します. プロトコル 1.3 は明示 backend profile と端末別可用性を追加し, CPU への暗黙フォールバックを禁止します. プラグイン内部の REMOTE ターゲットは V1 では公開されません.

ホスト build 5276 以降が必要です. リリースには arm64-v8a, x86_64, universal APK variant が含まれます.

******

### ホスト統合状態

******

> AutoJs6 (ビルド 5276 以降) の `ai.ask`, `ai.chat`, `ai.stream` はローカルプラグイン経路に対応. `ai.session({ plugin: true })` は永続的な複数ターン Conversation を作成し, その後の `ask`, `chat`, `stream` は新しいユーザープロンプトだけを送信します. `ai.ask(messages, { plugin: true })` はプレーンテキストの `system`, `user`, `assistant` メッセージを順序どおり保持し, 最後のメッセージは `user` である必要があります. `ai.chat` は正確な token 数を `usage` に, 実測生成時間を `usage.raw.durationMillis` に返し, `ai.stream` は完了前に同じ累積 usage を送信します. `plugin: true` を渡すと本プラグインが選択され, モデルが 1 つだけの場合はモデル ID を省略可能. `ai.models({ plugin: true })` でモデルと `backendProfiles` を列挙できます. 生成は `backend: 'cpu' | 'gpu' | 'npu'` を受け付け, 利用不可 profile は明示エラーとなり CPU へ戻りません. プラグイン未インストール, プラグインセンターで無効, モデル未インポートの場合, スクリプトには明確なエラーが通知されます. `plugin: { component, providerId, modelId }` による明示固定も可能です. `responseSchema` は構造化出力を暗黙に有効化し, schema なしの `structuredJson: true` は既定の object-root schema を使用します. `ai.ask` と `ai.chat().text` は引き続き JSON テキストを返し, ストリーム delta は部分的な JSON テキストです. 永続セッションでは全ターンで 1 つの固定 schema と backend を使用します.

******

### セキュリティとプライバシー

******

ユーザーが開始する推奨モデルのダウンロードとユーザー設定 online ターゲットへのリクエストに `INTERNET` 権限を要求しますが, 現在の V1 ローカル生成はネットワークを使用しません. 広範なストレージ権限は要求しません. ダウンロードは不変 HTTPS リビジョン, 固定サイズと SHA-256 を使い, 選択された SAF 保存先だけへ書き込みます; LiteRT-LM ヘッダー, サイズ, ダイジェスト, flush, fsync がすべて成功するまで完了扱いにしません. インポートは引き続きピッカー URI のみを読み, 検証済みコピーを `files/models` に書いて原子的に有効化します. Provider は AutoJs6 パッケージ, UID, 署名も検証します.

******

### 動作制限

******

- モデルのインポートには 8 GiB の厳格な上限があり, 完了後に 256 MiB 以上の空き容量が必要です.
- アプリプロセス内のダウンロードは 1 件だけです. Activity 再作成後も進捗とキャンセル権限を保持し, キャンセルまたは失敗時は保存先を削除または空にします. プロセス終了時には外部の不完全ファイルが残る場合があり, 手動削除が必要です.
- Application scope の単一インポート coordinator により Activity 再作成中も処理を継続します. Fsync 済み pending journal でコールドスタート復旧と stale な `.incoming`, `.current`, `.pending` 一時ファイルの cleanup を行います. 復旧で削除するのは現在の試行が新規作成し current metadata で一度も公開していない destination だけで, 公開済み, current, 履歴 hash 世代は保持します.
- 独立した `:provider` プロセスとのプロセス間競合を避けるため, インポート中には以前の SHA-256 hash 名モデル世代を自動削除しません. モデル管理画面では未選択の catalog モデルを削除し, catalog から参照されなくなった hash 名ファイルを回収できます.
- プロセス内で同時に有効な生成セッションは 1 つだけです. リクエスト記述子は非同期処理前に複製され, プロトコルの quota に従って閉じられます.
- Provider はモデル SHA-256 と backend profile の組をキーに初期化済み Engine を最大 1 つキャッシュします. 同じ組は再利用し, いずれかのキー変更, 5 分間のアイドル, または明示的なメモリ圧迫時に安全に解放します.
- モデルチェックが確認するのは, 現在のデバイスと同梱ランタイムで `Engine.initialize()` が成功することだけです. 出力品質は評価せず, デバイスまたはランタイムの変更後に再チェックできます.
- Provider が宣言するコンテキスト上限は 256 KiB, 出力上限は 64 KiB です. リクエストとモデルはさらに低い上限を設定できます.
- 応答 schema は 64 KiB 以下の JSON object である必要があります. 対応 keyword は同梱 LiteRT-LM/LLGuidance ランタイムの実装に従います. 完成出力は厳密に parse と検証を行うため, JSON 値全体に十分な `maxTokens` を確保してください.
- `maxTokens` は 1 から 2,147,483,647 までの整数です. 省略すると出力 token 数は model/engine の既定値に委ねられますが, provider の 64 KiB 出力安全上限は維持されます. `temperature` は有限かつ 0 以上, `topK` は正の整数, `topP` は 0 から 1 の有限値である必要があります. 3 つの sampling 設定をすべて省略すると model/engine の既定値を維持し, 一部だけ指定すると未指定項目を LiteRT-LM baseline の `topK: 1`, `topP: 0.95`, `temperature: 1` で補完します.
- ストリーミングは有限の credit と制限付き chunk を使い, 無制限なバッファやバックプレッシャーなしの callback を防ぎます.
- Usage token 数は LiteRT-LM Conversation の KV cache と decode カウンターから直接取得し, 文字数による推定は行いません. `durationMillis` はプラグイン生成呼び出しのみを測定し, ホストの探索, バインド, モデル列挙, ディスパッチ時間を含みません.
- 永続的な `ai.session` は 1 つのアクティブターンだけを許可し, 正常完了後もネイティブ Conversation を保持します. キャンセル, timeout, 生成失敗, 明示的な終了の後は再作成が必要です.
- キャンセル, セッション終了, timeout は結果公開を停止し, 1 つの終端状態でリクエストを終了します.

******

### 宣言しない機能

******

- Reasoning と tools は宣言しません.
- Tool role メッセージ, tool schema, tool call, tool result は受け付けません.
- ネットワークでのモデル探索や任意 URL からのモデルダウンロードはありません. ランチャーチャットに表示されるのはインポート済みローカルモデルと明示設定されたオンライン profile のみです; AI Provider V1 ホストルーティングはローカル専用のままで, ダウンロードできるのは固定された内蔵推奨カタログだけです.
- NPU 推論は宣言しません. profile は `npu-runtime-not-packaged` 理由付きの `unavailable` として確認できます. GPU は `libOpenCL.so` を読み込める場合だけ宣言され, `.litertlm` 拡張子だけではモデル初期化を保証しません.

******

### ロードマップ

******

ロードマップは提供可能なユーザー向け機能を単位に構成され, 各項目は個別にチェックと検収が可能です

- [ROADMAP.md を表示](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

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
* `機能` ホストのカタログに合わせた OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter のプリセットを追加; 統一オンライン実行層は OpenAI-compatible プロトコルを再利用し, Anthropic Messages と Gemini GenerateContent 固有の認証, リクエスト, SSE 終端, usage, JSON Schema を個別に適応し, プロトコル間またはローカル/オンライン間の fallback は行わない
* `機能` 10 言語のオンラインサービス設定 UI を追加し, profile の追加, 編集, 削除, API キーを表示しない置換と消去, 既定ターゲット選択, 認証情報アクセス前の従量制ネットワーク許可, キャンセル可能な最長 120 秒の明示接続テストに対応; 設定はプロセス間アトミック文書を共有し, AI Provider V1 はローカル限定のまま
* `機能` ランチャーチャットにローカル/クラウド統合ターゲット選択を追加: 各会話は 1 つのターゲットスナップショットを保存し, メッセージのある会話の切り替えでは新規会話を推奨, 既存コンテキストでの続行には明示確認と変更記録が必要
* `機能` 各アシスタント応答に実際の target/provider/model/locality スナップショットを追加; 再生成は記録された応答ターゲットを厳密に再利用し, ID 情報の変更や利用不可時には明示的に失敗し, 現在の会話ターゲットへ暗黙にフォールバックしない
* `機能` ローカルとクラウドの生成失敗を選択済みの境界内に維持; ランチャーチャットは機密情報を含まない限定的な失敗理由を追記し, 境界をまたぐ自動フォールバックがなかったことを明示し, 部分出力を保持したまま手動ターゲット切り替えを提供
* `修正` 実行可能な説明例に設定されていた暗黙の 256 token / 4 KiB 出力上限を削除し, `maxTokens` 省略時はモデルまたは engine の既定値を使用, raw Binder 例は provider の 64 KiB 出力許容量全体を使用するよう修正
* `修正` 10 言語のローカライズ済みプラグイン説明にある低レベル Binder 例が, プロトコル 1.1 の 14 引数 `AiGenerationOptions` コンストラクターを呼び続け, プロトコル 1.3 API で失敗する問題を修正
* `修正` システムのダークモードでもモデル管理画面がライトテーマの文字色を保持し, 本文, チェックボックス, モデル行が暗い背景で読めなくなる問題を修正
* `修正` 入力欄をソフトキーボードの上に維持し, 送信ボタン文字色をテーマ色とのコントラストで選択し, 前へ, 次へ, 閉じる検索操作を統一
* `修正` 生成 listener callback 内から session を close した場合に callback quiescence が自身を待ち続けるデッドロックを修正; 他のスレッドで実行中の callback は引き続き待機
* `修正` Android が信頼済みの `/data/user/0` アプリデータルートを `/data/data` に canonicalize する場合に, アプリ非公開のオンライン profile と認証情報ストレージを誤って拒否する問題を修正; 直接の子リンクと containment 逸脱は引き続き拒否
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

##### その他のリリース

* [CHANGELOG-ja.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-ja.md)

******

### ビルド

******

```powershell
.\gradlew.bat :app:assembleDebug
```

リリースビルド:

```powershell
.\gradlew.bat :app:assembleRelease
```

ビルド設定は `version.properties` から取得します. 現在の最小 SDK は 24, ターゲット SDK は 36 で, JDK 21 以降が必要です.

プロトコル ABI は `libs` にあるリポジトリローカル AAR から提供されます:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-provider-api.aar
```

Runtime は Maven の LiteRT-LM 0.15.0 を使用します. リリースビルドは LiteRT-LM runtime クラスを保持し, 2 個の ABI APK と 1 個の universal APK を生成します.

******

### ライセンス

******

プロジェクトのソースコードは MPL-2.0 です. LiteRT-LM とその他のサードパーティコンポーネントには各ライセンスが引き続き適用されます.

******

### リソース構成

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` は JSON ソースから 10 言語の README とアプリ内 changelog を生成します. Android 文字列は各リソースディレクトリで管理されます.

******

### リンク

******

- AutoJs6 ドキュメント: https://docs.autojs6.com
- LiteRT-LM プロジェクト: https://github.com/google-ai-edge/LiteRT-LM
