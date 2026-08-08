# Génération de texte IA AutoJs6

Ce plugin importe un paquet de modèle `.litertlm` local via Android Storage Access Framework (SAF), le copie dans le stockage privé de cette application et exécute une génération LiteRT-LM uniquement sur CPU avec un historique en texte brut et une sortie texte en streaming.

Le plugin exige la build hôte AutoJs6 5270 ou ultérieure et Android API 24 ou ultérieur.

Sécurité et limites opérationnelles:

- Un import de modèle est limité à 8 GiB et doit laisser au moins 256 MiB libres.
- Le contexte est limité à 256 KiB, la sortie à 64 KiB et une seule session de génération peut être active.
- Le provider ne déclare aucun plafond en nombre de tokens. Les requêtes qui définissent `maximumOutputTokens` ne sont donc pas prises en charge.
- Seuls streaming et `text/plain` sont déclarés. Reasoning, tools, structured JSON et usage ne sont pas pris en charge.
- Le plugin ne demande aucune permission réseau ou de stockage.
- Seul le client AutoJs6 avec la même signature peut lier le service provider.
- Les générations précédentes nommées par hash SHA-256 sont conservées pour la sécurité interprocessus et continuent d'occuper le stockage privé.
