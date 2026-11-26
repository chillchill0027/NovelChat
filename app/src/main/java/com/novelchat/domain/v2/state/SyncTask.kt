package com.novelchat.domain.v2.state

data class SyncTask(
    val storyId: Long,
    val rounds: List<Int>,
    val clearsLegacyPendingFlag: Boolean = false
) {
    val lastRound: Int
        get() = rounds.maxOrNull() ?: 0
}
