package com.novelchat.domain.v2.narrator

import com.novelchat.data.remote.AiCallStage
import com.novelchat.data.remote.llm.LlmClient
import com.novelchat.data.remote.llm.LlmPriority
import com.novelchat.data.remote.llm.LlmRequest
import com.novelchat.data.remote.llm.LlmStreamEvent
import com.novelchat.data.remote.llm.ResponseMode
import com.novelchat.data.remote.model.Usage
import com.novelchat.domain.v2.context.PromptContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

class NarratorAgent(
    private val llmClient: LlmClient
) {
    data class StreamResult(
        val rawText: String,
        val finishReason: String?,
        val usage: Usage?
    )

    fun stream(
        promptContext: PromptContext,
        baseTemperature: Float,
        isLocalEndpoint: Boolean,
        retryForRepetition: Boolean = false,
        extraTrailingHint: String? = null
    ): Flow<LlmStreamEvent> {
        val messages = if (extraTrailingHint.isNullOrBlank()) {
            promptContext.toMessages()
        } else {
            val base = promptContext.toMessages().toMutableList()
            val last = base.removeAt(base.lastIndex)
            base.add(last.copy(content = "${last.content}\n\n$extraTrailingHint"))
            base
        }

        val effectiveTemp = when {
            isLocalEndpoint && retryForRepetition -> 0.8f
            isLocalEndpoint -> 0.7f
            retryForRepetition -> (baseTemperature + 0.1f).coerceAtMost(1.1f)
            else -> baseTemperature
        }
        val presencePenalty = when {
            isLocalEndpoint -> null
            retryForRepetition -> 0.6f
            else -> 0.3f
        }
        val frequencyPenalty = if (isLocalEndpoint) null else 0.2f

        val request = LlmRequest(
            stage = AiCallStage.NARRATIVE,
            messages = messages,
            temperature = effectiveTemp,
            maxTokens = promptContext.lengthHint.maxTokens,
            responseMode = ResponseMode.TEXT,
            presencePenalty = presencePenalty,
            frequencyPenalty = frequencyPenalty,
            priority = LlmPriority.FOREGROUND
        )
        return llmClient.stream(request)
    }

    suspend fun collectStream(
        flow: Flow<LlmStreamEvent>,
        onDelta: suspend (String) -> Unit
    ): StreamResult {
        val sb = StringBuilder()
        var finishReason: String? = null
        var usage: Usage? = null
        flow.onEach { event ->
            when (event) {
                is LlmStreamEvent.Delta -> {
                    sb.append(event.text)
                    onDelta(event.text)
                }
                is LlmStreamEvent.Finished -> {
                    finishReason = event.finishReason
                    usage = event.usage
                }
            }
        }.collect { }
        return StreamResult(
            rawText = sb.toString(),
            finishReason = finishReason,
            usage = usage
        )
    }
}
