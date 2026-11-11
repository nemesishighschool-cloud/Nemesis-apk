package com.nemesis.apk

import android.content.Context
import android.os.PowerManager
import com.chaquo.python.Python
import org.json.JSONObject
import java.util.concurrent.Executors

/** Not owned by an Activity: navigation, backgrounding and recreation don't cancel the job. */
object JobRunner {
    private val worker = Executors.newSingleThreadExecutor()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val idleCallbacks = mutableListOf<() -> Unit>()
    @Synchronized fun whenIdle(callback: () -> Unit) {
        if (busySession == null) main.post { callback() } else idleCallbacks.add(callback)
    }
    @Volatile var busySession: String? = null
        private set
    @Volatile var preview: String? = null
        private set
    @Volatile var lastError: String? = null
        private set
    @Synchronized fun start(context: Context, id: String, text: String, attachments: String, previewText: String): Boolean {
        if (busySession != null) return false
        busySession = id; preview = previewText; lastError = null
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        val lock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NEMESIS:task")
        try { lock.acquire() } catch (_: Exception) { }
        worker.execute {
            try {
                val result = JSONObject(Python.getInstance().getModule("mobile_agent").callAttr("send",id,text,attachments).toString())
                if (!result.optBoolean("ok")) lastError = result.optString("error","Erreur")
            } catch (e: Exception) { lastError = e.message ?: "Erreur pendant la tâche" }
            finally {
                if (lock.isHeld) lock.release()
                val callbacks = synchronized(this) {
                    preview = null; busySession = null
                    idleCallbacks.toList().also { idleCallbacks.clear() }
                }
                callbacks.forEach { callback -> main.post { callback() } }
            }
        }
        return true
    }
    fun takeError(): String? = lastError.also { lastError = null }
}
