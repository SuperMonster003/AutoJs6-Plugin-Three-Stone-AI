<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>オンデバイス AI プラグイン. LiteRT-LM によりローカル端末でテキストをストリーミング生成, ネットワーク不要</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 言語

******

現在の README.md は次の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### 概要

******

On-Device AI (オンデバイス AI) は AutoJs6 の公式オンデバイス AI テキスト生成プラグインです. ユーザーがインポートした LiteRT-LM モデルを CPU 上で実行し, プレーンテキストのメッセージ履歴を受け取り, 制御されたストリーミングセッションでプレーンテキストを返します. 推論はすべてローカルで完結し, ネットワークアクセスもデータ送信もありません.

******

### 機能

******

- Android システムピッカーから `.litertlm` モデルパッケージをインポートし, 検証済みコピーをアプリ専用ストレージに保存します.
- システムピッカーを開く前にプライベートストレージを事前確認し, 現在のインポート予算とプライベートコピーの推定使用量を表示し, コピー前に選択ファイルを再確認します.
- プレーンテキストの system, user, assistant 履歴からローカル生成リクエストを作成します.
- AutoJs6 の `ai.ask`, `ai.chat`, `ai.stream` から `temperature`, `topK`, `topP`, `maxTokens` を LiteRT-LM まで渡します.
- モデルの SHA-256 をキーに初期化済み Engine を再利用し, 同じモデルへの連続リクエストで繰り返すコールドスタートをなくします.
- インポートした各モデルを任意で一度初期化し, 「利用可能/互換性なし」の状態を保存して, モデル管理画面から再チェックできます.
- credit バックプレッシャーでテキスト chunk を順番に配信し, 完了, 失敗, キャンセルのいずれか 1 つの終端状態だけを公開します.
- インポート済みモデルの一覧表示, 選択, 名前変更, 未選択モデルの削除, 管理画面からの未参照モデルファイルの回収を行います.
- モデルのダウンロードやリモート推論サービスを使わず, CPU backend で完全に端末内実行します.

******

### モデルとデータ形式

******

バージョン 1 は次のモデルとテキスト範囲だけを宣言します:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### プラグインインターフェース

******

ホストは次の識別情報でプラグインを検出して呼び出します:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1.1
required host build: 5276
```

プラグインは ON_DEVICE 実行と NONE credential モードを宣言します. 宣言する機能は `streaming` のみで, 入出力は `text/plain` のみです.

ホスト build 5276 以降が必要です. リリースには arm64-v8a, x86_64, universal APK variant が含まれます.

******

### ホスト統合状態

******

> AutoJs6 (ビルド 5276 以降) の `ai.ask`, `ai.chat`, `ai.stream` はローカルプラグイン経路に対応. `ai.ask(messages, { plugin: true })` はプレーンテキストの `system`, `user`, `assistant` メッセージを順序どおり保持し, 最後のメッセージは `user` である必要があります. `plugin: true` を渡すと本プラグインが選択され, モデルが 1 つだけの場合はモデル ID を省略可能. `ai.models({ plugin: true })` でインポート済みモデルを列挙できます. プラグイン未インストール, プラグインセンターで無効, モデル未インポートの場合, スクリプトには明確なエラーが通知されます. `plugin: { component, providerId, modelId }` による明示固定も可能です.

******

### セキュリティとプライバシー

******

ネットワーク権限とストレージ権限は要求しません. システムピッカーが許可した URI だけからモデルを読み込み, SHA-256 を計算しながらアプリ専用の `files/models` にコピーし, fsync 後に同じディレクトリの pointer を原子的に置換して有効化します. Provider サービスは AutoJs6 パッケージ名, 呼び出し UID の所有権, 双方の一致する署名も検証します.

******

### 動作制限

******

- モデルのインポートには 8 GiB の厳格な上限があり, 完了後に 256 MiB 以上の空き容量が必要です.
- Application scope の単一インポート coordinator により Activity 再作成中も処理を継続します. Fsync 済み pending journal でコールドスタート復旧と stale な `.incoming`, `.current`, `.pending` 一時ファイルの cleanup を行います. 復旧で削除するのは現在の試行が新規作成し current metadata で一度も公開していない destination だけで, 公開済み, current, 履歴 hash 世代は保持します.
- 独立した `:provider` プロセスとのプロセス間競合を避けるため, インポート中には以前の SHA-256 hash 名モデル世代を自動削除しません. モデル管理画面では未選択の catalog モデルを削除し, catalog から参照されなくなった hash 名ファイルを回収できます.
- プロセス内で同時に有効な生成セッションは 1 つだけです. リクエスト記述子は非同期処理前に複製され, プロトコルの quota に従って閉じられます.
- Provider がキャッシュする初期化済み Engine は最大 1 つです. 同じモデルへの連続リクエストでは再利用し, モデル切り替え時は直ちに, 5 分間のアイドル後は自動的に, Android から明示的なメモリ圧迫通知を受けた場合は有効なセッション終了後に安全に解放します.
- モデルチェックが確認するのは, 現在のデバイスと同梱ランタイムで `Engine.initialize()` が成功することだけです. 出力品質は評価せず, デバイスまたはランタイムの変更後に再チェックできます.
- Provider が宣言するコンテキスト上限は 256 KiB, 出力上限は 64 KiB です. リクエストとモデルはさらに低い上限を設定できます.
- `maxTokens` は 1 から 2,147,483,647 までの整数です. `temperature` は有限かつ 0 以上, `topK` は正の整数, `topP` は 0 から 1 の有限値である必要があります. 3 つの sampling 設定をすべて省略すると model/engine の既定値を維持し, 一部だけ指定すると未指定項目を LiteRT-LM baseline の `topK: 1`, `topP: 0.95`, `temperature: 1` で補完します.
- ストリーミングは有限の credit と制限付き chunk を使い, 無制限なバッファやバックプレッシャーなしの callback を防ぎます.
- キャンセル, セッション終了, timeout は結果公開を停止し, 1 つの終端状態でリクエストを終了します.

******

### 宣言しない機能

******

- Reasoning, tools, structured JSON, usage は宣言しません.
- Tool role メッセージ, tool schema, tool call, tool result は受け付けません.
- ネットワークでのモデル探索, モデルダウンロード, cloud 推論, credential フローはありません.
- GPU または NPU backend は宣言しません. `.litertlm` 拡張子だけでは現在の LiteRT-LM runtime がモデルを読み込める保証にはなりません.

******

### ロードマップ

******

ロードマップは提供可能なユーザー向け機能を単位に構成され, 各項目は個別にチェックと検収が可能です

- [ROADMAP.md を表示](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### リリース履歴

******

# v1.1.0

###### 2026/08/20

* `機能` プラグイン名を On-Device AI (オンデバイス AI) に変更し, AutoJs6 公式オンデバイス AI プラグインとして位置付け
* `機能` AutoJs6 の `ai.ask`/`ai.chat`/`ai.stream` における `plugin: true` 短縮セレクターと `ai.models` モデル列挙に対応
* `機能` On-Device AI プロトコル 1.1 により `temperature`, `topK`, `topP`, `maxTokens` を LiteRT-LM の sampling と出力 token 制御まで伝達
* `改善` プラグイン説明, 使用手順, 10 言語 README を更新し, ホスト `ai.*` ローカルプラグイン経路の正式化に整合
* `改善` ROADMAP を項目ごとにチェック可能な機能ロードマップとして再構成

# v1.0.0

###### 2026/08/08

* `機能` Plugin ID と engine が `on-device-ai`, provider ID が `autojs6.on-device-ai`, variant が `default` の端末内 On-Device AI プロトコル V1 provider
* `機能` System, user, assistant 履歴と credit 制御ストリーミングに対応する CPU-only LiteRT-LM プレーンテキスト生成
* `機能` 8 GiB 上限, 空き容量予約, SHA-256, fsync, 原子的な有効化を備えた `.litertlm` のアプリ専用領域への SAF インポート
* `機能` 単一アクティブセッション, 制限付き I/O, descriptor quota, キャンセル, timeout, 単一終端状態, 同一署名 AutoJs6 呼び出し元検証
* `機能` Reasoning, tools, structured JSON, usage, ネットワーク, credential 機能を明示的に非搭載
* `機能` arm64-v8a, x86_64, universal APK と 10 言語の README, changelog, Android UI, プラグイン説明
* `機能` 完全なモデルカタログとアプリ専用ストレージの使用量を確認でき、モデルファイルをコピーせずに現在のモデルを原子的に切り替えるモデル管理画面
* `改善` `:provider` とのプロセス間競合を避けるため置換インポート後も以前の SHA-256 hash 名モデル世代を保持し, 保持ファイルがアプリ専用ストレージを引き続き使用
* `改善` Activity 再作成中の継続, コールドスタート復旧, stale 一時ファイル cleanup, 現在の試行が作成して未公開の destination だけに限定した削除のため application scope 単一インポート coordinator と fsync 済み pending journal を追加し, 公開済み, current, 履歴 hash 世代を保持
* `依存関係` 端末内 CPU テキスト生成用に LiteRT-LM 0.15.0 を追加

##### その他のリリース

* [CHANGELOG-ja.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-ja.md)

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
on-device-ai-api.aar
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
