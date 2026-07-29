package com.novelchat.data.remote

import com.novelchat.data.remote.model.ThinkingConfig
import java.net.URI

internal object ChatRequestPolicy {

    fun reasoningEffort(baseUrl: String, model: String): String? {
        if (!ApiEndpointPolicy.isLocalEndpoint(baseUrl)) return null
        val normalizedModel = model.trim().lowercase()
        val isControllableReasoningModel =
            normalizedModel == "qwen" ||
                localReasoningModelMarkers.any(normalizedModel::contains)
        return "none".takeIf { isControllableReasoningModel }
    }

    fun thinkingConfig(baseUrl: String, model: String): ThinkingConfig? {
        val host = runCatching { URI(baseUrl.trim()).host.orEmpty().lowercase() }
            .getOrDefault("")
        val normalizedModel = model.trim().lowercase()
        val isDeepSeekV4 = normalizedModel == "deepseek-v4-flash" ||
                normalizedModel == "deepseek-v4-pro"
        return ThinkingConfig().takeIf {
            host == "api.deepseek.com" && isDeepSeekV4
        }
    }

    private val localReasoningModelMarkers = listOf(
        "gemma4",
        "gemma-4",
        "qwen3"
    )
}
