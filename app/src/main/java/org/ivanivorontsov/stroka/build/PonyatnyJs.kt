package org.ivanivorontsov.stroka.build

import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

object PonyatnyJs {
    @Volatile
    var appContext: android.content.Context? = null

    fun wrap(js: String, title: String, mode: String): String {
        val t = json(title)
        val m = json(mode)
        return "window.__STROKA__={title:$t,mode:$m};\nwindow.__strokaMain=function(__R){\n$js\n};\n"
    }

    fun compile(source: String, yadroSource: String): String {
        if (yadroSource.isBlank()) throw BuildException("Не найден компилятор «Понятный».")
        val ctx = appContext
        if (ctx != null) return compileWithWebView(ctx, source, yadroSource)
        return compileWithNodeProcess(source, yadroSource)
    }

    fun compileFile(source: String, yadroJs: File): String {
        if (!yadroJs.isFile) throw BuildException("Не найден компилятор «Понятный»: ${yadroJs.absolutePath}")
        return compile(source, yadroJs.readText(Charsets.UTF_8))
    }

    fun compileWithNode(source: String, yadroJs: File, node: File, work: File): String = compileFile(source, yadroJs)

    private fun compileWithNodeProcess(source: String, yadroSource: String): String {
        val node = findNode() ?: throw BuildException("Нет движка JavaScript для компилятора «Понятный».")
        val work = File(System.getProperty("java.io.tmpdir"), "stroka-pon-" + System.nanoTime())
        work.mkdirs()
        try {
            val yadro = File(work, "yadro.js")
            yadro.writeText(yadroSource, Charsets.UTF_8)
            val src = File(work, "in.pon")
            val out = File(work, "out.js")
            val script = File(work, "compile.cjs")
            src.writeText(source, Charsets.UTF_8)
            script.writeText(
                """const fs = require("fs");
const Y = require(${json(yadro.absolutePath)});
const src = fs.readFileSync(process.argv[2], "utf8");
try {
  const js = Y.compile(src);
  fs.writeFileSync(process.argv[3], js);
} catch (e) {
  const m = e && e.message ? e.message : String(e);
  process.stderr.write("Ошибка. " + m + "\n");
  process.exit(1);
}
""",
                Charsets.UTF_8
            )
            Proc.require(
                listOf(node, script.absolutePath, src.absolutePath, out.absolutePath),
                work,
                err = "Компилятор «Понятный» не смог обработать программу."
            )
            if (!out.isFile) throw BuildException("Компилятор «Понятный» не создал JavaScript.")
            return out.readText(Charsets.UTF_8)
        } finally {
            work.deleteRecursively()
        }
    }

    private fun findNode(): String? {
        for (c in listOf(System.getenv("STROKA_NODE"), "/usr/local/bin/node", "/usr/bin/node", "node")) {
            if (c.isNullOrBlank()) continue
            val f = File(c)
            if (f.isFile && f.canExecute()) return f.absolutePath
            if (c == "node") return c
        }
        return null
    }

    private fun compileWithWebView(ctx: android.content.Context, source: String, yadroSource: String): String {
        val latch = CountDownLatch(1)
        val box = AtomicReference<Any>()
        val app = ctx.applicationContext
        val run = Runnable {
            try {
                val web = WebView(app)
                web.settings.javaScriptEnabled = true
                web.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val expr = "(function(){try{var js=PonyatnyYadro.compile(" + JSONObject.quote(source) + ");return {ok:true,js:js};}catch(e){return {ok:false,err:String(e&&e.message?e.message:e)};}})()"
                        web.evaluateJavascript(expr) { raw ->
                            try {
                                val json = unwrapJs(raw)
                                val obj = JSONObject(json)
                                if (obj.optBoolean("ok")) box.set(obj.getString("js"))
                                else box.set(BuildException("Компилятор «Понятный» не смог обработать программу.\n" + obj.optString("err")))
                            } catch (e: Exception) {
                                box.set(BuildException("Не удалось разобрать ответ компилятора «Понятный»: ${e.message}"))
                            } finally {
                                latch.countDown()
                                web.destroy()
                            }
                        }
                    }
                }
                val html = "<!doctype html><meta charset=utf-8><script>\n" + yadroSource + "\n</script>"
                web.loadDataWithBaseURL("https://stroka.local/", html, "text/html", "utf-8", null)
            } catch (e: Exception) {
                box.set(BuildException("Не удалось запустить движок JavaScript: ${e.message}"))
                latch.countDown()
            }
        }
        val main = Looper.getMainLooper()
        if (main != null && Looper.myLooper() == main) {
            throw BuildException("Компилятор «Понятный» нельзя вызывать с главного потока.")
        }
        Handler(main).post(run)
        if (!latch.await(45, TimeUnit.SECONDS)) throw BuildException("Компилятор «Понятный» не ответил за 45 с.")
        val v = box.get()
        if (v is BuildException) throw v
        if (v is String && v.isNotBlank()) return v
        throw BuildException("Компилятор «Понятный» не создал JavaScript.")
    }

    private fun unwrapJs(raw: String?): String {
        if (raw == null || raw == "null") throw BuildException("Пустой ответ движка JavaScript.")
        return if (raw.startsWith("\"") && raw.endsWith("\"")) JSONObject.quote("x").let { JSONObject("{\"s\":$raw}").getString("s") } else raw
    }

    fun detectMode(source: String, js: String): String {
        val has = listOf("сложить", "вычесть", "умножить", "разделить").all { js.contains("$$it") || source.contains("функция $it") }
        return if (has) "calc" else "auto"
    }

    fun json(s: String): String {
        val b = StringBuilder()
        b.append('"')
        for (c in s) when (c) {
            '\\' -> b.append("\\\\")
            '"' -> b.append("\\\"")
            '\n' -> b.append("\\n")
            '\r' -> b.append("\\r")
            '\t' -> b.append("\\t")
            else -> b.append(c)
        }
        b.append('"')
        return b.toString()
    }
}
