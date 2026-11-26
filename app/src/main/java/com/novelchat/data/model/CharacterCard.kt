package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

data class CharacterCard(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("status") val status: String = "active",

    // 叙事状态属性（字段名保留以兼容既有存档）
    @SerializedName("rarity") val rarity: String = "普通",     // 普通/稀有/史诗/传说
    @SerializedName("libido") val libido: Int = 50,           // 行动倾向 0-100
    @SerializedName("threshold") val threshold: Int = 50,      // 防御阈值 0-100
    @SerializedName("emotion") val emotion: String = "平静",   // 情绪状态
    @SerializedName("relationship_status") val relationshipStatus: String = "单身", // 关系状态
    @SerializedName("kink") val kink: String = "",             // 隐藏线索
    @SerializedName("favor") val favor: Int = 0,               // 好感度 -100 ~ 100

    // 基础信息
    @SerializedName("race") val race: String = "",
    @SerializedName("age") val age: Int = 0,
    @SerializedName("gender") val gender: String = "",
    @SerializedName("occupation") val occupation: String = "",
    @SerializedName("appearance") val appearance: String = "",
    @SerializedName("personality") val personality: String = "",
    @SerializedName("background") val background: String = "",
    @SerializedName("lore") val lore: CharacterLore = CharacterLore(),

    // 通用状态（保留与原系统兼容）
    @SerializedName("physical_state") val physicalState: PhysicalState = PhysicalState(),
    @SerializedName("mental_state") val mentalState: MentalState = MentalState(),
    @SerializedName("custom_attributes") val customAttributes: Map<String, String> = emptyMap(),
    @SerializedName("relationships") val relationships: Map<String, Relationship> = emptyMap(),
    @SerializedName("inventory") val inventory: List<InventoryItem> = emptyList(),
    @SerializedName("memories") val memories: List<MemoryNote> = emptyList(),

    @SerializedName("created_by") val createdBy: String = "ai",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("last_modified") val lastModified: String = ""
)

data class MemoryNote(
    @SerializedName("turn") val turn: Int = 0,
    @SerializedName("text") val text: String = "",
    @SerializedName("weight") val weight: Int = 1
)
