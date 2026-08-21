<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/res/mipmap/ic_launcher_on_device_ai.png?raw=true" alt="on-device-ai-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin IA locale. Génère du texte en streaming sur l'appareil avec LiteRT-LM, sans réseau</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-On-Device-AI?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-On-Device-AI?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Langues

******

Le fichier README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-en.md)
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/.readme/README-ar.md)

******

### Introduction

******

On-Device AI est le plugin officiel de génération de texte IA locale pour AutoJs6. Il exécute sur le CPU les modèles LiteRT-LM importés par l'utilisateur, accepte un historique de messages en texte brut et renvoie du texte brut via une session de streaming contrôlée. Toute l'inférence se fait localement : aucun accès réseau, aucune donnée envoyée.

******

### Fonctions

******

- Importer un paquet de modèle `.litertlm` avec le sélecteur système Android et conserver une copie vérifiée dans le stockage privé de l'application.
- Contrôler le stockage privé avant d’ouvrir le sélecteur, afficher le budget d’import actuel et l’espace estimé de la copie privée, puis revérifier le fichier sélectionné avant la copie.
- Créer des requêtes de génération locale avec un historique system, user et assistant en texte brut.
- Transmettre `temperature`, `topK`, `topP` et `maxTokens` depuis `ai.ask`, `ai.chat` et `ai.stream` d'AutoJs6 jusqu'à LiteRT-LM.
- Réutiliser l'Engine initialisé selon le SHA-256 du modèle afin d'éviter un nouveau démarrage à froid pour les requêtes consécutives sur le même modèle.
- Initialiser facultativement chaque modèle importé une fois, conserver son état Disponible/Incompatible et relancer la vérification depuis le gestionnaire de modèles.
- Transmettre les chunks de texte dans l'ordre avec une contre-pression par credits et publier un seul état terminal terminé, échoué ou annulé.
- Lister, sélectionner et renommer les modèles importés, supprimer les modèles non sélectionnés et récupérer les fichiers de modèle non référencés depuis le gestionnaire.
- Fonctionner entièrement sur l'appareil avec un backend CPU, sans téléchargement de modèle ni service d'inférence distant.

******

### Formats du modèle et des données

******

La version 1 déclare uniquement le périmètre suivant:

```text
model package: .litertlm
input: text/plain message history
output: streamed text/plain chunks
runtime: LiteRT-LM 0.15.0
```

******

### Interface du plugin

******

L'hôte découvre et appelle le plugin avec les identités suivantes:

```text
service action: org.autojs.plugin.ON_DEVICE_AI
plugin id: on-device-ai
protocol provider id: autojs6.on-device-ai
engine: on-device-ai
variant: default
protocol: V1.1
required host build: 5276
```

Le plugin déclare une exécution ON_DEVICE et le mode credential NONE. Il déclare seulement la capacité `streaming` et des entrées et sorties `text/plain`.

La build hôte 5276 ou ultérieure est requise. Les versions incluent les variantes APK arm64-v8a, x86_64, universal.

******

### État de l'intégration hôte

******

> Dans AutoJs6 (build 5276 et ultérieur), `ai.ask`, `ai.chat` et `ai.stream` prennent en charge la route de plugin local : passez `plugin: true` pour sélectionner ce plugin, et l'ID de modèle peut être omis lorsqu'un seul modèle est importé ; `ai.models({ plugin: true })` énumère les modèles importés. Si le plugin n'est pas installé, désactivé dans le Centre de plugins ou sans modèle, les scripts reçoivent une erreur claire. Le sélecteur explicite `plugin: { component, providerId, modelId }` reste pris en charge.

******

### Sécurité et confidentialité

******

Le plugin ne demande aucune permission réseau ou de stockage. Il lit le modèle uniquement avec un URI accordé par le sélecteur système, calcule SHA-256 pendant la copie vers le dossier privé `files/models`, appelle fsync et l'active par remplacement atomique du pointer dans ce dossier. Les services vérifient aussi le nom du paquet AutoJs6, le propriétaire de l'UID appelant et les signatures correspondantes.

******

### Limites opérationnelles

******

- Une importation de modèle est limitée à 8 GiB et doit laisser au moins 256 MiB libres.
- Un coordinateur d'import unique à portée application maintient le travail pendant la recréation de Activity. Un pending journal synchronisé par fsync permet la récupération au démarrage à froid et le nettoyage des fichiers temporaires stale `.incoming`, `.current` et `.pending`. La récupération supprime uniquement une destination créée par la tentative actuelle et jamais publiée par current metadata; les générations publiées, current et historiques nommées par hash sont conservées.
- Pour éviter les conditions de concurrence interprocessus avec le processus isolé `:provider`, les imports ne suppriment pas automatiquement les générations précédentes nommées par hash SHA-256. Le gestionnaire peut supprimer les modèles non sélectionnés du catalogue et récupérer les fichiers nommés par hash qui ne sont plus référencés.
- Une seule session de génération peut être active dans le processus. Les descripteurs sont dupliqués avant le travail asynchrone et fermés selon les quotas du protocole.
- Le provider ne conserve qu'un seul Engine initialisé. Les requêtes consécutives sur le même modèle le réutilisent; il est libéré immédiatement lors d'un changement de modèle, après cinq minutes d'inactivité ou, en toute sécurité, après la session active lorsqu'Android signale explicitement une pression mémoire.
- La vérification d'un modèle prouve uniquement que `Engine.initialize()` réussit sur l'appareil et le runtime inclus actuels; elle n'évalue pas la qualité de sortie et peut être relancée après un changement d'appareil ou de runtime.
- Le provider annonce un plafond de contexte de 256 KiB et un plafond de sortie de 64 KiB. Les requêtes et modèles peuvent imposer des limites inférieures.
- `maxTokens` accepte les entiers de 1 à 2 147 483 647. `temperature` doit être fini et positif ou nul, `topK` un entier positif et `topP` fini entre 0 et 1. Omettre les trois réglages d'échantillonnage conserve les valeurs du modèle ou moteur ; un remplacement partiel complète les réglages omis avec la base LiteRT-LM `topK: 1`, `topP: 0.95` et `temperature: 1`.
- Le streaming utilise des credits finis et des chunks bornés pour éviter les tampons illimités ou les callbacks sans contre-pression.
- L'annulation, la fermeture de session et le timeout arrêtent la publication et terminent la requête avec un seul état terminal.

******

### Capacités non déclarées

******

- Reasoning, tools, structured JSON et usage ne sont pas déclarés.
- Les messages de rôle tool, les schemas d'outils, les tool calls et les tool results ne sont pas acceptés.
- Aucune découverte réseau de modèles, aucun téléchargement, aucune inférence cloud et aucun flux credential ne sont fournis.
- Aucun backend GPU ou NPU n'est déclaré. L'extension `.litertlm` seule ne garantit pas que le runtime LiteRT-LM actuel puisse charger le modèle.

******

### Feuille de route

******

La feuille de route est organisée en fonctionnalités livrables, chacune vérifiable séparément

- [Consulter ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/ROADMAP.md)

******

### Historique des versions

******

# v1.1.0

###### 2026/08/20

* `Fonction` Plugin renommé On-Device AI, positionné comme le plugin IA locale officiel d'AutoJs6
* `Fonction` Compatible avec le sélecteur abrégé `plugin: true` de `ai.ask`/`ai.chat`/`ai.stream` et l'énumération de modèles `ai.models` d'AutoJs6
* `Fonction` Transmission de `temperature`, `topK`, `topP` et `maxTokens` par le protocole On-Device AI 1.1 vers les contrôles d'échantillonnage et de tokens de sortie de LiteRT-LM
* `Amélioration` Description du plugin, instructions et README en 10 langues mis à jour pour refléter la formalisation de la route de plugin local `ai.*`
* `Amélioration` ROADMAP réécrite comme feuille de route de fonctionnalités avec des éléments vérifiables individuellement

# v1.0.0

###### 2026/08/08

* `Fonction` Provider sur l'appareil pour le protocole On-Device AI V1 avec ID et moteur `on-device-ai`, provider ID `autojs6.on-device-ai` et variante `default`
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

* [CHANGELOG-fr.md](https://github.com/SuperMonster003/AutoJs6-Plugin-On-Device-AI/blob/master/app/src/main/assets/doc/CHANGELOG-fr.md)

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
on-device-ai-api.aar
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
