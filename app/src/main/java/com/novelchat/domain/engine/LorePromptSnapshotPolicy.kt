package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.WorldLore
import com.novelchat.data.model.WorldSetting

internal object LorePromptSnapshotPolicy {
    private const val MAX_TEXT = 800
    private const val MAX_LIST_ITEMS = 10
    private const val MAX_MAP_ITEMS = 10

    fun world(setting: WorldSetting): Map<String, Any> {
        return linkedMapOf<String, Any>().apply {
            putText("name", setting.name)
            putText("genre", setting.genre)
            putText("tech_level", setting.techLevel)
            putText("magic_level", setting.magicLevel)
            putText("tone", setting.tone)
            putList("forbidden", setting.forbidden)
            putText("description", setting.description)
            worldLore(setting.lore).takeIf { it.isNotEmpty() }?.let { put("lore", it) }
        }
    }

    fun character(lore: CharacterLore): Map<String, Any> {
        return linkedMapOf<String, Any>().apply {
            putList("aliases", lore.aliases)
            putText("role", lore.role)
            putText("pronouns", lore.pronouns)
            putText("birth_date", lore.birthDate)
            putText("nationality", lore.nationality)
            putText("residence", lore.residence)
            putText("education", lore.education)
            putList("values", lore.values)
            putList("goals", lore.goals)
            putList("motivations", lore.motivations)
            putList("fears", lore.fears)
            putList("weaknesses", lore.weaknesses)
            putList("secrets", lore.secrets)
            putList("habits", lore.habits)
            putText("speech_style", lore.speechStyle)
            putList("abilities", lore.abilities)
            putList("knowledge", lore.knowledge)
            putList("boundaries", lore.boundaries)
            putText("character_arc", lore.characterArc)
            putMap("relationship_notes", lore.relationshipNotes)
            putMap("custom", lore.custom)
        }
    }

    private fun worldLore(lore: WorldLore): Map<String, Any> {
        return linkedMapOf<String, Any>().apply {
            putText("era", lore.era)
            putText("premise", lore.premise)
            putText("history", lore.history)
            putText("geography", lore.geography)
            putText("cosmology", lore.cosmology)
            putText("society", lore.society)
            putText("culture", lore.culture)
            putText("politics", lore.politics)
            putText("economy", lore.economy)
            putText("law", lore.law)
            putText("religion", lore.religion)
            putList("languages", lore.languages)
            putText("calendar", lore.calendar)
            putText("power_system", lore.powerSystem)
            putText("technology_rules", lore.technologyRules)
            putList("factions", lore.factions)
            putList("conflicts", lore.conflicts)
            putList("rules", lore.rules)
            putList("themes", lore.themes)
            putText("narrative_style", lore.narrativeStyle)
            putMap("custom", lore.custom)
        }
    }

    private fun MutableMap<String, Any>.putText(key: String, value: String) {
        value.trim().take(MAX_TEXT).takeIf { it.isNotBlank() }?.let { put(key, it) }
    }

    private fun MutableMap<String, Any>.putList(key: String, values: List<String>) {
        values.asSequence()
            .map { it.trim().take(MAX_TEXT) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_LIST_ITEMS)
            .toList()
            .takeIf { it.isNotEmpty() }
            ?.let { put(key, it) }
    }

    private fun MutableMap<String, Any>.putMap(key: String, values: Map<String, String>) {
        values.entries.asSequence()
            .mapNotNull { (entryKey, entryValue) ->
                val normalizedKey = entryKey.trim().take(80)
                val normalizedValue = entryValue.trim().take(MAX_TEXT)
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .take(MAX_MAP_ITEMS)
            .toMap()
            .takeIf { it.isNotEmpty() }
            ?.let { put(key, it) }
    }
}
