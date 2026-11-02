package com.ovozai.assistant.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiBrain(private val apiKeyProvider: () -> String) {

    private val gson = Gson()
    private val onDeviceEngine = OnDeviceUzbekEngine()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    companion object {
        private const val SYSTEM_PROMPT = """
Siz Android smartfonini to'liq ovoz orqali mustaqil boshqaruvchi O'zbek tilidagi aqlli AI Yordamchisiz (OvozAI).
Siz foydalanuvchining har qanday o'zbekcha buyrug'ini tushunib:
1. Foydalanuvchi bilan samimiy, aniq va ravon o'zbek tilida gaplashishingiz kerak (voice_response).
2. Telefonda bajariladigan amallar rejasini toza JSON ro'yxati (execution_plan) shaklida tuzishingiz kerak.

MUMKIN BO'LGAN AMALLAR (tools):
- open_app: telefondagi HAR QANDAY ilovani ochish (parameters: {"app_name": "Telegram" | "Payme" | "Click" | "Instagram" | "YouTube" | "Sozlamalar" | "Kalkulyator" | ...})
- check_messages: yangi kelgan xabarlarni o'qish (parameters: {})
- reply_message: oxirgi yoki aytilgan kontaktga to'g'ridan-to'g'ri xabar yozish (parameters: {"sender": "Akmal", "text": "Hozir yetib boraman"})
- send_app_message: messenjer ichiga kirib avtonom xabar yuborish (parameters: {"app_name": "Telegram", "recipient": "Akmal", "text": "Salom"})
- click_text: ekrandagi berilgan matnli tugmani bosish (parameters: {"text": "Yuborish" | "Qo'ng'iroq" | ...})
- type_text: ekranda faol maydonga matn yozish (parameters: {"text": "Salom..."})
- scroll: ekranni aylantirish (parameters: {"direction": "down" | "up"})
- global_action: tizim amallari (parameters: {"action": "home" | "back" | "recents" | "notifications" | "lock" | "screenshot"})
- set_volume: ovoz balandligini sozlash (parameters: {"percent": 0..100})
- toggle_setting: sozlamani yoqish/o'chirish (parameters: {"setting": "wifi" | "bluetooth" | "flashlight", "state": true | false})
- make_call: qo'ng'iroq qilish (parameters: {"contact_or_number": "Ali"})

MUHIM QOIDALAR:
1. Agar foydalanuvchi "yangi xabarlar bormi?", "kimdir yozdimi?", "xabarlarni o'qib ber" deb so'rasa:
   - Agar kontekstda kelgan xabarlar bo'lsa, ularni kimdan kelganini va matnini ayting, so'ng: "Unga biror nima deb yozib yuboraymi?" deb so'rang.
   - execution_plan bo'sh [] bo'ladi.
2. Agar foydalanuvchi avvalgi savolga javoban "Ha, [matn] deb yoz" yoki "Unga [matn] deb javob ber" desa:
   - reply_message amalini chaqiring va parameters ga text va sender ni bering!
   - voice_response: "Xabar yuborildi."
3. Har qanday ilova aytilganda open_app dan foydalaning.
4. Faqat toza JSON formatida javob qaytaring.
"""
    }

    suspend fun analyzeIntent(
        userSpeech: String,
        visibleScreenText: String = "",
        incomingMessagesContext: String = "",
        lastTurnContext: String = ""
    ): AiResponse = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()

        // 1. Agar API kalit bo'lmasa, 100% BEPUL lokal dvigatel orqali zudlik bilan ishlaydi
        if (apiKey.isEmpty()) {
            return@withContext onDeviceEngine.process(userSpeech, incomingMessagesContext, lastTurnContext)
        }

        // 2. Agar API kalit bo'lsa (Google AI Studio bepul kvotasi)
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val promptBuilder = StringBuilder()
            promptBuilder.append("Foydalanuvchi buyrug'i: \"$userSpeech\"\n")
            if (incomingMessagesContext.isNotEmpty()) {
                promptBuilder.append("Telefonidagi so'nggi xabarlar: $incomingMessagesContext\n")
            }
            if (lastTurnContext.isNotEmpty()) {
                promptBuilder.append("Avvalgi suhbat konteksti: $lastTurnContext\n")
            }
            if (visibleScreenText.isNotEmpty()) {
                promptBuilder.append("Ekrandagi ko'rinib turgan matnlar: [$visibleScreenText]\n")
            }

            val jsonBody = """
            {
              "contents": [
                {
                  "role": "user",
                  "parts": [{"text": ${gson.toJson(promptBuilder.toString())}}]
                }
              ],
              "systemInstruction": {
                "parts": [{"text": ${gson.toJson(SYSTEM_PROMPT)}}]
              },
              "generationConfig": {
                "responseMimeType": "application/json",
                "temperature": 0.2
              }
            }
            """.trimIndent()

            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext onDeviceEngine.process(userSpeech, incomingMessagesContext, lastTurnContext)
            }

            val bodyString = response.body?.string() ?: ""
            val jsonObject = gson.fromJson(bodyString, JsonObject::class.java)
            val candidates = jsonObject.getAsJsonArray("candidates")
            if (candidates != null && candidates.size() > 0) {
                val contentObj = candidates.get(0).asJsonObject.getAsJsonObject("content")
                val parts = contentObj.getAsJsonArray("parts")
                val textResponse = parts.get(0).asJsonObject.get("text").asString
                return@withContext gson.fromJson(textResponse, AiResponse::class.java)
            }

            return@withContext onDeviceEngine.process(userSpeech, incomingMessagesContext, lastTurnContext)
        } catch (e: Exception) {
            // Internet uzilsa yoki xatolik bo'lsa, zaxiradagi bepul dvigatelga o'tadi
            return@withContext onDeviceEngine.process(userSpeech, incomingMessagesContext, lastTurnContext)
        }
    }
}
