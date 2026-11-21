package org.ivanivorontsov.stroka.run

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.chaquo.python.PyObject
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

class PythonRunner(private val ctx: Context) {

    interface Sink {
        fun onOutput(text: String, error: Boolean)
        fun onInputRequested(prompt: String)
        fun onFinished(code: Int, millis: Long)
    }

    inner class Callback(private val sink: Sink) {
        private val buf = StringBuilder()
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
            try {
                if (!Python.isStarted()) Python.start(AndroidPlatform(ctx.applicationContext))
                val m = module ?: Python.getInstance().getModule("stroka_runner").also { module = it }
                status = m.callAttr("run", code, fileName, cwd, cb).toInt()
            } catch (e: Throwable) {
                cb.out("Ошибка запуска Python: ${e.message}\n", true)
            } finally {
                cb.flush()
                running.set(false)
                val ms = System.currentTimeMillis() - start
                main.postDelayed({ sink.onFinished(status, ms) }, 60)
            }
        }, "python-run").apply { isDaemon = true }.start()
    }

    fun stop() {
        if (!running.get()) return
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
        private val EOF = String(charArrayOf('\u0000', 'E', 'O', 'F'))
    }
}
