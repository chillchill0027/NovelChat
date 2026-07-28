package com.novelchat.domain.engine

import com.novelchat.data.model.PlayerState

internal object PlayerPromptSnapshotPolicy {

    fun create(player: PlayerState): Map<String, Any> {
        return linkedMapOf(
            "name" to player.name,
            "age" to player.age,
            "gender" to player.gender,
            "race" to player.race,
            "occupation" to player.class_,
            "personality" to player.personality,
            "background" to player.background,
            "appearance" to player.appearance,
            "lore" to LorePromptSnapshotPolicy.character(player.lore),
            "capabilities" to linkedMapOf(
                "charm" to player.charm,
                "action_skill" to player.huntingSkill,
                "social_rank" to player.socialRank,
                "luck" to player.luck,
                "willpower" to player.alpha,
                "resilience" to player.resistance
            ),
            "current_risk" to player.cv,
            "skills" to player.skills,
            "physical_state" to player.physicalState,
            "mental_state" to player.mentalState,
            "relationships" to player.relationships,
            "inventory" to player.inventory,
            "quest_log" to player.questLog,
            "story_progress" to player.storyProgress
        )
    }
}
