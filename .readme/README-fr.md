<!--suppress HtmlDeprecatedAttribute, HttpUrlsUsage -->

<div align="center">
  <p>
    <img src="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/res/mipmap/ic_launcher_ai.png?raw=true" alt="ai-text-generation-ic-launcher" border="0" width="128" />
  </p>

  <p>Plugin local de génération de texte IA. Diffusion de texte brut sur l'appareil avec LiteRT-LM</p>

  <p>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/releases"><img alt="GitHub release (latest by date)" src="https://img.shields.io/github/v/release/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?label=Release"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/issues"><img alt="GitHub closed issues" src="https://img.shields.io/github/issues/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=A24232&label=Issues"/></a>
    <a href="https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/LICENSE"><img alt="GitHub License" src="https://img.shields.io/github/license/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation?color=534BAE&label=License"/></a>
  </p>
</div>

******

### Langues

******

Le fichier README.md actuel prend en charge les langues suivantes:

- [简体中文 [zh-Hans]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hans.md)
- [繁體中文 (香港) [zh-Hant-HK]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-HK.md)
- [繁體中文 (台灣) [zh-Hant-TW]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-zh-Hant-TW.md)
- [English [en]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-en.md)
- Français [fr] # actuel
- [Español [es]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-es.md)
- [日本語 [ja]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ja.md)
- [한국어 [ko]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ko.md)
- [Русский [ru]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ru.md)
- [العربية [ar]](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/.readme/README-ar.md)

******

### Introduction

******

AI Text Generation est un provider autonome sur l'appareil pour la version 1 du protocole AI Text Generation d'AutoJs6. Il exécute sur CPU un modèle LiteRT-LM importé par l'utilisateur, accepte un historique de messages en texte brut et renvoie du texte brut dans une session de streaming contrôlée.

******

### Fonctions

******

- Importer un paquet de modèle `.litertlm` avec le sélecteur système Android et conserver une copie vérifiée dans le stockage privé de l'application.
- Créer des requêtes de génération locale avec un historique system, user et assistant en texte brut.
- Transmettre les chunks de texte dans l'ordre avec une contre-pression par credits et publier un seul état terminal terminé, échoué ou annulé.
- Lister le modèle actuellement importé et publier une nouvelle generation de liste après son remplacement.
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
service action: org.autojs.plugin.AI_TEXT_GENERATION
plugin id: ai-text-generation
protocol provider id: autojs6.local.text
engine: ai-text-generation
variant: default
protocol: V1
required host build: 5270
```

Le plugin déclare une exécution ON_DEVICE et le mode credential NONE. Il déclare seulement la capacité `streaming` et des entrées et sorties `text/plain`.

La build hôte 5270 ou ultérieure est requise. Les versions incluent les variantes APK arm64-v8a, x86_64, universal.

******

### État de l'intégration hôte

******

> AutoJs6 fournit désormais des routes de production publiques explicites pour `ai.ask(..., { plugin: ... })`, `ai.chat(..., { plugin: ... })` limité au prompt et `ai.stream(..., { plugin: ... })`, avec sélection exacte du composant/provider/modèle et sans repli cloud. Le public ask avec plugin/modèle réel dispose d'une preuve L3 sur appareil ; stream et chat avec provider factice déterministe disposent d'une preuve Android L2. Installer le plugin ne redirige toujours pas le comportement `ai.*` historique : le script doit fournir le sélecteur explicite et un modelId importé. Chat/stream avec modèle réel et l'annulation active sur appareil restent une dette de preuve non bloquante.

******

### Sécurité et confidentialité

******

Le plugin ne demande aucune permission réseau ou de stockage. Il lit le modèle uniquement avec un URI accordé par le sélecteur système, calcule SHA-256 pendant la copie vers le dossier privé `files/models`, appelle fsync et l'active par remplacement atomique du pointer dans ce dossier. Les services vérifient aussi le nom du paquet AutoJs6, le propriétaire de l'UID appelant et les signatures correspondantes.

******

### Limites opérationnelles

******

- Une importation de modèle est limitée à 8 GiB et doit laisser au moins 256 MiB libres.
- Un coordinateur d'import unique à portée application maintient le travail pendant la recréation de Activity. Un pending journal synchronisé par fsync permet la récupération au démarrage à froid et le nettoyage des fichiers temporaires stale `.incoming`, `.current` et `.pending`. La récupération supprime uniquement une destination créée par la tentative actuelle et jamais publiée par current metadata; les générations publiées, current et historiques nommées par hash sont conservées.
- Pour éviter les conditions de concurrence interprocessus avec le processus isolé `:provider`, un import de remplacement conserve les générations précédentes nommées par hash SHA-256. Ces fichiers continuent d'occuper le stockage privé de l'application.
- Une seule session de génération peut être active dans le processus. Les descripteurs sont dupliqués avant le travail asynchrone et fermés selon les quotas du protocole.
- Le provider annonce un plafond de contexte de 256 KiB et un plafond de sortie de 64 KiB. Les requêtes et modèles peuvent imposer des limites inférieures.
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

La feuille de route est structurée autour de 28 résultats produit vérifiables. D0, D1 et D2 sont terminés ; D3, catalog multi-modèle et sélection, reste l'étape actuelle, et son résultat concernant les metadata stables du modèle, l'état de sélection et les imports idempotents d'un même SHA-256 a atteint L1. La production est `1f92414` et les tests ciblés sont `c38a016` ; une seule invocation de `:app:testDebugUnitTest :app:assembleDebug` a obtenu `BUILD SUCCESSFUL` en 35 s, avec 48 tâches et 16 rapports XML couvrant 44 tests, 0 échec/erreur/ignoré, dont 5 cas `ModelCatalogTest` et 4 `PendingModelTransactionPolicyTest`, tandis que le hash de version est resté inchangé. Aucun ADB ni fault injection n'a été exécuté ; la migration L2, le pager/listing et l'UI restent ouverts. L1 couvre l'implémentation, les tests ciblés et le build affecté ; L2 couvre Android/Binder ; L3 couvre une preuve sur appareil avec plugin/modèle réel signé, et l'absence de preuve supérieure ne rouvre pas un résultat terminé.

- [Consulter ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/ROADMAP.md)

******

### Historique des versions

******

# v1.0.0

###### 2026/08/08

* `Fonction` Provider sur l'appareil pour le protocole AI Text Generation V1 avec ID et moteur `ai-text-generation`, provider ID `autojs6.local.text` et variante `default`
* `Fonction` Génération de texte brut LiteRT-LM sur CPU avec historique system, user et assistant et streaming contrôlé par credits
* `Fonction` Import SAF de `.litertlm` dans le stockage privé avec limite de 8 GiB, réserve d'espace, SHA-256, fsync et activation atomique
* `Fonction` Une session active, I/O bornées, quotas de descripteurs, annulation, timeout, un état terminal et vérification de l'appelant AutoJs6 avec la même signature
* `Fonction` Absence explicite des capacités reasoning, tools, structured JSON, usage, réseau et credential
* `Fonction` APK arm64-v8a, x86_64 et universal avec README, changelog, interface Android et instructions du plugin en 10 langues
* `Amélioration` Conservation des générations précédentes nommées par hash SHA-256 après un import de remplacement afin d'éviter les courses interprocessus avec `:provider`, les fichiers conservés continuant d'occuper le stockage privé
* `Amélioration` Ajout d'un coordinateur d'import unique à portée application et d'un pending journal synchronisé par fsync pour résister à la recréation de Activity, récupérer au démarrage à froid, nettoyer les temporaires stale et limiter la suppression aux destinations créées par la tentative actuelle et jamais publiées, tout en conservant les générations publiées, current et historiques nommées par hash
* `Dépendance` Ajout de LiteRT-LM 0.15.0 pour la génération de texte sur CPU dans l'appareil

##### Autres versions

* [CHANGELOG-fr.md](https://github.com/SuperMonster003/AutoJs6-Plugin-AI-Text-Generation/blob/master/app/src/main/assets/doc/CHANGELOG-fr.md)

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
ai-text-generation-api.aar
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
