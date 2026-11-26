package com.novelchat.data.repo

import com.google.gson.JsonElement
import com.google.gson.annotations.SerializedName

data class RoundCommitJournal(
    @SerializedName("version") val version: Int = CURRENT_VERSION,
    @SerializedName("rounds") val rounds: List<Int> = emptyList(),
    @SerializedName("status") val status: String = STATUS_PREPARED,
    @SerializedName("writes") val writes: List<EntityWrite> = emptyList()
) {
    val lastRound: Int
        get() = rounds.maxOrNull() ?: 0

    companion object {
        const val CURRENT_VERSION = 1
        const val STATUS_PREPARED = "prepared"
        const val STATUS_COMPLETED = "completed"
    }
}

data class EntityWrite(
    @SerializedName("type") val type: String = "",
    @SerializedName("key") val key: String = "",
    @SerializedName("after") val after: JsonElement? = null
) {
    companion object {
        const val TYPE_PLAYER = "player"
        const val TYPE_CHARACTER = "character"
        const val TYPE_WORLDBOOK = "worldbook"
        const val TYPE_STORY = "story"
        const val TYPE_SUMMARY = "summary"
        const val TYPE_CHAPTER = "chapter"
    }
}

data class SyncStatus(
    @SerializedName("last_synced_round") val lastSyncedRound: Int = 0,
    @SerializedName("failed_rounds") val failedRounds: List<Int> = emptyList(),
    @SerializedName("last_error") val lastError: String = "",
    @SerializedName("updated_at") val updatedAt: String = ""
)
