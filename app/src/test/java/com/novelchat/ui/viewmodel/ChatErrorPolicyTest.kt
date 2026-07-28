package com.novelchat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class ChatErrorPolicyTest {

    @Test
    fun userVisibleMessage_keepsFailureActionable() {
        val message = ChatErrorPolicy.userVisibleMessage("Socket closed")

        assertTrue(message.contains("生成失败"))
        assertTrue(message.contains("Socket closed"))
        assertTrue(message.contains("检查模型服务后重试"))
    }

    @Test
    fun userVisibleMessage_handlesBlankDetail() {
        val message = ChatErrorPolicy.userVisibleMessage("   ")

        assertTrue(message.contains("未知错误"))
    }

    @Test
    fun userVisibleMessage_redactsCredentialsAndMarkup() {
        val fakeKey = "sk-" + "1234567890abcdef"
        val fakeBearer = "abcdefghijklmnop"

        val message = ChatErrorPolicy.userVisibleMessage(
            "<b>Denied</b> $fakeKey Bearer $fakeBearer"
        )

        assertTrue(message.contains("Denied"))
        assertTrue(message.contains("***"))
        assertFalse(message.contains(fakeKey))
        assertFalse(message.contains(fakeBearer))
        assertFalse(message.contains("<b>"))

        val warning = ChatErrorPolicy.userVisibleWarning(
            "<p>同步失败</p> Bearer $fakeBearer" + "x".repeat(500)
        )
        assertTrue(warning.startsWith("同步失败 Bearer ***"))
        assertTrue(warning.length <= UserVisibleTextPolicy.MAX_ERROR_DETAIL_LENGTH)
        assertFalse(warning.contains(fakeBearer))
    }

    @Test
    fun userVisibleMessage_limitsProviderDetail() {
        val message = ChatErrorPolicy.userVisibleMessage("x".repeat(2_000))

        assertTrue(message.length < 400)
    }

    @Test
    fun failure_sanitizesSharedViewModelErrors() {
        val token = "sk-" + "z".repeat(24)
        val message = UserVisibleTextPolicy.failure("故事库加载失败：", "<p>$token timeout</p>")

        assertEquals("故事库加载失败：*** timeout", message)
    }
}
