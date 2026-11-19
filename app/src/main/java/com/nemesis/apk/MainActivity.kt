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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.api.ApiException

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
    private var currentMode = "work"
    private var newConversationMode = "work"
    private var workspaceUploadSession: String? = null
    private var projectUploadId: String? = null
    private var currentProjectId: String? = null
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
    // Python and SQLite may block; never wait for them in the UI stream timer.
    private val streamReader = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val chatLoader = java.util.concurrent.Executors.newFixedThreadPool(2)
    private var chatLoadSerial = 0L
    private var chatLoadingId: String? = null
    private val historyVisible = mutableMapOf<String,Int>()
    private val py get() = Python.getInstance().getModule("mobile_agent")
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun isWideLayout() = resources.configuration.screenWidthDp >= 840
    private fun wideContentWidthPx() = dp((resources.configuration.screenWidthDp - 300 - 40).coerceIn(400,960))

    // A nullable holder avoids touching a View before the Activity is created.
    private class FrameLayoutPlaceholder { var value: FrameLayout? = null }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        PreviewBridge.init(this)
        NemesisService.setEnabled(applicationContext,true)
        com.nemap.android.EmbeddedRuntime.start(applicationContext)
        window.statusBarColor = white; window.navigationBarColor = white
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        markdown = Markwon.builder(this).usePlugin(TablePlugin.create(this)).build()
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        if (!busy) configureStorage()
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 501)
        }
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),707)
        }
        SshBridge.init(this); AndroidComputerBridge.init(this); McpSecretBridge.init(this); GoogleWorkspaceBridge.init(this)
        AutomationScheduler.init(this)
        val id = saved?.getString("session") ?: intent?.getStringExtra(AutomationExecutionService.EXTRA_SESSION)
        if (id != null) { drafts[id] = saved?.getString("draft", "") ?: ""; showChat(id) } else showHome()
        try { py.callAttr("automation_reschedule_all") } catch (_:Exception) { }
        // Start after heavy Activity initialization so Android can dispatch the service promptly.
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            RuntimeRecoveryJob.schedule(applicationContext)
            startForegroundService(Intent(this, NemesisService::class.java))
        }
    }
    override fun onNewIntent(intent:Intent) {
        super.onNewIntent(intent); setIntent(intent)
        intent.getStringExtra(AutomationExecutionService.EXTRA_SESSION)?.let { showChat(it) }
    }
    override fun onSaveInstanceState(out: Bundle) {
        currentSession?.let { out.putString("session", it); out.putString("draft", drafts[it] ?: "") }
        super.onSaveInstanceState(out)
    }
    private fun configureStorage() {
        val hasStorage = checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        val shared = if (hasStorage) sharedNemesisDirectory().absolutePath else ""
        val activeDns = (getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
            .activeNetwork?.let { network -> (getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
                .getLinkProperties(network)?.dnsServers.orEmpty().mapNotNull { it.hostAddress?.substringBefore('%') }
                .joinToString(",") }.orEmpty()
        py.callAttr("configure", filesDir.absolutePath, com.nemap.android.EmbeddedRuntime.port(), com.nemap.android.EmbeddedRuntime.token(), shared,
            applicationInfo.nativeLibraryDir, android.os.Build.SUPPORTED_ABIS.first(), activeDns)
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
    private fun addMcpServer(prefillName:String="",prefillUrl:String="") {
        sheet("Ajouter un serveur MCP", "Les outils MCP agissent sans confirmation. HTTP non chiffré expose les données ; ne mettez pas de secret dans une commande ou une URL.") { body, dialog ->
            val name=formField("Nom du serveur",prefillName)
            val transport=Spinner(this).apply {
                adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("HTTP Streamable","Stdio via SSH"))
                contentDescription="Transport MCP"
            }
            val target=formField("URL HTTP(S) ou commande stdio",prefillUrl,multiline=true)
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
                            val connected=result.optBoolean("ok")
                            getSharedPreferences("mcp_connection_status", MODE_PRIVATE).edit().putBoolean(id,connected).putLong("${id}_tested_at",System.currentTimeMillis()).apply()
                            val tools=result.optJSONArray("tools") ?: JSONArray()
                            val detail=if(connected) (0 until tools.length()).joinToString("\n") { tools.getJSONObject(it).optString("name") }.ifEmpty { "Connexion réussie, aucun outil." } else result.optString("error","Connexion impossible")
                            sheet(if(result.optBoolean("ok")) "Connexion réussie" else "Connexion impossible",detail) { _,_ -> }
                        }
                    }
                }.start()
            }); body.addView(gap(8))
            body.addView(action("Supprimer ce serveur",Color.TRANSPARENT,danger) { py.callAttr("mcp_delete",id); McpSecretBridge.delete(id); getSharedPreferences("mcp_connection_status", MODE_PRIVATE).edit().remove(id).remove("${id}_tested_at").apply(); dialog.dismiss(); showMcpServers() })
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
        if (!NemesisService.isEnabled(applicationContext)) {
            NemesisService.setEnabled(applicationContext,true)
            com.nemap.android.EmbeddedRuntime.start(applicationContext)
            if (!busy) configureStorage()
            startForegroundService(Intent(this, NemesisService::class.java))
        }
        webHost?.let { com.nemap.android.web.WebViewHost.bindContainer(it) }
        currentSession?.let { if (screen == "chat") showChat(it) }
    }
    override fun onStart() { super.onStart(); AppTaskNotifications.foreground=true }
    override fun onStop() { AppTaskNotifications.foreground=false; super.onStop() }
    override fun onDestroy() {
        PreviewBridge.clear(this)
        streamReader.shutdownNow()
        chatLoader.shutdownNow()
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
            // Keep the embedded provider behind the native interface on every size class.
            frame.addView(web, FrameLayout.LayoutParams(-1,-1,Gravity.TOP or Gravity.START))
            setContentView(frame)
            com.nemap.android.web.WebViewHost.bindContainer(web)
        }
        if (::root.isInitialized) h.removeView(root)
        while(h.childCount > 1) h.removeViewAt(h.childCount-1)
        val wide=isWideLayout()
        if(wide) h.addView(expandedSidebar(),FrameLayout.LayoutParams(dp(300),-1,Gravity.START))
        root = column().apply { setBackgroundColor(canvas) }
        val content=FrameLayout.LayoutParams(-1,-1)
        if(wide) content.marginStart=dp(300)
        h.addView(root,content)
    }
    private fun topBar(title: String, subtitle: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(if(isWideLayout())dp(22) else dp(12),dp(8),dp(12),dp(8)); setBackgroundColor(white) }
        if(!isWideLayout()) row.addView(action("☰", Color.TRANSPARENT, strong) { openDrawer() }.apply { textSize = 21f; contentDescription = "Ouvrir le menu" }, LinearLayout.LayoutParams(dp(48),dp(48)))
        else row.addView(View(this),LinearLayout.LayoutParams(dp(8),dp(48)))
        val titles = column().apply { setPadding(dp(7),0,0,0); addView(label(title,16f,strong,true).apply { maxLines = 1 }); addView(label(subtitle,11f,muted)) }
        row.addView(titles, LinearLayout.LayoutParams(0,-2,1f))
        val sessionId = if (screen == "chat") currentSession else null
        if (sessionId != null && currentMode != "chat" && chatLoadingId != sessionId) {
            row.addView(action("📁", Color.TRANSPARENT, primary) { showSessionFiles(sessionId) }.apply { textSize=22f; contentDescription="Fichiers du workspace de cette discussion" }, LinearLayout.LayoutParams(dp(48),dp(48)))
        } else {
            row.addView(action("＋", Color.TRANSPARENT, primary) { showHome() }.apply { textSize = 24f; contentDescription = "Nouvelle discussion" }, LinearLayout.LayoutParams(dp(48),dp(48)))
        }
        return row
    }
    private fun bottomNav(selected:String):View {
        if(isWideLayout()) return View(this).apply { visibility=View.GONE }
        val outer=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER; setBackgroundColor(white); setPadding(dp(8),dp(5),dp(8),dp(6)) }
        val items=listOf(Triple("◉","Agent","agent"),Triple("▦","Projets","projects"),Triple("⌘","Code","code"),Triple("▤","Fichiers","files"))
        for((icon,title,key) in items) {
            val active=selected==key
            val cell=column().apply { gravity=Gravity.CENTER; setPadding(dp(3),dp(7),dp(3),dp(5)); isClickable=true; isFocusable=true; background=if(active) shape(pale,16) else null; contentDescription=title }
            cell.addView(label(icon,19f,if(active) primary else muted,true).apply { gravity=Gravity.CENTER })
            cell.addView(label(title,10.5f,if(active) primary else muted,active).apply { gravity=Gravity.CENTER })
            cell.setOnClickListener { when(key) { "agent"->showHome("work"); "projects"->showProjects(); "code"->showHome("code"); else->showFiles() } }
            outer.addView(cell,LinearLayout.LayoutParams(0,-2,1f))
        }
        return outer
    }
private fun sidebarSection(title:String):View = label(title,9.5f,muted,true).apply {
        setPadding(dp(10),dp(9),0,dp(2)); letterSpacing=.06f
    }
    /** One clean, full-width navigation row: icon chip + label, active pill. */
    private fun sidebarNavItem(icon:String, title:String, selected:Boolean, compact:Boolean=false, click:()->Unit):View {
        val height=if(compact) 36 else 42
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            background=if(selected) shape(pale,11) else null; isClickable=true; isFocusable=true
            contentDescription=title; setPadding(dp(4),dp(2),dp(8),dp(2))
        }
        val chipSize=if(compact) 26 else 29
        row.addView(label(icon,if(compact) 14f else 15.5f,if(selected) primary else muted,true).apply {
            gravity=Gravity.CENTER; background=shape(if(selected) white else canvas,8)
        },LinearLayout.LayoutParams(dp(chipSize),dp(chipSize)))
        row.addView(label(title,if(compact) 12.5f else 13.5f,if(selected) primary else strong,selected).apply {
            maxLines=1; ellipsize=android.text.TextUtils.TruncateAt.END; setPadding(dp(10),0,0,0)
        },LinearLayout.LayoutParams(0,-2,1f))
        if(selected) row.addView(label("●",9f,primary,true).apply { setPadding(0,0,dp(4),0) })
        row.setOnClickListener { click() }
        return row.apply { layoutParams=LinearLayout.LayoutParams(-1,dp(height)) }
    }
    private fun expandedSidebar():View {
        val panel=column().apply { setBackgroundColor(sidebar); setPadding(dp(12),dp(10),dp(10),dp(8)) }
        val brand=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        brand.addView(ImageView(this).apply { setImageResource(R.drawable.nemesis_logo); scaleType=ImageView.ScaleType.CENTER_CROP; background=shape(pale,10); clipToOutline=true },LinearLayout.LayoutParams(dp(34),dp(34)))
        brand.addView(column().apply { setPadding(dp(8),0,0,0); addView(label("NEMESIS",13f,strong,true)); addView(label("Votre espace IA",9.5f,muted)) },LinearLayout.LayoutParams(0,-2,1f))
        panel.addView(brand); panel.addView(gap(7))
        val search=EditText(this).apply { hint="Rechercher une discussion"; textSize=12f; setSingleLine(true); setTextColor(strong); setHintTextColor(muted); background=shape(white,11,line); setPadding(dp(10),0,dp(10),0); minHeight=dp(38); contentDescription="Rechercher une discussion" }
        panel.addView(search); panel.addView(gap(6))
        panel.addView(action("+  Nouvelle discussion",primary,white){create("work")}.apply{gravity=Gravity.CENTER;minHeight=dp(38);textSize=12f}); panel.addView(gap(4))
        panel.addView(sidebarSection("NAVIGATION"))
        addSidebarNavigation(panel) {}
        panel.addView(sidebarSection("DISCUSSIONS"))
        val list=column(); val listScroll=ScrollView(this).apply { isFillViewport=true; addView(list) }
        panel.addView(listScroll,LinearLayout.LayoutParams(-1,0,1f))
        fun refresh(query:String) {
            list.removeAllViews(); val sessions=JSONArray(try{py.callAttr("list_sessions").toString()}catch(_:Exception){"[]"}); var shown=0
            for(i in 0 until sessions.length()) {
                val item=sessions.optJSONObject(i)?:continue
                if(!item.isNull("project_id")) continue
                val itemMode=item.optString("mode"); if(itemMode!="work" && itemMode!="code") continue
                val title=item.optString("title"); if(!title.contains(query,true))continue; shown++
                val id=item.optString("id")
                val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; background=shape(if(id==currentSession)pale else Color.TRANSPARENT,9) }
                val text=column().apply {
                    setPadding(dp(7),dp(4),dp(2),dp(4))
                    addView(label(title,11.5f,strong,id==currentSession).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END})
                    val tag=buildList{ add(modeName(itemMode)); if(item.optInt("pinned")==1) add("Épinglée"); if(item.optInt("uncertain")==1) add("À vérifier") }.joinToString(" · ")
                    addView(label(tag,9.5f,muted))
                    isClickable=true;isFocusable=true;setOnClickListener{showChat(id)}
                    setOnLongClickListener{sessionOptions(item);true}
                }
                row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
                row.addView(action("⋯",Color.TRANSPARENT,muted){sessionOptions(item)}.apply{textSize=18f;contentDescription="Options de la discussion $title"},LinearLayout.LayoutParams(dp(36),dp(38)))
                list.addView(row)
            }
            if(shown==0)list.addView(label("Aucune discussion",10.5f,muted).apply{setPadding(dp(7),dp(8),0,0)})
        }
        refresh(""); search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh(s?.toString()?:"")};override fun afterTextChanged(s:Editable?){}})
        panel.addView(separator())
        addSidebarSecondaryNavigation(panel) {}
        return panel
    }
    /** Primary spaces, one clear row each, in a stable order. */
    private fun addSidebarNavigation(panel:LinearLayout, dismiss:()->Unit) {
        fun item(icon:String,title:String,selected:Boolean,click:()->Unit) {
            panel.addView(sidebarNavItem(icon,title,selected,false){dismiss();click()})
            panel.addView(gap(1))
        }
        item("◉","Agent",(screen=="home"&&newConversationMode!="code")||(screen=="chat"&&currentMode=="work"&&currentProjectId==null)){showHome("work")}
        item("⌘","Code",(screen=="home"&&newConversationMode=="code")||(screen=="chat"&&currentMode=="code"&&currentProjectId==null)){showHome("code")}
        item("▦","Projets",screen=="projects"||screen=="project"||(screen=="chat"&&currentProjectId!=null)){showProjects()}
    }
    /** Secondary tools, compact but still one per row; no cramped tab cells. */
    private fun addSidebarSecondaryNavigation(panel:LinearLayout, dismiss:()->Unit) {
        panel.addView(sidebarSection("OUTILS"))
        fun item(icon:String,title:String,selected:Boolean,click:()->Unit) {
            panel.addView(sidebarNavItem(icon,title,selected,true){dismiss();click()})
        }
        item("◷","Automatisations",screen=="automations"){showAutomations()}
        item("⇄","Connecteurs",screen=="connectors"){showConnectors()}
        item("▤","Fichiers",screen=="files"){showFiles()}
        item("⚙","Paramètres",screen=="settings"){showSettings()}
    }
    private fun separator() = View(this).apply { setBackgroundColor(line); layoutParams = LinearLayout.LayoutParams(-1,dp(1)) }
    private fun scrollArea(pad: Int = 18): Pair<ScrollView,LinearLayout> {
        val body = column().apply { setPadding(dp(pad),dp(14),dp(pad),dp(16)) }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false; addView(body) }
        if(isWideLayout()) {
            val stage=FrameLayout(this)
            stage.addView(scroll,FrameLayout.LayoutParams(wideContentWidthPx(),-1,Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            root.addView(stage,LinearLayout.LayoutParams(-1,0,1f))
        } else root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        return scroll to body
    }
    private fun modeName(mode: String) = if(mode=="code") "Code" else "Agent"
    private fun modeDescription(mode: String) = when(mode) {
        "work" -> "Fichiers et commandes essentielles"
        "code" -> "Développement et environnement SSH"
        else -> "Une conversation, tout simplement"
    }
    private fun showHome(initialMode:String="work") {
        screen = "home"; currentSession = null; newConversationMode = if(initialMode=="code") "code" else "work"
        val code=newConversationMode=="code"
        frame(); root.addView(topBar(if(code)"NEMESIS CODE" else "NEMESIS", if(code)"Développement et projets" else "Votre assistant personnel")); root.addView(separator())
        val (_, body) = scrollArea(24)
        body.gravity = Gravity.CENTER_HORIZONTAL
        body.addView(gap(26))
        body.addView(ImageView(this).apply { setImageResource(R.drawable.nemesis_logo); scaleType = ImageView.ScaleType.CENTER_CROP; background = shape(pale,20); clipToOutline = true; contentDescription = "Logo NEMESIS" }, LinearLayout.LayoutParams(dp(62),dp(62)))
        body.addView(gap(20))
        body.addView(label(if(code)"NEMESIS Code" else "Bonjour,", 27f,strong,true).apply { gravity=Gravity.CENTER })
        body.addView(label(if(code)"Votre espace de développement" else "que souhaitez-vous accomplir ?", 20f,strong,true).apply { gravity=Gravity.CENTER })
        body.addView(gap(10))
        body.addView(label(if(code)"Explorez vos dépôts, utilisez le terminal et poursuivez vos projets." else "Un agent polyvalent pour vos recherches, fichiers et applications.", 14f,muted).apply { gravity=Gravity.CENTER })
        body.addView(gap(22))
        body.addView(action(if(code)"＋  Nouvelle discussion Code" else "＋  Nouvelle discussion",primary,white){create(if(code)"code" else "work")})
        body.addView(gap(20))
        if(code) {
            body.addView(label("DISCUSSIONS CODE",11f,muted,true).apply{gravity=Gravity.START});body.addView(gap(6))
            val sessions=JSONArray(try{py.callAttr("list_sessions").toString()}catch(_:Exception){"[]"});var shown=0
            for(i in 0 until sessions.length()) {
                val item=sessions.optJSONObject(i)?:continue
                if(item.optString("mode")!="code" || !item.isNull("project_id")) continue
                shown++
                val id=item.optString("id");val title=item.optString("title")
                val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=shape(white,12,line)}
                row.addView(action(title,Color.TRANSPARENT,strong){showChat(id)}.apply{gravity=Gravity.CENTER_VERTICAL;maxLines=1;setOnLongClickListener{sessionOptions(item);true}},LinearLayout.LayoutParams(0,dp(50),1f))
                row.addView(action("⋯",Color.TRANSPARENT,muted){sessionOptions(item)}.apply{contentDescription="Options de la discussion $title"},LinearLayout.LayoutParams(dp(44),dp(44)))
                body.addView(row);body.addView(gap(5))
            }
            if(shown==0)body.addView(label("Vos discussions Code apparaîtront ici.",13f,muted).apply{gravity=Gravity.START})
            body.addView(gap(12));body.addView(action("▦  Ouvrir les projets",pale,primary){showProjects()})
        } else {
            body.addView(label("Vos discussions précédentes restent accessibles dans le menu latéral.",12f,muted).apply { gravity=Gravity.CENTER })
        }
        root.addView(bottomNav(if(code) "code" else "agent"))
    }
    private fun create(mode: String, projectId:String?=null) {
        if (busy) { toast("Attendez la fin de la réponse en cours."); return }
        val r = try { JSONObject((if(projectId==null) py.callAttr("create_session",mode) else py.callAttr("create_session",mode,projectId)).toString()) }
            catch(e:Exception) { toast("Création de la discussion impossible : ${e.message ?: "erreur interne"}"); return }
        if (!r.optBoolean("ok")) { toast(r.optString("error","Création impossible")); return }
        if (projectId!=null && r.optString("project_id")!=projectId) { toast("La discussion n’a pas été rattachée au projet; aucune conversation n’a été ouverte."); return }
        showChat(r.getString("id"))
    }
    private fun refreshCurrentScreen() {
        when(screen) {
            "chat" -> currentSession?.let { showChat(it) } ?: showHome(newConversationMode)
            "projects" -> showProjects()
            "project" -> currentProjectId?.let { showProject(it) } ?: showProjects()
            "automations" -> showAutomations()
            "connectors" -> showConnectors()
            "settings" -> showSettings()
            "files" -> showFiles()
            else -> showHome(newConversationMode)
        }
    }
    private fun closeDrawer() { drawer?.let { host.value?.removeView(it) }; drawer = null }
    private fun openDrawer() {
        if (isWideLayout() || drawer != null) return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(root.windowToken,0)
        val layer=FrameLayout(this)
        layer.addView(View(this).apply{setBackgroundColor(0x660C2133);setOnClickListener{closeDrawer()}},FrameLayout.LayoutParams(-1,-1))
        val width=minOf(dp(322),resources.displayMetrics.widthPixels-dp(42))
        val menu=column().apply{setBackgroundColor(sidebar);setPadding(dp(12),dp(10),dp(10),dp(8));elevation=dp(10).toFloat()}
        val brand=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL;orientation=LinearLayout.HORIZONTAL}
        brand.addView(ImageView(this).apply{setImageResource(R.drawable.nemesis_logo);scaleType=ImageView.ScaleType.CENTER_CROP;background=shape(pale,10);clipToOutline=true},LinearLayout.LayoutParams(dp(36),dp(36)))
        brand.addView(column().apply{setPadding(dp(8),0,0,0);addView(label("NEMESIS",13f,strong,true));addView(label("Votre espace IA",9.5f,muted))},LinearLayout.LayoutParams(0,-2,1f))
        brand.addView(action("×",Color.TRANSPARENT,muted){closeDrawer()}.apply{textSize=20f;contentDescription="Fermer le menu"},LinearLayout.LayoutParams(dp(38),dp(38)))
        menu.addView(brand);menu.addView(gap(7))
        val search=EditText(this).apply{hint="Rechercher une discussion";textSize=12f;setSingleLine(true);setTextColor(strong);setHintTextColor(muted);background=shape(white,11,line);setPadding(dp(10),0,dp(10),0);minHeight=dp(38);contentDescription="Rechercher une discussion"}
menu.addView(search);menu.addView(gap(6))
        menu.addView(action("+  Nouvelle discussion",primary,white){closeDrawer();create("work")}.apply{gravity=Gravity.CENTER;minHeight=dp(38);textSize=12f});menu.addView(gap(4))
        menu.addView(sidebarSection("NAVIGATION"))
        addSidebarNavigation(menu) { closeDrawer() }
        menu.addView(sidebarSection("DISCUSSIONS"))
        val list=column();val scroll=ScrollView(this).apply{addView(list);isFillViewport=true}
        menu.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        fun refresh(query:String){
            list.removeAllViews();val sessions=JSONArray(try{py.callAttr("list_sessions").toString()}catch(_:Exception){"[]"});var shown=0
            for(i in 0 until sessions.length()){
                val item=sessions.optJSONObject(i)?:continue
                if(!item.isNull("project_id"))continue
                val itemMode=item.optString("mode");if(itemMode!="work" && itemMode!="code")continue
                val title=item.optString("title");if(!title.contains(query,true))continue;shown++
                val id=item.optString("id");val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=shape(if(id==currentSession)pale else Color.TRANSPARENT,9)}
                val text=column().apply{setPadding(dp(8),dp(5),dp(2),dp(5));addView(label(title,12f,strong,id==currentSession).apply{maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END});val tag=buildList{add(modeName(itemMode));if(item.optInt("pinned")==1)add("Épinglée");if(item.optInt("uncertain")==1)add("À vérifier")}.joinToString(" · ");addView(label(tag,9.5f,muted));isClickable=true;isFocusable=true;setOnClickListener{closeDrawer();showChat(id)};setOnLongClickListener{closeDrawer();sessionOptions(item);true}}
                row.addView(text,LinearLayout.LayoutParams(0,-2,1f));row.addView(action("⋯",Color.TRANSPARENT,muted){closeDrawer();sessionOptions(item)}.apply{textSize=19f;contentDescription="Options de la discussion $title"},LinearLayout.LayoutParams(dp(40),dp(40)))
                list.addView(row)
            }
            if(shown==0)list.addView(label("Aucune discussion trouvée.",11f,muted).apply{setPadding(dp(8),dp(10),0,0)})
        }
        refresh("");search.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){refresh(s?.toString()?:"")};override fun afterTextChanged(s:Editable?){}})
        menu.addView(separator())
        addSidebarSecondaryNavigation(menu) { closeDrawer() }
        layer.addView(menu,FrameLayout.LayoutParams(width,-1,Gravity.START));host.value?.addView(layer,FrameLayout.LayoutParams(-1,-1));drawer=layer
    }
    private fun showProjects() {
        currentProjectId=null;screen="projects";currentSession=null;frame();root.addView(topBar("Projets","Conversations, fichiers et contexte partagés"));root.addView(separator())
        val (_,body)=scrollArea(18);body.addView(gap(8))
        body.addView(label("Un projet garde ses instructions, ses fichiers et sa mémoire partagés entre ses discussions.",14f,muted));body.addView(gap(14))
        body.addView(action("＋  Nouveau projet",primary,white){createProject()});body.addView(gap(14))
        val projects=try{JSONArray(py.callAttr("project_list").toString())}catch(e:Exception){JSONArray()}
        if(projects.length()==0) body.addView(label("Vos projets apparaîtront ici.",14f,muted).apply{setPadding(dp(8),dp(18),0,0)})
        for(i in 0 until projects.length()) {
            val item=projects.getJSONObject(i);val id=item.optString("id");val name=item.optString("name")
            val card=column().apply{setPadding(dp(16),dp(14),dp(16),dp(14));background=shape(white,18,line);isClickable=true;isFocusable=true}
            val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
            head.addView(label(name,17f,strong,true),LinearLayout.LayoutParams(0,-2,1f));head.addView(action("⋯",Color.TRANSPARENT,muted){projectOptions(id,name)}.apply{contentDescription="Options du projet $name"},LinearLayout.LayoutParams(dp(44),dp(42)))
            card.addView(head);card.addView(gap(5));card.addView(label("${item.optInt("conversations")} discussion(s) · instructions et mémoire partagées",12f,muted))
            if(item.optString("instructions").isNotBlank())card.addView(label(item.optString("instructions").take(120),12f,muted).apply{setPadding(0,dp(5),0,0)})
            card.setOnClickListener{showProject(id)};body.addView(card);body.addView(gap(9))
        }
        root.addView(bottomNav("projects"))
    }

    private fun createProject() {
        val name=formField("Nom du projet");val instructions=formField("Instructions propres à ce projet",multiline=true)
        sheet("Créer un projet","Un espace de développement avec des discussions, des fichiers et un contexte partagés."){body,dialog->
            body.addView(label("Nom",13f,muted,true));body.addView(gap(4));body.addView(name);body.addView(gap(10))
            body.addView(label("Instructions du projet",13f,muted,true));body.addView(gap(4));body.addView(instructions);body.addView(gap(12))
            body.addView(action("Créer le projet",primary,white){
                val result=JSONObject(py.callAttr("project_create",name.text.toString(),"code",instructions.text.toString()).toString())
                if(result.optBoolean("ok")){dialog.dismiss();showProject(result.getString("id"))}else toast(result.optString("error","Création impossible"))
            })
        }
    }

    private fun showProject(id:String) {
        val data=JSONObject(py.callAttr("project_get",id).toString());val item=data.optJSONObject("project")?:run{toast("Projet introuvable");showProjects();return}
        val name=item.optString("name")
        currentProjectId=id;screen="project";currentSession=null;frame();root.addView(topBar(name,"Espace de développement partagé"));root.addView(separator())
        val (_,body)=scrollArea(18);body.addView(gap(8))
        body.addView(label("Instructions du projet",14f,strong,true));body.addView(gap(5))
        val instructions=item.optString("instructions")
        body.addView(label(if(instructions.isBlank())"Aucune instruction ajoutée." else instructions,13f,muted));body.addView(gap(10))
        val projectActions=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        projectActions.addView(action("Modifier",pale,primary){editProject(id,name,instructions)},LinearLayout.LayoutParams(0,dp(42),1f))
        projectActions.addView(View(this),LinearLayout.LayoutParams(dp(8),1))
        projectActions.addView(action("Supprimer",Color.TRANSPARENT,danger){confirmDeleteProject(id,name)},LinearLayout.LayoutParams(0,dp(42),1f))
        body.addView(projectActions);body.addView(gap(6))
        body.addView(action("＋  Ajouter des fichiers au projet",pale,primary){chooseProjectUpload(id)});body.addView(gap(14))
        val rootPath=File(py.callAttr("project_root",id).toString());if(!rootPath.exists())rootPath.mkdirs()
        body.addView(label("FICHIERS",11f,muted,true));body.addView(gap(5))
        val files=rootPath.walkTopDown().maxDepth(8).filter{it.isFile && !it.isHidden}.take(100).toList()
        if(files.isEmpty())body.addView(label("Aucun fichier pour le moment.",12f,muted))
        for(file in files){val rel=file.relativeTo(rootPath).path;body.addView(sheetRow(rel,"${file.length()} octets") { toast("Fichier du projet : $rel") });body.addView(gap(5))}
        body.addView(gap(14));body.addView(label("DISCUSSIONS",11f,muted,true));body.addView(gap(5))
        body.addView(action("＋  Nouvelle discussion",primary,white){create("code",id)});body.addView(gap(8))
        val sessions=JSONArray(py.callAttr("list_sessions").toString());var count=0
        for(i in 0 until sessions.length()) {
            val session=sessions.getJSONObject(i);if(session.optString("project_id")!=id)continue;count++
            val sid=session.optString("id");val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;background=shape(white,12,line)}
            row.addView(action(session.optString("title"),Color.TRANSPARENT,strong){showChat(sid)}.apply{gravity=Gravity.CENTER_VERTICAL;maxLines=1},LinearLayout.LayoutParams(0,dp(48),1f))
            row.addView(action("⋯",Color.TRANSPARENT,muted){sessionOptions(session)}.apply{contentDescription="Options de la discussion ${session.optString("title")}"},LinearLayout.LayoutParams(dp(44),dp(44)))
            body.addView(row);body.addView(gap(5))
        }
        if(count==0)body.addView(label("La première conversation apparaîtra ici.",12f,muted))
        root.addView(bottomNav("projects"))
    }

    private fun projectOptions(id:String,name:String) {
        sheet("Projet",name){body,dialog->
            body.addView(sheetRow("Ouvrir","Discussions, fichiers et instructions") { dialog.dismiss();showProject(id) });body.addView(gap(7))
            body.addView(sheetRow("Modifier","Renommer ou changer les instructions") {
                dialog.dismiss();val project=JSONObject(py.callAttr("project_get",id).toString()).optJSONObject("project")
                if(project!=null)editProject(id,project.optString("name"),project.optString("instructions")) else showProjects()
            });body.addView(gap(7))
            body.addView(sheetRow("Supprimer","Supprimer le projet et son contenu local") { dialog.dismiss();confirmDeleteProject(id,name) })
        }
    }
    private fun confirmDeleteProject(id:String,name:String) {
        if(busy){toast("Attendez la fin de la tâche du projet avant de le supprimer.");return}
        val data=try{JSONObject(py.callAttr("project_get",id).toString())}catch(_:Exception){JSONObject()}
        if(!data.optBoolean("ok")){toast("Projet introuvable");showProjects();return}
        sheet("Supprimer le projet ?","« $name » et ses discussions locales, tâches planifiées, mémoires et fichiers partagés seront supprimés. Cette action est irréversible. Les autres projets et discussions ne seront pas touchés."){body,dialog->
            body.addView(action("Supprimer le projet et son contenu",danger,white){
                Thread{
                    val result=try{JSONObject(py.callAttr("project_delete",id).toString())}catch(e:Exception){JSONObject().put("ok",false).put("error",e.message?:"Erreur interne")}
                    runOnUiThread{if(!isFinishing&&!isDestroyed){if(result.optBoolean("ok")){dialog.dismiss();currentProjectId=null;toast("Projet et contenu supprimés");showProjects()}else toast(result.optString("error","Suppression impossible"))}}
                }.start()
            });body.addView(gap(7));body.addView(action("Annuler",Color.TRANSPARENT,muted){dialog.dismiss()})
        }
    }

    private fun editProject(id:String,name:String,instructions:String) {
        val n=formField("Nom du projet",name);val bodyText=formField("Instructions",instructions,multiline=true)
        sheet("Modifier le projet"){body,dialog->body.addView(n);body.addView(gap(8));body.addView(bodyText);body.addView(gap(10));body.addView(action("Enregistrer",primary,white){
            val result=JSONObject(py.callAttr("project_update",id,n.text.toString(),bodyText.text.toString()).toString())
            if(result.optBoolean("ok")){dialog.dismiss();showProject(id)}else toast(result.optString("error","Modification impossible"))
        })}
    }

    private fun chooseProjectUpload(id:String) {
        projectUploadId=id
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="*/*"}
        try{startActivityForResult(intent,305)}catch(_:Exception){projectUploadId=null;toast("Sélecteur de fichiers indisponible.")}
    }

    private fun showAutomations() {
        val previousSession=currentSession
        screen="automations";currentSession=null;frame();root.addView(topBar("Automatisations","Tâches dans leurs discussions d’origine"));root.addView(separator())
        val (_,body)=scrollArea(18)
        body.addView(gap(6))
        body.addView(label("Les tâches différées s’exécutent dans le même fil, sans créer une nouvelle conversation. Demandez à NEMESIS de programmer une tâche depuis ce fil.",14f,muted))
        body.addView(gap(12))
        if(previousSession!=null) body.addView(action("‹  Revenir à la discussion ouverte",pale,primary){showChat(previousSession)}.apply{gravity=Gravity.START})
        body.addView(action("↻  Actualiser",Color.TRANSPARENT,primary){showAutomations()}.apply{gravity=Gravity.START})
        body.addView(gap(10))
        val data=try{JSONObject(py.callAttr("automation_list_all").toString())}catch(e:Exception){JSONObject().put("success",false).put("error",e.message?:"Indisponible")}
        val items=data.optJSONArray("automations")?:JSONArray()
        if(!data.optBoolean("success")) body.addView(label(data.optString("error","Automatisations indisponibles"),13f,danger))
        else if(items.length()==0) body.addView(label("Aucune automatisation pour le moment.",14f,muted).apply{setPadding(dp(10),dp(15),0,dp(12))})
        for(i in 0 until items.length()) {
            val item=items.optJSONObject(i)?:continue
            val id=item.optString("id");val sid=item.optString("session_id");val title=item.optString("title","Automatisation")
            val status=item.optString("status");val repeating=when(item.optString("repeat")){"daily"->" · chaque jour";"weekly"->" · chaque semaine";else->""}
            val whenText=try{java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM,java.text.DateFormat.SHORT,java.util.Locale.getDefault()).format(java.util.Date((item.optDouble("run_at")*1000).toLong()))}catch(_:Exception){"Heure non disponible"}
            val state=when(status){"scheduled"->"À venir$repeating";"running"->"En cours";"completed"->"Terminée";"failed"->"Échec";"cancelled"->"Annulée";"paused"->"Désactivée";else->status}
            val card=column().apply{setPadding(dp(15),dp(13),dp(15),dp(13));background=shape(white,16,line)}
            card.addView(label(title,15f,strong,true));card.addView(gap(4))
            card.addView(label("$state · $whenText",12f,if(status=="failed")danger else primary));card.addView(gap(3))
            card.addView(label("${item.optString("mode").replaceFirstChar{if(it.isLowerCase())it.titlecase() else it.toString()}} · ${item.optString("conversation","Discussion")}",11.5f,muted))
            if(item.optString("last_error").isNotBlank())card.addView(label(item.optString("last_error").take(160),11f,danger))
            val buttons=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};buttons.addView(action("Ouvrir le fil",pale,primary){if(sid.isNotBlank())showChat(sid)}.apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(42),1f))
            if(status=="scheduled" || status=="paused") {
                buttons.addView(View(this),LinearLayout.LayoutParams(dp(6),1))
                buttons.addView(action(if(status=="scheduled")"Désactiver" else "Réactiver",Color.TRANSPARENT,if(status=="scheduled")danger else primary){
                    val method=if(status=="scheduled")"automation_pause" else "automation_resume"
                    val result=try{JSONObject(py.callAttr(method,"",id,true).toString())}catch(e:Exception){JSONObject().put("success",false).put("error",e.message)}
                    if(result.optBoolean("success"))showAutomations() else toast(result.optString("error","Modification impossible"))
                }.apply{gravity=Gravity.CENTER},LinearLayout.LayoutParams(0,dp(42),1f))
            }
            card.addView(gap(6));card.addView(buttons)
            if(status!="running")card.addView(action("Supprimer cette automatisation",Color.TRANSPARENT,danger){
                sheet("Supprimer la tâche ?","La tâche sera retirée et son alarme annulée. La discussion d’origine reste intacte."){confirm,_ ->
                    confirm.addView(action("Supprimer",danger,white){
                        val result=try{JSONObject(py.callAttr("automation_delete","",id,true).toString())}catch(e:Exception){JSONObject().put("success",false).put("error",e.message)}
                        if(result.optBoolean("success"))showAutomations() else toast(result.optString("error","Suppression impossible"))
                    })
                }
            }.apply{gravity=Gravity.START})
            body.addView(card);body.addView(gap(9))
        }
    }

    private fun sessionOptions(session:JSONObject) {
        val id=session.getString("id"); val title=session.optString("title"); val pinned=session.optInt("pinned")==1
        sheet("Discussion", title) { body, dialog ->
            body.addView(sheetRow("Renommer", "Modifier le nom affiché") { dialog.dismiss(); renameSession(id,title) }); body.addView(gap(8))
            body.addView(sheetRow(if(pinned) "Désépingler" else "Épingler", "Retrouver rapidement cette discussion") {
                val result=try{JSONObject(py.callAttr("pin_session",id,!pinned).toString())}catch(e:Exception){JSONObject().put("ok",false).put("error",e.message)}
                if(result.optBoolean("ok")){dialog.dismiss();refreshCurrentScreen()}else toast(result.optString("error","Épinglage impossible"))
            }); body.addView(gap(8))
            body.addView(sheetRow("Supprimer", "Supprimer uniquement la copie locale") { dialog.dismiss(); confirmDelete(id) })
        }
    }
    private fun renameSession(id:String, old:String) {
        if(busy) { toast("Attendez la fin de la tâche avant de renommer."); return }
        sheet("Renommer la discussion") { body, dialog ->
            val input=formField("Nom de la discussion",old); body.addView(input); body.addView(gap(15))
            body.addView(action("Enregistrer",primary,white) {
                if(busy) { toast("Attendez la fin de la tâche avant de renommer."); return@action }
                val result=JSONObject(py.callAttr("rename_session",id,input.text.toString()).toString())
                if(result.optBoolean("ok")) { dialog.dismiss(); refreshCurrentScreen() } else toast(result.optString("error"))
            })
        }
    }
    private fun confirmDelete(id:String) {
        if(busy) { toast("Attendez la fin de la réponse."); return }
        sheet("Supprimer la discussion ?", "La copie locale sera supprimée. La conversation DeepSeek ne sera pas supprimée.") { body, dialog ->
            body.addView(action("Supprimer",danger,white) {
                val result=try{JSONObject(py.callAttr("delete_session",id).toString())}catch(e:Exception){JSONObject().put("ok",false).put("error",e.message?:"Suppression impossible")}
                if(result.optBoolean("ok")){dialog.dismiss();drafts.remove(id);if(currentSession==id)showHome()else refreshCurrentScreen()}
                else toast(result.optString("error","Suppression impossible"))
            }); body.addView(gap(8))
            body.addView(action("Annuler",Color.TRANSPARENT,muted) { dialog.dismiss() })
        }
    }
    private fun bubble(role:String, text:String, sessionId:String?=null, messageId:Long=0L, durationMs:Long?=null, initialRating:Int=0, attachmentData:JSONArray?=null): View {
        val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=if(role=="user") Gravity.END else Gravity.START }
        val card=column().apply {
            setPadding(dp(16),dp(13),dp(16),dp(13)); background=shape(if(role=="user") pale else white,20,if(role=="error") Color.rgb(232,187,185) else line)
            addView(label(if(role=="user") "VOUS" else if(role=="error") "ATTENTION" else "NEMESIS",10f,if(role=="error") danger else primary,true)); addView(gap(5))
            val msg=label("",15f,if(role=="error") danger else strong).apply { setTextIsSelectable(true); setLineSpacing(dp(3).toFloat(),1f) }
            if (text.length <= 8_000) {
                if(role=="user" || role=="error") msg.text=text else markdown.setMarkdown(msg,text)
                addView(msg)
            } else {
                // Keep the full original message for copy/storage. Rendering a single
                // huge TextView can block weaker 32-bit devices for many seconds.
                // Pages are plain text to avoid breaking Markdown fences mid-block.
                val cuts=mutableListOf(0); var at=0
                while(at<text.length) {
                    var end=minOf(at+8_000,text.length)
                    if(end<text.length && Character.isHighSurrogate(text[end-1]) && Character.isLowSurrogate(text[end])) end--
                    at=end; cuts.add(at)
                }
                var page=0; val info=label("",11f,muted)
                val navigation=LinearLayout(this@MainActivity).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
                lateinit var previous:TextView; lateinit var next:TextView
                fun display() {
                    msg.text=text.substring(cuts[page],cuts[page+1])
                    info.text="Texte long, affichage brut · partie ${page+1}/${cuts.size-1}"
                    previous.isEnabled=page>0; previous.alpha=if(previous.isEnabled) 1f else .35f
                    next.isEnabled=page<cuts.size-2; next.alpha=if(next.isEnabled) 1f else .35f
                }
                previous=action("‹ Précédent",pale,primary) { if(page>0) { page--; display() } }.apply { contentDescription="Partie précédente du message" }
                next=action("Suivant ›",pale,primary) { if(page<cuts.size-2) { page++; display() } }.apply { contentDescription="Partie suivante du message" }
                addView(info); addView(gap(6)); addView(msg); addView(gap(6))
                navigation.addView(previous,LinearLayout.LayoutParams(0,-2,1f)); navigation.addView(next,LinearLayout.LayoutParams(0,-2,1f)); addView(navigation)
                display()
            }
            if(role=="user" && attachmentData!=null) {
                for(i in 0 until attachmentData.length()) {
                    val item=attachmentData.optJSONObject(i) ?: continue
                    addView(gap(8)); addView(attachmentCard(item))
                }
            }
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
            } else if(role=="assistant" && currentMode!="chat" && durationMs!=null) {
                val seconds=(durationMs+500)/1000
                val elapsed=if(seconds<60) "${seconds} s" else "${seconds/60} min ${seconds%60} s"
                addView(label("◷  $elapsed",11f,muted).apply { contentDescription="Durée de la tâche : $elapsed"; setPadding(dp(5),dp(8),0,0) })
            }
        }
        val paneWidth=if(isWideLayout()) wideContentWidthPx() else resources.displayMetrics.widthPixels
        val width = (paneWidth * if(role=="user") .83 else .90).toInt()
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
            "open_url" -> "Ouverture du navigateur"
            "share_text" -> "Préparation d’un partage"
            "android_device" -> "Manipulation d’une application Android"
            "web_preview_start" -> "Démarrage de l’aperçu Web"
            "project_memory_add", "project_memory_update", "project_memory_delete" -> "Mémoire du projet mise à jour"
            "project_memory_search" -> "Consultation de la mémoire du projet"
            else -> "Utilisation d’un outil"
        }
        val card=label((if(pending) "◌  " else if(ok) "✓  " else "!  ")+title+(if(pending) "…" else if(!ok) " · échec" else "  › détails"),12f,if(!pending && !ok) danger else muted).apply {
            setPadding(dp(8),dp(6),dp(8),dp(6));background=shape(pale,10);isClickable=true;isFocusable=true
            contentDescription=title+if(ok)" · toucher pour afficher les paramètres et le résultat" else " · échec"
        }
        card.setOnClickListener {
            val details=try{JSONObject(raw)}catch(_:Exception){JSONObject()}
            val arguments=details.optJSONObject("parameters")?.toString(2)?.take(7000)?:"{}"
            val output=details.optJSONObject("result")?.toString(2)?.take(10000)?:raw.take(10000)
            sheet(title,"Détails de l’action et résultat retourné par l’outil") { body,_ ->
                body.addView(label("PARAMÈTRES",11f,muted,true));body.addView(gap(4))
                body.addView(label(arguments,12f,strong).apply{typeface=Typeface.MONOSPACE;setTextIsSelectable(true);setPadding(dp(10),dp(8),dp(10),dp(8));background=shape(canvas,12)})
                body.addView(gap(10));body.addView(label("RÉSULTAT",11f,muted,true));body.addView(gap(4))
                body.addView(label(output,12f,strong).apply{typeface=Typeface.MONOSPACE;setTextIsSelectable(true);setPadding(dp(10),dp(8),dp(10),dp(8));background=shape(canvas,12)})
            }
        }
        return card
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
        val switched = screen != "chat" || currentSession != id
        currentSession=id; screen="chat"
        val ticket=++chatLoadSerial; chatLoadingId=id
        val count=historyVisible[id] ?: 20
        if(switched) {
            frame(); root.addView(topBar("NEMESIS", "Chargement de la discussion…")); root.addView(separator())
            root.addView(label("Chargement…",14f,muted).apply { gravity=Gravity.CENTER },LinearLayout.LayoutParams(-1,0,1f))
        }
        try { chatLoader.execute {
            val session=try { JSONObject(py.callAttr("get_session",id,count).toString()) }
                catch(e:Exception) { JSONObject().put("ok",false).put("error",e.message ?: "Chargement impossible") }
            runOnUiThread {
                if (isFinishing || isDestroyed || ticket != chatLoadSerial || screen != "chat" || currentSession != id) return@runOnUiThread
                chatLoadingId=null
                if(!session.optBoolean("ok")) { toast(session.optString("error","Discussion introuvable")); showHome() }
                else renderChat(id,preservedScrollY,session)
            }
        } } catch (_:java.util.concurrent.RejectedExecutionException) { chatLoadingId=null }
    }
    private fun renderChat(id:String, preservedScrollY:Int?, session:JSONObject) {
        currentSession=id; currentMode=session.optString("mode","work"); currentProjectId=if(session.isNull("project_id")) null else session.optString("project_id").takeIf{it.isNotBlank()}; screen="chat"; frame()
        root.addView(topBar("NEMESIS",modeName(currentMode)+" · DeepSeek")); root.addView(separator())
        val (scroll,body)=scrollArea(16)
        // The shortcut floats above the discussion, not inside the growing
        // message list, and only appears when more content is below the reader.
        val scrollHost = scroll.parent as? android.view.ViewGroup
        scrollHost?.removeView(scroll)
        // On Chromebook/tablet scrollArea placed the scroll in a weighted stage.
        // Remove that now-empty stage too, or it competes with scrollLayer for half the height.
        if (scrollHost != null && scrollHost !== root) root.removeView(scrollHost)
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
        val transcriptParams=LinearLayout.LayoutParams(if(isWideLayout())wideContentWidthPx() else -1,0,1f)
        if(isWideLayout()) transcriptParams.gravity=Gravity.CENTER_HORIZONTAL
        root.addView(scrollLayer,transcriptParams)
        val messages=session.optJSONArray("messages") ?: JSONArray()
        val messageRevision=session.optLong("messages_revision",0)
        if(session.optBoolean("has_older",false)) {
            body.addView(action("Afficher les messages précédents",pale,primary) {
                historyVisible[id]=(historyVisible[id] ?: 20)+20
                showChat(id,scroll.scrollY)
            }.apply { contentDescription="Afficher les messages précédents" }); body.addView(gap(12))
        }
        if(messages.length()==0) {
            body.addView(gap(55)); body.addView(ImageView(this).apply { setImageResource(R.drawable.nemesis_logo); scaleType=ImageView.ScaleType.CENTER_CROP; background=shape(pale,14); clipToOutline=true; contentDescription="Logo NEMESIS" }, LinearLayout.LayoutParams(dp(52),dp(52)).apply { gravity=Gravity.CENTER_HORIZONTAL })
            body.addView(gap(12)); body.addView(label("Que souhaitez-vous faire ?",22f,strong,true).apply { gravity=Gravity.CENTER })
            body.addView(gap(7)); body.addView(label(modeDescription(currentMode),14f,muted).apply { gravity=Gravity.CENTER })
            if(currentMode!="chat") { body.addView(gap(14)); body.addView(label("Les outils sont exécutés sans confirmation. Les fichiers SSH et locaux sont distincts.",12f,muted).apply { gravity=Gravity.CENTER }) }
        }
        for(i in 0 until messages.length()) {
            val item=messages.getJSONObject(i); val role=item.optString("role"); val text=item.optString("text")
            val duration=if(item.isNull("duration_ms")) null else item.optLong("duration_ms")
            val view=if(role=="tool") { if(currentMode=="code") toolCard(text) else compactTool(text,currentMode) } else bubble(role,text,id,item.optLong("id"),duration,item.optInt("rating"),item.optJSONArray("attachments"))
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
            var readInFlight = false
            val update = object : Runnable {
                override fun run() {
                    if (screen != "chat" || currentSession != id || !live.isAttachedToWindow) return
                    if (!busy || busySession != id) {
                        JobRunner.takeError()?.let { toast(it) }
                        showChat(id,scroll.scrollY); return
                    }
                    status.text="NEMESIS réfléchit" + ".".repeat(((android.os.SystemClock.uptimeMillis()/500)%4).toInt())
                    if (!readInFlight) {
                        readInFlight = true
                        try { streamReader.execute {
                            val currentRevision = try { py.callAttr("message_revision",id).toString().toLong() } catch (_:Exception) { messageRevision }
                            val snapshot = try { JSONObject(py.callAttr("live_progress",id,12_000).toString()) } catch (_:Exception) { JSONObject() }
                            handler.post {
                                readInFlight = false
                                if (screen == "chat" && currentSession == id && live.isAttachedToWindow && busy && busySession == id) {
                                    if (currentRevision != messageRevision) { if(chatLoadingId != id) showChat(id,scroll.scrollY) }
                                    else {
                                        val raw = snapshot.optString("text", "")
                                        // SQLite counts Unicode codepoints, Android measures UTF-16 units.
                                        var start=(raw.length-12_000).coerceAtLeast(0)
                                        if(start>0 && start<raw.length && Character.isLowSurrogate(raw[start]) && Character.isHighSurrogate(raw[start-1])) start++
                                        val partial = if (snapshot.optBoolean("truncated",false) || start>0) "Aperçu de la fin du flux — la réponse complète est conservée.\n\n" + raw.substring(start) else raw
                                        if (partial != last) {
                                            last = partial; live.visibility = if(partial.isBlank()) View.GONE else View.VISIBLE
                                            // A bounded preview avoids re-laying out the entire growing response on every frame.
                                            // Never drag the reader down while new text arrives.
                                            markdown.setMarkdown(live,partial)
                                        }
                                    }
                                }
                            }
                        } } catch (_:java.util.concurrent.RejectedExecutionException) { readInFlight = false }
                    }
                    handler.postDelayed(this,if (last.length >= 10_000) 1_000L else 350L)
                }
            }
            live.postDelayed(update,350)
        }
        scroll.post { if(preservedScrollY!=null) scroll.scrollTo(0,preservedScrollY) else scroll.fullScroll(View.FOCUS_DOWN) }
        val compose=column().apply { setBackgroundColor(canvas); setPadding(dp(16),dp(6),dp(16),dp(10)) }
        if(session.optInt("uncertain")==1 && !busy) {
            compose.addView(action("Vérifier la page DeepSeek actuelle",pale,primary) {
                // Do not navigate: a security challenge may be on the live page.
                startActivity(Intent(this, com.nemap.android.BrowserActivity::class.java))
            }.apply { contentDescription="Vérifier la page DeepSeek actuelle" })
            compose.addView(gap(6))
            compose.addView(action("Reprendre la discussion",pale,primary) { recoverChat(id,if(session.isNull("conversation_url")) "" else session.optString("conversation_url", "")) })
            compose.addView(gap(6))
        }
        val pending=attachments[id] ?: mutableListOf()
        if(pending.isNotEmpty()) for(item in pending.toList()) {
            compose.addView(attachmentCard(item) { attachments[id]?.remove(item); File(item.optString("path")).delete(); showChat(id) }); compose.addView(gap(6))
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
                val preview = previewText
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
        val composeParams=LinearLayout.LayoutParams(if(isWideLayout())wideContentWidthPx() else -1,-2)
        if(isWideLayout()) composeParams.gravity=Gravity.CENTER_HORIZONTAL
        root.addView(compose,composeParams)
        root.addView(bottomNav(if(currentMode=="code") "code" else "agent"))
    }
    /** Compact attachment cards remain inside the corresponding message bubble. */
    private fun attachmentCard(item:JSONObject, remove:(()->Unit)?=null):View {
        val name=item.optString("name","Pièce jointe")
        val mime=item.optString("mime","")
        val file=try { File(item.optString("path")).canonicalFile } catch(_:Exception) { null }
        val base=File(filesDir,"attachments").canonicalFile
        val safe=file!=null && file.path.startsWith(base.path+File.separator) && file.isFile
        val row=LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL
            setPadding(dp(6),dp(6),dp(8),dp(6)); background=shape(white,12,line)
            contentDescription="Pièce jointe : $name"
        }
        val thumb=ImageView(this).apply {
            scaleType=ImageView.ScaleType.CENTER_CROP; clipToOutline=true
            contentDescription="Aperçu de $name"
        }
        val box=FrameLayout(this).apply { background=shape(pale,8); clipToOutline=true }
        box.addView(label(if(mime=="application/pdf" || name.endsWith(".pdf",true)) "PDF" else "▤",12f,primary,true).apply { gravity=Gravity.CENTER },FrameLayout.LayoutParams(-1,-1))
        box.addView(thumb,FrameLayout.LayoutParams(-1,-1))
        row.addView(box,LinearLayout.LayoutParams(dp(54),dp(54)))
        val text=column().apply { setPadding(dp(10),0,dp(4),0)
            addView(label(name,13f,strong,true).apply { maxLines=2; ellipsize=android.text.TextUtils.TruncateAt.END })
            val size=item.optLong("size",if(safe) file!!.length() else 0)
            val info=if(!safe) "Fichier indisponible" else if(size>=1024*1024) String.format(java.util.Locale.FRANCE,"%.1f Mo",size/1048576.0) else "${maxOf(1,size/1024)} Ko"
            addView(label(info,11f,muted))
        }
        row.addView(text,LinearLayout.LayoutParams(0,-2,1f))
        if(remove!=null) row.addView(action("×",Color.TRANSPARENT,muted) { remove() }.apply { contentDescription="Retirer $name" },LinearLayout.LayoutParams(dp(40),dp(40)))
        if(safe && (mime.startsWith("image/") || mime=="application/pdf" || name.endsWith(".pdf",true))) {
            Thread {
                val bitmap=try {
                    if(mime=="application/pdf" || name.endsWith(".pdf",true)) {
                        android.os.ParcelFileDescriptor.open(file,android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                            android.graphics.pdf.PdfRenderer(fd).use { pdf ->
                                if(pdf.pageCount==0) null else pdf.openPage(0).use { page ->
                                    val w=200; val h=(w.toFloat()*page.height/page.width).toInt().coerceIn(1,300)
                                    android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                                        bmp.eraseColor(Color.WHITE); page.render(bmp,null,null,android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    }
                                }
                            }
                        }
                    } else {
                        val opts=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                        android.graphics.BitmapFactory.decodeFile(file!!.path,opts)
                        opts.inSampleSize=maxOf(1,maxOf(opts.outWidth,opts.outHeight)/200); opts.inJustDecodeBounds=false
                        android.graphics.BitmapFactory.decodeFile(file.path,opts)
                    }
                } catch(_:Exception) { null }
                if(bitmap!=null) thumb.post { if(thumb.isAttachedToWindow) thumb.setImageBitmap(bitmap) else bitmap.recycle() }
            }.start()
        }
        return row
    }
    private fun attachmentMenu(id:String) {
        val session=try { JSONObject(py.callAttr("get_session",id).toString()) } catch(_:Exception) { JSONObject() }
        val mode=session.optString("mode",currentMode)
        sheet("Ajouter", when(mode) {
            "chat" -> "Joindre un fichier à cette discussion."
            "code" -> "GitHub et Git utilisent le shell Linux sélectionné; aucun connecteur ni jeton GitHub n’est intégré à l’application."
            else -> "Pièces jointes et outils de travail pour cette discussion."
        }) { body, dialog ->
            if(mode=="work") {
                val connector="google_workspace"
                val status=try { JSONObject(py.callAttr("connector_status",connector).toString()) } catch(_:Exception) { JSONObject() }
                val active=try { py.callAttr("connector_active",id,connector).toString()=="true" } catch(_:Exception) { false }
                val summary=if(!status.optBoolean("configured")) "Non configuré · Paramètres → Connecteurs" else if(active) "Actif pour cette discussion${if(status.optString("account").isNotBlank()) " · "+status.optString("account") else ""}" else "Connecté, désactivé pour cette discussion"
                body.addView(sheetRow("Google Workspace",summary) {
                    if(!status.optBoolean("configured")) { dialog.dismiss(); showSettings(); showConnectors() }
                    else {
                        val next=!active; py.callAttr("connector_set_active",id,connector,next)
                        if(screen=="chat" && currentSession==id) showChat(id)
                        dialog.dismiss(); toast(if(next) "Google Workspace activé pour cette discussion" else "Google Workspace désactivé")
                    }
                }); body.addView(gap(8))
            } else if(mode=="code") {
                body.addView(sheetRow("Environnement Linux", "Ouvrir Alpine/Debian/SSH et configurer gh/git dans le terminal") {
                    dialog.dismiss(); showExecutionEnvironments()
                }); body.addView(gap(8))
            }
            body.addView(sheetRow("Choisir un fichier",if((attachments[id]?.size ?: 0)>=4) "Maximum 4 fichiers par message" else "PDF, images et documents") {
                dialog.dismiss()
                if((attachments[id]?.size ?: 0)>=4) { toast("Maximum 4 pièces jointes."); return@sheetRow }
                pickerSession=id
                val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="*/*" }
                try { startActivityForResult(intent,302) } catch(_:Exception) { toast("Sélecteur de fichiers indisponible.") }
            })
        }
    }

    private fun googleSignIn() {
        val scopes=arrayOf(
            Scope("https://www.googleapis.com/auth/drive.readonly"),
            Scope("https://www.googleapis.com/auth/drive.file"),
            Scope("https://www.googleapis.com/auth/documents"),
            Scope("https://www.googleapis.com/auth/spreadsheets")
        )
        val options=GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestScopes(scopes[0],*scopes.drop(1).toTypedArray()).build()
        try { startActivityForResult(GoogleSignIn.getClient(this,options).signInIntent,303) }
        catch(e:Exception) { toast("Google Sign-In indisponible : ${e.message}") }
    }

    private fun showConnectors() {
        screen="connectors"; currentSession=null; frame()
        root.addView(topBar("Connecteurs","Services et outils externes")); root.addView(separator())
        val (_,body)=scrollArea(20)
        body.addView(gap(6)); body.addView(label("Connecter vos services",23f,strong,true)); body.addView(gap(6))
        body.addView(label("Les connecteurs MCP exposent les outils publiés par chaque service. Les jetons Bearer sont chiffrés dans Android Keystore. Testez chaque connexion avant utilisation.",14f,muted)); body.addView(gap(20))
        body.addView(label("CONNECTEUR NATIF",11f,muted,true)); body.addView(gap(8))
        val google=JSONObject(try{py.callAttr("connector_status","google_workspace").toString()}catch(_:Exception){"{}"})
        body.addView(settingCard("Google Workspace",if(google.optBoolean("configured"))"Compte lié · Drive à tester" else "Drive, Docs et Sheets · connexion Google") { editGoogleConnector() }); body.addView(gap(18))
        body.addView(label("CATALOGUE MCP",11f,muted,true)); body.addView(gap(8))
        val catalog = listOf(
            Triple("GitHub", "Dépôts, code et outils GitHub · authentification requise", "https://api.githubcopilot.com/mcp/"),
            Triple("Slack", "Recherche et actions dans les espaces Slack · authentification requise", "https://mcp.slack.com/mcp"),
            Triple("Notion", "Pages, bases de données et recherche Notion · authentification requise", "https://mcp.notion.com/mcp"),
            Triple("Linear", "Issues, projets et cycles Linear · authentification requise", "https://mcp.linear.app/mcp"),
            Triple("Atlassian", "Jira et Confluence · authentification requise", "https://mcp.atlassian.com/v2/mcp"),
            Triple("Figma", "Fichiers de design et FigJam · authentification requise", "https://mcp.figma.com/mcp"),
            Triple("Sentry", "Incidents et erreurs de vos projets · authentification requise", "https://mcp.sentry.dev/mcp")
        )
        val savedMcp = JSONArray(try { py.callAttr("mcp_servers").toString() } catch (_:Exception) { "[]" })
        val testState = getSharedPreferences("mcp_connection_status", MODE_PRIVATE)
        for ((name, description, endpoint) in catalog) {
            var configuredId = ""
            for (i in 0 until savedMcp.length()) {
                val server = savedMcp.optJSONObject(i) ?: continue
                if (server.optString("name").equals(name, true) || server.optString("target").trimEnd('/') == endpoint.trimEnd('/')) {
                    configuredId = server.optString("id"); break
                }
            }
            val status = when {
                configuredId.isBlank() -> "DISPONIBLE · non configuré"
                testState.getBoolean(configuredId, false) -> "CONNECTÉ · dernier test positif"
                else -> "CONFIGURÉ · connexion à tester"
            }
            body.addView(settingCard(name, "$status\n$description") {
                if (configuredId.isNotBlank()) mcpServerOptions(configuredId, name) else addMcpServer(name, endpoint)
            }); body.addView(gap(8))
        }
        body.addView(gap(10))
        body.addView(label("Connecteurs natifs et serveurs distants sont séparés : le catalogue ne signifie pas qu’un compte est lié. Les serveurs OAuth peuvent nécessiter une étape d’autorisation que NEMESIS ne prend pas encore en charge automatiquement.",12f,muted)); body.addView(gap(18))
        body.addView(label("AUTRES SERVICES",11f,muted,true)); body.addView(gap(8))
        body.addView(action("＋  Ajouter un serveur MCP personnalisé",pale,primary){addMcpServer()}); body.addView(gap(8))
        body.addView(action("Gérer et tester les connecteurs configurés",white,strong){showMcpServers()}.apply{background=shape(white,14,line)}); body.addView(gap(12))
        body.addView(label("Les services qui exigent OAuth doivent fournir un jeton d’accès compatible ou une configuration OAuth propre au service. Un connecteur n’est pas annoncé comme actif avant un test positif.",12f,muted))
    }

    private fun editGoogleConnector() {
        val old=JSONObject(try { py.callAttr("connector_status","google_workspace").toString() } catch(_:Exception) { "{}" })
        sheet("Google Workspace", "Connexion via l’écran Google officiel, pas dans la WebView DeepSeek. Le projet Android doit être enregistré dans Google Cloud Console (APIs Drive, Docs et Sheets activées, OAuth client Android avec le package et le certificat de signature). Accès Drive en lecture + création/modification des fichiers créés par NEMESIS.") { body, dialog ->
            if(old.optBoolean("configured")) {
                body.addView(label("Compte lié : ${old.optString("account")} · l’accès Drive doit être vérifié",14f,strong)); body.addView(gap(12))
                body.addView(action("Tester l’accès Google Drive",pale,primary) {
                    toast("Vérification de l’accès Google Drive…")
                    Thread {
                        val result=try { JSONObject(py.callAttr("google_test_config").toString()) }
                            catch(e:Exception) { JSONObject().put("ok",false).put("error",e.message ?: "Erreur") }
                        runOnUiThread {
                            if(!isFinishing && !isDestroyed) toast(if(result.optBoolean("ok")) "Google Drive fonctionne pour ${result.optString("account")}" else result.optString("error","Test Google impossible"))
                        }
                    }.start()
                }); body.addView(gap(8))
                body.addView(action("Déconnecter ce compte",Color.TRANSPARENT,danger) {
                    val options=GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().build()
                    GoogleSignIn.getClient(this,options).revokeAccess().addOnCompleteListener { task ->
                        py.callAttr("connector_disconnect","google_workspace"); dialog.dismiss(); showSettings()
                        if(!task.isSuccessful) toast("Compte déconnecté localement ; révocation Google à confirmer en ligne.")
                    }
                }); body.addView(gap(8))
            }
            body.addView(action(if(old.optBoolean("configured")) "Reconnecter Google" else "Se connecter avec Google",primary,white) { dialog.dismiss(); googleSignIn() })
        }
    }
    private fun chooseWorkspaceTarget() {
        val sessions=JSONArray(py.callAttr("list_sessions").toString())
        sheet("Importer dans un workspace", "Le fichier sera copié dans l’espace local choisi, sans être joint ni envoyé dans une discussion.") { body, dialog ->
            var count=0
            for(i in 0 until sessions.length()) {
                val session=sessions.getJSONObject(i); val mode=session.optString("mode")
                if(mode!="work" && mode!="code") continue
                count++; val id=session.optString("id")
                body.addView(sheetRow(session.optString("title"),modeName(mode)+" · workspace de cette discussion") { dialog.dismiss(); chooseWorkspaceUpload(id) }); body.addView(gap(8))
            }
            if(count==0) body.addView(label("Créez d’abord une discussion Work ou Code pour disposer d’un workspace.",14f,muted))
        }
    }
    private fun chooseWorkspaceUpload(id:String) {
        workspaceUploadSession=id
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="*/*" }
        try { startActivityForResult(intent,304) } catch(_:Exception) { workspaceUploadSession=null; toast("Sélecteur de fichiers indisponible.") }
    }
    private fun showFiles() {
        screen="files"; currentSession=null; frame(); root.addView(topBar("Fichiers","Espaces de travail")); root.addView(separator())
        val (_,body)=scrollArea(20); body.addView(gap(12)); body.addView(label("Espaces de travail",25f,strong,true)); body.addView(gap(6))
        body.addView(label("Fichiers Work et Code, classés par discussion.",14f,muted)); body.addView(gap(14))
        body.addView(action("↑   Importer dans un workspace",pale,primary) { chooseWorkspaceTarget() }); body.addView(gap(18))
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
        root.addView(bottomNav("files"))
    }
    private fun showSessionFiles(id:String) {
        val files=try { JSONArray(py.callAttr("list_files",id).toString()) } catch (e:Exception) { toast(e.message ?: "Espace indisponible"); return }
        sheet("Workspace · fichiers", "Ces fichiers restent dans l’espace local de la discussion. L’import ne les joint pas au message et ne les envoie pas automatiquement à l’agent.") { body, dialog ->
            body.addView(action("↑   Importer un fichier ici",pale,primary) { dialog.dismiss(); chooseWorkspaceUpload(id) }); body.addView(gap(12))
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
        if(request==303) {
            if(result==RESULT_OK && data!=null) {
                try {
                    val account=GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
                    val email=account.email ?: throw IllegalStateException("Google n’a pas fourni l’adresse du compte")
                    GoogleWorkspaceBridge.saveAccount(email)
                    py.callAttr("connector_refresh_status")
                    toast("Compte Google lié : $email. Teste l’accès Drive dans Connecteurs.")
                    if(screen=="settings") showSettings()
                } catch(e:Exception) {
                    val status=(e as? com.google.android.gms.common.api.ApiException)?.statusCode
                    val message=when(status) {
                        10 -> "OAuth Google mal configuré : ajoute le package com.nemesis.apk et le SHA‑1 de signature dans Google Cloud Console."
                        12501 -> "Connexion Google annulée."
                        else -> "Connexion Google échouée : ${e.message ?: "erreur inconnue"}"
                    }
                    toast(message)
                }
            } else toast("Connexion Google annulée.")
            return
        }
        val uri=data?.data
        if(request==301 && result==RESULT_OK && uri!=null && selectedFile!=null) {
            try { File(selectedFile!!).inputStream().use { source -> contentResolver.openOutputStream(uri)?.use { source.copyTo(it) } }; toast("Fichier exporté") } catch(_:Exception) { toast("Export impossible") }
            selectedFile=null
        }
        if(request==305) {
            val id=projectUploadId;projectUploadId=null
            if(result!=RESULT_OK || uri==null || id==null)return
            Thread {
                var reserved:File?=null
                try {
                    var original="fichier";contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())original=it.getString(0)?:original}
                    original=original.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001F]"),"_").trim().trim('.').take(180).ifBlank{"fichier"}
                    val directory=File(py.callAttr("project_root",id).toString()).canonicalFile
                    if(!directory.exists()&&!directory.mkdirs())throw IllegalStateException("Impossible de préparer le dossier du projet")
                    var target=File(directory,original);var suffix=2
                    while(!target.createNewFile()){target=File(directory,"${File(original).nameWithoutExtension} ($suffix)"+(if(File(original).extension.isBlank())"" else ".${File(original).extension}"));suffix++;if(suffix>5000)throw IllegalStateException("Trop de fichiers portant ce nom")}
                    reserved=target
                    contentResolver.openInputStream(uri)?.use{input->target.outputStream().use{output->val buffer=ByteArray(65536);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;if(total>100L*1024*1024)throw IllegalArgumentException("Fichier limité à 100 Mo");output.write(buffer,0,n)}}}?:throw IllegalArgumentException("Fichier illisible")
                    val copied=target.name;runOnUiThread{if(!isFinishing&&!isDestroyed){toast("Ajouté au projet : $copied");if(screen=="project")showProject(id)}}
                }catch(e:Exception){reserved?.delete();runOnUiThread{if(!isFinishing&&!isDestroyed)toast(e.message?:"Import du projet impossible")}}
            }.start();return
        }
        if(request==304) {
            val id=workspaceUploadSession
            workspaceUploadSession=null
            if(result!=RESULT_OK || uri==null || id==null) { if(result!=RESULT_CANCELED) toast("Import annulé."); return }
            Thread {
                var reserved:File?=null
                try {
                    val session=JSONObject(py.callAttr("get_session",id).toString())
                    val mode=session.optString("mode")
                    if(mode!="work" && mode!="code") throw IllegalArgumentException("Le workspace n’est disponible qu’en mode Work ou Code.")
                    var original="fichier"
                    contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) original=it.getString(0) ?: original }
                    original=original.substringAfterLast('/').substringAfterLast('\\').replace(Regex("[\\u0000-\\u001F]"),"_").trim().trim('.').take(180).ifBlank { "fichier" }
                    val ext=original.substringAfterLast('.',"").takeIf { it.isNotBlank() && original.contains('.') }
                    val stem=if(ext==null) original else original.dropLast(ext.length+1).ifBlank { "fichier" }
                    val directory=File(py.callAttr("workspace_prepare",id).toString()).canonicalFile
                    if(!directory.exists() && !directory.mkdirs()) throw IllegalStateException("Impossible de préparer le workspace.")
                    if(!directory.isDirectory) throw IllegalStateException("Workspace invalide.")
                    var candidate=File(directory,original); var suffix=2
                    while(!candidate.createNewFile()) {
                        val name=if(ext==null) "$stem ($suffix)" else "$stem ($suffix).$ext"
                        candidate=File(directory,name); suffix++
                        if(suffix>10_000) throw IllegalStateException("Trop de fichiers portant ce nom.")
                    }
                    reserved=candidate
                    contentResolver.openInputStream(uri)?.use { input -> candidate.outputStream().use { output ->
                        val buffer=ByteArray(65536); var total=0L; val limit=100L*1024*1024
                        while(true) { val n=input.read(buffer); if(n<0) break; total+=n; if(total>limit) throw IllegalArgumentException("Fichier trop volumineux (100 Mo maximum). "); output.write(buffer,0,n) }
                    } } ?: throw IllegalArgumentException("Fichier illisible.")
                    val copied=candidate
                    runOnUiThread {
                        if(isFinishing || isDestroyed) return@runOnUiThread
                        toast("Copié dans le workspace : ${copied.name}. Demandez ensuite à l’agent de le traiter.")
                        if(screen=="files") showFiles() else if(screen=="chat" && currentSession==id) showSessionFiles(id)
                    }
                } catch(e:Exception) {
                    reserved?.delete()
                    runOnUiThread { if(!isFinishing && !isDestroyed) toast(e.message ?: "Import impossible.") }
                }
            }.start()
            return
        }
        if(request==302 && result==RESULT_OK && uri!=null) {
            val id=pickerSession ?: return
            pickerSession=null
            // Copy off the UI thread: a 50 MiB document must not freeze Android.
            Thread {
                var target:File?=null
                try {
                    var name="Pièce jointe"; contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) name=it.getString(0) ?: name }
                    var mime=contentResolver.getType(uri) ?: ""
                    if(name.endsWith(".pdf",true)) mime="application/pdf"
                    if(mime=="application/octet-stream" && name.endsWith(".txt",true)) mime="text/plain"
                    if(mime.isBlank()) mime="application/octet-stream"
                    val limit=(if(mime=="application/pdf") 50L else 20L)*1024*1024
                    val dir=File(filesDir,"attachments").apply { mkdirs() }; target=File(dir,UUID.randomUUID().toString())
                    contentResolver.openInputStream(uri)?.use { input -> target!!.outputStream().use { output ->
                        val buffer=ByteArray(65536); var total=0L
                        while(true) { val n=input.read(buffer); if(n<0) break; total+=n; if(total>limit) throw IllegalArgumentException("Fichier trop volumineux (maximum ${limit/1024/1024} Mo)."); output.write(buffer,0,n) }
                        if(total==0L) throw IllegalArgumentException("Fichier vide.")
                    } } ?: throw IllegalArgumentException("Fichier illisible")
                    val prepared=target!!; val item=JSONObject().put("name",name).put("mime",mime).put("path",prepared.absolutePath).put("size",prepared.length())
                    runOnUiThread {
                        val existing=attachments[id] ?: mutableListOf()
                        val total=existing.sumOf { it.optLong("size",File(it.optString("path")).length()) }+prepared.length()
                        if(isFinishing || isDestroyed || existing.size>=4 || total>64L*1024*1024) {
                            prepared.delete(); if(!isFinishing && !isDestroyed) toast("Maximum 4 fichiers et 64 Mo au total.")
                        } else {
                            attachments.getOrPut(id){mutableListOf()}.add(item)
                            if(screen=="chat" && currentSession==id) showChat(id)
                        }
                    }
                } catch(e:Exception) {
                    target?.delete(); runOnUiThread { if(!isFinishing && !isDestroyed) toast(e.message ?: "Impossible d’ajouter le fichier") }
                }
            }.start()
        }
        pickerSession=null
    }
    private fun showSettings() {
        screen="settings"; currentSession=null; frame(); root.addView(topBar("Paramètres","Connexion et environnement")); root.addView(separator())
        val (_,body)=scrollArea(20); body.addView(gap(12)); body.addView(label("Paramètres",25f,strong,true)); body.addView(gap(8)); body.addView(label("Configurez votre assistant et votre espace de travail.",14f,muted)); body.addView(gap(22))
        body.addView(gap(22))
        body.addView(label("ENVIRONNEMENT D’EXÉCUTION",11f,muted,true)); body.addView(gap(8))
        body.addView(settingCard("Environnements Linux", "Alpine, Debian ou SSH distant pour les commandes et le terminal interactif.") { showExecutionEnvironments() }); body.addView(gap(10))
        val accessibility=AndroidComputerBridge.accessibilityEnabled()
        body.addView(settingCard("Contrôle des applications Android", if(accessibility) "Accessibilité activée · outils d’interface disponibles en Work et Code." else "Facultatif · autorisez NEMESIS dans Accessibilité pour les gestes et la lecture de l’interface.") { AndroidComputerBridge.openAccessibilitySettings() }); body.addView(gap(22))
        body.addView(label("MOTEUR PRINCIPAL",11f,muted,true)); body.addView(gap(8))
        val prov = runCatching { JSONObject(py.callAttr("provider_get_config").toString()) }.getOrElse { JSONObject().put("type","nemapi") }
        val activeType = prov.optString("type","nemapi")
        fun applyProvider(type:String) {
            runCatching { py.callAttr("provider_set_type", type) }
            showSettings()
        }
        // Ligne NemApi : switch + carte
        val nemapiRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)); background=shape(white,16,line) }
        val nemapiLabels = column().apply { addView(label("NemApi",15f,strong,true)); addView(gap(4)); addView(label(if(activeType=="nemapi") "Moteur actif · DOM DeepSeek" else "Connexion DeepSeek et paramètres intégrés",13f,muted)) }
        nemapiRow.addView(nemapiLabels, LinearLayout.LayoutParams(0,-2,1f))
        val nemapiSwitch = Switch(this).apply {
            isChecked = activeType=="nemapi"; contentDescription="Activer le moteur NemApi"
            setOnCheckedChangeListener { _, checked -> if(checked && activeType!="nemapi") applyProvider("nemapi") else if(!checked && activeType=="nemapi") toast("NemApi reste le moteur par défaut ; activez TokenHarbor pour basculer.") }
        }
        nemapiRow.addView(nemapiSwitch); body.addView(nemapiRow); body.addView(gap(6))
        body.addView(settingCard("Ouvrir les paramètres NemApi","Connexion DeepSeek, DOM, redémarrage du moteur.") { startActivity(Intent(this, com.nemap.android.MainActivity::class.java)) }); body.addView(gap(10))
        // Ligne TokenHarbor : switch + carte
        val th = prov.optJSONObject("tokenharbor") ?: JSONObject()
        val thHas = th.optBoolean("has_keys", false)
        val thRow = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(12)); background=shape(white,16,line) }
        val thLabels = column().apply { addView(label("TokenHarbor",15f,strong,true)); addView(gap(4)); addView(label(if(activeType=="tokenharbor") "Moteur actif · ${(th.optJSONArray("keys")?.length()?:0)} clé(s)" else if(thHas) "Multi-clés · ${(th.optJSONArray("keys")?.length()?:0)} clé(s) configurée(s)" else "Aucune clé · ouvrez la configuration",13f,muted)) }
        thRow.addView(thLabels, LinearLayout.LayoutParams(0,-2,1f))
        val thSwitch = Switch(this).apply {
            isChecked = activeType=="tokenharbor"; contentDescription="Activer le moteur TokenHarbor"
            setOnCheckedChangeListener { _, checked ->
                if(checked) {
                    if(!thHas) { toast("Configurez d'abord au moins une clé TokenHarbor."); isChecked=false; return@setOnCheckedChangeListener }
                    applyProvider("tokenharbor")
                } else if(activeType=="tokenharbor") {
                    toast("Activez NemApi pour rebasculer sur le moteur DOM."); isChecked=true
                }
            }
        }
        thRow.addView(thSwitch); body.addView(thRow); body.addView(gap(6))
        body.addView(settingCard("Configurer TokenHarbor","Clés API, sélection du modèle et routage automatique sur quota.") { showTokenHarborSettings() }); body.addView(gap(10))
        body.addView(settingCard("Redémarrer le moteur DOM","Si le moteur ne répond plus après une longue session. Cookies et discussions conservés, aucun message renvoyé.") { restartDomEngine() }); body.addView(gap(10))
        body.addView(label("CONNEXION",11f,muted,true)); body.addView(gap(8))
        body.addView(settingCard("Redémarrer le moteur DOM","Si le moteur ne répond plus après une longue session. Cookies et discussions conservés, aucun message renvoyé.") { restartDomEngine() }); body.addView(gap(10))
        body.addView(settingCard("Connecteurs","Google Workspace pour Work; GitHub via gh/git dans le shell Linux. Aucun jeton GitHub stocké dans l’APK.") { showConnectors() }); body.addView(gap(10))
        body.addView(settingCard("GitHub dans l’environnement Linux", "Pour Code, utilisez gh/git dans Alpine, Debian ou le shell SSH. Authentifiez-vous depuis ce terminal; NEMESIS ne conserve aucun PAT GitHub.") { showExecutionEnvironments() }); body.addView(gap(10))
        body.addView(gap(24)); body.addView(label("ESPACE DE TRAVAIL",11f,muted,true)); body.addView(gap(8))
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
        body.addView(gap(20)); body.addView(label("NEMESIS Android · version ${packageManager.getPackageInfo(packageName,0).versionName}",12f,muted))
    }
    private fun executionState() = JSONObject(py.callAttr("environment_status").toString())
    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val mb = bytes / 1_000_000.0
        return if (mb < 1000) String.format(java.util.Locale.getDefault(), "%.0f Mo", mb)
        else String.format(java.util.Locale.getDefault(), "%.1f Go", mb / 1000.0)
    }
    private fun showExecutionEnvironments() {
        val state = try { executionState() } catch (e: Exception) { toast("État indisponible : ${e.message}"); return }
        val items = state.optJSONArray("items") ?: JSONArray()
        val active = state.optString("active", "")
        sheet("Environnements", "Un seul shell actif à la fois. Les distributions s’installent uniquement sur demande; l’agent Bash et le Terminal utilisent le shell choisi.") { body, dialog ->
            body.addView(gap(8))
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val supported = item.optBoolean("supported", true)
                val installed = item.optBoolean("installed")
                val selected = active == id
                val card = column().apply {
                    setPadding(dp(15),dp(13),dp(15),dp(13))
                    background = shape(if (selected) pale else white,18,if (selected) primary else line)
                }
                val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
                head.addView(label(item.optString("label"),17f,strong,true), LinearLayout.LayoutParams(0,-2,1f))
                if (selected) head.addView(label("ACTIF",10f,primary,true).apply { setPadding(dp(9),dp(5),dp(9),dp(5)); background=shape(white,12,primary) })
                card.addView(head); card.addView(gap(4))
                val caption = when (id) {
                    "alpine" -> "Léger et rapide · Alpine Linux ${item.optString("version")}"
                    "debian" -> "Stable et robuste · Debian ${item.optString("version")}"
                    else -> if (installed) "${item.optString("user")}@${item.optString("host")} · shell Linux distant" else "Connectez un serveur Linux distant via SSH."
                }
                card.addView(label(caption,12.5f,muted))
                if (id != "ssh") {
                    val estimate = "Téléchargement ≈ ${formatBytes(item.optLong("download_bytes"))}  ·  installé ≈ ${formatBytes(item.optLong("installed_bytes"))}"
                    card.addView(gap(5)); card.addView(label(estimate,11.5f,muted))
                    val ready=item.optBoolean("toolchain_ready")
                    card.addView(label(if(ready) "Git · Python/pip · Node.js/npm · SSH · outils Unix prêts" else "Inclut Git, Python/pip, Node.js/npm, SSH et outils Unix",11.5f,if(ready) primary else muted))
                }
                if (!supported) {
                    card.addView(gap(8)); card.addView(label(item.optString("error","Non disponible sur cet appareil"),12f,danger))
                } else {
                    card.addView(gap(10))
                    val row = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
                    if (id == "ssh" && !installed) {
                        row.addView(action("Configurer SSH",pale,primary) { dialog.dismiss(); sshSettings() }, LinearLayout.LayoutParams(-2,dp(44)))
                    } else {
                        val toggleText = if (selected) "Désactiver" else if (installed) "Activer" else "Activer après installation"
                        row.addView(action(toggleText,if(selected) Color.TRANSPARENT else primary,if(selected) danger else white) {
                            if (!installed) { toast("Installez ou configurez cet environnement d’abord."); return@action }
                            val result=JSONObject(py.callAttr("environment_set",if(selected) "" else id).toString())
                            if(result.optBoolean("success")) { dialog.dismiss(); showExecutionEnvironments() } else toast(result.optString("error","Sélection impossible"))
                        }, LinearLayout.LayoutParams(0,dp(44),1f))
                        if (installed) {
                            row.addView(View(this),LinearLayout.LayoutParams(dp(8),1))
                            row.addView(action("Terminal",pale,primary) { dialog.dismiss(); showExecutionTerminal(id) },LinearLayout.LayoutParams(0,dp(44),1f))
                        } else {
                            row.addView(View(this),LinearLayout.LayoutParams(dp(8),1))
                            lateinit var installButton:TextView
                            installButton = action("Installer",primary,white) { installExecutionEnvironment(id,dialog,installButton) }
                            row.addView(installButton,LinearLayout.LayoutParams(0,dp(44),1f))
                        }
                    }
                    card.addView(row)
                    if (id != "ssh") {
                        card.addView(gap(5))
                        card.addView(action("Journal d'installation",Color.TRANSPARENT,muted) { showProotInstallLog(id) },LinearLayout.LayoutParams(-1,dp(40)))
                    }
                    if (installed && id == "ssh") {
                        card.addView(gap(6))
                        card.addView(action("Configurer SSH / SFTP",pale,primary) { dialog.dismiss(); sshSettings() },LinearLayout.LayoutParams(-1,dp(44)))
                    }
                    if (installed && id != "ssh" && !item.optBoolean("toolchain_ready")) {
                        card.addView(gap(5))
                        lateinit var toolButton:TextView
                        toolButton=action("Installer Git, Python, Node.js…",pale,primary) { installExecutionEnvironment(id,dialog,toolButton) }
                        card.addView(toolButton,LinearLayout.LayoutParams(-1,dp(44)))
                    }
                    if (installed && id != "ssh") {
                        card.addView(gap(5))
                        card.addView(action("Supprimer la distribution",Color.TRANSPARENT,danger) {
                            sheet("Supprimer ${item.optString("label")} ?", "Seuls les fichiers privés de cette distribution seront supprimés. Les autres environnements et vos fichiers restent intacts.") { confirm, ask ->
                                confirm.addView(action("Supprimer",Color.TRANSPARENT,danger) {
                                    Thread {
                                        val result=try { JSONObject(py.callAttr("environment_remove",id).toString()) } catch(e:Exception) { JSONObject().put("success",false).put("error",e.message) }
                                        runOnUiThread { if(!isFinishing && !isDestroyed) { ask.dismiss(); dialog.dismiss(); if(result.optBoolean("success")) showExecutionEnvironments() else toast(result.optString("error","Suppression impossible")) } }
                                    }.start()
                                }); confirm.addView(gap(6)); confirm.addView(action("Annuler",Color.TRANSPARENT,muted) { ask.dismiss() })
                            }
                        })
                    }
                }
                body.addView(card); body.addView(gap(10))
            }
            body.addView(label("Les tailles sont indicatives; l’espace utilisé exact s’affiche après installation. Les rootfs ne sont pas intégrés à l’APK.",11f,muted))
        }
    }
    private fun installExecutionEnvironment(id:String,parent:Dialog,button:TextView) {
        button.isEnabled=false;button.text="Installation…"
        val progress=android.app.ProgressDialog(this).apply{
            setTitle(if(id=="debian")"Installation de Debian" else "Installation d’Alpine")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);max=1000;progress=0;setIndeterminate(true);setCancelable(false)
            setMessage("Préparation de l’environnement…");show()
        }
        val handler=android.os.Handler(android.os.Looper.getMainLooper());var finished=false
        val poll=object:Runnable{override fun run(){
            if(finished||isFinishing||isDestroyed)return
            val state=try{JSONObject(py.callAttr("environment_install_progress",id).toString())}catch(_:Exception){JSONObject()}
            val phase=state.optString("phase","starting");val percent=state.optInt("percent",0).coerceIn(0,100)
            val phaseText=when(phase){"download"->"Téléchargement du rootfs";"verify"->"Vérification de l’archive";"extract"->"Extraction du système";"packages"->"Installation des outils et configuration de nemesis";"finalize"->"Vérifications finales";else->"Préparation de l’environnement"}
            val detail=state.optString("detail","")
            progress.setMessage(if(detail.isBlank())phaseText else "$phaseText\n$detail")
            progress.isIndeterminate=phase=="packages"||phase=="starting"
            progress.progress=percent*10
            handler.postDelayed(this,600)
        }}
        handler.post(poll)
        Thread{
            val result=try{JSONObject(py.callAttr("environment_install",id).toString())}catch(e:Exception){JSONObject().put("success",false).put("error",e.stackTraceToString())}
            runOnUiThread{
                finished=true;handler.removeCallbacks(poll);if(progress.isShowing)progress.dismiss()
                if(isFinishing||isDestroyed)return@runOnUiThread
                if(result.optBoolean("success")&&result.optBoolean("installed")){
                    parent.dismiss();toast("${result.optString("label",id)} installé · ${formatBytes(result.optLong("actual_installed_bytes"))}");showExecutionEnvironments()
                }else{
                    button.isEnabled=true;button.text="Réessayer"
                    val diagnostic=result.optString("error","Échec sans détail transmis par l’environnement Python.")
                    android.app.AlertDialog.Builder(this).setTitle("Installation échouée — $id")
                        .setMessage(diagnostic).setPositiveButton("Fermer",null)
                        .setNeutralButton("Copier le diagnostic"){_,_->
                            val clipboard=getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Diagnostic installation $id",diagnostic));toast("Diagnostic copié")
                        }.show()
                }
            }
        }.start()
    }
    /**
     * Journal d'installation PRoot : sortie complète de l'étape qui a échoué,
     * diagnostics du moteur (PRoot) et test des primitives. Lecture seule.
     */
    private fun showProotInstallLog(id:String) {
        Thread {
            val payload=try { JSONObject(py.callAttr("environment_install_log",id).toString()) } catch(e:Exception){ JSONObject().put("log",e.message) }
            runOnUiThread {
                if(isFinishing||isDestroyed) return@runOnUiThread
                val text=payload.optString("log","")
                sheet("Journal d'installation · $id",payload.optString("path","")) { body, dialog ->
                    body.addView(label(if(text.isBlank())"Aucun journal d'installation pour cette distribution." else text,10f,muted).apply{
                        setTextIsSelectable(true)
                        setPadding(dp(4),dp(4),dp(4),dp(4))
                    })
                }
            }
        }.start()
    }

    fun showWebPreview(url:String) {
        if (!url.matches(Regex("http://127\\.0\\.0\\.1:[0-9]{1,5}"))) { toast("URL d’aperçu locale refusée."); return }
        sheet("Aperçu Web · Code", "Serveur local dans l’environnement Linux actif · $url") { body, dialog ->
            val bar=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
            val state=label("Chargement de l’aperçu…",12f,muted)
            bar.addView(state,LinearLayout.LayoutParams(0,-2,1f))
            body.addView(bar); body.addView(gap(7))
            val web=WebView(this).apply {
                setBackgroundColor(Color.WHITE); settings.javaScriptEnabled=true; settings.domStorageEnabled=true
                settings.allowFileAccess=false; settings.allowContentAccess=false; settings.javaScriptCanOpenWindowsAutomatically=false
                settings.setSupportZoom(true)
                webViewClient=object:WebViewClient(){
                    override fun onPageFinished(view:WebView?,loaded:String?){state.text=loaded ?: "Aperçu prêt"}
                    override fun onReceivedError(view:WebView?,request:android.webkit.WebResourceRequest?,error:android.webkit.WebResourceError?){if(request?.isForMainFrame==true)state.text="Erreur : "+(error?.description ?: "chargement impossible")}
                }
                webChromeClient=WebChromeClient()
            }
            body.addView(web,LinearLayout.LayoutParams(-1,dp(530)))
            web.loadUrl(url)
            dialog.setOnDismissListener { web.stopLoading(); web.destroy() }
        }
    }

    private fun showExecutionTerminal(id:String) {
        val title = when(id) { "debian" -> "Terminal · Debian"; "ssh" -> "Terminal · SSH"; else -> "Terminal · Alpine" }
        sheet(title,"Session shell persistante avec PTY : commandes interactives, ANSI, historique et signaux clavier.") { body, dialog ->
            val status=label("Chargement du terminal…",11f,muted)
            body.addView(status); body.addView(gap(6))
            val web=android.webkit.WebView(this).apply {
                setBackgroundColor(Color.rgb(11,17,24)); isFocusable=true; isFocusableInTouchMode=true
                settings.javaScriptEnabled=true
                settings.domStorageEnabled=false
                settings.allowFileAccess=true
                settings.allowContentAccess=false
                settings.javaScriptCanOpenWindowsAutomatically=false
                settings.setSupportZoom(false)
            }
            val sessionId=java.util.concurrent.atomic.AtomicReference<String?>(null)
            val startRequested=java.util.concurrent.atomic.AtomicBoolean(false)
            val closed=java.util.concurrent.atomic.AtomicBoolean(false)
            val reportedClosed=java.util.concurrent.atomic.AtomicBoolean(false)
            val executor=java.util.concurrent.ScheduledThreadPoolExecutor(1).apply { removeOnCancelPolicy=true }
            fun showStatus(message:String) {
                runOnUiThread {
                    if(!isFinishing && !isDestroyed && dialog.isShowing) {
                        status.text=message
                        web.evaluateJavascript("window.nemesisStatus(${JSONObject.quote(message)})",null)
                    }
                }
            }
            web.webChromeClient=object : android.webkit.WebChromeClient() {
                override fun onConsoleMessage(message:android.webkit.ConsoleMessage):Boolean {
                    if(message.messageLevel()==android.webkit.ConsoleMessage.MessageLevel.ERROR) {
                        android.util.Log.e("NemesisTerminal", "WebView JS ${message.message()} (${message.sourceId()}:${message.lineNumber()})")
                        if(!startRequested.get() && !closed.get()) showStatus("Erreur du terminal web : ${message.message().take(140)}")
                    }
                    return true
                }
            }
            web.webViewClient=object : android.webkit.WebViewClient() {
                override fun onPageFinished(view:android.webkit.WebView?, url:String?) {
                    android.util.Log.i("NemesisTerminal", "Page chargée ${url ?: ""}")
                }
                override fun onReceivedError(view:android.webkit.WebView?, request:android.webkit.WebResourceRequest?, error:android.webkit.WebResourceError?) {
                    if(request?.isForMainFrame==true && !closed.get()) {
                        android.util.Log.e("NemesisTerminal", "Chargement échoué ${error?.description ?: "erreur WebView"}")
                        showStatus("Chargement du terminal impossible : ${error?.description ?: "erreur WebView"}")
                    }
                }
            }
            web.postDelayed({
                if(!closed.get() && !startRequested.get() && status.text.toString()=="Chargement du terminal…")
                    showStatus("Le terminal web ne répond pas. Fermez puis rouvrez le terminal.")
            },10_000)
            web.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface fun ready(columns:Int, rows:Int) {
                    if(closed.get() || !startRequested.compareAndSet(false,true)) return
                    android.util.Log.i("NemesisTerminal", "xterm prêt ${columns}x${rows}; ouverture du PTY")
                    showStatus("Préparation du shell…")
                    web.postDelayed({ if(!closed.get() && sessionId.get()==null) showStatus("Le shell met plus de temps à démarrer…") },8_000)
                    executor.execute {
                        try {
                            val result=JSONObject(py.callAttr("environment_terminal_start",id,columns,rows).toString())
                            if(!result.optBoolean("success")) throw IllegalStateException(result.optString("error","Terminal indisponible"))
                            val sid=result.optString("session_id")
                            if(sid.isBlank()) throw IllegalStateException("Identifiant de session terminal manquant")
                            sessionId.set(sid)
                            if(closed.get()) {
                                py.callAttr("environment_terminal_close",sid,id); sessionId.set(null); return@execute
                            }
                            val environment=if(id=="ssh") "SSH distant" else "${result.optString("distro",id).replaceFirstChar { it.uppercase() }} ${result.optString("version")}".trim()
                            android.util.Log.i("NemesisTerminal", "PTY démarré · $environment")
                            showStatus("Connecté · $environment · PTY actif")
                            executor.scheduleWithFixedDelay({
                                if(closed.get()) return@scheduleWithFixedDelay
                                val active=sessionId.get() ?: return@scheduleWithFixedDelay
                                try {
                                    val update=JSONObject(py.callAttr("environment_terminal_read",active,id).toString())
                                    val output=update.optString("output_b64","")
                                    if(output.isNotEmpty()) web.post { if(!closed.get()) web.evaluateJavascript("window.nemesisWriteB64(${JSONObject.quote(output)})",null) }
                                    if(update.optBoolean("closed") && reportedClosed.compareAndSet(false,true)) showStatus("Session shell fermée")
                                    else if(!update.optBoolean("success") && !reportedClosed.get()) showStatus(update.optString("error","Lecture du terminal interrompue"))
                                } catch(e:Exception) { if(reportedClosed.compareAndSet(false,true)) showStatus("Session shell interrompue") }
                            },0,40,java.util.concurrent.TimeUnit.MILLISECONDS)
                            web.postDelayed({
                                if(!closed.get()) {
                                    web.requestFocus()
                                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(web,InputMethodManager.SHOW_IMPLICIT)
                                }
                            },350)
                        } catch(e:Exception) {
                            startRequested.set(false)
                            showStatus("Échec : ${e.message ?: "terminal indisponible"}")
                        }
                    }
                }
                @android.webkit.JavascriptInterface fun sendInput(encoded:String) {
                    if(closed.get() || encoded.isEmpty()) return
                    try { executor.execute {
                        val sid=sessionId.get() ?: return@execute
                        val result=JSONObject(py.callAttr("environment_terminal_write",sid,encoded,id).toString())
                        if(!result.optBoolean("success")) showStatus(result.optString("error","Entrée terminal impossible"))
                    } } catch(_:Exception) {}
                }
                @android.webkit.JavascriptInterface fun resize(columns:Int,rows:Int) {
                    if(closed.get()) return
                    try { executor.execute {
                        val sid=sessionId.get() ?: return@execute
                        py.callAttr("environment_terminal_resize",sid,columns,rows,id)
                    } } catch(_:Exception) {}
                }
            },"Nemesis")
            body.addView(web,LinearLayout.LayoutParams(-1,dp(470)))
            dialog.setOnDismissListener {
                closed.set(true)
                try {
                    executor.execute {
                        val sid=sessionId.getAndSet(null)
                        if(sid!=null) try { py.callAttr("environment_terminal_close",sid,id) } catch(_:Exception) {}
                        executor.shutdown()
                    }
                } catch(_:Exception) { executor.shutdownNow() }
            }
            web.loadUrl("file:///android_asset/terminal/index.html")
        }
    }

    private fun restartDomEngine() {
        val detail = com.nemap.android.core.DomHealth.summary()
        sheet("Redémarrer le moteur DOM ?", "Le navigateur intégré sera reconstruit. Les données, cookies et discussions sont conservés ; aucun message ne sera renvoyé automatiquement. État : $detail") { body, dialog ->
            body.addView(action("Redémarrer", primary, white) {
                com.nemap.android.EmbeddedRuntime.restartDom()
                dialog.dismiss(); toast("Moteur DOM redémarré")
            }); body.addView(gap(8))
            body.addView(action("Annuler", Color.TRANSPARENT, muted) { dialog.dismiss() })
        }
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
        sheet("Environnement SSH", "Hôte DNS/IP, hôte:port, utilisateur@hôte ou URL ssh://utilisateur@hôte:port. Une machine hors réseau local fonctionne si son SSH est joignable (DNS public/VPN/port ouvert). L’empreinte est mémorisée à la première connexion. GitHub Codespaces nécessite son tunnel GitHub + clé/agent SSH : ce connecteur direct par mot de passe ne peut pas joindre un Codespace à lui seul.") { body, dialog ->
            val host=formField("Hôte ou URL SSH",old.optString("host","")); body.addView(host); body.addView(gap(8))
            val port=formField("Port",old.optInt("port",22).toString()); body.addView(port); body.addView(gap(8))
            val user=formField("Utilisateur",old.optString("user","")); body.addView(user); body.addView(gap(8))
            val password=formField("Mot de passe SSH",secret=true); body.addView(password); body.addView(gap(14))
            body.addView(action("Enregistrer",primary,white) {
                try { SshBridge.save(host.text.toString(),port.text.toString().toInt(),user.text.toString(),"",password.text.toString()); dialog.dismiss(); toast("SSH enregistré"); showSettings() } catch(e:Exception) { toast(e.message?:"Configuration invalide") }
            }); body.addView(gap(8))
            if(old.optBoolean("configured")) {
                body.addView(action("Tester la connexion",pale,primary) {
                    toast("Test SSH en cours…")
                    Thread {
                        val result=try { JSONObject(SshBridge.testConnection()) } catch(e:Exception) { JSONObject().put("success",false).put("error",e.message ?: "Erreur") }
                        runOnUiThread {
                            if(!isFinishing && !isDestroyed) {
                                if(result.optBoolean("success")) toast("SSH joignable : ${result.optString("user")}@${result.optString("host")}:${result.optInt("port")} · ${result.optString("fingerprint")}")
                                else toast(result.optString("error","Connexion impossible"))
                            }
                        }
                    }.start()
                }); body.addView(gap(8))
            }
            body.addView(action("Effacer la configuration",Color.TRANSPARENT,danger) { SshBridge.clear(); dialog.dismiss(); showSettings() })
        }
    }
    private fun showTokenHarborSettings() {
        val cfg = runCatching { JSONObject(py.callAttr("provider_get_config").toString()) }.getOrElse { JSONObject() }
        val th = cfg.optJSONObject("tokenharbor") ?: JSONObject()
        val baseUrl = th.optString("base_url", "https://tokenharbor.ai/v1")
        val currentModel = th.optString("model", "")
        val keysArr = th.optJSONArray("keys") ?: org.json.JSONArray()
        val maskedArr = th.optJSONArray("keys_masked") ?: org.json.JSONArray()

        sheet("TokenHarbor", "Passerelle OpenAI-compatible multi-modèles. Contrairement à NemApi, chaque requête renvoie tout l'historique de la discussion. Plusieurs clés permettent le routage automatique quand le quota d'une clé est épuisé (429).") { body, dialog ->
            body.addView(label("URL de base",13f,muted,true)); body.addView(gap(6))
            val urlField = formField("https://tokenharbor.ai/v1", baseUrl); body.addView(urlField); body.addView(gap(12))

            body.addView(label("Clés API (${keysArr.length()})",13f,muted,true)); body.addView(gap(6))
            if(keysArr.length() == 0) {
                body.addView(label("Aucune clé enregistrée.",13f,muted)); body.addView(gap(6))
            } else {
                for(i in 0 until keysArr.length()) {
                    val row = LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(dp(10),dp(8),dp(10),dp(8)); background=shape(pale,10,line) }
                    row.addView(label(maskedArr.optString(i, "…"),13f,strong), LinearLayout.LayoutParams(0,-2,1f))
                    row.addView(action("Supprimer",Color.TRANSPARENT,danger) {
                        val kept = org.json.JSONArray()
                        val allKeys = th.optJSONArray("keys") ?: org.json.JSONArray()
                        for(j in 0 until allKeys.length()) if(j != i) kept.put(allKeys.optString(j))
                        try {
                            val res = JSONObject(py.callAttr("tokenharbor_save_config", kept.toString(), currentModel, urlField.text.toString()).toString())
                            if(res.optBoolean("ok")) { dialog.dismiss(); showTokenHarborSettings() } else toast(res.optString("error","Suppression impossible"))
                        } catch(e:Exception) { toast("Erreur : ${e.message}") }
                    })
                    body.addView(row); body.addView(gap(6))
                }
            }
            body.addView(gap(6))
            body.addView(label("Ajouter une clé",13f,muted,true)); body.addView(gap(6))
            val newKey = formField("thk_live_…", secret=true); body.addView(newKey); body.addView(gap(12))

            body.addView(label("Modèle actuel",13f,muted,true)); body.addView(gap(4))
            body.addView(label(if(currentModel.isBlank()) "(non défini)" else currentModel,14f,primary,true)); body.addView(gap(10))

            body.addView(action("Charger les modèles disponibles",pale,primary) {
                toast("Chargement des modèles…")
                Thread {
                    val result = try { JSONObject(py.callAttr("tokenharbor_list_models").toString()) } catch(e:Exception) { JSONObject().put("ok",false).put("error",e.message) }
                    runOnUiThread {
                        if(isFinishing || isDestroyed) return@runOnUiThread
                        if(!result.optBoolean("ok")) { toast(result.optString("error","Chargement impossible")); return@runOnUiThread }
                        val models = result.optJSONArray("models") ?: org.json.JSONArray()
                        val ids = (0 until models.length()).map { models.optJSONObject(it)?.optString("id","") ?: "" }.filter { it.isNotBlank() }
                        if(ids.isEmpty()) { toast("Aucun modèle retourné"); return@runOnUiThread }
                        sheet("Sélection du modèle","Modèle TokenHarbor actif pour toutes les discussions.") { b2, d2 ->
                            ids.forEach { id ->
                                b2.addView(action(id, if(id==currentModel) pale else Color.TRANSPARENT, primary) {
                                    val allKeys = th.optJSONArray("keys") ?: org.json.JSONArray()
                                    try {
                                        val res = JSONObject(py.callAttr("tokenharbor_save_config", allKeys.toString(), id, urlField.text.toString()).toString())
                                        if(res.optBoolean("ok")) { d2.dismiss(); dialog.dismiss(); showTokenHarborSettings(); toast("Modèle : $id") } else toast(res.optString("error","Échec"))
                                    } catch(e:Exception) { toast("Erreur : ${e.message}") }
                                }); b2.addView(gap(4))
                            }
                        }
                    }
                }.start()
            }); body.addView(gap(10))

            body.addView(action("Tester la connexion",pale,primary) {
                toast("Test TokenHarbor…")
                Thread {
                    val result = try { JSONObject(py.callAttr("tokenharbor_test_connection").toString()) } catch(e:Exception) { JSONObject().put("ok",false).put("error",e.message) }
                    runOnUiThread { if(!isFinishing && !isDestroyed) toast(if(result.optBoolean("ok")) "Connexion TokenHarbor : OK" else result.optString("error","Connexion impossible")) }
                }.start()
            }); body.addView(gap(10))

            body.addView(action("Enregistrer",primary,white) {
                try {
                    val allKeys = th.optJSONArray("keys") ?: org.json.JSONArray()
                    val updated = org.json.JSONArray()
                    for(j in 0 until allKeys.length()) updated.put(allKeys.optString(j))
                    val nk = newKey.text.toString().trim()
                    if(nk.isNotBlank()) updated.put(nk)
                    val res = JSONObject(py.callAttr("tokenharbor_save_config", updated.toString(), currentModel, urlField.text.toString()).toString())
                    if(res.optBoolean("ok")) { dialog.dismiss(); showSettings(); toast("Configuration TokenHarbor enregistrée") } else toast(res.optString("error","Échec enregistrement"))
                } catch(e:Exception) { toast("Erreur : ${e.message}") }
            }); body.addView(gap(8))
        }
    }
    private fun toast(message:String) { Toast.makeText(this,message.take(300),Toast.LENGTH_LONG).show() }
}
