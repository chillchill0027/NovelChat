package com.novelchat.domain.engine

import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.StoryState

internal object StateSyncPolicy {

    const val PENDING_FROM_ROUND_KEY = "state_sync_pending_from_round"
    private const val MAX_RECOVERY_ROUNDS = 6

    fun pendingFromRound(state: StoryState): Int? {
        return state.globalFlags[PENDING_FROM_ROUND_KEY]
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    fun markPending(state: StoryState, round: Int): StoryState {
        if (round <= 0) return state
        val pendingFrom = minOf(pendingFromRound(state) ?: round, round)
        return state.copy(
            globalFlags = state.globalFlags + (PENDING_FROM_ROUND_KEY to pendingFrom.toString())
        )
    }

    fun clearPending(state: StoryState): StoryState {
        if (PENDING_FROM_ROUND_KEY !in state.globalFlags) return state
        return state.copy(globalFlags = state.globalFlags - PENDING_FROM_ROUND_KEY)
    }

    fun reconcilePersistedNarratives(
        state: StoryState,
        narratives: List<NarrativeEntry>
    ): StoryState {
        val latestNarrativeRound = narratives.maxOfOrNull { it.turn } ?: return state
        if (latestNarrativeRound <= state.currentRound) return state
        val firstUnsyncedRound = narratives
            .asSequence()
            .map { it.turn }
            .filter { it > state.currentRound }
            .minOrNull()
            ?: latestNarrativeRound
        return markPending(
            state = state.copy(currentRound = latestNarrativeRound),
            round = firstUnsyncedRound
        )
    }

    fun recoveryNarratives(
        state: StoryState,
        recentNarratives: List<NarrativeEntry>
    ): List<NarrativeEntry> {
        val pendingFrom = pendingFromRound(state) ?: return emptyList()
        return recentNarratives
            .filter { it.turn >= pendingFrom }
            .takeLast(MAX_RECOVERY_ROUNDS)
    }
}
