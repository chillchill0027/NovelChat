package com.novelchat.data.remote.model

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class ChatRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<ChatMessage>,
    @SerializedName("stream") val stream: Boolean = true,
    @SerializedName("temperature") val temperature: Float = 0.7f,
    @SerializedName("max_tokens") val maxTokens: Int? = null,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null,
    @SerializedName("reasoning_effort") val reasoningEffort: String? = null,
    @SerializedName("thinking") val thinking: ThinkingConfig? = null
)

data class ResponseFormat(
    @SerializedName("type") val type: String = "json_object",
    @SerializedName("json_schema") val jsonSchema: JsonSchemaFormat? = null
)

data class JsonSchemaFormat(
    @SerializedName("name") val name: String,
    @SerializedName("strict") val strict: Boolean = false,
    @SerializedName("schema") val schema: JsonObject
)

data class ThinkingConfig(
    @SerializedName("type") val type: String = "disabled"
)

data class ChatMessage(
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String
)
