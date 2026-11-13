<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <picture>
      <source srcset="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap-night/ic_launcher.png?raw=true" media="(prefers-color-scheme: dark)" />
      <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/app/src/main/res/mipmap/ic_launcher.png?raw=true" alt="autojs6-plugin-three-stone-ai-ic-launcher" border="0" width="128" />
    </picture>
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

3-Stone AI est le plugin officiel de génération de texte IA pour AutoJs6. Il exécute les modèles LiteRT-LM importés par l'utilisateur sur un backend CPU explicitement choisi ou sur un GPU compatible, et appelle également les services OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter et OpenAI Compatible configurés par l'utilisateur. AI Provider V2 expose directement ces cibles locales et en ligne dans un catalogue unifié; chaque requête sélectionne une cible explicite, et les cibles locales fonctionnent sans accès réseau ni envoi de données. Les réglages du plugin gèrent les identifiants chiffrés, la cible par défaut (en ligne ou locale), les réseaux facturés et les tests de connexion explicites.

******

### Fonctions

******

- Importer un paquet de modèle `.litertlm` avec le sélecteur système Android et conserver une copie vérifiée dans le stockage privé de l'application.
- Télécharger en une étape, depuis le catalogue intégré, un modèle LiteRT Community recommandé, épinglé et sans authentification: il est écrit dans le stockage privé de l'application et importé automatiquement après vérification exacte de la taille et du SHA-256. Un modèle en cours de téléchargement apparaît comme une carte dans la section "Modèles locaux", avec progression, pause, annulation et nouvelle tentative en cas d'échec, et un téléchargement interrompu reprend à partir de la partie déjà reçue; la confirmation indique la mémoire minimale du modèle et avertit si l'appareil en a moins.
- Contrôler le stockage privé avant d'ouvrir le sélecteur, afficher le budget d'import actuel et l'espace estimé de la copie privée, puis revérifier le fichier sélectionné avant la copie.
- Créer des requêtes de génération locale avec un historique system, user et assistant en texte brut.
- Transmettre `temperature`, `topK`, `topP` et `maxTokens` depuis `ai.ask`, `ai.chat` et `ai.stream` d'AutoJs6 jusqu'à LiteRT-LM.
- Contraindre nativement la sortie avec JSON Schema de LiteRT-LM via `structuredJson` et `responseSchema` d'AutoJs6; les valeurs complètes restent du texte JSON pour `JSON.parse`.
- Rapporter les nombres exacts de tokens d'entrée, de sortie et totaux de LiteRT-LM, ainsi que la durée de génération côté fournisseur, via `ai.chat().usage` et les événements usage du streaming.
- Conserver le contexte multi-tour dans une seule Conversation native LiteRT-LM via `ai.session` d'AutoJs6, en envoyant uniquement le nouveau prompt utilisateur aux tours suivants.
- Réutiliser l'Engine initialisé selon le SHA-256 du modèle afin d'éviter un nouveau démarrage à froid pour les requêtes consécutives sur le même modèle.
- Initialiser facultativement chaque modèle importé une fois, conserver son état Disponible/Incompatible et relancer la vérification depuis le gestionnaire de modèles.
- Transmettre les chunks de texte dans l'ordre avec une contre-pression par credits et publier un seul état terminal terminé, échoué ou annulé.
- Lister, renommer, vérifier et supprimer les modèles importés dans la section "Modèles locaux", chaque carte indiquant l'espace occupé par le modèle, et récupérer en une touche les fichiers de modèle non référencés.
- Sélectionner explicitement le backend `cpu`, `gpu` ou `npu` via AutoJs6; CPU est utilisé par défaut, GPU seulement après une sonde de chargement OpenCL, et NPU est signalé indisponible car son runtime EAP n'est pas inclus.
- Gérer les services en ligne intégrés et personnalisés, les identifiants Android Keystore, la cible par défaut unique (un modèle en ligne ou local), les réseaux facturés et les tests de connexion explicites et limités dans Paramètres > Modèles et services. Les services se modifient sur la page plein écran "Détails du service": chaque service peut contenir plusieurs modèles, que l'on réordonne par glisser-déposer et parmi lesquels on choisit un modèle préféré, et les modifications ne sont enregistrées qu'avec la coche.
- Associer chaque conversation du lanceur à un instantané de cible locale ou en ligne; un nouveau chat est recommandé lors du changement d'une conversation remplie, et continuer exige une confirmation explicite enregistrée.
- Enregistrer la cible, le fournisseur, le modèle et la localité réellement utilisés pour chaque réponse de l'assistant; la régénération réutilise exactement cette cible, échoue explicitement si son identité change ou si elle devient indisponible, sans repli silencieux sur la cible actuelle.
- Maintenir les échecs de génération locale et cloud dans la cible sélectionnée: le chat du lanceur ajoute une raison bornée sans données sensibles, indique qu'aucun repli entre frontières n'a eu lieu et propose un changement manuel explicite de cible sans supprimer la sortie partielle.
- Appels natifs aux outils pour les cibles en ligne compatibles OpenAI, Anthropic Messages et Gemini GenerateContent, avec arguments en streaming, appels parallèles et reprise après résultats.
- Entrée JPEG/PNG et résultats visuels des outils via AI Provider 2.1 négocié, avec réglage par modèle.
- Actualiser les modèles prédéfinis en ligne depuis le catalogue public du projet sur GitHub. La mise à jour automatique est activée par défaut et vérifie une fois toutes les 24 heures à l'ouverture de la page "Modèles et services"; le menu de la section "Services en ligne" permet de la désactiver ou d'actualiser manuellement. Elle respecte le réglage des réseaux facturés, conserve la liste en cache ou intégrée hors ligne ou en cas d'échec, et laisse les services enregistrés et les ID personnalisés inchangés.
- Regrouper les modèles prédéfinis par fournisseur et élargir le choix OpenRouter avec Qwen, Kimi, GLM, Grok, Meta et MiniMax, en conservant les ID exacts.
- Unifier les réglages avec des groupes plats, des lignes cohérentes et des dialogues arrondis centrés. La langue, le mode nuit, la couleur et l'icône ne changent qu'après validation; Annuler conserve les valeurs enregistrées. La couleur suit AutoJs6 par défaut et propose une palette commune, une saisie HEX/RGB et un aperçu local. Les fonds neutres restent stables et les contrôles suivent le thème. L'icône utilise le mode adaptatif automatique par défaut, tout en préservant les choix explicites lors des mises à jour.
- La génération des réponses ne dépend plus de l'écran de discussion: elle continue pendant la rotation de l'écran ou le changement de mode sombre, de langue ou de taille du texte des messages, et une réponse terminée pendant que vous avez quitté la discussion s'affiche en entier à votre retour (une réponse est encore perdue si le système arrête le processus de l'application). Appuyer sur "Arrêter", créer ou changer de conversation, changer de modèle ou supprimer des messages coupe la requête en ligne en moins de 1 seconde, et une réponse arrêtée ne compte plus dans l'utilisation.
- Les services en ligne peuvent définir un effort par défaut (Par défaut, Faible, Moyen, Élevé, Très élevé ou Maximal; avec "Par défaut", le modèle décide), et les services Anthropic peuvent activer le mode rapide par défaut (réponses plus rapides, mais plus chères); les deux peuvent être modifiés pour la conversation en cours seulement, et la modification est enregistrée avec elle. Si un modèle ne prend pas en charge le niveau choisi, le niveau inférieur le plus proche est utilisé. Les scripts qui appellent l'IA via l'interface du plugin n'utilisent pas ces deux réglages.
- Changer de modèle ouvre un panneau depuis le bas: les favoris sont dans le groupe "Favoris" en haut et les autres sont groupés par service, chaque modèle n'apparaissant qu'une fois; les résultats de recherche ne sont pas groupés; chaque ligne indique son service en dessous (la taille du fichier pour les modèles locaux) et des étiquettes pour l'entrée d'images, le raisonnement et le mode rapide; le panneau modifie aussi l'effort et le mode rapide de la conversation en cours, et "Gérer les modèles et services" en bas ouvre les paramètres des modèles. La barre en haut de la discussion affiche l'effort et le mode rapide de la conversation en cours.
- Affiche le raisonnement du modèle (Claude 5 et versions plus récentes, Gemini 2.5 et versions plus récentes, ainsi que les services compatibles OpenAI qui renvoient leur raisonnement, comme DeepSeek): pendant la génération, le raisonnement s'affiche en direct dans une carte au-dessus de la réponse, puis se réduit à "Réflexion pendant N s"; un appui l'ouvre ou la ferme. Les options "Afficher le raisonnement pendant la génération" et "Réduire le raisonnement à la fin" sont activées par défaut. Le raisonnement est enregistré avec la conversation, mais il n'est ni lu à voix haute, ni pris en compte par la recherche ou la copie, et les scripts ne le reçoivent pas.
- L'historique des conversations utilise le format v6, qui enregistre aussi l'effort et le mode rapide de chaque conversation ainsi que le raisonnement et sa durée pour chaque réponse; les conversations enregistrées par la version 1.5.0 et les précédentes s'ouvrent toujours.
- Un micro dans le champ de saisie: touchez-le pour dicter, et le texte reconnu est inséré au curseur pour que vous le vérifiiez avant l'envoi; un appui long lance une conversation vocale qui envoie ce que vous dites et écoute à nouveau après la lecture de la réponse.
- Lecture des réponses à voix haute: appuyez longuement sur une réponse et choisissez "Lire à voix haute", ou activez "Lire les réponses à voix haute" dans Paramètres > Voix pour entendre les nouvelles réponses pendant leur génération. Les marques de titre et de liste ne sont pas lues, et un bloc de code ou un tableau est annoncé en une phrase.
- Répond au `VOICE_COMMAND` de l'assistant numérique de 3-Macaw Speech: maintenez le bouton marche, parlez et touchez "3-Stone AI"; la question est envoyée dans une nouvelle conversation et la réponse lue à voix haute.

******

### Voix

******

La dictée et la lecture utilisent les interfaces système d'Android (`RecognizerIntent` et `TextToSpeech`). 3-Stone AI n'enregistre jamais l'audio lui-même et ne demande pas l'autorisation du micro. Si 3-Macaw Speech est installé, "Privilégier 3-Macaw Speech" dans Paramètres > Voix est activé par défaut: la dictée ouvre sa fenêtre de reconnaissance hors ligne et la lecture utilise ses voix hors ligne. Sinon, ou avec l'option désactivée, l'application de reconnaissance du téléphone et le moteur de synthèse vocale par défaut sont utilisés. Dans la version 1.4.0, chaque tour d'une conversation vocale ouvre l'écran de reconnaissance; pendant la lecture d'une réponse, touchez la barre d'état au-dessus du champ de saisie pour l'interrompre et parler aussitôt, ou utilisez son bouton pour quitter.

- **Plusieurs applications de reconnaissance vocale demandent un choix à chaque fois?** Laissez "Privilégier 3-Macaw Speech" activé pour utiliser directement 3-Macaw Speech. Désactivé, le système décide, et vous pouvez définir une application par défaut dans le sélecteur du système.
- **Des pauses entre les phrases sur un téléphone lent?** Certaines voix (comme MeloTTS et Kokoro) synthétisent moins vite qu'elles ne parlent sur les téléphones lents. Choisissez AISHELL-3, Piper ou Matcha comme voix par défaut dans 3-Macaw Speech.
- **Le micro indique qu'aucun modèle de reconnaissance n'est installé?** La fenêtre de 3-Macaw Speech vous guide pour en télécharger un; d'ici là, 3-Stone AI ne passe pas de lui-même à une autre application. Désactivez "Privilégier 3-Macaw Speech" pour en utiliser une autre en attendant.
- **Comment poser une question via l'assistant numérique de 3-Macaw Speech?** Définissez 3-Macaw Speech comme assistant numérique par défaut, maintenez le bouton marche (ou accueil), parlez, puis touchez "3-Stone AI" sur la carte du résultat. 3-Stone AI ouvre une nouvelle conversation, envoie aussitôt la question et lit la réponse; déverrouillez d'abord le téléphone s'il est verrouillé. Moins de 10 secondes après une question précédente de ce type, le texte est seulement placé dans le champ de saisie et n'est pas envoyé.
- **Où va le texte reconnu?** Au modèle sélectionné, comme un texte saisi; un modèle en ligne l'envoie à son fournisseur. 3-Macaw Speech fonctionne entièrement hors ligne; les autres applications de reconnaissance ou de synthèse peuvent utiliser le réseau.

******

### Formats du modèle et des données

******

Formats de modèles et entrées pris en charge:

```text
model package: .litertlm
input: text/plain history + application/json schema; negotiated 2.1: image/jpeg and image/png descriptors
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
protocol: V2 (2.0 / 2.1)
required host build: 5276
```

AI Provider V2 expose un catalogue paginé unique de cibles `local:*` et `profile:*`. Chaque cible déclare séparément son provider, son modèle, sa localité, sa configuration et sa disponibilité, ses capacités, ses limites, ses contrôles et ses origines HTTPS. Un catalogue uniquement local déclare ON_DEVICE/NONE; la présence de profils en ligne déclare HYBRID/PLUGIN_MANAGED et l'union exacte de leurs origines HTTPS. Le profil backend local reste un contrôle facultatif de la cible, et aucune cible ni aucun profil indisponible ne fait l'objet d'un repli silencieux.

La build hôte 5276 ou ultérieure et Android 7.0 ou ultérieur sont requis. Les builds produisent les APK armeabi-v7a, arm64-v8a, x86, x86_64, universal. Sur x86 et armeabi-v7a, utilisez l'APK individuel correspondant pour l'application, l'IA en ligne et l'intégration hôte. L'inférence locale LiteRT-LM nécessite arm64-v8a ou x86_64 et la bibliothèque native correspondante dans l'APK. L'APK universal contient uniquement des bibliothèques 64 bits et ne peut pas être installé sur un appareil exclusivement 32 bits.

******

### État de l'intégration hôte

******

> Dans AutoJs6 (build 5276 et suivants), `ai.catalog()` renvoie chaque modèle local importé et profile en ligne configuré dans un catalogue de cibles unique, avec ID exact, fournisseur, modèle, localité, configuration, disponibilité, capacités, contrôles, limites, origine et profiles backend locaux. Passez un `target` exact à `ai.ask`, `ai.chat`, `ai.stream` ou `ai.session` ; `target` seul sélectionne le plugin officiel 3-Stone AI, tandis que `plugin: true` utilise sa cible par défaut déclarée. Les cibles locales peuvent proposer `cpu`, `gpu` et `npu` indisponible ; les cibles en ligne n'ont aucun profile d'exécution local. Un backend ou une cible indisponible échoue sans repli et les routes locale/en ligne ne changent jamais automatiquement. Les réponses terminées et diffusées exposent target, plugin, profile, raisonnement, motif de fin, usage complet et durée mesurée par le fournisseur. Des erreurs stables distinguent fournisseur absent ou désactivé, cible inconnue, non configurée, indisponible ou aux capacités incompatibles et backend indisponible. `responseSchema` active la sortie structurée ; `structuredJson: true` sans schema utilise une racine object par défaut et les sessions persistantes fixent le même target, schema et backend facultatif pour tous les tours.

Les applications signées séparément demandent un accès IA explicite dans le module, lié à leur identité et installation; la révocation dans les réglages arrête les requêtes actives. Cette autorisation couvre les modèles locaux et les comptes en ligne enregistrés. Les appels en ligne peuvent consommer un quota payant. Utilisez ai.hasAccess() et ai.requestAccess() dans Node.js, Bun ou Workers.

******

### Sécurité et confidentialité

******

Le plugin demande `INTERNET` pour les téléchargements de modèles recommandés déclenchés par l'utilisateur, les requêtes vers une cible en ligne configurée et les mises à jour du catalogue public de modèles prédéfinis depuis GitHub sur la page "Modèles et services". Ces mises à jour ne nécessitent aucune clé API, ne lancent aucune inférence et ne démarrent ni à l'activation du plugin ni pendant la génération locale. Les identifiants restent dans le stockage privé chiffré du plugin et ne traversent jamais Binder ni le catalogue de cibles. Le plugin ne demande aucune permission générale de stockage. Les téléchargements de fichiers de modèles utilisent des révisions HTTPS immuables, une taille et un SHA-256 épinglés, et n'écrivent que dans un fichier partiel du stockage privé `files/models` (reprise possible après une interruption); le modèle n'est confié à l'import pour une activation atomique qu'après la réussite de l'en-tête LiteRT-LM, de la taille, de l'empreinte, du flush et du fsync. Le service Provider original de l'hôte vérifie aussi le paquet AutoJs6, l'UID appelant et les signatures. Les fonctions vocales ne demandent pas l'autorisation du micro: c'est l'application de reconnaissance qui enregistre. Le texte reconnu et le texte lu ne sont pas journalisés, et les messages dictés sont enregistrés comme les messages saisis. Le raisonnement renvoyé par un modèle est enregistré uniquement dans l'historique local des conversations; il n'est pas renvoyé au service en ligne lors des tours suivants, et les scripts ne le reçoivent pas via l'interface du plugin.

******

### Limites opérationnelles

******

- Une importation de modèle est limitée à 8 GiB et doit laisser au moins 256 MiB libres.
- Un seul téléchargement de modèle inachevé est conservé; lancer celui d'un autre modèle l'abandonne (la confirmation le précise). Le téléchargement est écrit dans `files/models/.download-<sha256>.part`: une pause, une coupure réseau, la fermeture de l'application ou l'arrêt du processus conservent ce fichier, et la reprise ou une nouvelle tentative revérifie d'abord la partie reçue avant de continuer; une annulation, un contenu invalide ou une empreinte non conforme le suppriment. Au démarrage à froid, le fichier partiel le plus récent d'un modèle recommandé non encore importé est restauré en pause et les autres sont supprimés.
- Les modèles locaux téléchargés et importés sont stockés dans les données privées de l'application (`files/models`), et l'espace qu'ils occupent est indiqué sur les cartes de la section "Modèles locaux" et dans le panneau de changement de modèle. Désinstaller le plugin ou effacer ses données supprime aussi ces modèles; les fichiers de modèle téléchargés auparavant ailleurs peuvent être ajoutés avec "Importer un fichier de modèle".
- Un coordinateur d'import unique à portée application maintient le travail pendant la recréation de Activity. Un pending journal synchronisé par fsync permet la récupération au démarrage à froid et le nettoyage des fichiers temporaires stale `.incoming`, `.current` et `.pending`. La récupération supprime uniquement une destination créée par la tentative actuelle et jamais publiée par current metadata; les générations publiées, current et historiques nommées par hash sont conservées.
- Pour éviter les conditions de concurrence interprocessus avec le processus isolé `:provider`, les imports ne suppriment pas automatiquement les générations précédentes nommées par hash SHA-256. La section "Modèles locaux" permet de supprimer des modèles du catalogue et de récupérer les fichiers nommés par hash qui ne sont plus référencés.
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
- Dans Paramètres > Modèles et services, ouvrez les "Détails du service" et activez "Entrée d'images" depuis le menu de ligne de chaque modèle de la liste qui accepte les images. Elle est désactivée par défaut pour les services existants. Les images sont envoyées uniquement à ce service. LiteRT et ai.session persistant restent textuels; les captures Agent nécessitent Android 11+, des versions compatibles de AutoJs6 et AI Agent, le groupe observe et l'entrée d'images activée pour le modèle exact sélectionné.
- AiGoCode gpt-5.6-sol a réussi les tests réels avec image initiale et image dans un résultat d'outil sur Provider 1.2.0 / build 218. Ces images synthétiques ne prouvent ni la compatibilité des autres cibles ni la réussite d'une tâche visuelle Agent complète.
- Les échecs en ligne exposent uniquement des catégories fixes `ONLINE_*` dans le champ facultatif `providerCode`. Les exceptions inconnues restent sans catégorie; les URL, identifiants, réponses du service et textes originaux des exceptions sont exclus. Le code d'échec et la politique de nouvelle tentative restent inchangés.

******

### Capacités non déclarées

******

- L'interface du plugin ne déclare pas la sortie reasoning, donc les scripts ne reçoivent pas le raisonnement (il n'est affiché que dans la discussion du lanceur). Les cibles LiteRT-LM locales ne déclarent toujours pas tools.
- Les outils natifs autorisent 16 tours et 32 appels en attente au maximum. Les résultats doivent correspondre exactement au lot attendu; limites de contexte/sortie, annulation et délai initial restent actifs. Les outils natifs ne se combinent pas avec les tours persistants ai.session; l'historique initial refuse le rôle tool.
- Le téléchargement de fichiers de modèles depuis une URL arbitraire n'est pas pris en charge. Les modèles prédéfinis peuvent être actualisés depuis le catalogue public du projet, sans créer de services ni vérifier les droits du compte. Le catalogue de cibles ne présente que les modèles locaux importés et les services en ligne explicitement configurés; les téléchargements locaux restent limités aux recommandations intégrées et épinglées.
- L'inférence NPU n'est pas déclarée: le profil reste visible comme `unavailable` avec `npu-runtime-not-packaged`. GPU n'est déclaré que si `libOpenCL.so` est chargeable, et l'extension `.litertlm` ne garantit toujours pas l'initialisation du modèle.

******

### Feuille de route

******

La feuille de route est organisée en fonctionnalités livrables, chacune vérifiable séparément

- [Consulter ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Historique des versions

******

# v1.7.0

###### 2026/11/12

* `Fonctionnalité` Vous pouvez désormais ajouter des pièces jointes dans le chat: touchez "+" à gauche du champ de saisie pour choisir des images ou des fichiers, jusqu'à 20 par message. Sont pris en charge les images (5 Mo maximum chacune; les images trop grandes sont réduites automatiquement), les fichiers texte UTF-8 (par exemple txt, md, json, csv et fichiers de code, 256 Ko maximum) et les PDF (32 Mo et 100 pages maximum). Les images nécessitent que "Entrée d'images" soit activé pour le modèle actuel; les PDF ne peuvent pour l'instant être envoyés qu'aux services dont le fournisseur est Anthropic ou Gemini (avec Gemini, les pièces jointes d'une requête ne doivent pas dépasser 20 Mo au total); les modèles locaux ne prennent pas encore en charge les pièces jointes. Les pièces jointes que le modèle actuel ne prend pas en charge sont estompées; retirez-les ou changez de modèle avant d'envoyer
* `Fonctionnalité` Les pièces jointes sont enregistrées avec le message dans les données de l'application: le message affiche les miniatures des images ou les noms des fichiers, et un appui ouvre la pièce jointe dans une autre application; les pièces jointes choisies mais pas encore envoyées sont conservées après une rotation de l'écran ou la fermeture de l'application. Quand la conversation continue plus tard, les pièces jointes précédentes sont renvoyées au modèle dans la limite du contexte; pour celles qui ne tiennent pas, seul leur nom est indiqué au modèle. Les pièces jointes d'une conversation ne peuvent pas dépasser 48 Mo au total; supprimer une conversation supprime ses pièces jointes, et "Nettoyer les fichiers non référencés" dans la page "Modèles et services" supprime aussi les pièces jointes qui ne sont plus utilisées
* `Correctif` Dans les longues conversations, les messages plus anciens sont résumés automatiquement pour économiser le contexte, mais certains services en ligne et modèles locaux entouraient le résumé de marqueurs de bloc de code Markdown ou y ajoutaient du texte explicatif, si bien que le résumé échouait et que le modèle oubliait plus facilement le début de la conversation; ces marqueurs et ce texte sont désormais retirés avant la lecture du résumé
* `Correctif` Après la suppression d'un modèle local, les fichiers de cache créés lors de son premier chargement (jusqu'à plusieurs Go par modèle) restaient dans le cache de l'application et continuaient d'occuper de l'espace; supprimer un modèle local supprime désormais aussi ces caches. Les caches laissés par des modèles supprimés auparavant sont nettoyés automatiquement à la prochaine ouverture de ce plugin, ou avec "Nettoyer les fichiers non référencés" dans la page "Modèles et services"; les caches des modèles locaux encore présents ne sont pas touchés
* `Amélioration` Paramètres réorganisés: l'accueil ne contient plus que les réglages d'apparence et 5 entrées, "Conversation", "Modèles et services", "Voix", "Confidentialité et autorisations" et "Mises à jour et à propos", et chaque réglage n'apparaît qu'à un seul endroit; "Génération" dans "Conversation" devient "Longueur de sortie et sampling", son formulaire ne se ferme plus en glissant vers le bas ni en touchant à l'extérieur et demande confirmation en cas de modifications non enregistrées; l'historique des versions est déplacé dans "Mises à jour et à propos". Le menu et le panneau latéral de la page de discussion n'ont plus d'entrée "Modèles" séparée, tout passe par "Paramètres"; ouvrir les paramètres de ce plugin depuis AutoJs6 mène directement à "Modèles et services", et Retour ramène à l'accueil des paramètres
* `Amélioration` Les anciennes pages "Modèles" et "Services en ligne" sont fusionnées en une seule page "Modèles et services": en haut, la cible par défaut et "Autoriser les réseaux mobiles et facturés", puis deux sections, "Services en ligne" et "Modèles locaux". Chaque service et chaque modèle local s'affiche sous la même forme de carte, avec une étiquette "Par défaut", "Prêt", "Clé API manquante" ou "Indisponible"; les actions comme définir par défaut, tester, renommer, cloner, effacer la clé API et supprimer se trouvent dans le menu à droite de chaque carte, tandis qu'importer / exporter des services, mettre à jour les modèles prédéfinis et nettoyer les fichiers non référencés se trouvent dans le menu à droite du titre de chaque section. Touchez un modèle local pour voir ses détails et copier son ID. Les profils en ligne s'appellent désormais partout "services"
* `Amélioration` L'ancienne "Cible en ligne par défaut" et le modèle local sélectionné sont fusionnés en une seule "Cible par défaut", qui peut être n'importe quel modèle en ligne ou local et sert aux nouvelles conversations et aux scripts qui n'indiquent pas de modèle; après la mise à jour, votre choix par défaut précédent est conservé. Si la cible par défaut est momentanément indisponible (par exemple après avoir effacé sa clé API), un autre modèle disponible est utilisé automatiquement jusqu'à ce qu'elle redevienne disponible; si son service ou son modèle est supprimé, un autre modèle disponible est également utilisé. Le "Modèle par défaut" d'un service s'appelle désormais "Modèle préféré"
* `Amélioration` Les services s'ajoutent et se modifient désormais sur une page plein écran "Détails du service" au lieu de l'ancien formulaire affiché depuis le bas de l'écran: les informations de connexion (nom du service, fournisseur, Base URL, clé API, etc.) sont en haut et la "Liste des modèles" en dessous; appuyez longuement sur une ligne ou faites glisser sa poignée pour changer l'ordre, ou utilisez le menu de chaque ligne pour la définir comme modèle préféré, la marquer pour l'"Entrée d'images", la monter, la descendre ou la retirer, et ajoutez plusieurs modèles à la fois. Les anciens sélecteurs "ID du modèle", "Modèle par défaut" et "Modèles d'entrée d'images" disparaissent donc. Appuyez sur la coche en haut à droite pour enregistrer, puis testez la connexion sur la même page; revenir en arrière avec des modifications non enregistrées demande d'abord confirmation, et ce que vous avez saisi est conservé quand l'écran pivote ou que le système récupère l'application en arrière-plan. Après un changement d'ordre, la cible par défaut et les favoris désignent toujours les mêmes modèles
* `Amélioration` Le téléchargement d'un modèle local recommandé se fait désormais en une étape: après confirmation, le modèle est téléchargé dans le stockage privé de l'application et importé automatiquement une fois vérifié, sans plus passer par "Choisir l'emplacement" puis "Importer le modèle téléchargé". Pendant le téléchargement, la page "Modèles et services" affiche une carte de téléchargement qui permet de suspendre, reprendre ou annuler, et de réessayer après un échec; si le réseau est coupé ou que l'application est fermée, la partie déjà téléchargée est conservée et la reprise continue là où elle s'était arrêtée. La confirmation du téléchargement indique la mémoire minimale du modèle et avertit lorsque l'appareil en a moins, sans empêcher le téléchargement. Les fichiers de modèle téléchargés ailleurs auparavant peuvent toujours être ajoutés avec "Importer un fichier de modèle". Les modèles téléchargés sont stockés dans les données de l'application et sont supprimés quand ces données sont effacées ou que l'application est désinstallée
* `Amélioration` Dans le panneau de changement de modèle, chaque modèle n'apparaît plus qu'une fois: les favoris sont affichés uniquement dans le groupe "Favoris" en haut, et non plus aussi sous leur service, et reprennent leur place quand on les retire des favoris; les résultats de recherche ne sont plus groupés et s'affichent en une seule liste dans l'ordre habituel. Chaque ligne indique son service en dessous, et les modèles locaux indiquent la taille de leur fichier; le groupe "Sur cet appareil" est renommé "Modèles locaux"

# v1.6.0

###### 2026/11/12

* `Fonctionnalité` Les profils en ligne peuvent définir un effort par défaut (Faible, Moyen, Élevé, Très élevé ou Maximal; avec "Par défaut", le modèle décide), et les profils Anthropic peuvent utiliser le mode rapide par défaut (réponses plus rapides, mais plus chères); les deux peuvent aussi être modifiés pour la conversation en cours seulement, la modification est enregistrée avec la conversation, et les nouvelles conversations partent des valeurs du profil. Si un modèle ne prend pas en charge le niveau choisi, le niveau inférieur le plus proche est utilisé
* `Fonctionnalité` Changer de modèle ouvre désormais un panneau depuis le bas: recherche, modèles groupés par profil, favoris épinglés en haut et étiquettes indiquant quels modèles prennent en charge l'entrée d'images, le raisonnement et le mode rapide; le panneau modifie aussi l'effort et le mode rapide de la conversation en cours, et "Gérer les modèles et services" en bas ouvre les paramètres des modèles. La barre en haut de la discussion affiche l'effort et le mode rapide de la conversation en cours
* `Fonctionnalité` Le chat montre maintenant le raisonnement du modèle (Claude 5 et versions plus récentes, Gemini 2.5 et versions plus récentes, ainsi que les services compatibles OpenAI qui renvoient leur raisonnement, comme DeepSeek): pendant la génération, le raisonnement s'affiche en direct dans une carte au-dessus de la réponse; une fois la réponse terminée, la carte se réduit à "Réflexion pendant N s", et un appui l'ouvre ou la ferme. Le raisonnement est enregistré avec la conversation, mais il n'est pas lu à voix haute et la recherche dans les messages ne le prend pas en compte. Les options "Afficher le raisonnement pendant la génération" et "Réduire le raisonnement à la fin" sont activées par défaut et peuvent être désactivées. Les scripts qui appellent l'IA via le plugin ne reçoivent pas le raisonnement
* `Correctif` Une réponse en cours de génération était arrêtée lors de la rotation de l'écran ou du changement de mode sombre, de langue ou de taille du texte des messages; la réponse continue désormais à se générer, et une réponse terminée pendant que vous avez quitté la discussion s'affiche en entier à votre retour
* `Correctif` Après avoir appuyé sur "Arrêter", la requête en ligne continuait en arrière-plan jusqu'à la fin de la réponse (et comptait toujours dans l'utilisation); appuyer sur Arrêter coupe désormais la requête en moins de 1 seconde, de même que créer une conversation, changer de conversation, changer de modèle ou supprimer des messages
* `Correctif` Les modèles Claude récents refusaient les requêtes des profils Anthropic qui définissaient la température, Top K ou Top P; les profils Anthropic n'envoient plus ces trois paramètres, et la page des paramètres l'indique
* `Correctif` Après la modification du modèle par défaut d'un profil, une nouvelle conversation déjà ouverte et sans message gardait l'ancien modèle par défaut; elle passe désormais au nouveau modèle par défaut (sauf si vous avez choisi un autre modèle dans cette conversation)
* `Correctif` Lors de l'ajout ou de la modification d'un profil en ligne, balayer vers le bas sur le formulaire le fermait facilement et tout ce que vous aviez saisi était perdu; le formulaire ne se ferme désormais qu'avec le bouton de fermeture en haut à droite, "Annuler" ou Retour, demande confirmation avant d'abandonner les modifications non enregistrées et conserve votre saisie quand l'écran pivote
* `Correctif` Échec de la mise à jour automatique du catalogue de modèles en ligne après le déplacement de la documentation Gemini et le changement du format de ses tableaux

# v1.5.0

###### 2026/11/04

* `Fonctionnalité` Les applications signées séparément demandent un accès IA explicite dans le module, lié à leur identité et installation; la révocation dans les réglages arrête les requêtes actives
* `Fonctionnalité` L'écran reste allumé pendant la génération d'une réponse au lieu de s'éteindre à l'expiration du délai
* `Correctif` Les requêtes AI suivantes échouaient lorsque l'arrêt du service abandonnait la fermeture d'une session
* `Correctif` Les requêtes AI successives étaient rejetées pendant la fermeture de la session annulée précédente
* `Correctif` Les profils Anthropic envoyaient toujours max_tokens=1024 même lorsque le maximum de jetons de sortie était illimité, si bien que les réponses étaient coupées vers 1024 jetons sans aucun avertissement (les modèles Claude qui réfléchissent par défaut y comptent aussi leur réflexion); la valeur par défaut passe à 16384 jetons et une réponse arrêtée à la limite de sortie est signalée sous le message par "Limite de sortie atteinte" (également pour les profils compatibles OpenAI et Gemini)
* `Correctif` Le test de connexion n'accordait au modèle que 8 jetons de sortie; les modèles Claude qui réfléchissent par défaut les épuisaient en réflexion sans renvoyer de texte, si bien qu'un profil Anthropic correctement configuré était signalé comme réponse invalide. Le test accorde désormais 1024 jetons
* `Correctif` Lorsque la génération dans le cloud ou le test de connexion échouait, seule une catégorie d'échec fixe était affichée et la cause réelle (par exemple un solde de compte épuisé) restait invisible; le statut HTTP et l'explication du fournisseur lui-même (300 caractères au plus, clés masquées) apparaissent désormais sous le message d'échec

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/docs/16kb.md)
