package com.ovozai.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ovozai.assistant.service.FloatingOverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvDirectPermissionsStatus: TextView
    private lateinit var tvLiveLog: TextView
    private lateinit var btnToggleService: Button

    companion object {
        private const val REQUEST_DIRECT_PERMISSIONS = 202
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvDirectPermissionsStatus = findViewById(R.id.tvDirectPermissionsStatus)
        tvLiveLog = findViewById(R.id.tvLiveLog)
        btnToggleService = findViewById(R.id.btnToggleFloatingAssistant)

        val btnGrantOverlay = findViewById<Button>(R.id.btnGrantOverlay)
        val btnGrantNotification = findViewById<Button>(R.id.btnGrantNotification)
        val btnGrantDirect = findViewById<Button>(R.id.btnGrantDirectPermissions)

        // 1. Boshqa ilovalar ustida ko'rsatish (Overlay)
        btnGrantOverlay.setOnClickListener {
            openOverlaySettings()
        }

        // 2. Xabarlarni o'qish (Notification Listener)
        btnGrantNotification.setOnClickListener {
            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_SETTINGS)
                startActivity(intent)
            }
        }

        // 3. Mikrofon, Kamera va Qo'ng'iroq qilish
        btnGrantDirect.setOnClickListener {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_DIRECT_PERMISSIONS
            )
        }

        btnToggleService.setOnClickListener {
            toggleAssistantService()
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (e: Exception) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivity(intent)
                } catch (e2: Exception) {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatuses()
    }

    private fun updatePermissionStatuses() {
        // 1. Overlay (Boshqa ilovalar ustida ko'rsatish)
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

        // 2. Notification Listener (Xabarlarni o'qish)
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(this)
        val hasNotificationAccess = enabledListeners.contains(packageName)
        if (hasNotificationAccess) {
            tvNotificationStatus.text = "Holati: Ruxsat berilgan ✅"
            tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
        } else {
            tvNotificationStatus.text = "Holati: Ruxsat berilmagan ❌"
            tvNotificationStatus.setTextColor(ContextCompat.getColor(this, R.color.red_inactive))
        }

        // 3. To'g'ridan-to'g'ri ruxsatlar (Mikrofon, Kamera, Qo'ng'iroq)
        val allGranted = REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            tvDirectPermissionsStatus.text = "Holati: Ruxsat berilgan ✅"
            tvDirectPermissionsStatus.setTextColor(ContextCompat.getColor(this, R.color.green_active))
        } else {
            tvDirectPermissionsStatus.text = "Holati: Ruxsat berilmagan ❌"
            tvDirectPermissionsStatus.setTextColor(ContextCompat.getColor(this, R.color.red_inactive))
        }

        // Service holati
        if (FloatingOverlayService.isRunning) {
            btnToggleService.text = "⏹ Ovozli Yordamchini To'xtatish"
            btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.red_inactive)
            tvLiveLog.text = "OvozAI faol! Ekranda suzuvchi mikrofon tugmachasi ishlamoqda."
        } else {
            btnToggleService.text = "🚀 Ovozli Yordamchini Ishga Tushirish"
            btnToggleService.backgroundTintList = ContextCompat.getColorStateList(this, R.color.green_active)
            tvLiveLog.text = if (canDrawOverlay && allGranted) {
                "Tayyor! Ishga tushirish tugmasini bosing."
            } else {
                "Kerakli ruxsatlarni berib, ishga tushirish tugmasini bosing."
            }
        }
    }

    private fun toggleAssistantService() {
        val canDrawOverlay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true

        if (!canDrawOverlay) {
            Toast.makeText(this, "Avval Suzuvchi tugmaga ruxsat bering!", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return
        }

        val micPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (micPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_DIRECT_PERMISSIONS)
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
            Toast.makeText(this, "Ovozli yordamchi ishga tushdi!", Toast.LENGTH_SHORT).show()
        }

        updatePermissionStatuses()
    }
}
