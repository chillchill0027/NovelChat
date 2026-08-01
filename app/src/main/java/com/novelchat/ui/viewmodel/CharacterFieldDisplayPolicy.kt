package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterCard
import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.CharacterFieldPolicy
import com.novelchat.data.model.PlayerState

data class CharacterFieldDisplay(
    val definition: CharacterFieldDefinition,
    val rawValue: String,
    val value: String
)

object CharacterFieldDisplayPolicy {

    fun playerPrimary(
        player: PlayerState,
        configured: List<CharacterFieldDefinition>
    ): List<CharacterFieldDisplay> {
        return playerFields(player, configured)
            .filter { CharacterFieldPolicy.isPrimaryFor(it.definition, CharacterFieldDefinition.TARGET_PLAYER) }
            .take(3)
    }

    fun npcPrimary(
        card: CharacterCard,
        playerName: String,
        configured: List<CharacterFieldDefinition>
    ): List<CharacterFieldDisplay> {
        return npcFields(card, playerName, configured)
            .filter { CharacterFieldPolicy.isPrimaryFor(it.definition, CharacterFieldDefinition.TARGET_NPC) }
            .take(2)
    }

    fun playerDetails(
        player: PlayerState,
        configured: List<CharacterFieldDefinition>
    ): List<CharacterFieldDisplay> = playerFields(player, configured)
        .filter { it.definition.display != CharacterFieldDefinition.DISPLAY_HIDDEN }
        .filterNot { it.definition.key == "name" }

    fun npcDetails(
        card: CharacterCard,
        playerName: String,
        configured: List<CharacterFieldDefinition>
    ): List<CharacterFieldDisplay> = npcFields(card, playerName, configured)
        .filter { it.definition.display != CharacterFieldDefinition.DISPLAY_HIDDEN }
        .filterNot { it.definition.key == "name" }

    private fun playerFields(
        player: PlayerState,
        configured: List<CharacterFieldDefinition>
    ): List<CharacterFieldDisplay> {
        return CharacterFieldPolicy.effectiveDefinitions(configured).mapNotNull { field ->
            CharacterFieldPolicy.valueFor(field, player)?.let { raw -> display(field, raw) }
        }
    }

    private fun npcFields(
        card: CharacterCard,
        playerName: String,
        configured: List<CharacterFieldDefinition>
    ): List<CharacterFieldDisplay> {
        return CharacterFieldPolicy.effectiveDefinitions(configured).mapNotNull { field ->
            CharacterFieldPolicy.valueFor(field, card, playerName)?.let { raw -> display(field, raw) }
        }
    }

    private fun display(field: CharacterFieldDefinition, raw: String): CharacterFieldDisplay {
        return CharacterFieldDisplay(
            definition = field,
            rawValue = raw,
            value = CharacterFieldPolicy.formatValue(field, raw, compact = false)
        )
    }
}
