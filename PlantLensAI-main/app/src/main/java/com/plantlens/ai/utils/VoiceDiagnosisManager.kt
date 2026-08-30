package com.plantlens.ai.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class VoiceDiagnosisManager(private val context: Context) : TextToSpeech.OnInitListener {

    private val tag = "VoiceDiagnosisManager"
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var pendingSpeechText: String? = null
    private var onSpeechStatusListener: ((Boolean) -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TextToSpeech: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isInitialized = true
            setupLanguage(TranslationManager.getCurrentLangCode())
            
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeechStatusListener?.invoke(true)
                }

                override fun onDone(utteranceId: String?) {
                    if (utteranceId == "FINAL_DIAGNOSIS_UTTERANCE") {
                        onSpeechStatusListener?.invoke(false)
                    }
                }

                override fun onError(utteranceId: String?) {
                    onSpeechStatusListener?.invoke(false)
                }
            })

            pendingSpeechText?.let {
                speak(it, onSpeechStatusListener)
                pendingSpeechText = null
            }
            Log.d(tag, "TextToSpeech initialized successfully.")
        } else {
            Log.e(tag, "TextToSpeech initialization failed with status $status")
        }
    }

    private fun setupLanguage(langCode: String) {
        val locale = when (langCode.lowercase(Locale.ROOT)) {
            "hi" -> Locale("hi", "IN")
            "ta" -> Locale("ta", "IN")
            "te" -> Locale("te", "IN")
            "kn" -> Locale("kn", "IN")
            "ml" -> Locale("ml", "IN")
            "mr" -> Locale("mr", "IN")
            "gu" -> Locale("gu", "IN")
            "pa" -> Locale("pa", "IN")
            "bn" -> Locale("bn", "IN")
            else -> Locale.US
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(tag, "Language $langCode not fully supported by TTS engine. Falling back to default.")
            tts?.language = Locale.US
        }
    }

    fun speak(text: String, onStatusChanged: ((Boolean) -> Unit)? = null) {
        onSpeechStatusListener = onStatusChanged
        if (!isInitialized || tts == null) {
            pendingSpeechText = text
            return
        }

        setupLanguage(TranslationManager.getCurrentLangCode())
        tts?.stop()

        // Clean bullets and split into natural spoken sentences
        val cleanText = text.replace("•", "").replace("*", "")
        val sentences = cleanText.split(Regex("[.\n]+")).map { it.trim() }.filter { it.isNotBlank() }

        if (sentences.isEmpty()) return

        onSpeechStatusListener?.invoke(true)
        for (i in sentences.indices) {
            val utteranceId = if (i == sentences.lastIndex) "FINAL_DIAGNOSIS_UTTERANCE" else "PART_${i}_UTTERANCE"
            tts?.speak(sentences[i], TextToSpeech.QUEUE_ADD, null, utteranceId)
        }
    }

    fun stop() {
        tts?.stop()
        onSpeechStatusListener?.invoke(false)
    }

    fun isSpeaking(): Boolean {
        return tts?.isSpeaking == true
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
            isInitialized = false
        } catch (e: Exception) {
            Log.e(tag, "Error shutting down TTS: ${e.message}")
        }
    }
}
