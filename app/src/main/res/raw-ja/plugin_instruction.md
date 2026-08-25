# AutoJs6 3-Stone AI

このプラグインは Android Storage Access Framework (SAF) からローカル `.litertlm` モデルパッケージをインポートし, アプリ専用ストレージへコピーします. CPU-only LiteRT-LM でプレーンテキスト履歴を処理し, プレーンテキストまたは schema 制約付き JSON テキストをストリーミング出力します.

AutoJs6 ホスト build 5276 以降と Android API 24 以降が必要です.

## モデルを取得

モデル管理画面で **推奨モデルをダウンロード** をタップし, 固定された LiteRT Community モデルと書き込み可能な SAF 保存先を選択します. 進捗を表示し, LiteRT-LM ヘッダー, 正確なバイト数, SHA-256 を検証してから **ダウンロード済みモデルをインポート** を有効にします. 推論はネットワークを使用しません. 外部ファイルとインポート後のアプリ専用コピーは別々に容量を使います; ダウンロード中に Android がプロセスを終了した場合は, 不完全な外部ドキュメントを手動で削除してください.

## クイックスタート (推奨)

AutoJs6 ビルド 5276 以降では, グローバル `ai` モジュールから本プラグインを直接呼び出せます. `plugin: true` で本プラグインを選択し, モデルが 1 つだけの場合はモデル ID を省略できます.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

複数ターンのコンテキストを保持するには, `ai.session` で永続 Conversation を作成します. 2 ターン目以降は新しいユーザープロンプトだけを送信します:

```javascript
ai.session({
    plugin: true,
    system: "Keep every answer short.",
}).then((session) => {
    return session.ask("Remember this code: Orion")
        .then(() => session.chat("What code should you remember?"))
        .then((response) => {
            console.log(response.text);
            session.close();
        }, (error) => {
            session.close();
            throw error;
        });
});
```

セッションで同時に実行できるターンは 1 つだけです. 使用後は必ず `session.close()` を呼び出してください. キャンセル, timeout, 生成失敗でもセッション全体が終了します.

同じ options object で sampling と出力長を制御できます:

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

`responseSchema` を渡すとネイティブ構造化生成が有効になります (`structuredJson: true` だけで schema を省略すると既定の object-root schema を使用). Promise は引き続き JSON テキストを返すため, 完成した `ai.ask` または `ai.chat().text` の結果だけを parse してください. `ai.stream` の delta は部分的な JSON テキストです. 永続 `ai.session` は全ターンで 1 つの固定 schema を使用します:

```javascript
let schema = {
    type: "object",
    properties: {
        answer: { type: "string" },
        ok: { type: "boolean" },
    },
    required: [ "answer", "ok" ],
};

ai.ask("Return answer as OK and ok as true.", {
    plugin: true,
    responseSchema: schema,
}).then((text) => {
    let value = JSON.parse(text);
    console.log(value.answer, value.ok);
});
```

ローカルとオンラインの全ターゲットを列挙し, 正確な `target` ID で 1 つを固定できます:

```javascript
ai.catalog({ plugin: true }).then((catalog) => {
    console.log("default:", catalog.defaultTarget);
    catalog.targets.forEach((target) => {
        console.log(target.id, target.displayName, target.locality);
        console.log(target.backendProfiles);
    });
});
```

`ai.ask`, `ai.chat`, `ai.stream`, `ai.session` は同じ正確な `target` を受け付けます. `plugin: true` だけの場合はプラグイン宣言のデフォルトターゲットを使います. プラグインがないか無効, またはターゲットが未設定か利用不可の場合, `AI_PROVIDER_UNAVAILABLE`, `AI_PROVIDER_DISABLED`, `TARGET_NOT_CONFIGURED`, `TARGET_UNAVAILABLE` などの安定したコードで拒否されます. ルートが自動的に変わることはありません.

## 上級: 生の Binder アクセス

以降のセクションでは `ai` モジュールを介さない低レベルプロトコル経路を示します. ほとんどのスクリプトでは不要です.

## 使用例

以下の完全な Rhino スクリプトでは **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)** モデルを例として使用します. この固定ダウンロードの SHA-256 は `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42` であるため, インポート後のモデル ID は `litertlm.ab7838cdfc8f77e54d8ca45eadceb204` です. ダウンロードしたファイル名が `gemma-4-E2B-it.litertlm` でもモデル ID には影響しません. インポート後, プラグイン説明画面の右上にあるコピー操作でスクリプト全体をコピーしてください. Node.js モードではなく, 通常の AutoJs6 Rhino スクリプトとして実行します. 別のリクエストを試す場合は `PROMPT` だけを変更してください.

別のモデルを使用する場合は, モデル管理画面からそのモデルの Model ID をコピーし, スクリプト内の `MODEL_ID` を置き換えてください. 例の値をそのまま使用しないでください.

```javascript
var PLUGIN_PACKAGE =
    "io.github.supermonster003.autojs6.plugin.threestoneai";

var SERVICE_CLASS =
    PLUGIN_PACKAGE + ".provider.ThreeStoneAiProviderService";

// 对应本次模型 SHA-256 的前 32 位
var MODEL_ID =
    "litertlm.ab7838cdfc8f77e54d8ca45eadceb204";

var PROMPT =
    "请用中文列出三条 Android 自动化脚本执行危险操作前应增加确认步骤的理由. 每条一句话.";

var TextApi =
    Packages.org.autojs.plugin.ai.provider.api;

var CommonApi =
    Packages.org.autojs.plugin.ai.common.api;

var AiProviderCodec = TextApi.AiProviderCodec.INSTANCE;
var AiCommonCodec = CommonApi.AiCommonCodec.INSTANCE;

var TimeUnit = java.util.concurrent.TimeUnit;
var CountDownLatch = java.util.concurrent.CountDownLatch;
var AtomicBoolean = java.util.concurrent.atomic.AtomicBoolean;
var AtomicInteger = java.util.concurrent.atomic.AtomicInteger;
var AtomicReference = java.util.concurrent.atomic.AtomicReference;

var connected = new CountDownLatch(1);
var finished = new CountDownLatch(1);

var providerRef = new AtomicReference(null);
var bindError = new AtomicReference(null);
var terminalError = new AtomicReference(null);
var finalText = new AtomicReference(null);
var usageRef = new AtomicReference(null);

var terminalClaimed = new AtomicBoolean(false);
var returnedCredits = new AtomicInteger(0);

var session = null;
var bound = false;

function javaString(value) {
    return new java.lang.String(String(value));
}

function failTerminal(message) {
    if (terminalClaimed.compareAndSet(false, true)) {
        terminalError.set(javaString(message));
        finished.countDown();
    }
}

function describeProtocolError(raw) {
    try {
        var error = AiCommonCodec.decodeError(raw);
        return "AI 错误 code=" + error.getCode() +
            (error.getMessage() == null
                ? ""
                : ": " + error.getMessage());
    } catch (e) {
        return "无法解析插件错误: " + e;
    }
}

function closeDescriptors(fds) {
    if (fds == null) return;

    for (var i = 0; i < fds.length; i++) {
        try {
            if (fds[i] != null) fds[i].close();
        } catch (_) {
        }
    }
}

var textCallback = new JavaAdapter(
    TextApi.IAiCallback.Stub,
    {
        onStarted: function (raw) {
            try {
                var started =
                    AiProviderCodec.decodeSessionStarted(raw);

                console.log(
                    "会话已启动: " + started.getSessionId()
                );
            } catch (e) {
                failTerminal("无法解析会话信息: " + e);
            }
        },

        onChunk: function (raw) {
            try {
                var chunk =
                    AiProviderCodec.decodeTextChunk(raw);

                console.log(
                    "chunk[" + chunk.getSequence() + "]: " +
                    chunk.getTextDelta()
                );

                // 只记录待补 credit, 不在 Binder 回调线程反向调用插件
                returnedCredits.incrementAndGet();
            } catch (e) {
                failTerminal("无法解析流式文本: " + e);
            }
        },

        onToolCalls: function (raw, fds) {
            closeDescriptors(fds);
            failTerminal("插件返回了本示例未启用的工具调用");
        },

        onUsage: function (raw) {
            try {
                usageRef.set(AiCommonCodec.decodeUsage(raw));
            } catch (e) {
                failTerminal("无法解析用量信息: " + e);
            }
        },

        onCompleted: function (raw, fds) {
            try {
                if (!terminalClaimed.compareAndSet(false, true)) {
                    return;
                }

                try {
                    var result =
                        AiProviderCodec.decodeCompletionResult(raw);

                    var bytes =
                        result.getOutput().getInlineBytes();

                    if (bytes == null) {
                        throw new Error("完成结果不是内联文本");
                    }

                    finalText.set(
                        new java.lang.String(
                            bytes,
                            java.nio.charset.StandardCharsets.UTF_8
                        )
                    );
                } catch (e) {
                    terminalError.set(
                        javaString("无法解析完成结果: " + e)
                    );
                } finally {
                    finished.countDown();
                }
            } finally {
                closeDescriptors(fds);
            }
        },

        onFailed: function (raw) {
            failTerminal(describeProtocolError(raw));
        },

        onCancelled: function () {
            failTerminal("生成已取消");
        }
    }
);

var connection = new JavaAdapter(
    android.content.ServiceConnection,
    {
        onServiceConnected: function (name, binder) {
            try {
                providerRef.set(
                    TextApi.IAiProvider.Stub.asInterface(binder)
                );
            } catch (e) {
                bindError.set(
                    javaString("无法创建 provider 接口: " + e)
                );
            } finally {
                connected.countDown();
            }
        },

        onServiceDisconnected: function (name) {
            if (providerRef.get() == null) {
                bindError.compareAndSet(
                    null,
                    javaString("AI provider 已断开")
                );
                connected.countDown();
            } else {
                failTerminal("AI provider 已断开");
            }
        },

        onNullBinding: function (name) {
            bindError.compareAndSet(
                null,
                javaString("AI provider 返回了空 Binder")
            );
            connected.countDown();
        },

        onBindingDied: function (name) {
            bindError.compareAndSet(
                null,
                javaString("AI provider 绑定已失效")
            );
            connected.countDown();
            failTerminal("AI provider 绑定已失效");
        }
    }
);

try {
    var intent = new android.content.Intent();

    intent.setComponent(
        new android.content.ComponentName(
            PLUGIN_PACKAGE,
            SERVICE_CLASS
        )
    );

    bound = context.bindService(
        intent,
        connection,
        android.content.Context.BIND_AUTO_CREATE
    );

    if (!bound) {
        throw new Error("无法绑定 3-Stone AI 插件");
    }

    if (!connected.await(10, TimeUnit.SECONDS)) {
        throw new Error("绑定 AI provider 超时");
    }

    if (bindError.get() != null) {
        throw new Error(String(bindError.get()));
    }

    var provider = providerRef.get();

    if (provider == null) {
        throw new Error("AI provider 不可用");
    }

    // 构造 UTF-8 用户消息
    var utf8 = new java.lang.String(PROMPT).getBytes(
        java.nio.charset.StandardCharsets.UTF_8
    );

    var payload = new CommonApi.AiPayloadReference(
        "text/plain",
        utf8.length,
        null,
        utf8,
        null,
        "utf-8"
    );

    var part = new TextApi.AiContentPart(payload);

    // role=2 表示 USER
    var message = new TextApi.AiMessage(
        2,
        java.util.Collections.singletonList(part),
        null
    );

    var options = new TextApi.AiGenerationOptions(
        true,   // stream
        false,  // reasoning
        false,  // structured JSON
        true,   // usage
        65536,  // 插件输出安全上限 64 KiB
        null,   // 不额外限制 token, 使用模型/引擎默认值
        0,      // tool rounds
        300000, // 插件侧超时 5 分钟
        "text/plain",
        null,
        java.util.Arrays.asList("streaming", "usage"),
        java.lang.Double.valueOf("0.7"), // temperature
        java.lang.Integer.valueOf("40"), // topK
        java.lang.Double.valueOf("0.9"), // topP
        false,  // persistent session
        "cpu"   // explicit backend profile
    );

    var request = new TextApi.AiProviderRequest(
        java.util.UUID.randomUUID().toString(),
        new CommonApi.AiProtocolVersion(2, 0),
        "autojs6.three-stone-ai",
        MODEL_ID,
        java.util.Collections.singletonList(message),
        options,
        java.util.Collections.emptyList()
    );

    var noDescriptors =
        java.lang.reflect.Array.newInstance(
            android.os.ParcelFileDescriptor,
            0
        );

    // openSession 会立即开始模型生成
    session = provider.openSession(
        AiProviderCodec.encodeTextRequest(request),
        noDescriptors,
        textCallback
    );

    // 建立初始流式窗口
    session.grantCredits(8);

    var deadline =
        android.os.SystemClock.elapsedRealtime() + 330000;

    while (
        finished.getCount() > 0 &&
        android.os.SystemClock.elapsedRealtime() < deadline
    ) {
        finished.await(100, TimeUnit.MILLISECONDS);

        // 在普通脚本线程补回已经消费的 credit
        var credits = returnedCredits.getAndSet(0);

        if (credits > 0 && finished.getCount() > 0) {
            session.grantCredits(credits);
        }
    }

    if (finished.getCount() > 0) {
        session.cancel();
        throw new Error("等待模型生成超时");
    }

    if (terminalError.get() != null) {
        throw new Error(String(terminalError.get()));
    }

    var usage = usageRef.get();

    if (usage == null) {
        throw new Error("插件未返回用量信息");
    }

    console.log(
        "usage: input=" + usage.getInputTokens() +
        ", output=" + usage.getOutputTokens() +
        ", total=" + usage.getTotalTokens() +
        ", durationMillis=" + usage.getDurationMillis()
    );

    console.log(
        "\n===== 完整结果 =====\n" + finalText.get()
    );

    toastLog("模型生成完成");
} finally {
    try {
        if (session != null) session.close();
    } catch (_) {
    }

    try {
        if (bound) context.unbindService(connection);
    } catch (_) {
    }
}
```

セキュリティと動作制限:

- モデルのインポート上限は 8 GiB で, 完了後に 256 MiB 以上の空き容量が必要です.
- コンテキスト上限は 256 KiB, 出力上限は 64 KiB で, 同時に有効な生成セッションは 1 つだけです.
- `maxTokens` (direct protocol では `maximumOutputTokens`) は 1 から 2,147,483,647 までで, usage reporting なしで適用されます. `temperature` は有限かつ 0 以上, `topK` は正, `topP` は 0 から 1 の有限値である必要があります.
- `maxTokens` を省略した場合 (direct protocol では `maximumOutputTokens` を `null` にした場合), モデルまたは engine の既定値を使用しますが, provider の 64 KiB 出力安全上限は引き続き適用されます.
- Streaming, usage, 永続セッション, structured JSON, `text/plain`, `application/json` を宣言します. Reasoning と tools は非対応です.
- 応答 schema は 64 KiB 以下の JSON object である必要があり, 対応 keyword は同梱 LiteRT-LM/LLGuidance ランタイムの実装に従います. 完成した構造化出力は厳密に parse と検証を行うため, JSON 値全体に十分な `maxTokens` を確保してください.
- Usage token 数は LiteRT-LM Conversation の KV cache と decode カウンターから取得し, 文字数では推定しません. `durationMillis` はプロバイダー生成呼び出しのみを測定し, ホストの探索, バインド, モデル列挙, ディスパッチ時間を含みません.
- 明示的な推奨モデルのダウンロードにのみ `INTERNET` 権限を要求し, 広範なストレージ権限は要求しません. 推論はネットワークを使わず, SAF は選択された入出力先だけへのアクセスを許可します.
- 同じ署名の AutoJs6 ホストだけが provider サービスを bind できます.
- プロセス間の安全性のため, 以前の SHA-256 hash 名モデル世代を保持します. これらはアプリ専用ストレージを引き続き使用します.
