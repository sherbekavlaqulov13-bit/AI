package com.ovozai.assistant.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log

data class AppItem(
    val label: String,
    val packageName: String,
    val normalizedLabel: String
)

class AppLauncher(private val context: Context) {

    private val installedApps = mutableListOf<AppItem>()

    // O'zbekcha / Ruscha sinonimlar xaritasi
    private val standardAliases = mapOf(
        "sozlama" to listOf("settings", "параметры", "настройки"),
        "sozlamalar" to listOf("settings", "параметры", "настройки"),
        "kalkulyator" to listOf("calculator", "калькулятор", "hisoblagich"),
        "kamera" to listOf("camera", "камера", "surat"),
        "galereya" to listOf("gallery", "photos", "галерея", "фото", "rasmlar"),
        "rasmlar" to listOf("gallery", "photos", "галерея", "фото"),
        "telefon" to listOf("phone", "dialer", "телефон", "qo'ng'iroq"),
        "kontaktlar" to listOf("contacts", "контакты", "raqamlar"),
        "xabarlar" to listOf("messages", "messaging", "sms", "сообщения"),
        "sms" to listOf("messages", "messaging", "sms", "сообщения"),
        "soat" to listOf("clock", "alarm", "часы", "budilnik"),
        "budilnik" to listOf("clock", "alarm", "часы", "budilnik"),
        "brauzer" to listOf("chrome", "browser", "браузер", "internet"),
        "internet" to listOf("chrome", "browser", "браузер"),
        "fayllar" to listOf("files", "file manager", "проводник", "hujjatlar"),
        "xaritalar" to listOf("maps", "карты", "navigator", "yandex maps"),
        "musiqa" to listOf("music", "spotify", "музыка", "pleyer", "yandex music"),
        "diktofon" to listOf("recorder", "sound recorder", "диктофон", "ovoz yozish"),
        "pochta" to listOf("gmail", "mail", "почта", "elektron pochta")
    )

    init {
        reloadApps()
    }

    fun reloadApps() {
        installedApps.clear()
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        for (ri in resolveInfos) {
            val label = ri.loadLabel(pm).toString()
            val pkg = ri.activityInfo.packageName
            installedApps.add(
                AppItem(
                    label = label,
                    packageName = pkg,
                    normalizedLabel = normalize(label)
                )
            )
        }

        // Qo'shimcha ravishda barcha o'rnatilgan ilovalarni ham qo'shamiz
        val allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (app in allApps) {
            if ((app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || app.packageName.startsWith("com.google.") || app.packageName.startsWith("com.android.")) {
                val label = pm.getApplicationLabel(app).toString()
                if (installedApps.none { it.packageName == app.packageName }) {
                    installedApps.add(
                        AppItem(
                            label = label,
                            packageName = app.packageName,
                            normalizedLabel = normalize(label)
                        )
                    )
                }
            }
        }
        Log.d("OvozAI", "Yuklangan ilovalar soni: ${installedApps.size}")
    }

    fun launchApp(query: String): Pair<Boolean, String> {
        val qNorm = normalize(query)
        if (installedApps.isEmpty()) {
            reloadApps()
        }

        // 1. To'g'ridan-to'g'ri moslik (aniq nom bo'yicha)
        var target = installedApps.firstOrNull { it.normalizedLabel == qNorm }

        // 2. Ilova nomi so'rovni o'z ichiga olgan bo'lsa
        if (target == null) {
            target = installedApps.firstOrNull { it.normalizedLabel.contains(qNorm) || qNorm.contains(it.normalizedLabel) }
        }

        // 3. Sinonimlar lug'ati bo'yicha qidirish
        if (target == null) {
            for ((aliasKey, synonyms) in standardAliases) {
                if (qNorm.contains(aliasKey) || aliasKey.contains(qNorm)) {
                    for (syn in synonyms) {
                        target = installedApps.firstOrNull {
                            it.normalizedLabel.contains(syn) || it.packageName.lowercase().contains(syn)
                        }
                        if (target != null) break
                    }
                }
                if (target != null) break
            }
        }

        // 4. Qisman so'zlar bo'yicha moslik
        if (target == null) {
            val words = qNorm.split(" ").filter { it.length > 2 }
            target = installedApps.firstOrNull { item ->
                words.any { w -> item.normalizedLabel.contains(w) || item.packageName.lowercase().contains(w) }
            }
        }

        if (target != null) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(target.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.d("OvozAI", "${target.label} (${target.packageName}) ochildi!")
                return Pair(true, target.label)
            }
        }

        return Pair(false, query)
    }

    private fun normalize(str: String): String {
        return str.lowercase()
            .replace("'", "")
            .replace("`", "")
            .replace("’", "")
            .replace("-", " ")
            .trim()
    }
}
