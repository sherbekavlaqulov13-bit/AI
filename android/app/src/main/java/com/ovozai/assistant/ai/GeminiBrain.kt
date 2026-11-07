package com.ovozai.assistant.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 100% BEPUL va hech qanday API kalitsiz ishlovchi OvozAI Dvigateli.
 */
class GeminiBrain {

    private val onDeviceEngine = OnDeviceUzbekEngine()

    suspend fun analyzeIntent(
        userSpeech: String,
        incomingMessagesContext: String = "",
        lastTurnContext: String = ""
    ): AiResponse = withContext(Dispatchers.Default) {
        onDeviceEngine.process(
            input = userSpeech,
            incomingMessagesContext = incomingMessagesContext,
            lastTurnContext = lastTurnContext
        )
    }
}
