package org.ivanivorontsov.stroka.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import org.ivanivorontsov.stroka.BuildConfig
import org.ivanivorontsov.stroka.R
import org.ivanivorontsov.stroka.core.Completer
import org.ivanivorontsov.stroka.core.KeyAction
import org.ivanivorontsov.stroka.core.Lang
import org.ivanivorontsov.stroka.core.Templates
import org.ivanivorontsov.stroka.core.TextSearch
import org.ivanivorontsov.stroka.core.VoiceAction
import org.ivanivorontsov.stroka.core.VoiceMapper
import org.ivanivorontsov.stroka.files.DocRef
import org.ivanivorontsov.stroka.files.FNode
import org.ivanivorontsov.stroka.files.FileIO
import org.ivanivorontsov.stroka.files.LocalNode
import org.ivanivorontsov.stroka.files.SafNode
import org.ivanivorontsov.stroka.git.GitOps
import org.ivanivorontsov.stroka.run.JsRunner
import org.ivanivorontsov.stroka.run.PythonRunner
import org.ivanivorontsov.stroka.voice.VoiceInput
import java.io.File
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), CodeEditor.Listener, ExtraKeysView.Listener, VoiceInput.Listener {

    private lateinit var prefs: Prefs
    private lateinit var drawer: DrawerLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tabs: TabLayout
    private lateinit var editor: CodeEditor
    private lateinit var empty: TextView
    private lateinit var extraKeys: ExtraKeysView
    private lateinit var completions: LinearLayout
    private lateinit var completionScroll: HorizontalScrollView
    private lateinit var treeAdapter: FileTreeAdapter
    private lateinit var findBar: View
    private lateinit var findText: EditText
    private lateinit var replaceText: EditText
    private lateinit var findCount: TextView
    private lateinit var optRegex: CheckBox
    private lateinit var optCase: CheckBox
    private lateinit var optWord: CheckBox
    private lateinit var panel: View
    private lateinit var consoleText: TextView
    private lateinit var consoleScroll: ScrollView
    private lateinit var inputRow: View
    private lateinit var consoleInput: EditText
    private lateinit var panelStatus: TextView
    private lateinit var panelTabs: MaterialButtonToggleGroup
    private lateinit var consoleBox: View
    private lateinit var previewBox: FrameLayout
    private lateinit var voicePanel: View
    private lateinit var voiceStatus: TextView
    private lateinit var voicePartial: TextView
    private lateinit var voiceLast: TextView
    private lateinit var voiceLangBtn: Button

    private val docs = ArrayList<Doc>()
    private var current = -1
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val gitIo = Executors.newSingleThreadExecutor()
    private var suppressTabEvents = false
    private val consoleBuf = SpannableStringBuilder()
    private var preview: WebView? = null
    private var previewDoc: Doc? = null
    private lateinit var python: PythonRunner
    private lateinit var js: JsRunner
    private lateinit var voice: VoiceInput
    private val git = GitOps { line -> main.post { appendConsole(line + "\n", Kind.INFO) } }

    private enum class Kind { OUT, ERR, INFO, INPUT }

    private val autosaveTask = Runnable { if (prefs.autosave) saveCurrent(silent = true) }
    private val completionTask = Runnable { updateCompletions() }
    private val previewTask = Runnable { refreshPreview() }
    private val findTask = Runnable { updateFindMatches() }

    private val openTree = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) addSafRoot(uri) }
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startVoice() else toast(getString(R.string.mic_permission))
    }

    private val projectsDir: File by lazy { File(filesDir, "projects").also { it.mkdirs() } }
    private val sessionDir: File by lazy { File(filesDir, "session").also { it.mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)
        python = PythonRunner(this)
        js = JsRunner(this)
        voice = VoiceInput(this, this)
        bindViews()
        setupInsets()
        setupToolbar()
        setupDrawer()
        setupFindBar()
        setupPanel()
        setupVoicePanel()
        applyPrefs()
        restoreSession()
        handleIntent(intent)
        setupBack()
    }

    private fun bindViews() {
        drawer = findViewById(R.id.drawer)
        toolbar = findViewById(R.id.toolbar)
        tabs = findViewById(R.id.tabs)
        editor = findViewById(R.id.editor)
        empty = findViewById(R.id.empty)
        extraKeys = findViewById(R.id.extraKeys)
        completions = findViewById(R.id.completions)
        completionScroll = findViewById(R.id.completionScroll)
        findBar = findViewById(R.id.findBar)
        findText = findViewById(R.id.findText)
        replaceText = findViewById(R.id.replaceText)
        findCount = findViewById(R.id.findCount)
        optRegex = findViewById(R.id.optRegex)
        optCase = findViewById(R.id.optCase)
        optWord = findViewById(R.id.optWord)
        panel = findViewById(R.id.panel)
        consoleText = findViewById(R.id.consoleText)
        consoleScroll = findViewById(R.id.consoleScroll)
        inputRow = findViewById(R.id.inputRow)
        consoleInput = findViewById(R.id.consoleInput)
        panelStatus = findViewById(R.id.panelStatus)
        panelTabs = findViewById(R.id.panelTabs)
        consoleBox = findViewById(R.id.consoleBox)
        previewBox = findViewById(R.id.previewBox)
        voicePanel = findViewById(R.id.voicePanel)
        voiceStatus = findViewById(R.id.voiceStatus)
        voicePartial = findViewById(R.id.voicePartial)
        voiceLast = findViewById(R.id.voiceLast)
        voiceLangBtn = findViewById(R.id.voiceLang)
        editor.listener = this
        extraKeys.listener = this
    }

    private fun setupInsets() {
        val mainView = findViewById<View>(R.id.main)
        val drawerPanel = findViewById<View>(R.id.drawerPanel)
        val toolbarTop = toolbar.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            toolbar.updatePadding(top = toolbarTop + bars.top)
            v.updatePadding(left = bars.left, right = bars.right, bottom = maxOf(bars.bottom, ime.bottom))
            WindowInsetsCompat.CONSUMED
        }
        ViewCompat.setOnApplyWindowInsetsListener(drawerPanel) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.updatePadding(top = bars.top, bottom = bars.bottom, left = bars.left)
            insets
        }
    }

    private fun setupToolbar() {
        toolbar.inflateMenu(R.menu.main)
        toolbar.setNavigationOnClickListener { drawer.openDrawer(GravityCompat.START) }
        toolbar.setOnMenuItemClickListener { onMenu(it.itemId) }
        tabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) { if (!suppressTabEvents) switchTo(tab.position) }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun onMenu(id: Int): Boolean {
        when (id) {
            R.id.action_undo -> editor.undo()
            R.id.action_redo -> editor.redo()
            R.id.action_run -> runCurrent()
            R.id.action_save -> saveCurrent(silent = false)
            R.id.action_find -> showFind(null)
            R.id.action_goto -> askGoToLine()
            R.id.action_new_file -> askNewFile(null)
            R.id.action_template -> askTemplate()
            R.id.action_git -> showGitMenu()
            R.id.action_console -> showPanel(preview = false)
            R.id.action_preview -> openPreview()
            R.id.action_comment -> editor.toggleComment()
            R.id.action_wrap -> { prefs.wrap = !prefs.wrap; editor.setWrap(prefs.wrap); updateMenuChecks() }
            R.id.action_theme -> toggleTheme()
            R.id.action_voice_help -> showVoiceHelp()
            R.id.action_close -> if (current >= 0) closeDoc(current)
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.action_about -> showAbout()
            else -> return false
        }
        return true
    }

    private fun isNight(): Boolean = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    private fun toggleTheme() {
        saveSession()
        prefs.sp.edit().putString("theme", if (isNight()) "light" else "dark").apply()
        prefs.applyTheme()
    }

    private fun updateMenuChecks() {
        toolbar.menu.findItem(R.id.action_wrap)?.isChecked = prefs.wrap
        toolbar.menu.findItem(R.id.action_theme)?.isChecked = isNight()
    }

    private fun applyPrefs() {
        editor.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.fontSize)
        editor.showLineNumbers = prefs.lineNumbers
        editor.setWrap(prefs.wrap)
        editor.indentUnit = prefs.indentUnit
        editor.autoPair = prefs.autoPair
        editor.autoIndent = prefs.autoIndent
        extraKeys.setKeys(prefs.extraKeys)
        extraKeys.haptics = prefs.haptics
        completionScroll.visibility = if (prefs.autocomplete) View.VISIBLE else View.GONE
        Insets.preferHighRefresh(this, prefs.highRefresh)
        updateMenuChecks()
    }

    override fun onResume() {
        super.onResume()
        applyPrefs()
    }

    override fun onPause() {
        super.onPause()
        if (prefs.autosave) saveAllDirty()
        saveSession()
        if (voice.isActive) stopVoice()
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.release()
        js.release()
        preview?.destroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val uri = i?.data ?: return
        if (i.action != Intent.ACTION_VIEW && i.action != Intent.ACTION_EDIT) return
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
        }
        var name = "файл.txt"
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) name = c.getString(0) ?: name
            }
        } catch (e: Exception) {
        }
        openRef(DocRef.Saf(uri.toString(), name))
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(GravityCompat.START) -> drawer.closeDrawer(GravityCompat.START)
                    voice.isActive || voicePanel.visibility == View.VISIBLE -> stopVoice()
                    findBar.visibility == View.VISIBLE -> hideFind()
                    panel.visibility == View.VISIBLE -> hidePanel()
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })
    }

    private fun roots(): List<FNode> {
        val list = ArrayList<FNode>()
        list.add(LocalNode(projectsDir, "Проекты (память приложения)"))
        for (s in prefs.safRoots) {
            val uri = Uri.parse(s)
            val df = DocumentFile.fromTreeUri(this, uri) ?: continue
            list.add(SafNode(this, df, "📂 " + SafNode.displayName(this, uri), uri))
        }
        return list
    }

    private fun setupDrawer() {
        val rv = findViewById<RecyclerView>(R.id.tree)
        rv.layoutManager = LinearLayoutManager(this)
        treeAdapter = FileTreeAdapter({ node ->
            node.ref()?.let { openRef(it) }
            drawer.closeDrawer(GravityCompat.START)
        }) { node, anchor, isRoot -> showNodeMenu(node, anchor, isRoot) }
        rv.adapter = treeAdapter
        treeAdapter.restoreExpanded(prefs.expanded)
        treeAdapter.setRoots(roots(), expandFirst = prefs.expanded.isEmpty())
        findViewById<Button>(R.id.btnNewProject).setOnClickListener { askTemplate() }
        findViewById<Button>(R.id.btnOpenFolder).setOnClickListener {
            try { openTree.launch(null) } catch (e: Exception) { toast("Не удалось открыть выбор папки") }
        }
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                hideKeyboard()
                treeAdapter.refresh()
            }
        })
    }

    private fun addSafRoot(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
            toast("Нет постоянного доступа к папке: ${e.message}")
        }
        val list = prefs.safRoots.toMutableList()
        if (uri.toString() !in list) list.add(uri.toString())
        prefs.safRoots = list
        treeAdapter.setRoots(roots(), expandFirst = false)
        drawer.openDrawer(GravityCompat.START)
    }

    private fun showNodeMenu(node: FNode, anchor: View, isRoot: Boolean) {
        val pm = PopupMenu(this, anchor)
        if (node.isDir) {
            pm.menu.add(0, 1, 0, "Новый файл")
            pm.menu.add(0, 2, 0, "Новая папка")
        }
        if (!isRoot) {
            pm.menu.add(0, 3, 0, "Переименовать")
            pm.menu.add(0, 4, 0, "Удалить")
        }
        if (isRoot && node is SafNode) pm.menu.add(0, 5, 0, "Убрать из списка")
        if (node.isDir && node.localDir != null) pm.menu.add(0, 6, 0, "Git: клонировать сюда")
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> askNewFile(node)
                2 -> askName("Новая папка", "") { n -> runIo({ node.createDir(n) }) { treeAdapter.refresh() } }
                3 -> askName("Переименовать", node.name) { n ->
                    runIo({ node.rename(n) }) { ok -> if (ok != true) toast("Не удалось переименовать"); treeAdapter.refresh() }
                }
                4 -> MaterialAlertDialogBuilder(this).setTitle("Удалить «${node.name}»?")
                    .setMessage(if (node.isDir) "Папка будет удалена вместе со всем содержимым." else "Файл будет удалён без возможности восстановления.")
                    .setPositiveButton("Удалить") { _, _ ->
                        val key = node.key
                        runIo({ node.delete() }) { ok ->
                            if (ok != true) toast("Не удалось удалить")
                            docs.indices.reversed().filter { docs[it].ref.key == key || docs[it].ref.key.startsWith("$key/") }.forEach { closeDoc(it, force = true) }
                            treeAdapter.refresh()
                        }
                    }.setNegativeButton("Отмена", null).show()
                5 -> {
                    val sn = node as SafNode
                    prefs.safRoots = prefs.safRoots.filter { it != sn.treeUri.toString() }
                    try { sn.treeUri?.let { contentResolver.releasePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) } } catch (e: Exception) {}
                    treeAdapter.setRoots(roots(), expandFirst = false)
                }
                6 -> askClone(node.localDir!!)
            }
            true
        }
        pm.show()
    }

    private fun <T> runIo(work: () -> T, done: (T?) -> Unit) {
        io.execute {
            val r = try { work() } catch (e: Exception) { main.post { toast(e.message ?: e.toString()) }; null }
            main.post { done(r) }
        }
    }

    private fun askName(title: String, initial: String, hint: String = "", onOk: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial)
            setSelection(initial.substringBeforeLast('.', initial).length)
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(true)
        }
        val box = FrameLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input)
        }
        val dlg = MaterialAlertDialogBuilder(this).setTitle(title).setView(box)
            .setPositiveButton("ОК") { _, _ ->
                val n = input.text.toString().trim()
                if (n.isEmpty() || n == "." || n == "..") toast("Недопустимое имя") else onOk(n)
            }
            .setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            input.requestFocus()
            input.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0) }, 150)
        }
        dlg.show()
    }

    private fun currentDir(): FNode {
        val d = docs.getOrNull(current)
        val r = d?.ref
        if (r is DocRef.Local) File(r.path).parentFile?.let { return LocalNode(it) }
        return LocalNode(File(projectsDir, "Песочница").also { it.mkdirs() })
    }

    private fun askNewFile(parent: FNode?) {
        val dir = parent ?: currentDir()
        askName("Новый файл в «${dir.name}»", "", "например main.py") { n ->
            if (n.contains('/') && dir !is LocalNode) { toast("Недопустимое имя"); return@askName }
            runIo({ dir.createFile(n) }) { node ->
                treeAdapter.refresh()
                node?.ref()?.let { openRef(it) } ?: toast("Не удалось создать файл")
            }
        }
    }

    private fun askTemplate() {
        val names = Templates.all.map { it.title }.toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle(R.string.from_template).setItems(names) { _, which ->
            val t = Templates.all[which]
            var base = t.id
            var n = 1
            while (File(projectsDir, base).exists()) { n++; base = "${t.id}$n" }
            askName("Имя проекта", base) { name ->
                val dir = File(projectsDir, name)
                if (dir.exists()) { toast("Такая папка уже есть"); return@askName }
                runIo({
                    dir.mkdirs()
                    for ((f, content) in t.files) File(dir, f).also { it.parentFile?.mkdirs() }.writeText(content)
                    true
                }) {
                    treeAdapter.restoreExpanded(listOf("file:" + dir.absolutePath))
                    treeAdapter.refresh()
                    openRef(DocRef.Local(File(dir, t.main).absolutePath))
                    drawer.closeDrawer(GravityCompat.START)
                }
            }
        }.show()
    }

    private fun restoreSession() {
        val refs = prefs.openDocs.mapNotNull { DocRef.decode(it) }
        for ((i, r) in refs.withIndex()) {
            try {
                val draft = File(sessionDir, "$i.txt")
                val text = if (draft.exists()) draft.readText() else FileIO.read(this, r)
                val d = Doc(r, text)
                if (draft.exists()) d.dirty = true
                docs.add(d)
            } catch (e: Exception) {
            }
        }
        sessionDir.listFiles()?.forEach { it.delete() }
        if (docs.isEmpty()) {
            val sandbox = File(projectsDir, "Песочница").also { it.mkdirs() }
            val f = File(sandbox, "main.py")
            if (!f.exists()) f.writeText(WELCOME)
            docs.add(Doc(DocRef.Local(f.absolutePath), f.readText()))
        }
        rebuildTabs()
        switchTo(prefs.activeDoc.coerceIn(0, docs.size - 1), saveOld = false)
    }

    private fun saveSession() {
        if (current in docs.indices) captureEditor(docs[current])
        prefs.openDocs = docs.map { it.ref.encode() }
        prefs.activeDoc = current.coerceAtLeast(0)
        prefs.expanded = treeAdapter.expandedKeys()
        sessionDir.listFiles()?.forEach { it.delete() }
        docs.forEachIndexed { i, d -> if (d.dirty) try { File(sessionDir, "$i.txt").writeText(d.text) } catch (e: Exception) {} }
    }

    private fun captureEditor(d: Doc) {
        d.text = editor.text?.toString() ?: ""
        d.selStart = editor.selectionStart
        d.selEnd = editor.selectionEnd
        d.scrollY = editor.scrollY
    }

    fun openRef(ref: DocRef) {
        val idx = docs.indexOfFirst { it.ref.key == ref.key }
        if (idx >= 0) { selectTab(idx); return }
        runIo({ FileIO.read(this, ref) }) { text ->
            if (text == null) return@runIo
            docs.add(Doc(ref, text))
            rebuildTabs()
            selectTab(docs.size - 1)
        }
    }

    private fun selectTab(i: Int) {
        if (i != current) switchTo(i)
        tabs.post { tabs.getTabAt(i)?.let { suppressTabEvents = true; it.select(); suppressTabEvents = false } }
    }

    private fun switchTo(i: Int, saveOld: Boolean = true) {
        if (i !in docs.indices) { showEmpty(); return }
        if (saveOld && current in docs.indices && current != i) captureEditor(docs[current])
        current = i
        val d = docs[i]
        empty.visibility = View.GONE
        editor.visibility = View.VISIBLE
        editor.setDocument(d.text, d.undo, d.lang, d.selStart, d.selEnd, d.scrollY)
        suppressTabEvents = true
        tabs.getTabAt(i)?.select()
        suppressTabEvents = false
        treeAdapter.activeKey = d.ref.key
        updateTitle()
        if (findBar.visibility == View.VISIBLE) scheduleFind()
        updateCompletions()
    }

    private fun showEmpty() {
        current = -1
        editor.visibility = View.GONE
        empty.visibility = View.VISIBLE
        toolbar.title = getString(R.string.app_name)
        toolbar.subtitle = null
        completions.removeAllViews()
    }

    private fun rebuildTabs() {
        suppressTabEvents = true
        tabs.removeAllTabs()
        for ((i, d) in docs.withIndex()) {
            val tab = tabs.newTab()
            val v = LayoutInflater.from(this).inflate(R.layout.tab_item, tabs, false)
            v.findViewById<TextView>(R.id.tabTitle).text = tabLabel(d)
            v.findViewById<ImageView>(R.id.tabClose).setOnClickListener { closeDoc(docs.indexOf(d)) }
            tab.customView = v
            tabs.addTab(tab, i == current)
        }
        suppressTabEvents = false
    }

    private fun tabLabel(d: Doc) = (if (d.dirty) "● " else "") + d.name

    private fun refreshTabLabel(i: Int) {
        val v = tabs.getTabAt(i)?.customView ?: return
        v.findViewById<TextView>(R.id.tabTitle).text = tabLabel(docs[i])
    }

    private fun closeDoc(i: Int, force: Boolean = false) {
        if (i !in docs.indices) return
        if (i == current) captureEditor(docs[i])
        val d = docs[i]
        if (d.dirty && !force) {
            MaterialAlertDialogBuilder(this).setTitle("Сохранить «${d.name}»?")
                .setPositiveButton("Сохранить") { _, _ -> saveDoc(d, silent = false) { ok -> if (ok) closeDoc(docs.indexOf(d), true) } }
                .setNeutralButton("Не сохранять") { _, _ -> closeDoc(docs.indexOf(d), true) }
                .setNegativeButton("Отмена", null).show()
            return
        }
        docs.removeAt(i)
        if (previewDoc === d) previewDoc = null
        val next = when {
            docs.isEmpty() -> -1
            i == current -> minOf(i, docs.size - 1)
            i < current -> current - 1
            else -> current
        }
        current = -1
        rebuildTabs()
        if (next >= 0) switchTo(next, saveOld = false) else showEmpty()
    }

    private fun updateTitle() {
        val d = docs.getOrNull(current) ?: return
        toolbar.title = (if (d.dirty) "● " else "") + d.name
        val (l, c) = editor.currentLineCol()
        toolbar.subtitle = "${d.lang.title} · $l:$c"
    }

    private fun saveCurrent(silent: Boolean) {
        val d = docs.getOrNull(current) ?: return
        captureEditor(d)
        if (!d.dirty && silent) return
        saveDoc(d, silent) {}
    }

    private fun saveAllDirty() {
        if (current in docs.indices) captureEditor(docs[current])
        for (d in docs) if (d.dirty) saveDoc(d, silent = true) {}
    }

    private fun saveDoc(d: Doc, silent: Boolean, done: (Boolean) -> Unit) {
        val text = d.text
        d.dirty = false
        refreshTabLabel(docs.indexOf(d))
        updateTitle()
        io.execute {
            val err = try { FileIO.write(this, d.ref, text); null } catch (e: Exception) { e.message ?: e.toString() }
            main.post {
                if (err != null) {
                    d.dirty = true
                    refreshTabLabel(docs.indexOf(d))
                    updateTitle()
                    toast("Не удалось сохранить: $err")
                    done(false)
                } else {
                    if (!silent) toast("Сохранено: ${d.name}")
                    done(true)
                }
            }
        }
    }

    override fun onEdited() {
        val d = docs.getOrNull(current) ?: return
        if (!d.dirty) {
            d.dirty = true
            refreshTabLabel(current)
        }
        updateTitle()
        main.removeCallbacks(autosaveTask)
        main.postDelayed(autosaveTask, 1500)
        main.removeCallbacks(completionTask)
        main.postDelayed(completionTask, 70)
        if (findBar.visibility == View.VISIBLE) scheduleFind()
        if (preview != null && panel.visibility == View.VISIBLE && previewBox.visibility == View.VISIBLE) {
            main.removeCallbacks(previewTask)
            main.postDelayed(previewTask, 900)
        }
    }

    override fun onCursorMoved() {
        if (current < 0) return
        updateTitle()
        main.removeCallbacks(completionTask)
        main.postDelayed(completionTask, 90)
    }

    override fun onFontSizeChanged(sp: Float) {
        prefs.fontSize = sp
    }

    private fun updateCompletions() {
        completions.removeAllViews()
        if (!prefs.autocomplete || current < 0) return
        val t = editor.text ?: return
        val cur = editor.selectionStart
        if (cur < 0 || cur != editor.selectionEnd) return
        val lang = docs[current].lang
        val list = Completer.suggest(t, cur, lang)
        for (w in list) {
            val chip = TextView(this)
            chip.text = w
            chip.typeface = Typeface.MONOSPACE
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            chip.gravity = Gravity.CENTER
            chip.setPadding(dp(14), 0, dp(14), 0)
            chip.setBackgroundResource(R.drawable.chip_bg)
            chip.setTextColor(resolveAttr(com.google.android.material.R.attr.colorOnSecondaryContainer))
            chip.setOnClickListener {
                val tt = editor.text ?: return@setOnClickListener
                val c = editor.selectionStart
                val p = Completer.prefixAt(tt, c, lang)
                var end = c
                while (end < tt.length && (tt[end].isLetterOrDigit() || tt[end] == '_')) end++
                editor.undoStack.breakGroup()
                tt.replace(c - p.length, end, w)
                editor.undoStack.breakGroup()
            }
            completions.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        if (list.isNotEmpty()) completionScroll.scrollTo(0, 0)
    }

    private fun resolveAttr(attr: Int): Int {
        val tv = TypedValue()
        theme.resolveAttribute(attr, tv, true)
        return tv.data
    }

    override fun onKey(action: KeyAction) {
        if (current < 0) return
        if (action != KeyAction.Voice && !editor.hasFocus()) editor.requestFocus()
        when (action) {
            is KeyAction.Insert -> editor.insertText(action.text)
            KeyAction.Tab -> editor.insertIndent()
            KeyAction.Outdent -> editor.outdent()
            KeyAction.Left -> editor.moveChars(-1)
            KeyAction.Right -> editor.moveChars(1)
            KeyAction.Up -> editor.moveLines(-1)
            KeyAction.Down -> editor.moveLines(1)
            KeyAction.Home -> editor.moveHome()
            KeyAction.End -> editor.moveEnd()
            KeyAction.Undo -> editor.undo()
            KeyAction.Redo -> editor.redo()
            KeyAction.Comment -> editor.toggleComment()
            KeyAction.Select -> { editor.selectMode = !editor.selectMode; extraKeys.setSelectActive(editor.selectMode) }
            KeyAction.Voice -> toggleVoice()
        }
    }

    override fun onCursorDrag(dx: Int, dy: Int) {
        if (current < 0) return
        if (dx != 0) editor.moveChars(dx)
        if (dy != 0) editor.moveLines(dy)
    }

    private fun setupFindBar() {
        findText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { scheduleFind() }
        })
        listOf(optRegex, optCase, optWord).forEach { it.setOnCheckedChangeListener { _, _ -> scheduleFind() } }
        findText.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEARCH) { findNext(false); true } else false }
        findViewById<ImageButton>(R.id.findNext).setOnClickListener { findNext(false) }
        findViewById<ImageButton>(R.id.findPrev).setOnClickListener { findNext(true) }
        findViewById<ImageButton>(R.id.findClose).setOnClickListener { hideFind() }
        findViewById<Button>(R.id.replaceOne).setOnClickListener { replaceOne() }
        findViewById<Button>(R.id.replaceAll).setOnClickListener { replaceAll() }
    }

    private fun query() = TextSearch.Query(findText.text.toString(), optRegex.isChecked, optCase.isChecked, optWord.isChecked)

    private fun showFind(initial: String?) {
        findBar.visibility = View.VISIBLE
        val a = minOf(editor.selectionStart, editor.selectionEnd)
        val b = maxOf(editor.selectionStart, editor.selectionEnd)
        val sel = if (a in 0 until b) editor.text?.subSequence(a, b)?.toString() else null
        val q = initial ?: sel?.takeIf { !it.contains('\n') && it.length < 200 }
        if (q != null) findText.setText(q)
        findText.requestFocus()
        findText.selectAll()
        scheduleFind()
    }

    private fun hideFind() {
        findBar.visibility = View.GONE
        editor.setSearchHighlights(emptyList())
        editor.requestFocus()
    }

    private fun scheduleFind() {
        main.removeCallbacks(findTask)
        main.postDelayed(findTask, 150)
    }

    private fun updateFindMatches() {
        if (findBar.visibility != View.VISIBLE || current < 0) return
        val q = query()
        val err = if (q.regex) TextSearch.validate(q) else null
        if (err != null) {
            findCount.text = "ошибка"
            editor.setSearchHighlights(emptyList())
            return
        }
        val t = editor.text ?: return
        val all = TextSearch.findAll(t, q)
        editor.setSearchHighlights(all)
        val a = minOf(editor.selectionStart, editor.selectionEnd)
        val b = maxOf(editor.selectionStart, editor.selectionEnd)
        val idx = all.indexOfFirst { it.first == a && it.last + 1 == b }
        findCount.text = if (q.text.isEmpty()) "" else if (all.isEmpty()) "0" else "${if (idx >= 0) (idx + 1).toString() else "–"}/${all.size}"
    }

    private fun findNext(back: Boolean) {
        val t = editor.text ?: return
        val q = query()
        if (q.text.isEmpty()) return
        val from = if (back) minOf(editor.selectionStart, editor.selectionEnd) else maxOf(editor.selectionStart, editor.selectionEnd)
        val r = TextSearch.findNext(t, q, from, back)
        if (r == null) { findCount.text = "0"; return }
        editor.setSelection(r.first, r.last + 1)
        editor.bringPointIntoView(r.first)
        updateFindMatches()
    }

    private fun replaceOne() {
        val t = editor.text ?: return
        val q = query()
        if (q.text.isEmpty()) return
        val a = minOf(editor.selectionStart, editor.selectionEnd)
        val b = maxOf(editor.selectionStart, editor.selectionEnd)
        val repl = if (b > a) TextSearch.replacementFor(t, q, a until b, replaceText.text.toString()) else null
        if (repl != null) {
            editor.undoStack.breakGroup()
            t.replace(a, b, repl)
            editor.undoStack.breakGroup()
            editor.setSelection(a + repl.length)
        }
        findNext(false)
    }

    private fun replaceAll() {
        val t = editor.text ?: return
        val q = query()
        if (q.text.isEmpty()) return
        val (res, n) = try { TextSearch.replaceAll(t, q, replaceText.text.toString()) } catch (e: Exception) { toast("Ошибка: ${e.message}"); return }
        if (n == 0) { toast("Ничего не найдено"); return }
        val cur = editor.selectionStart
        editor.undoStack.breakGroup()
        t.replace(0, t.length, res)
        editor.undoStack.breakGroup()
        editor.setSelection(cur.coerceIn(0, res.length))
        toast("Заменено: $n")
        updateFindMatches()
    }

    private fun askGoToLine() {
        if (current < 0) return
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            hint = "1 – ${editor.lineCount2}"
            setSingleLine(true)
        }
        val box = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
        val dlg = MaterialAlertDialogBuilder(this).setTitle(R.string.goto_line).setView(box)
            .setPositiveButton("Перейти") { _, _ -> input.text.toString().toIntOrNull()?.let { editor.goToLine(it) } }
            .setNegativeButton("Отмена", null).create()
        dlg.setOnShowListener {
            input.requestFocus()
            input.postDelayed({ (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0) }, 150)
        }
        dlg.show()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupPanel() {
        findViewById<ImageButton>(R.id.panelClose).setOnClickListener { hidePanel() }
        findViewById<ImageButton>(R.id.panelClear).setOnClickListener {
            if (previewBox.visibility == View.VISIBLE) refreshPreview() else { consoleBuf.clear(); consoleText.text = "" }
        }
        findViewById<ImageButton>(R.id.panelStop).setOnClickListener { stopRun() }
        findViewById<Button>(R.id.consoleSend).setOnClickListener { sendInput() }
        consoleInput.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_SEND || a == EditorInfo.IME_ACTION_DONE) { sendInput(); true } else false }
        panelTabs.addOnButtonCheckedListener { _, id, checked ->
            if (!checked) return@addOnButtonCheckedListener
            if (id == R.id.tabPreview) {
                consoleBox.visibility = View.GONE
                previewBox.visibility = View.VISIBLE
                if (preview == null) openPreview()
            } else {
                consoleBox.visibility = View.VISIBLE
                previewBox.visibility = View.GONE
            }
        }
        val handle = findViewById<View>(R.id.panelHandle)
        var startY = 0f
        var startH = 0
        handle.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { startY = e.rawY; startH = panel.height; true }
                MotionEvent.ACTION_MOVE -> {
                    val host = findViewById<View>(R.id.main).height
                    val h = (startH + (startY - e.rawY)).toInt().coerceIn(dp(110), (host * 0.8f).toInt())
                    panel.layoutParams = panel.layoutParams.apply { height = h }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { prefs.panelHeight = panel.height; true }
                else -> true
            }
        }
        if (prefs.panelHeight > 0) panel.layoutParams = panel.layoutParams.apply { height = prefs.panelHeight }
    }

    private fun showPanel(preview: Boolean) {
        panel.visibility = View.VISIBLE
        panelTabs.check(if (preview) R.id.tabPreview else R.id.tabConsole)
        if (!preview) {
            consoleBox.visibility = View.VISIBLE
            previewBox.visibility = View.GONE
        }
    }

    private fun hidePanel() {
        panel.visibility = View.GONE
    }

    private fun appendConsole(s: String, kind: Kind) {
        val start = consoleBuf.length
        consoleBuf.append(s)
        val color = when (kind) {
            Kind.ERR -> ContextCompat.getColor(this, R.color.console_err)
            Kind.INFO -> ContextCompat.getColor(this, R.color.console_info)
            Kind.INPUT -> ContextCompat.getColor(this, R.color.syn_string)
            Kind.OUT -> 0
        }
        if (color != 0) consoleBuf.setSpan(ForegroundColorSpan(color), start, consoleBuf.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (consoleBuf.length > 300_000) consoleBuf.delete(0, consoleBuf.length - 200_000)
        consoleText.setText(consoleBuf, TextView.BufferType.SPANNABLE)
        consoleScroll.post { consoleScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendInput() {
        val line = consoleInput.text.toString()
        consoleInput.setText("")
        appendConsole(line + "\n", Kind.INPUT)
        if (python.isRunning) python.provideInput(line)
    }

    private fun runCurrent() {
        val d = docs.getOrNull(current) ?: return
        captureEditor(d)
        if (prefs.autosave) saveAllDirty()
        val code = d.text
        val cwd = (d.ref as? DocRef.Local)?.let { File(it.path).parent } ?: File(projectsDir, "Песочница").absolutePath
        when (d.lang) {
            Lang.PYTHON -> runPython(code, d.name, cwd)
            Lang.JAVASCRIPT -> runJs(code, d.name)
            Lang.HTML, Lang.CSS, Lang.MARKDOWN -> openPreview()
            else -> {
                showPanel(false)
                appendConsole("Запуск ${d.lang.title} прямо на телефоне не поддерживается. Запускаются Python, JavaScript, HTML и Markdown (просмотр).\n", Kind.INFO)
            }
        }
    }

    private fun runPython(code: String, name: String, cwd: String) {
        if (python.isRunning || js.isRunning) { toast("Программа уже выполняется"); return }
        showPanel(false)
        hideKeyboard()
        appendConsole("▶ python $name\n", Kind.INFO)
        panelStatus.text = "Python выполняется…"
        inputRow.visibility = View.VISIBLE
        python.run(code, name, cwd, object : PythonRunner.Sink {
            override fun onOutput(text: String, error: Boolean) = appendConsole(text, if (error) Kind.ERR else Kind.OUT)
            override fun onInputRequested(prompt: String) {
                panelStatus.text = "Ждёт ввода…"
                consoleInput.requestFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(consoleInput, 0)
            }
            override fun onFinished(code: Int, millis: Long) {
                inputRow.visibility = View.GONE
                panelStatus.text = "Завершено: код $code, $millis мс"
                appendConsole("■ код выхода $code ($millis мс)\n", if (code == 0) Kind.INFO else Kind.ERR)
            }
        })
    }

    private fun runJs(code: String, name: String) {
        if (python.isRunning || js.isRunning) { toast("Программа уже выполняется"); return }
        showPanel(false)
        appendConsole("▶ js $name\n", Kind.INFO)
        panelStatus.text = "JavaScript выполняется…"
        js.run(code, name, object : JsRunner.Sink {
            override fun onOutput(text: String, error: Boolean) = appendConsole(text, if (error) Kind.ERR else Kind.OUT)
            override fun onPrompt(message: String, default: String, result: (String?) -> Unit) {
                val input = EditText(this@MainActivity).apply { setText(default); setSingleLine(true) }
                val box = FrameLayout(this@MainActivity).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(input) }
                var answered = false
                MaterialAlertDialogBuilder(this@MainActivity).setTitle(message.ifBlank { "Ввод" }).setView(box)
                    .setPositiveButton("ОК") { _, _ -> answered = true; result(input.text.toString()) }
                    .setNegativeButton("Отмена") { _, _ -> answered = true; result(null) }
                    .setOnDismissListener { if (!answered) result(null) }.show()
            }
            override fun onFinished(code: Int, millis: Long) {
                panelStatus.text = "Завершено: код $code, $millis мс"
                appendConsole("■ код выхода $code ($millis мс)\n", if (code == 0) Kind.INFO else Kind.ERR)
            }
        })
    }

    private fun stopRun() {
        if (python.isRunning) python.stop()
        if (js.isRunning) js.stop()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensurePreview(): WebView {
        preview?.let { return it }
        val w = WebView(this)
        w.settings.javaScriptEnabled = true
        w.settings.domStorageEnabled = true
        w.settings.allowFileAccess = true
        w.settings.allowContentAccess = true
        w.settings.builtInZoomControls = true
        w.settings.displayZoomControls = false
        w.webViewClient = object : WebViewClient() {}
        w.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(m: ConsoleMessage): Boolean {
                val err = m.messageLevel() == ConsoleMessage.MessageLevel.ERROR || m.messageLevel() == ConsoleMessage.MessageLevel.WARNING
                appendConsole("[страница] ${m.message()}" + (if (m.lineNumber() > 0) " (стр. ${m.lineNumber()})" else "") + "\n", if (err) Kind.ERR else Kind.OUT)
                return true
            }
        }
        previewBox.addView(w, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        preview = w
        return w
    }

    private fun pickPreviewTarget(): Doc? {
        val d = docs.getOrNull(current) ?: return previewDoc
        if (d.lang == Lang.HTML || d.lang == Lang.MARKDOWN) return d
        val r = d.ref
        if (r is DocRef.Local) {
            val dir = File(r.path).parentFile
            val p = previewDoc
            if (p != null && (p.ref as? DocRef.Local)?.let { File(it.path).parentFile == dir } == true) return p
            val idx = File(dir, "index.html")
            if (idx.exists()) {
                docs.firstOrNull { it.ref.key == DocRef.Local(idx.absolutePath).key }?.let { return it }
                return Doc(DocRef.Local(idx.absolutePath), idx.readText())
            }
        }
        return previewDoc
    }

    private fun openPreview() {
        val target = pickPreviewTarget()
        showPanel(preview = true)
        consoleBox.visibility = View.GONE
        previewBox.visibility = View.VISIBLE
        if (target == null) {
            panelStatus.text = "Откройте HTML-файл"
            return
        }
        previewDoc = target
        refreshPreview()
    }

    private fun refreshPreview() {
        val target = previewDoc ?: pickPreviewTarget() ?: return
        previewDoc = target
        if (docs.getOrNull(current) === target) captureEditor(target)
        val cur = docs.getOrNull(current)
        if (cur != null && cur !== target && cur.ref is DocRef.Local) {
            captureEditor(cur)
            if (cur.dirty) {
                saveDoc(cur, silent = true) { loadPreview(target) }
                return
            }
        }
        loadPreview(target)
    }

    private fun loadPreview(target: Doc) {
        val w = ensurePreview()
        val base = (target.ref as? DocRef.Local)?.let { "file://" + File(it.path).parent + "/" }
        val html = if (target.lang == Lang.MARKDOWN) MarkdownHtml.render(target.text, isNight()) else target.text
        panelStatus.text = "Просмотр: ${target.name}"
        w.loadDataWithBaseURL(base, html, "text/html", "utf-8", null)
    }

    private fun showGitMenu() {
        val d = docs.getOrNull(current)
        val start = (d?.ref as? DocRef.Local)?.let { File(it.path).parentFile }
        val repo = git.findRepoRoot(start)
        val items = ArrayList<Pair<String, () -> Unit>>()
        if (repo != null) {
            items.add("Статус (${repo.name})" to { gitTask("status") { git.status(repo) } })
            items.add("Получить (pull)" to { gitTask("pull") { git.pull(repo, creds()) } })
            items.add("Коммит всех изменений…" to { askCommit(repo, push = false) })
            items.add("Коммит и отправить (push)…" to { askCommit(repo, push = true) })
            items.add("Отправить (push)" to { gitTask("push") { git.push(repo, creds()) } })
        } else if (start != null && start.absolutePath.startsWith(projectsDir.absolutePath + "/")) {
            val projectRoot = generateSequence(start) { it.parentFile }.first { it.parentFile == projectsDir }
            items.add("Создать репозиторий в «${projectRoot.name}»…" to { askInit(projectRoot) })
        }
        items.add("Клонировать репозиторий…" to { askClone(projectsDir) })
        MaterialAlertDialogBuilder(this).setTitle(if (repo != null) "Git: ${repo.name}" else "Git")
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second() }
            .setNegativeButton("Закрыть", null).show()
    }

    private fun creds(): GitOps.Creds = GitOps.Creds(prefs.gitUser, SecureStore(this).get("github_token"), prefs.gitName, prefs.gitEmail)

    private fun gitTask(name: String, after: (() -> Unit)? = null, work: () -> Unit) {
        if (prefs.autosave) saveAllDirty()
        showPanel(false)
        appendConsole("▶ git $name\n", Kind.INFO)
        panelStatus.text = "git $name…"
        val token = SecureStore(this).get("github_token")
        gitIo.execute {
            try {
                work()
                main.post { panelStatus.text = "git $name: готово"; after?.invoke() }
            } catch (e: Throwable) {
                var msg = e.message ?: e.toString()
                if (!token.isNullOrEmpty()) msg = msg.replace(token, "***")
                main.post {
                    appendConsole("Ошибка: $msg\n", Kind.ERR)
                    if (msg.contains("not authorized", true) || msg.contains("Authentication", true) || msg.contains("401") || msg.contains("403")) {
                        appendConsole("Проверьте логин и токен в Настройки → Git и GitHub.\n", Kind.INFO)
                    }
                    panelStatus.text = "git $name: ошибка"
                }
            }
        }
    }

    private fun askCommit(repo: File, push: Boolean) {
        askName("Сообщение коммита", "", "что изменено") { msg ->
            gitTask(if (push) "commit + push" else "commit") {
                git.commit(repo, msg, creds())
                if (push) git.push(repo, creds())
            }
        }
    }

    private fun askInit(dir: File) {
        askName("Адрес на GitHub (можно пусто)", "https://github.com/${prefs.gitUser.ifBlank { "user" }}/${dir.name}.git") { url ->
            gitTask("init") { git.init(dir, url.takeIf { it.startsWith("http") }) }
        }
    }

    private fun askClone(parent: File) {
        askName("Адрес репозитория", "https://github.com/", "https://github.com/user/repo.git") { url ->
            val name = url.trimEnd('/').substringAfterLast('/').removeSuffix(".git").ifBlank { "repo" }
            var target = File(parent, name)
            var n = 1
            while (target.exists()) { n++; target = File(parent, "$name$n") }
            val t = target
            gitTask("clone", after = {
                treeAdapter.restoreExpanded(listOf("file:" + t.absolutePath))
                treeAdapter.refresh()
                drawer.openDrawer(GravityCompat.START)
            }) { git.clone(url, t, creds()) }
        }
    }

    private fun setupVoicePanel() {
        findViewById<ImageButton>(R.id.voiceClose).setOnClickListener { stopVoice() }
        findViewById<ImageButton>(R.id.voiceHelp).setOnClickListener { showVoiceHelp() }
        voiceLangBtn.text = prefs.voiceLang.uppercase()
        voiceLangBtn.setOnClickListener {
            prefs.voiceLang = if (prefs.voiceLang == "ru") "en" else "ru"
            voiceLangBtn.text = prefs.voiceLang.uppercase()
            if (voice.isActive) voice.start(prefs.voiceLang, prefs.voiceEngine)
        }
    }

    private fun toggleVoice() {
        if (voice.isActive) { stopVoice(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        startVoice()
    }

    private fun startVoice() {
        voicePanel.visibility = View.VISIBLE
        voiceStatus.text = "Запуск микрофона…"
        voicePartial.text = ""
        voiceLangBtn.text = prefs.voiceLang.uppercase()
        extraKeys.setMicActive(true)
        voice.start(prefs.voiceLang, prefs.voiceEngine)
    }

    private fun stopVoice() {
        voice.stop(silent = true)
        voicePanel.visibility = View.GONE
        extraKeys.setMicActive(false)
    }

    override fun onListening(engine: String) {
        voiceStatus.text = "Слушаю · $engine"
    }

    override fun onPartial(text: String) {
        voicePartial.text = text
    }

    override fun onFinal(text: String) {
        voicePartial.text = ""
        val actions = VoiceMapper.map(text)
        voiceLast.text = "«$text» → " + actions.joinToString(" ") { describe(it) }
        for (a in actions) applyVoice(a)
    }

    override fun onStopped() {
        voicePanel.visibility = View.GONE
        extraKeys.setMicActive(false)
    }

    override fun onError(message: String) {
        toast(message)
    }

    private fun describe(a: VoiceAction): String = when (a) {
        is VoiceAction.Insert -> "«${a.text}»"
        VoiceAction.NewLine -> "⏎"
        VoiceAction.Indent -> "⇥"
        VoiceAction.Outdent -> "⇤"
        VoiceAction.Save -> "[сохранить]"
        VoiceAction.Run -> "[запуск]"
        VoiceAction.Undo -> "[отменить]"
        VoiceAction.Redo -> "[повторить]"
        is VoiceAction.Find -> "[найти ${a.query}]"
        is VoiceAction.GoToLine -> "[строка ${a.line}]"
        else -> "[" + a.javaClass.simpleName + "]"
    }

    fun applyVoice(a: VoiceAction) {
        if (current < 0 && a !is VoiceAction.StopListening) return
        when (a) {
            is VoiceAction.Insert -> {
                val t = editor.text ?: return
                val c = minOf(editor.selectionStart, editor.selectionEnd).coerceAtLeast(0)
                val prev = if (c > 0) t[c - 1] else ' '
                val first = a.text.firstOrNull() ?: return
                val needSpace = (prev.isLetterOrDigit() || prev == ')' || prev == '"') && (first.isLetterOrDigit() || first == '"')
                editor.insertText(if (needSpace) " " + a.text else a.text)
            }
            VoiceAction.NewLine -> editor.insertText("\n")
            VoiceAction.Indent -> editor.insertIndent()
            VoiceAction.Outdent -> editor.outdent()
            VoiceAction.Save -> saveCurrent(silent = false)
            VoiceAction.Run -> runCurrent()
            VoiceAction.Undo -> editor.undo()
            VoiceAction.Redo -> editor.redo()
            VoiceAction.SelectAll -> editor.selectAll()
            VoiceAction.Copy -> editor.onTextContextMenuItem(android.R.id.copy)
            VoiceAction.Paste -> editor.onTextContextMenuItem(android.R.id.paste)
            VoiceAction.Cut -> editor.onTextContextMenuItem(android.R.id.cut)
            VoiceAction.Backspace -> editor.backspace()
            VoiceAction.DeleteWord -> editor.deleteWord()
            VoiceAction.DeleteLine -> editor.deleteLine()
            VoiceAction.LineStart -> editor.moveHome()
            VoiceAction.LineEnd -> editor.moveEnd()
            VoiceAction.Comment -> editor.toggleComment()
            VoiceAction.StopListening, VoiceAction.Close -> stopVoice()
            is VoiceAction.Find -> showFind(a.query.ifBlank { null })
            is VoiceAction.GoToLine -> editor.goToLine(a.line)
        }
    }

    private fun showVoiceHelp() {
        val tv = TextView(this)
        tv.text = VOICE_HELP
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(22), dp(12), dp(22), dp(12))
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        val sv = ScrollView(this)
        sv.addView(tv)
        MaterialAlertDialogBuilder(this).setTitle("Голосовой ввод").setView(sv).setPositiveButton("Понятно", null).show()
    }

    private fun showAbout() {
        MaterialAlertDialogBuilder(this).setTitle("Строка ${BuildConfig.VERSION_NAME}")
            .setMessage("Редактор кода для телефона.\n\nЗапуск: Python 3.12 (Chaquopy), JavaScript (WebView), просмотр HTML и Markdown.\nГолос: Google SpeechRecognizer и офлайн Vosk.\nGit: JGit, токен хранится в Android Keystore.\n\nПроекты лежат в памяти приложения. Чтобы работать с папкой на телефоне, нажмите «Папка…» в панели файлов.")
            .setPositiveButton("ОК", null).show()
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(editor.windowToken, 0)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val WELCOME = "print(\"Привет! Это «Строка» — редактор кода для телефона.\")\n\n\ndef square(n):\n    return n * n\n\n\nfor i in range(1, 6):\n    print(i, \"→\", square(i))\n\nname = input(\"Как тебя зовут? \")\nprint(f\"Рад знакомству, {name}!\")\n"

        private val VOICE_HELP = """
Нажмите 🎤 на панели над клавиатурой. Кнопка RU/EN переключает язык.

Символы (говорите словами):
скобка / скобка закрыть → ( )
фигурная скобка / фигурная закрыть → { }
квадратная скобка / квадратная закрыть → [ ]
кавычка → " (вторая закрывает), одинарная кавычка → '
точка с запятой ; · двоеточие : · запятая , · точка .
равно = · двойное равно == · не равно != · плюс, минус, звёздочка, слэш
больше > · меньше < · стрелка -> · жирная стрелка =>
подчёркивание _ · решётка # · пробел
новая строка — перенос с автоотступом
отступ / убрать отступ

English: open paren, close paren, open brace, close brace, open bracket, quote, semicolon, colon, comma, dot, equals, double equals, not equal, new line, tab, indent, underscore.

Имена: «кэмел user name» → userName, «снейк max speed» → max_speed, «капс max speed» → MAX_SPEED, «паскаль my class» → MyClass, «с большой буквы» — следующее слово с заглавной, «буквально точка» — вставить слово как есть.
Числа словами становятся цифрами: «двадцать пять» → 25. «икс», «игрек» → x, y.

Команды (отдельной фразой):
сохранить · запустить · отменить · повторить
найти <текст> · перейти к строке <номер>
выделить всё · копировать · вставить · вырезать
стереть · удалить слово · удалить строку
в начало строки · в конец строки · закомментировать
стоп — выключить микрофон
English: save, run, undo, redo, find <text>, go to line <n>, select all, copy, paste, delete line, stop.
Если фраза похожа на обычный текст, начните с «команда …».

Без интернета работает офлайн-модель Vosk: скачайте её в Настройки → Голосовой ввод.
""".trim()
    }
}
