package com.aiconsole.knightriderlauncher.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * KITT's voice: one app-wide TTS engine shared by the assistant and proactive alerts.
 * Synthesis runs in the TTS engine's own process. Slightly lowered pitch and a measured
 * rate give it a calm, deliberate delivery.
 */
class TextToSpeechHelper(context: Context, initialVoice: String = "") {

    @Volatile
    private var ready = false
    private var preferredVoice = initialVoice

    /** Spoken once the engine finishes starting (e.g. the greeting at boot). */
    private var pending: String? = null
    private lateinit var tts: TextToSpeech

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext) { status -> onInit(status) }
    }

    private fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (!ready) return
        tts.setPitch(0.92f)
        tts.setSpeechRate(0.96f)
        applyVoice(preferredVoice)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _speaking.value = false
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speaking.value = false
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _speaking.value = false
            }
        })
        pending?.let { speak(it) }
        pending = null
    }

    /** Offline-capable English voices, for the voice picker. Empty until the engine is ready. */
    fun englishVoices(): List<String> = if (!ready) emptyList() else runCatching {
        tts.voices.orEmpty()
            .filter { it.locale.language == Locale.ENGLISH.language && !it.isNetworkConnectionRequired }
            .filterNot { it.features.orEmpty().contains("notInstalled") }
            .map(Voice::getName)
            .sorted()
    }.getOrDefault(emptyList())

    fun setVoice(name: String) {
        preferredVoice = name
        if (ready) applyVoice(name)
    }

    private fun applyVoice(name: String) {
        val voice = runCatching { tts.voices.orEmpty().firstOrNull { it.name == name } }.getOrNull()
        if (voice != null) tts.voice = voice else tts.language = Locale.getDefault()
    }

    /** No-op when no TTS engine is installed; the answer is still shown on screen. */
    fun speak(text: String, interrupt: Boolean = true) {
        if (!ready) {
            pending = text
            return
        }
        if (text.isNotBlank()) {
            tts.speak(
                text.replace("•", ""),
                if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                null,
                "kitt-${System.nanoTime()}"
            )
        }
    }

    fun stop() {
        if (ready) tts.stop()
        _speaking.value = false
    }

    fun shutdown() {
        tts.shutdown()
    }
}
