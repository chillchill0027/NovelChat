package com.novelchat.data.remote.llm

import com.novelchat.data.remote.AiService
import com.novelchat.data.remote.ApiEndpointPolicy
import com.novelchat.data.remote.model.ChatMessage
import com.novelchat.data.remote.model.JsonSchemaFormat
import com.novelchat.data.remote.model.ResponseFormat
import com.novelchat.data.remote.model.Usage
import com.novelchat.data.storage.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class OpenAiCompatibleLlmClient(
    private val aiService: AiService,
    private val scheduler: LlmScheduler,
    private val metrics: LlmMetrics
) : LlmClient {

    override fun stream(request: LlmRequest): Flow<LlmStreamEvent> = flow {
        val connection = aiService.resolveConnection(request.stage)
        val baseUrl = connection.baseUrl.trim()
        val endpointKey = ApiEndpointPolicy.normalizeBaseUrl(baseUrl)
        val isLocal = ApiEndpointPolicy.isLocalEndpoint(baseUrl)
        val model = connection.model.trim().ifBlank { SettingsRepository.DEFAULT_MODEL }
        val responseFormat = resolveResponseFormat(request, isLocal)
        val totalTimeoutMs = request.totalTimeoutMs
            ?: if (isLocal) DEFAULT_LOCAL_TOTAL_TIMEOUT_MS else DEFAULT_REMOTE_TOTAL_TIMEOUT_MS
        val idleTimeoutMs = request.idleTimeoutMs
            ?: if (isLocal) DEFAULT_LOCAL_IDLE_TIMEOUT_MS else DEFAULT_REMOTE_IDLE_TIMEOUT_MS

        scheduler.run(endpointKey = endpointKey, isLocal = isLocal, priority = request.priority) {
            val startMs = System.currentTimeMillis()
            var ttftMs: Long? = null
            var finishReason: String? = null
            var usage: Usage? = null
            var attempts = 1
            var outcome = "ok"
            try {
                aiService.chatStreamV2(
                    connection = connection,
                    stage = request.stage,
                    messages = request.messages.map { ChatMessage(role = it.role, content = it.content) },
                    temperature = request.temperature,
                    maxTokens = request.maxTokens,
                    responseFormat = responseFormat,
                    presencePenalty = request.presencePenalty,
                    frequencyPenalty = request.frequencyPenalty,
                    totalTimeoutMs = totalTimeoutMs,
                    idleTimeoutMs = idleTimeoutMs,
                    attemptSink = { attempts = it }
                ).collect { event ->
                    when (event) {
                        is LlmStreamEvent.Delta -> {
                            if (ttftMs == null) ttftMs = System.currentTimeMillis() - startMs
                        }
                        is LlmStreamEvent.Finished -> {
                            finishReason = event.finishReason
                            usage = event.usage
                        }
                    }
                    emit(event)
                }
                if (attempts > 1) outcome = "retry"
            } catch (e: CancellationException) {
                outcome = "cancelled"
                throw e
            } catch (e: Exception) {
                outcome = "error"
                throw e
            } finally {
                metrics.recordCall(
                    LlmCallMetric(
                        timestampMs = System.currentTimeMillis(),
                        stage = request.stage,
                        model = model,
                        isLocal = isLocal,
                        ttftMs = ttftMs,
                        totalMs = System.currentTimeMillis() - startMs,
                        promptTokens = usage?.promptTokens ?: 0,
                        completionTokens = usage?.completionTokens ?: 0,
                        cachedTokens = usage?.cachedTokens() ?: 0,
                        finishReason = finishReason,
                        outcome = outcome,
                        attempt = attempts
                    )
                )
            }
        }
    }

    override suspend fun complete(request: LlmRequest): LlmCompletion {
        val connection = aiService.resolveConnection(request.stage)
        val baseUrl = connection.baseUrl.trim()
        val endpointKey = ApiEndpointPolicy.normalizeBaseUrl(baseUrl)
        val isLocal = ApiEndpointPolicy.isLocalEndpoint(baseUrl)
        val model = connection.model.trim().ifBlank { SettingsRepository.DEFAULT_MODEL }
        val responseFormat = resolveResponseFormat(request, isLocal)
        val totalTimeoutMs = request.totalTimeoutMs
            ?: if (isLocal) DEFAULT_LOCAL_TOTAL_TIMEOUT_MS else DEFAULT_REMOTE_TOTAL_TIMEOUT_MS

        return scheduler.run(endpointKey = endpointKey, isLocal = isLocal, priority = request.priority) {
            val startMs = System.currentTimeMillis()
            var attempts = 1
            var completion: LlmCompletion? = null
            var outcome = "ok"
            try {
                completion = aiService.chatCompleteV2(
                    connection = connection,
                    stage = request.stage,
                    messages = request.messages.map { ChatMessage(role = it.role, content = it.content) },
                    temperature = request.temperature,
                    maxTokens = request.maxTokens,
                    responseFormat = responseFormat,
                    presencePenalty = request.presencePenalty,
                    frequencyPenalty = request.frequencyPenalty,
                    totalTimeoutMs = totalTimeoutMs,
                    attemptSink = { attempts = it }
                )
                if (attempts > 1) outcome = "retry"
                completion
            } catch (e: CancellationException) {
                outcome = "cancelled"
                throw e
            } catch (e: Exception) {
                outcome = "error"
                throw e
            } finally {
                val usage = completion?.usage
                metrics.recordCall(
                    LlmCallMetric(
                        timestampMs = System.currentTimeMillis(),
                        stage = request.stage,
                        model = model,
                        isLocal = isLocal,
                        ttftMs = null,
                        totalMs = System.currentTimeMillis() - startMs,
                        promptTokens = usage?.promptTokens ?: 0,
                        completionTokens = usage?.completionTokens ?: 0,
                        cachedTokens = usage?.cachedTokens() ?: 0,
                        finishReason = completion?.finishReason,
                        outcome = outcome,
                        attempt = attempts
                    )
                )
            }
        }
    }

    private fun resolveResponseFormat(request: LlmRequest, isLocal: Boolean): ResponseFormat? {
        return when (request.responseMode) {
            ResponseMode.TEXT -> null
            ResponseMode.JSON_OBJECT -> ResponseFormat(type = "json_object")
            ResponseMode.JSON_SCHEMA -> if (isLocal && request.jsonSchema != null) {
                ResponseFormat(
                    type = "json_schema",
                    jsonSchema = JsonSchemaFormat(
                        name = "state_delta",
                        strict = false,
                        schema = request.jsonSchema
                    )
                )
            } else {
                ResponseFormat(type = "json_object")
            }
        }
    }

    companion object {
        const val DEFAULT_REMOTE_TOTAL_TIMEOUT_MS = 90_000L
        const val DEFAULT_REMOTE_IDLE_TIMEOUT_MS = 45_000L
        const val DEFAULT_LOCAL_TOTAL_TIMEOUT_MS = 300_000L
        const val DEFAULT_LOCAL_IDLE_TIMEOUT_MS = 90_000L
    }
}
