package org.ivanivorontsov.stroka.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

class VisualBuilderActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var recognizer: SpeechRecognizer? = null
    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecognition() else sendVoiceResult("Нет разрешения на микрофон")
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
        setContentView(web)
        val source = URLEncoder.encode(intent.getStringExtra("source").orEmpty(), "UTF-8").replace("+", "%20")
        val page = assets.open("visual_builder.html").bufferedReader().use { it.readText() }
            .replace("const seed=decodeURIComponent(location.hash.slice(1));", "const seed=decodeURIComponent(\"$source\");")
        web.loadDataWithBaseURL("https://stroka.local/", page, "text/html", "UTF-8", null)
    }

    private fun startRecognition() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            sendVoiceResult("На устройстве нет службы распознавания речи")
            return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { speech ->
            speech.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = sendVoiceResult("Слушаю…")
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) = sendVoiceResult("Не удалось распознать речь, попробуйте ещё раз")
                override fun onResults(results: Bundle?) {
                    val words = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    sendVoiceResult(words?.firstOrNull().orEmpty())
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            speech.startListening(request)
        }
    }

    private fun sendVoiceResult(text: String) {
        runOnUiThread { if (::web.isInitialized) web.evaluateJavascript("window.onVoiceResult && window.onVoiceResult(${JSONObject.quote(text)})", null) }
    }

    private inner class Bridge {
        @JavascriptInterface fun startVoice() {
            runOnUiThread {
                if (ContextCompat.checkSelfPermission(this@VisualBuilderActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecognition()
                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
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
        recognizer?.destroy()
        recognizer = null
        web.destroy()
        super.onDestroy()
    }
}
