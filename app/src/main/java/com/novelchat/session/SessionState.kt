package com.novelchat.session

import com.novelchat.domain.v2.context.ContextDebugInfo
import com.novelchat.domain.v2.state.ApplyIssue

enum class RoundPhase {
    IDLE,
    PREPARING,
    NARRATING,
    GUARDING,
    COMMITTING,
    FAILED
}

data class SyncState(
    val syncingRounds: Set<Int> = emptySet(),
    val failedRounds: Set<Int> = emptySet(),
    val lastSyncedRound: Int = 0,
    val lastSyncCompletedAtMs: Long = 0L,
    val lastError: String? = null
)

data class SessionState(
    val storyId: Long,
    val phase: RoundPhase = RoundPhase.IDLE,
    val statusText: String = "",
    val canCancelGeneration: Boolean = false,
    val streamingText: String = "",
    val activeTurn: Int = 0,
    val activeUserInput: String = "",
    val errorMessage: String? = null,
    val lastFailedAction: String? = null,
    val sync: SyncState = SyncState(),
    val suggestions: List<String> = emptyList(),
    val lastDebugInfo: ContextDebugInfo? = null,
    val lastApplyIssues: List<ApplyIssue> = emptyList()
)
