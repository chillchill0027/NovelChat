package com.novelchat.data.remote

import java.io.BufferedReader

/**
 * Reads SSE lines with a fixed upper bound while treating CRLF as one
 * delimiter. BufferedReader.readLine() has the right delimiter semantics but
 * cannot reject an oversized line before allocating the whole value.
 */
internal class BoundedSseLineReader(
    private val reader: BufferedReader,
    private val maxLineChars: Int = ApiResponsePolicy.MAX_SSE_LINE_CHARS
) {
    fun readLine(): String? {
        val result = StringBuilder()
        var receivedAny = false

        while (true) {
            val value = reader.read()
            if (value < 0) return result.takeIf { receivedAny }?.toString()
            receivedAny = true

            when (value.toChar()) {
                '\n' -> return result.toString()
                '\r' -> {
                    consumeOptionalLineFeed()
                    return result.toString()
                }
                else -> {
                    if (result.length >= maxLineChars) {
                        throw ApiResponseTooLargeException(
                            "AI 流式分块过大，已停止读取，请重试"
                        )
                    }
                    result.append(value.toChar())
                }
            }
        }
    }

    private fun consumeOptionalLineFeed() {
        reader.mark(1)
        val next = reader.read()
        if (next >= 0 && next.toChar() != '\n') {
            reader.reset()
        }
    }
}
