package com.novelchat.ui.viewmodel

import com.novelchat.data.model.PlayerState

internal object PlayerCardPolicy {

    fun currentGoal(player: PlayerState, pendingEvents: List<String>): String {
        if (player.storyProgress.currentGoal.isNotBlank()) return player.storyProgress.currentGoal
        val activeQuest = player.questLog.firstOrNull {
            it.status !in setOf("完成", "已完成", "failed", "失败", "cancelled", "已取消")
        }
        if (activeQuest != null) {
            return activeQuest.currentStep.ifBlank { activeQuest.name }
        }
        if (player.mentalState.focus.isNotBlank()) return player.mentalState.focus
        return pendingEvents.firstOrNull { it.isNotBlank() }.orEmpty()
    }
}
