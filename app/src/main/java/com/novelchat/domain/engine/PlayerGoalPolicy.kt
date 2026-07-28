package com.novelchat.domain.engine

import com.novelchat.data.model.PlayerState

internal object PlayerGoalPolicy {

    fun alignWithPendingEvents(player: PlayerState, pendingEvents: List<String>): PlayerState {
        val primaryGoal = pendingEvents.firstOrNull { it.isNotBlank() } ?: return player
        if (player.storyProgress.currentGoal == primaryGoal) return player
        return player.copy(
            storyProgress = player.storyProgress.copy(currentGoal = primaryGoal)
        )
    }
}
