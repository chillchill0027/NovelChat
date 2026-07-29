package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldbookEntry

data class ChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val playerState: PlayerState? = null,
    val storyState: StoryState? = null,
    val npcCards: List<CharacterCard> = emptyList(),
    val worldbookEntries: List<WorldbookEntry> = emptyList(),
    val isReady: Boolean = false,
    val isStreaming: Boolean = false,
    val canCancelGeneration: Boolean = false,
    val currentRound: Int = 0,
    val statusText: String = "",
    val errorMessage: String? = null,
    val lastFailedAction: String? = null,
    val regeneratableTurns: Set<Int> = emptySet()
)

data class ChatMessageItem(
    val turn: Int,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val canRegenerate: Boolean = false
)
