package com.nemesis.apk

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit opt-in: sends real messages to the authenticated provider. */
@RunWith(AndroidJUnit4::class)
class RealDeepSeekInstrumentedTest {
    @Test fun backgroundConversationAndNativePdf() {
        val inst=InstrumentationRegistry.getInstrumentation(); val args=InstrumentationRegistry.getArguments()
        assumeTrue("Explicit provider opt-in required",args.getString("real_provider")=="true")
        val ctx=inst.targetContext
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        inst.waitForIdleSync()
        val m=Python.getInstance().getModule("mobile_agent")
        val id=JSONObject(m.callAttr("create_session","work").toString()).getString("id")
        val show=MainActivity::class.java.getDeclaredMethod("showChat",String::class.java).apply { isAccessible=true }
        var pdf:File?=null
        fun run(text:String,attachments:String,leave:Boolean) {
            assertTrue(JobRunner.start(ctx,id,text,attachments,text))
            if(leave) { Thread.sleep(6000); inst.runOnMainSync { activity.onBackPressed() } }
            val deadline=System.currentTimeMillis()+240_000
            while(JobRunner.busySession!=null && System.currentTimeMillis()<deadline) Thread.sleep(500)
            if(JobRunner.busySession!=null) { m.callAttr("cancel"); fail("Délai du test fournisseur dépassé") }
            val state=JSONObject(m.callAttr("get_session",id).toString()); val messages=state.getJSONArray("messages")
            val last=messages.getJSONObject(messages.length()-1)
            assertEquals("Erreur réelle : " + last.optString("text"),"assistant",last.getString("role"))
            assertTrue("Réponse vide",last.optString("text").isNotBlank())
            assertEquals(0,state.optInt("uncertain")); assertTrue(state.optString("conversation_url").startsWith("https://chat.deepseek.com/a/chat/s/"))
        }
        try {
            inst.runOnMainSync { show.invoke(activity,id) }
            run("Bonjour, réponds simplement PRET.","[]",true)
            inst.runOnMainSync { show.invoke(activity,id) }
            val hookReady=java.util.concurrent.CountDownLatch(1)
            inst.runOnMainSync {
                com.nemap.android.web.WebViewHost.webView!!.evaluateJavascript("(function(){if(!window.__nap)return 'missing';window.__nap.__originalState=window.__nap.state;window.__nap.state=function(){var r=JSON.parse(window.__nap.__originalState());if(r.started)r.text='';return JSON.stringify(r)};return 'installed'})()") { value ->
                    if(value?.contains("installed") == true) hookReady.countDown()
                }
            }
            assertTrue("Test hook non installé",hookReady.await(10,java.util.concurrent.TimeUnit.SECONDS))
            pdf=File(ctx.filesDir,"attachments/real-provider-test-pdf"); pdf!!.parentFile?.mkdirs()
            val document=android.graphics.pdf.PdfDocument()
            val page=document.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(500,700,1).create())
            page.canvas.drawText("Le projet de test commence lundi.",40f,60f,android.graphics.Paint())
            document.finishPage(page); pdf!!.outputStream().use { document.writeTo(it) }; document.close()
            val attachment=JSONArray().put(JSONObject().put("name","checklist.pdf").put("mime","application/pdf").put("path",pdf!!.absolutePath))
            run("Résume le document PDF joint en une seule phrase.",attachment.toString(),false)
        } finally {
            inst.runOnMainSync { com.nemap.android.web.WebViewHost.webView?.evaluateJavascript("if(window.__nap && window.__nap.__originalState) window.__nap.state=window.__nap.__originalState",null) }
            pdf?.delete()
            if(JobRunner.busySession==null) m.callAttr("delete_session",id)
            activity.finish()
        }
    }
    @Test fun chatAndCodeCanRespond() {
        val inst=InstrumentationRegistry.getInstrumentation(); val args=InstrumentationRegistry.getArguments()
        assumeTrue("Explicit provider opt-in required",args.getString("real_provider")=="true")
        val ctx=inst.targetContext
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        inst.waitForIdleSync(); val m=Python.getInstance().getModule("mobile_agent")
        for(mode in listOf("chat","code")) {
            val id=JSONObject(m.callAttr("create_session",mode).toString()).getString("id")
            try {
                assertTrue(JobRunner.start(ctx,id,"Réponds simplement : OK.","[]","Réponds simplement : OK."))
                val end=System.currentTimeMillis()+180_000
                while(JobRunner.busySession!=null && System.currentTimeMillis()<end) Thread.sleep(500)
                if(JobRunner.busySession!=null) { m.callAttr("cancel"); fail("Délai du test $mode dépassé") }
                val state=JSONObject(m.callAttr("get_session",id).toString()); val messages=state.getJSONArray("messages")
                val last=messages.getJSONObject(messages.length()-1)
                assertEquals("$mode : " + last.optString("text"),"assistant",last.getString("role"))
                assertTrue(last.optString("text").isNotBlank()); assertEquals(0,state.optInt("uncertain"))
            } finally { if(JobRunner.busySession==null) m.callAttr("delete_session",id) }
        }
        activity.finish()
    }

}
