package com.ovozai.assistant.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
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
    private val appContext = context.applicationContext

    init {
        tts = TextToSpeech(appContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                setupLanguageAndAudio()
                isInitialized = true
                onReady?.invoke()
                Log.d("OvozAI", "TTS muvaffaqiyatli ishga tushdi")
            } else {
                Log.e("OvozAI", "TextToSpeech ishga tushirishda xatolik: $status")
            }
        }
    }

    private fun setupLanguageAndAudio() {
        val ttsEngine = tts ?: return

        // 1. O'zbek tili sozlamasini tekshirish
        val uzLocale = Locale("uz", "UZ")
        val uzResult = ttsEngine.setLanguage(uzLocale)

        if (uzResult == TextToSpeech.LANG_MISSING_DATA || uzResult == TextToSpeech.LANG_NOT_SUPPORTED) {
            val generalUz = Locale("uz")
            val generalResult = ttsEngine.setLanguage(generalUz)
            if (generalResult == TextToSpeech.LANG_MISSING_DATA || generalResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Rus yoki tizim standart tili orqali fonetik o'qish (Android ko'p hollarda o'zbek tiliga rus yoki ingliz tts vositasini moslashtiradi)
                val defLocale = Locale.getDefault()
                val defResult = ttsEngine.setLanguage(defLocale)
                if (defResult == TextToSpeech.LANG_MISSING_DATA || defResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    ttsEngine.language = Locale.ENGLISH
                }
            }
        }

        // 2. Audio oqimini baland ovozda (STREAM_MUSIC) o'rnatish
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            ttsEngine.setAudioAttributes(audioAttributes)
        }

        ttsEngine.setSpeechRate(0.95f) // Tabiiy va aniq gapirish tezligi
        ttsEngine.setPitch(1.0f)
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onDone?.invoke()
            return
        }

        val ttsEngine = tts ?: run {
            onDone?.invoke()
            return
        }

        val utteranceId = "OvozAI_${System.currentTimeMillis()}"

        ttsEngine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                Log.d("OvozAI", "TTS gapirishni boshladi: $text")
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) onDone?.invoke()
            }

            override fun onError(id: String?) {
                if (id == utteranceId) onDone?.invoke()
            }
        })

        // Ovozsiz qolib ketmasligi uchun ovoz oqimini tekshiramiz
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val currentVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
        if (currentVol == 0) {
            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 10
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVol * 0.6).toInt(), 0)
        }

        ttsEngine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("OvozAI", "TTS shutdown xatosi: ${e.message}")
        }
    }
}
