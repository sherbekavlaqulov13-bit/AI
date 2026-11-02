package com.ovozai.assistant.service

import android.app.Notification
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

data class IncomingMessage(
    val packageName: String,
    val appName: String,
    val sender: String,
    val text: String,
    val timestamp: Long,
    val notificationKey: String,
    val directReplyAction: Notification.Action? = null
)

class OvozNotificationListenerService : NotificationListenerService() {

    companion object {
        var instance: OvozNotificationListenerService? = null
            private set

        val isRunning: Boolean
            get() = instance != null

        // Oxirgi kelgan xabarlar ro'yxati (eng yangilari birinchi)
        val recentMessages = mutableListOf<IncomingMessage>()
        var lastReceivedMessage: IncomingMessage? = null

        fun getUnreadSummary(): String {
            if (recentMessages.isEmpty()) {
                return "Hozircha sizga yangi xabarlar kelmagan."
            }

            val latest = recentMessages.take(3)
            val summaries = latest.map {
                "${it.appName}dan ${it.sender} yozgan: \"${it.text}\""
            }

            val text = summaries.joinToString(". ")
            return "$text. Unga biror nima deb javob yozib yuboraymi?"
        }

        fun replyToLast(replyText: String, context: Context): Boolean {
            val last = lastReceivedMessage ?: recentMessages.firstOrNull() ?: return false
            return sendReply(last, replyText, context)
        }

        fun replyTo(senderName: String, replyText: String, context: Context): Boolean {
            val target = recentMessages.firstOrNull {
                it.sender.lowercase().contains(senderName.lowercase()) ||
                it.appName.lowercase().contains(senderName.lowercase())
            } ?: lastReceivedMessage ?: return false

            return sendReply(target, replyText, context)
        }

        private fun sendReply(msg: IncomingMessage, replyText: String, context: Context): Boolean {
            val action = msg.directReplyAction
            if (action != null && action.remoteInputs != null) {
                for (remoteInput in action.remoteInputs) {
                    val bundle = Bundle().apply {
                        putCharSequence(remoteInput.resultKey, replyText)
                    }
                    val intent = Intent()
                    RemoteInput.addResultsToIntent(action.remoteInputs, intent, bundle)
                    try {
                        action.actionIntent.send(context, 0, intent)
                        Log.d("OvozAI", "${msg.sender}ga RemoteInput orqali to'g'ridan-to'g'ri javob yuborildi: $replyText")
                        return true
                    } catch (e: Exception) {
                        Log.e("OvozAI", "RemoteInput yuborishda xato: ${e.message}")
                    }
                }
            }
            return false
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.d("OvozAI", "OvozNotificationListenerService ulandi!")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        Log.d("OvozAI", "OvozNotificationListenerService uzildi!")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val pkg = sbn.packageName
        // Faqat messenjer va xabar ilovalarini tahlil qilamiz
        val messagingPackages = setOf(
            "org.telegram.messenger",
            "org.telegram.plus",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.instagram.android",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.android.mms",
            "com.facebook.orca",
            "com.viber.voip"
        )

        val isMessagingApp = messagingPackages.contains(pkg) || pkg.contains("msg") || pkg.contains("sms")
        if (!isMessagingApp) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""

        // Agar sarlavha yoki matn bo'sh bo'lsa, saqlamaymiz
        if (title.isEmpty() || text.isEmpty()) return
        if (title.contains("Telegram") && text.contains("yangi xabar")) return

        val appName = getFriendlyAppName(pkg)
        val replyAction = findDirectReplyAction(sbn.notification)

        val incoming = IncomingMessage(
            packageName = pkg,
            appName = appName,
            sender = title,
            text = text,
            timestamp = sbn.postTime,
            notificationKey = sbn.key,
            directReplyAction = replyAction
        )

        synchronized(recentMessages) {
            recentMessages.removeAll { it.sender == title && it.text == text }
            recentMessages.add(0, incoming)
            if (recentMessages.size > 20) {
                recentMessages.removeAt(recentMessages.size - 1)
            }
        }
        lastReceivedMessage = incoming

        Log.d("OvozAI", "Yangi xabar qabul qilindi: [$appName] $title: $text")
    }

    private fun findDirectReplyAction(notification: Notification): Notification.Action? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.allowFreeFormInput) {
                    return action
                }
            }
        }
        return null
    }

    private fun getFriendlyAppName(pkg: String): String {
        return when {
            pkg.contains("telegram") -> "Telegram"
            pkg.contains("whatsapp") -> "WhatsApp"
            pkg.contains("instagram") -> "Instagram"
            pkg.contains("messaging") || pkg.contains("mms") -> "SMS"
            pkg.contains("orca") -> "Messenger"
            else -> try {
                val pm = packageManager
                val appInfo = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                "Xabarlar"
            }
        }
    }
}
