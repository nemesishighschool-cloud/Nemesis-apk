package com.nemesis.apk

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nemap.android.api.NemesisSession
import com.nemap.android.web.Engine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** No messages are sent. Requires an already authenticated provider session. */
@RunWith(AndroidJUnit4::class)
class ProviderReadinessInstrumentedTest {
 @Test fun existingUrlWaitsForComposerAndGuardPreventsWrongSession() = runBlocking {
  val inst=InstrumentationRegistry.getInstrumentation()
  assumeTrue(InstrumentationRegistry.getArguments().getString("readiness_real")=="true")
  val a=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); inst.waitForIdleSync()
  NemesisSession.prepare(JSONObject().put("fresh_chat",true)); delay(500)
  val path=Engine.evalJs("(document.querySelector('a[href*=\"/a/chat/s/\"]')||{}).getAttribute('href')") ?: ""
  assertTrue("Une discussion DeepSeek existante est nécessaire",path.isNotBlank())
  val target="https://chat.deepseek.com"+path
  val config=JSONObject().put("conversation_url",target)
  NemesisSession.prepare(config)
  assertTrue(Engine.adapter("ready").optBoolean("ok"))
  val armed=Engine.evalJs("(function(){var e=document.querySelector('textarea');if(!e)return 'missing';e.readOnly=true;setTimeout(function(){e.readOnly=false},3000);return 'armed'})()")
  assertEquals("armed",armed); assertFalse(Engine.adapter("ready").optBoolean("ok"))
  val t=android.os.SystemClock.elapsedRealtime(); NemesisSession.prepare(config)
  assertTrue("L’URL identique ne suffit pas : attendre la remise en état",android.os.SystemClock.elapsedRealtime()-t >= 1500)
  assertTrue(Engine.adapter("ready").optBoolean("ok"))
  val result=Engine.adapter("submit","https://chat.deepseek.com/a/chat/s/another123456")
  assertEquals("conversation_changed",result.optString("reason")); assertTrue(result.optBoolean("not_submitted"))
  assertEquals(target.trimEnd('/'),Engine.currentUrl().trimEnd('/'))
  a.finish()
 }
}
