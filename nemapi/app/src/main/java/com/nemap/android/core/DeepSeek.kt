package com.nemap.android.core

import com.nemap.android.web.Engine
import kotlinx.coroutines.delay
import org.json.JSONObject

/** Une pièce jointe multimodale (image ou fichier, décodé depuis base64 ou HTTP). */
class ImagePart(val mime: String, val name: String, val bytes: ByteArray)

data class Msg(
    val role: String,
    val content: String,
    val images: List<ImagePart> = emptyList()
)

/**
 * Orchestrateur du provider DeepSeek Web.
 *
 *  1. DOM  → écrire le prompt, cliquer Envoyer / Stop (actions ponctuelles)
 *  2. hook réseau (tee SSE) → lire la réponse et détecter la fin de flux
 *  3. DOM  → lecture de secours si le hook n'a rien vu
 */
object DeepSeek {

    private const val ELEMENT_MS = 15_000L
    private const val FIRST_TOKEN_MS = 60_000L
    private const val STABILITY_MS = 2_000L
    private const val POLL_MS = 250L
    /** Plafond de sécurité, plus jamais atteint en pratique (voir waitUpload). */
    private const val UPLOAD_MS = 45_000L
    /** Au-delà, on considère que la page garde la pièce jointe en local. */
    private const val UPLOAD_SILENCE_MS = 3_000L
    /** Taille d'un morceau de base64 transféré par evaluateJavascript. */
    private const val CHUNK = 96_000

    /** Construit le prompt envoyé à la page à partir de l'historique OpenAI. */
    fun buildPrompt(messages: List<Msg>): String {
        val cleaned = messages.mapNotNull { m ->
            val c = m.content.trim()
            if (c.isEmpty()) null else m.role.lowercase() to c
        }
        if (cleaned.isEmpty()) throw NemApiException(
            NemApiException.PROVIDER_NOT_CONFIGURED,
            "La requête ne contient aucun message exploitable."
        )
        val lastUser = cleaned.lastOrNull { it.first == "user" }?.second
            ?: throw NemApiException(NemApiException.PROVIDER_NOT_CONFIGURED, "Aucun message utilisateur dans la requête.")

        // Un seul message utilisateur : on l'envoie tel quel (cas des agents de code,
        // où le prompt est déjà complet et ne doit pas être préfixé).
        if (cleaned.size == 1) return lastUser

        val sb = StringBuilder()
        for ((role, content) in cleaned) {
            when (role) {
                "system" -> sb.append("[consigne système]\n").append(content).append("\n\n")
                "user" -> sb.append(content).append("\n\n")
                "assistant" -> sb.append("[réponse précédente]\n").append(content).append("\n\n")
                else -> sb.append(content).append("\n\n")
            }
        }
        // Pas de plafond arbitraire sur l'historique. Les prompts longs passent
        // par Engine.setPrompt(), qui les injecte en morceaux sans troncature.
        return sb.toString().trim()
    }

    private fun isCancelled(token: () -> Boolean) = token()

    /**
     * Exécute une génération complète. `onDelta` reçoit les fragments au fil de l'eau.
     * Rend le texte complet. Lève une NemApiException avec un code stable sinon.
     */
    suspend fun chat(
        messages: List<Msg>,
        onDelta: (String) -> Unit,
        shouldCancel: () -> Boolean = { false },
        onKeepAlive: () -> Unit = {},
        onSubmitAttempt: () -> Unit = {},
        onSubmitNotAttempted: () -> Unit = {},
        expectedUrl: String? = null
    ): String {
        if (!AppState.busy.compareAndSet(false, true)) throw NemApiException.busy()
        try {
            return run(messages, onDelta, shouldCancel, onKeepAlive, onSubmitAttempt, onSubmitNotAttempted, expectedUrl)
        } finally {
            AppState.busy.set(false)
        }
    }

    private suspend fun run(
        messages: List<Msg>,
        onDelta: (String) -> Unit,
        shouldCancel: () -> Boolean,
        onKeepAlive: () -> Unit,
        onSubmitAttempt: () -> Unit,
        onSubmitNotAttempted: () -> Unit,
        expectedUrl: String?
    ): String {
        if (!com.nemap.android.web.WebViewHost.isCreated) {
            throw NemApiException.notReady("Le navigateur intégré n'est pas ouvert : ouvre l'app NemApi puis l'onglet du provider.")
        }

        // The page can transiently lose the input even immediately after URL
        // navigation finished. Retry readiness only BEFORE submission.
        awaitComposer(shouldCancel, onKeepAlive, expectedUrl)

        val prompt = buildPrompt(messages)
        AppState.promptChars.addAndGet(prompt.length.toLong())
        NemLog.i("dom", "prompt de ${prompt.length} caractères")

        Engine.resetStream()

        // 0) pièces jointes éventuelles, AVANT le texte (le site vide le
        //    composeur à chaque fichier ajouté)
        val images = messages.flatMap { it.images }
        if (images.isNotEmpty()) attachImages(images, shouldCancel)

        // 1) écrire le prompt — avec vérification que la page l'a bien pris
        var set = Engine.setPrompt(prompt)
        if (!set.optBoolean("ok") && set.optString("reason") == "input_missing") {
            awaitComposer(shouldCancel, onKeepAlive, expectedUrl)
            set = Engine.setPrompt(prompt) // only writing a draft, never submitting it
        }
        // A navigation can reset the JS buffer between chunks. Never submit an
        // empty or partial prompt even if the adapter considers it successful.
        if (!set.optBoolean("ok") || set.optInt("want", -1) != prompt.length || set.optInt("len", 0) <= 0) {
            throw NemApiException(
                NemApiException.SELECTOR_MISSING,
                "Le champ de saisie n'a pas gardé le prompt (${set.optString("reason")} : " +
                    "${set.optInt("got")} c. gardés sur ${set.optInt("want")} attendus)."
            )
        }
        NemLog.i("dom", "prompt écrit (${set.optInt("len")} c.)")

        // 2) attendre que le contrôle d'envoi devienne utilisable
        val budget = minOf(20_000L, 3_000L + prompt.length / 4L)
        val readySend = waitSendReady(budget, shouldCancel)
        if (!readySend) NemLog.w("dom", "contrôle d'envoi inactif après $budget ms → repli Entrée")

        // Keep a baseline so DOM fallback cannot return the previous turn as a new answer.
        val beforeSubmit = Engine.adapter("answer")
        // 3) envoyer
        // Once the submit call starts its outcome may be unknown even on error.
        onSubmitAttempt()
        val sub = Engine.adapter("submit", expectedUrl)
        if (!sub.optBoolean("ok")) {
            if (sub.optBoolean("not_submitted")) onSubmitNotAttempted()
            throw NemApiException(
                NemApiException.SELECTOR_MISSING,
                "Impossible de déclencher l'envoi (${sub.optString("reason")})."
            )
        }
        NemLog.i("dom", "envoi déclenché via ${sub.optString("via")}")

        // 4) suivre la réponse, puis actionner « Continue » si le modèle a
        // atteint sa limite de sortie. Chaque reprise ouvre un nouveau flux;
        // son texte est dédoublonné avant d'être envoyé au client OpenAI.
        val answer = follow(prompt, onDelta, shouldCancel, onKeepAlive, beforeSubmit)
        return continueAnswer(answer, onDelta, shouldCancel, onKeepAlive)
    }

    /** Continue an already-started answer. Public for opt-in provider diagnostics;
     * the caller must serialize access to the shared provider WebView. */
    suspend fun continueAnswer(
        initial: String,
        onDelta: (String) -> Unit,
        shouldCancel: () -> Boolean = { false },
        onKeepAlive: () -> Unit = {}
    ): String {
        var answer = initial
        var continuationCount = 0
        while (true) {
            val available = waitContinueButton(shouldCancel, if (answer.length >= 6_000) 8_000L else 2_500L, onKeepAlive)
            if (!available) break
            if (isCancelled(shouldCancel)) throw NemApiException.cancelled()
            val beforeContinue = Engine.adapter("answer")
            Engine.resetStream()
            val target = Engine.adapter("continueTarget")
            if (!target.optBoolean("ok")) throw NemApiException(NemApiException.SELECTOR_MISSING, "Le bouton de continuation n'est plus disponible ; réponse incomplète.")
            val tapped = Engine.tapCssPosition(target.optDouble("x",Double.NaN), target.optDouble("y",Double.NaN), target.optDouble("ratio",1.0))
            if (!tapped) throw NemApiException(NemApiException.SELECTOR_MISSING, "Impossible de poursuivre la génération ; réponse incomplète.")
            continuationCount++
            NemLog.i("dom", "DeepSeek a affiché « Continue » → reprise $continuationCount")
            val tail = ContinuationTail(answer, onDelta)
            follow("", onDelta = tail::append, shouldCancel = shouldCancel, onKeepAlive = onKeepAlive, baseline = beforeContinue, continuation = true)
            tail.finish()
            val combined = tail.fullText()
            if (combined.length <= answer.length) throw NemApiException(
                NemApiException.STREAM_INTERRUPTED, "La continuation n’a produit aucun nouveau texte ; vérifiez DeepSeek avant de reprendre.")
            answer = combined
        }
        return answer
    }

    /**
     * Dépose les images dans le sélecteur de fichiers du composeur, puis
     * attend que le site ait fini de les téléverser (vignettes visibles,
     * aucun indicateur de chargement). Sans cette attente, le site renvoie
     * le message avant que l'image soit prête.
     */
    private suspend fun attachImages(images: List<ImagePart>, shouldCancel: () -> Boolean) {
        Engine.adapter("attachBegin")
        var sent = 0
        for (img in images) {
            val b64 = android.util.Base64.encodeToString(img.bytes, android.util.Base64.NO_WRAP)
            var off = 0
            while (off < b64.length) {
                if (isCancelled(shouldCancel)) throw NemApiException.cancelled()
                val end = minOf(off + CHUNK, b64.length)
                val payload = JSONObject()
                    .put("mime", img.mime)
                    .put("name", img.name)
                    .put("first", off == 0)
                    .put("b64", b64.substring(off, end))
                    .toString()
                // `first` distingue une nouvelle image du morceau suivant
                val r = Engine.adapter("attachChunk", payload, 30_000)
                if (!r.optBoolean("ok")) throw NemApiException(
                    NemApiException.PROVIDER_NOT_CONFIGURED,
                    "Transfert de la pièce jointe interrompu (${r.optString("reason")})."
                )
                off = end
            }
            sent++
        }

        try { Engine.evalJs("window.__napUploadReset && window.__napUploadReset()") } catch (_: Throwable) {}
        val commit = Engine.adapter("attachCommit", null, 30_000)
        if (!commit.optBoolean("ok")) {
            Engine.adapter("attachBegin")
            throw NemApiException(NemApiException.PROVIDER_NOT_CONFIGURED,
                "La pièce jointe n’a pas été acceptée par DeepSeek (${commit.optString("reason")}) ; message non envoyé.")
        }
        // `in_input` retombe souvent à 0 : la page vide l'input dès qu'elle a lu
        // les fichiers. C'est `wanted` qui donne le nombre de pièces transmises.
        val inInput = commit.optInt("in_input")
        val wanted = commit.optInt("wanted", 0)
        NemLog.i(
            "dom",
            if (inInput > 0) "pièce(s) jointe(s) déposée(s) : $inInput fichier(s) dans l'entrée"
            else "pièce(s) jointe(s) déposée(s) : $wanted fichier(s) transmis au composeur"
        )

        waitUpload(shouldCancel)
    }

    /**
     * Attend la fin du téléversement des pièces jointes.
     *
     * Avant : on interrogeait le DOM (vignettes, `input.files`) ; quand les
     * sélecteurs ne correspondaient pas, on attendait **toujours** le plafond
     * de 45 s — d'où un premier token figé autour de 46 s pour chaque image.
     *
     * Maintenant : on écoute le réseau (requêtes de téléversement captées par
     * `hook.js`). On sort dès que la requête est terminée, et au bout de
     * [UPLOAD_SILENCE_MS] si la page ne fait aucune requête (pièce jointe
     * traitée en local). Le DOM ne sert plus que de secours.
     */
    private suspend fun waitUpload(shouldCancel: () -> Boolean) {
        val t0 = System.currentTimeMillis()
        var lastNote = ""
        while (System.currentTimeMillis() - t0 < UPLOAD_MS) {
            if (isCancelled(shouldCancel)) throw NemApiException.cancelled()
            val st = try {
                Engine.uploadState()
            } catch (_: Throwable) {
                JSONObject().put("seen", false).put("active", 0)
            }
            val seen = st.optBoolean("seen")
            val active = st.optInt("active")
            val elapsed = System.currentTimeMillis() - t0

            if (seen && active == 0) {
                val ok = st.isNull("ok") || st.optBoolean("ok", true)
                if(!ok) throw NemApiException(NemApiException.UPSTREAM_ERROR, "DeepSeek a refusé le téléversement (HTTP ${st.optInt("status")}) ; message non envoyé.")
                NemLog.i("dom", "téléversement terminé en ${elapsed} ms " +
                    "(HTTP ${st.optInt("status")}, ${st.optInt("total")} requête(s))")
                return
            }

            // aucun téléversement réseau : la page gère la pièce jointe en local
            if (!seen && elapsed > UPLOAD_SILENCE_MS) {
                NemLog.i("dom", "aucune requête de téléversement en ${elapsed} ms — pièce jointe traitée en local")
                return
            }

            // secours DOM (vignette déjà affichée)
            if (seen && active > 0 && elapsed > UPLOAD_SILENCE_MS) {
                val dom = try {
                    Engine.adapter("uploadState")
                } catch (_: Throwable) {
                    JSONObject()
                }
                if (dom.optBoolean("ready")) {
                    NemLog.i("dom", "téléversement confirmé par le DOM en ${elapsed} ms")
                    return
                }
                lastNote = "requête active depuis ${elapsed} ms"
            }
            delay(POLL_MS)
        }
        NemLog.w("dom", "téléversement non confirmé après $UPLOAD_MS ms ($lastNote) — envoi quand même")
    }

    private suspend fun waitContinueButton(
        shouldCancel: () -> Boolean,
        timeoutMs: Long,
        onKeepAlive: () -> Unit
    ): Boolean {
        val started = System.currentTimeMillis()
        val deadline = started + timeoutMs
        var disabledDeadline = 0L
        var lastKeepAlive = started
        while (true) {
            val now = System.currentTimeMillis()
            if (now >= deadline && (disabledDeadline == 0L || now >= disabledDeadline)) break
            if (isCancelled(shouldCancel)) throw NemApiException.cancelled()
            try {
                val status = Engine.adapter("continueAvailable", timeoutMs = 1_500)
                if (status.optBoolean("available")) {
                    if (status.optBoolean("enabled", true)) return true
                    if (disabledDeadline == 0L) {
                        disabledDeadline = now + 20_000L
                        NemLog.i("dom", "action « Continue » détectée mais encore désactivée — attente de son activation")
                    }
                }
            } catch (_: Throwable) {
            }
            if (now - lastKeepAlive >= 2_000L) {
                try { onKeepAlive() } catch (_: Throwable) {}
                lastKeepAlive = now
            }
            delay(250)
        }
        if (disabledDeadline != 0L) NemLog.w("dom", "action « Continue » restée désactivée pendant 20 s")
        return false
    }

    private suspend fun awaitComposer(shouldCancel: () -> Boolean, onKeepAlive: () -> Unit, expectedUrl: String?) {
        val start = android.os.SystemClock.elapsedRealtime()
        var loginObserved = false
        var differentUrl = false
        var lastKeepAlive = start
        while (android.os.SystemClock.elapsedRealtime() - start < 20_000L) {
            if (isCancelled(shouldCancel)) throw NemApiException.cancelled()
            try {
                differentUrl = expectedUrl != null && Engine.currentUrl().trimEnd('/') != expectedUrl.trimEnd('/')
                if (!differentUrl) {
                    Engine.ensureAdapter()
                    val ready=Engine.adapter("ready", timeoutMs=3_000)
                    if (ready.optBoolean("ok")) return
                    loginObserved = loginObserved || ready.optBoolean("login_required")
                }
            } catch(_: NemApiException) { /* transient DOM/renderer failure before submit */ }
            val now=android.os.SystemClock.elapsedRealtime()
            if (now-lastKeepAlive>=5_000L) { try { onKeepAlive() } catch (_: Throwable) {}; lastKeepAlive=now }
            delay(400)
        }
        if (differentUrl) throw NemApiException.notReady("La discussion DeepSeek a changé ; aucun message n'a été envoyé.")
        if (loginObserved) throw NemApiException.login()
        throw NemApiException.notReady("Le champ de saisie DeepSeek reste indisponible après attente ; aucun message n'a été envoyé. Vérifiez la page DeepSeek et réessayez.")
    }

    private suspend fun waitSendReady(budgetMs: Long, shouldCancel: () -> Boolean): Boolean {
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < budgetMs) {
            if (isCancelled(shouldCancel)) throw NemApiException.cancelled()
            val r = Engine.adapter("sendReady")
            if (r.optBoolean("found") && !r.optBoolean("disabled")) return true
            delay(120)
        }
        return false
    }

    internal fun isFreshDomAnswer(answer: JSONObject, baseline: JSONObject): Boolean {
        return DomFreshness.isFresh(answer.optBoolean("ok"), answer.optString("text", ""), answer.optInt("blocks", 0), baseline.optString("text", ""), baseline.optInt("blocks", 0))
    }

    private suspend fun follow(
        prompt: String,
        onDelta: (String) -> Unit,
        shouldCancel: () -> Boolean,
        onKeepAlive: () -> Unit = {},
        baseline: JSONObject,
        continuation: Boolean = false
    ): String {
        val t0 = System.currentTimeMillis()
        var lastKeepAlive = t0
        var acc = ""
        var mode = "hook"
        var lastDom = ""
        var stableSince = 0L
        var sawStream = false
        var lastContinueCheck = 0L
        var emptyStreamEndedAt = 0L
        var lastProgressAt = t0
        fun freshText(a: JSONObject): String {
            return DomFreshness.freshText(a.optBoolean("ok"),a.optString("text", ""),a.optInt("blocks",0),
                baseline.optString("text", ""),baseline.optInt("blocks",0),continuation)
        }

        while (true) {
            if (isCancelled(shouldCancel)) {
                stopPage()
                throw NemApiException.cancelled()
            }
            val now = System.currentTimeMillis()
            if (now - lastKeepAlive >= 10_000L) {
                try { onKeepAlive() } catch (_: Throwable) {}
                lastKeepAlive = now
            }

            if (mode == "hook") {
                val st = Engine.streamState()
                if (st.optBoolean("started")) {
                    sawStream = true
                    val lastEvent = now - st.optLong("idle", 0).coerceAtLeast(0)
                    if (lastEvent > lastProgressAt) lastProgressAt = lastEvent
                    val text = st.optString("text", "")
                    if (text.length > acc.length) {
                        onDelta(text.substring(acc.length))
                        acc = text
                    }
                    if (st.optBoolean("done")) {
                        // JSONObject.optString rend la chaîne "null" pour un JSON null :
                        // on teste la nullité réelle du champ.
                        val err = if (st.isNull("error")) "" else st.optString("error", "")
                        if (err.isNotEmpty() && acc.isNotEmpty()) {
                            throw NemApiException(NemApiException.STREAM_INTERRUPTED,
                                "DeepSeek a interrompu une réponse en cours ; elle peut être incomplète.")
                        }
                        if (err.isNotEmpty() && acc.isEmpty()) {
                            val domAnswer = Engine.adapter("answer")
                            val fromDom = freshText(domAnswer)
                            if (fromDom.isNotEmpty()) {
                                NemLog.w("hook", "flux en erreur ($err) mais réponse présente dans le DOM → lecture DOM")
                                onDelta(fromDom)
                                acc = fromDom
                                break
                            }
                            throw NemApiException(
                                if (err == "networkError" || err == "network_error") NemApiException.NETWORK_ERROR else NemApiException.UPSTREAM_ERROR,
                                "Le provider a interrompu la réponse (" +
                                    (if (st.isNull("errorMessage")) err else st.optString("errorMessage", err)) + ")."
                            )
                        }
                        if (acc.isNotBlank()) break
                        // The network hook can finish before the page has rendered the answer,
                        // or the provider can use a format the hook does not understand.
                        // Wait for a *fresh* DOM answer rather than immediately reporting empty.
                        emptyStreamEndedAt = now
                        mode = "dom"
                        NemLog.w("hook", "flux vide terminé → attente de la réponse DOM")
                    }
                    // flux muet mais page redevenue inactive → considéré terminé
                    if (mode == "hook" && now - t0 > FIRST_TOKEN_MS && st.optLong("idle", 0) > STABILITY_MS) {
                        val gen = Engine.adapter("generating").optBoolean("generating")
                        if (!gen) break
                    }
                } else if (now - t0 > FIRST_TOKEN_MS) {
                    NemLog.w("hook", "aucun flux observé après $FIRST_TOKEN_MS ms → lecture DOM")
                    mode = "dom"
                    emptyStreamEndedAt = now
                }
            }

            if (mode == "dom") {
                val a = Engine.adapter("answer")
                val txt = freshText(a)
                if (txt.isNotEmpty() && txt != lastDom) {
                    lastDom = txt
                    stableSince = now
                    lastProgressAt = now
                    if (txt.length > acc.length && txt.startsWith(acc)) {
                        onDelta(txt.substring(acc.length)); acc = txt
                    }
                }
                val gen = Engine.adapter("generating").optBoolean("generating")
                if (emptyStreamEndedAt > 0 && lastDom.isEmpty() && now - emptyStreamEndedAt > 30_000L && !gen) break
                if (!gen && lastDom.isNotEmpty() && now - stableSince > STABILITY_MS) {
                    if (lastDom.length > acc.length) {
                        onDelta(lastDom.substring(minOf(acc.length, lastDom.length)))
                        acc = lastDom
                    }
                    break
                }
            }

            // Continue may appear before the network wrapper reports [DONE].
            // Watch for it during the stream so a slow finalization cannot leave
            // the client hanging until its own read timeout.
            if ((sawStream || mode == "dom") && now - lastContinueCheck >= 750L) {
                lastContinueCheck = now
                val continuation = try { Engine.adapter("continueAvailable", timeoutMs = 1_000) } catch (_: Throwable) { JSONObject() }
                if (continuation.optBoolean("available") && continuation.optBoolean("enabled", true)) {
                    if (mode == "hook") {
                        val latest = Engine.streamState()
                        val latestText = latest.optString("text", "")
                        if (latestText.length > acc.length) {
                            onDelta(latestText.substring(acc.length))
                            acc = latestText
                        }
                    } else {
                        val latest = Engine.adapter("answer")
                        val latestText = freshText(latest)
                        if (latestText.length > acc.length) {
                            onDelta(latestText.substring(acc.length))
                            acc = latestText
                        }
                    }
                    NemLog.i("dom", "action « Continue » active détectée pendant le flux → reprise immédiate")
                    break
                }
            }

            if (now - lastProgressAt > 120_000L) {
                throw NemApiException(NemApiException.STREAM_INTERRUPTED,
                    "Aucune progression de DeepSeek pendant deux minutes ; vérifiez la discussion avant de reprendre.")
            }
            delay(POLL_MS)
        }

        // fin de flux : si le hook n'a rien donné, on tente le DOM
        if (acc.isBlank()) {
            val a = Engine.adapter("answer")
            acc = freshText(a)
            if(acc.isNotBlank()) onDelta(acc)
        }
        if (acc.isBlank()) {
            throw NemApiException(
                NemApiException.STREAM_INTERRUPTED,
                if (sawStream) "La réponse du provider était vide." else "Aucune réponse détectée sur la page."
            )
        }
        AppState.completionChars.addAndGet(acc.length.toLong())
        return acc
    }

    suspend fun stopPage() {
        try {
            Engine.adapter("stop")
            NemLog.i("dom", "Stop envoyé à la page")
        } catch (t: Throwable) {
            NemLog.w("dom", "Stop impossible : ${t.message}")
        }
    }

    /** Réponse de diagnostic (structure de page, sans contenu de conversation). */
    suspend fun describe(): JSONObject = Engine.adapter("describe")
}
