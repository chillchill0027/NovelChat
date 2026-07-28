package com.novelchat.data.remote

import com.google.gson.JsonObject
import com.google.gson.JsonParser

internal object ApiErrorPolicy {
    private const val MAX_VISIBLE_LENGTH = 240

    private val secretKeyPattern = Regex("(?i)\\bsk-[a-z0-9_-]{8,}\\b")
    private val bearerPattern = Regex("(?i)\\bbearer\\s+[a-z0-9._~+/-]{8,}=*")
    private val whitespacePattern = Regex("\\s+")

    fun message(statusCode: Int, rawBody: String?): String {
        val detail = extractStructuredMessage(rawBody)
            ?: rawBody
                ?.replace(Regex("<[^>]+>"), " ")
                ?.let(::sanitize)
                ?.takeIf { it.isNotBlank() }
            ?: defaultDetail(statusCode)
        return "API 请求失败（$statusCode）：${sanitize(detail).take(MAX_VISIBLE_LENGTH)}"
    }

    private fun extractStructuredMessage(rawBody: String?): String? {
        if (rawBody.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(rawBody).takeIf { it.isJsonObject }?.asJsonObject
                ?: return null
            stringValue(root, "message")
                ?: root.getAsJsonObjectOrNull("error")?.let { error ->
                    stringValue(error, "message") ?: stringValue(error, "detail")
                }
                ?: stringValue(root, "detail")
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitize(value: String): String {
        return value
            .replace(secretKeyPattern, "***")
            .replace(bearerPattern, "Bearer ***")
            .replace(whitespacePattern, " ")
            .trim()
    }

    private fun stringValue(json: JsonObject, key: String): String? {
        val value = json.get(key) ?: return null
        return value.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
            ?.asString
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.getAsJsonObjectOrNull(key: String): JsonObject? {
        return get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    }

    private fun defaultDetail(statusCode: Int): String = when (statusCode) {
        401 -> "认证失败，请检查 API Key"
        403 -> "当前凭据没有访问权限"
        404 -> "接口或模型不存在"
        408 -> "服务响应超时"
        429 -> "请求过于频繁，请稍后重试"
        in 500..599 -> "模型服务暂时不可用"
        else -> "服务未返回可读错误信息"
    }
}
