package com.nemesis.apk

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.jcraft.jsch.HostKey
import com.jcraft.jsch.HostKeyRepository
import com.jcraft.jsch.JSch
import com.jcraft.jsch.UserInfo
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Password encrypted by Android Keystore; host key is pinned to a supplied fingerprint. */
object SshBridge {
    private lateinit var app: Context
    private const val alias = "nemesis_ssh_password"
    fun init(context: Context) { app = context.applicationContext }
    private fun prefs() = app.getSharedPreferences("ssh_executor", Context.MODE_PRIVATE)
    fun details(): JSONObject = JSONObject().put("host",prefs().getString("host", ""))
        .put("port",prefs().getInt("port",22)).put("user",prefs().getString("user", ""))
        .put("fingerprint",prefs().getString("fingerprint", ""))
        .put("configured", !prefs().getString("secret", "").isNullOrBlank())
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
    fun save(host: String, port: Int, user: String, fingerprint: String, password: String) {
        require(host.isNotBlank() && user.isNotBlank() && port in 1..65535) { "Hôte, port ou utilisateur invalide" }
        val fp = fingerprint.trim()
        require(fp.isEmpty() || (fp.startsWith("SHA256:") && fp.length in 30..90)) { "Empreinte SHA256 invalide" }
        val previous=details()
        val retained=if(fp.isNotEmpty()) fp.trimEnd('=') else if(previous.optString("host")==host.trim() && previous.optInt("port")==port) previous.optString("fingerprint") else ""
        require(password.isNotEmpty()) { "Mot de passe SSH requis" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.iv + cipher.doFinal(password.toByteArray(Charsets.UTF_8))
        prefs().edit().putString("host",host.trim()).putInt("port",port).putString("user",user.trim())
            .putString("fingerprint",retained)
            .putString("secret", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()
    }
    private fun password(): String {
        val raw = Base64.decode(prefs().getString("secret", ""), Base64.NO_WRAP)
        require(raw.size > 12) { "Mot de passe SSH non configuré" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0,12)))
        return String(cipher.doFinal(raw.copyOfRange(12,raw.size)), Charsets.UTF_8)
    }
    fun clear() { check(prefs().edit().clear().commit()) { "Impossible de supprimer la configuration SSH." } }

    @Synchronized internal fun connect(): com.jcraft.jsch.Session {
        val config = details()
        require(config.optBoolean("configured")) { "Configurez d'abord un serveur SSH ou Termux dans Réglages." }
        val fingerprint = config.getString("fingerprint").trimEnd('=')
        val jsch = JSch()
        var observedFingerprint = ""
        jsch.hostKeyRepository = object : HostKeyRepository {
            override fun check(host: String?, key: ByteArray?): Int {
                if (key == null) return HostKeyRepository.CHANGED
                val actual = "SHA256:" + Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(key), Base64.NO_WRAP or Base64.NO_PADDING)
                if(fingerprint.isEmpty()) { observedFingerprint=actual; return HostKeyRepository.OK }
                return if (MessageDigest.isEqual(actual.toByteArray(), fingerprint.toByteArray())) HostKeyRepository.OK else HostKeyRepository.CHANGED
            }
            override fun add(hostkey: HostKey?, ui: UserInfo?) {}
            override fun remove(host: String?, type: String?) {}
            override fun remove(host: String?, type: String?, key: ByteArray?) {}
            override fun getKnownHostsRepositoryID(): String = "NEMESIS pinned SHA256"
            override fun getHostKey(): Array<HostKey> = emptyArray()
            override fun getHostKey(host: String?, type: String?): Array<HostKey> = emptyArray()
        }
        val session = jsch.getSession(config.getString("user"), config.getString("host"), config.getInt("port"))
        session.setPassword(password()); session.setConfig("StrictHostKeyChecking", "yes")
        session.setConfig("PreferredAuthentications", "password,keyboard-interactive")
        session.connect(15_000)
        if(fingerprint.isEmpty()) {
            if(observedFingerprint.isBlank() || !prefs().edit().putString("fingerprint",observedFingerprint).commit()) {
                session.disconnect(); throw IllegalStateException("Impossible de mémoriser la clé du serveur SSH")
            }
        }
        return session
    }

    @JvmStatic fun execute(command: String, timeoutSeconds: Int): String {
        val config = details(); var session: com.jcraft.jsch.Session? = null
        var channel: com.jcraft.jsch.ChannelExec? = null
        return try {
            require(config.optBoolean("configured")) { "Configurez d'abord un serveur SSH ou Termux dans Réglages." }
            require(command.isNotBlank()) { "Commande vide" }
            session = connect()
            channel = session.openChannel("exec") as com.jcraft.jsch.ChannelExec
            channel.setCommand(command)
            val bytes = ByteArrayOutputStream()
            val capped = object : OutputStream() {
                @Synchronized override fun write(b: Int) { bytes.write(b) }
                @Synchronized override fun write(b: ByteArray, off: Int, len: Int) { bytes.write(b, off, len) }
            }
            channel.setOutputStream(capped); channel.setErrStream(capped); channel.connect(15_000)
            val until = System.currentTimeMillis() + timeoutSeconds.coerceAtLeast(1).toLong() * 1000L
            while (!channel.isClosed && System.currentTimeMillis() < until) Thread.sleep(100)
            if (!channel.isClosed) JSONObject().put("success",false).put("error","Délai SSH dépassé ; la commande distante peut continuer.")
            else JSONObject().put("success",channel.exitStatus == 0).put("exit_code",channel.exitStatus)
                .put("output", bytes.toString("UTF-8")).put("truncated", false)
        } catch (e: Exception) {
            JSONObject().put("success",false).put("error", "SSH : " + (e.message ?: e.javaClass.simpleName))
        } finally { channel?.disconnect(); session?.disconnect() }
        .toString()
    }
    @JvmStatic fun download(remote: String, local: String): String {
        var session: com.jcraft.jsch.Session? = null
        var channel: com.jcraft.jsch.ChannelSftp? = null
        val destination = java.io.File(local)
        var temp: java.io.File? = null
        return try {
            require(remote.isNotBlank() && remote.length <= 1000) { "Chemin distant invalide" }
            session = connect()
            channel = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp
            channel.connect(15_000)
            val attrs = channel.stat(remote)
            require(!attrs.isDir && attrs.size >= 0) { "Fichier distant absent ou dossier" }
            val parent = destination.parentFile ?: throw IllegalArgumentException("Dossier local invalide")
            check(parent.exists() || parent.mkdirs()) { "Dossier local inaccessible" }
            temp = java.io.File.createTempFile(".nemesis-transfer-", ".part", parent)
            channel.get(remote, temp.absolutePath)
            check(temp.length() == attrs.size) { "Transfert incomplet" }
            check(temp.renameTo(destination)) { "Impossible de placer le fichier transféré" }
            JSONObject().put("success",true).put("size",destination.length())
        } catch (e: Exception) {
            JSONObject().put("success",false).put("error","SSH transfert : " + (e.message ?: e.javaClass.simpleName))
        } finally { temp?.delete(); channel?.disconnect(); session?.disconnect() }.toString()
    }

    /** Synchronise a skill folder to an explicitly named remote SSH directory. */
    @JvmStatic fun uploadDirectory(local: String, remote: String): String {
        var session: com.jcraft.jsch.Session? = null
        var channel: com.jcraft.jsch.ChannelSftp? = null
        return try {
            val root=java.io.File(local).canonicalFile
            require(root.isDirectory && java.io.File(root,"SKILL.md").isFile) { "Dossier de skill invalide" }
            require(remote.startsWith("/") && remote.split('/').none { it == ".." } && remote.none { it.code < 32 }) { "Chemin distant absolu requis" }
            session = connect(); channel = session.openChannel("sftp") as com.jcraft.jsch.ChannelSftp; channel.connect(15_000)
            val sftp=channel
            fun mkdirs(path:String) {
                var part=""
                for(segment in path.split('/').filter { it.isNotBlank() && it != "." }) {
                    part += "/" + segment
                    try { val a=sftp.stat(part); check(a.isDir) { "Le chemin distant existe sans être un dossier" } }
                    catch(e:com.jcraft.jsch.SftpException) { if(e.id != com.jcraft.jsch.ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e; sftp.mkdir(part) }
                }
            }
            val dest=remote.trimEnd('/'); require(dest.isNotEmpty()) { "Racine distante interdite" }; mkdirs(dest)
            var count=0
            root.walkTopDown().forEach { entry ->
                if (entry != root) {
                    require(!java.nio.file.Files.isSymbolicLink(entry.toPath())) { "Lien symbolique interdit dans le skill" }
                    val resolved=entry.canonicalFile
                    require(resolved.path.startsWith(root.path+java.io.File.separator)) { "Chemin de skill hors dossier" }
                    val rel=entry.relativeTo(root).path.replace(java.io.File.separatorChar, '/')
                    val target="$dest/$rel"
                    if(entry.isDirectory) mkdirs(target) else if(entry.isFile) {
                        mkdirs(target.substringBeforeLast('/'))
                        entry.inputStream().use { input -> sftp.put(input,target) }
                        if(entry.canExecute()) try { sftp.chmod(493,target) } catch(_:Exception) {}
                        count++
                    }
                }
            }
            JSONObject().put("success",true).put("remote_path",dest).put("files",count)
        } catch(e:Exception) { JSONObject().put("success",false).put("error","SSH skill : " + (e.message ?: e.javaClass.simpleName)) }
        finally { channel?.disconnect(); session?.disconnect() }.toString()
    }

}
