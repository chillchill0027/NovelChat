package com.novelchat.data.model

import com.google.gson.annotations.SerializedName

// ========== 玩家/用户状态 ==========

data class PlayerState(
    @SerializedName("name") val name: String = "",
    @SerializedName("race") val race: String = "",
    @SerializedName("age") val age: Int = 25,
    @SerializedName("gender") val gender: String = "男",
    @SerializedName("class") val class_: String = "",
    @SerializedName("appearance") val appearance: String = "",
    @SerializedName("personality") val personality: String = "",
    @SerializedName("background") val background: String = "",
    @SerializedName("lore") val lore: CharacterLore = CharacterLore(),

    // 通用叙事属性
    @SerializedName("charm") val charm: Int = 25,
    @SerializedName("hunting_skill") val huntingSkill: Int = 10,
    @SerializedName("social_rank") val socialRank: String = "E",
    @SerializedName("luck") val luck: Int = 50,
    @SerializedName("alpha") val alpha: Int = 15,
    @SerializedName("resistance") val resistance: Int = 20,
    @SerializedName("cv") val cv: Int = 0,

    // 通用状态（保留兼容）
    @SerializedName("level") val level: Int = 1,
    @SerializedName("skills") val skills: List<PlayerSkill> = emptyList(),
    @SerializedName("physical_state") val physicalState: PhysicalState = PhysicalState(),
    @SerializedName("mental_state") val mentalState: MentalState = MentalState(),
    @SerializedName("custom_attributes") val customAttributes: Map<String, String> = emptyMap(),
    @SerializedName("relationships") val relationships: Map<String, Relationship> = emptyMap(),
    @SerializedName("inventory") val inventory: List<InventoryItem> = emptyList(),
    @SerializedName("quest_log") val questLog: List<Quest> = emptyList(),
    @SerializedName("story_progress") val storyProgress: StoryProgress = StoryProgress()
)

// ========== 通用子数据类 ==========

data class PhysicalState(
    @SerializedName("stamina") val stamina: Int = 100,
    @SerializedName("health") val health: String = "良好",
    @SerializedName("injuries") val injuries: List<String> = emptyList(),
    @SerializedName("fatigue") val fatigue: String = "无",
    @SerializedName("hunger") val hunger: String = "不饿",
    @SerializedName("pain") val pain: String = "无"
)

data class MentalState(
    @SerializedName("mood") val mood: String = "",
    @SerializedName("stress") val stress: String = "低",
    @SerializedName("focus") val focus: String = "",
    @SerializedName("current_thoughts") val currentThoughts: String = ""
)

data class Relationship(
    @SerializedName("favor") val favor: Int = 0,
    @SerializedName("trust") val trust: Int = 0,
    @SerializedName("stage") val stage: String = "陌生人"
)

data class InventoryItem(
    @SerializedName("name") val name: String = "",
    @SerializedName("quantity") val quantity: Int = 1,
    @SerializedName("type") val type: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("origin") val origin: String = "",
    @SerializedName("acquired_at") val acquiredAt: String = ""
)

data class PlayerSkill(
    @SerializedName("name") val name: String = "",
    @SerializedName("level") val level: Int = 1
)

data class Quest(
    @SerializedName("name") val name: String = "",
    @SerializedName("status") val status: String = "",
    @SerializedName("description") val description: String = "",
    @SerializedName("current_step") val currentStep: String = ""
)

data class StoryProgress(
    @SerializedName("current_arc") val currentArc: String = "",
    @SerializedName("current_chapter") val currentChapter: String = "",
    @SerializedName("current_scene") val currentScene: String = "",
    @SerializedName("current_goal") val currentGoal: String = ""
)
