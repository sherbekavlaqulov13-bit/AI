package com.ovozai.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 100% BEPUL va hech qanday API kalit yoki to'lovlarsiz ishlovchi OvozAI Dvigateli.
 */
class GeminiBrain(private val apiKeyProvider: () -> String = { "" }) {

    private val onDeviceEngine = OnDeviceUzbekEngine()

    suspend fun analyzeIntent(
        userSpeech: String,
        visibleScreenText: String = "",
        incomingMessagesContext: String = "",
        lastTurnContext: String = ""
    ): AiResponse = withContext(Dispatchers.Default) {
        // Har doim lokal, tekin va tezkor sun'iy idrok dvigatelidan foydalanadi
        onDeviceEngine.process(
            input = userSpeech,
            incomingMessagesContext = incomingMessagesContext,
            lastTurnContext = lastTurnContext
        )
    }
}
