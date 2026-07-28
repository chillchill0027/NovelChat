package com.novelchat.ui.viewmodel

import com.novelchat.data.model.WorldbookContentPolicy
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.MentalState
import com.novelchat.data.model.PhysicalState
import com.novelchat.data.model.WorldLore
import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedOpening
import com.novelchat.data.remote.model.ParsedPlayer
import com.novelchat.data.remote.model.ParsedWorldSetting
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult

object StoryParseContentPolicy {
    const val MAX_NPCS = 50
    const val MAX_WORLDBOOK_ENTRIES = 100
    private const val MAX_SHORT_LENGTH = 120
    private const val MAX_NAME_LENGTH = 80
    private const val MAX_SUMMARY_LENGTH = 2_000
    private const val MAX_LONG_LENGTH = 6_000
    private const val MAX_LIST_ITEMS = 30
    private const val MAX_LORE_LIST_ITEMS = 20
    private const val MAX_CUSTOM_FIELDS = 24

    fun normalize(result: StoryParseResult): StoryParseResult {
        return result.copy(
            worldSetting = normalizeWorld(result.worldSetting),
            player = normalizePlayer(result.player),
            npcs = result.npcs
                .map(::normalizeNpc)
                .distinctBy { npc -> npc.name.lowercase() }
                .take(MAX_NPCS),
            worldbook = result.worldbook
                .map(::normalizeWorldbook)
                .distinctBy { item -> "${item.type}:${item.name.lowercase()}" }
                .take(MAX_WORLDBOOK_ENTRIES),
            opening = normalizeOpening(result.opening)
        )
    }

    private fun normalizeWorld(world: ParsedWorldSetting): ParsedWorldSetting {
        return world.copy(
            name = name(world.name),
            genre = short(world.genre),
            techLevel = short(world.techLevel),
            magicLevel = short(world.magicLevel),
            tone = summary(world.tone),
            forbidden = list(world.forbidden),
            description = long(world.description),
            lore = normalizeWorldLore(world.lore)
        )
    }

    private fun normalizePlayer(player: ParsedPlayer): ParsedPlayer {
        return player.copy(
            name = name(player.name),
            age = player.age.takeIf { it in 1..120 } ?: 20,
            gender = short(player.gender),
            race = short(player.race),
            class_ = short(player.class_),
            personality = summary(player.personality),
            background = long(player.background),
            appearance = summary(player.appearance),
            skills = list(player.skills),
            lore = normalizeCharacterLore(player.lore),
            physicalState = normalizePhysicalState(player.physicalState),
            mentalState = normalizeMentalState(player.mentalState),
            inventory = normalizeInventory(player.inventory)
        )
    }

    private fun normalizeNpc(npc: ParsedNpc): ParsedNpc {
        return npc.copy(
            name = name(npc.name),
            age = npc.age.takeIf { it in 1..120 } ?: 0,
            gender = short(npc.gender),
            race = short(npc.race),
            occupation = short(npc.occupation),
            personality = summary(npc.personality),
            background = long(npc.background),
            appearance = summary(npc.appearance),
            relationshipToPlayer = short(npc.relationshipToPlayer),
            lore = normalizeCharacterLore(npc.lore),
            physicalState = normalizePhysicalState(npc.physicalState),
            mentalState = normalizeMentalState(npc.mentalState),
            inventory = normalizeInventory(npc.inventory)
        )
    }

    private fun normalizeWorldbook(item: ParsedWorldbookItem): ParsedWorldbookItem {
        return item.copy(
            type = WorldbookContentPolicy.normalizeType(item.type),
            name = WorldbookContentPolicy.normalizeName(item.name),
            description = WorldbookContentPolicy.normalizeDescription(item.description),
            keywords = WorldbookContentPolicy.normalizeKeywords(item.keywords),
            relatedCharacters = list(item.relatedCharacters),
            relatedEvents = list(item.relatedEvents),
            relatedLocations = list(item.relatedLocations),
            status = short(item.status).ifBlank { "正常" }
        )
    }

    private fun normalizeOpening(opening: ParsedOpening): ParsedOpening {
        return opening.copy(
            currentLocation = name(opening.currentLocation),
            timeOfDay = short(opening.timeOfDay),
            weather = short(opening.weather),
            mood = summary(opening.mood),
            sceneDescription = summary(opening.sceneDescription),
            activeCharacters = list(opening.activeCharacters),
            pendingEvents = list(opening.pendingEvents),
            globalFlags = map(opening.globalFlags)
        )
    }

    private fun normalizeWorldLore(lore: WorldLore): WorldLore {
        return lore.copy(
            era = short(lore.era),
            premise = summary(lore.premise),
            history = summary(lore.history),
            geography = summary(lore.geography),
            cosmology = summary(lore.cosmology),
            society = summary(lore.society),
            culture = summary(lore.culture),
            politics = summary(lore.politics),
            economy = summary(lore.economy),
            law = summary(lore.law),
            religion = summary(lore.religion),
            languages = loreList(lore.languages),
            calendar = summary(lore.calendar),
            powerSystem = summary(lore.powerSystem),
            technologyRules = summary(lore.technologyRules),
            factions = loreList(lore.factions),
            conflicts = loreList(lore.conflicts),
            rules = loreList(lore.rules),
            themes = loreList(lore.themes),
            narrativeStyle = summary(lore.narrativeStyle),
            custom = map(lore.custom)
        )
    }

    private fun normalizeCharacterLore(lore: CharacterLore): CharacterLore {
        return lore.copy(
            aliases = loreList(lore.aliases),
            role = short(lore.role),
            pronouns = short(lore.pronouns),
            birthDate = short(lore.birthDate),
            nationality = short(lore.nationality),
            residence = summary(lore.residence),
            education = summary(lore.education),
            values = loreList(lore.values),
            goals = loreList(lore.goals),
            motivations = loreList(lore.motivations),
            fears = loreList(lore.fears),
            weaknesses = loreList(lore.weaknesses),
            secrets = loreList(lore.secrets),
            habits = loreList(lore.habits),
            speechStyle = summary(lore.speechStyle),
            abilities = loreList(lore.abilities),
            knowledge = loreList(lore.knowledge),
            boundaries = loreList(lore.boundaries),
            characterArc = summary(lore.characterArc),
            relationshipNotes = map(lore.relationshipNotes),
            custom = map(lore.custom)
        )
    }

    private fun normalizePhysicalState(state: PhysicalState): PhysicalState {
        return state.copy(
            stamina = state.stamina.coerceIn(0, 100),
            health = short(state.health).ifBlank { "良好" },
            injuries = loreList(state.injuries),
            fatigue = short(state.fatigue).ifBlank { "无" },
            hunger = short(state.hunger).ifBlank { "不饿" },
            pain = short(state.pain).ifBlank { "无" }
        )
    }

    private fun normalizeMentalState(state: MentalState): MentalState {
        return state.copy(
            mood = short(state.mood),
            stress = short(state.stress).ifBlank { "低" },
            focus = summary(state.focus),
            currentThoughts = summary(state.currentThoughts)
        )
    }

    private fun normalizeInventory(items: List<InventoryItem>): List<InventoryItem> {
        return items
            .map { item ->
                item.copy(
                    name = name(item.name),
                    quantity = item.quantity.coerceIn(1, 9_999),
                    type = short(item.type),
                    description = summary(item.description),
                    origin = summary(item.origin),
                    acquiredAt = short(item.acquiredAt)
                )
            }
            .filter { it.name.isNotBlank() }
            .distinctBy { it.name.lowercase() }
            .take(MAX_LIST_ITEMS)
    }

    private fun name(value: String): String = value.trim().take(MAX_NAME_LENGTH)

    private fun short(value: String): String = value.trim().take(MAX_SHORT_LENGTH)

    private fun summary(value: String): String = value.trim().take(MAX_SUMMARY_LENGTH)

    private fun long(value: String): String = value.trim().take(MAX_LONG_LENGTH)

    private fun list(values: List<String>): List<String> {
        return values
            .map(::short)
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_LIST_ITEMS)
    }

    private fun loreList(values: List<String>): List<String> {
        return values
            .map(::summary)
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_LORE_LIST_ITEMS)
    }

    private fun map(values: Map<String, String>): Map<String, String> {
        return values.entries
            .mapNotNull { (key, value) ->
                val normalizedKey = name(key)
                val normalizedValue = summary(value)
                if (normalizedKey.isBlank() || normalizedValue.isBlank()) null
                else normalizedKey to normalizedValue
            }
            .distinctBy { it.first.lowercase() }
            .take(MAX_CUSTOM_FIELDS)
            .toMap()
    }
}
