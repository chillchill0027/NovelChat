package com.novelchat.data.remote.model

import com.google.gson.annotations.SerializedName
import com.novelchat.data.model.CharacterLore
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.InventoryItem
import com.novelchat.data.model.MentalState
import com.novelchat.data.model.PhysicalState
import com.novelchat.data.model.WorldLore

/**
 * AI 解析用户提供的背景故事文本后的结构化结果
 */
data class StoryParseResult(
    @SerializedName("world_setting") val worldSetting: ParsedWorldSetting = ParsedWorldSetting(),
    @SerializedName("player") val player: ParsedPlayer = ParsedPlayer(),
    @SerializedName("npcs") val npcs: List<ParsedNpc> = emptyList(),
    @SerializedName("worldbook") val worldbook: List<ParsedWorldbookItem> = emptyList(),
    @SerializedName("opening") val opening: ParsedOpening = ParsedOpening()
)

data class ParsedOpening(
    @SerializedName("current_location") val currentLocation: String = "",
    @SerializedName("time_of_day") val timeOfDay: String = "",
    @SerializedName("weather") val weather: String = "",
    @SerializedName("mood") val mood: String = "",
    @SerializedName("scene_description") val sceneDescription: String = "",
    @SerializedName("active_characters") val activeCharacters: List<String> = emptyList(),
    @SerializedName("pending_events") val pendingEvents: List<String> = emptyList(),
    @SerializedName("global_flags") val globalFlags: Map<String, String> = emptyMap()
)

data class ParsedWorldSetting(
    @SerializedName("name") val name: String = "",
    @SerializedName("genre") val genre: String = "",
    @SerializedName("tech_level") val techLevel: String = "",
    @SerializedName("magic_level") val magicLevel: String = "",
    @SerializedName("tone") val tone: String = "",
    @SerializedName("forbidden") val forbidden: List<String> = emptyList(),
    @SerializedName("description") val description: String = "",
    @SerializedName("lore") val lore: WorldLore = WorldLore(),
    @SerializedName("character_fields") val characterFields: List<CharacterFieldDefinition> = emptyList()
)

data class ParsedPlayer(
    @SerializedName("name") val name: String = "",
    @SerializedName("age") val age: Int = 20,
    @SerializedName("gender") val gender: String = "",
    @SerializedName("race") val race: String = "",
    @SerializedName("class") val class_: String = "",
    @SerializedName("personality") val personality: String = "",
    @SerializedName("background") val background: String = "",
    @SerializedName("appearance") val appearance: String = "",
    @SerializedName("skills") val skills: List<String> = emptyList(),
    @SerializedName("level") val level: Int = 1,
    @SerializedName("charm") val charm: Int = 25,
    @SerializedName("action_skill") val actionSkill: Int = 10,
    @SerializedName("social_rank") val socialRank: String = "E",
    @SerializedName("luck") val luck: Int = 50,
    @SerializedName("willpower") val willpower: Int = 15,
    @SerializedName("resilience") val resilience: Int = 20,
    @SerializedName("lore") val lore: CharacterLore = CharacterLore(),
    @SerializedName("physical_state") val physicalState: PhysicalState = PhysicalState(),
    @SerializedName("mental_state") val mentalState: MentalState = MentalState(),
    @SerializedName("custom_attributes") val customAttributes: Map<String, String> = emptyMap(),
    @SerializedName("inventory") val inventory: List<InventoryItem> = emptyList()
)

data class PlayerModificationPatch(
    @SerializedName("name") val name: String? = null,
    @SerializedName("age") val age: Int? = null,
    @SerializedName("gender") val gender: String? = null,
    @SerializedName("race") val race: String? = null,
    @SerializedName("class") val class_: String? = null,
    @SerializedName("personality") val personality: String? = null,
    @SerializedName("background") val background: String? = null,
    @SerializedName("appearance") val appearance: String? = null,
    @SerializedName("skills") val skills: List<String>? = null
)

data class ParsedNpc(
    @SerializedName("name") val name: String = "",
    @SerializedName("age") val age: Int = 0,
    @SerializedName("gender") val gender: String = "",
    @SerializedName("race") val race: String = "",
    @SerializedName("occupation") val occupation: String = "",
    @SerializedName("personality") val personality: String = "",
    @SerializedName("background") val background: String = "",
    @SerializedName("appearance") val appearance: String = "",
    @SerializedName("relationship_to_player") val relationshipToPlayer: String = "",
    @SerializedName("lore") val lore: CharacterLore = CharacterLore(),
    @SerializedName("physical_state") val physicalState: PhysicalState = PhysicalState(),
    @SerializedName("mental_state") val mentalState: MentalState = MentalState(),
    @SerializedName("custom_attributes") val customAttributes: Map<String, String> = emptyMap(),
    @SerializedName("inventory") val inventory: List<InventoryItem> = emptyList()
)

data class ParsedWorldbookItem(
    @SerializedName("type") val type: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("keywords") val keywords: List<String> = emptyList(),
    @SerializedName("related_characters") val relatedCharacters: List<String> = emptyList(),
    @SerializedName("related_events") val relatedEvents: List<String> = emptyList(),
    @SerializedName("related_locations") val relatedLocations: List<String> = emptyList(),
    @SerializedName("is_secret") val isSecret: Boolean = false,
    @SerializedName("known_by_player") val knownByPlayer: Boolean = true,
    @SerializedName("status") val status: String = "正常"
)
