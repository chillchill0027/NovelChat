package com.novelchat.ui.viewmodel

data class RetryPreparation(
    val messages: List<ChatMessageItem>,
    val action: String?
)

object ChatRetryPolicy {

    fun prepare(
        messages: List<ChatMessageItem>,
        lastFailedAction: String?
    ): RetryPreparation {
        val action = lastFailedAction?.trim()?.takeIf { it.isNotEmpty() }
            ?: messages.lastOrNull { it.role == "user" }?.content?.trim()?.takeIf { it.isNotEmpty() }

        val cleaned = messages
            .filterNot { it.role == "error" || (it.role == "assistant" && it.isStreaming) }
            .toMutableList()

        if (action != null) {
            val failedActionIndex = cleaned.indexOfLast {
                it.role == "user" && it.content.trim() == action
            }
            if (failedActionIndex >= 0) cleaned.removeAt(failedActionIndex)
        }

        return RetryPreparation(messages = cleaned, action = action)
    }
}
