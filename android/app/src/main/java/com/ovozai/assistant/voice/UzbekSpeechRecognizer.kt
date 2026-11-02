package com.ovozai.assistant.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

class UzbekSpeechRecognizer(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null

    fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onStatus("Qurilmada SpeechRecognizer mavjud emas")
            return
        }

        stopListening()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    onStatus("Eshitmoqdaman...")
                }

                override fun onBeginningOfSpeech() {
                    onStatus("Gapirilmoqda...")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    onStatus("Tahlil qilinmoqda...")
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "Ovoz tanilmadi"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Ovoz eshitilmadi"
                        SpeechRecognizer.ERROR_AUDIO -> "Audio yozishda xato"
                        else -> "Xatolik: $error"
                    }
                    onStatus(msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        val text = matches[0]
                        onResult(text)
                    } else {
                        onStatus("Ovoz aniqlanmadi")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uz-UZ")
            putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf("uz-UZ", "uz", "en-US", "ru-RU"))
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("OvozAI", "SpeechRecognizer xatosi: ${e.message}")
            onStatus("Xatolik: ${e.message}")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
