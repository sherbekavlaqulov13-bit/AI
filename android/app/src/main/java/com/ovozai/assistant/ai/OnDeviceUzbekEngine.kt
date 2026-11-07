package com.ovozai.assistant.ai

import java.util.Locale

class OnDeviceUzbekEngine {

    /**
     * Internet yoki API kalitisiz, 100% bepul va avtonom ishlovchi O'zbek tili sun'iy idrok dvigateli.
     */
    fun process(
        input: String,
        incomingMessagesContext: String = "",
        lastTurnContext: String = ""
    ): AiResponse {
        val raw = input.trim()
        val text = normalize(raw)
        val plan = mutableListOf<ActionStep>()

        // 1. Yangi xabarlar bormi deb so'ralganda / xabarlarni o'qish
        if (isAskingForMessages(text)) {
            val response = if (incomingMessagesContext.isNotBlank()) {
                incomingMessagesContext
            } else {
                "Hozircha sizga yangi xabarlar kelmagan."
            }
            return AiResponse(voiceResponse = response, executionPlan = emptyList())
        }

        // 2. Avvalgi savolga javoban xabar yozish ("Ha, yetib bordim deb yoz")
        if (lastTurnContext.contains("xabar") && (text.startsWith("ha") || text.contains("deb yoz") || text.contains("yoz"))) {
            val replyText = extractReplyMessage(text)
            plan.add(ActionStep("reply_message", mapOf("text" to replyText)))
            return AiResponse(
                voiceResponse = "Xabar yuborildi: \"$replyText\"",
                executionPlan = plan
            )
        }

        // 3. To'g'ridan-to'g'ri biror ilovada xabar yozish (masalan: "Telegramda Akmalga salom deb yoz")
        if ((text.contains("yoz") || text.contains("yubor")) && (text.contains("telegram") || text.contains("whatsapp") || text.contains("sms") || text.contains("ga "))) {
            val app = when {
                text.contains("whatsapp") -> "WhatsApp"
                text.contains("sms") -> "Messages"
                else -> "Telegram"
            }

            var recipient = "Kontakt"
            val words = text.split(" ")
            for (w in words) {
                if (w.endsWith("ga") && !w.startsWith("tele") && !w.startsWith("what")) {
                    recipient = w.removeSuffix("ga").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                }
            }

            var messageContent = "Salom"
            if (text.contains("deb")) {
                val parts = text.split("deb")
                messageContent = parts[0].split(" ").takeLast(4).joinToString(" ").trim()
            } else if (text.contains("yoz")) {
                val parts = text.split("yoz")
                messageContent = parts[0].split(" ").takeLast(3).joinToString(" ").trim()
            }

            plan.add(ActionStep("send_app_message", mapOf("app_name" to app, "recipient" to recipient, "text" to messageContent)))
            return AiResponse(
                voiceResponse = "$app orqali $recipient ga \"$messageContent\" xabari yuborilmoqda.",
                executionPlan = plan
            )
        }

        // 4. Salomatlik va erkin suhbat (Small Talk)
        if (isGreeting(text)) {
            val greetings = listOf(
                "Assalomu alaykum! Telefoningizni boshqarishga tayyorman, qanday buyruq bajaramiz?",
                "Vaalaykum assalom! Xizmatdaman, nimani ochib beray yoki qanday yordam bera olaman?",
                "Salom! Qanday buyruq bajaramiz?"
            )
            return AiResponse(voiceResponse = greetings.random(), executionPlan = emptyList())
        }

        if (text.contains("qalaysan") || text.contains("ishlar qalay") || text.contains("qandaysan") || text.contains("yaxshimisiz")) {
            return AiResponse(
                voiceResponse = "Rahmat, a'loman! Sizga xizmat qilishdan xursandman. Telefoningizda biror amal bajaramizmi?",
                executionPlan = emptyList()
            )
        }

        if (text.contains("kim san") || text.contains("kimsan") || text.contains("nima bu")) {
            return AiResponse(
                voiceResponse = "Men OvozAI — telefoningizni to'liq o'zbek tilida ovoz orqali boshqaruvchi bepul aqlli yordamchingizman.",
                executionPlan = emptyList()
            )
        }

        if (text.contains("rahmat") || text.contains("tashakkur") || text.contains("barakalla")) {
            return AiResponse(
                voiceResponse = "Arzimaydi, doim xizmatingizdaman!",
                executionPlan = emptyList()
            )
        }

        // 5. Fonar / Chiroq boshqaruvi
        if (text.contains("fonar") || text.contains("chiroq") || text.contains("fonarni") || text.contains("chiroqni")) {
            val state = !(text.contains("o'chir") || text.contains("yop") || text.contains("ochir"))
            plan.add(ActionStep("toggle_setting", mapOf("setting" to "flashlight", "state" to state)))
            return AiResponse(
                voiceResponse = if (state) "Fonar yoqildi." else "Fonar o'chirildi.",
                executionPlan = plan
            )
        }

        // 6. Rasmga olish / Kamera
        if (text.contains("rasmga ol") || text.contains("rasm ol") || text.contains("foto ol") || text.contains("suratga ol") || text.contains("kamerani och")) {
            plan.add(ActionStep("open_camera", emptyMap()))
            return AiResponse(voiceResponse = "Kamera ochilmoqda, rasmga olishga tayyor.", executionPlan = plan)
        }

        // 7. Qo'ng'iroq qilish / Telefon qilish
        if (text.contains("qo'ng'iroq") || text.contains("qongiroq") || text.contains("telefon qil") || text.contains("tel qil")) {
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

        // 9. Tizim navigatsiyasi (Home, Back, Recents)
        if (text.contains("bosh sahifa") || text.contains("bosh ekran") || text.contains("uyga") || text.contains("home")) {
            plan.add(ActionStep("global_action", mapOf("action" to "home")))
            return AiResponse(voiceResponse = "Bosh ekranga qaytildi.", executionPlan = plan)
        }

        if (text.contains("orqaga") || text.contains("back")) {
            plan.add(ActionStep("global_action", mapOf("action" to "back")))
            return AiResponse(voiceResponse = "Orqaga qaytildi.", executionPlan = plan)
        }

        if (text.contains("skrinshot") || text.contains("ekran rasmi") || text.contains("screenshot") || text.contains("ss ol")) {
            plan.add(ActionStep("global_action", mapOf("action" to "screenshot")))
            return AiResponse(voiceResponse = "Ekran rasmi olindi.", executionPlan = plan)
        }

        if (text.contains("ochiq ilova") || text.contains("so'nggi ilova") || text.contains("recents")) {
            plan.add(ActionStep("global_action", mapOf("action" to "recents")))
            return AiResponse(voiceResponse = "Ochiq ilovalar ko'rsatilmoqda.", executionPlan = plan)
        }

        // 10. Skroll (aylantirish)
        if (text.contains("pastga") || text.contains("pastga tush") || text.contains("quyiga")) {
            plan.add(ActionStep("scroll", mapOf("direction" to "down")))
            return AiResponse(voiceResponse = "Pastga aylantirildi.", executionPlan = plan)
        }

        if (text.contains("tepaga") || text.contains("yuqoriga") || text.contains("tepaga chiq")) {
            plan.add(ActionStep("scroll", mapOf("direction" to "up")))
            return AiResponse(voiceResponse = "Tepaga aylantirildi.", executionPlan = plan)
        }

        // 11. Ekrandagi tugmani bosish
        if (text.contains("bos") || text.contains("bosing") || text.contains("tugmasini bos")) {
            val btnTarget = text.replace("tugmasini", "").replace("tugmani", "").replace("bos", "").replace("bosing", "").trim()
            if (btnTarget.isNotBlank()) {
                plan.add(ActionStep("click_text", mapOf("text" to btnTarget)))
                return AiResponse(voiceResponse = "\"$btnTarget\" bosilmoqda.", executionPlan = plan)
            }
        }

        // 12. Telefondagi hamma ilovalarni ochish
        val appQuery = extractAppQuery(text)
        if (appQuery.isNotBlank()) {
            plan.add(ActionStep("open_app", mapOf("app_name" to appQuery)))
            return AiResponse(voiceResponse = "$appQuery ochilmoqda.", executionPlan = plan)
        }

        // 13. Erkin / umumiy javob
        return AiResponse(
            voiceResponse = "Buyrug'ingizni tushundim. Telefoningizda bajarilmoqda.",
            executionPlan = plan
        )
    }

    private fun isAskingForMessages(t: String): Boolean {
        return (t.contains("xabar") || t.contains("sms") || t.contains("kim yozdi") || t.contains("bildirishnoma")) &&
               !t.contains("yoz ") && !t.contains("yubor")
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
        if (t.contains("och") || t.contains("kir") || t.contains("boshla") || t.contains("yoq")) {
            var q = t
            q = q.replace("och", "")
                .replace("kir", "")
                .replace("ilovani", "")
                .replace("dasturni", "")
                .replace("boshla", "")
                .replace("yoq", "")
                .replace("ni", "")
                .replace("ga", "")
                .trim()
            return q
        }
        return ""
    }

    private fun extractContact(t: String): String {
        val words = t.split(" ")
        for (w in words) {
            if (w.endsWith("ga") && !w.startsWith("qo'n")) {
                return w.removeSuffix("ga").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }
        }
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
