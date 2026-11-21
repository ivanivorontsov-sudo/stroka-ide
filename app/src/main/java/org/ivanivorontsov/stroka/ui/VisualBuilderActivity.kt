package org.ivanivorontsov.stroka.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URLEncoder
import org.ivanivorontsov.stroka.voice.VoiceInput

class VisualBuilderActivity : AppCompatActivity(), VoiceInput.Listener {
    private lateinit var web: WebView
    private var voice: VoiceInput? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognition() else sendVoiceStatus("Нет разрешения на микрофон")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.allowFileAccess = false
        web.settings.allowContentAccess = false
        web.webChromeClient = WebChromeClient()
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(), "StrokaBuilder")
        voice = VoiceInput(this, this)
        setContentView(web)
        val source = URLEncoder.encode(intent.getStringExtra("source").orEmpty(), "UTF-8").replace("+", "%20")
        val page = assets.open("visual_builder.html").bufferedReader().use { it.readText() }
            .replace("let seed=decodeURIComponent(location.hash.slice(1));", "const seed=decodeURIComponent(\"$source\");")
        web.loadDataWithBaseURL("https://stroka.local/", page, "text/html", "UTF-8", null)
    }

    private fun startRecognition() {
        val language = if (intent.getStringExtra("language") == "en") "en" else "ru"
        voice?.start(language, "auto")
    }

    private fun sendVoiceStatus(text: String) {
        runOnUiThread {
            if (::web.isInitialized) {
                web.evaluateJavascript("window.onVoiceStatus && window.onVoiceStatus(${JSONObject.quote(text)})", null)
            }
        }
    }

    private fun sendVoiceResult(text: String) {
        if (text.isBlank()) return
        runOnUiThread {
            if (::web.isInitialized) {
                web.evaluateJavascript("window.onVoiceResult && window.onVoiceResult(${JSONObject.quote(text)})", null)
            }
        }
    }

    override fun onListening(engine: String) {
        sendVoiceStatus("Слушаю · $engine")
    }

    override fun onPartial(text: String) {
        sendVoiceStatus("Слушаю… $text")
    }

    override fun onFinal(text: String) {
        sendVoiceResult(text)
    }

    override fun onFinalCandidates(candidates: List<String>) {
        sendVoiceResult(candidates.firstOrNull().orEmpty())
    }

    override fun onStopped() = Unit

    override fun onError(message: String) {
        sendVoiceStatus(message)
    }

    private inner class Bridge {
        @JavascriptInterface fun startVoice() {
            runOnUiThread {
                if (voice?.isActive == true) {
                    voice?.stop()
                    sendVoiceStatus("Голосовой ввод остановлен")
                } else if (ContextCompat.checkSelfPermission(this@VisualBuilderActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startRecognition()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
        @JavascriptInterface fun save(html: String) {
            runOnUiThread {
                setResult(Activity.RESULT_OK, intent.putExtra("html", html))
                finish()
            }
        }
    }

    override fun onDestroy() {
        voice?.release()
        voice = null
        web.destroy()
        super.onDestroy()
    }
}
