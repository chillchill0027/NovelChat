package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChatStreamDecoderTest {
    @Test
    fun standardSse_decodesCommentsBomChunksAndDoneMarker() {
        val events = decode(
            "\uFEFF: connected",
            "",
            """data: {"choices":[{"delta":{"content":"林克"}}]}""",
            "",
            """data: {"choices":[{"delta":{"content":"走进咖啡店"},"finish_reason":"stop"}]}""",
            "",
            "data: [DONE]",
            ""
        )

        assertEquals("林克走进咖啡店", events.joinToString("") { it.content.orEmpty() })
        assertEquals("stop", events.last { it.finishReason != null }.finishReason)
        assertTrue(events.any { it.done })
        assertFalse(events.any { it.malformed })
    }

    @Test
    fun consecutiveDataLines_withoutBlankSeparatorsRemainStreamable() {
        val events = decode(
            """data: {"choices":[{"delta":{"content":"第一段"}}]}""",
            """data: {"choices":[{"delta":{"content":"第二段"},"finish_reason":"stop"}]}""",
            "data: [DONE]"
        )

        assertEquals("第一段第二段", events.joinToString("") { it.content.orEmpty() })
        assertTrue(events.any { it.done })
    }

    @Test
    fun multilineDataEvent_joinsPayloadAccordingToSseRules() {
        val events = decode(
            """event: message""",
            """data: {"choices":[{"delta":{"content":""",
            """data: "跨行内容"},"finish_reason":"stop"}]}""",
            ""
        )

        assertEquals("跨行内容", events.single().content)
        assertEquals("stop", events.single().finishReason)
    }

    @Test
    fun contentPartsAndEscapedText_areFlattenedWithoutLosingText() {
        val events = decode(
            """data: {"choices":[{"delta":{"content":[{"type":"text","text":"甲\n"},{"type":"text","text":{"value":"乙"}}]},"finish_reason":"stop"}]}""",
            "",
            "data: [DONE]",
            ""
        )

        assertEquals("甲\n乙", events.first().content)
    }

    @Test
    fun completeJsonBody_isAcceptedWhenServerIgnoresStreamingFlag() {
        val events = decode(
            """{"choices":[{"message":{"role":"assistant","content":"完整回退"},"finish_reason":"stop"}]}"""
        )

        assertEquals("完整回退", events.single().content)
        assertEquals("stop", events.single().finishReason)
        assertTrue(events.single().nonStreamingResponse)
    }

    @Test
    fun prettyPrintedJsonBody_isAcceptedAsNonStreamingFallback() {
        val events = decode(
            "{",
            """  "choices": [""",
            """    {"message":{"content":"分行回退"},"finish_reason":"stop"}""",
            "  ]",
            "}"
        )

        assertEquals("分行回退", events.single().content)
        assertTrue(events.single().nonStreamingResponse)
    }

    @Test
    fun keepaliveAndUsageOnlyChunks_doNotBecomeNarrativeText() {
        val events = decode(
            "data: ping",
            "",
            """data: {"choices":[],"usage":{"total_tokens":12}}""",
            "",
            """data: {"choices":[{"delta":{"content":"正文"},"finish_reason":"stop"}]}""",
            "",
            "data: [DONE]",
            ""
        )

        assertEquals("正文", events.joinToString("") { it.content.orEmpty() })
        assertFalse(events.any { it.malformed })
    }

    @Test
    fun malformedAndServerErrorEvents_areReportedExplicitly() {
        val malformed = decode("data: {not-json}", "")
        val serverError = decode("""event: error""", """data: {"message":"failed"}""", "")

        assertTrue(malformed.single().malformed)
        assertTrue(serverError.single().serverError)
    }

    @Test
    fun incompatibleChoicesShapes_areReportedAsMalformedWithoutThrowing() {
        val objectChoices = decode("""data: {"choices":{"delta":{"content":"错误"}}}""", "")
        val primitiveChoice = decode("""data: {"choices":["wrong"]}""", "")

        assertTrue(objectChoices.single().malformed)
        assertTrue(primitiveChoice.single().malformed)
    }

    @Test
    fun contentAndFinishReasonMayArriveInSeparateChunks() {
        val events = decode(
            """data: {"choices":[{"delta":{"content":"正文"}}]}""",
            "",
            """data: {"choices":[{"delta":{},"finish_reason":"stop"}]}""",
            ""
        )

        assertEquals("正文", events.joinToString("") { it.content.orEmpty() })
        assertEquals("stop", events.last().finishReason)
    }

    @Test
    fun eofWithPartialPayload_isMarkedMalformed() {
        val events = decode(
            """data: {"choices":[{"delta":{"content":"未完成"}"""
        )

        assertTrue(events.single().malformed)
    }

    @Test
    fun completionPolicy_rejectsDisconnectBeforeCompletionMarker() {
        val error = assertThrows(IOException::class.java) {
            ChatStreamCompletionPolicy.requireComplete(
                receivedContent = true,
                sawDone = false,
                finishReason = null,
                malformed = false,
                serverError = false,
                nonStreamingResponse = false,
                jsonMode = false
            )
        }

        assertTrue(error.message.orEmpty().contains("中断"))
    }

    @Test
    fun completionPolicy_acceptsDoneFinishReasonAndNonStreamingFallbacks() {
        listOf(
            Triple(true, null, false),
            Triple(false, "stop", false),
            Triple(false, null, true)
        ).forEach { (sawDone, finishReason, fallback) ->
            ChatStreamCompletionPolicy.requireComplete(
                receivedContent = true,
                sawDone = sawDone,
                finishReason = finishReason,
                malformed = false,
                serverError = false,
                nonStreamingResponse = fallback,
                jsonMode = false
            )
        }
    }

    @Test
    fun completionPolicy_preservesLengthAndCorruptionFailures() {
        assertThrows(CompletionLengthException::class.java) {
            ChatStreamCompletionPolicy.requireComplete(
                receivedContent = true,
                sawDone = true,
                finishReason = "length",
                malformed = false,
                serverError = false,
                nonStreamingResponse = false,
                jsonMode = true
            )
        }
        assertThrows(IOException::class.java) {
            ChatStreamCompletionPolicy.requireComplete(
                receivedContent = true,
                sawDone = true,
                finishReason = "stop",
                malformed = true,
                serverError = false,
                nonStreamingResponse = false,
                jsonMode = false
            )
        }
        assertThrows(IOException::class.java) {
            ChatStreamCompletionPolicy.requireComplete(
                receivedContent = false,
                sawDone = false,
                finishReason = null,
                malformed = false,
                serverError = true,
                nonStreamingResponse = false,
                jsonMode = false
            )
        }
    }

    @Test
    fun decoder_extractsUsageFromEmptyChoicesChunkAndAllowsV2LengthCompletion() {
        val events = decode(
            """data: {"choices":[{"delta":{"content":"正文片段"}}]}""",
            "",
            """data: {"choices":[],"usage":{"prompt_tokens":120,"completion_tokens":45,"total_tokens":165,"prompt_cache_hit_tokens":96}}""",
            "",
            "data: [DONE]",
            ""
        )

        val usage = events.firstNotNullOfOrNull { it.usage }
        assertEquals(120, usage?.promptTokens)
        assertEquals(45, usage?.completionTokens)
        assertEquals(96, usage?.cachedTokens())

        ChatStreamCompletionPolicy.requireComplete(
            receivedContent = true,
            sawDone = true,
            finishReason = "length",
            malformed = false,
            serverError = false,
            nonStreamingResponse = false,
            jsonMode = false,
            throwOnLength = false
        )
    }

    private fun decode(vararg lines: String): List<ChatStreamEvent> {
        val decoder = ChatStreamDecoder()
        val events = mutableListOf<ChatStreamEvent>()
        lines.forEach { events += decoder.acceptLine(it) }
        events += decoder.finish()
        return events
    }
}
