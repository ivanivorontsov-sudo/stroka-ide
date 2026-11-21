package org.ivanivorontsov.stroka.run

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class PythonRunner(private val ctx: Context) {

    interface Sink {
        fun onOutput(text: String, error: Boolean)
        fun onInputRequested(prompt: String)
        fun onFinished(code: Int, millis: Long)
        fun onPreview(html: String) {}
    }

    inner class Callback(private val sink: Sink) {
        private val buf = StringBuilder()
        private val events = LinkedBlockingQueue<String>()
        private var lastErr = false
        private var scheduled = false

        fun out(s: String, err: Boolean) {
            synchronized(buf) {
                if (err != lastErr && buf.isNotEmpty()) flushLocked()
                lastErr = err
                buf.append(s)
                if (buf.length > 8192) flushLocked()
                else if (!scheduled) {
                    scheduled = true
                    main.postDelayed({ synchronized(buf) { flushLocked() } }, 40)
                }
            }
        }

        private fun flushLocked() {
            scheduled = false
            if (buf.isEmpty()) return
            val text = buf.toString()
            val e = lastErr
            buf.setLength(0)
            main.post { sink.onOutput(text, e) }
        }

        fun flush() = synchronized(buf) { flushLocked() }

        fun showPreview(html: String) {
            main.post { sink.onPreview(html) }
        }

        fun waitUiEvent(timeoutMs: Int): String {
            return try {
                events.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: ""
            } catch (_: InterruptedException) {
                ""
            }
        }

        fun waitUiEvent(timeoutMs: Long): String = waitUiEvent(timeoutMs.toInt())

        fun waitUiEvent(): String = waitUiEvent(24 * 60 * 60 * 1000)

        fun postUiEvent(event: String) {
            events.offer(event ?: "")
        }

        fun readLine(prompt: String): String? {
            flush()
            main.post { sink.onInputRequested(prompt) }
            val v = input.take()
            return if (v === EOF) null else v
        }
    }

    private val main = Handler(Looper.getMainLooper())
    private val input = LinkedBlockingQueue<String>()
    private val running = AtomicBoolean(false)
    private var module: PyObject? = null
    @Volatile private var activeCb: Callback? = null

    val isRunning: Boolean get() = running.get()

    fun provideInput(line: String) {
        input.offer(line)
    }

    fun run(code: String, fileName: String, cwd: String?, sink: Sink) {
        if (!running.compareAndSet(false, true)) return
        input.clear()
        Thread({
            val start = System.currentTimeMillis()
            var status = 1
            val cb = Callback(sink)
            activeCb = cb
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
                val m = module ?: Python.getInstance().getModule("stroka_runner").also { module = it }
                status = m.callAttr("run", code, fileName, cwd, cb).toInt()
            } catch (e: Throwable) {
                cb.out("Ошибка запуска Python: ${e.message}\n", true)
            } finally {
                cb.flush()
                activeCb = null
                running.set(false)
                val ms = System.currentTimeMillis() - start
                main.postDelayed({ sink.onFinished(status, ms) }, 60)
            }
        }, "python-run").apply { isDaemon = true }.start()
    }

    fun postUiEvent(event: String) {
        activeCb?.postUiEvent(event)
    }

    fun stop() {
        if (!running.get()) return
        postUiEvent("__stop__")
        input.offer(EOF)
        try {
            module?.callAttr("stop")
        } catch (e: Throwable) {
        }
    }

    fun version(): String = try {
        if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
        Python.getInstance().getModule("stroka_runner").callAttr("version").toString()
    } catch (e: Throwable) {
        "?"
    }

    companion object {
        private val NATIVE = Regex(
            "^\\s*(from\\s+(java|android|com\\.chaquo|stroka)\\b|import\\s+(java|android|stroka)\\b)|\\bjclass\\s*\\(|\\bdynamic_proxy\\s*\\(|\\bstatic_proxy\\s*\\(",
            RegexOption.MULTILINE
        )

        fun usesNativeUi(code: String): Boolean = NATIVE.containsMatchIn(code)

        private val EOF = String(charArrayOf('\u0000', 'E', 'O', 'F'))
    }
}
