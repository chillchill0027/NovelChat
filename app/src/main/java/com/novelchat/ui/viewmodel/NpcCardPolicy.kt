package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.Relationship

internal data class NpcRelationshipSnapshot(
    val favor: Int,
    val trust: Int?,
    val stage: String
)

internal object NpcCardPolicy {

    fun relationshipSnapshot(card: CharacterCard, playerName: String): NpcRelationshipSnapshot {
        val relationship = findRelationship(card.relationships, playerName)
        val favor = (relationship?.favor ?: card.favor).coerceIn(-100, 100)
        val inferredStage = stageForFavor(favor)
        val storedStage = relationship?.stage.orEmpty().trim()
        val selectedStage = storedStage.takeIf {
            it.isNotBlank() && (it !in genericStages || inferredStage == "陌生")
        } ?: inferredStage
        return NpcRelationshipSnapshot(
            favor = favor,
            trust = relationship?.trust?.coerceIn(0, 100),
            stage = normalizeStage(selectedStage)
        )
    }

    fun trustLabel(trust: Int?): String {
        return trust?.coerceIn(0, 100)?.let { "$it/100" } ?: "未知"
    }

    private fun findRelationship(
        relationships: Map<String, Relationship>,
        rawPlayerName: String
    ): Relationship? {
        val playerName = rawPlayerName.trim()
        if (playerName.isBlank()) return null
        relationships[playerName]?.let { return it }
        return relationships.entries
            .filter { (rawName, _) ->
                val name = rawName.trim()
                name.isNotBlank() && (name.contains(playerName) || playerName.contains(name))
            }
            .singleOrNull()
            ?.value
    }

    private fun stageForFavor(favor: Int): String = when {
        favor >= 60 -> "信赖"
        favor >= 20 -> "友好"
        favor <= -40 -> "敌意"
        favor <= -10 -> "冷淡"
        else -> "陌生"
    }

    private fun normalizeStage(value: String): String {
        return value
            .replace(stageSeparator, " · ")
            .replace(whitespace, " ")
            .trim()
            .ifBlank { "陌生" }
    }

    private val genericStages = setOf("陌生", "陌生人", "初识", "未知")
    private val stageSeparator = Regex("""\s*[/／|｜]\s*""")
    private val whitespace = Regex("""\s+""")
}
