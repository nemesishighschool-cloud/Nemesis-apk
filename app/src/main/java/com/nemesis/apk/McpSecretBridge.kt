package com.nemesis.apk

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Bearer credentials never enter the model prompt or the MCP server database. */
object McpSecretBridge {
    private lateinit var app:Context
    fun init(context:Context) { app=context.applicationContext }
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("nemesis_mcp_secrets",null) as? SecretKey)?.let { return it }
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder("nemesis_mcp_secrets",KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun save(id:String, secret:String) {
        val prefs=app.getSharedPreferences("mcp_secrets",Context.MODE_PRIVATE)
        if(secret.isBlank()) { prefs.edit().remove(id).commit(); return }
        val cipher=Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE,key())
        val data=cipher.iv+cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString(id,Base64.encodeToString(data,Base64.NO_WRAP)).commit())
    }
    @JvmStatic fun get(id:String):String {
        val encoded=app.getSharedPreferences("mcp_secrets",Context.MODE_PRIVATE).getString(id,"") ?: ""
        if(encoded.isBlank()) return ""
        val raw=Base64.decode(encoded,Base64.NO_WRAP); val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,raw.copyOfRange(0,12)))
        return String(cipher.doFinal(raw.copyOfRange(12,raw.size)),Charsets.UTF_8)
    }
    fun delete(id:String) { app.getSharedPreferences("mcp_secrets",Context.MODE_PRIVATE).edit().remove(id).commit() }
}
