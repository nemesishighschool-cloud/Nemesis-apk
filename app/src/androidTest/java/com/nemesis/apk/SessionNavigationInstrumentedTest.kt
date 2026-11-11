package com.nemesis.apk

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import com.nemap.android.api.NemesisSession
import com.nemap.android.web.Engine
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicit opt-in: three short real messages in two sessions. */
@RunWith(AndroidJUnit4::class)
class SessionNavigationInstrumentedTest {
 @Test fun multipleSessionsAndResumeAfterTemporaryInputLoss() = runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation()
  assumeTrue(InstrumentationRegistry.getArguments().getString("sessions_real")=="true")
  val ctx=inst.targetContext
  val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); inst.waitForIdleSync()
  val m=Python.getInstance().getModule("mobile_agent"); val ids=mutableListOf<String>()
  fun create(mode:String):String=JSONObject(m.callAttr("create_session",mode).toString()).getString("id").also { ids.add(it) }
  fun send(id:String,text:String):JSONObject {
   assertTrue("Task did not start",JobRunner.start(ctx,id,text,"[]",text))
   val end=System.currentTimeMillis()+180_000
   while(JobRunner.busySession!=null && System.currentTimeMillis()<end) Thread.sleep(500)
   if(JobRunner.busySession!=null) { m.callAttr("cancel"); fail("Délai dépassé") }
   val state=JSONObject(m.callAttr("get_session",id).toString()); val messages=state.getJSONArray("messages"); val last=messages.getJSONObject(messages.length()-1)
   assertEquals("Provider: "+last.optString("text"),"assistant",last.getString("role"))
   assertTrue(last.optString("text").isNotBlank()); assertEquals(0,state.optInt("uncertain")); return state
  }
  try {
   val chat=create("chat"); val work=create("work")
   val first=send(chat,"Bonjour, réponds en une seule phrase.")
   val url=first.getString("conversation_url"); assertTrue(NemesisSession.validConversationUrl(url))
   val second=send(work,"Bonjour, réponds simplement en une phrase.")
   assertNotEquals("Les sessions doivent rester distinctes",url,second.getString("conversation_url"))
   NemesisSession.prepare(JSONObject().put("conversation_url",url))
   val armed=Engine.evalJs("(function(){var e=document.querySelector('textarea');if(!e)return 'missing';e.readOnly=true;setTimeout(function(){e.readOnly=false},3000);return 'armed'})()")
   assertEquals("armed",armed)
   val resumed=send(chat,"Merci, réponds simplement en une phrase.")
   assertEquals("La reprise doit rester sur la même URL",url,resumed.getString("conversation_url"))
  } finally { if(JobRunner.busySession==null) ids.forEach { m.callAttr("delete_session",it) }; a.finish() }
 }
}
