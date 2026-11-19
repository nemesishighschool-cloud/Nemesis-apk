#!/usr/bin/env python3
"""Provider TokenHarbor — passerelle OpenAI-compatible multi-modeles.

Contrairement a NemApi (contexte gere cote serveur), TokenHarbor est
stateless : l'historique complet de la conversation est renvoye a chaque
requete dans ``messages``.

Support multi-cles : rotation automatique sur HTTP 429 (Retry-After) ou
401/403 (cle invalide), avec cooldown par cle.
"""

import json
import time
from typing import Dict, Any, List, Optional

from providers.base import BaseProvider


class TokenHarborProvider(BaseProvider):
    """Provider TokenHarbor (https://tokenharbor.ai/v1).

    Config attendue dans ``config['provider']`` :
      - keys : list[str]  (cles thk_live_...)
      - model : str (id du modele, ex. 'deepseek-v4.1-flash:free')
      - base_url : str (defaut 'https://tokenharbor.ai/v1')
    """

    DEFAULT_BASE_URL = "https://tokenharbor.ai/v1"
    supports_images = True

    def __init__(self, config: Dict[str, Any]):
        super().__init__(config)
        import requests as req
        self.req = req

        pc = self.provider_config
        self.base_url = (pc.get("base_url") or self.DEFAULT_BASE_URL).rstrip("/")
        self.model = pc.get("model") or "deepseek-v4.1-flash:free"
        raw_keys = pc.get("keys") or []
        if isinstance(raw_keys, str):
            raw_keys = [raw_keys]
        self._keys: List[Dict[str, Any]] = [
            {"key": str(k).strip(), "cooldown_until": 0.0, "invalid": False}
            for k in raw_keys if str(k).strip()
        ]
        self._current = 0
        self._models_cache: Optional[List[Dict[str, Any]]] = None

    # ------------------------------------------------------------------
    # Gestion des cles
    # ------------------------------------------------------------------
    def _usable_keys(self) -> List[int]:
        now = time.time()
        return [
            i for i, k in enumerate(self._keys)
            if not k["invalid"] and k["cooldown_until"] <= now
        ]

    def _next_key(self) -> Optional[Dict[str, Any]]:
        usable = self._usable_keys()
        if not usable:
            return None
        if self._current not in usable:
            self._current = usable[0]
        return self._keys[self._current]

    def _rotate(self, reason: str, retry_after: int = 60) -> bool:
        """Marque la cle courante et bascule sur la suivante.
        Retourne True si une autre cle est disponible."""
        if not self._keys:
            return False
        cur = self._keys[self._current]
        if reason == "invalid":
            cur["invalid"] = True
        else:
            cur["cooldown_until"] = time.time() + max(1, retry_after)
        usable = self._usable_keys()
        if not usable:
            return False
        self._current = usable[0]
        return True

    def _headers(self, key: str) -> Dict[str, str]:
        return {
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
        }

    # ------------------------------------------------------------------
    # Interface BaseProvider
    # ------------------------------------------------------------------
    def send_message(
        self,
        message: str,
        role: str = "user",
        cancel_event=None,
        system_prefix: str = "",
        images: Optional[List[Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        """Envoie l'historique complet + le nouveau message (stateless)."""
        if not self._keys:
            return {"success": False, "error": "Aucune cle TokenHarbor configuree."}

        # Construire le contenu du nouveau message
        if role == "tool_result":
            send_role, send_content = "user", f"[TOOL RESULT]\n{message}"
        elif role == "system":
            send_role, send_content = "system", f"[SYSTEM INSTRUCTIONS]\n{message}"
        else:
            send_role, send_content = role, message

        if images and send_role == "user":
            content: Any = [{"type": "text", "text": send_content}, *images]
        else:
            content = send_content

        # Historique complet (TokenHarbor est stateless)
        history = list(self._conversation)
        if system_prefix and not history:
            history.append({"role": "system", "content": system_prefix.strip()})
        messages = history + [{"role": send_role, "content": content}]

        payload = {
            "model": self.model,
            "messages": messages,
            "stream": False,
        }

        attempts = 0
        max_attempts = max(1, len(self._keys))
        last_error = ""

        while attempts < max_attempts:
            if cancel_event is not None and cancel_event.is_set():
                return {"success": False, "error": "Requete interrompue par l'utilisateur."}

            key_entry = self._next_key()
            if key_entry is None:
                return {
                    "success": False,
                    "error": "Toutes les cles TokenHarbor sont epuisees ou invalides. " + last_error,
                }

            attempts += 1
            try:
                resp = self.req.post(
                    f"{self.base_url}/chat/completions",
                    headers=self._headers(key_entry["key"]),
                    json=payload,
                    timeout=120,
                )
            except self.req.exceptions.Timeout:
                last_error = "Timeout TokenHarbor (120s)"
                # Timeout : on ne rotate pas forcement, on retente la meme cle
                if attempts >= max_attempts:
                    break
                continue
            except self.req.exceptions.ConnectionError as e:
                return {"success": False, "error": f"Connexion TokenHarbor refusee: {e}"}

            if resp.status_code == 200:
                try:
                    data = resp.json()
                    reply = data["choices"][0]["message"]["content"]
                except Exception:
                    return {"success": False, "error": f"Reponse TokenHarbor illisible: {resp.text[:200]}"}
                # Succes : memoriser la conversation
                self._conversation.append({"role": send_role, "content": send_content})
                self._conversation.append({"role": "assistant", "content": reply})
                key_entry["cooldown_until"] = 0.0  # reset
                return {"success": True, "response": reply}

            # Erreurs
            err_text = resp.text[:500]
            if resp.status_code == 429:
                retry_after = 60
                try:
                    retry_after = int(resp.headers.get("Retry-After", "60"))
                except Exception:
                    pass
                last_error = f"HTTP 429 quota (Retry-After {retry_after}s)"
                if not self._rotate("quota", retry_after):
                    return {"success": False, "error": f"Quota epuise sur toutes les cles. {last_error}"}
                continue

            if resp.status_code in (401, 403):
                last_error = f"HTTP {resp.status_code} cle invalide"
                if not self._rotate("invalid"):
                    return {"success": False, "error": f"Toutes les cles sont invalides. {last_error}"}
                continue

            if resp.status_code == 413:
                return {"success": False, "error": "Requete trop volumineuse (limite 4 Mo compte gratuit). Reduisez l'historique."}

            # Autres erreurs : pas de rotation, on remonte
            return {"success": False, "error": f"HTTP {resp.status_code}: {err_text}"}

        return {"success": False, "error": f"Echec apres {attempts} tentative(s): {last_error}"}

    def test_connection(self) -> bool:
        key_entry = self._next_key()
        if key_entry is None:
            return False
        try:
            resp = self.req.get(
                f"{self.base_url}/models",
                headers=self._headers(key_entry["key"]),
                timeout=20,
            )
            return resp.status_code == 200
        except Exception:
            return False

    def list_models(self) -> List[Dict[str, Any]]:
        key_entry = self._next_key()
        if key_entry is None:
            return []
        try:
            resp = self.req.get(
                f"{self.base_url}/models",
                headers=self._headers(key_entry["key"]),
                timeout=30,
            )
            if resp.status_code != 200:
                return []
            data = resp.json()
            raw = data.get("data") if isinstance(data, dict) else data
            if not isinstance(raw, list):
                return []
            models = []
            for item in raw:
                if isinstance(item, dict) and item.get("id"):
                    models.append({
                        "id": str(item["id"]),
                        "owned_by": item.get("owned_by", "tokenharbor"),
                        "display_name": str(item["id"]).replace("-", " ").replace("_", " ").title(),
                    })
            self._models_cache = models
            return models
        except Exception:
            return []
