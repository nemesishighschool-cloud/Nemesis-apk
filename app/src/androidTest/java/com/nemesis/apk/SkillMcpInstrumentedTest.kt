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

@RunWith(AndroidJUnit4::class)
class SkillMcpInstrumentedTest {
    @Test fun bearerSecretUsesAndroidKeystore() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        McpSecretBridge.init(context)
        val id="instrumented-keystore-test"
        val secret="example-test-secret-never-a-real-token"
        try {
            McpSecretBridge.save(id,secret)
            val stored=context.getSharedPreferences("mcp_secrets",android.content.Context.MODE_PRIVATE).getString(id,"") ?: ""
            assertTrue(stored.isNotBlank()); assertFalse(stored.contains(secret)); assertEquals(secret,McpSecretBridge.get(id))
        } finally { McpSecretBridge.delete(id) }
        assertEquals("",McpSecretBridge.get(id))
    }
    @Test fun bundledSkillsAndRealMcpServers() {
        val inst=InstrumentationRegistry.getInstrumentation(); val args=InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in test fixture required",args.getString("mcp_fixture") == "true")
        val context=inst.targetContext
        val activity=inst.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync()
        val python=Python.getInstance(); val mod=python.getModule("mobile_agent"); val json=python.getModule("json")
        fun tool(name:String,args:JSONObject):JSONObject = JSONObject(json.callAttr("dumps",mod.callAttr("_tool",name,json.callAttr("loads",args.toString()))).toString())
        val skills=JSONArray(mod.callAttr("list_skills").toString())
        val names=(0 until skills.length()).map { skills.getJSONObject(it).getString("name") }
        assertTrue(names.containsAll(listOf("docx","pdf","pptx","xlsx","android-ui-design","frontend-design")))
        val read=tool("skill_read",JSONObject().put("name","pdf"))
        assertTrue(read.toString(),read.optBoolean("success")); assertTrue(read.optString("content").contains("PDF"))
        SshBridge.init(context)
        val config=SshBridge.details(); assumeTrue("Expected isolated SSH fixture",config.optString("host")=="10.0.2.2" && config.optString("user")=="nemesis")
        val remote="/workspace/mcp-fixture/nemesis-skill-test"
        try {
            val sync=tool("skill_sync",JSONObject().put("name","pdf").put("remote_path",remote))
            assertTrue(sync.toString(),sync.optBoolean("success")); assertTrue(sync.optInt("files")>0)
            val check=JSONObject(SshBridge.execute("test -s $remote/SKILL.md",20)); assertTrue(check.toString(),check.optBoolean("success"))
        } finally { SshBridge.execute("rm -rf /workspace/mcp-fixture/nemesis-skill-test",20) }
        for ((name,kind,target) in listOf(
            Triple("NEMESIS MCP HTTP test","http","http://10.0.2.2:22280/mcp"),
            Triple("NEMESIS MCP SSH test","ssh_stdio","/workspace/mcp-fixture/venv/bin/python /workspace/mcp-fixture/server.py")
        )) {
            val saved=JSONObject(mod.callAttr("mcp_save",name,kind,target).toString()); assertTrue(saved.toString(),saved.optBoolean("ok")); val id=saved.getString("id")
            try {
                val tested=JSONObject(mod.callAttr("mcp_test",id).toString()); assertTrue(tested.toString(),tested.optBoolean("ok"))
                val tools=tested.getJSONArray("tools"); assertTrue((0 until tools.length()).any { tools.getJSONObject(it).optString("name")=="add_numbers" })
                val call=tool("mcp_call",JSONObject().put("server",id).put("name","add_numbers").put("arguments",JSONObject().put("a",4).put("b",9)))
                assertTrue(call.toString(),call.optBoolean("success"))
                assertEquals(13,call.getJSONObject("result").getJSONObject("structuredContent").getInt("result"))
            } finally { mod.callAttr("mcp_delete",id) }
        }
        activity.finish()
    }
}
