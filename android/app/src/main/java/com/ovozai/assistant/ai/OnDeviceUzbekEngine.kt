package com.ovozai.assistant.ai

import java.util.Locale

class OnDeviceUzbekEngine {

    /**
     * O'zbek tili sun'iy idrok dvigateli:
     * - Chat va xabarlarni tushunish
     * - Aloqa va kontaktlar
     * - Telefondagi barcha ilovalarni aniqlash
     * - Fonar, kamera, qo'ng'iroq va tizim buyruqlari
     */
    fun process(
        input: String,
        incomingMessagesContext: String = "",
        lastTurnContext: String = ""
    ): AiResponse {
        val raw = input.trim()
        val text = normalize(raw)
        val plan = mutableListOf<ActionStep>()

        // 1. Yangi xabarlarni o'qish ("kimdir yozdimi", "xabarlar bormi", "xabarlarni o'qi")
        if (isAskingForMessages(text)) {
            val response = if (incomingMessagesContext.isNotBlank()) {
                incomingMessagesContext
            } else {
                "Hozircha sizga yangi xabarlar kelmagan."
            }
            return AiResponse(voiceResponse = response, executionPlan = emptyList())
        }

        // 2. Avvalgi xabarga javob yozish ("Ha, yetib bordim deb yoz", "Ha", "ha javob yoz")
        if (lastTurnContext.contains("xabar") && (text.startsWith("ha") || text.contains("deb yoz") || text.contains("yoz"))) {
            val replyText = extractReplyMessage(text)
            plan.add(ActionStep("reply_message", mapOf("text" to replyText)))
            return AiResponse(
                voiceResponse = "Xabar yuborildi: $replyText",
                executionPlan = plan
            )
        }

        // 3. Kimgadir xabar yozish / Chat ochish (Telegram, WhatsApp, SMS va barcha messenjerlar)
        // Masalan: "Telegramda Akmalga salom deb yoz", "Akmalga xabar yoz", "Chatni och", "Telegramni ochib Akmalga yoz"
        if (isChatOrMessageCommand(text)) {
            val targetApp = detectMessagingApp(text)
            val recipient = extractRecipient(text)
            val messageText = extractMessageBody(text)

            plan.add(
                ActionStep(
                    tool = "send_app_message",
                    parameters = mapOf(
                        "app_name" to targetApp,
                        "recipient" to recipient,
                        "text" to messageText
                    )
                )
            )

            val voiceRes = if (recipient.isNotBlank() && recipient != "Kontakt") {
                "$targetApp ochilmoqda. $recipient ga xabar yuborishga tayyor."
            } else {
                "$targetApp chatlari ochilmoqda."
            }

            return AiResponse(voiceResponse = voiceRes, executionPlan = plan)
        }

        // 4. Salomatlik va erkin suhbat (AI muloqoti)
        if (isGreeting(text)) {
            val greetings = listOf(
                "Assalomu alaykum! Xizmatdaman, qanday buyruq bajaramiz?",
                "Vaalaykum assalom! Qanday yordam bera olaman?",
                "Salom! Telefoningizda qaysi ilovani ochaylik yoki kimgadir yozaylikmi?"
            )
            return AiResponse(voiceResponse = greetings.random(), executionPlan = emptyList())
        }

        if (text.contains("qalaysan") || text.contains("ishlar qalay") || text.contains("qandaysan") || text.contains("nima gap")) {
            return AiResponse(
                voiceResponse = "Rahmat, hammasi a'lo! Sizga xizmat qilishdan mamnunman. Buyrug'ingiz bormi?",
                executionPlan = emptyList()
            )
        }

        if (text.contains("kim san") || text.contains("kimsan") || text.contains("isming nima")) {
            return AiResponse(
                voiceResponse = "Men OvozAI — telefoningizni to'liq o'zbek tilida ovoz orqali boshqaruvchi aqlli yordamchingizman.",
                executionPlan = emptyList()
            )
        }

        if (text.contains("rahmat") || text.contains("tashakkur") || text.contains("barakalla") || text.contains("gap yoq")) {
            return AiResponse(
                voiceResponse = "Arzimaydi, doimo xizmatingizdaman!",
                executionPlan = emptyList()
            )
        }

        // 5. Fonar / Chiroq
        if (text.contains("fonar") || text.contains("chiroq") || text.contains("fonarni") || text.contains("chiroqni")) {
            val state = !(text.contains("o'chir") || text.contains("yop") || text.contains("ochir"))
            plan.add(ActionStep("toggle_setting", mapOf("setting" to "flashlight", "state" to state)))
            return AiResponse(
                voiceResponse = if (state) "Fonar yoqildi." else "Fonar o'chirildi.",
                executionPlan = plan
            )
        }

        // 6. Rasmga olish / Kamera
        if (text.contains("rasmga ol") || text.contains("rasm ol") || text.contains("foto ol") || text.contains("suratga ol") || text.contains("kamera")) {
            plan.add(ActionStep("open_camera", emptyMap()))
            return AiResponse(voiceResponse = "Kamera ochilmoqda, rasmga olishga tayyor.", executionPlan = plan)
        }

        // 7. Qo'ng'iroq qilish / Telefon qilish ("Ali akaga telefon qil", "998901234567 ga tel qil")
        if (text.contains("qo'ng'iroq") || text.contains("qongiroq") || text.contains("telefon qil") || text.contains("tel qil") || text.contains("go'shakni ko'tar")) {
            val contact = extractContact(text)
            plan.add(ActionStep("make_call", mapOf("contact_or_number" to contact)))
            return AiResponse(voiceResponse = "$contact ga qo'ng'iroq qilinmoqda.", executionPlan = plan)
        }

        // 8. Ovoz balandligi
        if (text.contains("ovoz") || text.contains("ovozni")) {
            when {
                text.contains("pasay") || text.contains("kam") || text.contains("pasayt") -> {
                    plan.add(ActionStep("set_volume", mapOf("percent" to 25)))
                    return AiResponse(voiceResponse = "Ovoz pasaytirildi.", executionPlan = plan)
                }
                text.contains("ko'tar") || text.contains("oshir") || text.contains("baland") || text.contains("kotar") -> {
                    plan.add(ActionStep("set_volume", mapOf("percent" to 85)))
                    return AiResponse(voiceResponse = "Ovoz ko'tarildi.", executionPlan = plan)
                }
                text.contains("o'chir") || text.contains("ovozsiz") -> {
                    plan.add(ActionStep("set_volume", mapOf("percent" to 0)))
                    return AiResponse(voiceResponse = "Ovoz o'chirildi.", executionPlan = plan)
                }
                text.contains("maksimal") || text.contains("oxirigacha") -> {
                    plan.add(ActionStep("set_volume", mapOf("percent" to 100)))
                    return AiResponse(voiceResponse = "Ovoz to'liq baland qilindi.", executionPlan = plan)
                }
            }
        }

        // 9. Tizim navigatsiyasi (Bosh ekran)
        if (text.contains("bosh sahifa") || text.contains("bosh ekran") || text.contains("uyga") || text.contains("home")) {
            plan.add(ActionStep("home", emptyMap()))
            return AiResponse(voiceResponse = "Bosh ekranga qaytildi.", executionPlan = plan)
        }

        // 10. Telefondagi ilovalarni ochish ("YouTube", "Telegram", "Instagram", "Payme", "Click", "Sozlamalar", "Kalkulyator", ...)
        val appQuery = extractAppQuery(text)
        if (appQuery.isNotBlank()) {
            plan.add(ActionStep("open_app", mapOf("app_name" to appQuery)))
            return AiResponse(voiceResponse = "$appQuery ochilmoqda.", executionPlan = plan)
        }

        // 11. Tushunarsiz yoki umumiy gap bo'lsa
        return AiResponse(
            voiceResponse = "Buyrug'ingiz qabul qilindi. Bajaryapman.",
            executionPlan = plan
        )
    }

    private fun isAskingForMessages(t: String): Boolean {
        return (t.contains("xabar") || t.contains("sms") || t.contains("kim yozdi") || t.contains("bildirishnoma")) &&
               !t.contains("yoz ") && !t.contains("yubor")
    }

    private fun isChatOrMessageCommand(t: String): Boolean {
        return (t.contains("yoz") || t.contains("yubor") || t.contains("chat") || t.contains("sms yoz")) &&
               (t.contains("telegram") || t.contains("whatsapp") || t.contains("sms") || t.contains("ga ") || t.contains("kimgadir"))
    }

    private fun detectMessagingApp(t: String): String {
        return when {
            t.contains("whatsapp") -> "WhatsApp"
            t.contains("sms") || t.contains("xabar") -> "Xabarlar"
            t.contains("instagram") -> "Instagram"
            else -> "Telegram"
        }
    }

    private fun extractRecipient(t: String): String {
        val words = t.split(" ")
        for (w in words) {
            if (w.endsWith("ga") && !w.startsWith("tele") && !w.startsWith("what") && !w.startsWith("insta") && !w.startsWith("qo'n")) {
                return w.removeSuffix("ga").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
        return "Kontakt"
    }

    private fun extractMessageBody(t: String): String {
        if (t.contains("deb")) {
            val parts = t.split("deb")
            return parts[0].split(" ").takeLast(4).joinToString(" ").trim()
        }
        if (t.contains("yoz")) {
            val parts = t.split("yoz")
            return parts[0].split(" ").takeLast(3).joinToString(" ").trim()
        }
        return "Salom"
    }

    private fun isGreeting(t: String): Boolean {
        return t.contains("salom") || t.contains("assalom") || t.contains("salomualaykum")
    }

    private fun extractReplyMessage(t: String): String {
        var clean = t
        clean = clean.replace(Regex("^ha[,.]?\\s*"), "")
        clean = clean.replace(Regex("\\s*deb\\s*yoz\\s*$"), "")
        clean = clean.replace(Regex("\\s*yozib\\s*yubor\\s*$"), "")
        clean = clean.replace(Regex("\\s*yoz\\s*$"), "")
        clean = clean.trim()
        return clean.ifBlank { "Hozir yetib boraman" }
    }

    private fun extractAppQuery(t: String): String {
        var q = t
        val triggers = listOf("och", "kir", "boshla", "yoq", "ishga tushir", "app", "ilova")
        for (tr in triggers) {
            q = q.replace(tr, "")
        }
        q = q.replace("ilovani", "")
            .replace("dasturni", "")
            .replace("ni", "")
            .replace("ga", "")
            .replace("chiq", "")
            .trim()

        return q
    }

    private fun extractContact(t: String): String {
        val words = t.split(" ")
        for (w in words) {
            if (w.endsWith("ga") && !w.startsWith("qo'n") && !w.startsWith("tel")) {
                return w.removeSuffix("ga").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
        // Agar raqam bo'lsa
        val digits = t.filter { it.isDigit() }
        if (digits.length >= 7) return digits

        return "Kontakt"
    }

    private fun normalize(str: String): String {
        return str.lowercase(Locale.ROOT)
            .replace("’", "'")
            .replace("`", "'")
            .replace("‘", "'")
            .replace("  ", " ")
            .trim()
    }
}
