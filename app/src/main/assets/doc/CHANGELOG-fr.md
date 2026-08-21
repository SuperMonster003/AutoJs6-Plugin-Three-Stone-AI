******

### Historique des versions

******

# v1.1.0

###### 2026/08/20

* `Fonction` Plugin renommé On-Device AI, positionné comme le plugin IA locale officiel d'AutoJs6
* `Fonction` Compatible avec le sélecteur abrégé `plugin: true` de `ai.ask`/`ai.chat`/`ai.stream` et l'énumération de modèles `ai.models` d'AutoJs6
* `Fonction` Transmission de `temperature`, `topK`, `topP` et `maxTokens` par le protocole On-Device AI 1.1 vers les contrôles d'échantillonnage et de tokens de sortie de LiteRT-LM
* `Fonction` Rapport des nombres exacts de tokens d'entrée, de sortie et totaux de LiteRT-LM, avec la durée de génération mesurée côté fournisseur, via `ai.chat().usage` et les événements usage du streaming
* `Fonction` Sessions persistantes du protocole On-Device AI 1.2 et réutilisation d'une Conversation multi-tour via `ai.session` d'AutoJs6 sans renvoyer l'historique précédent
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
