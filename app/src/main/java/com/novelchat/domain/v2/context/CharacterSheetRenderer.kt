package com.novelchat.domain.v2.context

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.MemoryNote

object CharacterSheetRenderer {

    fun full(
        card: CharacterCard,
        playerName: String,
        characterFields: List<CharacterFieldDefinition>,
        currentRound: Int,
        maxMemories: Int = 3
    ): String {
        val relationship = card.relationships[playerName]
        val relStage = relationship?.stage?.takeIf { it.isNotBlank() }
            ?: card.relationshipStatus.takeIf { it.isNotBlank() && it != "未知" }
        val headerMeta = buildList {
            card.occupation.takeIf { it.isNotBlank() }?.let(::add)
            card.age.takeIf { it > 0 }?.let { add("${it}岁") }
        }.joinToString("，")
        val relationPart = relStage?.let { "与${playerName.ifBlank { "主角" }}：$it" }
        val fullMeta = listOfNotNull(
            headerMeta.takeIf { it.isNotBlank() },
            relationPart
        ).joinToString("；")

        val lines = mutableListOf<String>()
        lines += if (fullMeta.isNotBlank()) "### ${card.name}（$fullMeta）" else "### ${card.name}"

        if (card.personality.isNotBlank()) {
            lines += "性格：${card.personality.trim()}"
        }
        if (card.appearance.isNotBlank()) {
            lines += "外貌：${card.appearance.trim()}"
        }
        if (card.lore.speechStyle.isNotBlank()) {
            lines += "说话方式：${card.lore.speechStyle.trim()}"
        }
        if (card.lore.sampleDialogues.isNotEmpty()) {
            val quotes = card.lore.sampleDialogues
                .map(String::trim)
                .filter(String::isNotBlank)
                .take(4)
                .joinToString("") { "「${it.trim('「', '」', '“', '”', '"')}」" }
            if (quotes.isNotBlank()) lines += "示例台词：$quotes"
        }

        val nowParts = mutableListOf<String>()
        val mood = card.emotion.takeIf { it.isNotBlank() } ?: card.mentalState.mood
        if (mood.isNotBlank()) {
            val thoughts = card.mentalState.currentThoughts.takeIf { it.isNotBlank() }
            nowParts += if (thoughts != null) "$mood（$thoughts）" else mood
        }
        val favor = relationship?.favor ?: card.favor
        val trust = relationship?.trust ?: 0
        nowParts += "好感 $favor，信任 $trust/100"

        val dynamicExtra = CharacterFieldPolicy.effectiveDefinitions(characterFields)
            .filter { field ->
                field.display != CharacterFieldDefinition.DISPLAY_HIDDEN &&
                    field.updateMode == CharacterFieldDefinition.UPDATE_DYNAMIC &&
                    CharacterFieldPolicy.appliesTo(field, CharacterFieldDefinition.TARGET_NPC) &&
                    field.key !in standardNpcStatusKeys
            }
            .mapNotNull { field ->
                val raw = CharacterFieldPolicy.valueFor(field, card, playerName) ?: return@mapNotNull null
                "${field.label} ${CharacterFieldPolicy.formatValue(field, raw, compact = false)}"
            }
            .take(4)
        nowParts.addAll(dynamicExtra)
        if (nowParts.isNotEmpty()) {
            lines += "此刻：${nowParts.joinToString("；")}。"
        }

        if (maxMemories > 0 && card.memories.isNotEmpty()) {
            val topMemories = selectTopMemories(card.memories, currentRound, maxMemories)
            if (topMemories.isNotEmpty()) {
                val rendered = topMemories.joinToString("；") { "${it.text}（第${it.turn}轮）" }
                lines += "记得：$rendered。"
            }
        }

        val authorNotes = buildList {
            card.lore.secrets.filter { it.isNotBlank() }.forEach { add(it.trim()) }
            card.kink.takeIf { it.isNotBlank() }?.let { add(it.trim()) }
        }.distinct()
        if (authorNotes.isNotEmpty()) {
            lines += "作者备注（角色不会主动透露）：${authorNotes.joinToString("；")}"
        }
        return lines.joinToString("\n")
    }

    fun brief(card: CharacterCard, playerName: String): String {
        val relationship = card.relationships[playerName]
        val relStage = relationship?.stage?.takeIf { it.isNotBlank() }
            ?: card.relationshipStatus.takeIf { it.isNotBlank() && it != "未知" }
        val parts = buildList {
            card.occupation.takeIf { it.isNotBlank() }?.let(::add)
            relStage?.let { add("与${playerName.ifBlank { "主角" }}：$it") }
        }
        return if (parts.isEmpty()) card.name else "${card.name}（${parts.joinToString("，")}）"
    }

    fun selectTopMemories(
        memories: List<MemoryNote>,
        currentRound: Int,
        limit: Int
    ): List<MemoryNote> {
        if (limit <= 0 || memories.isEmpty()) return emptyList()
        return memories
            .filter { it.text.isNotBlank() }
            .sortedByDescending { note ->
                note.weight * 10 - (currentRound - note.turn).coerceAtLeast(0)
            }
            .take(limit)
    }

    private val standardNpcStatusKeys = setOf(
        "mood", "favor", "trust", "relationship_stage", "current_thoughts",
        "health", "fatigue", "hunger", "pain", "stress", "focus"
    )
}
