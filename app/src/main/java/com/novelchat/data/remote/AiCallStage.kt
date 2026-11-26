package com.novelchat.data.remote

enum class AiCallStage(val storageKey: String) {
    STORY_IMPORT("story_import"),
    CHARACTER_SETUP("character_setup"),
    NARRATIVE("narrative"),
    NARRATIVE_REPAIR("narrative_repair"),
    TASK_ASSIGNMENT("task_assignment"),
    CHARACTER_STATE("character_state"),
    STATE_KEEPER("state_keeper"),
    SUMMARIZER("summarizer");

    companion object {
        fun fromStorageKey(value: String): AiCallStage? =
            entries.firstOrNull { it.storageKey == value }
    }
}

data class AiRoundRouteLocality(
    val narrative: Boolean,
    val taskAssignment: Boolean,
    val characterState: Boolean
)
