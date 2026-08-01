package com.novelchat.ui.viewmodel

import com.novelchat.data.model.CharacterFieldDefinition
import com.novelchat.data.model.PhysicalState
import com.novelchat.data.model.PlayerState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterFieldDisplayPolicyTest {

    @Test
    fun playerCard_usesStoryLabelsAndOnlyConfiguredPrimaryFields() {
        val fields = listOf(
            CharacterFieldDefinition(
                key = "stamina",
                label = "魔法值",
                valueType = CharacterFieldDefinition.TYPE_NUMBER,
                minValue = 0,
                maxValue = 200,
                unit = "MP",
                display = CharacterFieldDefinition.DISPLAY_PRIMARY,
                primaryFor = CharacterFieldDefinition.TARGET_PLAYER,
                updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
            ),
            CharacterFieldDefinition(
                key = "arcane_affinity",
                label = "奥术亲和",
                group = CharacterFieldDefinition.GROUP_ABILITY,
                valueType = CharacterFieldDefinition.TYPE_NUMBER,
                minValue = 0,
                maxValue = 10,
                appliesTo = CharacterFieldDefinition.TARGET_PLAYER,
                display = CharacterFieldDefinition.DISPLAY_PRIMARY,
                primaryFor = CharacterFieldDefinition.TARGET_PLAYER,
                updateMode = CharacterFieldDefinition.UPDATE_EVENT,
                order = 25
            ),
            CharacterFieldDefinition(key = "stress", label = "压力", display = CharacterFieldDefinition.DISPLAY_HIDDEN),
            CharacterFieldDefinition(
                key = "risk",
                label = "风险",
                valueType = CharacterFieldDefinition.TYPE_NUMBER,
                display = CharacterFieldDefinition.DISPLAY_HIDDEN
            )
        )
        val player = PlayerState(
            name = "林克",
            physicalState = PhysicalState(stamina = 155),
            customAttributes = mapOf("arcane_affinity" to "7")
        )

        val primary = CharacterFieldDisplayPolicy.playerPrimary(player, fields)

        assertEquals(listOf("奥术亲和", "魔法值"), primary.map { it.definition.label })
        assertTrue(primary.any { it.value == "155/200MP" })
        assertTrue(primary.any { it.value == "7/10" })
    }
}
