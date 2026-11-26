package com.novelchat.data.remote.model

import com.google.gson.annotations.SerializedName

data class ChatResponse(
    @SerializedName("id") val id: String = "",
    @SerializedName("object") val object_: String = "",
    @SerializedName("created") val created: Long = 0,
    @SerializedName("model") val model: String = "",
    @SerializedName("choices") val choices: List<Choice> = emptyList(),
    @SerializedName("usage") val usage: Usage? = null
)

data class Choice(
    @SerializedName("index") val index: Int = 0,
    @SerializedName("delta") val delta: Delta? = null,
    @SerializedName("message") val message: ChatMessage? = null,
    @SerializedName("finish_reason") val finishReason: String? = null
)

data class Delta(
    @SerializedName("role") val role: String? = null,
    @SerializedName("content") val content: String? = null
)

data class Usage(
    @SerializedName("prompt_tokens") val promptTokens: Int = 0,
    @SerializedName("completion_tokens") val completionTokens: Int = 0,
    @SerializedName("total_tokens") val totalTokens: Int = 0,
    @SerializedName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int? = null,
    @SerializedName("prompt_tokens_details") val promptTokensDetails: PromptTokensDetails? = null
) {
    fun cachedTokens(): Int = promptCacheHitTokens ?: promptTokensDetails?.cachedTokens ?: 0
}

data class PromptTokensDetails(
    @SerializedName("cached_tokens") val cachedTokens: Int? = null
)
