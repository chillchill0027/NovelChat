package com.novelchat.domain.v2.state

import com.google.gson.annotations.SerializedName

data class StateDelta(
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("scene") val scene: SceneDelta? = null,
    @SerializedName("goals") val goals: GoalsDelta? = null,
    @SerializedName("player") val player: PlayerDelta? = null,
    @SerializedName("npcs") val npcs: List<NpcDelta>? = null,
    @SerializedName("new_npcs") val newNpcs: List<NewNpcDelta>? = null,
    @SerializedName("departed") val departed: List<DepartedNpc>? = null,
    @SerializedName("transfers") val transfers: List<ItemTransferDelta>? = null,
    @SerializedName("world") val world: List<WorldDelta>? = null,
    @SerializedName("events") val events: List<EventDelta>? = null,
    @SerializedName("suggestions") val suggestions: List<String>? = null
)

data class SceneDelta(
    @SerializedName("location") val location: String? = null,
    @SerializedName("time_of_day") val timeOfDay: String? = null,
    @SerializedName("weather") val weather: String? = null,
    @SerializedName("present") val present: List<String>? = null,
    @SerializedName("description") val description: String? = null
)

data class GoalsDelta(
    @SerializedName("complete") val complete: List<String>? = null,
    @SerializedName("add") val add: List<String>? = null,
    @SerializedName("update") val update: List<GoalUpdate>? = null,
    @SerializedName("current") val current: String? = null
)

data class GoalUpdate(
    @SerializedName("from") val from: String = "",
    @SerializedName("to") val to: String = ""
)

data class PlayerDelta(
    @SerializedName("fields") val fields: Map<String, String>? = null,
    @SerializedName("inventory") val inventory: List<InventoryOp>? = null,
    @SerializedName("relationships") val relationships: List<RelationshipOp>? = null
)

data class InventoryOp(
    @SerializedName("op") val op: String = "",
    @SerializedName("item") val item: String = "",
    @SerializedName("qty") val qty: Int = 1
)

data class RelationshipOp(
    @SerializedName("name") val name: String = "",
    @SerializedName("stage") val stage: String = ""
)

data class NpcDelta(
    @SerializedName("name") val name: String = "",
    @SerializedName("emotion") val emotion: String? = null,
    @SerializedName("favor_delta") val favorDelta: Int? = null,
    @SerializedName("trust_delta") val trustDelta: Int? = null,
    @SerializedName("relationship_stage") val relationshipStage: String? = null,
    @SerializedName("fields") val fields: Map<String, String>? = null,
    @SerializedName("memory") val memory: String? = null,
    @SerializedName("inventory") val inventory: List<InventoryOp>? = null
)

data class NewNpcDelta(
    @SerializedName("name") val name: String = "",
    @SerializedName("identity") val identity: String = "",
    @SerializedName("relationship_to_player") val relationshipToPlayer: String = "",
    @SerializedName("gender") val gender: String = "",
    @SerializedName("age") val age: Int? = null,
    @SerializedName("personality") val personality: String = "",
    @SerializedName("appearance") val appearance: String = "",
    @SerializedName("speech_style") val speechStyle: String = "",
    @SerializedName("fields") val fields: Map<String, String>? = null
)

data class DepartedNpc(
    @SerializedName("name") val name: String = "",
    @SerializedName("permanent") val permanent: Boolean = false
)

data class ItemTransferDelta(
    @SerializedName("item") val item: String = "",
    @SerializedName("qty") val qty: Int = 1,
    @SerializedName("from") val from: String = "",
    @SerializedName("to") val to: String = ""
)

data class WorldDelta(
    @SerializedName("name") val name: String = "",
    @SerializedName("type") val type: String = "location",
    @SerializedName("description") val description: String = "",
    @SerializedName("keywords") val keywords: List<String>? = null
)

data class EventDelta(
    @SerializedName("title") val title: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("related") val related: List<String>? = null
)
