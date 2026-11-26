package com.novelchat.data.repo

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.NarrationSettings
import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.PlayerState
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.SummaryEntry
import com.novelchat.data.model.WorldSetting
import com.novelchat.data.model.WorldbookEntry
import com.novelchat.domain.v2.memory.ChapterSummary

data class StorySnapshot(
    val storyId: Long,
    val world: WorldSetting,
    val story: StoryState,
    val player: PlayerState,
    val characters: List<CharacterCard>,
    val worldbook: List<WorldbookEntry>,
    val narratives: List<NarrativeEntry>,
    val summaries: List<SummaryEntry>,
    val chapters: List<ChapterSummary>,
    val narration: NarrationSettings,
    val syncStatus: SyncStatus = SyncStatus(),
    val regeneratableTurns: Set<Int> = emptySet(),
    val version: Long = 0L
)
