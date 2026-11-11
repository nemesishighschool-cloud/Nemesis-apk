package com.nemesis.apk

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.Session
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.Executors

/** One persistent stdio MCP process per configured server, running on the pinned SSH host. */
object McpSshBridge {
    private data class Peer(val command:String, val session:Session, val channel:ChannelExec, val reader:BufferedReader, val writer:BufferedWriter)
    private val peers = mutableMapOf<String,Peer>()
    private val io = Executors.newCachedThreadPool()
    @JvmStatic @Synchronized fun close(id:String) { peers.remove(id)?.let { try { it.channel.disconnect() } finally { it.session.disconnect() } } }
    @JvmStatic @Synchronized fun exchange(id:String, command:String, data:String):String {
        return try {
            require(command.isNotBlank() && command.none { it.code < 32 && it != '\n' && it != '\t' }) { "Commande MCP invalide" }
            var peer=peers[id]
            if(peer != null && (peer.command != command || !peer.channel.isConnected)) { close(id); peer=null }
            if(peer == null) {
                val session=SshBridge.connect()
                try {
                    val channel=session.openChannel("exec") as ChannelExec
                    channel.setCommand(command)
                    channel.setErrStream(object:java.io.OutputStream() { override fun write(b:Int) {} })
                    val reader=channel.inputStream.bufferedReader(Charsets.UTF_8)
                    val writer=channel.outputStream.bufferedWriter(Charsets.UTF_8)
                    channel.connect(15_000); peer=Peer(command,session,channel,reader,writer); peers[id]=peer
                } catch(t:Throwable) { session.disconnect(); throw t }
            }
            val active=peer ?: throw IllegalStateException("Serveur MCP inaccessible")
            val payload=JSONObject(data); val wanted=if(payload.has("id")) payload.get("id").toString() else null
            active.writer.write(payload.toString()); active.writer.newLine(); active.writer.flush()
            if(wanted==null) "{}" else {
                val future=io.submit<String> {
                    while(true) {
                        val line=active.reader.readLine() ?: throw IllegalStateException("Le serveur MCP a fermé sa sortie")
                        val msg=JSONObject(line)
                        if(msg.has("id") && msg.get("id").toString()==wanted) return@submit msg.toString()
                        if(msg.has("id") && msg.has("method")) {
                            val error=JSONObject().put("code",-32601).put("message","Client request not supported")
                            active.writer.write(JSONObject().put("jsonrpc","2.0").put("id",msg.get("id")).put("error",error).toString()); active.writer.newLine(); active.writer.flush()
                        }
                    }
                    @Suppress("UNREACHABLE_CODE") "{}"
                }
                future.get()
            }
        } catch(e:Throwable) { close(id); JSONObject().put("_transport_error", e.cause?.message ?: e.message ?: e.javaClass.simpleName).toString() }
    }
}
