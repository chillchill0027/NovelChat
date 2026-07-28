package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.CharacterReferencePolicy
import com.novelchat.data.model.StoryState
import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedOpening
import com.novelchat.data.remote.model.ParsedWorldbookItem

internal object ImportedOpeningPolicy {

    fun normalize(
        opening: ParsedOpening,
        worldbook: List<ParsedWorldbookItem>,
        npcs: List<ParsedNpc> = emptyList()
    ): ParsedOpening {
        val location = opening.currentLocation.trim().ifBlank {
            worldbook.firstOrNull { it.type == "location" }?.name.orEmpty()
                .ifBlank { worldbook.firstOrNull()?.name.orEmpty() }
                .ifBlank { "故事起点" }
        }
        val fallbackEvent = worldbook.firstOrNull { it.type == "event" }?.let { event ->
            event.description.ifBlank { event.name }
        }.orEmpty().ifBlank {
            "在${location}确认眼前处境并决定下一步行动"
        }
        val pendingEvents = opening.pendingEvents
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .ifEmpty { listOf(fallbackEvent) }

        return opening.copy(
            currentLocation = normalizeCharacterReferences(location, npcs),
            timeOfDay = opening.timeOfDay.trim().ifBlank { "开局时刻" },
            weather = opening.weather.trim(),
            mood = normalizeCharacterReferences(
                opening.mood.trim().ifBlank { "局势尚待展开" },
                npcs
            ),
            sceneDescription = normalizeCharacterReferences(
                opening.sceneDescription.trim(),
                npcs
            ),
            activeCharacters = opening.activeCharacters
                .map(String::trim)
                .filter(String::isNotBlank)
                .map { normalizeCharacterReferences(it, npcs) }
                .mapNotNull { reference ->
                    npcs.firstOrNull { npc ->
                        reference == npc.name || reference.contains(npc.name)
                    }?.name
                }
                .distinct(),
            pendingEvents = pendingEvents
                .map { normalizeCharacterReferences(it, npcs) }
                .distinct(),
            globalFlags = opening.globalFlags
                .mapKeys { (key, _) -> key.trim() }
                .mapValues { (_, value) -> value.trim() }
                .filter { (key, value) -> key.isNotBlank() && value.isNotBlank() }
        )
    }

    fun createStoryState(opening: ParsedOpening): StoryState {
        return StoryState(
            currentLocation = opening.currentLocation,
            timeOfDay = opening.timeOfDay,
            weather = opening.weather,
            mood = opening.mood,
            sceneDescription = opening.sceneDescription,
            activeCharacters = opening.activeCharacters,
            pendingEvents = opening.pendingEvents,
            globalFlags = opening.globalFlags
        )
    }

    private fun normalizeCharacterReferences(text: String, npcs: List<ParsedNpc>): String {
        if (text.isBlank() || npcs.isEmpty()) return text
        val references = npcs.mapNotNull { npc ->
            val rawName = npc.name.trim()
            if (rawName.isBlank()) return@mapNotNull null
            val card = CharacterNamingPolicy.normalizeNpcCard(
                CharacterCard(
                    id = rawName,
                    name = rawName,
                    occupation = npc.occupation,
                    background = npc.background,
                    relationshipStatus = npc.relationshipToPlayer
                )
            )
            rawName to card
        }
        val cards = references.map { it.second }
        var normalized = text

        references
            .filter { (rawName, card) ->
                rawName != card.name && CharacterNamingPolicy.isGenericNpcName(rawName)
            }
            .sortedByDescending { (rawName, _) -> rawName.length }
            .forEach { (rawName, card) ->
                val replacement = if (placeholderSuffix.containsMatchIn(rawName)) {
                    card.name
                } else {
                    "$rawName${card.name}"
                }
                normalized = normalized.replace(
                    Regex("${Regex.escape(rawName)}(?!${Regex.escape(card.name)})"),
                    replacement
                )
            }

        genericRoleReferences.forEach { role ->
            if (!normalized.contains(role)) return@forEach
            val card = CharacterReferencePolicy.resolve(cards, role) ?: return@forEach
            if (normalized.contains(card.name)) return@forEach
            normalized = normalized.replace(
                Regex("${Regex.escape(role)}(?!${Regex.escape(card.name)})"),
                "$role${card.name}"
            )
        }
        return normalized
    }

    private val placeholderSuffix = Regex("[A-Z甲乙丙丁一二三四五六七八九十0-9]+$")
    private val genericRoleReferences = listOf(
        "咖啡店店员", "便利店店员", "彩票店老板", "招聘顾问", "前台接待", "工作人员",
        "女朋友", "男朋友", "服务员", "陌生人", "店员", "店主", "老板", "邻居", "居民",
        "同事", "前台", "接待", "顾客", "医生", "护士", "保安", "司机", "房东", "顾问",
        "女友", "男友", "妻子", "丈夫", "老婆", "老公", "伴侣"
    )
}
