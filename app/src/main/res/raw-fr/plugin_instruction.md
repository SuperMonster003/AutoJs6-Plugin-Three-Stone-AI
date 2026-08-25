# AutoJs6 3-Stone AI

Ce plugin importe un paquet de modèle `.litertlm` local via Android Storage Access Framework (SAF), le copie dans le stockage privé de cette application et exécute une génération LiteRT-LM uniquement sur CPU avec un historique en texte brut et une sortie en streaming de texte brut ou JSON contraint par un schema.

Le plugin exige la build hôte AutoJs6 5276 ou ultérieure et Android API 24 ou ultérieur.

## Obtenir un modèle

Dans le gestionnaire de modèles, touchez **Télécharger un modèle recommandé**, puis choisissez un modèle LiteRT Community épinglé et un emplacement SAF accessible en écriture. La progression est affichée et l'en-tête LiteRT-LM, le nombre exact d'octets et le SHA-256 sont vérifiés avant d'activer **Importer le modèle téléchargé**. L'inférence n'utilise jamais le réseau. Le fichier externe et sa copie privée importée occupent chacun de l'espace; si Android arrête le processus pendant le téléchargement, supprimez manuellement tout document externe partiel.

## Démarrage rapide (recommandé)

Avec AutoJs6 build 5276 ou ultérieur, appelez le plugin directement via le module global `ai`. `plugin: true` sélectionne ce plugin ; si un seul modèle est importé, l'ID de modèle peut être omis.

```javascript
ai.ask("Hello", { plugin: true }).then((text) => {
    console.log(text);
});
```

Pour conserver un contexte multi-tour, créez une Conversation persistante avec `ai.session`. Les tours suivants envoient uniquement le nouveau prompt utilisateur:

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

Un seul tour peut être actif par session. Appelez toujours `session.close()` à la fin; une annulation, un timeout ou un échec de génération ferme également toute la session.

L'échantillonnage et la longueur de sortie se règlent dans le même objet d'options :

```javascript
ai.ask("Hello", {
    plugin: true,
    temperature: 0.7,
    topK: 40,
    topP: 0.9,
    // maxTokens: 1024,
}).then((text) => console.log(text));
```

Passez `responseSchema` pour activer la génération structurée native (`structuredJson: true` sans schema utilise un schema objet-racine par défaut). La promesse produit toujours du texte JSON; n'analysez donc qu'un résultat `ai.ask` ou `ai.chat().text` complet, car les deltas de `ai.stream` sont du texte JSON partiel. Une `ai.session` persistante conserve un schema fixe pour tous ses tours:

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

Énumérez toutes les cibles locales et en ligne, puis épinglez-en une avec son ID `target` exact :

```javascript
ai.catalog({ plugin: true }).then((catalog) => {
    console.log("default:", catalog.defaultTarget);
    catalog.targets.forEach((target) => {
        console.log(target.id, target.displayName, target.locality);
        console.log(target.backendProfiles);
    });
});
```

`ai.ask`, `ai.chat`, `ai.stream` et `ai.session` acceptent le même `target` exact ; `plugin: true` seul utilise la cible par défaut déclarée par le plugin. Un plugin absent ou désactivé et une cible non configurée ou indisponible sont rejetés avec des codes stables comme `AI_PROVIDER_UNAVAILABLE`, `AI_PROVIDER_DISABLED`, `TARGET_NOT_CONFIGURED` ou `TARGET_UNAVAILABLE`. La route ne change jamais automatiquement.

## Avancé : accès Binder brut

Les sections suivantes montrent le chemin protocolaire de bas niveau sans le module `ai`. La plupart des scripts n'en ont pas besoin.

## Exemple d'utilisation

Le script Rhino complet ci-dessous utilise le modèle **[gemma-4-E2B-it.litertlm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/ee5eb9da5d635904dd8f804d79bb6bc5cde92ba1/gemma-4-E2B-it.litertlm?download=true)** comme exemple. Ce téléchargement épinglé possède le SHA-256 `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`; son identifiant après import est donc `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`. Le fichier téléchargé peut s'appeler `gemma-4-E2B-it.litertlm`; son nom n'affecte pas l'identifiant du modèle. Après l'import, utilisez l'action de copie en haut à droite des instructions du plugin pour copier le script entier. Exécutez-le comme un script Rhino AutoJs6 normal, et non en mode Node.js. Modifiez uniquement `PROMPT` pour essayer une autre requête.

Pour utiliser un autre modèle, copiez son Model ID depuis l'écran de gestion des modèles et remplacez `MODEL_ID` dans le script. Ne réutilisez pas la valeur de l'exemple.

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

Sécurité et limites opérationnelles:

- Un import de modèle est limité à 8 GiB et doit laisser au moins 256 MiB libres.
- Le contexte est limité à 256 KiB, la sortie à 64 KiB et une seule session de génération peut être active.
- `maxTokens` (ou `maximumOutputTokens` dans le protocole direct) accepte de 1 à 2 147 483 647 et s'applique sans exiger de rapport usage. `temperature` doit être fini et positif ou nul, `topK` positif et `topP` fini entre 0 et 1.
- Si `maxTokens` est omis (ou si `maximumOutputTokens` vaut `null` dans le protocole direct), la valeur par défaut du modèle ou du moteur est utilisée; la limite de sécurité de sortie de 64 KiB du fournisseur reste applicable.
- Streaming, usage, les sessions persistantes, structured JSON, `text/plain` et `application/json` sont déclarés. Reasoning et tools ne sont pas pris en charge.
- Le schema de réponse doit être un objet JSON de 64 KiB au maximum; les mots-clés acceptés sont ceux implémentés par le runtime LiteRT-LM/LLGuidance intégré. La sortie structurée complète est analysée et validée strictement, il faut donc réserver assez de `maxTokens` pour la valeur JSON entière.
- Les tokens de usage proviennent des compteurs de cache KV et decode de Conversation dans LiteRT-LM, sans estimation par caractères. `durationMillis` mesure la génération du fournisseur et exclut la découverte, la liaison, la liste des modèles et la distribution de l'hôte.
- Le plugin demande `INTERNET` uniquement pour les téléchargements explicites de modèles recommandés et aucune permission générale de stockage. L'inférence n'utilise jamais le réseau; SAF limite l'accès à la source ou destination choisie.
- Seul le client AutoJs6 avec la même signature peut lier le service provider.
- Les générations précédentes nommées par hash SHA-256 sont conservées pour la sécurité interprocessus et continuent d'occuper le stockage privé.
