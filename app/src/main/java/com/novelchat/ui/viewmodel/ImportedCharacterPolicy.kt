package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.data.model.Relationship
import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.domain.engine.TaskAssignmentRosterPolicy

internal object ImportedCharacterPolicy {

    fun createCard(npc: ParsedNpc, playerName: String, createdAt: String): CharacterCard {
        val relationship = npc.relationshipToPlayer.trim()
        val card = CharacterCard(
            id = npc.name,
            name = npc.name,
            status = "active",
            age = npc.age,
            gender = npc.gender,
            race = npc.race,
            occupation = npc.occupation,
            personality = npc.personality,
            background = npc.background,
            appearance = npc.appearance,
            lore = npc.lore,
            physicalState = npc.physicalState,
            mentalState = npc.mentalState,
            inventory = npc.inventory,
            emotion = npc.mentalState.mood.ifBlank { "平静" },
            relationshipStatus = TaskAssignmentRosterPolicy.relationshipStatusFor(relationship),
            relationships = if (relationship.isNotBlank() && playerName.isNotBlank()) {
                mapOf(playerName to Relationship(stage = relationship))
            } else {
                emptyMap()
            },
            createdBy = "user",
            createdAt = createdAt,
            lastModified = createdAt
        )
        return CharacterNamingPolicy.normalizeNpcCard(card)
    }
}
