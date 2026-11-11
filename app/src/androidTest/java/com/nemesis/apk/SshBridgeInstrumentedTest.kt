package com.nemesis.apk

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject

/** Explicit opt-in: a temporary test server is supplied by instrumentation arguments. */
@RunWith(AndroidJUnit4::class)
class SshBridgeInstrumentedTest {
    @Test fun pinnedHostAndCommandOnTemporaryServer() {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("test_ssh_host") ?: ""
        val user = args.getString("test_ssh_user") ?: ""
        val password = args.getString("test_ssh_password") ?: ""
        val fingerprint = args.getString("test_ssh_fingerprint") ?: ""
        val port = args.getString("test_ssh_port")?.toIntOrNull() ?: 0
        assumeTrue("External temporary SSH fixture not configured", host.isNotEmpty() && user.isNotEmpty() && password.isNotEmpty() && fingerprint.isNotEmpty() && port > 0)
        SshBridge.init(InstrumentationRegistry.getInstrumentation().targetContext)
        assumeFalse("User SSH configuration must not be overwritten", SshBridge.details().optBoolean("configured"))
        try {
            SshBridge.save(host, port, user, fingerprint, password)
            val result = JSONObject(SshBridge.execute("printf nemesis_ssh_test", 20))
            assertTrue(result.optString("error"), result.optBoolean("success"))
            assertEquals("nemesis_ssh_test", result.optString("output"))
            SshBridge.save(host, port, user, "SHA256:" + "A".repeat(43), password)
            val invalid = JSONObject(SshBridge.execute("printf must_not_run", 20))
            assertFalse("Untrusted host fingerprint must fail", invalid.optBoolean("success"))
            assertFalse(invalid.optString("output").contains("must_not_run"))
        } finally { SshBridge.clear() }
    }
    @Test fun isolatedFixtureConnectAndDownload() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("opt in required", args.getString("fixture_setup") == "true")
        val host = args.getString("test_ssh_host") ?: ""
        val user = args.getString("test_ssh_user") ?: ""
        val password = args.getString("test_ssh_password") ?: ""
        val fingerprint = args.getString("test_ssh_fingerprint") ?: ""
        val port = args.getString("test_ssh_port")?.toIntOrNull() ?: 0
        assertTrue(host.isNotBlank() && user.isNotBlank() && password.isNotBlank() && fingerprint.isNotBlank() && port > 0)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        SshBridge.init(context)
        assumeFalse("User SSH configuration must not be overwritten", SshBridge.details().optBoolean("configured"))
        SshBridge.save(host, port, user, fingerprint, password)
        try {
            val execution=JSONObject(SshBridge.execute("printf NEMESIS_SFTP_CHECK > /workspace/sftp-check.txt",20))
            assertTrue(execution.optString("error"),execution.optBoolean("success"))
            val target=java.io.File(context.cacheDir,"sftp-check.txt")
            val transfer=JSONObject(SshBridge.download("/workspace/sftp-check.txt",target.absolutePath))
            assertTrue(transfer.optString("error"),transfer.optBoolean("success"))
            assertEquals("NEMESIS_SFTP_CHECK",target.readText()); target.delete()
            assertTrue(JSONObject(SshBridge.execute("rm /workspace/sftp-check.txt",20)).optBoolean("success"))
        } catch (e:Throwable) { SshBridge.clear(); throw e }
    }

    @Test fun firstUsePinsAndChangedKeyIsRejected() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("opt in required",args.getString("tofu_fixture")=="true")
        val host=args.getString("test_ssh_host") ?: ""; val port=args.getString("test_ssh_port")?.toIntOrNull() ?: 0
        val user=args.getString("test_ssh_user") ?: ""; val password=args.getString("test_ssh_password") ?: ""
        assertTrue(host.isNotEmpty() && port>0 && user.isNotEmpty() && password.isNotEmpty())
        val context=InstrumentationRegistry.getInstrumentation().targetContext; val prefs=context.getSharedPreferences("ssh_executor",android.content.Context.MODE_PRIVATE)
        val backup=prefs.all.toMap(); SshBridge.init(context)
        try {
            SshBridge.clear(); SshBridge.save(host,port,user,"",password)
            assertEquals("",SshBridge.details().optString("fingerprint"))
            val result=JSONObject(SshBridge.execute("printf pin_test",20)); assertTrue(result.toString(),result.optBoolean("success"))
            assertTrue(SshBridge.details().optString("fingerprint").startsWith("SHA256:"))
            SshBridge.save(host,port,user,"",password); assertTrue(SshBridge.details().optString("fingerprint").startsWith("SHA256:"))
            SshBridge.save(host,port,user,"SHA256:"+"A".repeat(43),password)
            assertFalse(JSONObject(SshBridge.execute("printf do_not_execute",20)).optBoolean("success"))
        } finally {
            val e=prefs.edit().clear(); backup.forEach { (k,v) -> when(v) { is String -> e.putString(k,v); is Int -> e.putInt(k,v); is Boolean -> e.putBoolean(k,v); is Long -> e.putLong(k,v); is Float -> e.putFloat(k,v) } }; e.commit()
        }
    }

}
