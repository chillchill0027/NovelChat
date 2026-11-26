package com.novelchat.data.remote

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.novelchat.data.remote.model.PromptTokensDetails
import com.novelchat.data.remote.model.Usage
import java.io.IOException

internal data class ChatStreamEvent(
    val content: String? = null,
    val finishReason: String? = null,
    val done: Boolean = false,
    val malformed: Boolean = false,
    val serverError: Boolean = false,
    val nonStreamingResponse: Boolean = false,
    val usage: Usage? = null
)

/**
 * Incremental decoder for OpenAI-compatible SSE responses.
 *
 * It also accepts a complete non-streaming chat-completion object when a
 * compatible server ignores `stream=true`.
 */
internal class ChatStreamDecoder {
    private val pendingDataLines = mutableListOf<String>()
    private val fallbackBody = StringBuilder()
    private var pendingEventName: String? = null
    private var firstLine = true
    private var sawSseField = false

    fun acceptLine(rawLine: String): List<ChatStreamEvent> {
        val line = if (firstLine) {
            firstLine = false
            rawLine.removePrefix("\uFEFF")
        } else {
            rawLine
        }

        if (line.isBlank()) {
            return dispatchPendingData()
        }

        val normalized = line.trimStart()
        if (normalized.startsWith(":")) {
            sawSseField = true
            return emptyList()
        }

        val separator = normalized.indexOf(':')
        val field = (if (separator >= 0) normalized.substring(0, separator) else normalized).trim()
        val rawValue = if (separator >= 0) normalized.substring(separator + 1) else ""
        val value = rawValue.removePrefix(" ")

        return when (field) {
            "data" -> {
                sawSseField = true
                val completed = if (
                    pendingDataLines.isNotEmpty() &&
                    isCompletePayload(pendingDataLines.joinToString("\n"))
                ) {
                    dispatchPendingData()
                } else {
                    emptyList()
                }
                pendingDataLines += value
                completed
            }

            "event" -> {
                sawSseField = true
                pendingEventName = value.trim()
                emptyList()
            }

            "id", "retry" -> {
                sawSseField = true
                emptyList()
            }

            else -> {
                if (!sawSseField) appendFallbackLine(line)
                emptyList()
            }
        }
    }

    fun finish(): List<ChatStreamEvent> {
        val pending = dispatchPendingData()
        if (pending.isNotEmpty() || sawSseField) return pending

        val payload = fallbackBody.toString().trim()
        if (payload.isBlank()) return emptyList()
        return decodePayload(
            payload = payload,
            eventName = null,
            nonStreamingResponse = true
        )
    }

    private fun appendFallbackLine(line: String) {
        val addedLength = line.length + 1
        ApiResponsePolicy.nextTotal(fallbackBody.length, addedLength)
        fallbackBody.append(line).append('\n')
    }

    private fun dispatchPendingData(): List<ChatStreamEvent> {
        if (pendingDataLines.isEmpty()) {
            pendingEventName = null
            return emptyList()
        }
        val payload = pendingDataLines.joinToString("\n")
        val eventName = pendingEventName
        pendingDataLines.clear()
        pendingEventName = null
        return decodePayload(payload, eventName, nonStreamingResponse = false)
    }

    private fun decodePayload(
        payload: String,
        eventName: String?,
        nonStreamingResponse: Boolean
    ): List<ChatStreamEvent> {
        val trimmed = payload.trim()
        if (trimmed.isBlank() || trimmed.equals("ping", ignoreCase = true)) return emptyList()
        if (
            trimmed == "[DONE]" ||
            trimmed.equals("[KEEPALIVE]", ignoreCase = true)
        ) {
            return if (trimmed == "[DONE]") {
                listOf(ChatStreamEvent(done = true))
            } else {
                emptyList()
            }
        }

        val root = try {
            JsonParser.parseString(trimmed)
        } catch (_: Exception) {
            return listOf(ChatStreamEvent(malformed = true))
        }

        if (eventName.equals("error", ignoreCase = true) || root.hasServerError()) {
            return listOf(ChatStreamEvent(serverError = true))
        }
        if (root.isJsonPrimitive && root.asJsonPrimitive.isString) {
            return listOf(
                ChatStreamEvent(
                    content = root.asString,
                    nonStreamingResponse = nonStreamingResponse
                )
            )
        }
        if (!root.isJsonObject) return emptyList()

        val rootObj = root.asJsonObject
        val usage = rootObj.objectOrNull("usage")?.toUsage()
        val choicesNode = rootObj.get("choices")
        if (choicesNode == null) {
            return if (usage != null) {
                listOf(ChatStreamEvent(usage = usage, nonStreamingResponse = nonStreamingResponse))
            } else {
                emptyList()
            }
        }
        if (!choicesNode.isJsonArray) {
            return listOf(ChatStreamEvent(malformed = true))
        }
        val choices = choicesNode.asJsonArray
        if (choices.isEmpty) {
            return if (usage != null) {
                listOf(ChatStreamEvent(usage = usage, nonStreamingResponse = nonStreamingResponse))
            } else {
                emptyList()
            }
        }
        val firstChoice = choices.first()
        if (!firstChoice.isJsonObject) {
            return listOf(ChatStreamEvent(malformed = true))
        }
        val choice = firstChoice.asJsonObject
        val finishReason = choice.stringOrNull("finish_reason")
        val contentNode = choice.objectOrNull("delta")?.get("content")
            ?: choice.objectOrNull("message")?.get("content")
        val content = contentNode.extractText().takeIf { it.isNotEmpty() }

        return if (content != null || finishReason != null || usage != null) {
            listOf(
                ChatStreamEvent(
                    content = content,
                    finishReason = finishReason,
                    nonStreamingResponse = nonStreamingResponse,
                    usage = usage
                )
            )
        } else {
            emptyList()
        }
    }

    private fun isCompletePayload(payload: String): Boolean {
        val trimmed = payload.trim()
        if (
            trimmed == "[DONE]" ||
            trimmed.equals("[KEEPALIVE]", ignoreCase = true) ||
            trimmed.equals("ping", ignoreCase = true)
        ) {
            return true
        }
        return runCatching { JsonParser.parseString(trimmed) }.isSuccess
    }
}

internal object ChatStreamCompletionPolicy {
    fun requireComplete(
        receivedContent: Boolean,
        sawDone: Boolean,
        finishReason: String?,
        malformed: Boolean,
        serverError: Boolean,
        nonStreamingResponse: Boolean,
        jsonMode: Boolean,
        throwOnLength: Boolean = true
    ) {
        if (throwOnLength && finishReason == "length") {
            val detail = if (jsonMode) "，JSON 可能不完整" else ""
            throw CompletionLengthException("AI 输出达到长度上限$detail")
        }
        if (serverError) {
            throw IOException("AI 流式响应返回错误，请检查模型服务日志")
        }
        if (malformed) {
            throw IOException("AI 流式响应包含损坏分块，已拒绝保存不完整内容")
        }
        if (!receivedContent) {
            throw IOException("AI 未返回可用正文，请重试")
        }
        if (finishReason == "content_filter" || finishReason == "error") {
            throw IOException("AI 流式输出未正常完成：$finishReason")
        }
        if (!sawDone && finishReason.isNullOrBlank() && !nonStreamingResponse) {
            throw IOException("AI 流式连接在完成标记前中断，已拒绝保存不完整内容")
        }
    }
}

private fun JsonElement.hasServerError(): Boolean {
    return isJsonObject && asJsonObject.has("error")
}

private fun JsonObject.objectOrNull(name: String): JsonObject? {
    val value = get(name) ?: return null
    return value.takeIf { it.isJsonObject }?.asJsonObject
}

private fun JsonObject.stringOrNull(name: String): String? {
    val value = get(name) ?: return null
    return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
}

private fun JsonObject.intOrNull(name: String): Int? {
    val value = get(name) ?: return null
    return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asInt
}

private fun JsonObject.toUsage(): Usage {
    val details = objectOrNull("prompt_tokens_details")?.let { detailObj ->
        PromptTokensDetails(cachedTokens = detailObj.intOrNull("cached_tokens"))
    }
    return Usage(
        promptTokens = intOrNull("prompt_tokens") ?: 0,
        completionTokens = intOrNull("completion_tokens") ?: 0,
        totalTokens = intOrNull("total_tokens") ?: 0,
        promptCacheHitTokens = intOrNull("prompt_cache_hit_tokens"),
        promptTokensDetails = details
    )
}

private fun JsonElement?.extractText(): String {
    if (this == null || isJsonNull) return ""
    if (isJsonPrimitive) {
        return if (asJsonPrimitive.isString) asString else ""
    }
    if (isJsonArray) {
        return asJsonArray.joinToString(separator = "") { it.extractText() }
    }
    if (!isJsonObject) return ""

    val preferred = listOf("text", "content", "value", "output_text")
        .firstNotNullOfOrNull { key ->
            asJsonObject.get(key)?.extractText()?.takeIf { it.isNotEmpty() }
        }
    return preferred.orEmpty()
}
