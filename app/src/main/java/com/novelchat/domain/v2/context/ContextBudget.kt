package com.novelchat.domain.v2.context

data class ContextBudget(
    val systemChars: Int,
    val chapterChars: Int,
    val historyChars: Int,
    val contextChars: Int,
    val latestTurnFloorChars: Int = 3_000,
    val maxPresentNpcs: Int = 4,
    val maxRelatedNpcs: Int = 4,
    val maxWorldbookEntries: Int = 8,
    val maxMemoriesPerNpc: Int = 3,
    val maxPendingEvents: Int = 3
) {
    companion object {
        val REMOTE = ContextBudget(
            systemChars = 6_000,
            chapterChars = 2_000,
            historyChars = 14_000,
            contextChars = 5_000,
            latestTurnFloorChars = 3_000
        )
        val LOCAL = ContextBudget(
            systemChars = 2_500,
            chapterChars = 600,
            historyChars = 4_000,
            contextChars = 2_000,
            latestTurnFloorChars = 2_500,
            maxWorldbookEntries = 6
        )

        fun forEndpoint(isLocal: Boolean): ContextBudget = if (isLocal) LOCAL else REMOTE
    }
}
