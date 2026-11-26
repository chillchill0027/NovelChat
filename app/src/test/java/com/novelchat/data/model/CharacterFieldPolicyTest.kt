package com.novelchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CharacterFieldPolicyTest {

    @Test
    fun emptyConfiguration_keepsLegacyStoriesOnCompleteDefaults() {
        val definitions = CharacterFieldPolicy.effectiveDefinitions(emptyList())

        assertNotNull(definitions.firstOrNull { it.key == "personality" })
        assertNotNull(definitions.firstOrNull { it.key == "background" })
        assertNotNull(definitions.firstOrNull { it.key == "charm" })
        assertNotNull(definitions.firstOrNull { it.key == "stamina" })
    }

    @Test
    fun configuredBuiltIn_overridesLabelRangeAndDisplayContract() {
        val configured = listOf(
            CharacterFieldDefinition(
                key = "stamina",
                label = "魔法值",
                description = "施法消耗的能量",
                group = CharacterFieldDefinition.GROUP_STATUS,
                valueType = CharacterFieldDefinition.TYPE_NUMBER,
                minValue = 0,
                maxValue = 200,
                defaultValue = "160",
                unit = "MP",
                display = CharacterFieldDefinition.DISPLAY_PRIMARY,
                primaryFor = CharacterFieldDefinition.TARGET_PLAYER,
                updateMode = CharacterFieldDefinition.UPDATE_DYNAMIC
            )
        )

        val field = CharacterFieldPolicy.definitionFor(configured, "stamina")!!

        assertEquals("魔法值", field.label)
        assertEquals(200, field.maxValue)
        assertEquals("200", CharacterFieldPolicy.sanitizeValue(field, "999"))
        assertEquals("160/200MP", CharacterFieldPolicy.formatValue(field, "160", compact = false))
    }

    @Test
    fun customAttributes_useDefinitionsDefaultsAndTypeRules() {
        val configured = listOf(
            CharacterFieldDefinition(
                key = "arcane_affinity",
                label = "奥术亲和",
                valueType = CharacterFieldDefinition.TYPE_NUMBER,
                minValue = 0,
                maxValue = 10,
                defaultValue = "2",
                appliesTo = CharacterFieldDefinition.TARGET_ALL
            ),
            CharacterFieldDefinition(
                key = "known_spells",
                label = "已知法术",
                valueType = CharacterFieldDefinition.TYPE_LIST,
                defaultValue = "照明术"
            )
        )

        val normalized = CharacterFieldPolicy.normalizeImportedAttributes(
            values = mapOf("arcane_affinity" to "18", "known_spells" to "照明术, 护盾术, 照明术"),
            configured = configured,
            target = CharacterFieldDefinition.TARGET_PLAYER
        )

        assertEquals("10", normalized["arcane_affinity"])
        assertEquals("照明术、护盾术", normalized["known_spells"])
        assertTrue(CharacterFieldPolicy.usesCustomStorage("arcane_affinity", CharacterFieldDefinition.TARGET_PLAYER))
    }

    @Test
    fun formatValue_avoidsAbsurdDenominatorsForAgeLevelAndPercentage() {
        val ageField = CharacterFieldPolicy.definitionFor(emptyList(), "age")!!
        val levelField = CharacterFieldPolicy.definitionFor(emptyList(), "level")!!
        val alertField = CharacterFieldDefinition(
            key = "alertness",
            label = "警觉度",
            valueType = CharacterFieldDefinition.TYPE_NUMBER,
            minValue = 0,
            maxValue = 100,
            unit = "%"
        )

        assertEquals("29岁", CharacterFieldPolicy.formatValue(ageField, "29", compact = false))
        assertEquals("29岁", CharacterFieldPolicy.formatValue(ageField, "29", compact = true))
        assertEquals("2", CharacterFieldPolicy.formatValue(levelField, "2", compact = false))
        assertEquals("55%", CharacterFieldPolicy.formatValue(alertField, "55", compact = false))
        assertEquals("55%", CharacterFieldPolicy.formatValue(alertField, "55", compact = true))
    }
}
