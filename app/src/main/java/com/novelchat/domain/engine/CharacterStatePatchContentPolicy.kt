package com.novelchat.domain.engine

import com.novelchat.data.model.CharacterStatePatch

internal object CharacterStatePatchContentPolicy {
    private const val MAX_SHORT_LENGTH = 120
    private const val MAX_PROFILE_LENGTH = 2_000
    private const val MAX_INJURY_LENGTH = 1_000
    private const val MAX_INJURIES = 20
    private const val MAX_INVENTORY_CHANGES = 50

    fun normalize(patch: CharacterStatePatch): CharacterStatePatch {
        return patch.copy(
            emotion = patch.emotion?.let(::short),
            physicalState = patch.physicalState?.let { state ->
                state.copy(
                    health = state.health?.let(::short),
                    injuries = state.injuries
                        ?.take(MAX_INJURIES)
                        ?.map { injury -> injury.trim().take(MAX_INJURY_LENGTH) }
                        ?.filter(String::isNotBlank),
                    fatigue = state.fatigue?.let(::short),
                    hunger = state.hunger?.let(::short),
                    pain = state.pain?.let(::short)
                )
            },
            mentalState = patch.mentalState?.let { state ->
                state.copy(
                    mood = state.mood?.let { it.trim().take(MAX_PROFILE_LENGTH) },
                    stress = state.stress?.let(::short),
                    focus = state.focus?.let { it.trim().take(MAX_PROFILE_LENGTH) },
                    currentThoughts = state.currentThoughts?.let { it.trim().take(MAX_PROFILE_LENGTH) }
                )
            },
            relationshipToPlayer = patch.relationshipToPlayer?.let { relationship ->
                relationship.copy(
                    favorDelta = relationship.favorDelta.coerceIn(-100, 100),
                    trustDelta = relationship.trustDelta.coerceIn(-100, 100),
                    stage = relationship.stage?.let(::short)
                )
            },
            inventoryChanges = patch.inventoryChanges
                .take(MAX_INVENTORY_CHANGES)
                .map { change ->
                    change.copy(
                        action = change.action.trim().lowercase().take(16),
                        item = short(change.item),
                        quantity = change.quantity.coerceIn(1, 9999)
                    )
                }
                .filter { change ->
                    change.action in setOf("add", "remove") && change.item.isNotBlank()
                }
        )
    }

    private fun short(value: String): String = value.trim().take(MAX_SHORT_LENGTH)
}
