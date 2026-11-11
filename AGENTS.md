# Consignes de travail — NEMESIS Android

Lire d’abord `CONTEXTE.md` et `README.md`, puis inspecter l’état actuel (`git status`, branche et fichiers concernés) : le contexte est un instantané et peut devenir obsolète. La dernière version de référence à l’arrêt de cette session est `v0.1.0-beta.4`. Ne pas modifier l’APK, publier une nouvelle version ou lancer une nouvelle phase de développement sans demande de l’utilisateur.

## Lieu et périmètre

- Travailler dans le Codespace 2 cœurs de l’utilisateur, dépôt `ekouenem/Nemesis-apk` ; développement et tests dans le Codespace, pas sur une copie locale. Vérifier l’accès et l’état avant toute modification.
- NemApi est intégré dans l’APK de NEMESIS ; ne rien pousser vers le dépôt NemApi-Apk original. Conserver le moteur Python adapté et ne pas le réécrire de zéro sans décision explicite.
- L’ancien plan architectural est provisoire et ne décrit pas le produit beta.4. Décrire le code tel qu’il est, pas des composants imaginés.
- Respecter les demandes de l’utilisateur, lui donner régulièrement des nouvelles et signaler les limites concrètes. Ne pas prétendre qu’un test ou une fonctionnalité non vérifiés sont opérationnels.

## Données, accès et opérations

- Ne jamais écrire ou afficher jetons, mots de passe, cookies, clés privées, sauvegardes de session ou journaux sensibles dans Git, les notes, les captures ou les réponses. Utiliser les accès temporaires uniquement en mémoire et demander un nouvel accès si nécessaire. L’utilisateur gère la révocation.
- Préserver la session DeepSeek, les discussions et `/sdcard/Nemesis` ; privilégier `adb install -r`. Ne pas désinstaller, effacer les données de l’application, réinitialiser l’AVD ou utiliser `-wipe-data` sans autorisation explicite et sauvegarde vérifiée.
- Préserver hors dépôt la clé de signature debug et les sauvegardes ; leur perte peut empêcher une mise à jour par-dessus. Ne pas exposer directement noVNC, ADB, SSH de test ou une WebView de session sur un port public.
- Avant une action destructive ou force-push, vérifier l’état et demander une autorisation explicite. Pour un commit, ajouter uniquement les fichiers pertinents et contrôler `git diff --check` ainsi que l’absence de secrets ; ne pas modifier l’historique partagé inutilement.

## Invariants produit

- Interface Android native claire blanc/bleu, tiroir et trois modes Chat/Work/Code. Ne pas réintroduire de défilement forcé pendant le streaming ; préserver la lecture et le bouton de retour en bas. Copie, votes locaux et durée restent disponibles pour les réponses Chat quand applicables.
- Une discussion DeepSeek se reprend par son URL, sans renvoi automatique de l’historique, du prompt système ou de la mémoire. Ne jamais rejouer un message dont l’envoi est potentiellement intervenu. Vérifier l’URL cible au moment de la soumission ; n’indiquer `not_submitted` que si cela est prouvé.
- Ne pas assimiler automatiquement `input_missing` à une déconnexion : diagnostiquer disponibilité du champ, navigation, WebView et changements du site. Respecter les CAPTCHA et ne pas essayer de les contourner.
- Mémoire activée par défaut et contrôlable ; cartes discrètes en Work et dépliables en Code ; pièces jointes via le `+` ; espaces de travail séparés sous `Nemesis`. Ne pas ajouter de confirmation individuelle des outils : l’utilisateur a explicitement choisi leur exécution sans confirmation. Ne pas réintroduire de plafond fixe d’itérations.
- Préserver SSH/SFTP, skills authentiques et MCP existants ; ne pas prétendre que tous les outils originaux ou transports MCP sont déjà implémentés. Maintenir les crédits, licences et provenance des contenus tiers.

## Validation et livraison

- Adapter les tests au changement. Le README donne les commandes Python, Node et Gradle. Les essais Android bêta ont été réalisés sur émulateur x86 Android 8.1, pas sur ARM ; mentionner distinctement tout nouvel appareil effectivement testé.
- Éviter les campagnes artificielles de prompts DeepSeek. Les tests réels instrumentés sont opt-in ; comprendre leurs effets, préserver les données et limiter strictement les appels. Ne pas relancer les tests de réponse longue si l’utilisateur ne le souhaite pas.
- La continuation automatique longue n’est pas validée de bout en bout. La notification du service sur Android 8.1 et l’absence de connexion Google automatique ont des limites de plateforme : ne pas promettre leur résolution sans vérification.
- Quand une release est explicitement demandée, vérifier le statut Git, l’APK, son SHA-256, sa signature, version/minSdk/ABIs, les résultats des tests et les limites. Signaler une APK debug comme telle. Ne jamais inclure la clé de signature ni des données utilisateur dans la publication.
