******

### リリース履歴

******

# v1.1.0

###### 2026/08/21

* `機能` プラグイン名を On-Device AI (オンデバイス AI) に変更し, AutoJs6 公式オンデバイス AI プラグインとして位置付け
* `機能` AutoJs6 の `ai.ask`/`ai.chat`/`ai.stream` における `plugin: true` 短縮セレクターと `ai.models` モデル列挙に対応
* `機能` On-Device AI プロトコル 1.1 により `temperature`, `topK`, `topP`, `maxTokens` を LiteRT-LM の sampling と出力 token 制御まで伝達
* `機能` LiteRT-LM の正確な入力, 出力, 合計 token 数とプロバイダー実測の生成時間を `ai.chat().usage` とストリーム usage イベントで報告
* `機能` On-Device AI プロトコル 1.2 の永続セッションと AutoJs6 `ai.session` による複数ターン Conversation 再利用に対応し, 以前の履歴の再送信を不要化
* `機能` AutoJs6 の `structuredJson` と `responseSchema` による LiteRT-LM ネイティブ JSON Schema 制約デコードを追加し, 単発呼び出し, ストリーミング, 永続セッションと完成 JSON の厳密な検証に対応
* `機能` プロトコル 1.3 と AutoJs6 生成オプションで明示的な `cpu`, `gpu`, `npu` backend profile を提供し, デバイス互換性報告, モデル/profile 単位のキャッシュ分離, 使用不可 profile からのフォールバック禁止に対応; GPU は OpenCL ロード検査成功後のみ宣言し, NPU は EAP runtime 未同梱のため使用不可を維持
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
