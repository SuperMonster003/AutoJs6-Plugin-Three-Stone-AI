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

> AutoJs6 fournit maintenant des routes de production publiques et explicites pour `ai.ask(..., { plugin: ... })` et `ai.stream(..., { plugin: ... })`, avec sélection exacte component/provider/modèle et sans fallback cloud. Un one-shot ask réel avec release plugin/modèle a réussi sur QV710AF65F (API 31, arm64-v8a) ; un smoke public stream déterministe avec fake provider y a également réussi, avec une fin naturelle après plus de 8 chunks et le franchissement de la fenêtre initiale de 8 credits. Installer uniquement le plugin ne redirige toujours pas le `ai.*` legacy ; le script doit fournir le plugin selector explicite et utiliser un modelId importé. L'ancien exemple JavaAdapter/raw Binder, clipboard, UI, `chat`, le streaming sur modèle réel et l'annulation active sur appareil restent à valider.

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

`R0` reste en cours. Les validations de build du 2026-08-10 ont réussi: 32 tests/0 échec, lint 0 erreur, Debug/Release `BUILD SUCCESSFUL` en 3m37s, avec `VERSION_BUILD`/`BUILD_TIME` inchangés; le smoke réel plugin/modèle via public `ai.ask` a maintenant réussi sur appareil, tandis que l'écriture clipboard et la copie après recréation de l'Activity restent à valider. `R1` couvre maintenant cinq tranches hôte désactivées et non raccordées par défaut, sans point d'appel en production: PackageManager `exact-action discovery`/`exact-component reinspection` en lecture seule, une liaison Binder metadata-only au composant explicite, une policy/catalog de transcript de liste de modèles indépendante du transport et un coordinator Android avec transport Binder `IAiModelListCallback`. L'ancien handshake metadata réinspecte chaque frontière d'identité atteinte et n'active le fuse que si la deadline absolue expire pendant que la RPC Binder synchrone de l'interface descriptor, `getProviderInfo()` ou `getCapabilities()` s'exécute encore. La policy model-list décode strictement des résultats bornés sur une ou plusieurs pages et les erreurs du provider, rejette le changement de generation, le rejeu/les cycles de token, les ID de modèle dupliqués, les incompatibilités de capacités et les callbacks périmés/dupliqués, et garantit un seul état terminal gagnant. Uniquement pendant l'initialisation du listing, le coordinator revalide les provider metadata sur le même Binder dont le descriptor a été vérifié; il utilise ensuite `AiTextProviderPackageSnapshot.samePackageIdentityAs` pour réinspecter exactement l'identité package/component avant chaque dispatch initial ou de continuation. Chaque callback valide l'UID appelant, la taille du typed page/error envelope et un flood slot borné avant l'unique copie du payload; un page-token ledger borné est isolé pour chaque pinned identity complète et stable. Contrairement à l'ancien handshake, le coordinator conserve son watchdog pour toute `operationsInFlight` admise, notamment exact PackageManager inspect, bind, prepare, dispatch et callback admission, et fuse l'exact component si la deadline expire avant l'unwind. Aucun fuse ne peut interrompre une opération bloquée; le gate du coordinator reste `BUSY` jusqu'à l'unwind tardif. La cinquième tranche est `AiTextProviderSessionPolicy`, désactivée par défaut, non raccordée et indépendante du transport: elle épingle plan/request/provider/model/context, garde derrière un commit explicite les callbacks reçus avant le retour synchrone de `openSession`, vérifie UID puis les limites des envelopes typées et la propriété des descriptors, gère les 8 credits initiaux et un backpressure borné par chunk, admet started/chunk/usage/completed/failed/cancelled avec un seul terminal gagnant entre fin provider, cancel, timeout et death, attend le settlement des descriptors, contrôles distants et cleanup avant publication, et refuse les tools en fail-closed. Elle ne contient aucun adapter Android `IAiTextCallback`/`openSession`/PFD, aucune reinspection du package avant session dispatch, ni dispatch réel, intégration runtime/UI ou routage `ai.*`. Ses preuves comprennent désormais du source/JVM statique et du Gradle JVM focused: compilation standalone Kotlin 2.3.21 K2/JDK 21/JVM 17, 40/40 JUnit et 1200/1200 sur 30 runs; focused AutoJs6 Gradle `:app:testAppDebugUnitTest` s'est terminé avec exit 0 et son XML a enregistré 40 tests, 0 skipped, 0 failures et 0 errors, la compilation réussissant encore via fallback après une nouvelle tentative du Kotlin daemon. Elles n'incluent toujours pas d'adapter Android `IAiTextCallback`/`openSession`/PFD, de preuve ADB/device, de runtime/UI ni de `ai.*`; les éléments R1 généraux et les portes de sortie restent décochés. Le gate Gradle AutoJs6 isolé du 2026-08-10 a réussi coordinator 15/0 et assemblé host Debug, androidTest et fake APK sans modifier les metadata de version. Sur QV710AF65F (API 31, arm64-v8a), metadata et model-list ont chacun obtenu `OK (1 test)` comme preuve positive PARTIAL, avec quatre pages/quatre modèles fake et `pageSize=1`; signer/hash, identité avant/après, callback hors main et terminal unique sont détaillés dans la preuve hôte. Les trois packages étaient absents avant installation et le sont redevenus après nettoyage. Cette preuve ne valide que l'élément R1 étroit; les éléments généraux et portes de sortie restent décochés. QV710AF65F n'avait aucun plugin/modèle réel, donc R0 reste ouvert. `R2` à `R8` restent planifiés. La sixième tranche étroite ajoute un coordinator de session Android par exact-component avec transport `IAiTextCallback`/PFD, désactivé et non raccordé par défaut. Le standalone K2 a réussi 18/18, puis le même artefact 540/540 sur 30 tours; le Gradle focused a enregistré 18 tests/0 failures et les trois APK ont été assemblés. Sur QV710AF65F (API 31, arm64-v8a), les deux méthodes ont chacune obtenu `OK (1 test)`: request par reliable-pipe PFD et environ 18 chunks au-delà des 8 credits initiaux, ainsi que descriptor exact/short/trailing/reliable producer error et idempotent close. Cette preuve reste `PARTIAL`: callback completion/tool PFD interprocessus, wrong UID, hostile death/update, plugin/modèle réels, runtime/UI et `ai.*` manquent encore; les éléments R1 généraux et les portes de sortie restent donc décochés. Une tranche étroite ultérieure et cochée de conformance hostile Android session est enregistrée par les commits H1 isolés `edd10008f`/`06ebc788c` et les commits intégrés `0cbc19d9f`/`72eb4d0c0`. Le Gradle focused a réussi les suites coordinator 18/0 et fake provider 31/0, puis assemblé les trois APK. Sur QV710AF65F (API 31, arm64-v8a), sept méthodes instrumentation exactes ont chacune obtenu `OK (1 test)`: un callback completion par ordinary pipe a validé le transfert interprocessus de ownership PFD, exact length/EOF, SHA-256, la matérialisation UTF-8 et le cleanup; un tool PFD a été pris en charge puis rejeté une seule fois avec `TOOLS_UNSUPPORTED`; chunk-before-start, sequence-gap et une référence descriptor invalide ont échoué en fail-closed; duplicate terminal est resté borné à un smoke à terminal unique; et une session bloquée a été annulée après `Started`, avec réutilisation owner/gate. Cet élément `[x]` reste étroit: cross-process reliable-pipe status, wrong UID, no-credit, provider death, package update/uninstall, plugin/modèle réels, runtime/UI et `ai.*` ne sont pas couverts. Les éléments R1 généraux et les portes de sortie restent décochés. L'état des cases à cocher fait foi dans la feuille de route du projet. Une tranche supplémentaire et cochée du cycle de vie H2 est enregistrée par les commits intégrés `e5bd92b16`/`10dad3e39`/`0b9a94742`. Le Gradle focused a validé coordinator 18/0 et fake provider 31/0, et les trois APK ont été assemblés. Sur QV710AF65F (API 31, arm64-v8a), `callbackFromIsolatedProviderUidIsRejectedAndReleasesOwner` et `providerProcessDeathAfterStartedPublishesOneBinderDiedAndReleasesOwner` ont chacun renvoyé `OK (1 test)`: le premier rejette le callback isolated-process avant tout transcript avec un unique terminal `TRANSCRIPT_REJECTED`/`CALLBACK_UID_MISMATCH`, puis réutilise owner/gate; le second observe `Started`, un chunk valide de sequence 0 et l'acknowledgement de credit replenishment avant la mort du provider, publie un seul `BinderDied`, puis réutilise owner/gate. Les SHA-256 local/device correspondent exactement pour host `0685CE99C0F9E8F9056BE5F3A8EEBC2C7EA5FFCA2D422E21EAC69D0CB3364629`, androidTest `7C91A0AF651E2098AD124FF8A89AE3AC3018E0F1D0DEC068367595E964739178` et fake provider `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`; les trois certificats signer v2 ont le SHA-256 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8`. Les packages host, androidTest et fake étaient absents avant installation et de nouveau absents après cleanup. Cette preuve Android est PARTIAL uniquement pour wrong-UID/provider-death. No-credit reste JVM-only; les formes update/uninstall restent limitées aux cas JVM de final-reinspection (`lastUpdateTime` drift et `Completed(emptyList())`), sans mutation de package sur le device. Cross-process no-credit, update/uninstall réels, plugin/model réels, runtime/UI et `ai.*` restent non couverts; les éléments R1 généraux et les portes de sortie restent décochés. Une autre tranche étroite cochée, enregistrée par les commits `e4297a688`/`64db31ea5` du dépôt principal AutoJs6, raccorde la route source de production explicite `ai.ask(..., { plugin: ... })` : le sélecteur fixe strictement le component/provider/model exact ; en absence de `plugin`, le comportement cloud historique est conservé, tandis que sa présence ne lit jamais le vault, ne passe jamais par HTTP/cloud et ne fait aucun fallback ; la route accepte actuellement un unique message utilisateur en texte brut, en mode non-stream, et propage la fermeture du moteur au teardown de la session distante. K2 standalone a réussi 15/15, Gradle ciblé 19/19 et la compilation Android main a réussi, uniquement comme preuves source-only. Cette tranche ne couvre aucun essai réel de plugin/model/device, ni UI, ni route `chat` ou `stream` ; les éléments R1 généraux et les critères de sortie restent décochés. Le 2026-08-11, les APK testés et l'exécution sur appareil provenaient directement du commit d'arbre isolé `ceb44b8ba272a958bad37ec4f1aae2fd4b29dcbd`; le même test blob a ensuite été intégré comme `7ce26ceea` sur le parent main actuel avec un app tree identique, mais ce commit d'intégration n'était pas la source directe du build APK. QV710AF65F (API 31, arm64-v8a) a exécuté un one-shot réel depuis Rhino global `ai.ask(..., { plugin: ... })` via le plugin release et un LiteRT-LM de 2,583,085,056 octets. Le SHA-256 du modèle `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42` a produit le modelId `litertlm.ab7838cdfc8f77e54d8ca45eadceb204`; `AiTextPluginPublicAskSmokeTest#publicRhinoAiAskCompletesThroughExactRealPlugin` a renvoyé `OK (1 test)` en 7.071s. Les SHA-256 local/device correspondaient pour host `b7dab13c33b49f12f45de7a2091fabffa41618c983055fa19083ab1482af9561`, androidTest `09b6277f7da86d1b0a6b7143bb27236c46873c731736640b79fe8e72edcfd5cf` et plugin `ee16cea749753b4e8d7c03d4cce72066495f8b7fb251ea0a88bf5165a6c0a5bb`; tous trois partageaient le certificat signer v2 SHA-256 `31a681fcfffb3e428420cae280ded89292b12a3b0f59e19b7a73e32a8ae4c213`, et chaque base device correspondait à son APK candidat. Les portes test/lint/Debug/Release du plugin et 19/19 tests ciblés plus assemble du host ont réussi; les packages exacts host/test/plugin et les chemins réservés staging/UI-dump étaient absents pour users 0/10 avant et après. Cela ne coche pas l'élément baseline R0 de l'exemple raw Binder/JavaAdapter; seules les portes R6 étroites du smoke réel optionnel/point d'entrée local sont cochées, mais reste une preuve mono-appareil, mono-modèle, one-shot non-stream avec host appDebug. Aucun clipboard, streaming, chat, tools, structured output, usage, matrice release-host/API, update/uninstall réel, performance ou soak n'est couvert. L'état last-location de DocumentsUI n'a pas pu être restauré sans perte. Les éléments R1 généraux et les critères de sortie restent décochés. Les commits AutoJs6 `2ea3360a2`/`50b43d00c` enregistrent une autre tranche public plugin stream étroite, cochée et PARTIAL : focused Gradle a réussi 25/25, et l'exact method `AiTextPluginPublicStreamSmokeTest#publicRhinoAiStreamCompletesThroughExactFakeProvider` a renvoyé `OK (1 test)` en 1.645s sur QV710AF65F (API 31, arm64-v8a). Le fake provider (`fake.local`/`fake.stream`) a émis plus de 8 chunks au-delà des 8 credits initiaux, puis l'exécution Rhino détenue s'est terminée naturellement. Public cancel et engine teardown restent JVM-only ; le streaming sur modèle réel, l'annulation active sur appareil et la matrice release/API n'ont pas été testés, et les éléments généraux et portes de sortie R1 restent décochés. Une tranche R1 étroite supplémentaire cochée consigne la route de production explicite du plugin `ai.chat`, limitée à un prompt. Les commits production/test isolés sont `d480b6918`/`b0aa6768484d6046551264f69ecc84b527bbb442`, puis les commits d’intégration `2d256b99f`/`c35f199b8` ; Gradle ciblé a réussi 14/14 (3+4+7). La source réellement testée par Gradle, les trois APK et l’appareil est `b0aa6768484d6046551264f69ecc84b527bbb442`, et non les commits d’intégration ; les commits DEX intermédiaires sans chevauchement de chemins ne changent pas cette provenance. Sur QV710AF65F (API 31, arm64-v8a), l’exact `AiTextPluginPublicChatSmokeTest#publicRhinoAiChatReturnsNormalizedResponseThroughExactFakeProvider` a renvoyé `OK (1 test)`, `Time: 1.54` et le code `-1` via `fake.local`/`fake.echo` ; la Promise s’est terminée naturellement, a reproduit exactement le prompt et a rendu les 12 clés exactes `text`, `reasoning`, `toolCalls`, `usage`, `finishReason`, `message`, `error`, `raw`, `profile`, `route`, `provider`, `model`. Les SHA-256 des APK host/androidTest/fake sont `2D3DFB9C16AD91CCB73C6A969DB7DCC9B10046DF14F716C82D9E61F07FAAF1D3`, `9CAA1CC6936D98C380C9C9FFE81178A0E81E667207DA9623FA95C19BF3A11166` et `6C7319A6682B08CAB2E3C610D6DB0919C7C71BC034C2CEC8B46EB23623D55A18`, avec le certificat v2 commun SHA-256 `2e64822e13a6c80c12e1c4b47e8fb32d1e9334526289da75777b7a79145de4b8` ; après nettoyage, les trois paquets des utilisateurs 0/10 et les neuf chemins réservés étaient de nouveau absents. Il s’agit uniquement d’une preuve PARTIAL de fin naturelle avec fake provider : aucun modèle réel, échec/annulation sur appareil ni matrice d’API n’a été testé, et l’annulation publique/fermeture du moteur n’a que des preuves JVM ; R1 général/ses critères de sortie, R0 et R6 restent inchangés.

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
