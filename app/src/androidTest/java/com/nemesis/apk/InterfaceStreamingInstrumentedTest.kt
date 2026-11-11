package com.nemesis.apk

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InterfaceStreamingInstrumentedTest {
    private fun children(v:View):List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
    @Test fun copyRatingAndScrollPosition() {
        val inst=InstrumentationRegistry.getInstrumentation(); val ctx=inst.targetContext
        val a=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        inst.waitForIdleSync(); val m=Python.getInstance().getModule("mobile_agent")
        val id=JSONObject(m.callAttr("create_session","chat").toString()).getString("id")
        val show=MainActivity::class.java.getDeclaredMethod("showChat",String::class.java).apply { isAccessible=true }
        val busy=JobRunner::class.java.getDeclaredField("busySession").apply { isAccessible=true }
        try {
            repeat(10) { m.callAttr("_message",id,"assistant","Réponse $it "+"texte ".repeat(35),12345) }
            inst.runOnMainSync { show.invoke(a,id) }; inst.waitForIdleSync()
            var views=children(a.window.decorView)
            assertTrue(views.any { it.contentDescription=="Temps de réponse : 12 s" })
            val copy=views.first { it.contentDescription=="Copier la réponse" }
            inst.runOnMainSync { copy.performClick() }
            var copied=""
            inst.runOnMainSync { val clip=ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; copied=clip.primaryClip!!.getItemAt(0).text.toString() }
            assertTrue(copied.startsWith("Réponse 0"))
            val good=views.first { it.contentDescription=="Bonne réponse" }
            inst.runOnMainSync { good.performClick() }
            assertEquals(1,JSONObject(m.callAttr("get_session",id).toString()).getJSONArray("messages").getJSONObject(0).getInt("rating"))
            busy.set(JobRunner,id)
            inst.runOnMainSync { show.invoke(a,id) }; inst.waitForIdleSync()
            var scroll=children(a.window.decorView).filterIsInstance<ScrollView>().first()
            inst.runOnMainSync { scroll.scrollTo(0,0) }; m.callAttr("_set_live",id,"Un flux "+"contenu long ".repeat(300))
            Thread.sleep(1200); inst.waitForIdleSync()
            assertEquals("Streaming must not force the reader down",0,scroll.scrollY)
            m.callAttr("_message",id,"assistant","Nouvelle réponse",1000)
            Thread.sleep(1200); inst.waitForIdleSync()
            scroll=children(a.window.decorView).filterIsInstance<ScrollView>().first()
            assertEquals("Rebuilding messages must preserve the reader position",0,scroll.scrollY)
            val down=children(a.window.decorView).first { it.contentDescription=="Aller en bas de la discussion" }
            assertEquals("The down shortcut appears when there is content below",View.VISIBLE,down.visibility)
            inst.runOnMainSync { down.performClick() }
            val deadline=System.currentTimeMillis()+5000
            var canScroll=true
            while (canScroll && System.currentTimeMillis()<deadline) {
                Thread.sleep(100); inst.runOnMainSync { canScroll=scroll.canScrollVertically(1) }
            }
            assertFalse("The shortcut reaches the bottom",canScroll)
        } finally { busy.set(JobRunner,null); m.callAttr("delete_session",id); a.finish() }
    }
}
