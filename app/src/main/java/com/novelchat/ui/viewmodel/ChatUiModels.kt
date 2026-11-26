package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CreativityLevel
import com.novelchat.data.model.LengthPreference
import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.remote.llm.LlmCallMetric
import com.novelchat.data.remote.llm.RoundPerfSummary
import com.novelchat.domain.v2.context.ContextDebugInfo

data class ChatUiState(
    val messages: List<ChatMessageItem> = emptyList(),
    val playerState: PlayerState? = null,
    val storyState: StoryState? = null,
    val worldSetting: WorldSetting? = null,
    val npcCards: List<CharacterCard> = emptyList(),
    val worldbookEntries: List<WorldbookEntry> = emptyList(),
    val isReady: Boolean = false,
    val isStreaming: Boolean = false,
    val canCancelGeneration: Boolean = false,
    val currentRound: Int = 0,
    val statusText: String = "",
    val errorMessage: String? = null,
    val lastFailedAction: String? = null,
    val regeneratableTurns: Set<Int> = emptySet(),
    val canSubmitInput: Boolean = true,
    val syncingRounds: Set<Int> = emptySet(),
    val failedSyncRounds: Set<Int> = emptySet(),
    val showSyncRetryBanner: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val lastSyncCompletedAtMs: Long = 0L,
    val narrationSettings: NarrationSettings = NarrationSettings(),
    val narrationCreativity: CreativityLevel = CreativityLevel.BALANCED,
    val defaultNarrationLength: LengthPreference = LengthPreference.STANDARD
)

data class ChatMessageItem(
    val turn: Int,
    val role: String,
    val content: String,
    val isStreaming: Boolean = false,
    val canRegenerate: Boolean = false
)

data class PerfDebugSnapshot(
    val recentCalls: List<LlmCallMetric> = emptyList(),
    val recentRounds: List<RoundPerfSummary> = emptyList(),
    val lastContextDebug: ContextDebugInfo? = null,
    val syncingRounds: Set<Int> = emptySet(),
    val failedRounds: Set<Int> = emptySet()
)
