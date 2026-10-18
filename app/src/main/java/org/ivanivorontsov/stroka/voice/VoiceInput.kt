package org.ivanivorontsov.stroka.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.util.concurrent.Executors

class VoiceInput(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        fun onListening(engine: String)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onStopped()
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var google: SpeechRecognizer? = null
    private var vosk: SpeechService? = null
    private val voskModels = HashMap<String, Model>()
    private var active = false
    private var lang = "ru"
    private var engine = "auto"
    private var restarts = 0

    val isActive: Boolean get() = active

    fun start(language: String, enginePref: String) {
        stop(silent = true)
        active = true
        lang = language
        engine = enginePref
        restarts = 0
        val googleOk = SpeechRecognizer.isRecognitionAvailable(ctx)
        val voskOk = VoskModels.isInstalled(ctx, lang)
        when (enginePref) {
            "google" -> if (googleOk) startGoogle() else fail("Распознавание Google недоступно на этом телефоне")
            "vosk" -> if (voskOk) startVosk() else fail("Офлайн-модель Vosk (${VoskModels.models[lang]?.title}) не скачана. Настройки → Голосовой ввод")
            else -> when {
                googleOk && (online() || !voskOk) -> startGoogle()
                voskOk -> startVosk()
                googleOk -> startGoogle()
                else -> fail("Нет распознавания речи: установите Google или скачайте модель Vosk в настройках")
            }
        }
    }

    private fun fail(msg: String) {
        active = false
        listener.onError(msg)
        listener.onStopped()
    }

    private fun online(): Boolean {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun locale() = if (lang == "en") "en-US" else "ru-RU"

    private fun startGoogle() {
        val r = google ?: SpeechRecognizer.createSpeechRecognizer(ctx).also { google = it }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listener.onListening("Google") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) listener.onPartial(t)
            }

            override fun onResults(results: Bundle?) {
                val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) { restarts = 0; listener.onFinal(t) }
                if (active) main.postDelayed({ if (active) listenGoogle() }, 150)
            }

            override fun onError(error: Int) {
                if (!active) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (++restarts > 6) { stop(); return }
                        main.postDelayed({ if (active) listenGoogle() }, 200)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> main.postDelayed({ if (active) listenGoogle() }, 500)
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER, 13 -> {
                        if (engine == "auto" && VoskModels.isInstalled(ctx, lang)) {
                            google?.cancel()
                            listener.onPartial("Нет связи с Google — перехожу на Vosk")
                            startVosk()
                        } else {
                            listener.onError("Нет связи с сервисом распознавания. Скачайте офлайн-модель Vosk в настройках")
                            stop()
                        }
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { listener.onError("Нет доступа к микрофону"); stop() }
                    12 -> {
                        if (engine == "auto" && VoskModels.isInstalled(ctx, lang)) startVosk()
                        else { listener.onError("Язык ${locale()} не поддержан Google на этом телефоне"); stop() }
                    }
                    else -> { listener.onError("Ошибка распознавания ($error)"); stop() }
                }
            }
        })
        listenGoogle()
    }

    private fun listenGoogle() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            if (!online()) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            google?.startListening(i)
        } catch (e: Exception) {
            listener.onError("Google: ${e.message}")
            stop()
        }
    }

    private fun startVosk() {
        listener.onPartial("Загружаю офлайн-модель…")
        val l = lang
        io.execute {
            try {
                val model = synchronized(voskModels) {
                    voskModels[l] ?: Model(VoskModels.dir(ctx, l).absolutePath).also { voskModels[l] = it }
                }
                main.post {
                    if (!active) return@post
                    try {
                        val rec = Recognizer(model, 16000f)
                        val svc = SpeechService(rec, 16000f)
                        vosk = svc
                        svc.startListening(object : org.vosk.android.RecognitionListener {
                            override fun onPartialResult(hypothesis: String?) {
                                val t = parse(hypothesis, "partial")
                                if (t.isNotBlank()) listener.onPartial(t)
                            }
                            override fun onResult(hypothesis: String?) {
                                val t = parse(hypothesis, "text")
                                if (t.isNotBlank()) listener.onFinal(t)
                            }
                            override fun onFinalResult(hypothesis: String?) {
                                val t = parse(hypothesis, "text")
                                if (t.isNotBlank()) listener.onFinal(t)
                            }
                            override fun onError(exception: Exception?) {
                                listener.onError("Vosk: ${exception?.message}")
                                stop()
                            }
                            override fun onTimeout() { stop() }
                        })
                        listener.onListening("Vosk (офлайн)")
                    } catch (e: Exception) {
                        listener.onError("Vosk: ${e.message}")
                        stop()
                    }
                }
            } catch (e: Throwable) {
                main.post { listener.onError("Не удалось загрузить модель Vosk: ${e.message}"); stop() }
            }
        }
    }

    private fun parse(json: String?, key: String): String = try {
        JSONObject(json ?: "{}").optString(key, "")
    } catch (e: Exception) {
        ""
    }

    fun stop(silent: Boolean = false) {
        val was = active
        active = false
        try { google?.cancel() } catch (e: Exception) {}
        try { vosk?.stop(); vosk?.shutdown() } catch (e: Exception) {}
        vosk = null
        if (was && !silent) listener.onStopped()
    }

    fun release() {
        stop(silent = true)
        try { google?.destroy() } catch (e: Exception) {}
        google = null
        synchronized(voskModels) {
            voskModels.values.forEach { try { it.close() } catch (e: Exception) {} }
            voskModels.clear()
        }
    }
}
