#!/usr/bin/env bash
# Private local fixture for NEMESIS development in the Codespace; not an Android runtime.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
PRIVATE=${NEMESIS_TEST_PRIVATE_DIR:-/workspaces/.nemesis-private/ssh-sandbox}
mkdir -p "$PRIVATE/work" "$PRIVATE/keys"
chmod 700 "$PRIVATE" "$PRIVATE/keys"
if [ ! -f "$PRIVATE/password" ]; then (umask 077; openssl rand -hex 20 > "$PRIVATE/password"); fi
if [ ! -f "$PRIVATE/keys/ssh_host_ed25519_key" ]; then ssh-keygen -q -t ed25519 -N '' -f "$PRIVATE/keys/ssh_host_ed25519_key"; fi
if docker inspect nemesis-private-test >/dev/null 2>&1; then
    docker start nemesis-private-test >/dev/null
else
    docker build -t nemesis-private-test-env "$HERE/test-ssh-image"
    docker run -d --name nemesis-private-test --restart unless-stopped \
      -p 127.0.0.1:22279:22 -e "NEMESIS_TEST_PASSWORD=$(cat "$PRIVATE/password")" \
      -v "$PRIVATE/work:/workspace" \
      -v "$PRIVATE/keys/ssh_host_ed25519_key:/etc/ssh/ssh_host_ed25519_key:ro" \
      -v "$PRIVATE/keys/ssh_host_ed25519_key.pub:/etc/ssh/ssh_host_ed25519_key.pub:ro" \
      --memory 768m --cpus 1 --pids-limit 128 --cap-drop NET_RAW --security-opt no-new-privileges \
      nemesis-private-test-env >/dev/null
fi
printf 'Serveur de test démarré sur 127.0.0.1:22279 ; depuis l’émulateur Android : 10.0.2.2:22279\n'
printf 'Utilisateur : nemesis ; mot de passe privé : %s/password\n' "$PRIVATE"
ssh-keygen -lf "$PRIVATE/keys/ssh_host_ed25519_key.pub" | awk '{print "Empreinte:",$2}'
