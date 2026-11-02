package com.ovozai.assistant.service

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import com.ovozai.assistant.ai.ActionStep
import com.ovozai.assistant.util.AppLauncher

class DeviceActionController(private val context: Context) {

    val appLauncher = AppLauncher(context)

    fun executeStep(step: ActionStep): Boolean {
        Log.d("OvozAI", "Ijro qilinmoqda: ${step.tool} - ${step.parameters}")
        return try {
            when (step.tool) {
                // 1. Telefondagi hamma ilovani aytilganda ochish
                "open_app" -> {
                    val appName = step.parameters["app_name"]?.toString() ?: ""
                    val (success, _) = appLauncher.launchApp(appName)
                    success
                }

                // 2. Yangi kelgan xabarlarga javob yozish
                "reply_message" -> {
                    val replyText = step.parameters["text"]?.toString() ?: ""
                    val sender = step.parameters["sender"]?.toString() ?: ""
                    if (sender.isNotEmpty()) {
                        OvozNotificationListenerService.replyTo(sender, replyText, context)
                    } else {
                        OvozNotificationListenerService.replyToLast(replyText, context)
                    }
                }

                // 3. Ilovada avtonom xabar yuborish (Telegram, WhatsApp va h.k.)
                "send_app_message" -> {
                    val appName = step.parameters["app_name"]?.toString() ?: "Telegram"
                    val recipient = step.parameters["recipient"]?.toString() ?: ""
                    val text = step.parameters["text"]?.toString() ?: ""
                    appLauncher.launchApp(appName)
                    OvozAccessibilityService.instance?.autonomousSendMessage(recipient, text) {}
                    true
                }

                "click_text" -> {
                    val text = step.parameters["text"]?.toString() ?: ""
                    OvozAccessibilityService.instance?.clickByText(text) ?: false
                }
                "type_text" -> {
                    val text = step.parameters["text"]?.toString() ?: ""
                    OvozAccessibilityService.instance?.inputText(text) ?: false
                }
                "scroll" -> {
                    val dir = step.parameters["direction"]?.toString() ?: "down"
                    OvozAccessibilityService.instance?.scrollScreen(dir == "down") ?: false
                }
                "global_action" -> {
                    val action = step.parameters["action"]?.toString() ?: ""
                    OvozAccessibilityService.instance?.executeGlobal(action) ?: false
                }
                "set_volume" -> {
                    val percent = (step.parameters["percent"] as? Number)?.toInt() ?: 50
                    setVolume(percent)
                }
                "toggle_setting" -> {
                    val setting = step.parameters["setting"]?.toString() ?: ""
                    val state = (step.parameters["state"] as? Boolean) ?: true
                    toggleSetting(setting, state)
                }
                "make_call" -> {
                    val contactOrNumber = step.parameters["contact_or_number"]?.toString() ?: ""
                    makeCall(contactOrNumber)
                }
                else -> false
            }
        } catch (e: Exception) {
            Log.e("OvozAI", "Buyruqni bajarishda xatolik: ${e.message}")
            false
        }
    }

    private fun setVolume(percent: Int): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVol = (maxVol * (percent.coerceIn(0, 100) / 100.0)).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVol, AudioManager.FLAG_SHOW_UI)
        return true
    }

    private fun toggleSetting(setting: String, state: Boolean): Boolean {
        return when (setting.lowercase()) {
            "flashlight", "chiroq", "fonar" -> {
                try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = cameraManager.cameraIdList[0]
                    cameraManager.setTorchMode(cameraId, state)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            "wifi" -> {
                val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
            "bluetooth" -> {
                val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                true
            }
            else -> false
        }
    }

    private fun makeCall(numberOrContact: String): Boolean {
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$numberOrContact")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
