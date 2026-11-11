package com.nemesis.apk

import android.content.Intent
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nemap.android.EmbeddedRuntime
import com.nemap.android.web.WebViewHost
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

@RunWith(AndroidJUnit4::class)
class RuntimeRecoveryInstrumentedTest {
    @Test fun apiRestartsOnSamePortAndRendererRecovers() {
        val inst=InstrumentationRegistry.getInstrumentation()
        assumeTrue("Explicit destructive test opt-in required",InstrumentationRegistry.getArguments().getString("runtime_recovery")=="true")
        val ctx=inst.targetContext
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync()
        val port=EmbeddedRuntime.port(); val token=EmbeddedRuntime.token(); assertTrue(port>0)
        fun status():Int = try { val c=URL("http://127.0.0.1:$port/v1/models").openConnection() as HttpURLConnection
            c.connectTimeout=2000; c.readTimeout=2000; c.setRequestProperty("Authorization","Bearer $token"); c.responseCode.also { c.disconnect() }
        } catch(_:Exception) { -1 }
        assertEquals(200,status())
        val field=EmbeddedRuntime::class.java.getDeclaredField("api").apply { isAccessible=true }
        val api=field.get(EmbeddedRuntime) as com.nemap.android.api.ApiServer
        api.stop()
        val deadline=SystemClock.elapsedRealtime()+30000
        while(SystemClock.elapsedRealtime()<deadline && status()!=200) Thread.sleep(500)
        assertEquals("API non redémarrée sur le port initial",200,status()); assertEquals(port,EmbeddedRuntime.port()); assertEquals(token,EmbeddedRuntime.token())
        val before=WebViewHost.webView; assertNotNull(before)
        inst.runOnMainSync { before!!.loadUrl("chrome://crash") }
        val end=SystemClock.elapsedRealtime()+30000
        while(SystemClock.elapsedRealtime()<end && (WebViewHost.webView==null || WebViewHost.webView===before)) Thread.sleep(500)
        assertNotNull("WebView non recréée",WebViewHost.webView); assertNotSame("Ancienne WebView encore utilisée",before,WebViewHost.webView)
        activity.finish()
    }
}
