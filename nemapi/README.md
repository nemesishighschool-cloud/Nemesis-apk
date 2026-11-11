# NemApi pour NEMESIS — variante de développement

Copie adaptée pour le projet NEMESIS, distincte du dépôt NemApi d’origine. Seule l’intégration NEMESIS est proposée ; DeepSeek est le fournisseur de cette variante. L’interface NEMESIS est native : la WebView de NemApi sert uniquement à la connexion et aux échanges avec DeepSeek.

Le serveur local écoute sur `127.0.0.1:8090`. Cela ne constitue **pas** une authentification entre applications Android : la sécurité du serveur local reste à renforcer avant une utilisation de production. Ne pas exposer le port sur le réseau.

## Sessions

Pour `/v1/chat/completions`, `nemesis_session: {"fresh_chat": true}` demande une nouvelle discussion ; `nemesis_session: {"conversation_url": "https://chat.deepseek.com/a/chat/s/…"}` reprend une discussion. La réponse contient `nemesis_session.conversation_url` et `url_captured`. En streaming SSE, un événement `nemesis_session` est transmis avant `[DONE]`. Le runtime conserve cette URL, suspend la session si la confirmation manque, et ne renvoie pas tout l’historique.

## Build

Dans le Codespace, avec Android SDK configuré : `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Les mises à jour via `adb install -r` conservent normalement les données ; ne pas désinstaller ni effacer les données de la WebView contenant la session DeepSeek. La sauvegarde de session est sensible et reste hors Git.

## Statut

Prototype non destiné à la production. L’activation automatique de la WebView après un redémarrage doit encore être améliorée. Tests réalisés sur émulateur Android 8.1 x86 ; ARMv7 n’a pas été testé. Voir `LICENSE-UPSTREAM` : les indications de licence upstream doivent être clarifiées avant redistribution.
