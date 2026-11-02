package com.ovozai.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ovozai.assistant.service.FloatingOverlayService
import com.ovozai.assistant.service.OvozAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvMicStatus: TextView
    private lateinit var tvLiveLog: TextView
    private lateinit var etApiKey: EditText
    private lateinit var btnToggleService: Button

    companion object {
        private const val REQUEST_RECORD_AUDIO = 201
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvMicStatus = findViewById(R.id.tvMicStatus)
        tvLiveLog = findViewById(R.id.tvLiveLog)
        etApiKey = findViewById(R.id.etApiKey)
        btnToggleService = findViewById(R.id.btnToggleFloatingAssistant)

        val btnGrantAccessibility = findViewById<Button>(R.id.btnGrantAccessibility)
        val btnGrantOverlay = findViewById<Button>(R.id.btnGrantOverlay)
        val btnGrantNotification = findViewById<Button>(R.id.btnGrantNotification)
        val btnGrantMic = findViewById<Button>(R.id.btnGrantMic)
        val btnSaveApiKey = findViewById<Button>(R.id.btnSaveApiKey)

        // API kalitni yuklash
        val prefs = getSharedPreferences("OvozAI_Prefs", Context.MODE_PRIVATE)
        val savedKey = prefs.getString("gemini_api_key", "") ?: ""
        etApiKey.setText(savedKey)

        btnSaveApiKey.setOnClickListener {
            val key = etApiKey.text.toString().trim()
            prefs.edit().putString("gemini_api_key", key).apply()
            Toast.makeText(this, "API kaliti saqlandi!", Toast.LENGTH_SHORT).show()
        }

        btnGrantAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnGrantOverlay.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        btnGrantNotification.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        }

        btnGrantMic.setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
        }

        btnToggleService.setOnClickListener {
            toggleAssistantService()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        // 1. Accessibility Service
        val isAccessibilityActive = OvozAccessibilityService.isRunning
        if (isAccessibilityActive) {
            tvAccessibilityStatus.text = "Holati: Ruxsat berilgan ✅"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
        } else {
            tvAccessibilityStatus.text = "Holati: Ruxsat berilmagan ❌"
            tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, R.color.red_inactive))
        }

        // 2. Overlay
        val canDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (canDrawOverlay) {
            tvOverlayStatus.text = "Holati: Ruxsat berilgan ✅"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
        } else {
            tvOverlayStatus.text = "Holati: Ruxsat berilmagan ❌"
            tvOverlayStatus.setTextColor(ContextCompat.getColor(this, R.color.red_inactive))
        }

        // 3. Notification Listener
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        val hasNotificationAccess = enabledListeners.contains(packageName)
        if (hasNotificationAccess) {
            tvNotificationStatus.text = "Holati: Ruxsat berilgan ✅"
            tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
        } else {
            tvNotificationStatus.text = "Holati: Ruxsat berilmagan ❌"
            tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.red_inactive))
        }

        // 4. Microphone
        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        val hasMic = micPermission == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            tvMicStatus.text = "Holati: Ruxsat berilgan ✅"
            tvMicStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
        } else {
            tvMicStatus.text = "Holati: Ruxsat berilmagan ❌"
            tvMicStatus.setTextColor(ContextCompat.getColor(this, R.color.red_inactive))
        }

        // Service holati
        if (FloatingOverlayService.isRunning) {
            btnToggleService.text = "⏹ Ovozli Yordamchini To'xtatish"
            btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red_inactive)
            tvLiveLog.text = "OvozAI faol! Ekranda suzuvchi mikrofon tugmachasi paydo bo'ldi."
        } else {
            btnToggleService.text = "🚀 Ovozli Yordamchini Ishga Tushirish"
            btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_active)
            tvLiveLog.text = if (isAccessibilityActive && canDrawOverlay && hasMic) {
                "Barcha ruxsatlar tayyor! Ishga tushirish tugmasini bosing."
            } else {
                "Iltimos, yuqoridagi qizil ruxsatlarni berib chiqing."
            }
        }
    }

    private fun toggleAssistantService() {
        val canDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (!canDrawOverlay) {
            Toast.makeText(this, "Avval Boshqa ilovalar ustidan ko'rsatish ruxsatini bering!", Toast.LENGTH_LONG).show()
            return
        }

        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (micPermission != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Avval mikrofonga ruxsat bering!", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, FloatingOverlayService::class.java)
        if (FloatingOverlayService.isRunning) {
            stopService(intent)
            Toast.makeText(this, "Yordamchi to'xtatildi", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Toast.makeText(this, "Yordamchi ishga tushdi!", Toast.LENGTH_SHORT).show()
        }

        updatePermissionStatuses()
    }
}
