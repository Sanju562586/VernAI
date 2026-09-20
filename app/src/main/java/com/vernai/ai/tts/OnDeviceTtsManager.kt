package com.vernai.ai.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.vernai.core.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

/**
 * On-Device Text-to-Speech (TTS) Manager for VernAI.
 * Operates strictly offline utilizing the host device's local speech synthesis engines
 * (e.g., Google TTS on-device data, Samsung TTS, or OEM Indic voice engines).
 *
 * Essential for low-literacy farmers and students in rural India to listen to
 * drafted petitions, circulars, and scholarship explanations.
 */
class OnDeviceTtsManager(
    private val context: Context,
    private val onInitResult: ((Boolean) -> Unit)? = null
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _currentLanguage = MutableStateFlow(Language.TELUGU)
    val currentLanguage: StateFlow<Language> = _currentLanguage.asStateFlow()

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupUtteranceListener()
            setLanguage(Language.TELUGU)
            onInitResult?.invoke(true)
        } else {
            isInitialized = false
            onInitResult?.invoke(false)
        }
    }

    private fun setupUtteranceListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                _isSpeaking.value = false
            }

            override fun onError(utteranceId: String?) {
                _isSpeaking.value = false
            }
        })
    }

    /**
     * Changes the speech synthesis language.
     */
    fun setLanguage(language: Language): Boolean {
        _currentLanguage.value = language
        if (!isInitialized || tts == null) return false

        val locale = getLocaleForLanguage(language)
        val result = tts?.setLanguage(locale)
        return result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /**
     * Checks if the given language is available offline on the device.
     */
    fun isLanguageAvailable(language: Language): Boolean {
        if (!isInitialized || tts == null) return false
        val locale = getLocaleForLanguage(language)
        val res = tts?.isLanguageAvailable(locale) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return res >= TextToSpeech.LANG_AVAILABLE
    }

    /**
     * Speaks the given text in the requested language.
     * Queues or flushes previous speech.
     */
    fun speak(
        text: String,
        language: Language = _currentLanguage.value,
        queueMode: Int = TextToSpeech.QUEUE_FLUSH
    ): Boolean {
        if (text.isBlank()) return false
        if (!isInitialized || tts == null) return false

        setLanguage(language)

        val utteranceId = UUID.randomUUID().toString()
        val result = tts?.speak(text, queueMode, null, utteranceId)
        val success = result == TextToSpeech.SUCCESS
        if (success) {
            _isSpeaking.value = true
        }
        return success
    }

    /**
     * Immediately stops any active speech.
     */
    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
    }

    /**
     * Releases TTS resources.
     */
    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }

    private fun getLocaleForLanguage(language: Language): Locale {
        return when (language) {
            Language.TELUGU -> Locale("te", "IN")
            Language.TAMIL -> Locale("ta", "IN")
            Language.HINDI -> Locale("hi", "IN")
            Language.MARATHI -> Locale("mr", "IN")
            Language.BENGALI -> Locale("bn", "IN")
            Language.GUJARATI -> Locale("gu", "IN")
            Language.KANNADA -> Locale("kn", "IN")
            Language.MALAYALAM -> Locale("ml", "IN")
            Language.ENGLISH -> Locale("en", "IN")
        }
    }
}
