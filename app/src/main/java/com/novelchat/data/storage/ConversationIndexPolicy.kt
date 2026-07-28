package com.novelchat.data.storage

import com.novelchat.data.model.ConversationEntry
import com.novelchat.data.model.NarrativeEntry

object ConversationIndexPolicy {
    fun sanitize(entries: List<ConversationEntry>): List<ConversationEntry> {
        return entries
            .asSequence()
            .filter { it.id > 0 }
            .map { entry ->
                entry.copy(
                    characterName = entry.characterName.orEmpty().trim().ifBlank { "未命名故事" },
                    characterDesc = entry.characterDesc.orEmpty(),
                    type = entry.type.orEmpty().ifBlank { "story" },
                    presetKey = entry.presetKey.orEmpty(),
                    createdAt = entry.createdAt.orEmpty(),
                    lastMessageAt = entry.lastMessageAt.orEmpty(),
                    messageCount = entry.messageCount.coerceAtLeast(0),
                    lastMessage = entry.lastMessage.orEmpty()
                )
            }
            .distinctBy { it.id }
            .toList()
    }

    fun sort(entries: List<ConversationEntry>): List<ConversationEntry> {
        return sanitize(entries).sortedWith(
            compareByDescending<ConversationEntry> { it.lastMessageAt.ifBlank { it.createdAt } }
                .thenByDescending { it.id }
        )
    }

    fun updateAfterRound(
        entries: List<ConversationEntry>,
        characterId: Long,
        latestNarrative: NarrativeEntry?,
        round: Int,
        now: String
    ): List<ConversationEntry> {
        val index = entries.indexOfFirst { it.id == characterId }
        if (index < 0) return entries

        val updated = entries[index].copy(
            lastMessageAt = now,
            lastMessage = preview(latestNarrative).ifBlank { entries[index].lastMessage },
            messageCount = round
        )
        return listOf(updated) + entries.filterIndexed { itemIndex, _ -> itemIndex != index }
    }

    fun preview(narrative: NarrativeEntry?, maxLength: Int = 80): String {
        if (narrative == null) return ""
        return narrative.narrative
            .ifBlank { narrative.userInput }
            .replace(Regex("""\s+"""), " ")
            .trim()
            .take(maxLength)
    }
}
