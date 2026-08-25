******

### Historique des versions

******

# v1.1.0

###### 2026/08/25

* `Fonction` Identité de marque et d'exécution du plugin IA locale officiel d'AutoJs6 finalisée sous le nom 3-Stone AI
* `Fonction` L'intégration interprocessus utilise les identités neutres `ai-provider-api`, `org.autojs.plugin.ai.provider.api`, `org.autojs.plugin.AI_PROVIDER` et `IAiProvider`/`IAiSession`/`IAiCallback` sans conserver d'alias des identités remplacées
* `Fonction` Exposition directe de `local:*` et `profile:*` dans le catalogue paginé de cibles AI Provider V2, avec provider/model/locality, états configured et available, capacités, limites, contrôles et origines HTTPS indépendants pour chaque cible
* `Fonction` Transmission de `temperature`, `topK`, `topP` et `maxTokens` par les requêtes de génération AI Provider V2 vers les contrôles d'échantillonnage et de tokens de sortie de LiteRT-LM
* `Fonction` Rapport des nombres exacts de tokens d'entrée, de sortie et totaux de LiteRT-LM, avec la durée de génération mesurée côté fournisseur, via `ai.chat().usage` et les événements usage du streaming
* `Fonction` Sessions persistantes AI Provider V2 et réutilisation d'une Conversation multi-tour via `ai.session` d'AutoJs6 sans renvoyer l'historique précédent
* `Fonction` Décodage natif contraint par JSON Schema de LiteRT-LM via `structuredJson` et `responseSchema` d'AutoJs6, pour les appels uniques, le streaming et les sessions persistantes, avec validation stricte du JSON complet
* `Fonction` Profils backend explicites `cpu`, `gpu` et `npu` comme contrôles facultatifs de cible AI Provider V2, avec rapport de compatibilité de l'appareil, isolation du cache modèle/profil et aucun repli depuis un profil indisponible; GPU n'est déclaré qu'après une sonde de chargement OpenCL et NPU reste indisponible car son runtime EAP n'est pas intégré
* `Fonction` Téléchargement direct de modèles LiteRT Community épinglés vers un emplacement SAF choisi, avec progression, annulation précise, nettoyage, vérification de l'en-tête LiteRT-LM, de la taille et du SHA-256, puis import direct
* `Fonction` Ajout d'un espace de conversation accessible au lancement avec Markdown en streaming, historique persistant, avertissement lors du remplacement d'une branche après modification d'un ancien message, recherche multi-résultat et saisie adaptée au clavier
* `Fonction` Ajout de paramètres d'application pour la couleur du thème, le mode sombre, la langue, les informations sur l'application et le développeur, et l'historique des versions, avec Suivre AutoJs6 par défaut lorsque possible
* `Fonction` Ajout de paramètres de conversation pour la taille de police, le comportement de la touche Entrée, les output tokens illimités ou personnalisés et l'échantillonnage `temperature`, `topK` et `topP` par défaut du modèle ou personnalisé
* `Fonction` Rendu du contenu en ligne `$\text{...}$` pendant le streaming, avec des commandes mathématiques courantes et des styles exposant et indice
* `Fonction` Ajout d'un coffre d'identifiants géré par le plugin avec Android Keystore, AES-256-GCM, un texte chiffré authentifié lié au profile, des fichiers privés atomiques interprocessus, la seule consultation de l'état configured et l'effacement immédiat du texte en clair
* `Fonction` Ajout d'un dépôt strict et non secret de profils en ligne pour les points de terminaison OpenAI Compatible exclusivement HTTPS, avec UUID canoniques, métadonnées atomiques interprocessus et remplacement ou suppression obligatoire des identifiants lors d'un changement de provider ou d'origin
* `Fonction` Ajout du backend d'exécution HTTPS OpenAI Compatible interne au plugin pour des profils avec baseUrl, identifiant et modèle personnalisés, avec streaming SSE borné et repli JSON, annulation précise, usage du provider, historique persistant des tours terminés, mapping JSON Schema et erreurs fixes sans données sensibles; les cibles `profile:*` configurées l'appellent directement via AI Provider V2
* `Fonction` Ajout de préréglages OpenAI, Anthropic, Gemini, DeepSeek et OpenRouter alignés sur le catalogue de l'hôte; la couche d'exécution en ligne unifiée réutilise le protocole compatible OpenAI et adapte séparément l'authentification, les requêtes, les terminaux SSE, l'usage et JSON Schema natifs d'Anthropic Messages et Gemini GenerateContent, sans repli entre protocoles ni entre local et en ligne
* `Fonction` Ajout de la UI des services en ligne en 10 langues pour ajouter, modifier et supprimer les profils, remplacer et effacer les clés API sans les afficher, choisir la cible par défaut, imposer le choix des réseaux facturés avant accès aux identifiants et lancer des tests explicites annulables de 120 secondes; les réglages partagent le document atomique interprocessus et actualisent dynamiquement le catalogue de cibles V2
* `Fonction` Ajout au chat du lanceur d'un sélecteur unifié de cibles locales et cloud: chaque conversation conserve un instantané de cible, un nouveau chat est recommandé lors du changement d'une conversation remplie, et continuer avec le contexte exige une confirmation explicite enregistrée
* `Fonction` Ajout à chaque réponse de l'assistant d'un instantané de la cible, du fournisseur, du modèle et de la localité réellement utilisés; la régénération réutilise exactement cette cible, échoue explicitement si son identité change ou si elle devient indisponible, sans repli silencieux sur la cible actuelle
* `Fonction` Maintien des échecs de génération locale et cloud dans la cible sélectionnée: le chat du lanceur ajoute une raison bornée sans données sensibles, indique qu'aucun repli entre frontières n'a eu lieu et propose un changement manuel explicite de cible sans supprimer la sortie partielle
* `Correction` Suppression des limites implicites de 256 tokens et 4 KiB des exemples exécutables: l'omission de `maxTokens` utilise désormais la valeur par défaut du modèle ou du moteur, et l'exemple Binder direct utilise les 64 KiB complets autorisés par le fournisseur
* `Correction` Mise à jour de l'exemple Binder de bas niveau dans les 10 instructions localisées vers les API finales de requête et de liste de cibles AI Provider V2
* `Correction` Correction du gestionnaire de modèles qui conservait les couleurs de texte du thème clair en mode sombre système, rendant le texte, les cases à cocher et les lignes de modèles illisibles sur l'arrière-plan sombre
* `Correction` Maintien de l'éditeur au-dessus du clavier logiciel, choix du texte du bouton Envoyer selon le contraste avec la couleur du thème et harmonisation des contrôles de recherche précédent, suivant et fermer
* `Correction` Correction du blocage de close appelé depuis un callback listener de génération, où l'attente de quiescence attendait son propre callback indéfiniment; close attend toujours les callbacks déjà actifs sur les autres threads
* `Correction` Correction du rejet du stockage privé des profils en ligne et des identifiants lorsque Android canonicalise la racine fiable `/data/user/0` vers `/data/data`; les liens de fils directs et les sorties de confinement restent refusés
* `Amélioration` Description du plugin, instructions et README en 10 langues mis à jour pour refléter la formalisation de la route unifiée de cibles `ai.*`
* `Amélioration` ROADMAP réécrite comme feuille de route de fonctionnalités avec des éléments vérifiables individuellement
* `Amélioration` Normalisation de la ponctuation ASCII dans l'application et les textes localisés générés, avec un test de régression pour les textes empaquetés et générés
* `Amélioration` Introduction d'une couche partagée `AiBackend`/`AiTarget`/`AiBackendSession` afin que le chat du lanceur et le provider Binder utilisent le même chemin `LiteRtLocalBackend` pour le catalogue, les capacités, la création de session, le streaming et l'annulation
* `Amélioration` Fusion des cibles locales `local:*` et en ligne `profile:*` dans un catalogue et un répartiteur uniques au niveau Application, avec exposition directe des deux via AI Provider V2 et dérivation dynamique de provider locality, credential mode et HTTPS origins sans exposer les octets d'identification

# v1.0.0

###### 2026/08/08

* `Fonction` Base AI Provider sur l'appareil avec ID et moteur `three-stone-ai`, provider ID `autojs6.three-stone-ai` et variante `default`
* `Fonction` Génération de texte brut LiteRT-LM sur CPU avec historique system, user et assistant et streaming contrôlé par credits
* `Fonction` Import SAF de `.litertlm` dans le stockage privé avec limite de 8 GiB, réserve d'espace, SHA-256, fsync et activation atomique
* `Fonction` Une session active, I/O bornées, quotas de descripteurs, annulation, timeout, un état terminal et vérification de l'appelant AutoJs6 avec la même signature
* `Fonction` Absence explicite des capacités reasoning, tools, structured JSON, usage, réseau et credential
* `Fonction` APK arm64-v8a, x86_64 et universal avec README, changelog, interface Android et instructions du plugin en 10 langues
* `Fonction` Écran de gestion des modèles affichant le catalogue complet et l'espace occupé dans le stockage privé, avec sélection atomique du modèle courant sans copie des fichiers de modèle
* `Amélioration` Conservation des générations précédentes nommées par hash SHA-256 après un import de remplacement afin d'éviter les courses interprocessus avec `:provider`, les fichiers conservés continuant d'occuper le stockage privé
* `Amélioration` Ajout d'un coordinateur d'import unique à portée application et d'un pending journal synchronisé par fsync pour résister à la recréation de Activity, récupérer au démarrage à froid, nettoyer les temporaires stale et limiter la suppression aux destinations créées par la tentative actuelle et jamais publiées, tout en conservant les générations publiées, current et historiques nommées par hash
* `Dépendance` Ajout de LiteRT-LM 0.15.0 pour la génération de texte sur CPU dans l'appareil
