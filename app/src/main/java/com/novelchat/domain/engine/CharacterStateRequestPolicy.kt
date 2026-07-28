package com.novelchat.domain.engine

import com.novelchat.data.remote.CompletionLengthException

internal object CharacterStateRequestPolicy {
    const val INITIAL_MAX_TOKENS = 1_800
    const val LENGTH_RETRY_MAX_TOKENS = 2_600

    suspend fun <T> execute(
        onLengthRetry: (Int) -> Unit = {},
        request: suspend (maxTokens: Int) -> T
    ): T {
        return try {
            request(INITIAL_MAX_TOKENS)
        } catch (_: CompletionLengthException) {
            onLengthRetry(LENGTH_RETRY_MAX_TOKENS)
            request(LENGTH_RETRY_MAX_TOKENS)
        }
    }
}
