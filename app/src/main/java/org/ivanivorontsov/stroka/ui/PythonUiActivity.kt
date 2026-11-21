package org.ivanivorontsov.stroka.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class PythonUiActivity : Activity() {

    companion object {
        const val EXTRA_CODE = "code"
        const val EXTRA_NAME = "name"
        const val EXTRA_CWD = "cwd"
        @Volatile var sink: ((String, Boolean) -> Unit)? = null
        @Volatile var pendingCode: String? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra(EXTRA_NAME) ?: "program.py"
        title = name
        val status = TextView(this).apply {
            text = "Запускаю $name…"
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }
        setContentView(status)
        val code = pendingCode ?: intent.getStringExtra(EXTRA_CODE) ?: ""
        pendingCode = null
        val cwd = intent.getStringExtra(EXTRA_CWD)
        window.decorView.post {
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(applicationContext))
                val code0 = Python.getInstance().getModule("stroka_runner")
                    .callAttr("run_native", code, name, cwd, this).toInt()
                out("■ программа с интерфейсом запущена, код $code0\n", code0 != 0)
            } catch (e: Throwable) {
                showError("Ошибка запуска Python: ${e.message ?: e}")
            }
        }
    }

    fun out(s: String, err: Boolean) {
        val k = sink ?: return
        runOnUiThread { k(s, err) }
    }

    fun flush() {}

    fun showError(text: String) {
        runOnUiThread {
            val tv = TextView(this).apply {
                this.text = "Ошибка в программе\n\n$text"
                setTextColor(Color.rgb(200, 40, 40))
                textSize = 14f
                setTextIsSelectable(true)
                setPadding(40, 40, 40, 40)
            }
            setContentView(ScrollView(this).apply { addView(tv) })
        }
    }
}
