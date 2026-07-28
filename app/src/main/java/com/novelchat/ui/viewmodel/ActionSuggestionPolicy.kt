package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState

/** Builds short, editable actions from the current scene without asking the model again. */
object ActionSuggestionPolicy {

    fun suggestions(
        player: PlayerState?,
        storyState: StoryState?,
        npcCards: List<CharacterCard>,
        limit: Int = 4
    ): List<String> {
        if (limit <= 0) return emptyList()

        val candidates = buildList {
            npcCards.firstOrNull()?.name?.takeIf { it.isNotBlank() }?.let { name ->
                add("和${name}聊聊")
            }

            val hasGoal = player?.let {
                PlayerCardPolicy.currentGoal(it, storyState?.pendingEvents.orEmpty()).isNotBlank()
            } == true
            if (hasGoal) add("继续推进当前目标")

            if (storyState?.pendingEvents.orEmpty().isNotEmpty()) {
                add("查看待办和时间安排")
            }

            add("观察一下周围")
            add("查看手机上的消息")
        }

        return candidates.distinct().take(limit)
    }
}
