package com.novelchat.ui.viewmodel

import com.novelchat.data.model.StoryState

internal object CustomOpeningPolicy {

    fun apply(state: StoryState, openingPrompt: String): StoryState {
        val normalizedPrompt = openingPrompt
            .trim()
            .replace(Regex("""\s+"""), " ")
            .take(MAX_PROMPT_LENGTH)
        if (normalizedPrompt.isBlank()) return state

        return state.copy(
            mood = "故事即将从玩家指定的情境展开",
            pendingEvents = listOf(normalizedPrompt)
        )
    }

    private const val MAX_PROMPT_LENGTH = 600
}
