3-Stone AI apporte à AutoJs6 la conversation avec l'IA et la gestion des modèles: les modèles locaux s'exécutent hors ligne sur le téléphone, et les services en ligne (par exemple OpenAI, Anthropic, Gemini) utilisent votre propre clé API. Le plugin a sa propre interface de chat et fournit aussi les modèles du module `ai` des scripts AutoJs6.

### Prise en main

1. Ouvrez 3-Stone AI depuis son icône sur l'écran d'accueil, touchez "Paramètres" dans le menu de la page de chat, puis "Modèles et services".
2. Pour un modèle local, touchez "Télécharger un modèle" dans la section "Modèles locaux" et choisissez un modèle recommandé; il est importé automatiquement après téléchargement et vérification. Si vous avez déjà un fichier `.litertlm`, touchez "Importer un fichier de modèle".
3. Pour un service en ligne, touchez "Ajouter un service" dans la section "Services en ligne", choisissez un fournisseur, saisissez l'ID du modèle et la clé API, puis enregistrez. Vous pouvez ensuite choisir "Tester la connexion" dans le menu du service.
4. Revenez à la page de chat, touchez le nom du modèle en haut pour choisir un modèle, puis saisissez une question et envoyez-la.

### Fonctionnalités principales

- **Chat**: les conversations sont enregistrées automatiquement et le raisonnement du modèle peut être affiché; régénérer ou modifier un message conserve chaque version, entre lesquelles vous pouvez basculer.
- **Pièces jointes**: touchez "+" à gauche du champ de saisie pour ajouter des images, des fichiers texte ou des PDF. Les images vont aux modèles dont "Entrée d'images" est activée, les PDF uniquement aux services Anthropic et Gemini; les modèles locaux ne prennent pas encore en charge les pièces jointes.
- **Changer de modèle**: changez de modèle à tout moment pendant le chat; les scripts utilisent la "Cible par défaut" de la page "Modèles et services".
- **Gestion des modèles**: vérifiez, renommez ou supprimez les modèles dans la section "Modèles locaux". Les clés API restent sur l'appareil et ne sont pas incluses lors de l'export des services.
- **En-têtes de requête**: ajoutez des en-têtes de requête personnalisés dans "En-têtes de requête" sur la page de détails du service, par exemple l'en-tête de session exigé par OpenCode Go; les valeurs secrètes restent sur l'appareil et ne sont pas incluses dans les exports.
- **Voix**: touchez le micro pour dicter, appuyez longuement dessus pour démarrer une conversation vocale, et appuyez longuement sur une réponse pour choisir "Lire à voix haute"; avec 3-Macaw Speech installé, la dictée s'affiche en direct dans le champ de saisie, la conversation vocale n'ouvre plus d'écran de reconnaissance, et la dictée et la lecture fonctionnent hors ligne.
- **Exporter et partager**: choisissez "Exporter en Markdown" ou "Partager en image" dans le menu de la page de chat.

### Utilisation dans les scripts

Les scripts AutoJs6 utilisent ce plugin via le module `ai`. Sans cible, la "Cible par défaut" est utilisée:

```js
ai.ask('Introduce AutoJs6 in one sentence.').then((text) => {
    console.log(text);
});
```

Pour fixer un modèle précis, trouvez sa cible avec `ai.catalog()` et passez son `id` comme `target`. Cet exemple utilise le premier modèle local disponible:

```js
ai.catalog().then((catalog) => {
    let target = catalog.targets.find((item) => item.available && item.locality === 'local');
    if (!target) throw new Error('No available local model');
    return ai.ask('Reply with OK.', { target: target.id, temperature: 0.7 });
}).then((text) => console.log(text));
```

Pour une conversation à plusieurs tours, utilisez `ai.session`, n'envoyez que la nouvelle invite à chaque tour et appelez `session.close()` à la fin:

```js
ai.session({ system: 'Keep every answer short.' }).then((session) => {
    return session.ask('Remember this code: Orion.')
        .then(() => session.ask('What code should you remember?'))
        .then((text) => {
            console.log(text);
            session.close();
        }, (error) => {
            session.close();
            throw error;
        });
});
```

Avec 3-Macaw Speech installé, le module `speech` enchaîne la dictée, la question et la lecture: arrêtez l'écoute après une phrase, passez le texte à `ai.chat`, puis lisez la réponse avec `speech.speak`:

```js
let session = speech.listen({ timeoutMillis: 8000 });
session.on('final', () => session.stop());
session.on('end', () => {
    let question = session.result();
    if (!question) return;
    ai.chat(question, { maxTokens: 256 })
        .then((response) => speech.speak(response.text))
        .catch((error) => console.error(error.code, error.message));
});
```

- `ai.chat` renvoie la réponse complète avec l'utilisation, et `ai.stream` renvoie le texte au fil de la génération.
- `temperature`, `topK`, `topP` et `maxTokens` règlent l'échantillonnage et la longueur de sortie; si la cible prend en charge la sortie structurée, passer `responseSchema` renvoie un texte conforme à ce JSON Schema.
- Si la cible est indisponible ou non configurée, la requête échoue directement (par exemple avec `TARGET_UNAVAILABLE`) et ne bascule jamais vers un autre modèle.
- `ai.stream` peut aussi suivre `speech.listen`: lisez `response.text` dans l'événement `done`, ou découpez le texte de `delta` en phrases et lisez chacune avec `speech.speak(sentence, { queue: 'add' })` au fil de la génération. Le module `speech` est fourni par 3-Macaw Speech; sans lui, les appels échouent (`PLUGIN_UNAVAILABLE`).

### Prérequis

- Android 7.0 ou version ultérieure, AutoJs6 6.8.0 ou version ultérieure.
- Les modèles locaux nécessitent un appareil 64 bits (arm64-v8a ou x86_64) et assez d'espace de stockage; la confirmation de téléchargement indique la mémoire minimale requise. Les appareils 32 bits ne peuvent utiliser que les services en ligne.
- Les services en ligne nécessitent votre propre clé API, et le fournisseur peut facturer l'utilisation.
- L'autorisation réseau sert à télécharger les modèles, à joindre les services en ligne et à mettre à jour la liste des modèles prédéfinis; l'inférence locale n'utilise pas le réseau. Le plugin ne demande pas d'autorisation de stockage ni de micro.

### Questions fréquentes

**Différences entre modèles locaux et services en ligne**

Les modèles locaux s'exécutent sur le téléphone: les conversations ne quittent pas l'appareil, mais la vitesse et la qualité dépendent du téléphone. Les services en ligne envoient les conversations au fournisseur choisi et nécessitent un réseau et une clé API.

**Raisons pour lesquelles un échec ne bascule pas vers un autre modèle**

Les requêtes n'utilisent que le modèle choisi, afin que les conversations ne partent jamais vers un autre service à votre insu. En cas d'échec, vérifiez ce modèle dans "Modèles et services" ou changez de modèle vous-même.

**Cas où un modèle local ne fonctionne pas**

Ouvrez le menu de ce modèle dans la section "Modèles locaux" et touchez "Vérifier le modèle" pour voir la raison; si la mémoire manque, utilisez un modèle recommandé plus petit.

**Cas où d'autres applications demandent à utiliser l'IA**

Avant qu'une application empaquetée avec AutoJs6 et signée séparément puisse utiliser l'IA, 3-Stone AI vous demande de confirmer. Une fois autorisée, elle peut utiliser les modèles locaux et les services en ligne enregistrés, ce qui peut consommer un quota payant; retirez l'accès dans "Paramètres > Confidentialité et autorisations > Applications autorisées pour IA".

**Cas où OpenCode Go renvoie MissingSessionID**

OpenCode Go exige l'en-tête de session x-opencode-session dans chaque requête. Touchez "Ajouter l'en-tête de session" dans l'explication de l'erreur, ou ajoutez le préréglage "Session OpenCode Go" dans "En-têtes de requête" sur la page de détails du service, enregistrez, puis réessayez.

Toutes les options, les événements et les codes d'erreur figurent au chapitre [Intelligence artificielle (IA)](https://docs.autojs6.com/#/ai) de la documentation AutoJs6; la confidentialité et les limites sont décrites sur la [page du projet](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI).
