package org.ivanivorontsov.stroka.voice

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.json.JSONObject
import org.ivanivorontsov.stroka.core.VoiceMapper
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import java.util.ArrayList
import java.util.concurrent.Executors

class VoiceInput(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        fun onListening(engine: String)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onFinalCandidates(candidates: List<String>) {
            candidates.firstOrNull()?.let { onFinal(it) }
        }
        fun onStopped()
        fun onError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var recognizer: SpeechRecognizer? = null
    private var recognizerOnDevice = false
    private var vosk: SpeechService? = null
    private val voskModels = HashMap<String, Model>()
    private var active = false
    private var lang = "ru"
    private var engine = "auto"
    private var activeEngine = "Google"
    private var restarts = 0

    val isActive: Boolean get() = active

    fun start(language: String, enginePref: String) {
        stop(silent = true)
        active = true
        lang = language
        engine = enginePref
        restarts = 0

        val systemAvailable = SpeechRecognizer.isRecognitionAvailable(ctx)
        val onDeviceAvailable = isOnDeviceAvailable()
        val voskAvailable = VoskModels.isInstalled(ctx, lang)

        when (enginePref) {
            "google" -> if (systemAvailable) startGoogle()
                else fail("Системное распознавание речи недоступно на этом телефоне")
            "ondevice" -> if (onDeviceAvailable) startOnDevice()
                else fail("Офлайн-распознавание Android недоступно. Нужен Android 12 или новее и установленная модель речи")
            "vosk" -> if (voskAvailable) startVosk()
                else fail("Офлайн-модель Vosk (${VoskModels.models[lang]?.title}) не скачана. Настройки → Голосовой ввод")
            else -> when {
                systemAvailable && online() -> startGoogle()
                onDeviceAvailable -> startOnDevice()
                voskAvailable -> startVosk()
                systemAvailable -> startGoogle()
                else -> fail("Нет распознавания речи. Включите системный сервис или скачайте модель Vosk в настройках")
            }
        }
    }

    private fun isOnDeviceAvailable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)

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

    private fun startGoogle() = startSystemRecognizer(onDevice = false)

    private fun startOnDevice() {
        if (!isOnDeviceAvailable()) {
            if (engine == "auto" && VoskModels.isInstalled(ctx, lang)) {
                startVosk()
            } else {
                fail("Офлайн-распознавание Android недоступно на этом устройстве")
            }
            return
        }
        startSystemRecognizer(onDevice = true)
    }

    private fun startSystemRecognizer(onDevice: Boolean) {
        if (!active) return
        try {
            val r = if (recognizer != null && recognizerOnDevice == onDevice) {
                recognizer!!
            } else {
                try { recognizer?.cancel() } catch (_: Exception) {}
                try { recognizer?.destroy() } catch (_: Exception) {}
                val created = if (onDevice) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
                } else {
                    SpeechRecognizer.createSpeechRecognizer(ctx)
                }
                recognizer = created
                recognizerOnDevice = onDevice
                created
            }
            activeEngine = if (onDevice) "Android на устройстве (офлайн)" else "Google / системный сервис"
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    listener.onListening(activeEngine)
                }
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
                    val candidates = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        ?.distinct()
                        .orEmpty()
                    if (candidates.isNotEmpty()) {
                        restarts = 0
                        listener.onFinalCandidates(candidates)
                    }
                    if (active) main.postDelayed({ if (active) listenSystem() }, 180)
                }

                override fun onError(error: Int) {
                    if (!active) return
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                            if (++restarts > 6) {
                                listener.onError("Речь не распознана. Попробуйте говорить чуть медленнее и ближе к микрофону")
                                stop()
                                return
                            }
                            main.postDelayed({ if (active) listenSystem() }, 250)
                        }
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                            main.postDelayed({ if (active) listenSystem() }, 500)
                        SpeechRecognizer.ERROR_NETWORK,
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                        SpeechRecognizer.ERROR_SERVER,
                        13 -> switchToOffline("Сервис распознавания недоступен — пробую офлайн-режим")
                        12 -> switchToOffline("Выбранный сервис не поддерживает язык ${locale()} — пробую другой движок")
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                            listener.onError("Нет доступа к микрофону")
                            stop()
                        }
                        else -> {
                            listener.onError("Ошибка распознавания ($error)")
                            stop()
                        }
                    }
                }
            })
            listenSystem()
        } catch (e: Exception) {
            if (onDevice && engine == "auto" && VoskModels.isInstalled(ctx, lang)) {
                listener.onPartial("Не удалось запустить офлайн-движок Android — перехожу на Vosk")
                startVosk()
            } else {
                listener.onError("Распознавание: ${e.message ?: e.javaClass.simpleName}")
                stop()
            }
        }
    }

    private fun switchToOffline(message: String) {
        if (engine != "auto") {
            listener.onError("Нет связи с сервисом распознавания. Выберите офлайн-движок в настройках")
            stop()
            return
        }
        listener.onPartial(message)
        try { recognizer?.cancel() } catch (_: Exception) {}

        if (!recognizerOnDevice && isOnDeviceAvailable()) {
            startOnDevice()
        } else if (VoskModels.isInstalled(ctx, lang)) {
            startVosk()
        } else {
            listener.onError("Офлайн-модель не установлена. Настройки → Голосовой ввод → Vosk")
            stop()
        }
    }

    private fun listenSystem() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale())
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putStringArrayListExtra(
                    RecognizerIntent.EXTRA_BIASING_STRINGS,
                    ArrayList(VoiceMapper.recognitionHints())
                )
            }
            if (!online()) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            recognizer?.startListening(i)
        } catch (e: Exception) {
            listener.onError("Не удалось начать распознавание: ${e.message}")
            stop()
        }
    }

    private fun startVosk() {
        if (!active) return
        try { recognizer?.cancel() } catch (_: Exception) {}
        activeEngine = "Vosk (офлайн)"
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
                                listener.onError("Vosk: ${exception?.message ?: "ошибка распознавания"}")
                                stop()
                            }
                            override fun onTimeout() { stop() }
                        })
                        listener.onListening(activeEngine)
                    } catch (e: Exception) {
                        listener.onError("Vosk: ${e.message}")
                        stop()
                    }
                }
            } catch (e: Throwable) {
                main.post {
                    listener.onError("Не удалось загрузить модель Vosk: ${e.message}")
                    stop()
                }
            }
        }
    }

    private fun parse(json: String?, key: String): String = try {
        JSONObject(json ?: "{}").optString(key, "")
    } catch (_: Exception) {
        ""
    }

    fun stop(silent: Boolean = false) {
        val was = active
        active = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { vosk?.stop(); vosk?.shutdown() } catch (_: Exception) {}
        vosk = null
        if (was && !silent) listener.onStopped()
    }

    fun release() {
        stop(silent = true)
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        synchronized(voskModels) {
            voskModels.values.forEach { try { it.close() } catch (_: Exception) {} }
            voskModels.clear()
        }
        io.shutdownNow()
    }
}
