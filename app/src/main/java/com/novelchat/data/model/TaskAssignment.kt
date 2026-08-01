package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class TaskAssignment(
    @SerializedName("story_update") val storyUpdate: StoryStateUpdate? = null,
    @SerializedName("player_update") val playerUpdate: PlayerUpdate? = null,
    @SerializedName("new_characters") val newCharacters: List<NewCharacter> = emptyList(),
    @SerializedName("retired_characters") val retiredCharacters: List<RetiredCharacter> = emptyList(),
    @SerializedName("character_updates") val characterUpdates: List<CharacterUpdateRequest> = emptyList(),
    @SerializedName("worldbook_updates") val worldbookUpdates: List<WorldbookUpdate> = emptyList(),
    @SerializedName("item_transfers") val itemTransfers: List<ItemTransfer> = emptyList(),
    @SerializedName("events") val events: List<EventRecord> = emptyList(),
    @SerializedName("sync_status") val syncStatus: String = "",
    @SerializedName("no_change_reason") val noChangeReason: String = ""
)

data class PlayerUpdate(
    @SerializedName("reason") val reason: String = "",
    @SerializedName("changes") val changes: PlayerChanges = PlayerChanges()
)

data class PlayerChanges(
    @SerializedName("inventory") val inventory: List<InventoryChange> = emptyList(),
    @SerializedName("relationships") val relationships: Map<String, RelationshipStatePatch> = emptyMap(),
    @SerializedName("quest_log") val questLog: List<Quest>? = null,
    @SerializedName("story_progress") val storyProgress: StoryProgressPatch? = null,
    @SerializedName("physical_state") val physicalState: PhysicalStatePatch? = null,
    @SerializedName("mental_state") val mentalState: MentalStatePatch? = null,
    @SerializedName("attributes") val attributes: Map<String, String> = emptyMap(),
    @SerializedName("risk") val risk: Int? = null
)

data class RelationshipStatePatch(
    @SerializedName("favor") val favor: Int? = null,
    @SerializedName("trust") val trust: Int? = null,
    @SerializedName("stage") val stage: String? = null
)

data class StoryProgressPatch(
    @SerializedName("current_arc") val currentArc: String? = null,
    @SerializedName("current_chapter") val currentChapter: String? = null,
    @SerializedName("current_scene") val currentScene: String? = null,
    @SerializedName("current_goal") val currentGoal: String? = null
)

data class StoryStateUpdate(
    @SerializedName("current_location") val currentLocation: String? = null,
    @SerializedName("time_of_day") val timeOfDay: String? = null,
    @SerializedName("weather") val weather: String? = null,
    @SerializedName("mood") val mood: String? = null,
    @SerializedName("scene_description") val sceneDescription: String? = null,
    @SerializedName("active_characters") val activeCharacters: List<String>? = null,
    @SerializedName("pending_events") val pendingEvents: List<String>? = null
)

data class PhysicalStatePatch(
    @SerializedName("stamina") val stamina: Int? = null,
    @SerializedName("health") val health: String? = null,
    @SerializedName("injuries") val injuries: List<String>? = null,
    @SerializedName("fatigue") val fatigue: String? = null,
    @SerializedName("hunger") val hunger: String? = null,
    @SerializedName("pain") val pain: String? = null
)

data class MentalStatePatch(
    @SerializedName("mood") val mood: String? = null,
    @SerializedName("stress") val stress: String? = null,
    @SerializedName("focus") val focus: String? = null,
    @SerializedName("current_thoughts") val currentThoughts: String? = null
)

data class CharacterStatePatch(
    @SerializedName("emotion") val emotion: String? = null,
    @SerializedName("physical_state") val physicalState: PhysicalStatePatch? = null,
    @SerializedName("mental_state") val mentalState: MentalStatePatch? = null,
    @SerializedName("attributes") val attributes: Map<String, String> = emptyMap(),
    @SerializedName("relationship_to_player") val relationshipToPlayer: RelationshipPatch? = null,
    @SerializedName("inventory_changes") val inventoryChanges: List<InventoryChange> = emptyList()
)

data class RelationshipPatch(
    @SerializedName("favor_delta") val favorDelta: Int = 0,
    @SerializedName("trust_delta") val trustDelta: Int = 0,
    @SerializedName("stage") val stage: String? = null
)

data class InventoryChange(
    @SerializedName("action") val action: String = "",
    @SerializedName("item") val item: String = "",
    @SerializedName("quantity") val quantity: Int = 1
)

data class NewCharacter(
    @SerializedName("name") val name: String = "",
    @SerializedName("age") val age: Int = 0,
    @SerializedName("gender") val gender: String = "",
    @SerializedName("race") val race: String = "",
    @SerializedName("occupation") val occupation: String = "",
    @SerializedName("personality") val personality: String = "",
    @SerializedName("background") val background: String = "",
    @SerializedName("appearance") val appearance: String = "",
    @SerializedName("relationship_to_player") val relationshipToPlayer: String = "",
    @SerializedName("first_appearance") val firstAppearance: String = "",
    @SerializedName("attributes") val attributes: Map<String, String> = emptyMap(),
    @SerializedName("initial_status") val initialStatus: CharacterCard? = null
)

data class RetiredCharacter(
    @SerializedName("name") val name: String = "",
    @SerializedName("reason") val reason: String = "",
    @SerializedName("retired_at") val retiredAt: String = ""
)

data class CharacterUpdateRequest(
    @SerializedName("name") val name: String = "",
    @SerializedName("reason") val reason: String = ""
)

data class WorldbookUpdate(
    @SerializedName("action") val action: String = "",
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("keywords") val keywords: List<String>? = null,
    @SerializedName("changes") val changes: Map<String, Any>? = null
)

data class ItemTransfer(
    @SerializedName("item") val item: String = "",
    @SerializedName("quantity") val quantity: Int = 1,
    @SerializedName("from") val from: String = "",
    @SerializedName("to") val to: String = "",
    @SerializedName("reason") val reason: String = ""
)

data class EventRecord(
    @SerializedName("type") val type: String = "",
    @SerializedName("target") val target: String = "",
    @SerializedName("caused_by") val causedBy: String = "",
    @SerializedName("time") val time: String = "",
    @SerializedName("description") val description: String = ""
)
