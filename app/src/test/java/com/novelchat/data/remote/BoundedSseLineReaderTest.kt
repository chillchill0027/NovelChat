package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.StringReader

class BoundedSseLineReaderTest {
    @Test
    fun crlf_isConsumedAsOneDelimiterAndPreservesErrorEvent() {
        val source = """
            event: error
            data: {"message":"failed"}

        """.trimIndent().replace("\n", "\r\n")
        val lineReader = BoundedSseLineReader(BufferedReader(StringReader(source)))
        val decoder = ChatStreamDecoder()
        val events = mutableListOf<ChatStreamEvent>()

        while (true) {
            val line = lineReader.readLine() ?: break
            events += decoder.acceptLine(line)
        }
        events += decoder.finish()

        assertEquals(1, events.size)
        assertTrue(events.single().serverError)
    }

    @Test
    fun fragmentedUtf8Bytes_andEscapedJsonRemainLossless() {
        val narrative = "林克说：“兑奖\\路径确认。”\n顾宁点头🙂"
        val payload = """
            data: {"choices":[{"delta":{"content":"林克说：\u201c兑奖\\路径确认。\u201d\n顾宁点头\ud83d\ude42"},"finish_reason":"stop"}]}

            data: [DONE]

        """.trimIndent().replace("\n", "\r\n").toByteArray(Charsets.UTF_8)
        val stream = OneByteAtATimeInputStream(payload)
        val lineReader = BoundedSseLineReader(
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        )
        val decoder = ChatStreamDecoder()
        val events = mutableListOf<ChatStreamEvent>()

        while (true) {
            val line = lineReader.readLine() ?: break
            events += decoder.acceptLine(line)
        }
        events += decoder.finish()

        assertEquals(narrative, events.joinToString("") { it.content.orEmpty() })
        assertEquals("stop", events.first { it.finishReason != null }.finishReason)
        assertTrue(events.any { it.done })
    }

    @Test
    fun supportsLfCrAndFinalUnterminatedLineWithoutInventingLines() {
        val reader = BoundedSseLineReader(
            BufferedReader(StringReader("one\ntwo\rthree"))
        )

        assertEquals("one", reader.readLine())
        assertEquals("two", reader.readLine())
        assertEquals("three", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun rejectsLineBeforeItCanGrowPastConfiguredLimit() {
        val reader = BoundedSseLineReader(
            reader = BufferedReader(StringReader("12345")),
            maxLineChars = 4
        )

        assertThrows(ApiResponseTooLargeException::class.java) {
            reader.readLine()
        }
    }

    private class OneByteAtATimeInputStream(
        bytes: ByteArray
    ) : InputStream() {
        private val delegate = ByteArrayInputStream(bytes)

        override fun read(): Int = delegate.read()

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            return delegate.read(buffer, offset, minOf(length, 1))
        }
    }
}
