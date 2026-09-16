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

3-Stone AI est le plugin officiel de génération de texte IA pour AutoJs6. Il exécute les modèles LiteRT-LM importés par l'utilisateur sur un backend CPU explicitement choisi ou sur un GPU compatible, et appelle également les profils OpenAI, Anthropic, Gemini, DeepSeek, OpenRouter et OpenAI Compatible configurés par l'utilisateur. AI Provider V2 expose directement ces cibles locales et en ligne dans un catalogue unifié; chaque requête sélectionne une cible explicite, et les cibles locales fonctionnent sans accès réseau ni envoi de données. Les réglages du plugin gèrent les identifiants chiffrés, la cible en ligne par défaut, les réseaux facturés et les tests de connexion explicites.

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
- Enregistrer la cible, le fournisseur, le modèle et la localité réellement utilisés pour chaque réponse de l'assistant; la régénération réutilise exactement cette cible, échoue explicitement si son identité change ou si elle devient indisponible, sans repli silencieux sur la cible actuelle.
- Maintenir les échecs de génération locale et cloud dans la cible sélectionnée: le chat du lanceur ajoute une raison bornée sans données sensibles, indique qu'aucun repli entre frontières n'a eu lieu et propose un changement manuel explicite de cible sans supprimer la sortie partielle.

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
protocol: V2
required host build: 5276
```

AI Provider V2 expose un catalogue paginé unique de cibles `local:*` et `profile:*`. Chaque cible déclare séparément son provider, son modèle, sa localité, sa configuration et sa disponibilité, ses capacités, ses limites, ses contrôles et ses origines HTTPS. Un catalogue uniquement local déclare ON_DEVICE/NONE; la présence de profils en ligne déclare HYBRID/PLUGIN_MANAGED et l'union exacte de leurs origines HTTPS. Le profil backend local reste un contrôle facultatif de la cible, et aucune cible ni aucun profil indisponible ne fait l'objet d'un repli silencieux.

La build hôte 5276 ou ultérieure est requise. Les versions incluent les variantes APK arm64-v8a, x86_64, universal.

******

### État de l'intégration hôte

******

> Dans AutoJs6 (build 5276 et suivants), `ai.catalog()` renvoie chaque modèle local importé et profile en ligne configuré dans un catalogue de cibles unique, avec ID exact, fournisseur, modèle, localité, configuration, disponibilité, capacités, contrôles, limites, origine et profiles backend locaux. Passez un `target` exact à `ai.ask`, `ai.chat`, `ai.stream` ou `ai.session` ; `target` seul sélectionne le plugin officiel 3-Stone AI, tandis que `plugin: true` utilise sa cible par défaut déclarée. Les cibles locales peuvent proposer `cpu`, `gpu` et `npu` indisponible ; les cibles en ligne n'ont aucun profile d'exécution local. Un backend ou une cible indisponible échoue sans repli et les routes locale/en ligne ne changent jamais automatiquement. Les réponses terminées et diffusées exposent target, plugin, profile, raisonnement, motif de fin, usage complet et durée mesurée par le fournisseur. Des erreurs stables distinguent fournisseur absent ou désactivé, cible inconnue, non configurée, indisponible ou aux capacités incompatibles et backend indisponible. `responseSchema` active la sortie structurée ; `structuredJson: true` sans schema utilise une racine object par défaut et les sessions persistantes fixent le même target, schema et backend facultatif pour tous les tours.

******

### Sécurité et confidentialité

******

Le plugin demande `INTERNET` pour les téléchargements de modèles recommandés déclenchés par l'utilisateur et les requêtes vers une cible en ligne explicitement configurée; les cibles locales n'utilisent pas le réseau. Les identifiants restent dans le stockage privé chiffré du plugin et ne traversent jamais Binder ni le catalogue de cibles. Le plugin ne demande aucune permission générale de stockage. Les téléchargements utilisent des révisions HTTPS immuables, une taille et un SHA-256 épinglés, et n'écrivent que dans l'emplacement SAF choisi; en-tête LiteRT-LM, taille, empreinte, flush et fsync doivent tous réussir. Les services vérifient aussi le paquet AutoJs6, l'UID appelant et les signatures.

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
- Aucune découverte réseau de modèles ni aucun téléchargement depuis une URL arbitraire. Le catalogue de cibles ne présente que les modèles locaux importés et les profils en ligne explicitement configurés; seul le catalogue de recommandations intégré et épinglé est téléchargeable.
- L'inférence NPU n'est pas déclarée: le profil reste visible comme `unavailable` avec `npu-runtime-not-packaged`. GPU n'est déclaré que si `libOpenCL.so` est chargeable, et l'extension `.litertlm` ne garantit toujours pas l'initialisation du modèle.

******

### Feuille de route

******

La feuille de route est organisée en fonctionnalités livrables, chacune vérifiable séparément

- [Consulter ROADMAP.md](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/master/ROADMAP.md)

******

### Historique des versions

******

# v1.1.4

###### 2026/09/16

* `Amélioration` Après compileSdk, targetSdk passe à 37 (Android 17) ; le comportement du plugin ne dépend pas de la nouvelle cible

# v1.1.3

###### 2026/09/15

* `Amélioration` compileSdk passe à 37 (Android 17) ; targetSdk reste à 36 jusqu'à la vérification du comportement dépendant de la cible

# v1.1.2

###### 2026/09/13

* `Correctif` Conserver la date de version du plugin en anglais quelle que soit la langue de la machine de compilation
* `Amélioration` Ressources traduites cohérentes, activation explicite du plugin et validation des paquets de publication

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


[16 KB page alignment and build verification](https://github.com/SuperMonster003/AutoJs6-Plugin-Three-Stone-AI/blob/ui-redesign/docs/16kb.md)
