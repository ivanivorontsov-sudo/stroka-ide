package org.ivanivorontsov.stroka.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.ivanivorontsov.stroka.R
import org.ivanivorontsov.stroka.ai.AiClient
import org.ivanivorontsov.stroka.ai.AiMessage
import java.util.concurrent.Executors

class AiChatActivity : AppCompatActivity() {
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val history = ArrayList<AiMessage>()
    private lateinit var log: TextView
    private lateinit var input: EditText
    private lateinit var send: MaterialButton
    private lateinit var applyBtn: MaterialButton
    private var lastCode: String? = null
    private var fileName = "файл"
    private var langName = "текст"

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_chat)
        val bar = findViewById<MaterialToolbar>(R.id.aiToolbar)
        bar.setNavigationOnClickListener { finish() }
        Insets.applyTopBar(bar)
        log = findViewById(R.id.aiLog)
        input = findViewById(R.id.aiInput)
        send = findViewById(R.id.aiSend)
        applyBtn = findViewById(R.id.aiApply)
        fileName = intent.getStringExtra(EXTRA_NAME) ?: "файл"
        langName = intent.getStringExtra(EXTRA_LANG) ?: "текст"
        val code = intent.getStringExtra(EXTRA_CODE).orEmpty()
        val projectContext = intent.getStringExtra(EXTRA_PROJECT_CONTEXT).orEmpty()
        val prefs = Prefs(this)
        history.add(AiMessage("system", AiClient.systemPrompt(langName, fileName)))
        if (code.isNotBlank()) {
            history.add(AiMessage("user", "Текущий текст файла «$fileName»:\n\n```\n$code\n```"))
            append("Контекст: открыт «$fileName». ")
        } else {
            append("Опишите программу. ")
        }
        if (projectContext.isNotBlank()) {
            history.add(AiMessage("user", projectContext))
            append("ИИ получил текстовый контекст проекта (несколько файлов). ")
        }
        append("Напишите, что изменить или создать. Для применения кода сейчас используется кнопка «Применить» для основного файла.\n\n")
        send.setOnClickListener { turn() }
        applyBtn.setOnClickListener { applyCode() }
        bar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.aiSettings) {
                startActivity(android.content.Intent(this, SettingsActivity::class.java))
                true
            } else false
        }
    }

    private fun turn() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        val prefs = Prefs(this)
        val key = SecureStore(this).get("ai_key").orEmpty()
        if (key.isBlank()) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Нет ключа ИИ")
                .setMessage("Ключ хранится только в защищённых настройках и не попадает в чат и в репозиторий.")
                .setPositiveButton("Настройки") { _, _ -> startActivity(android.content.Intent(this, SettingsActivity::class.java)) }
                .setNegativeButton("Закрыть", null)
                .show()
            return
        }
        input.setText("")
        history.add(AiMessage("user", text))
        append("Вы: $text\n\n")
        send.isEnabled = false
        append("ИИ печатает…\n")
        val snap = ArrayList(history)
        val base = prefs.aiBase
        val model = prefs.aiModel
        io.execute {
            try {
                val answer = AiClient.chat(base, model, key, snap)
                val code = AiClient.extractCode(answer)
                main.post {
                    history.add(AiMessage("assistant", answer))
                    append("ИИ:\n$answer\n\n")
                    lastCode = code
                    applyBtn.visibility = if (code != null) View.VISIBLE else View.GONE
                    send.isEnabled = true
                    findViewById<android.widget.ScrollView>(R.id.aiScroll).post {
                        findViewById<android.widget.ScrollView>(R.id.aiScroll).fullScroll(View.FOCUS_DOWN)
                    }
                }
            } catch (e: Exception) {
                main.post {
                    append("Ошибка: ${e.message}\n\n")
                    send.isEnabled = true
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun applyCode() {
        val code = lastCode ?: return
        AiBridge.pendingCode = code
        Toast.makeText(this, "Код будет вставлен в открытый файл", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun append(s: String) {
        log.append(s)
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_NAME = "name"
        const val EXTRA_LANG = "lang"
        const val EXTRA_PROJECT_CONTEXT = "project_context"
    }
}

object AiBridge {
    var pendingCode: String? = null
}
