package com.ovozai.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.ovozai.assistant.R
import com.ovozai.assistant.ai.GeminiBrain
import com.ovozai.assistant.voice.UzbekSpeechRecognizer
import com.ovozai.assistant.voice.UzbekTextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var speechRecognizer: UzbekSpeechRecognizer? = null
    private var textToSpeech: UzbekTextToSpeech? = null
    private lateinit var actionController: DeviceActionController
    private lateinit var geminiBrain: GeminiBrain
    private val scope = CoroutineScope(Dispatchers.Main)

    private var tvUserSpeech: TextView? = null
    private var tvAiResponse: TextView? = null
    private var speechCard: LinearLayout? = null
    private var ivMicIcon: ImageView? = null

    // Suhbat kontekstini eslab qolish (masalan: xabarga javob yozish jarayoni)
    private var conversationContext: String = ""

    companion object {
        const val CHANNEL_ID = "OvozAI_Overlay_Channel"
        const val NOTIFICATION_ID = 1001
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        actionController = DeviceActionController(this)
        geminiBrain = GeminiBrain {
            val prefs = getSharedPreferences("OvozAI_Prefs", Context.MODE_PRIVATE)
            prefs.getString("gemini_api_key", "") ?: ""
        }

        textToSpeech = UzbekTextToSpeech(this)
        speechRecognizer = UzbekSpeechRecognizer(
            context = this,
            onResult = { recognizedText ->
                onSpeechRecognized(recognizedText)
            },
            onStatus = { status ->
                tvUserSpeech?.text = status
            }
        )

        startForegroundServiceNotification()
        setupFloatingView()
    }

    private fun startForegroundServiceNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OvozAI Ovozli Assistent",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OvozAI faol")
            .setContentText("Ovozli boshqaruvga tayyor")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupFloatingView() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_bubble, null)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 200
        }

        tvUserSpeech = overlayView?.findViewById(R.id.tvUserSpeech)
        tvAiResponse = overlayView?.findViewById(R.id.tvAiResponse)
        speechCard = overlayView?.findViewById(R.id.speechCard)
        ivMicIcon = overlayView?.findViewById(R.id.ivMicIcon)
        val bubbleLayout = overlayView?.findViewById<View>(R.id.bubbleLayout)

        bubbleLayout?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = true

            override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                            isClick = false
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            triggerListening()
                        }
                        return true
                    }
                }
                return false
            }
        })

        windowManager?.addView(overlayView, params)
    }

    private fun triggerListening() {
        speechCard?.visibility = View.VISIBLE
        tvUserSpeech?.text = "Eshitmoqdaman..."
        tvAiResponse?.text = ""
        speechRecognizer?.startListening()
    }

    private fun onSpeechRecognized(userSpeech: String) {
        tvUserSpeech?.text = "\"$userSpeech\""
        tvAiResponse?.text = "AI o'ylamoqda..."

        scope.launch {
            val visibleScreenText = OvozAccessibilityService.instance?.getVisibleScreenText() ?: ""
            val incomingMessagesSummary = OvozNotificationListenerService.getUnreadSummary()

            // AI orqali buyruq va suhbatni tahlil qilish
            val aiResult = geminiBrain.analyzeIntent(
                userSpeech = userSpeech,
                visibleScreenText = visibleScreenText,
                incomingMessagesContext = incomingMessagesSummary,
                lastTurnContext = conversationContext
            )

            tvAiResponse?.text = aiResult.voiceResponse

            // Agar AI foydalanuvchidan javob so'ragan bo'lsa (masalan "yozib yuboraymi?"), kontekstni saqlaymiz
            if (aiResult.voiceResponse.contains("yozib yuboraymi") || aiResult.voiceResponse.contains("javob")) {
                conversationContext = "xabarlar: oxirgi kelgan xabarga javob yozish jarayoni"
            } else {
                conversationContext = ""
            }

            // Ovozli javob berish
            val shouldListenAgain = aiResult.voiceResponse.contains("yozib yuboraymi")
            textToSpeech?.speak(aiResult.voiceResponse) {
                if (shouldListenAgain) {
                    // Agar AI savol bergan bo'lsa, foydalanuvchini darhol yana eshitadi
                    scope.launch {
                        delay(500)
                        triggerListening()
                    }
                }
            }

            // Amallarni ketma-ket bajarish
            for (step in aiResult.executionPlan) {
                delay(400)
                actionController.executeStep(step)
            }

            if (!shouldListenAgain) {
                delay(4000)
                speechCard?.visibility = View.GONE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        speechRecognizer?.stopListening()
        textToSpeech?.shutdown()
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
        }
    }
}
