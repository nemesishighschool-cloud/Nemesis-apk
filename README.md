# NEMESIS Android

NEMESIS est une application Android native qui réunit, dans une seule APK, une interface de conversation et une adaptation du moteur d’agent Python de NEMESIS-AGENT, reliées à une copie intégrée de NemApi-Apk. Le seul fournisseur intégré actuellement est **DeepSeek** : l’utilisateur doit s’y connecter dans l’application. L’API locale ne rend pas le modèle autonome ni utilisable hors ligne.

> **Préversion : v0.1.0-beta.4.** Elle est utilisable pour les essais, mais les limites ci-dessous restent importantes. L’architecture réelle est native Kotlin + Python/Chaquopy + NemApi/WebView, et non l’architecture Kotlin/Koog décrite dans un ancien plan provisoire.

## Installation

- [Télécharger l’APK beta.4](https://github.com/ekouenem/Nemesis-apk/releases/download/v0.1.0-beta.4/NEMESIS-v0.1.0-beta.4.apk) · [Notes de version](https://github.com/ekouenem/Nemesis-apk/releases/tag/v0.1.0-beta.4)
- Android minimum déclaré : **8.0 / API 26** ; cible de terrain : Android 8.1. Architectures incluses : `armeabi-v7a` et `x86`, 32 bits. Les essais Android ont été faits sur émulateur **x86 Android 8.1**, pas sur appareil ARM ; la présence d’une ABI n’est pas une validation matérielle.
- APK **debug**, pas une signature de production. La beta.4 est signée avec la même clé de développement que la beta.3 : l’installation par-dessus devrait préserver les données. Sauvegardez-les au préalable. Si la clé de signature est perdue, une future mise à jour pourra nécessiter une réinstallation et entraîner la perte des données privées.
- SHA-256 : `b5b2b244f07a150e600879060f50c94abf78b00b967211de5d640336b2a437ce`.

Ouvrez l’application et configurez DeepSeek dans son interface intégrée. La session est conservée dans le stockage privé de la WebView quand Android le permet, mais une reconnexion ou un CAPTCHA peuvent être demandés. La connexion Google automatique du compte Android n’est pas intégrée : OAuth Google interdit l’utilisation d’une WebView embarquée et le navigateur système ne partage pas automatiquement ses cookies avec celle-ci.

## Utilisation

- **Chat** : conversations, streaming, Markdown, mémoire, copie et appréciation locale des réponses ; la durée est affichée lorsqu’elle est disponible. Le bouton **↓** ramène en bas si du contenu se trouve plus bas. Le streaming ne force pas le défilement pendant la lecture.
- **Work** : recherche et lecture Web, fichiers, SSH, skills et MCP, avec cartes d’outils discrètes. Le prompt vise des explications adaptées au profil.
- **Code** : outils Work et outils de programmation supplémentaires, avec cartes dépliables. L’intégration du moteur d’origine est **partielle** : notamment, les sous-agents et le patch multi-fichiers ne sont pas disponibles.
- Le bouton **+** du composeur ouvre le sélecteur de fichiers Android. Jusqu’à quatre pièces jointes de 8 Mo chacune sont transmises comme fichiers ; leur prise en charge dépend de DeepSeek. Le suivi de téléversement comprend une heuristique : il ne prouve pas toujours la bonne réception.
- La mémoire est activée par défaut et contrôlable dans les paramètres. Sa création automatique dépend du modèle. Prompt système, préférences et mémoire sont transmis au début d’une nouvelle discussion ; ils ne sont pas réinjectés automatiquement lors d’une reprise par URL.
- Les outils Work/Code n’exigent pas de confirmation individuelle et n’ont pas de plafond fixe de tours. Le bouton d’arrêt ne garantit pas l’interruption d’une requête déjà soumise ou d’une commande SSH déjà lancée.

## Sessions DeepSeek et fiabilité

Chaque discussion peut reprendre son URL DeepSeek sans renvoyer tout son historique. La beta.4 attend que le composeur soit utilisable, même si la bonne URL est déjà ouverte, et vérifie l’URL au moment de l’envoi. Elle ne rejoue pas automatiquement un message dont la soumission est incertaine ; si nécessaire, vérifiez la conversation et utilisez la reprise explicite.

Le champ a été trouvé sur la page connectée stabilisée et une course de préparation a été corrigée. Cela ne prouve pas que tous les cas `input_missing` observés sur d’autres appareils sont résolus. Une modification du site, le réseau ou un challenge peuvent empêcher l’envoi. La continuation automatique des réponses longues a été améliorée mais **n’a pas été validée de bout en bout** : ne considérez pas une réponse ou un script incomplet comme terminé.

NemApi communique avec l’agent via une passerelle HTTP interne sur loopback, port aléatoire et secret en mémoire. Cela réduit l’exposition entre applications mais ne protège pas contre un appareil compromis. Le service de premier plan affiche une notification sur Android 8.1. Android peut interrompre ou différer les tâches malgré les mécanismes de surveillance ; la disponibilité permanente et la reprise après mort du processus ne sont pas garanties.

## Fichiers, SSH, skills et MCP

Avec l’autorisation de stockage, les espaces sont sous `Stockage interne/Nemesis/Work/<id-discussion>` ou `Nemesis/Code/<id-discussion>` ; sinon, stockage privé de repli. Le dossier partagé peut être accessible aux autres applications autorisées et survivre à la désinstallation. La migration depuis l’ancien dossier `NEMESIS` évite l’écrasement des conflits.

SSH est distinct du stockage Android. La clé du serveur est mémorisée à la première connexion (TOFU), puis une modification est refusée. La première connexion n’est pas protégée contre une usurpation. Les commandes s’exécutent avec les droits du compte configuré ; chaque commande démarre un nouveau shell. Le transfert SFTP vers la discussion vérifie le SHA-256. Aperçu intégré pour PDF et images ; DOCX en texte sans mise en forme. Utilisez un environnement de confiance et des sauvegardes.

Dix-huit skills tiers sont embarqués puis copiés sans écrasement vers `Nemesis/Skills`, après autorisation. Ils sont listables, lisibles et installables depuis GitHub ; leurs scripts et dépendances ne sont pas tous fournis. Consultez [SKILLS-SOURCES.md](SKILLS-SOURCES.md) et les licences tierces. Les contenus de skills et les résultats MCP sont non fiables, en particulier avant exécution.

MCP prend en charge Streamable HTTP (JSON ou SSE en réponse) et stdio via SSH pour `initialize`, `tools/list` et `tools/call` ; le jeton Bearer HTTP est protégé par Android Keystore. OAuth interactif, ancien HTTP+SSE, ressources/prompts et requêtes serveur avancées ne sont pas pris en charge. HTTP sans TLS expose les données.

## Architecture et développement

- `app/src/main/java/com/nemesis/apk/` : interface native, tâches, service, SSH et ponts MCP.
- `app/src/main/python/` : boucle d’agent, fournisseur NemApi, mémoire, outils Web, skills et MCP ; Python 3.11 embarqué via Chaquopy.
- `nemapi-embedded/` et `nemapi/` : intégration Android et copie de NemApi ; adaptateur DeepSeek dans `nemapi/app/src/main/assets/js/adapter-deepseek.js`.
- `tests/` : tests Python et JavaScript ; `app/src/androidTest/` : tests Android instrumentés.
- [CONTEXTE.md](CONTEXTE.md) : état et reprise ; [AGENTS.md](AGENTS.md) : consignes de travail.

### Compilation et tests

JDK 17 ou 21, Android SDK 34 et Python 3.11. L’environnement utilisé pour beta.4 disposait de Java 21 et du SDK sous `/workspaces/android-sdk`.

```bash
export ANDROID_HOME=/workspaces/android-sdk
export JAVA_HOME=/usr/local/sdkman/candidates/java/21.0.13.0+2-ms
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
./gradlew --no-daemon --max-workers=1 :app:assembleDebug :app:assembleDebugAndroidTest
PYTHONPATH=app/src/main/python python3 -m unittest discover -s tests -v
node tests/test_provider_readiness.js
node tests/test_adapter_dom.js
node nemapi/tools/test-hook.js
node nemapi/tools/test-continue.js
(cd nemapi && ./gradlew --no-daemon --max-workers=1 :app:testDebugUnitTest)
```

Ces chemins Java et SDK sont propres au Codespace documenté : adaptez-les si nécessaire. L’APK est générée dans `app/build/outputs/apk/debug/`. Installer avec `adb install -r` ; **ne pas désinstaller ni effacer les données** pour conserver la session. Les sauvegardes de cookies, clés et sessions sont sensibles et doivent rester hors Git.

Validation beta.4 : 44 tests Python, tests JavaScript, unitaires NemApi et trois tests Android de disponibilité, interface et régression. Un test réel avec trois courts messages a couvert deux discussions puis la reprise de la première après indisponibilité temporaire simulée du champ. Les tests réels ne doivent pas être relancés en masse : ils sollicitent le compte DeepSeek.

## Licences et crédits

Le provider et le parseur adaptés de NEMESIS-AGENT proviennent du commit `55e8daffb27c6da23274b86cf63d1524b312edb7`, licence MIT : voir [LICENSE-NEMESIS](LICENSE-NEMESIS). La copie NemApi conserve sa licence dans `nemapi/LICENSE-UPSTREAM`. Pour les skills tiers, consulter [SKILLS-SOURCES.md](SKILLS-SOURCES.md) et `third_party/`. Les conditions d’utilisation des services externes restent applicables.
