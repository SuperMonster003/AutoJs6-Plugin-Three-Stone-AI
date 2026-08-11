<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>ローカル AI テキスト生成プラグイン. LiteRT-LM で端末上のプレーンテキストをストリーミング生成</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### 言語

******

現在の README.md は次の言語に対応しています:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- [Français [fr]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-fr.md)
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- 日本語 [ja] # 現在
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### 概要

******

AI Text Generation は AutoJs6 AI Text Generation プロトコル V1 の独立した端末内 provider です. ユーザーがインポートした LiteRT-LM モデルを CPU で実行し, プレーンテキストのメッセージ履歴を受け取り, 制御されたストリーミングセッションでプレーンテキストを返します.

******

### 機能

******

- Android システムピッカーから `.litertlm` モデルパッケージをインポートし, 検証済みコピーをアプリ専用ストレージに保存します.
- プレーンテキストの system, user, assistant 履歴からローカル生成リクエストを作成します.
- credit バックプレッシャーでテキスト chunk を順番に配信し, 完了, 失敗, キャンセルのいずれか 1 つの終端状態だけを公開します.
- 現在インポートされているモデルを一覧し, 置換後に新しいモデル一覧 generation を公開します.
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
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

プラグインは ON_DEVICE 実行と NONE credential モードを宣言します. 宣言する機能は `streaming` のみで, 入出力は `text/plain` のみです.

ホスト build 5270 以降が必要です. リリースには arm64-v8a, x86_64, universal APK variant が含まれます.

******

### ホスト統合状態

******

> AutoJs6 は現在, `ai.ask(..., { plugin: ... })` と `ai.stream(..., { plugin: ... })` の明示的な public production route を提供し, exact component/provider/model を固定して cloud fallback しません. 実際の release plugin/model による one-shot ask は QV710AF65F (API 31, arm64-v8a) で通過し, 同じ端末で deterministic fake provider の public stream smoke も initial 8-credit window を越える 8 個超の chunks から自然完了しました. plugin のインストールだけでは legacy `ai.*` は切り替わらず, script は明示 plugin selector と import 済み modelId を指定する必要があります. 旧 JavaAdapter/raw Binder サンプル, clipboard, UI, `chat`, real-model streaming, device active cancel は未検証です.

******

### セキュリティとプライバシー

******

ネットワーク権限とストレージ権限は要求しません. システムピッカーが許可した URI だけからモデルを読み込み, SHA-256 を計算しながらアプリ専用の `files/models` にコピーし, fsync 後に同じディレクトリの pointer を原子的に置換して有効化します. Provider サービスは AutoJs6 パッケージ名, 呼び出し UID の所有権, 双方の一致する署名も検証します.

******

### 動作制限

******

- モデルのインポートには 8 GiB の厳格な上限があり, 完了後に 256 MiB 以上の空き容量が必要です.
- Application scope の単一インポート coordinator により Activity 再作成中も処理を継続します. Fsync 済み pending journal でコールドスタート復旧と stale な `.incoming`, `.current`, `.pending` 一時ファイルの cleanup を行います. 復旧で削除するのは現在の試行が新規作成し current metadata で一度も公開していない destination だけで, 公開済み, current, 履歴 hash 世代は保持します.
- 独立した `:provider` プロセスとのプロセス間競合を避けるため, 置換インポート後も以前の SHA-256 hash 名モデル世代を保持します. これらのファイルはアプリ専用ストレージを引き続き使用します.
- プロセス内で同時に有効な生成セッションは 1 つだけです. リクエスト記述子は非同期処理前に複製され, プロトコルの quota に従って閉じられます.
- Provider が宣言するコンテキスト上限は 256 KiB, 出力上限は 64 KiB です. リクエストとモデルはさらに低い上限を設定できます.
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

`R0` は引き続き進行中です. 2026-08-10 の build gate は 32 tests/0 failures, lint 0 error, Debug/Release `BUILD SUCCESSFUL` (3m37s) で通過し, `VERSION_BUILD`/`BUILD_TIME` も変更されませんでした; 実 plugin/model の public `ai.ask` device smoke は通過済みですが, clipboard 書き込みと Activity 再作成後のコピーは未検証です. `R1` は本番 call site のないデフォルト無効かつ未接続のホストスライスを 5 つ網羅します: 読み取り専用 PackageManager `exact-action discovery`/`exact-component reinspection`, 明示コンポーネント限定の metadata-only Binder bind, transport 非依存の model-list transcript policy/catalog, および `IAiModelListCallback` Binder transport を備えた Android model-list coordinator. 従来の metadata handshake は到達した ID 境界を再検査し, absolute deadline の期限切れ時にも interface descriptor, `getProviderInfo()`, `getCapabilities()` の同期 Binder RPC が実行中の場合に限り fuse します. model-list policy は有界な単一/複数ページ結果と provider error を厳格に decode し, generation の変化, token replay/cycle, model ID の重複, capability mismatch, stale/duplicate callback を拒否して, 競合する terminal state の勝者を 1 つに限定します. coordinator は listing 初期化時に限り descriptor 検証済みの同じ Binder 上で provider metadata を再検証し, その後 initial/continuation の各 dispatch 前に `AiTextProviderPackageSnapshot.samePackageIdentityAs` で exact package/component identity を再検査します. 各 callback は唯一の payload copy より前に calling UID, typed page/error envelope size, 有界な flood slot を検証します; 有界な page-token ledger は完全で安定した pinned identity ごとに分離されます. 従来の handshake と異なり, coordinator は exact PackageManager inspect, bind, prepare, dispatch, callback admission を含む, admission 済みのあらゆる `operationsInFlight` に watchdog を保持し, deadline までに unwind しなければ exact component を fuse します. どちらの fuse も停止した operation を強制中断できず, coordinator gate は遅延 unwind まで `BUSY` のままです. 5 番目はデフォルト無効・未接続・transport-independent の `AiTextProviderSessionPolicy` です: plan/request/provider/model/context を固定し, 同期 `openSession` が返る前の callback を明示的 commit gate の後ろに保持し, UID, typed envelope 上限, descriptor ownership の順に検証し, initial 8 credits と chunk ごとの有界 backpressure を自動管理します. started/chunk/usage/completed/failed/cancelled を有界に受理し, provider completion, cancel, timeout, death のうち terminal winner を 1 つに限定し, descriptor, remote control, cleanup がすべて settled してから terminal を公開し, tools は fail-closed で拒否します. Android `IAiTextCallback`/`openSession`/PFD adapter, session dispatch 前の package reinspection, 実際の session dispatch, runtime/UI 統合, `ai.*` routing は含みません. 証拠には source/static と focused JVM Gradle が含まれます: standalone Kotlin 2.3.21 K2/JDK 21/JVM 17 compile, 40/40 JUnit, 同一 artifact の 30 runs で 1200/1200; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` は exit 0 で, XML は 40 tests/0 skipped/0 failures/0 errors を記録し, Kotlin daemon retry 後も fallback compile が成功しました. Android `IAiTextCallback`/`openSession`/PFD adapter, ADB/device, runtime/UI, `ai.*` の証拠は依然なく, 広義 R1 item と exit gate は未チェックです. 2026-08-10 の isolated AutoJs6 Gradle gate は coordinator 15/0 を通過し, version metadata を変えずに host Debug, androidTest, fake APK を assemble しました. QV710AF65F (API 31, arm64-v8a) の metadata/model-list は positive PARTIAL として各 `OK (1 test)` で, `pageSize=1` により 4 ページ/4 fake model を収集しました; signer/hash, 前後 identity, 非 main callback, 唯一 terminal は host evidence に詳記されています. 3 package は install 前に存在せず, cleanup 後も不存在へ復元されました. この証拠は narrow R1 item のみを支持し, 広義 item と exit gate は未チェックです. QV710AF65F に実 plugin/model はなく, R0 は未完了です. `R2` から `R8` は引き続き計画項目です. 6 番目の narrow slice は、デフォルト無効・未接続の Android exact-component session coordinator と `IAiTextCallback`/PFD transport です. standalone K2 は 18/18、同一 artifact の 30 rounds は 540/540、focused Gradle は 18 tests/0 failures を記録し、3 APK の assemble も成功しました. QV710AF65F (API 31, arm64-v8a) では 2 methods がそれぞれ `OK (1 test)` となり、request reliable-pipe PFD、initial 8 credits をまたぐ約 18 chunks、descriptor exact/short/trailing/reliable producer error、idempotent close を確認しました. 証拠は `PARTIAL` に限定されます: callback completion/tool PFD の cross-process、wrong UID、hostile death/update、実 plugin/model、runtime/UI/`ai.*` は未検証であり、広義 R1 item と exit gate は未チェックのままです. その後、チェック済みの narrow hostile Android session conformance slice が追加され、isolated H1 commits `edd10008f`/`06ebc788c` と統合 commits `0cbc19d9f`/`72eb4d0c0` に記録されました. focused Gradle は coordinator 18/0 と fake provider 31/0 を通過し、3 APK の assemble にも成功しました. QV710AF65F (API 31, arm64-v8a) では 7 つの exact instrumentation method がそれぞれ `OK (1 test)` となりました: ordinary-pipe completion callback で cross-process PFD ownership transfer、exact length/EOF、SHA-256、UTF-8 materialization、cleanup を確認し、tool PFD は ownership 取得後に `TOOLS_UNSUPPORTED` で一度だけ拒否され、chunk-before-start、sequence-gap、invalid descriptor reference は fail-closed となり、duplicate terminal は bounded single-terminal smoke に限定され、stall は `Started` 後の host cancel と owner/gate reuse を確認しました. この `[x]` は narrow evidence のままです: cross-process reliable-pipe status、wrong UID、no-credit、provider death、package update/uninstall、実 plugin/model、runtime/UI、`ai.*` は未検証です. 広義 R1 item と exit gate は未チェックのままです. チェック状態はプロジェクトロードマップを参照してください. 追加のチェック済み H2 lifecycle narrow slice は integrated commits `e5bd92b16`/`10dad3e39`/`0b9a94742` に記録されています. focused Gradle は coordinator 18/0 と fake provider 31/0 を通過し, 3 APK の assemble も成功しました. QV710AF65F (API 31, arm64-v8a) では `callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner` と `providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner` がそれぞれ `OK (1 test)` を返しました: 前者は transcript 公開前に isolated-process callback を唯一の `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH` terminal として拒否し, owner/gate を再利用します; 後者は `Started`, 正常な sequence 0 chunk, credit replenishment acknowledgement の後に provider process を終了し, host は唯一の `BinderDied` を公開して owner/gate を再利用します. local/device SHA-256 は host `0685CE99C0F9E8F9056BE5F3A8EEBC2C7EA5FFCA2D422E21EAC69D0CB3364629`, androidTest `7C91A0AF651E2098AD124FF8A89AE3AC3018E0F1D0DEC068367595E964739178`, fake provider `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18` で完全一致し, 3 つの v2 signer certificate SHA-256 はすべて `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8` でした. host, androidTest, fake package は install 前も cleanup 後も absent でした. この Android evidence は wrong-UID/provider-death の 2 方法だけを対象とする PARTIAL です. no-credit は JVM-only のままで, update/uninstall 形状も final-reinspection JVM case (`lastUpdateTime` drift と `Completed(emptyList())`) のみであり, device package mutation は実施していません. cross-process no-credit, 実 update/uninstall, 実 plugin/model, runtime/UI, `ai.*` は未検証で, 広義 R1 item と exit gate は未チェックのままです. 追加のチェック済み狭域スライスは AutoJs6 メインリポジトリの commits `e4297a688`/`64db31ea5` に記録され、明示的な `ai.ask(..., { plugin: ... })` production source route を接続した。selector は exact component/provider/model を厳密に固定し、`plugin` がない場合は従来の cloud 動作を維持する一方、存在する場合は vault を読まず、HTTP/cloud に入らず、fallback もしない。現在は単一の plain-text user message と non-stream request のみを受け付け、engine close を remote session teardown へ伝播する。standalone K2 は 15/15、focused Gradle は 19/19、Android main compile も成功したが、いずれも source-only 証拠である。実 plugin/model/device の実行、UI、`chat`、`stream` route は含まず、広義の R1 項目と exit gate は未チェックのままである。 2026-08-11, 検証した APK と実機実行の直接ソースは隔離ツリー commit `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd` である; 同じ test blob は後に現行 main parent 上の `7ce26ceea` として統合され app tree は同一だが, 後者は APK の直接 build source ではない. QV710AF65F (API 31, arm64-v8a) で実際の Rhino global `ai.ask(..., { plugin: ... })` one-shot を release plugin と 2,583,085,056-byte LiteRT-LM により実行した. model SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42` から modelId `litertlm.ab7838cdfc8f77e54d8ca45eadceb204` を導出し, `AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin` は 7.071s で `OK (1 test)` を返した. local/device APK SHA-256 は host `b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`, androidTest `09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf`, plugin `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb` で完全一致し, 3 者の v2 signer certificate SHA-256 は `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213` で同一、device base も候補 APK と一致した. plugin test/lint/Debug/Release gate と host focused 19/19 tests/assemble は通過し, users 0/10 の exact host/test/plugin package および専用 staging/UI-dump path は前後とも absent だった. これは R0 raw Binder/JavaAdapter サンプル baseline 項目をチェックせず, R6 の限定的な optional real-model smoke/local-entry gate のみをチェックするが, single-device/single-model/non-stream one-shot/appDebug host の証拠に限られる. clipboard, streaming, chat, tools, structured output, usage, release-host/API matrix, 実機 update/uninstall, performance, soak は対象外である. DocumentsUI last-location state は無損に復元できなかった. 広義の R1 項目と exit gate は未チェックのままである. AutoJs6 commits `2ea3360a2`/`50b43d00c` は, さらに狭い checked PARTIAL public plugin stream slice を記録します. focused Gradle は 25/25, QV710AF65F (API 31, arm64-v8a) の exact method `AiTextPluginPublicStreamSmokeTest#publicRhinoAiStreamCompletesThroughExactFakeProvider` は 1.645s で `OK (1 test)` でした. fake provider (`fake.local`/`fake.stream`) は initial 8 credits を越えて 8 個超の chunks を発行し, owned Rhino execution は自然完了しました. public cancel と engine teardown は JVM-only で, real-model streaming, device active cancel, release/API matrix は未検証です. broad R1 items と exit gates は未チェックのままです. さらに、prompt-only の明示的 plugin `ai.chat` production route を、チェック済みの狭い R1 スライスとして記録しました。隔離 production/test commits は `d480b6918`/`b0aa6768484d6046551264f69ecc84b527bbb442`、後続 integration commits は `2d256b99f`/`c35f199b8` で、focused Gradle は 14/14（3+4+7）に合格しました。Gradle、3 APK、端末試験の実際の tested source は `b0aa6768484d6046551264f69ecc84b527bbb442` であり integration commits ではありません。途中の DEX commits はパスが重複せず、この provenance を変更しません。QV710AF65F（API 31、arm64-v8a）では exact `AiTextPluginPublicChatSmokeTest#publicRhinoAiChatReturnsNormalizedResponseThroughExactFakeProvider` が `fake.local`/`fake.echo` 経由で `OK (1 test)`、`Time: 1.54`、code `-1` を返し、Promise は自然完了して prompt を完全に echo し、`text`、`reasoning`、`toolCalls`、`usage`、`finishReason`、`message`、`error`、`raw`、`profile`、`route`、`provider`、`model` の正確な 12-key set を返しました。host/androidTest/fake APK の SHA-256 はそれぞれ `2D3DFB9C16AD91CCB73C6A969DB7DCC9B10046DF14F716C82D9E61F07FAAF1D3`、`9CAA1CC6936D98C380C9C9FFE81178A0E81E667207DA9623FA95C19BF3A11166`、`6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`、共通 v2 証明書 SHA-256 は `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8` です。cleanup 後、users 0/10 の3パッケージと予約済み9パスは再び absent でした。これは fake-provider natural-completion の PARTIAL 証拠だけです。real model、device failure/cancel、API matrix は未試験で、public cancel/engine close は JVM 証拠のみです。広義 R1/exit、R0、R6 は変更しません。

- [ROADMAP.md を表示](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### リリース履歴

******

# v1.0.0

###### 2026/08/08

* `機能` Plugin ID と engine が `ai-text-generation`, provider ID が `autojs6.local.text`, variant が `default` の端末内 AI Text Generation プロトコル V1 provider
* `機能` System, user, assistant 履歴と credit 制御ストリーミングに対応する CPU-only LiteRT-LM プレーンテキスト生成
* `機能` 8 GiB 上限, 空き容量予約, SHA-256, fsync, 原子的な有効化を備えた `.litertlm` のアプリ専用領域への SAF インポート
* `機能` 単一アクティブセッション, 制限付き I/O, descriptor quota, キャンセル, timeout, 単一終端状態, 同一署名 AutoJs6 呼び出し元検証
* `機能` Reasoning, tools, structured JSON, usage, ネットワーク, credential 機能を明示的に非搭載
* `機能` arm64-v8a, x86_64, universal APK と 10 言語の README, changelog, Android UI, プラグイン説明
* `改善` `:provider` とのプロセス間競合を避けるため置換インポート後も以前の SHA-256 hash 名モデル世代を保持し, 保持ファイルがアプリ専用ストレージを引き続き使用
* `改善` Activity 再作成中の継続, コールドスタート復旧, stale 一時ファイル cleanup, 現在の試行が作成して未公開の destination だけに限定した削除のため application scope 単一インポート coordinator と fsync 済み pending journal を追加し, 公開済み, current, 履歴 hash 世代を保持
* `依存関係` 端末内 CPU テキスト生成用に LiteRT-LM 0.15.0 を追加

##### その他のリリース

* [CHANGELOG-ja.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-ja.md)

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
ai-text-generation-api.aar
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
