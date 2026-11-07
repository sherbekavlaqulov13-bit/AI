package com.ovozai.assistant.service

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
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

                // 2. Yangi kelgan xabarlarga javob yozish (Notification RemoteInput orqali)
                "reply_message" -> {
                    val replyText = step.parameters["text"]?.toString() ?: ""
                    val sender = step.parameters["sender"]?.toString() ?: ""
                    if (sender.isNotEmpty()) {
                        OvozNotificationListenerService.replyTo(sender, replyText, context)
                    } else {
                        OvozNotificationListenerService.replyToLast(replyText, context)
                    }
                }

                // 3. Ilovada xabar yuborish / Chat ochish
                "send_app_message" -> {
                    val appName = step.parameters["app_name"]?.toString() ?: "Telegram"
                    val recipient = step.parameters["recipient"]?.toString() ?: ""
                    val text = step.parameters["text"]?.toString() ?: ""

                    // Agar messenjer Telegram bo'lsa va kontakt berilgan bo'lsa, chat ochishga harakat qiladi
                    if (appName.equals("Telegram", ignoreCase = true) && recipient.isNotBlank() && recipient != "Kontakt") {
                        try {
                            val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=$recipient")).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(tgIntent)
                            true
                        } catch (e: Exception) {
                            val (success, _) = appLauncher.launchApp(appName)
                            success
                        }
                    } else if (appName.equals("WhatsApp", ignoreCase = true) && text.isNotBlank()) {
                        try {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                                `package` = "com.whatsapp"
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(sendIntent)
                            true
                        } catch (e: Exception) {
                            val (success, _) = appLauncher.launchApp("WhatsApp")
                            success
                        }
                    } else {
                        val (success, _) = appLauncher.launchApp(appName)
                        success
                    }
                }

                // 4. Ovoz balandligini sozlash
                "set_volume" -> {
                    val percent = (step.parameters["percent"] as? Number)?.toInt() ?: 50
                    setVolume(percent)
                }

                // 5. Fonar / Chiroq yoqish va o'chirish
                "toggle_setting" -> {
                    val setting = step.parameters["setting"]?.toString() ?: ""
                    val state = (step.parameters["state"] as? Boolean) ?: true
                    toggleSetting(setting, state)
                }

                // 6. Telefon qilish
                "make_call" -> {
                    val contactOrNumber = step.parameters["contact_or_number"]?.toString() ?: ""
                    makeCall(contactOrNumber)
                }

                // 7. Kamerani ochish va rasmga olish rejimiga o'tish
                "open_camera", "take_photo" -> {
                    openCamera()
                }

                // 8. Bosh ekranga qaytish
                "home" -> {
                    goHome()
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
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$numberOrContact")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                true
            } catch (e2: Exception) {
                false
            }
        }
    }

    private fun openCamera(): Boolean {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            val (ok, _) = appLauncher.launchApp("kamera")
            ok
        }
    }

    private fun goHome(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
