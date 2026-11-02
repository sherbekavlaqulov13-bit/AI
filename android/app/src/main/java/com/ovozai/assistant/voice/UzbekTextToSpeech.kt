package com.ovozai.assistant.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

class UzbekTextToSpeech(
    context: Context,
    private val onReady: (() -> Unit)? = null
) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                // O'zbek tili sozlamasini o'rnatishga harakat qilamiz
                val uzLocale = Locale("uz")
                val result = tts?.setLanguage(uzLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Agar maxsus o'zbek tili bazasi bo'lmasa, umumiy lotin tili/inglizcha orqali o'qiladi
                    tts?.language = Locale.getDefault()
                }
                tts?.setSpeechRate(1.0f)
                tts?.setPitch(1.0f)
                isInitialized = true
                onReady?.invoke()
            } else {
                Log.e("OvozAI", "TextToSpeech ishga tushirishda xatolik")
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onDone?.invoke()
            return
        }

        val utteranceId = "OvozAI_${System.currentTimeMillis()}"
        if (onDone != null) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    if (id == utteranceId) onDone()
                }
                override fun onError(id: String?) {
                    if (id == utteranceId) onDone()
                }
            })
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
