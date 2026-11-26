package com.novelchat.eval

import com.google.gson.annotations.SerializedName

data class EvalScenario(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("story_source") val storySource: String = "default_modern",
    @SerializedName("inputs") val inputs: List<String> = emptyList(),
    @SerializedName("checkpoints") val checkpoints: List<EvalCheckpoint> = emptyList()
)

data class EvalCheckpoint(
    @SerializedName("after_round") val afterRound: Int = 0,
    @SerializedName("expected_location_keywords") val expectedLocationKeywords: List<String> = emptyList(),
    @SerializedName("expected_present_any") val expectedPresentAny: List<String> = emptyList(),
    @SerializedName("expected_inventory_contains") val expectedInventoryContains: List<String> = emptyList(),
    @SerializedName("expected_inventory_excludes") val expectedInventoryExcludes: List<String> = emptyList()
)

data class EvalRoundMetrics(
    val turn: Int,
    val input: String,
    val ttftMs: Long?,
    val narrativeCompleteMs: Long?,
    val blockingTotalMs: Long,
    val narrativeChars: Int,
    val syncSucceeded: Boolean,
    val blockedByPendingSync: Boolean,
    val error: String?,
    val warning: String?,
    val locationAfter: String,
    val presentAfter: List<String>
)

data class EvalCheckpointResult(
    val afterRound: Int,
    val passed: Boolean,
    val failures: List<String>
)

data class EvalScenarioReport(
    val scenarioId: String,
    val title: String,
    val engineMode: String,
    val model: String,
    val roundsAttempted: Int,
    val roundsSucceeded: Int,
    val syncSuccessCount: Int,
    val pendingSyncBlockedCount: Int,
    val ttftP50Ms: Long?,
    val blockingP50Ms: Long?,
    val checkpointPassCount: Int,
    val checkpointTotalCount: Int,
    val checkpointResults: List<EvalCheckpointResult>,
    val rounds: List<EvalRoundMetrics>
)

data class EvalSummaryReport(
    val generatedAt: String,
    val engineMode: String,
    val model: String,
    val baseUrl: String,
    val scenarios: List<EvalScenarioReport>
)
