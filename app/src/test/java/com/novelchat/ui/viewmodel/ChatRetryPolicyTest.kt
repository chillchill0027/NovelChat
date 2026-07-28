package com.novelchat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatRetryPolicyTest {

    @Test
    fun prepare_removesFailedActionErrorAndStreamingPreview() {
        val messages = listOf(
            ChatMessageItem(1, "user", "上一轮行动"),
            ChatMessageItem(1, "assistant", "上一轮正文"),
            ChatMessageItem(2, "user", "去咖啡厅"),
            ChatMessageItem(2, "assistant", "半截内容", isStreaming = true),
            ChatMessageItem(2, "error", "网络失败")
        )

        val result = ChatRetryPolicy.prepare(messages, "去咖啡厅")

        assertEquals("去咖啡厅", result.action)
        assertEquals(2, result.messages.size)
        assertFalse(result.messages.any { it.role == "error" || it.isStreaming })
    }

    @Test
    fun prepare_fallsBackToLatestUserAction() {
        val result = ChatRetryPolicy.prepare(
            messages = listOf(ChatMessageItem(3, "user", "回家休息")),
            lastFailedAction = null
        )

        assertEquals("回家休息", result.action)
        assertEquals(emptyList<ChatMessageItem>(), result.messages)
    }
}
