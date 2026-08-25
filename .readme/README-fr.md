<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="three-stone-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin IA unifié. LiteRT-LM reste local; les cibles en ligne sont toujours choisies explicitement</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Langues

******

Le fichier README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-en.md)
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/.readme/README-ar.md)

******

### Introduction

******

3-Stone AI est le plugin officiel de génération de texte IA pour AutoJs6. Il exécute les modèles LiteRT-LM importés par l'utilisateur sur un backend CPU explicitement choisi ou sur un GPU compatible, accepte un historique de messages en texte brut et renvoie du texte brut ou du texte JSON contraint par un schema via une session de streaming contrôlée. Les appels actuels de l'hôte AI Provider V1 utilisent ces cibles locales sans accès réseau ni envoi de données. Les réglages du plugin gèrent les profils OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter et OpenAI Compatible personnalisés, les identifiants chiffrés, la cible en ligne par défaut, les réseaux facturés et les tests de connexion explicites. Le chat du lanceur peut associer explicitement chaque conversation à une cible locale ou en ligne configurée; les API hôte V1 restent locales uniquement.

******

### Fonctions

******

- Importer un paquet de modèle `.litertlm` avec le sélecteur système Android et conserver une copie vérifiée dans le stockage privé de l'application.
- Télécharger un modèle LiteRT Community épinglé et sans authentification vers un emplacement SAF choisi par l'utilisateur, avec progression, annulation, nettoyage et vérification exacte de la taille et du SHA-256.
- Contrôler le stockage privé avant d'ouvrir le sélecteur, afficher le budget d'import actuel et l'espace estimé de la copie privée, puis revérifier le fichier sélectionné avant la copie.
- Créer des requêtes de génération locale avec un historique system, user et assistant en texte brut.
- Transmettre `temperature`, `topK`, `topP` et `maxTokens` depuis `ai.ask`, `ai.chat` et `ai.stream` d'AutoJs6 jusqu'à LiteRT-LM.
- Contraindre nativement la sortie avec JSON Schema de LiteRT-LM via `structuredJson` et `responseSchema` d'AutoJs6; les valeurs complètes restent du texte JSON pour `JSON.parse`.
- Rapporter les nombres exacts de tokens d'entrée, de sortie et totaux de LiteRT-LM, ainsi que la durée de génération côté fournisseur, via `ai.chat().usage` et les événements usage du streaming.
- Conserver le contexte multi-tour dans une seule Conversation native LiteRT-LM via `ai.session` d'AutoJs6, en envoyant uniquement le nouveau prompt utilisateur aux tours suivants.
- Réutiliser l'Engine initialisé selon le SHA-256 du modèle afin d'éviter un nouveau démarrage à froid pour les requêtes consécutives sur le même modèle.
- Initialiser facultativement chaque modèle importé une fois, conserver son état Disponible/Incompatible et relancer la vérification depuis le gestionnaire de modèles.
- Transmettre les chunks de texte dans l'ordre avec une contre-pression par credits et publier un seul état terminal terminé, échoué ou annulé.
- Lister, sélectionner et renommer les modèles importés, supprimer les modèles non sélectionnés et récupérer les fichiers de modèle non référencés depuis le gestionnaire.
- Sélectionner explicitement le backend `cpu`, `gpu` ou `npu` via AutoJs6; CPU est utilisé par défaut, GPU seulement après une sonde de chargement OpenCL, et NPU est signalé indisponible car son runtime EAP n'est pas inclus.
- Gérer les profils en ligne intégrés et personnalisés, les identifiants Android Keystore, la cible en ligne par défaut, les réseaux facturés et les tests de connexion explicites et limités dans les réglages.
- Associer chaque conversation du lanceur à un instantané de cible locale ou en ligne; un nouveau chat est recommandé lors du changement d'une conversation remplie, et continuer exige une confirmation explicite enregistrée.

******

### Formats du modèle et des données

******

La version 1 déclare uniquement le périmètre suivant:

```text
model package: .litertlm
input: text/plain message history plus application/json response schema
output: streamed text/plain or application/json text chunks
runtime: LiteRT-LM 0.15.0
```

******

### Interface du plugin

******

L'hôte découvre et appelle le plugin avec les identités suivantes:

```text
service action: org.autojs.plugin.AI_PROVIDER
plugin id: three-stone-ai
protocol provider id: autojs6.three-stone-ai
engine: three-stone-ai
variant: default
protocol: V1.2-V1.3
required host build: 5276
```

La surface publique AI Provider V1 déclare une exécution ON_DEVICE et le mode credential NONE. Elle déclare les capacités `streaming`, `usage`, `persistent-session` et `structured-json`, accepte des messages `text/plain` et des schemas de réponse `application/json`, et émet du texte `text/plain` ou `application/json`. Le protocole 1.3 ajoute des profils backend explicites et leur disponibilité sur l'appareil, sans repli silencieux sur le CPU. Les cibles REMOTE internes du plugin ne sont pas annoncées par V1.

La build hôte 5276 ou ultérieure est requise. Les versions incluent les variantes APK arm64-v8a, x86_64, universal.

******

### État de l'intégration hôte

******

> Dans AutoJs6 (build 5276 et ultérieur), `ai.ask`, `ai.chat` et `ai.stream` prennent en charge la route de plugin local. `ai.session({ plugin: true })` crée une Conversation multi-tour persistante dont les appels `ask`, `chat` et `stream` suivants envoient uniquement le nouveau prompt utilisateur. `ai.ask(messages, { plugin: true })` conserve dans l'ordre les messages texte de rôles `system`, `user` et `assistant`, et le dernier message doit avoir le rôle `user`. `ai.chat` renvoie les nombres exacts de tokens dans `usage` et la durée mesurée dans `usage.raw.durationMillis` ; `ai.stream` émet le même usage cumulatif avant la fin. Passez `plugin: true` pour sélectionner ce plugin, et l'ID de modèle peut être omis lorsqu'un seul modèle est importé ; `ai.models({ plugin: true })` énumère les modèles importés et leurs `backendProfiles`. La génération accepte `backend: 'cpu' | 'gpu' | 'npu'`; un profil indisponible échoue explicitement sans repli CPU. Si le plugin n'est pas installé, désactivé dans le Centre de plugins ou sans modèle, les scripts reçoivent une erreur claire. Le sélecteur explicite `plugin: { component, providerId, modelId }` reste pris en charge. `responseSchema` active implicitement la sortie structurée; `structuredJson: true` sans schema utilise un schema objet-racine par défaut. `ai.ask` et `ai.chat().text` renvoient toujours du texte JSON, les deltas de streaming sont du texte JSON partiel et une session persistante conserve un schema et un backend fixes pour tous ses tours.

******

### Sécurité et confidentialité

******

Le plugin demande `INTERNET` pour les téléchargements de modèles recommandés déclenchés par l'utilisateur et les requêtes vers une cible online configurée par l'utilisateur; la génération locale V1 actuelle n'utilise pas le réseau. Il ne demande aucune permission générale de stockage. Les téléchargements utilisent des révisions HTTPS immuables, une taille et un SHA-256 épinglés, et n'écrivent que dans l'emplacement SAF choisi; en-tête LiteRT-LM, taille, empreinte, flush et fsync doivent tous réussir. L'import lit toujours seulement un URI du sélecteur, écrit une copie vérifiée dans `files/models` et l'active atomiquement. Les services vérifient aussi le paquet AutoJs6, l'UID appelant et les signatures.

******

### Limites opérationnelles

******

- Une importation de modèle est limitée à 8 GiB et doit laisser au moins 256 MiB libres.
- Un seul téléchargement s'exécute dans le processus. La recréation de l'Activity conserve progression et annulation; une annulation ou un échec supprime ou tronque la destination. Un arrêt du processus peut néanmoins laisser un document externe partiel à supprimer manuellement.
- Un coordinateur d'import unique à portée application maintient le travail pendant la recréation de Activity. Un pending journal synchronisé par fsync permet la récupération au démarrage à froid et le nettoyage des fichiers temporaires stale `.incoming`, `.current` et `.pending`. La récupération supprime uniquement une destination créée par la tentative actuelle et jamais publiée par current metadata; les générations publiées, current et historiques nommées par hash sont conservées.
- Pour éviter les conditions de concurrence interprocessus avec le processus isolé `:provider`, les imports ne suppriment pas automatiquement les générations précédentes nommées par hash SHA-256. Le gestionnaire peut supprimer les modèles non sélectionnés du catalogue et récupérer les fichiers nommés par hash qui ne sont plus référencés.
- Une seule session de génération peut être active dans le processus. Les descripteurs sont dupliqués avant le travail asynchrone et fermés selon les quotas du protocole.
- Le provider conserve au plus un Engine initialisé, indexé par le SHA-256 du modèle et le profil backend. Les requêtes avec la même paire le réutilisent; un changement de clé, cinq minutes d'inactivité ou une pression mémoire explicite le libèrent en toute sécurité.
- La vérification d'un modèle prouve uniquement que `Engine.initialize()` réussit sur l'appareil et le runtime inclus actuels; elle n'évalue pas la qualité de sortie et peut être relancée après un changement d'appareil ou de runtime.
- Le provider annonce un plafond de contexte de 256 KiB et un plafond de sortie de 64 KiB. Les requêtes et modèles peuvent imposer des limites inférieures.
- Le schema de réponse doit être un objet JSON de 64 KiB au maximum. Les mots-clés acceptés sont ceux implémentés par le runtime LiteRT-LM/LLGuidance intégré; la sortie complète est analysée et validée strictement, il faut donc réserver assez de `maxTokens` pour la valeur JSON entière.
- `maxTokens` accepte les entiers de 1 à 2 147 483 647. S'il est omis, le nombre de tokens de sortie est laissé au modèle ou au moteur; la limite de sécurité de sortie de 64 KiB du fournisseur reste applicable. `temperature` doit être fini et positif ou nul, `topK` un entier positif et `topP` fini entre 0 et 1. Omettre les trois réglages d'échantillonnage conserve les valeurs du modèle ou moteur ; un remplacement partiel complète les réglages omis avec la base LiteRT-LM `topK: 1`, `topP: 0.95` et `temperature: 1`.
- Le streaming utilise des credits finis et des chunks bornés pour éviter les tampons illimités ou les callbacks sans contre-pression.
- Les tokens de usage proviennent directement des compteurs de cache KV et decode de Conversation dans LiteRT-LM, sans estimation par caractères. `durationMillis` mesure uniquement la génération du plugin et exclut la découverte, la liaison, la liste des modèles et la distribution de l'hôte.
- Une `ai.session` persistante autorise un seul tour actif et conserve sa Conversation native après une fin normale; elle doit être recréée après une annulation, un timeout, un échec de génération ou une fermeture explicite.
- L'annulation, la fermeture de session et le timeout arrêtent la publication et terminent la requête avec un seul état terminal.

******

### Capacités non déclarées

******

- Reasoning et tools ne sont pas déclarés.
- Les messages de rôle tool, les schemas d'outils, les tool calls et les tool results ne sont pas acceptés.
- Aucune découverte réseau de modèles ni aucun téléchargement depuis une URL arbitraire. Le chat du lanceur ne présente que les modèles locaux importés et les profils en ligne explicitement configurés; le routage hôte AI Provider V1 reste local uniquement et seul le catalogue intégré épinglé est téléchargeable.
- L'inférence NPU n'est pas déclarée: le profil reste visible comme `unavailable` avec `npu-runtime-not-packaged`. GPU n'est déclaré que si `libOpenCL.so` est chargeable, et l'extension `.litertlm` ne garantit toujours pas l'initialisation du modèle.

******

### Feuille de route

******

La feuille de route est organisée en fonctionnalités livrables, chacune vérifiable séparément

- [Consulter ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Historique des versions

******

# v1.1.0

###### 2026/08/24

* `Fonction` Identité de marque et d'exécution du plugin IA locale officiel d'AutoJs6 finalisée sous le nom 3-Stone AI
* `Fonction` L'intégration interprocessus utilise les identités neutres `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` et `IAiProvider`/`IAiSession`/`IAiCallback` sans conserver d'alias des identités remplacées
* `Fonction` Compatible avec le sélecteur abrégé `plugin: true` de `ai.ask`/`ai.chat`/`ai.stream` et l'énumération de modèles `ai.models` d'AutoJs6
* `Fonction` Transmission de `temperature`, `topK`, `topP` et `maxTokens` par le protocole AI Provider 1.1 vers les contrôles d'échantillonnage et de tokens de sortie de LiteRT-LM
* `Fonction` Rapport des nombres exacts de tokens d'entrée, de sortie et totaux de LiteRT-LM, avec la durée de génération mesurée côté fournisseur, via `ai.chat().usage` et les événements usage du streaming
* `Fonction` Sessions persistantes du protocole AI Provider 1.2 et réutilisation d'une Conversation multi-tour via `ai.session` d'AutoJs6 sans renvoyer l'historique précédent
* `Fonction` Décodage natif contraint par JSON Schema de LiteRT-LM via `structuredJson` et `responseSchema` d'AutoJs6, pour les appels uniques, le streaming et les sessions persistantes, avec validation stricte du JSON complet
* `Fonction` Profils backend explicites `cpu`, `gpu` et `npu` via le protocole 1.3 et les options de génération AutoJs6, avec rapport de compatibilité de l'appareil, isolation du cache modèle/profil et aucun repli depuis un profil indisponible; GPU n'est déclaré qu'après une sonde de chargement OpenCL et NPU reste indisponible car son runtime EAP n'est pas intégré
* `Fonction` Téléchargement direct de modèles LiteRT Community épinglés vers un emplacement SAF choisi, avec progression, annulation précise, nettoyage, vérification de l'en-tête LiteRT-LM, de la taille et du SHA-256, puis import direct
* `Fonction` Ajout d'un espace de conversation accessible au lancement avec Markdown en streaming, historique persistant, avertissement lors du remplacement d'une branche après modification d'un ancien message, recherche multi-résultat et saisie adaptée au clavier
* `Fonction` Ajout de paramètres d'application pour la couleur du thème, le mode sombre, la langue, les informations sur l'application et le développeur, et l'historique des versions, avec Suivre AutoJs6 par défaut lorsque possible
* `Fonction` Ajout de paramètres de conversation pour la taille de police, le comportement de la touche Entrée, les output tokens illimités ou personnalisés et l'échantillonnage `temperature`, `topK` et `topP` par défaut du modèle ou personnalisé
* `Fonction` Rendu du contenu en ligne `$\text{...}$` pendant le streaming, avec des commandes mathématiques courantes et des styles exposant et indice
* `Fonction` Ajout d'un coffre d'identifiants géré par le plugin avec Android Keystore, AES-256-GCM, un texte chiffré authentifié lié au profile, des fichiers privés atomiques interprocessus, la seule consultation de l'état configured et l'effacement immédiat du texte en clair
* `Fonction` Ajout d'un dépôt strict et non secret de profils en ligne pour les points de terminaison OpenAI Compatible exclusivement HTTPS, avec UUID canoniques, métadonnées atomiques interprocessus et remplacement ou suppression obligatoire des identifiants lors d'un changement de provider ou d'origin
* `Fonction` Ajout du backend d'exécution HTTPS OpenAI Compatible interne au plugin pour des profils avec baseUrl, identifiant et modèle personnalisés, avec streaming SSE borné et repli JSON, annulation précise, usage du provider, historique persistant des tours terminés, mapping JSON Schema et erreurs fixes sans données sensibles; le routage hôte AI Provider V1 reste limité aux cibles locales
* `Fonction` Ajout de préréglages OpenAI, Anthropic, Gemini, DeepSeek et OpenRouter alignés sur le catalogue de l'hôte; la couche d'exécution en ligne unifiée réutilise le protocole compatible OpenAI et adapte séparément l'authentification, les requêtes, les terminaux SSE, l'usage et JSON Schema natifs d'Anthropic Messages et Gemini GenerateContent, sans repli entre protocoles ni entre local et en ligne
* `Fonction` Ajout de la UI des services en ligne en 10 langues pour ajouter, modifier et supprimer les profils, remplacer et effacer les clés API sans les afficher, choisir la cible par défaut, imposer le choix des réseaux facturés avant accès aux identifiants et lancer des tests explicites annulables de 120 secondes; les réglages partagent le document atomique interprocessus et AI Provider V1 reste local uniquement
* `Fonction` Ajout au chat du lanceur d'un sélecteur unifié de cibles locales et cloud: chaque conversation conserve un instantané de cible, un nouveau chat est recommandé lors du changement d'une conversation remplie, et continuer avec le contexte exige une confirmation explicite enregistrée
* `Correction` Suppression des limites implicites de 256 tokens et 4 KiB des exemples exécutables: l'omission de `maxTokens` utilise désormais la valeur par défaut du modèle ou du moteur, et l'exemple Binder direct utilise les 64 KiB complets autorisés par le fournisseur
* `Correction` Correction de l'exemple Binder de bas niveau dans les instructions localisées en 10 langues, qui appelait encore le constructeur `AiGenerationOptions` à 14 arguments du protocole 1.1 et échouait avec l'API du protocole 1.3
* `Correction` Correction du gestionnaire de modèles qui conservait les couleurs de texte du thème clair en mode sombre système, rendant le texte, les cases à cocher et les lignes de modèles illisibles sur l'arrière-plan sombre
* `Correction` Maintien de l'éditeur au-dessus du clavier logiciel, choix du texte du bouton Envoyer selon le contraste avec la couleur du thème et harmonisation des contrôles de recherche précédent, suivant et fermer
* `Correction` Correction du blocage de close appelé depuis un callback listener de génération, où l'attente de quiescence attendait son propre callback indéfiniment; close attend toujours les callbacks déjà actifs sur les autres threads
* `Correction` Correction du rejet du stockage privé des profils en ligne et des identifiants lorsque Android canonicalise la racine fiable `/data/user/0` vers `/data/data`; les liens de fils directs et les sorties de confinement restent refusés
* `Amélioration` Description du plugin, instructions et README en 10 langues mis à jour pour refléter la formalisation de la route de plugin local `ai.*`
* `Amélioration` ROADMAP réécrite comme feuille de route de fonctionnalités avec des éléments vérifiables individuellement
* `Amélioration` Normalisation de la ponctuation ASCII dans l'application et les textes localisés générés, avec un test de régression pour les textes empaquetés et générés
* `Amélioration` Introduction d'une couche partagée `AiBackend`/`AiTarget`/`AiBackendSession` afin que le chat du lanceur et le provider Binder utilisent le même chemin `LiteRtLocalBackend` pour le catalogue, les capacités, la création de session, le streaming et l'annulation
* `Amélioration` Fusion des cibles locales `local:*` et en ligne `profile:*` dans un catalogue et un répartiteur uniques au niveau Application; la liste de modèles V1 reste locale et les cibles en ligne sont déclarées unavailable jusqu'à l'implémentation de leur transport HTTPS

# v1.0.0

###### 2026/08/08

* `Fonction` Provider sur l'appareil pour le protocole AI Provider V1 avec ID et moteur `three-stone-ai`, provider ID `autojs6.three-stone-ai` et variante `default`
* `Fonction` Génération de texte brut LiteRT-LM sur CPU avec historique system, user et assistant et streaming contrôlé par credits
* `Fonction` Import SAF de `.litertlm` dans le stockage privé avec limite de 8 GiB, réserve d'espace, SHA-256, fsync et activation atomique
* `Fonction` Une session active, I/O bornées, quotas de descripteurs, annulation, timeout, un état terminal et vérification de l'appelant AutoJs6 avec la même signature
* `Fonction` Absence explicite des capacités reasoning, tools, structured JSON, usage, réseau et credential
* `Fonction` APK arm64-v8a, x86_64 et universal avec README, changelog, interface Android et instructions du plugin en 10 langues
* `Fonction` Écran de gestion des modèles affichant le catalogue complet et l'espace occupé dans le stockage privé, avec sélection atomique du modèle courant sans copie des fichiers de modèle
* `Amélioration` Conservation des générations précédentes nommées par hash SHA-256 après un import de remplacement afin d'éviter les courses interprocessus avec `:provider`, les fichiers conservés continuant d'occuper le stockage privé
* `Amélioration` Ajout d'un coordinateur d'import unique à portée application et d'un pending journal synchronisé par fsync pour résister à la recréation de Activity, récupérer au démarrage à froid, nettoyer les temporaires stale et limiter la suppression aux destinations créées par la tentative actuelle et jamais publiées, tout en conservant les générations publiées, current et historiques nommées par hash
* `Dépendance` Ajout de LiteRT-LM 0.15.0 pour la génération de texte sur CPU dans l'appareil

##### Autres versions

* [CHANGELOG-fr.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/assets/doc/CHANGELOG-fr.md)

******

### Build

******

```powershell
.\gradlew.bat :app:assembleDebug
```

Build de version:

```powershell
.\gradlew.bat :app:assembleRelease
```

Les paramètres viennent de `version.properties`. Le SDK minimal actuel est 24, le SDK cible est 36 et JDK 21 ou ultérieur est requis.

L'ABI du protocole est fournie par les AAR locaux du dépôt dans `libs`:

```text
common-plugin-api.aar
protocol-wire-api.aar
ai-common-api.aar
ai-provider-api.aar
```

Le runtime utilise LiteRT-LM 0.15.0 depuis Maven. Les builds de version conservent les classes LiteRT-LM et produisent deux APK ABI ainsi qu'un APK universal.

******

### Licence

******

Le code source du projet est sous MPL-2.0. LiteRT-LM et les autres composants tiers conservent leurs licences respectives.

******

### Organisation des ressources

******

```text
.readme/lang_*.json
.changelog/lang_*.json
.python/generate_markdown.py
app/src/main/assets/doc/CHANGELOG-*.md
app/src/main/res/values-*/strings.xml
```

`.python/generate_markdown.py` produit les README et changelogs intégrés en 10 langues à partir des sources JSON. Les chaînes Android restent dans leurs propres dossiers de ressources.

******

### Liens

******

- Documentation AutoJs6: https://docs.autojs6.com
- Projet LiteRT-LM: https://github.com/google-ai-edge/LiteRT-LM
