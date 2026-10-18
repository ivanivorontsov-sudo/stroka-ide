package org.ivanivorontsov.stroka.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

object VoskModels {
    data class Info(val lang: String, val title: String, val url: String, val sizeMb: Int)

    val models = mapOf(
        "ru" to Info("ru", "русский", "https://alphacephei.com/vosk/models/vosk-model-small-ru-0.22.zip", 45),
        "en" to Info("en", "английский", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip", 40)
    )

    fun dir(ctx: Context, lang: String) = File(ctx.filesDir, "vosk/$lang")

    fun isInstalled(ctx: Context, lang: String): Boolean {
        val d = dir(ctx, lang)
        return File(d, "am").isDirectory || File(d, "final.mdl").exists() || File(d, "conf/model.conf").exists() && File(d, ".ok").exists()
    }

    fun delete(ctx: Context, lang: String) {
        dir(ctx, lang).deleteRecursively()
    }

    fun download(ctx: Context, lang: String, progress: (Int) -> Unit, done: (String?) -> Unit): Thread {
        val info = models[lang] ?: throw IllegalArgumentException(lang)
        val main = Handler(Looper.getMainLooper())
        val t = Thread {
            val tmp = File(ctx.cacheDir, "vosk-$lang.zip")
            val target = dir(ctx, lang)
            val staging = File(ctx.filesDir, "vosk/.$lang-staging")
            try {
                var url = URL(info.url)
                var conn: HttpURLConnection
                var redirects = 0
                while (true) {
                    conn = url.openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000
                    conn.readTimeout = 30000
                    conn.instanceFollowRedirects = false
                    val code = conn.responseCode
                    if (code in 300..399 && redirects < 5) {
                        url = URL(url, conn.getHeaderField("Location"))
                        conn.disconnect()
                        redirects++
                        continue
                    }
                    if (code != 200) throw java.io.IOException("Сервер ответил $code")
                    break
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { out ->
                        val buf = ByteArray(128 * 1024)
                        var read = 0L
                        var last = -1
                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            read += n
                            val p = if (total > 0) (read * 90 / total).toInt() else -1
                            if (p != last) { last = p; main.post { progress(p) } }
                        }
                    }
                }
                staging.deleteRecursively()
                staging.mkdirs()
                ZipInputStream(tmp.inputStream().buffered()).use { zin ->
                    while (true) {
                        val e = zin.nextEntry ?: break
                        val rel = e.name.substringAfter('/', "")
                        if (rel.isEmpty()) continue
                        val f = File(staging, rel)
                        if (!f.canonicalPath.startsWith(staging.canonicalPath)) continue
                        if (e.isDirectory) f.mkdirs() else {
                            f.parentFile?.mkdirs()
                            FileOutputStream(f).use { zin.copyTo(it) }
                        }
                    }
                }
                File(staging, ".ok").writeText("ok")
                target.deleteRecursively()
                target.parentFile?.mkdirs()
                if (!staging.renameTo(target)) throw java.io.IOException("Не удалось сохранить модель")
                main.post { progress(100); done(null) }
            } catch (e: InterruptedException) {
                main.post { done("Загрузка отменена") }
            } catch (e: Exception) {
                main.post { done(e.message ?: e.toString()) }
            } finally {
                tmp.delete()
                staging.deleteRecursively()
            }
        }
        t.start()
        return t
    }
}
