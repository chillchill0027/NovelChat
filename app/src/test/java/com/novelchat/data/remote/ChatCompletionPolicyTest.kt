package com.novelchat.data.remote

import com.novelchat.data.remote.model.ChatMessage
import com.novelchat.data.remote.model.ChatResponse
import com.novelchat.data.remote.model.Choice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ChatCompletionPolicyTest {

    @Test
    fun requireContent_returnsShortConnectionProbeResponse() {
        val response = ChatResponse(
            choices = listOf(Choice(message = ChatMessage(role = "assistant", content = "OK")))
        )

        assertEquals("OK", ChatCompletionPolicy.requireContent(response, jsonMode = false))
    }

    @Test
    fun requireContent_usesNonJsonLengthMessageForConnectionProbe() {
        val response = ChatResponse(choices = listOf(Choice(finishReason = "length")))

        val error = assertThrows(CompletionLengthException::class.java) {
            ChatCompletionPolicy.requireContent(response, jsonMode = false)
        }

        assertEquals("AI 输出达到长度上限", error.message)
    }

    @Test
    fun requireContent_keepsJsonWarningForStructuredCalls() {
        val response = ChatResponse(choices = listOf(Choice(finishReason = "length")))

        val error = assertThrows(CompletionLengthException::class.java) {
            ChatCompletionPolicy.requireContent(response, jsonMode = true)
        }

        assertEquals("AI 输出达到长度上限，JSON 可能不完整", error.message)
    }
}
