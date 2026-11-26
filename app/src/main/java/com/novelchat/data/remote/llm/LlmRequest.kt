package com.novelchat.data.remote.llm

import com.google.gson.JsonObject
import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.model.Usage

enum class ResponseMode {
    TEXT,
    JSON_OBJECT,
    JSON_SCHEMA
}

enum class LlmPriority {
    FOREGROUND,
    BACKGROUND
}

data class LlmMessage(
    val role: String,
    val content: String
)

data class LlmRequest(
    val stage: AiCallStage,
    val messages: List<LlmMessage>,
    val temperature: Float,
    val maxTokens: Int,
    val responseMode: ResponseMode = ResponseMode.TEXT,
    val jsonSchema: JsonObject? = null,
    val presencePenalty: Float? = null,
    val frequencyPenalty: Float? = null,
    val priority: LlmPriority = LlmPriority.BACKGROUND,
    val totalTimeoutMs: Long? = null,
    val idleTimeoutMs: Long? = null
)

sealed interface LlmStreamEvent {
    data class Delta(val text: String) : LlmStreamEvent
    data class Finished(
        val finishReason: String? = null,
        val usage: Usage? = null
    ) : LlmStreamEvent
}

data class LlmCompletion(
    val text: String,
    val finishReason: String? = null,
    val usage: Usage? = null
)
