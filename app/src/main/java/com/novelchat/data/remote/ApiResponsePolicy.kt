package com.novelchat.data.remote

import java.io.IOException

internal class ApiResponseTooLargeException(message: String) : IOException(message)

internal object ApiResponsePolicy {
    const val MAX_RESPONSE_CHARS = 512_000
    const val MAX_SSE_LINE_CHARS = 128_000

    fun nextTotal(currentChars: Int, incomingChars: Int): Int {
        require(currentChars >= 0 && incomingChars >= 0) { "响应长度不能为负数" }
        if (currentChars > MAX_RESPONSE_CHARS - incomingChars) {
            throw ApiResponseTooLargeException("AI 响应过大，已停止读取，请降低输出长度后重试")
        }
        return currentChars + incomingChars
    }

    fun requireCompleteLength(length: Int) {
        nextTotal(0, length)
    }
}
