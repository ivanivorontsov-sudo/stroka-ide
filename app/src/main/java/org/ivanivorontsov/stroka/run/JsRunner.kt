package org.ivanivorontsov.stroka.run

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.widget.Toast
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import org.json.JSONObject
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class JsRunner(private val ctx: Context) {

    interface Sink {
        fun onOutput(text: String, error: Boolean)
        fun onPrompt(message: String, default: String, result: (String?) -> Unit)
        fun onFinished(code: Int, millis: Long)
    }

    private val main = Handler(Looper.getMainLooper())
    private var web: WebView? = null
    private var sink: Sink? = null
    private var started = 0L
    private var pending: String? = null
    var isRunning = false
        private set

    inner class Bridge {
        @JavascriptInterface
        fun log(level: String, msg: String) {
            main.post { sink?.onOutput(msg + "\n", level == "error" || level == "warn") }
        }

        @JavascriptInterface
        fun done(ok: Boolean) {
            main.post { finish(if (ok) 0 else 1) }
        }
    }

    inner class SystemBridge {
        @JavascriptInterface
        fun toast(message: String) {
            main.post { Toast.makeText(ctx, message.take(3000), Toast.LENGTH_SHORT).show() }
        }

        @JavascriptInterface
        fun vibrate(durationMs: Long): Boolean {
            val duration = durationMs.coerceIn(1L, 1000L)
            main.post {
                try {
                    val vibrator = if (Build.VERSION.SDK_INT >= 31)
                        ctx.getSystemService(VibratorManager::class.java).defaultVibrator
                    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
                    if (vibrator.hasVibrator()) vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
                } catch (_: Exception) { }
            }
            return true
        }

        @JavascriptInterface
        fun openUrl(value: String): Boolean {
            val uri = runCatching { Uri.parse(value.trim()) }.getOrNull() ?: return false
            if (uri.scheme !in setOf("http", "https")) return false
            main.post {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.onFailure { toast("Не удалось открыть ссылку: " + (it.message ?: "ошибка")) }
            }
            return true
        }

        @JavascriptInterface
        fun copyText(value: String): Boolean {
            main.post {
                runCatching {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Строка", value))
                }
            }
            return true
        }

        @JavascriptInterface
        fun clipboardText(): String = onMain("") {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty()
        }

        @JavascriptInterface
        fun deviceInfo(): String = JSONObject()
            .put("manufacturer", Build.MANUFACTURER)
            .put("model", Build.MODEL)
            .put("android", Build.VERSION.RELEASE)
            .put("sdk", Build.VERSION.SDK_INT)
            .toString()

        @JavascriptInterface
        fun batteryPercent(): Int = try {
            ctx.getSystemService(BatteryManager::class.java)
                .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        } catch (_: Exception) { -1 }

        @JavascriptInterface
        fun networkAvailable(): Boolean = try {
            val manager = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = manager.activeNetwork ?: return false
            val caps = manager.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) { false }

        @JavascriptInterface
        fun openSettings(): Boolean {
            main.post {
                runCatching { ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return true
        }
    }

    private fun <T> onMain(fallback: T, action: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return runCatching(action).getOrDefault(fallback)
        val value = AtomicReference(fallback)
        val latch = CountDownLatch(1)
        main.post {
            try { value.set(action()) } catch (_: Exception) { } finally { latch.countDown() }
        }
        return try { if (latch.await(2, TimeUnit.SECONDS)) value.get() else fallback } catch (_: InterruptedException) { fallback }
    }

    private fun finish(code: Int) {
        if (!isRunning) return
        isRunning = false
        main.removeCallbacksAndMessages(TIMEOUT_TOKEN)
        sink?.onFinished(code, System.currentTimeMillis() - started)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensure(): WebView {
        web?.let { return it }
        val w = WebView(ctx)
        w.settings.javaScriptEnabled = true
        w.settings.allowFileAccess = false
        w.settings.allowContentAccess = false
        w.addJavascriptInterface(Bridge(), "StrokaHost")
        w.addJavascriptInterface(SystemBridge(), "StrokaSystem")
        w.webChromeClient = object : WebChromeClient() {
            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean {
                val s = sink
                if (s == null) { result.cancel(); return true }
                s.onPrompt(message ?: "", defaultValue ?: "") { v -> if (v == null) result.cancel() else result.confirm(v) }
                return true
            }
        }
        w.webViewClient = object : android.webkit.WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val js = pending
                pending = null
                if (js != null) view?.evaluateJavascript(js, null)
            }
        }
        w.loadDataWithBaseURL("https://stroka.local/", HARNESS, "text/html", "utf-8", null)
        web = w
        return w
    }

    fun run(code: String, fileName: String, sink: Sink) {
        if (isRunning) return
        this.sink = sink
        isRunning = true
        started = System.currentTimeMillis()
        release()
        val payload = JSONObject.quote(code)
        val name = JSONObject.quote(fileName)
        pending = "window.__strokaRun($payload, $name);"
        ensure()
    }

    fun stop() {
        if (!isRunning) return
        web?.destroy()
        web = null
        sink?.onOutput("Остановлено\n", true)
        finish(130)
    }

    fun release() {
        web?.destroy()
        web = null
    }

    companion object {
        private val TIMEOUT_TOKEN = Any()
        private const val HARNESS = """<!DOCTYPE html><html><head><meta charset="utf-8"></head><body><script>
(function(){
  function fmt(v){
    if (typeof v === 'string') return v;
    if (v instanceof Error) return v.stack || String(v);
    try { var s = JSON.stringify(v, null, 2); return s === undefined ? String(v) : s; } catch (e) { return String(v); }
  }
  function join(args){ return Array.prototype.map.call(args, fmt).join(' '); }
  ['log','info','debug','warn','error'].forEach(function(l){
    console[l] = function(){ StrokaHost.log(l, join(arguments)); };
  });
  console.table = function(v){ StrokaHost.log('log', fmt(v)); };
  window.alert = function(m){ StrokaHost.log('info', String(m)); };
  window.__strokaRun = function(code, name){
    var pending = 0;
    try {
      var r = (0, eval)(code + "\n//# sourceURL=" + name);
      if (r && typeof r.then === 'function') {
        r.then(function(){ StrokaHost.done(true); }, function(e){ StrokaHost.log('error', fmt(e)); StrokaHost.done(false); });
        return;
      }
      setTimeout(function(){ StrokaHost.done(true); }, 0);
    } catch (e) {
      StrokaHost.log('error', (e && e.name ? e.name + ': ' : '') + (e && e.message ? e.message : String(e)));
      StrokaHost.done(false);
    }
  };
  window.addEventListener('error', function(e){ StrokaHost.log('error', e.message + (e.lineno ? ' (строка ' + e.lineno + ')' : '')); });
  window.addEventListener('unhandledrejection', function(e){ StrokaHost.log('error', 'Promise: ' + fmt(e.reason)); });
})();
</script></body></html>"""
    }
}
