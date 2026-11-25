package org.ivanivorontsov.stroka.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import org.json.JSONObject
import org.ivanivorontsov.stroka.R
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class GitHubActionsActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var repoInput: EditText
    private lateinit var branchInput: EditText
    private lateinit var workflows: Spinner
    private lateinit var output: TextView
    private lateinit var refreshButton: MaterialButton
    private lateinit var runButton: MaterialButton
    private data class Workflow(val id: Long, val name: String, val path: String) {
        override fun toString() = "$name · $path"
    }
    private var items = emptyList<Workflow>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_github_actions)
        findViewById<MaterialToolbar>(R.id.actionsToolbar).setNavigationOnClickListener { finish() }
        repoInput = findViewById(R.id.actionsRepo)
        branchInput = findViewById(R.id.actionsBranch)
        workflows = findViewById(R.id.actionsWorkflow)
        output = findViewById(R.id.actionsOutput)
        refreshButton = findViewById(R.id.actionsRefresh)
        runButton = findViewById(R.id.actionsRun)
        val prefs = getSharedPreferences("stroka", MODE_PRIVATE)
        repoInput.setText(prefs.getString("actions_repo", ""))
        branchInput.setText(prefs.getString("actions_branch", "main"))
        refreshButton.setOnClickListener { refresh() }
        runButton.setOnClickListener { dispatch() }
        refresh()
    }

    private fun token(): String = SecureStore(this).get("github_token").orEmpty()

    private fun request(method: String, path: String, body: JSONObject? = null): String = try {
        org.ivanivorontsov.stroka.git.GitHubApi(token()).request(method, path, body)
    } catch (e: org.ivanivorontsov.stroka.git.GitHubApiException) {
        throw IllegalStateException(e.message + ". Проверьте права токена: Actions — read/write, Contents — read.")
    }

    private fun repository(): String {
        val value = repoInput.text.toString().trim()
        if (!value.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) throw IllegalArgumentException("Введите репозиторий в формате owner/repo.")
        return value
    }

    private fun refresh() {
        val repo = try { repository() } catch (e: Exception) {
            output.text = e.message
            return
        }
        val branch = branchInput.text.toString().trim().ifBlank { "main" }
        getSharedPreferences("stroka", MODE_PRIVATE).edit().putString("actions_repo", repo).putString("actions_branch", branch).apply()
        setBusy(true)
        io.execute {
            try {
                val workflowJson = JSONObject(request("GET", "/repos/$repo/actions/workflows?per_page=100"))
                val arr = workflowJson.optJSONArray("workflows") ?: org.json.JSONArray()
                val found = ArrayList<Workflow>()
                for (i in 0 until arr.length()) {
                    val w = arr.optJSONObject(i) ?: continue
                    if (w.optString("state") == "active") found += Workflow(w.optLong("id"), w.optString("name"), w.optString("path"))
                }
                val runsJson = JSONObject(request("GET", "/repos/$repo/actions/runs?per_page=20"))
                val runs = runsJson.optJSONArray("workflow_runs") ?: org.json.JSONArray()
                val log = StringBuilder("Активные workflow: ${found.size}\n\nПоследние запуски:\n")
                for (i in 0 until runs.length()) {
                    val r = runs.optJSONObject(i) ?: continue
                    log.append("• ").append(r.optString("name")).append(" — ")
                        .append(r.optString("status")).append("/")
                        .append(r.optString("conclusion").ifBlank { "выполняется" })
                        .append(" · ").append(r.optString("head_branch")).append("\n")
                }
                main.post {
                    items = found
                    workflows.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
                    output.text = log.toString()
                    setBusy(false)
                }
            } catch (e: Exception) {
                main.post { output.text = "Ошибка: ${e.message}"; setBusy(false) }
            }
        }
    }

    private fun dispatch() {
        val repo = try { repository() } catch (e: Exception) { Toast.makeText(this, e.message, Toast.LENGTH_LONG).show(); return }
        val workflow = items.getOrNull(workflows.selectedItemPosition) ?: run {
            Toast.makeText(this, "Сначала обновите список workflow.", Toast.LENGTH_SHORT).show()
            return
        }
        val branch = branchInput.text.toString().trim().ifBlank { "main" }
        if (!branch.matches(Regex("[A-Za-z0-9_./-]+")) || branch.contains("..")) {
            Toast.makeText(this, "Недопустимое имя ветки.", Toast.LENGTH_LONG).show()
            return
        }
        setBusy(true)
        output.text = "Отправляю dispatch: ${workflow.name} · $branch…"
        io.execute {
            try {
                request("POST", "/repos/$repo/actions/workflows/${workflow.id}/dispatches", JSONObject().put("ref", branch))
                main.post {
                    output.text = "Запуск отправлен. GitHub создаёт новый workflow run; обновите список через несколько секунд."
                    setBusy(false)
                }
            } catch (e: Exception) {
                main.post { output.text = "Ошибка запуска: ${e.message}"; setBusy(false) }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        refreshButton.isEnabled = !busy
        runButton.isEnabled = !busy
        repoInput.isEnabled = !busy
        branchInput.isEnabled = !busy
        workflows.isEnabled = !busy
        findViewById<View>(R.id.actionsProgress).visibility = if (busy) View.VISIBLE else View.GONE
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
