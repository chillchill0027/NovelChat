package com.novelchat.data.remote.llm

import com.novelchat.data.remote.AiCallStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlmMetricsTest {

    @Test
    fun ringBuffer_capsEntriesAndLogsSingleLinePerfFormat() {
        val logs = mutableListOf<String>()
        val metrics = LlmMetrics(maxEntries = 3, logSink = logs::add)

        repeat(5) { idx ->
            metrics.recordCall(
                LlmCallMetric(
                    timestampMs = idx.toLong(),
                    stage = AiCallStage.NARRATIVE,
                    model = "deepseek-v4-flash",
                    isLocal = false,
                    ttftMs = 400L + idx,
                    totalMs = 1_200L + idx,
                    promptTokens = 100,
                    completionTokens = 50,
                    cachedTokens = 80,
                    finishReason = "stop",
                    outcome = "ok",
                    attempt = 1
                )
            )
        }

        val recent = metrics.recentCalls()
        assertEquals(3, recent.size)
        assertEquals(2L, recent.first().timestampMs)
        assertEquals(4L, recent.last().timestampMs)
        assertTrue(logs.last().contains("cachedTokens=80"))

        metrics.recordRoundSummary(
            RoundPerfSummary(
                timestampMs = 100L,
                round = 4,
                blockingMs = 2_100L,
                calls = 2,
                syncMs = 950L
            )
        )
        assertEquals(1, metrics.recentRounds().size)
        assertTrue(logs.last().contains("round=4 blockingMs=2100"))
    }
}
