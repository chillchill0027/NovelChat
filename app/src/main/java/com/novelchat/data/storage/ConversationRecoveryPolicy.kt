package com.novelchat.data.storage

import com.novelchat.data.model.ConversationEntry
import com.novelchat.data.model.NarrativeEntry

internal data class ConversationRecoverySnapshot(
    val id: Long,
    val playerName: String,
    val worldName: String,
    val worldDescription: String,
    val currentRound: Int,
    val narratives: List<NarrativeEntry>,
    val fallbackTimestamp: String
)

internal object ConversationRecoveryPolicy {
    const val CORRUPT_DATA_DESCRIPTION = "存档部分文件损坏，打开时会停止写入以保护原数据"

    fun create(snapshot: ConversationRecoverySnapshot): ConversationEntry {
        val latestNarrative = snapshot.narratives.maxByOrNull { it.turn }
        val latestRound = snapshot.narratives.maxOfOrNull { it.turn } ?: 0
        return ConversationEntry(
            id = snapshot.id,
            characterName = snapshot.playerName.trim()
                .ifBlank { snapshot.worldName.trim() }
                .ifBlank { "恢复的故事" },
            characterDesc = snapshot.worldDescription.trim().ifBlank { "从本地存档恢复" },
            type = "recovered",
            createdAt = snapshot.fallbackTimestamp,
            lastMessageAt = latestNarrative?.createdAt
                ?.takeIf { it.isNotBlank() }
                ?: snapshot.fallbackTimestamp,
            messageCount = maxOf(snapshot.currentRound, latestRound).coerceAtLeast(0),
            lastMessage = ConversationIndexPolicy.preview(latestNarrative)
        )
    }

    fun merge(existing: ConversationEntry, recovered: ConversationEntry): ConversationEntry {
        require(existing.id == recovered.id) { "不能合并不同故事的索引信息" }
        val recoveredHasIdentity = recovered.characterName.isNotBlank() &&
            recovered.characterName != "恢复的故事"
        val recoveredIsAtLeastAsCurrent = recovered.messageCount >= existing.messageCount
        return existing.copy(
            characterName = recovered.characterName.takeIf { recoveredHasIdentity }
                ?: existing.characterName,
            characterDesc = when {
                recovered.characterDesc == CORRUPT_DATA_DESCRIPTION -> CORRUPT_DATA_DESCRIPTION
                existing.characterDesc == CORRUPT_DATA_DESCRIPTION -> recovered.characterDesc
                else -> existing.characterDesc
            },
            createdAt = existing.createdAt.ifBlank { recovered.createdAt },
            lastMessageAt = maxTimestamp(existing.lastMessageAt, recovered.lastMessageAt),
            messageCount = maxOf(existing.messageCount, recovered.messageCount),
            lastMessage = recovered.lastMessage
                .takeIf { recoveredIsAtLeastAsCurrent && it.isNotBlank() }
                ?: existing.lastMessage
        )
    }

    private fun maxTimestamp(left: String, right: String): String = when {
        left.isBlank() -> right
        right.isBlank() -> left
        else -> maxOf(left, right)
    }
}
