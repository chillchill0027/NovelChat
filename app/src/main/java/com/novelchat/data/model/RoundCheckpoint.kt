package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class RoundCheckpoint(
    @SerializedName("version") val version: Int = CURRENT_VERSION,
    @SerializedName("round") val round: Int = 0,
    @SerializedName("story_state") val storyState: StoryState = StoryState(),
    @SerializedName("player_state") val playerState: PlayerState = PlayerState(),
    @SerializedName("character_cards") val characterCards: List<CharacterCard> = emptyList(),
    @SerializedName("worldbook_entries") val worldbookEntries: List<WorldbookEntry> = emptyList(),
    @SerializedName("created_at") val createdAt: String = ""
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
