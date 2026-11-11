package com.nemesis.apk

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nemap.android.api.NemesisSession
import com.nemap.android.core.DeepSeek
import com.nemap.android.core.Msg
import com.nemap.android.web.Engine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real and expensive: manually opt in; tests an entire fresh long generation. */
@RunWith(AndroidJUnit4::class)
class LongContinuationInstrumentedTest {
    @Test fun longStreamContinuesEndToEnd() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("long_real")=="true")
        val inst=InstrumentationRegistry.getInstrumentation()
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); NemesisSession.prepare(JSONObject().put("fresh_chat",true))
        val emitted=StringBuilder()
        val prompt="Écris directement un unique fichier Python très long et complet, au moins 1000 lignes réelles, qui implémente un gestionnaire de bibliothèque avec catalogue, prêts, utilisateurs, rapports et tests. N'omets aucun code, ne résume pas et ne t'arrête pas avant la fin."
        val answer=withTimeout(420_000) { DeepSeek.chat(listOf(Msg("user",prompt)), { emitted.append(it) }) }
        val reqs=JSONArray(Engine.evalJs("window.__nap.reqs_seen()") ?: "[]")
        var continues=0
        for(i in 0 until reqs.length()) if(reqs.getString(i).contains("POST /api/v0/chat/continue")) continues++
        println("END_TO_END_CONTINUES="+continues+" chars="+answer.length+" streamed="+emitted.length)
        assertTrue("DeepSeek n'a pas proposé de continuation pendant ce test",continues>0)
        assertTrue(answer.length>1000); assertEquals("Le texte streamé et la réponse finale doivent coïncider",answer,emitted.toString())
        activity.finish()
    }
    /** Reuse a known diagnostic conversation, no new prompt. */
    @Test fun continueExistingAnswer() = runBlocking {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("long_real")=="true")
        val url=args.getString("diagnostic_url") ?: error("diagnostic URL missing")
        val inst=InstrumentationRegistry.getInstrumentation()
        val a=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); NemesisSession.prepare(JSONObject().put("conversation_url",url))
        val previous=Engine.adapter("answer").optString("text","")
        assertTrue("Aucune réponse DOM antérieure",previous.isNotBlank())
        assertTrue("Pas de bouton Continue dans la discussion de diagnostic",Engine.adapter("continueAvailable").optBoolean("available"))
        val emitted=StringBuilder()
        val answer=try { withTimeout(300_000) { DeepSeek.continueAnswer(previous, { emitted.append(it) }) } }
        catch (e:Throwable) {
            val diag=Engine.evalJs("""(function(){let s=window.__nap?JSON.parse(window.__nap.state()):{};delete s.text;let raw=window.__nap?window.__nap.raw(12000):'';let items=raw.split('\n').map(x=>x.replace(/^data:\s*/,''));let events=[];items.forEach(x=>{try{let j=JSON.parse(x);let out={keys:Object.keys(j)};['code','msg','message','error','biz_code','biz_msg','status'].forEach(k=>{if(j[k]!=null)out[k]=j[k]});if(j.data && typeof j.data==='object')out.dataKeys=Object.keys(j.data);events.push(out)}catch(e){}});let alerts=Array.from(document.querySelectorAll('[role=alert],[class*=toast],[class*=notification]')).map(x=>(x.innerText||'').slice(0,150));return JSON.stringify({state:s,rawLen:raw.length,events:events.slice(-6),alerts:alerts})})()""")
            println("PROVIDER_EMPTY_DIAGNOSTIC="+diag); throw e
        }
        val reqs=JSONArray(Engine.evalJs("window.__nap.reqs_seen()") ?: "[]")
        var count=0; for(i in 0 until reqs.length()) if(reqs.getString(i).contains("POST /api/v0/chat/continue")) count++
        println("EXISTING_E2E_CONTINUES="+count+" old="+previous.length+" added="+emitted.length+" result="+answer.length)
        assertTrue(count>0); assertTrue("Aucun nouveau texte",emitted.isNotEmpty()); assertEquals(previous+emitted.toString(),answer)
        a.finish()
    }

}
