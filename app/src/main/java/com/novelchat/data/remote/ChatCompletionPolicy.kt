package com.novelchat.data.remote

import com.novelchat.data.remote.model.ChatResponse

internal class CompletionLengthException(message: String) : IllegalArgumentException(message)

internal object ChatCompletionPolicy {

    fun requireContent(response: ChatResponse, jsonMode: Boolean): String {
        val choice = response.choices.firstOrNull() ?: throw IllegalArgumentException("AI 响应为空")
        if (choice.finishReason == "length") {
            val detail = if (jsonMode) "，JSON 可能不完整" else ""
            throw CompletionLengthException("AI 输出达到长度上限$detail")
        }
        return choice.message?.content?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("AI 响应为空")
    }
}
