package com.ovozai.assistant.ai

import com.google.gson.annotations.SerializedName

data class ActionStep(
    @SerializedName("tool")
    val tool: String,
    @SerializedName("parameters")
    val parameters: Map<String, Any?> = emptyMap()
)

data class AiResponse(
    @SerializedName("voice_response")
    val voiceResponse: String = "",
    @SerializedName("execution_plan")
    val executionPlan: List<ActionStep> = emptyList()
)
