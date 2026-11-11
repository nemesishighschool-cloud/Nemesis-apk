package com.nemesis.apk

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment

class MainActivity : Activity() {
    private val white = Color.rgb(255,255,255)
    private val canvas = Color.rgb(250,252,254)
    private val sidebar = Color.rgb(245,249,253)
    private val pale = Color.rgb(233,246,255)
    private val primary = Color.rgb(30,126,191)
    private val strong = Color.rgb(19,40,60)
    private val muted = Color.rgb(91,111,130)
    private val line = Color.rgb(222,232,240)
    private val danger = Color.rgb(160,52,51)
    private val drafts = mutableMapOf<String,String>()
    private val attachments = mutableMapOf<String,MutableList<JSONObject>>()
    private var currentSession: String? = null
    private var currentMode = "chat"
    private val busy get() = JobRunner.busySession != null
    private val busySession get() = JobRunner.busySession
    private val pendingPreview get() = JobRunner.preview
    private var webHost: FrameLayout? = null
    private var screen = "home"
    private var drawer: View? = null
    private var host = FrameLayoutPlaceholder()
    private lateinit var root: LinearLayout
    private var selectedFile: String? = null
    private var pickerSession: String? = null
    private lateinit var markdown: Markwon
    private val py get() = Python.getInstance().getModule("mobile_agent")
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()

    // A nullable holder avoids touching a View before the Activity is created.
    private class FrameLayoutPlaceholder { var value: FrameLayout? = null }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        com.nemap.android.EmbeddedRuntime.start(applicationContext)
        window.statusBarColor = white; window.navigationBarColor = white
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        markdown = Markwon.builder(this).usePlugin(TablePlugin.create(this)).build()
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        if (!busy) configureStorage()
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 501)
        }
        SshBridge.init(this); McpSecretBridge.init(this)
        val id = saved?.getString("session")
        if (id != null) { drafts[id] = saved.getString("draft", ""); showChat(id) } else showHome()
        // Start after heavy Activity initialization so Android can dispatch the service promptly.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            RuntimeRecoveryJob.schedule(applicationContext)
            startForegroundService(Intent(this, NemesisService::class.java))
        }
    }
    override fun onSaveInstanceState(out: Bundle) {
        currentSession?.let { out.putString("session", it); out.putString("draft", drafts[it] ?: "") }
        super.onSaveInstanceState(out)
    }
    private fun configureStorage() {
        val hasStorage = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val shared = if (hasStorage) sharedNemesisDirectory().absolutePath else ""
        py.callAttr("configure", filesDir.absolutePath, com.nemap.android.EmbeddedRuntime.port(), com.nemap.android.EmbeddedRuntime.token(), shared)
        val skills = File(py.callAttr("skills_root").toString()); copyAssetTree("skills",skills)
    }
    /** Migrate the old shared directory without replacing existing user files. */
    private fun sharedNemesisDirectory(): File {
        val storage=Environment.getExternalStorageDirectory()
        val old=File(storage,"NEMESIS"); val target=File(storage,"Nemesis")
        if(old.exists() && !target.exists() && old.renameTo(target)) return target
        // Android shared storage may be case-insensitive: both names then resolve
        // to the same inode even though the directory listing still says NEMESIS.
        val listed=storage.listFiles()?.map { it.name } ?: emptyList()
        if("NEMESIS" in listed && "Nemesis" !in listed && old.exists() && target.exists()) {
            val same=try { val a=android.system.Os.stat(old.path); val b=android.system.Os.stat(target.path); a.st_dev==b.st_dev && a.st_ino==b.st_ino } catch(_:Exception) { false }
            if(same) {
                val temporary=File(storage,".nemesis-rename-"+UUID.randomUUID().toString())
                if(old.renameTo(temporary)) {
                    if(temporary.renameTo(target)) return target
                    temporary.renameTo(old) // retain original on a failed rename
                }
            }
        }
        if(old.isDirectory && old.canonicalPath != target.canonicalPath) {
            fun copyMissing(source:File,dest:File) {
                if(source.isDirectory) { if(!dest.exists()) dest.mkdirs(); source.listFiles()?.forEach { copyMissing(it,File(dest,it.name)) } }
                else if(source.isFile && !dest.exists()) { dest.parentFile?.mkdirs(); source.copyTo(dest,overwrite=false) }
            }
            try { copyMissing(old,target) } catch(_:Exception) { /* keep original intact */ }
        }
        target.mkdirs(); return target
    }
    private fun copyAssetTree(path:String, target:File) {
        val children=assets.list(path) ?: emptyArray()
        if (children.isEmpty()) {
            if (!target.exists()) { target.parentFile?.mkdirs(); assets.open(path).use { input -> target.outputStream().use { input.copyTo(it) } } }
        } else { target.mkdirs(); children.forEach { copyAssetTree("$path/$it",File(target,it)) } }
    }
    private fun showMcpServers() {
        val servers=JSONArray(py.callAttr("mcp_servers").toString())
        sheet("Serveurs MCP", "Outils distants : HTTP Streamable ou processus stdio sur le serveur SSH configuré.") { body, dialog ->
            body.addView(action("＋  Ajouter un serveur",pale,primary) { dialog.dismiss(); addMcpServer() }); body.addView(gap(10))
            if(servers.length()==0) body.addView(label("Aucun serveur configuré.",14f,muted))
            for(i in 0 until servers.length()) {
                val server=servers.getJSONObject(i); val name=server.optString("name"); val id=server.optString("id")
                body.addView(sheetRow(name,if(server.optString("transport")=="http") "HTTP Streamable" else "Stdio via SSH") { dialog.dismiss(); mcpServerOptions(id,name) }); body.addView(gap(8))
            }
        }
    }
    private fun addMcpServer() {
        sheet("Ajouter un serveur MCP", "Les outils MCP agissent sans confirmation. HTTP non chiffré expose les données ; ne mettez pas de secret dans une commande ou une URL.") { body, dialog ->
            val name=formField("Nom du serveur")
            val transport=Spinner(this).apply {
                adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("HTTP Streamable","Stdio via SSH"))
                contentDescription="Transport MCP"
            }
            val target=formField("URL HTTP(S) ou commande stdio",multiline=true)
            val secret=formField("Jeton Bearer HTTP facultatif",secret=true)
            body.addView(name); body.addView(gap(8)); body.addView(transport); body.addView(gap(8)); body.addView(target); body.addView(gap(8)); body.addView(secret); body.addView(gap(14))
            body.addView(action("Enregistrer",primary,white) {
                val kind=if(transport.selectedItemPosition==0) "http" else "ssh_stdio"
                val result=JSONObject(py.callAttr("mcp_save",name.text.toString(),kind,target.text.toString()).toString())
                if(result.optBoolean("ok")) {
                    val id=result.getString("id")
                    try { McpSecretBridge.save(id,if(kind=="http") secret.text.toString() else ""); dialog.dismiss(); showMcpServers() }
                    catch(e:Exception) { py.callAttr("mcp_delete",id); toast("Impossible de protéger le jeton : ${e.message}") }
                } else toast(result.optString("error","Configuration invalide"))
            })
        }
    }
    private fun mcpServerOptions(id:String,name:String) {
        sheet(name,"Un test établit la connexion et liste les outils disponibles.") { body, dialog ->
            body.addView(action("Tester la connexion",primary,white) {
                dialog.dismiss(); toast("Test MCP en cours…")
                Thread {
                    val result=try { JSONObject(py.callAttr("mcp_test",id).toString()) } catch(e:Exception) { JSONObject().put("ok",false).put("error",e.message) }
                    runOnUiThread {
                        if(!isFinishing && !isDestroyed) {
                            val tools=result.optJSONArray("tools") ?: JSONArray()
                            val detail=if(result.optBoolean("ok")) (0 until tools.length()).joinToString("\n") { tools.getJSONObject(it).optString("name") }.ifEmpty { "Connexion réussie, aucun outil." } else result.optString("error","Connexion impossible")
                            sheet(if(result.optBoolean("ok")) "Connexion réussie" else "Connexion impossible",detail) { _,_ -> }
                        }
                    }
                }.start()
            }); body.addView(gap(8))
            body.addView(action("Supprimer ce serveur",Color.TRANSPARENT,danger) { py.callAttr("mcp_delete",id); McpSecretBridge.delete(id); dialog.dismiss(); showMcpServers() })
        }
    }
    private fun showSkills() {
        val skills=JSONArray(py.callAttr("list_skills").toString())
        sheet("Skills disponibles", "Fichiers dans Nemesis/Skills. Work et Code peuvent les charger, lire leurs références et installer d’autres skills GitHub.") { body, dialog ->
            if(skills.length()==0) body.addView(label("Aucun skill installé.",14f,muted))
            for(i in 0 until skills.length()) {
                val item=skills.getJSONObject(i); val name=item.optString("name")
                body.addView(sheetRow(name,item.optString("description").take(140)) {
                    dialog.dismiss(); val file=File(py.callAttr("skills_root").toString(),"$name/SKILL.md")
                    sheet(name,"Instructions du skill — source dans le dossier Skills") { content, _ ->
                        content.addView(label(file.readText(),13f,strong).apply { setTextIsSelectable(true) })
                    }
                }); body.addView(gap(8))
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 501 && !busy) {
            configureStorage()
            if (results.isEmpty() || results[0] != PackageManager.PERMISSION_GRANTED) toast("Stockage partagé indisponible : les fichiers restent privés à l’application.")
        }
    }
    override fun onResume() {
        super.onResume()
        webHost?.let { com.nemap.android.web.WebViewHost.bindContainer(it) }
        currentSession?.let { if (screen == "chat") showChat(it) }
    }
    override fun onDestroy() {
        val oldContainer = webHost
        if (oldContainer != null) JobRunner.whenIdle { com.nemap.android.web.WebViewHost.unbindContainer(oldContainer) }
        super.onDestroy()
    }
    @Deprecated("Deprecated in Java") override fun onBackPressed() {
        if (drawer != null) closeDrawer() else if (screen == "memory") showSettings() else if (screen != "home") showHome() else super.onBackPressed()
    }
    private fun shape(fill: Int, radius: Int = 16, border: Int? = null) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(radius).toFloat(); border?.let { setStroke(dp(1), it) }
    }
    private fun label(s: String, size: Float = 15f, color: Int = strong, bold: Boolean = false) = TextView(this).apply {
        text = s; textSize = size; setTextColor(color); includeFontPadding = true
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }
    private fun action(text: String, fill: Int = Color.TRANSPARENT, color: Int = strong, click: () -> Unit): TextView = label(text, 14f, color, true).apply {
        gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(12), dp(7), dp(12), dp(7))
        background = shape(fill, 14); isClickable = true; isFocusable = true; setOnClickListener { click() }
    }
    /** Native NEMESIS sheet, not a platform AlertDialog. */
    private fun sheet(title: String, description: String = "", build: (LinearLayout, Dialog) -> Unit): Dialog {
        val dialog = Dialog(this)
        val outer = column().apply { background=shape(white,26); setPadding(dp(22),dp(12),dp(22),dp(20)) }
        val grip = View(this).apply { background=shape(line,4) }
        outer.addView(grip,LinearLayout.LayoutParams(dp(40),dp(4)).apply { gravity=Gravity.CENTER_HORIZONTAL })
        outer.addView(gap(14))
        val head=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        head.addView(label(title,20f,strong,true),LinearLayout.LayoutParams(0,-2,1f))
        head.addView(action("×",Color.TRANSPARENT,muted) { dialog.dismiss() }.apply { textSize=24f; contentDescription="Fermer" },LinearLayout.LayoutParams(dp(48),dp(48)))
        outer.addView(head)
        if(description.isNotBlank()) { outer.addView(label(description,13f,muted)); outer.addView(gap(8)) }
        val content=column()
        val scroll=object:ScrollView(this) {
            override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                val cap=(resources.displayMetrics.heightPixels * .58f).toInt()
                super.onMeasure(widthMeasureSpec,View.MeasureSpec.makeMeasureSpec(cap,View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport=false; addView(content) }
        outer.addView(scroll,LinearLayout.LayoutParams(-1,-2)); build(content,dialog)
        dialog.setContentView(outer)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show(); dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.window?.setGravity(Gravity.BOTTOM)
        return dialog
    }
    private fun sheetRow(title:String, description:String="", click:()->Unit):View = LinearLayout(this).apply {
        orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),dp(12),dp(12),dp(12)); minimumHeight=dp(56)
        background=shape(canvas,15); isClickable=true; isFocusable=true
        val texts=column().apply { addView(label(title,15f,strong,true)); if(description.isNotBlank()) { addView(gap(3)); addView(label(description,12f,muted)) } }
        addView(texts,LinearLayout.LayoutParams(0,-2,1f)); addView(label("›",23f,muted)); setOnClickListener { click() }
    }
    private fun formField(hintText:String,value:String="",secret:Boolean=false,multiline:Boolean=false):EditText = EditText(this).apply {
        hint=hintText; textSize=15f; setTextColor(strong); setHintTextColor(muted); background=shape(canvas,14,line); setPadding(dp(14),dp(12),dp(14),dp(12)); minHeight=dp(50)
        if(secret) inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        else if(multiline) { inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines=3; maxLines=7 }
        else setSingleLine(true)
        setText(value)
    }
    private fun frame() {
        drawer = null
        val h = host.value ?: FrameLayout(this).also { frame ->
            frame.setBackgroundColor(canvas); host.value = frame
            val web = FrameLayout(this).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
            webHost = web
            // A 1dp viewport makes the provider virtualize away conversation DOM,
            // preventing recovery from an empty/unparseable network stream.
            // The opaque native root is layered above this full-size WebView.
            frame.addView(web, FrameLayout.LayoutParams(-1,-1,Gravity.TOP or Gravity.START))
            setContentView(frame)
            com.nemap.android.web.WebViewHost.bindContainer(web)
        }
        if (::root.isInitialized) h.removeView(root)
        while(h.childCount > 1) h.removeViewAt(h.childCount-1)
        root = column().apply { setBackgroundColor(canvas) }
        h.addView(root, FrameLayout.LayoutParams(-1,-1))
    }
    private fun topBar(title: String, subtitle: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)); setBackgroundColor(white) }
        row.addView(action("☰", Color.TRANSPARENT, strong) { openDrawer() }.apply { textSize = 21f; contentDescription = "Ouvrir le menu" }, LinearLayout.LayoutParams(dp(48),dp(48)))
        val titles = column().apply { setPadding(dp(7),0,0,0); addView(label(title,16f,strong,true).apply { maxLines = 1 }); addView(label(subtitle,11f,muted)) }
        row.addView(titles, LinearLayout.LayoutParams(0,-2,1f))
        val sessionId = if (screen == "chat") currentSession else null
        if (sessionId != null) {
            row.addView(action("📁", Color.TRANSPARENT, primary) { showSessionFiles(sessionId) }.apply { textSize=22f; contentDescription="Fichiers de cette discussion" }, LinearLayout.LayoutParams(dp(48),dp(48)))
        } else {
            row.addView(action("＋", Color.TRANSPARENT, primary) { chooseMode() }.apply { textSize = 24f; contentDescription = "Nouvelle discussion" }, LinearLayout.LayoutParams(dp(48),dp(48)))
        }
        return row
    }
    private fun separator() = View(this).apply { setBackgroundColor(line); layoutParams = LinearLayout.LayoutParams(-1,dp(1)) }
    private fun scrollArea(pad: Int = 18): Pair<ScrollView,LinearLayout> {
        val body = column().apply { setPadding(dp(pad),dp(14),dp(pad),dp(16)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(body) }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); return scroll to body
    }
    private fun modeName(mode: String) = when(mode) { "work" -> "Work"; "code" -> "Code"; else -> "Chat" }
    private fun modeDescription(mode: String) = when(mode) {
        "work" -> "Fichiers et commandes essentielles"
        "code" -> "Développement et environnement SSH"
        else -> "Une conversation, tout simplement"
    }
    private fun showHome() {
        screen = "home"; currentSession = null; frame(); root.addView(topBar("NEMESIS", "Votre assistant personnel")); root.addView(separator())
        val (_, body) = scrollArea(24)
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.addView(gap(40))
        body.addView(ImageView(this).apply { setImageResource(R.drawable.nemesis_logo); scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(pale,20); clipToOutline = true; contentDescription = "Logo NEMESIS" }, LinearLayout.LayoutParams(dp(62),dp(62)))
        body.addView(gap(23))
        body.addView(label("Bonjour,", 29f,strong,true).apply { gravity=Gravity.CENTER }); body.addView(label("comment puis-je vous aider ?", 23f,strong,true).apply { gravity = Gravity.CENTER })
        body.addView(gap(10)); body.addView(label("Choisissez un espace pour commencer.", 14f,muted).apply { gravity = Gravity.CENTER })
        body.addView(gap(32))
        for ((mode,title,desc) in listOf(Triple("chat","Discuter", "Échanger et explorer des idées"),Triple("work","Travailler", "Fichiers et tâches du quotidien"),Triple("code","Coder", "Développer avec votre environnement"))) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(14),dp(14),dp(14)); background = shape(white,18,line)
                val icon = label(when(mode){"work"->"▤";"code"->"</>";else->"C"},18f,primary,true).apply { gravity = Gravity.CENTER; background = shape(pale,12) }
                addView(icon, LinearLayout.LayoutParams(dp(46),dp(46)))
                val txt = column().apply { setPadding(dp(14),0,0,0); addView(label(title,15f,strong,true)); addView(label(desc,12f,muted)) }
                addView(txt,LinearLayout.LayoutParams(0,-2,1f)); addView(label("›",24f,muted))
                isClickable = true; isFocusable = true; setOnClickListener { create(mode) }
            }
            body.addView(card, LinearLayout.LayoutParams(-1,-2)); body.addView(gap(10))
        }
        body.addView(gap(18)); body.addView(label("Vos conversations sont dans le menu latéral.",12f,muted).apply { gravity = Gravity.CENTER })
    }
    private fun create(mode: String) {
        if (busy) { toast("Attendez la fin de la réponse en cours."); return }
        val r = JSONObject(py.callAttr("create_session",mode).toString())
        if (r.optBoolean("ok")) showChat(r.getString("id")) else toast(r.optString("error"))
    }
    private fun chooseMode() {
        sheet("Nouvelle discussion", "Choisissez l’espace adapté à votre besoin.") { body, dialog ->
            for ((mode,title,desc) in listOf(Triple("chat","Chat","Échanger, apprendre et créer"),Triple("work","Work","Recherche et travail de bureau"),Triple("code","Code","Développement et environnement SSH"))) {
                body.addView(sheetRow(title,desc) { dialog.dismiss(); create(mode) }); body.addView(gap(9))
            }
        }
    }
    private fun closeDrawer() { drawer?.let { host.value?.removeView(it) }; drawer = null }
    private fun openDrawer() {
        if (drawer != null) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
        val layer = FrameLayout(this)
        val scrim = View(this).apply { setBackgroundColor(0x660C2133); setOnClickListener { closeDrawer() } }
        layer.addView(scrim, FrameLayout.LayoutParams(-1,-1))
        val width = minOf(dp(322), resources.displayMetrics.widthPixels - dp(42))
        val menu = column().apply { setBackgroundColor(sidebar); setPadding(dp(16),dp(18),dp(16),dp(12)); elevation = dp(10).toFloat() }
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        brand.addView(ImageView(this).apply { setImageResource(R.drawable.nemesis_logo); scaleType=ImageView.ScaleType.CENTER_CROP; background=shape(pale,12); clipToOutline=true },LinearLayout.LayoutParams(dp(42),dp(42)))
        brand.addView(column().apply { setPadding(dp(11),0,0,0); addView(label("NEMESIS",15f,strong,true)); addView(label("Votre espace IA",11f,muted)) },LinearLayout.LayoutParams(0,-2,1f))
        brand.addView(action("×",Color.TRANSPARENT,muted) { closeDrawer() }.apply { textSize=23f; contentDescription="Fermer le menu" },LinearLayout.LayoutParams(dp(48),dp(48)))
        menu.addView(brand); menu.addView(gap(25))
        menu.addView(action("＋    Nouvelle discussion",pale,primary) { closeDrawer(); chooseMode() }.apply { gravity=Gravity.CENTER_VERTICAL or Gravity.START }); menu.addView(gap(14))
        val search = EditText(this).apply { hint="Rechercher une discussion"; textSize=14f; setSingleLine(true); setTextColor(strong); setHintTextColor(muted); background=shape(Color.rgb(235,242,248),14); setPadding(dp(13),0,dp(13),0); minHeight=dp(48) }
        menu.addView(search); menu.addView(gap(20)); menu.addView(label("DISCUSSIONS",11f,muted,true)); menu.addView(gap(7))
        val list = column(); val scroll = ScrollView(this).apply { addView(list); isFillViewport=true }
        menu.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun refresh(query: String) {
            list.removeAllViews(); val sessions = JSONArray(py.callAttr("list_sessions").toString()); var shown=0
            for(i in 0 until sessions.length()) {
                val s = sessions.getJSONObject(i); val title=s.optString("title"); if (!title.contains(query,true)) continue; shown++
                val id=s.getString("id")
                val item=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; background=shape(if(id==currentSession) pale else Color.TRANSPARENT,12) }
                val text=column().apply {
                    setPadding(dp(12),dp(10),dp(4),dp(10)); addView(label(title,14f,strong,id==currentSession).apply { maxLines=2 })
                    addView(label((if(s.optInt("pinned")==1) "Épinglée · " else "") + modeName(s.optString("mode")) + if(s.optInt("uncertain")==1) " · À vérifier" else "",11f,muted))
                    isClickable=true; isFocusable=true; setOnClickListener { closeDrawer(); showChat(id) }
                    setOnLongClickListener { closeDrawer(); sessionOptions(s); true }
                }
                item.addView(text,LinearLayout.LayoutParams(0,-2,1f))
                item.addView(action("⋯",Color.TRANSPARENT,muted) { closeDrawer(); sessionOptions(s) }.apply { textSize=22f; contentDescription="Options de la discussion $title" },LinearLayout.LayoutParams(dp(48),dp(48)))
                list.addView(item)

            }
            if(shown==0) list.addView(label("Aucune discussion trouvée.",13f,muted).apply { setPadding(dp(12),dp(18),0,0) })
        }
        refresh(""); search.addTextChangedListener(object: TextWatcher { override fun beforeTextChanged(s:CharSequence?, start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh(s?.toString()?:"")}; override fun afterTextChanged(s:Editable?){} })
        menu.addView(separator()); menu.addView(action("▤    Fichiers",Color.TRANSPARENT,strong) { closeDrawer(); showFiles() }.apply { gravity=Gravity.CENTER_VERTICAL })
        menu.addView(action("⚙    Paramètres",Color.TRANSPARENT,strong) { closeDrawer(); showSettings() }.apply { gravity=Gravity.CENTER_VERTICAL })
        menu.addView(label("NEMESIS · version de développement",11f,muted).apply { setPadding(dp(12),dp(5),0,0) })
        layer.addView(menu,FrameLayout.LayoutParams(width,-1,Gravity.START)); host.value?.addView(layer,FrameLayout.LayoutParams(-1,-1)); drawer=layer
    }
    private fun sessionOptions(session:JSONObject) {
        val id=session.getString("id"); val title=session.optString("title"); val pinned=session.optInt("pinned")==1
        sheet("Discussion", title) { body, dialog ->
            body.addView(sheetRow("Renommer", "Modifier le nom affiché") { dialog.dismiss(); renameSession(id,title) }); body.addView(gap(8))
            body.addView(sheetRow(if(pinned) "Désépingler" else "Épingler", "Retrouver rapidement cette discussion") {
                py.callAttr("pin_session",id,!pinned); dialog.dismiss(); openDrawer()
            }); body.addView(gap(8))
            body.addView(sheetRow("Supprimer", "Supprimer uniquement la copie locale") { dialog.dismiss(); confirmDelete(id) })
        }
    }
    private fun renameSession(id:String, old:String) {
        sheet("Renommer la discussion") { body, dialog ->
            val input=formField("Nom de la discussion",old); body.addView(input); body.addView(gap(15))
            body.addView(action("Enregistrer",primary,white) {
                val result=JSONObject(py.callAttr("rename_session",id,input.text.toString()).toString())
                if(result.optBoolean("ok")) { dialog.dismiss(); openDrawer() } else toast(result.optString("error"))
            })
        }
    }
    private fun confirmDelete(id:String) {
        if(busy) { toast("Attendez la fin de la réponse."); return }
        sheet("Supprimer la discussion ?", "La copie locale sera supprimée. La conversation DeepSeek ne sera pas supprimée.") { body, dialog ->
            body.addView(action("Supprimer",danger,white) { dialog.dismiss(); py.callAttr("delete_session",id); drafts.remove(id); if(currentSession==id) showHome() else openDrawer() }); body.addView(gap(8))
            body.addView(action("Annuler",Color.TRANSPARENT,muted) { dialog.dismiss() })
        }
    }
    private fun bubble(role:String, text:String, sessionId:String?=null, messageId:Long=0L, durationMs:Long?=null, initialRating:Int=0): View {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=if(role=="user") Gravity.END else Gravity.START }
        val card=column().apply {
            setPadding(dp(16),dp(13),dp(16),dp(13)); background=shape(if(role=="user") pale else white,20,if(role=="error") Color.rgb(232,187,185) else line)
            addView(label(if(role=="user") "VOUS" else if(role=="error") "ATTENTION" else "NEMESIS",10f,if(role=="error") danger else primary,true)); addView(gap(5))
            val msg=label("",15f,if(role=="error") danger else strong).apply { setTextIsSelectable(true); setLineSpacing(dp(3).toFloat(),1f) }
            if(role=="user" || role=="error") msg.text=text else markdown.setMarkdown(msg,text)
            addView(msg)
            if(role=="assistant" && currentMode=="chat" && sessionId!=null && messageId>0L) {
                val footer=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,dp(5),0,0) }
                fun marker(symbol:String, description:String, click:()->Unit):TextView = label(symbol,15f,muted).apply {
                    gravity=Gravity.CENTER; contentDescription=description; isClickable=true; isFocusable=true
                    setOnClickListener { click() }
                }
                footer.addView(marker("⧉","Copier la réponse") {
                    val clipboard=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Réponse NEMESIS",text)); toast("Réponse copiée")
                },LinearLayout.LayoutParams(dp(40),dp(40)))
                var rating=initialRating
                lateinit var good:TextView; lateinit var bad:TextView
                fun choose(value:Int) {
                    val next=if(rating==value) 0 else value
                    val result=JSONObject(py.callAttr("rate_message",sessionId,messageId,next).toString())
                    if(result.optBoolean("ok")) { rating=next; good.background=if(rating==1) shape(pale,10) else null; bad.background=if(rating== -1) shape(pale,10) else null; good.setTextColor(if(rating==1) primary else muted); bad.setTextColor(if(rating== -1) danger else muted); good.contentDescription=if(rating==1) "Bonne réponse, sélectionné" else "Bonne réponse"; bad.contentDescription=if(rating== -1) "Mauvaise réponse, sélectionné" else "Mauvaise réponse" }
                    else toast("Évaluation non enregistrée")
                }
                good=marker("👍","Bonne réponse") { choose(1) }; bad=marker("👎","Mauvaise réponse") { choose(-1) }
                good.setTextColor(if(rating==1) primary else muted); bad.setTextColor(if(rating== -1) danger else muted); good.background=if(rating==1) shape(pale,10) else null; bad.background=if(rating== -1) shape(pale,10) else null
                footer.addView(good,LinearLayout.LayoutParams(dp(40),dp(40))); footer.addView(bad,LinearLayout.LayoutParams(dp(40),dp(40)))
                if(durationMs!=null) {
                    val seconds=(durationMs+500)/1000
                    val elapsed=if(seconds<60) "${seconds} s" else "${seconds/60} min ${seconds%60} s"
                    footer.addView(label("◷  $elapsed",11f,muted).apply { contentDescription="Temps de réponse : $elapsed"; setPadding(dp(5),0,0,0) })
                }
                addView(footer)
            }
        }
        val width = (resources.displayMetrics.widthPixels * if(role=="user") .83 else .90).toInt()
        row.addView(card,LinearLayout.LayoutParams(width,-2)); return row
    }
    private fun toolCard(raw: String): View {
        val obj=try { JSONObject(raw) } catch(_:Exception) { null }
        val name=obj?.optString("name") ?: raw.substringBefore(" — ")
        val result=obj?.optJSONObject("result"); val pending=result?.optBoolean("pending",false) ?: false; val ok=result?.optBoolean("success",false) ?: !raw.contains("échec")
        val params=obj?.optJSONObject("parameters"); val arg=params?.optString("command")?.ifBlank { params.optString("path") } ?: ""
        val card=column().apply { background=shape(white,16,line); setPadding(dp(13),dp(8),dp(13),dp(8)) }
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; orientation=LinearLayout.HORIZONTAL }
        row.addView(label(if(pending) "…" else if(ok) "✓" else "!",15f,if(pending || ok) primary else danger,true).apply { gravity=Gravity.CENTER; background=shape(pale,10) },LinearLayout.LayoutParams(dp(34),dp(34)))
        val titles=column().apply { setPadding(dp(10),0,0,0); addView(label(name + if(pending) " · en cours" else if(ok) " · terminé" else " · échec",13f,strong,true)); if(arg.isNotBlank()) addView(label(arg.take(70),12f,muted).apply { maxLines=1 }) }
        row.addView(titles,LinearLayout.LayoutParams(0,-2,1f)); val arrow=label("⌄",20f,muted); row.addView(arrow)
        val detail=label(if(obj==null) raw else "PARAMÈTRES\n${params?.toString(2) ?: "{}"}\n\nRÉSULTAT\n${result?.toString(2) ?: "{}"}",12f,strong).apply {
            typeface=Typeface.MONOSPACE; setTextIsSelectable(true); setPadding(dp(8),dp(14),dp(8),dp(8)); visibility=View.GONE
        }
        row.isClickable=true; row.isFocusable=true; row.contentDescription="Détails de l’outil $name"; row.setOnClickListener { val open=detail.visibility==View.GONE; detail.visibility=if(open) View.VISIBLE else View.GONE; arrow.text=if(open) "⌃" else "⌄" }
        if(getSharedPreferences("display",MODE_PRIVATE).getBoolean("tool_details",false)) { detail.visibility=View.VISIBLE; arrow.text="⌃" }
        card.addView(row); card.addView(detail); return card
    }
    private fun compactTool(raw:String, mode:String):View? {
        val obj=try { JSONObject(raw) } catch(_:Exception) { null }
        val name=obj?.optString("name") ?: "outil"
        val result=obj?.optJSONObject("result")
        val pending=result?.optBoolean("pending",false) ?: false
        val ok=result?.optBoolean("success",false) ?: false
        if(mode=="chat") {
            if(name !in listOf("memory_add","memory_update","memory_delete")) return null
            val message=if(pending) "Mise à jour de la mémoire…" else if(ok) "Mémoire mise à jour" else "Mise à jour de la mémoire impossible"
            return label(message,12f,if(!pending && !ok) danger else muted).apply { setPadding(dp(4),dp(2),0,dp(2)) }
        }
        val title=when(name) {
            "bash" -> "Exécution d’une commande"; "read_file" -> "Lecture d’un fichier"; "write_file" -> "Création d’un fichier"
            "edit", "append" -> "Modification d’un fichier"; "download_file" -> "Récupération d’un fichier"
            "web_search" -> "Recherche sur le Web"; "web_fetch" -> "Consultation d’une page Web"
            "memory_add", "memory_update", "memory_delete" -> "Mise à jour de la mémoire"
            "memory_get", "memory_search" -> "Consultation de la mémoire"
            "skill_list", "skill_read", "skill_install", "skill_sync" -> "Utilisation d’un skill"
            "mcp_list", "mcp_tools", "mcp_call" -> "Utilisation d’un outil connecté"
            else -> "Utilisation d’un outil"
        }
        return label((if(pending) "◌  " else if(ok) "✓  " else "!  ")+title+(if(pending) "…" else if(!ok) " · échec" else ""),12f,if(!pending && !ok) danger else muted).apply { setPadding(dp(6),dp(4),dp(6),dp(4)); background=shape(pale,10) }
    }
    private fun recoverChat(id:String,known:String) {
        sheet("Reprendre la discussion", "Le dernier message a peut-être été envoyé à DeepSeek. Il ne sera pas renvoyé automatiquement. Vérifiez la conversation avant de continuer.") { body, dialog ->
            val url=formField("URL de la conversation DeepSeek",known); body.addView(url); body.addView(gap(10))
            body.addView(action("Reprendre",primary,white) {
                val result=JSONObject(py.callAttr("recover_session",id,url.text.toString()).toString())
                if(result.optBoolean("ok")) { dialog.dismiss(); showChat(id) } else toast(result.optString("error"))
            })
        }
    }
    private fun showChat(id: String) = showChat(id, null)
    private fun showChat(id: String, preservedScrollY:Int?) {
        val session=JSONObject(py.callAttr("get_session",id).toString())
        if(!session.optBoolean("ok")) { showHome(); return }
        currentSession=id; currentMode=session.optString("mode","chat"); screen="chat"; frame()
        root.addView(topBar("NEMESIS",modeName(currentMode)+" · DeepSeek")); root.addView(separator())
        val (scroll,body)=scrollArea(16)
        // The shortcut floats above the discussion, not inside the growing
        // message list, and only appears when more content is below the reader.
        root.removeView(scroll)
        val scrollLayer=FrameLayout(this)
        scrollLayer.addView(scroll,FrameLayout.LayoutParams(-1,-1))
        val down=label("↓",22f,primary,true).apply {
            gravity=Gravity.CENTER; background=shape(white,99,line); elevation=dp(5).toFloat()
            contentDescription="Aller en bas de la discussion"; isClickable=true; isFocusable=true; visibility=View.GONE
            setOnClickListener { scroll.smoothScrollTo(0,body.height) }
        }
        scrollLayer.addView(down,FrameLayout.LayoutParams(dp(48),dp(48),Gravity.BOTTOM or Gravity.END).apply { marginEnd=dp(16); bottomMargin=dp(16) })
        fun updateDownButton() { down.visibility=if(scroll.canScrollVertically(1)) View.VISIBLE else View.GONE }
        scroll.setOnScrollChangeListener { _:View, _:Int, _:Int, _:Int, _:Int -> updateDownButton() }
        body.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> updateDownButton() }
        scroll.post { updateDownButton() }
        root.addView(scrollLayer,LinearLayout.LayoutParams(-1,0,1f))
        val messages=session.optJSONArray("messages") ?: JSONArray()
        if(messages.length()==0) {
            body.addView(gap(55)); body.addView(ImageView(this).apply { setImageResource(R.drawable.nemesis_logo); scaleType=ImageView.ScaleType.CENTER_CROP; background=shape(pale,14); clipToOutline=true; contentDescription="Logo NEMESIS" }, LinearLayout.LayoutParams(dp(52),dp(52)).apply { gravity=Gravity.CENTER_HORIZONTAL })
            body.addView(gap(12)); body.addView(label("Que souhaitez-vous faire ?",22f,strong,true).apply { gravity=Gravity.CENTER })
            body.addView(gap(7)); body.addView(label(modeDescription(currentMode),14f,muted).apply { gravity=Gravity.CENTER })
            if(currentMode!="chat") { body.addView(gap(14)); body.addView(label("Les outils sont exécutés sans confirmation. Les fichiers SSH et locaux sont distincts.",12f,muted).apply { gravity=Gravity.CENTER }) }
        }
        for(i in 0 until messages.length()) {
            val item=messages.getJSONObject(i); val role=item.optString("role"); val text=item.optString("text")
            val duration=if(item.isNull("duration_ms")) null else item.optLong("duration_ms")
            val view=if(role=="tool") { if(currentMode=="code") toolCard(text) else compactTool(text,currentMode) } else bubble(role,text,id,item.optLong("id"),duration,item.optInt("rating"))
            if(view!=null) { body.addView(view); body.addView(gap(if(role=="tool") 5 else 12)) }
            if (role == "tool") {
                val tool = try { JSONObject(text) } catch (_:Exception) { null }
                val result = tool?.optJSONObject("result")
                val path = if (tool?.optString("name") in listOf("write_file","edit","append","download_file") && result?.optBoolean("success") == true) result.optString("path") else ""
                if (path.isNotBlank()) {
                    body.addView(action("▤   $path    ›", white, primary) { showFile(path,id) }.apply { gravity=Gravity.CENTER_VERTICAL or Gravity.START; background=shape(white,14,line); contentDescription="Ouvrir le fichier $path" }); body.addView(gap(12))
                }
            }
        }
        if(busy && busySession == id && pendingPreview != null) {
            val recorded = (0 until messages.length()).any { i ->
                val m = messages.getJSONObject(i); m.optString("role") == "user" && m.optString("text") == pendingPreview
            }
            if(!recorded) { body.addView(bubble("user", pendingPreview!!)); body.addView(gap(12)) }
        }
        if(busy && busySession == id) {
            val status = label("NEMESIS réfléchit…",13f,primary,true)
            val live = label("",15f,strong).apply { setLineSpacing(dp(3).toFloat(),1f); visibility=View.GONE }
            body.addView(status); body.addView(gap(8)); body.addView(live); body.addView(gap(10))
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var last = ""
            val update = object : Runnable {
                override fun run() {
                    if (screen != "chat" || currentSession != id || !live.isAttachedToWindow) return
                    if (!busy || busySession != id) {
                        JobRunner.takeError()?.let { toast(it) }
                        showChat(id,scroll.scrollY); return
                    }
                    status.text="NEMESIS réfléchit" + ".".repeat(((android.os.SystemClock.uptimeMillis()/500)%4).toInt())
                    val currentMessages = try { JSONObject(py.callAttr("get_session",id).toString()).optJSONArray("messages")?.toString() ?: "[]" } catch (_:Exception) { messages.toString() }
                    if (currentMessages != messages.toString()) { showChat(id,scroll.scrollY); return }
                    val partial = try { JSONObject(py.callAttr("live_progress",id).toString()).optString("text", "") } catch (_:Exception) { "" }
                    if (partial != last) {
                        last = partial; live.visibility = if(partial.isBlank()) View.GONE else View.VISIBLE
                        // Render a lightweight text preview during generation; completed answers use Markdown.
                        // Never drag the reader down while new text arrives.
                        live.text=partial
                    }
                    handler.postDelayed(this,350)
                }
            }
            live.postDelayed(update,350)
        }
        scroll.post { if(preservedScrollY!=null) scroll.scrollTo(0,preservedScrollY) else scroll.fullScroll(View.FOCUS_DOWN) }
        val compose=column().apply { setBackgroundColor(canvas); setPadding(dp(16),dp(6),dp(16),dp(10)) }
        if(session.optInt("uncertain")==1 && !busy) {
            compose.addView(action("Reprendre la discussion",pale,primary) { recoverChat(id,if(session.isNull("conversation_url")) "" else session.optString("conversation_url", "")) })
            compose.addView(gap(6))
        }
        val pending=attachments[id] ?: mutableListOf()
        if(pending.isNotEmpty()) for(item in pending.toList()) {
            compose.addView(action("Pièce jointe : ${item.optString("name")}    ×",pale,primary) { attachments[id]?.remove(item); File(item.optString("path")).delete(); showChat(id) }.apply { gravity=Gravity.START or Gravity.CENTER_VERTICAL })
        }
        val box=column().apply { background=shape(white,22,line); setPadding(dp(7),dp(4),dp(7),dp(6)); elevation=dp(2).toFloat() }
        val input=EditText(this).apply {
            hint="Écrivez à NEMESIS…"; textSize=15f; setTextColor(strong); setHintTextColor(muted); background=null
            setPadding(dp(12),dp(10),dp(12),dp(5)); minLines=1; maxLines=5; setText(drafts[id] ?: "")
            isEnabled=!busy
            addTextChangedListener(object:TextWatcher { override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}; override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){drafts[id]=s?.toString()?:""}; override fun afterTextChanged(s:Editable?){} })
        }
        box.addView(input,LinearLayout.LayoutParams(-1,-2))
        val controls=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        controls.addView(action("＋",Color.TRANSPARENT,primary) { attachmentMenu(id) }.apply { textSize=23f; contentDescription="Ajouter une pièce jointe"; isEnabled=!busy },LinearLayout.LayoutParams(dp(48),dp(48)))
        controls.addView(label(modeName(currentMode)+"  ·  DeepSeek",12f,muted,true),LinearLayout.LayoutParams(0,-2,1f))
        val send=action(if(busy && busySession==id) "■" else "↑",primary,white) {
            if (busy && busySession==id) { py.callAttr("cancel"); toast("Arrêt demandé…"); return@action }
            val text=input.text.toString().trim(); val list=attachments[id]?.toList() ?: emptyList()
            if((text.isNotEmpty() || list.isNotEmpty()) && !busy) {
                drafts[id]=""; attachments.remove(id); val json=JSONArray(); list.forEach { json.put(it) }
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(input.windowToken,0)
                val previewText = if(text.isBlank()) "Analyse les pièces jointes." else text
                val preview = previewText + (if(list.isEmpty()) "" else "\n\nPièces jointes : " + list.joinToString(", ") { it.optString("name") })
                if (JobRunner.start(applicationContext,id,text,json.toString(),preview)) showChat(id)

            }
        }.apply {
            textSize=20f; setPadding(0,0,0,0); minimumHeight=0; minHeight=0
            background=shape(primary,99); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isEnabled=(busy && busySession==id) || (!busy && session.optInt("uncertain")!=1); alpha=if(isEnabled) 1f else .45f
        }
        val sendTarget=FrameLayout(this).apply {
            contentDescription=if(busy && busySession==id) "Arrêter la tâche" else "Envoyer le message"; isClickable=true; isFocusable=true
            isEnabled=send.isEnabled; setOnClickListener { send.performClick() }
            addView(send,FrameLayout.LayoutParams(dp(38),dp(38),Gravity.CENTER))
        }
        controls.addView(sendTarget,LinearLayout.LayoutParams(dp(48),dp(48))); box.addView(controls); compose.addView(box)
        compose.addView(label(if(session.optInt("uncertain")==1) "Reprenez la discussion avant d’envoyer ; le dernier message a peut-être été transmis." else "NEMESIS peut se tromper. Vérifiez les informations importantes.",11f,muted).apply { gravity=Gravity.CENTER; setPadding(0,dp(7),0,0) })
        root.addView(compose)
    }
    private fun attachmentMenu(id:String) {
        if((attachments[id]?.size ?: 0)>=4) { toast("Maximum 4 pièces jointes."); return }
        pickerSession=id
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="*/*" }
        try { startActivityForResult(intent,302) } catch(_:Exception) { toast("Sélecteur de fichiers indisponible.") }
    }
    private fun showFiles() {
        screen="files"; currentSession=null; frame(); root.addView(topBar("Fichiers","Espaces de travail")); root.addView(separator())
        val (_,body)=scrollArea(20); body.addView(gap(12)); body.addView(label("Espaces de travail",25f,strong,true)); body.addView(gap(6))
        body.addView(label("Fichiers Work et Code, classés par discussion.",14f,muted)); body.addView(gap(20))
        val sessions=JSONArray(py.callAttr("list_sessions").toString()); var shown=0
        for(i in 0 until sessions.length()) {
            val session=sessions.getJSONObject(i); val id=session.optString("id"); val mode=session.optString("mode")
            if(mode != "work" && mode != "code") continue
            val files=try { JSONArray(py.callAttr("list_files",id).toString()) } catch (_:Exception) { JSONArray() }
            if(files.length()==0) continue
            shown++
            body.addView(sheetRow(session.optString("title"), modeName(mode)+" · "+files.length()+" fichier(s)") { showSessionFiles(id) }); body.addView(gap(8))
        }
        val legacy=JSONArray(py.callAttr("list_files").toString())
        for(i in 0 until legacy.length()) {
            shown++; val name=legacy.getJSONObject(i).optString("name")
            body.addView(sheetRow(name,"Ancien espace privé") { showFile(name) }); body.addView(gap(8))
        }
        if(shown==0) body.addView(label("Aucun fichier pour le moment.",14f,muted))
    }
    private fun showSessionFiles(id:String) {
        val files=try { JSONArray(py.callAttr("list_files",id).toString()) } catch (e:Exception) { toast(e.message ?: "Espace indisponible"); return }
        sheet("Fichiers de la discussion", "Les fichiers créés ou rapatriés dans cet espace local.") { body, dialog ->
            if(files.length()==0) body.addView(label("Aucun fichier pour le moment.",14f,muted))
            for(i in 0 until files.length()) {
                val item=files.getJSONObject(i); val name=item.optString("name")
                body.addView(sheetRow(name, "${item.optLong("size")} octets") { dialog.dismiss(); showFile(name,id) }); body.addView(gap(8))
            }
        }
    }
    private fun docxText(file:File):String {
        java.util.zip.ZipFile(file).use { zip ->
            val entry=zip.getEntry("word/document.xml") ?: return "Document Word non reconnu."
            if(entry.size > 2_000_000L) return "Document trop volumineux pour cet aperçu."
            val xml=zip.getInputStream(entry).use { input ->
                val out=java.io.ByteArrayOutputStream(); val b=ByteArray(8192); var total=0
                while(true) { val n=input.read(b); if(n<0) break; total+=n; if(total>2_000_000) throw IllegalArgumentException("Document trop volumineux"); out.write(b,0,n) }; out.toByteArray()
            }
            val parser=android.util.Xml.newPullParser(); parser.setInput(xml.inputStream(),"UTF-8")
            val result=StringBuilder(); var event=parser.eventType; var inText=false
            while(event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT && result.length<50_000) {
                val local=parser.name?.substringAfter(':')
                if(event==org.xmlpull.v1.XmlPullParser.START_TAG && local=="t") inText=true
                if(event==org.xmlpull.v1.XmlPullParser.TEXT && inText) result.append(parser.text)
                if(event==org.xmlpull.v1.XmlPullParser.END_TAG && local=="t") inText=false
                if(event==org.xmlpull.v1.XmlPullParser.END_TAG && local=="p") result.append('\n')
                event=parser.next()
            }
            return result.toString().ifBlank { "Aucun texte à afficher." }
        }
    }
    private fun showFile(name:String, sid:String?=null) {
        val base=File(if(sid==null) py.callAttr("workspace_root").toString() else py.callAttr("workspace_root",sid).toString()).canonicalFile
        val file=File(base,name).canonicalFile
        if(!file.path.startsWith(base.path+File.separator)||!file.isFile) { toast("Fichier inaccessible"); return }
        sheet(name,"Aperçu dans NEMESIS") { body, dialog ->
            try {
                when (file.extension.lowercase()) {
                    "pdf" -> android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        android.graphics.pdf.PdfRenderer(descriptor).use { pdf ->
                            val count=minOf(pdf.pageCount,10)
                            for (i in 0 until count) pdf.openPage(i).use { page ->
                                val width=minOf(720,resources.displayMetrics.widthPixels-dp(70)).coerceAtLeast(240)
                                val height=(width.toFloat()*page.height/page.width).toInt().coerceAtMost(2000)
                                val bitmap=android.graphics.Bitmap.createBitmap(width,height,android.graphics.Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                body.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true; contentDescription="Page ${i+1} du document" },LinearLayout.LayoutParams(-1,-2)); body.addView(gap(10))
                            }
                            if(pdf.pageCount>count) body.addView(label("Aperçu limité aux 10 premières pages.",12f,muted))
                        }
                    }
                    "png","jpg","jpeg","webp" -> {
                        val opts=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        android.graphics.BitmapFactory.decodeFile(file.path,opts); opts.inSampleSize=maxOf(1,maxOf(opts.outWidth,opts.outHeight)/1600); opts.inJustDecodeBounds=false
                        val bitmap=android.graphics.BitmapFactory.decodeFile(file.path,opts) ?: throw IllegalArgumentException("Image invalide")
                        body.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true },LinearLayout.LayoutParams(-1,-2))
                    }
                    "docx" -> body.addView(label(docxText(file),14f,strong).apply { setTextIsSelectable(true); setPadding(dp(12),dp(12),dp(12),dp(12)); background=shape(canvas,14) })
                    "txt","md","csv","json","xml","html","py","js","kt","java","log","yaml","yml" -> {
                        val preview=file.bufferedReader().use { val chars=CharArray(50_000); val n=it.read(chars); if(n>0) String(chars,0,n) else "Fichier vide" }
                        body.addView(label(preview,13f,strong).apply { setTextIsSelectable(true); typeface=Typeface.MONOSPACE; setPadding(dp(12),dp(12),dp(12),dp(12)); background=shape(canvas,14) })
                    }
                    else -> body.addView(label("Aperçu de ce format indisponible. Vous pouvez l’exporter.",14f,muted))
                }
            } catch(e:Exception) { body.addView(label("Aperçu impossible : ${e.message ?: "fichier invalide"}",13f,danger)) }
            body.addView(gap(14)); body.addView(action("Exporter",primary,white) {
                dialog.dismiss(); selectedFile=file.path
                val mime=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
                startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type=mime; putExtra(Intent.EXTRA_TITLE,file.name) },301)
            })
        }
    }
    @Deprecated("Deprecated in Java") override fun onActivityResult(request:Int, result:Int, data:Intent?) {
        super.onActivityResult(request,result,data)
        val uri=data?.data
        if(request==301 && result==RESULT_OK && uri!=null && selectedFile!=null) {
            try { File(selectedFile!!).inputStream().use { source -> contentResolver.openOutputStream(uri)?.use { source.copyTo(it) } }; toast("Fichier exporté") } catch(_:Exception) { toast("Export impossible") }
            selectedFile=null
        }
        if(request==302 && result==RESULT_OK && uri!=null) {
            val id=pickerSession ?: return
            try {
                var name="Pièce jointe"; contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) name=it.getString(0) ?: name }
                var mime=contentResolver.getType(uri) ?: ""
                if(mime=="application/octet-stream" && name.endsWith(".txt",true)) mime="text/plain"
                if(mime.isBlank()) mime="application/octet-stream"
                val dir=File(filesDir,"attachments").apply { mkdirs() }; val target=File(dir,UUID.randomUUID().toString())
                try { contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output ->
                    val buffer=ByteArray(8192); var total=0L
                    while(true) { val n=input.read(buffer); if(n<0) break; total+=n; if(total>8L*1024*1024) throw IllegalArgumentException("Fichier trop volumineux."); output.write(buffer,0,n) }
                } } ?: throw IllegalArgumentException("Fichier illisible") } catch(e:Exception) { target.delete(); throw e }
                attachments.getOrPut(id){mutableListOf()}.add(JSONObject().put("name",name).put("mime",mime).put("path",target.absolutePath))
                if(screen=="chat" && currentSession==id) showChat(id)
            } catch(e:Exception) { toast(e.message ?: "Impossible d’ajouter le fichier") }
        }
        pickerSession=null
    }
    private fun showSettings() {
        screen="settings"; currentSession=null; frame(); root.addView(topBar("Paramètres","Connexion et environnement")); root.addView(separator())
        val (_,body)=scrollArea(20); body.addView(gap(12)); body.addView(label("Paramètres",25f,strong,true)); body.addView(gap(8)); body.addView(label("Configurez votre assistant et votre espace de travail.",14f,muted)); body.addView(gap(22))
        body.addView(label("CONNEXION",11f,muted,true)); body.addView(gap(8))
        body.addView(settingCard("Moteur · NemApi","Connexion DeepSeek et paramètres intégrés à NEMESIS.") { startActivity(Intent(this, com.nemap.android.MainActivity::class.java)) }); body.addView(gap(10))
        val ssh=SshBridge.details(); val desc=if(ssh.optBoolean("configured")) "${ssh.optString("user")}@${ssh.optString("host")} · connexion à vérifier" else "Configurer un serveur SSH ou Termux."
        body.addView(settingCard("Environnement SSH",desc) { sshSettings() }); body.addView(gap(24)); body.addView(label("ESPACE DE TRAVAIL",11f,muted,true)); body.addView(gap(8))
        body.addView(settingCard("Fichiers locaux","Consulter les fichiers Work et Code par discussion.") { showFiles() }); body.addView(gap(18))
        val setting = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)); background=shape(white,16,line) }
        val labels = column().apply { addView(label("Détails des outils",15f,strong,true)); addView(gap(4)); addView(label("Déplier automatiquement les paramètres et résultats.",13f,muted)) }
        setting.addView(labels,LinearLayout.LayoutParams(0,-2,1f))
        val toggle = Switch(this).apply {
            isChecked=getSharedPreferences("display",MODE_PRIVATE).getBoolean("tool_details",false)
            contentDescription="Déplier automatiquement les détails des outils"
            setOnCheckedChangeListener { _, checked -> getSharedPreferences("display",MODE_PRIVATE).edit().putBoolean("tool_details",checked).apply() }
        }
        setting.addView(toggle); body.addView(setting); body.addView(gap(10))
        body.addView(gap(14)); body.addView(label("PERSONNALISATION",11f,muted,true)); body.addView(gap(8))
        body.addView(settingCard("Instructions personnalisées","Adapter le style et les préférences de NEMESIS.") { editPersonalization() }); body.addView(gap(10))
        body.addView(settingCard("Mémoire", "Choisir les informations à retenir entre les discussions.") { showMemory() }); body.addView(gap(20))
        body.addView(label("FONCTIONS À ADAPTER",11f,muted,true)); body.addView(gap(8))
        body.addView(settingCard("Skills", "Consulter les skills et leurs instructions.") { showSkills() }); body.addView(gap(8))
        body.addView(settingCard("Serveurs MCP", "Ajouter et tester des serveurs HTTP ou SSH.") { showMcpServers() })
        body.addView(gap(20)); body.addView(label("NEMESIS Android · version de développement",12f,muted))
    }
    private fun settingCard(title:String, description:String,click:()->Unit):View=column().apply {
        setPadding(dp(16),dp(16),dp(16),dp(16)); background=shape(white,16,line); isClickable=true; isFocusable=true
        addView(label(title,15f,strong,true)); addView(gap(5)); addView(label(description,13f,muted)); setOnClickListener { click() }
    }
    private fun editPersonalization() {
        val prefs=JSONObject(py.callAttr("get_preferences").toString())
        sheet("Instructions personnalisées", "Ces préférences seront transmises dans les nouvelles discussions. Ne renseignez pas de mots de passe.") { body, dialog ->
            val input=formField("Ex. Réponds en français et sois concis.",prefs.optString("custom_instructions"),multiline=true)
            body.addView(input); body.addView(gap(14))
            body.addView(action("Enregistrer",primary,white) {
                val result=JSONObject(py.callAttr("save_preferences",input.text.toString(),prefs.optBoolean("memory_enabled")).toString())
                if(result.optBoolean("ok")) { dialog.dismiss(); toast("Préférences enregistrées") } else toast(result.optString("error"))
            })
        }
    }
    private fun showMemory() {
        screen="memory"; currentSession=null; frame(); root.addView(topBar("Mémoire", "Préférences personnelles")); root.addView(separator())
        val (_,body)=scrollArea(20); body.addView(gap(12)); body.addView(label("Mémoire",25f,strong,true)); body.addView(gap(8))
        body.addView(label("NEMESIS peut retenir automatiquement vos préférences et informations utiles. Les souvenirs activés sont envoyés au moteur au début de chaque nouvelle discussion. Vous pouvez les modifier, les supprimer ou désactiver la mémoire ici.",14f,muted)); body.addView(gap(20))
        val prefs=JSONObject(py.callAttr("get_preferences").toString())
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(14),dp(10),dp(14),dp(10)); background=shape(white,14,line) }
        row.addView(label("Utiliser la mémoire",15f,strong,true),LinearLayout.LayoutParams(0,-2,1f))
        row.addView(Switch(this).apply { isChecked=prefs.optBoolean("memory_enabled"); contentDescription="Utiliser la mémoire"; setOnCheckedChangeListener { _, checked -> py.callAttr("save_preferences",prefs.optString("custom_instructions"),checked) } })
        body.addView(row); body.addView(gap(14))
        body.addView(action("＋  Ajouter un souvenir",pale,primary) { addMemorySheet() }); body.addView(gap(20))
        val memories=JSONArray(py.callAttr("list_memory").toString())
        if(memories.length()==0) body.addView(label("Aucun souvenir enregistré.",14f,muted))
        for(i in 0 until memories.length()) {
            val item=memories.getJSONObject(i); val id=item.getString("id")
            body.addView(settingCard(item.optString("content"),"Toucher pour modifier ou supprimer") {
                sheet("Modifier le souvenir", "Les souvenirs restent sous votre contrôle.") { content, dialog ->
                    val input=formField("Souvenir",item.optString("content"),multiline=true); content.addView(input); content.addView(gap(12))
                    content.addView(action("Enregistrer",primary,white) {
                        val result=JSONObject(py.callAttr("update_memory",id,input.text.toString()).toString())
                        if(result.optBoolean("ok")) { dialog.dismiss(); showMemory() } else toast(result.optString("error"))
                    }); content.addView(gap(8))
                    content.addView(action("Supprimer",Color.TRANSPARENT,danger) {
                        dialog.dismiss(); sheet("Supprimer ce souvenir ?",item.optString("content")) { confirmation, confirm ->
                            confirmation.addView(action("Supprimer",danger,white) { py.callAttr("delete_memory",id); confirm.dismiss(); showMemory() })
                        }
                    })
                }
            }); body.addView(gap(8))
        }
    }
    private fun addMemorySheet() {
        sheet("Ajouter un souvenir", "Une préférence utile, sans mot de passe ni donnée sensible.") { body, dialog ->
            val input=formField("Ex. Je préfère les réponses concises.",multiline=true); body.addView(input); body.addView(gap(14))
            body.addView(action("Ajouter",primary,white) {
                val result=JSONObject(py.callAttr("add_memory",input.text.toString()).toString())
                if(result.optBoolean("ok")) { dialog.dismiss(); showMemory() } else toast(result.optString("error"))
            })
        }
    }
    private fun sshSettings() {
        val old=SshBridge.details()
        sheet("Environnement SSH", "La clé du serveur est mémorisée à la première connexion. Un réseau compromis à cette étape peut usurper le serveur. Le mot de passe est chiffré avec Android Keystore.") { body, dialog ->
            val host=formField("Hôte SSH",old.optString("host","")); body.addView(host); body.addView(gap(8))
            val port=formField("Port",old.optInt("port",22).toString()); body.addView(port); body.addView(gap(8))
            val user=formField("Utilisateur",old.optString("user","")); body.addView(user); body.addView(gap(8))
            val password=formField("Mot de passe SSH",secret=true); body.addView(password); body.addView(gap(14))
            body.addView(action("Enregistrer",primary,white) {
                try { SshBridge.save(host.text.toString(),port.text.toString().toInt(),user.text.toString(),"",password.text.toString()); dialog.dismiss(); toast("SSH enregistré"); showSettings() } catch(e:Exception) { toast(e.message?:"Configuration invalide") }
            }); body.addView(gap(8))
            body.addView(action("Effacer la configuration",Color.TRANSPARENT,danger) { SshBridge.clear(); dialog.dismiss(); showSettings() })
        }
    }
    private fun toast(message:String) { Toast.makeText(this,message.take(300),Toast.LENGTH_LONG).show() }
}
