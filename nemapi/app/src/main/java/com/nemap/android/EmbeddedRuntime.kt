package com.nemap.android

import android.content.Context
import android.os.Looper
import com.nemap.android.api.ApiServer
import com.nemap.android.core.AppState
import com.nemap.android.core.WebStore
import com.nemap.android.web.Engine
import com.nemap.android.web.WebViewHost
import java.security.SecureRandom

/** Internal bridge is recreated with a random port and secret on each process start. */
object EmbeddedRuntime {
    @Volatile var enabled: Boolean = false
        private set
    private var api: ApiServer? = null
    private var secret: String = ""
    private var stablePort: Int = 0

    @JvmStatic @Synchronized fun start(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper()) { "WebView requires main thread" }
        if (enabled && api?.running == true) { WebViewHost.ensure(context.applicationContext); return }
        Engine.init(context.assets)
        WebStore.init(context.applicationContext)
        val previousPort=stablePort
        if(secret.isEmpty()) {
            val bytes = ByteArray(32); SecureRandom().nextBytes(bytes)
            secret = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        }
        val server = ApiServer(context.applicationContext, previousPort, secret)
        check(server.start()) { "Impossible de démarrer le moteur privé NemApi" }
        api = server
        stablePort=server.boundPort()
        try {
            WebViewHost.ensure(context.applicationContext)
            enabled = true
            AppState.serviceRunning = true
            if (AppState.startedAt == 0L) AppState.startedAt = System.currentTimeMillis()
        } catch (e: Throwable) {
            server.stop(); api = null; secret = ""; throw e
        }
    }
    @JvmStatic fun port(): Int = api?.boundPort() ?: -1
    @JvmStatic fun token(): String = secret
}
