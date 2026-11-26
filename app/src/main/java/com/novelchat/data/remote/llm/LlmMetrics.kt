package com.novelchat.data.remote.llm

import android.util.Log
import com.novelchat.data.remote.AiCallStage

data class LlmCallMetric(
    val timestampMs: Long,
    val stage: AiCallStage,
    val model: String,
    val isLocal: Boolean,
    val ttftMs: Long?,
    val totalMs: Long,
    val promptTokens: Int,
    val completionTokens: Int,
    val cachedTokens: Int,
    val finishReason: String?,
    val outcome: String,
    val attempt: Int
)

data class RoundPerfSummary(
    val timestampMs: Long,
    val round: Int,
    val blockingMs: Long,
    val calls: Int,
    val syncMs: Long?
)

class LlmMetrics(
    private val maxEntries: Int = DEFAULT_CAPACITY,
    private val logSink: (String) -> Unit = { line ->
        runCatching { Log.i(TAG, line) }
    }
) {
    private val lock = Any()
    private val calls = ArrayDeque<LlmCallMetric>(maxEntries)
    private val roundSummaries = ArrayDeque<RoundPerfSummary>(MAX_ROUNDS)

    fun recordCall(metric: LlmCallMetric) {
        synchronized(lock) {
            if (calls.size >= maxEntries) calls.removeFirst()
            calls.addLast(metric)
        }
        logSink(
            "stage=${metric.stage.storageKey} model=${metric.model} local=${metric.isLocal} " +
                "ttftMs=${metric.ttftMs ?: -1} totalMs=${metric.totalMs} " +
                "promptTokens=${metric.promptTokens} completionTokens=${metric.completionTokens} " +
                "cachedTokens=${metric.cachedTokens} finishReason=${metric.finishReason ?: "none"} " +
                "outcome=${metric.outcome} attempt=${metric.attempt}"
        )
    }

    fun recordRoundSummary(summary: RoundPerfSummary) {
        synchronized(lock) {
            if (roundSummaries.size >= MAX_ROUNDS) roundSummaries.removeFirst()
            roundSummaries.addLast(summary)
        }
        logSink(
            "round=${summary.round} blockingMs=${summary.blockingMs} " +
                "calls=${summary.calls} syncMs=${summary.syncMs ?: -1}"
        )
    }

    fun recentCalls(limit: Int = maxEntries): List<LlmCallMetric> = synchronized(lock) {
        calls.toList().takeLast(limit.coerceAtLeast(0))
    }

    fun recentRounds(limit: Int = MAX_ROUNDS): List<RoundPerfSummary> = synchronized(lock) {
        roundSummaries.toList().takeLast(limit.coerceAtLeast(0))
    }

    fun clear() {
        synchronized(lock) {
            calls.clear()
            roundSummaries.clear()
        }
    }

    companion object {
        const val TAG = "NovelChatPerf"
        const val DEFAULT_CAPACITY = 200
        private const val MAX_ROUNDS = 50
    }
}
