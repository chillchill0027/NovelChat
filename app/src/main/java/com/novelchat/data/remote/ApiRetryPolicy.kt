package com.novelchat.data.remote

internal object ApiRetryPolicy {
    const val MAX_ATTEMPTS = 2

    fun isTransientStatus(code: Int): Boolean {
        return code == 408 || code == 429 || code in 500..504
    }

    fun delayMillis(retryAfterSeconds: String?, attempt: Int): Long {
        val serverDelay = retryAfterSeconds
            ?.trim()
            ?.toLongOrNull()
            ?.times(1_000L)
        return serverDelay
            ?.coerceIn(500L, 10_000L)
            ?: (750L * (attempt.coerceAtLeast(0) + 1)).coerceAtMost(3_000L)
    }
}
