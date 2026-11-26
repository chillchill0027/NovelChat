package com.novelchat.data.remote.llm

import kotlinx.coroutines.flow.Flow

interface LlmClient {
    fun stream(request: LlmRequest): Flow<LlmStreamEvent>
    suspend fun complete(request: LlmRequest): LlmCompletion
}
