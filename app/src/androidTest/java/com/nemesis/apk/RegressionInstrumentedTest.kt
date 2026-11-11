package com.nemesis.apk

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.chaquo.python.Python
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegressionInstrumentedTest {
    private fun children(v:View):List<View> = listOf(v) + if(v is ViewGroup) (0 until v.childCount).flatMap { children(v.getChildAt(it)) } else emptyList()
    @Test fun modeCardsAndRecovery() {
        val inst=InstrumentationRegistry.getInstrumentation(); val ctx=inst.targetContext
        val activity=inst.startActivitySync(Intent(ctx,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        inst.waitForIdleSync()
        val m=Python.getInstance().getModule("mobile_agent"); val ids=mutableListOf<String>()
        val method=MainActivity::class.java.getDeclaredMethod("showChat",String::class.java).apply { isAccessible=true }
        fun create(mode:String):String { val id=JSONObject(m.callAttr("create_session",mode).toString()).getString("id"); ids.add(id);return id }
        fun show(id:String):List<View> { inst.runOnMainSync { method.invoke(activity,id) };inst.waitForIdleSync();return children(activity.window.decorView) }
        try {
            val chat=create("chat"); m.callAttr("_message",chat,"tool",JSONObject().put("name","memory_add").put("parameters",JSONObject().put("content","private value")).put("result",JSONObject().put("success",true)).toString())
            var views=show(chat)
            assertTrue(views.any { it is TextView && it.text.toString()=="Mémoire mise à jour" })
            assertFalse(views.any { it.contentDescription?.toString()?.contains("Détails de l’outil")==true })
            val work=create("work"); m.callAttr("_message",work,"tool",JSONObject().put("name","bash").put("parameters",JSONObject().put("command","private command")).put("result",JSONObject().put("success",true)).toString())
            views=show(work); assertTrue(views.any { it is TextView && it.text.toString().contains("Exécution d’une commande") })
            assertFalse(views.any { it.contentDescription?.toString()?.contains("Détails de l’outil")==true })
            val code=create("code"); m.callAttr("_message",code,"tool",JSONObject().put("name","bash").put("parameters",JSONObject().put("command","echo test")).put("result",JSONObject().put("success",true)).toString())
            views=show(code); assertTrue(views.any { it.contentDescription?.toString()=="Détails de l’outil bash" })
            val db=SQLiteDatabase.openDatabase(ctx.filesDir.resolve("nemesis.sqlite3").path,null,SQLiteDatabase.OPEN_READWRITE)
            db.execSQL("UPDATE sessions SET uncertain=1,conversation_url=? WHERE id=?",arrayOf("https://chat.deepseek.com/a/chat/s/testabc123",code)); db.close()
            views=show(code); val input=views.filterIsInstance<EditText>().first(); assertTrue(input.isEnabled)
            inst.runOnMainSync { input.requestFocus(); input.performClick() }; inst.waitForIdleSync()
            assertTrue("Le champ doit accepter le focus après interruption",input.hasFocus())
            assertTrue(views.any { it is TextView && it.text.toString()=="Reprendre la discussion" })
            assertTrue(JSONObject(m.callAttr("recover_session",code,"").toString()).getBoolean("ok"))
            views=show(code); assertFalse(views.any { it is TextView && it.text.toString()=="Reprendre la discussion" })
        } finally { ids.forEach { m.callAttr("delete_session",it) }; activity.finish() }
    }
}
