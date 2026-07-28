package com.novelchat.ui.viewmodel

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AdvancedStoryImportPolicyTest {

    @Test
    fun template_parsesAllDetailedContracts() {
        val result = AdvancedStoryImportPolicy.parse(AdvancedStoryImportPolicy.template)

        assertEquals("海临市", result.worldSetting.name)
        assertEquals("2026 年春季", result.worldSetting.lore.era)
        assertTrue(result.worldSetting.lore.rules.contains("时间连续推进"))
        assertEquals("林克", result.player.name)
        assertEquals("先核实再行动", result.player.lore.values[1])
        assertEquals(88, result.player.physicalState.stamina)
        assertEquals("手机", result.player.inventory.single().name)
        assertEquals("顾宁", result.npcs.single().name)
        assertEquals("核心关系角色", result.npcs.single().lore.role)
        assertEquals("云桥里", result.worldbook.first().name)
        assertEquals(listOf("顾宁"), result.opening.activeCharacters)
        assertEquals("true", result.opening.globalFlags["搬家完成"])
    }

    @Test
    fun parse_rejectsUnsupportedVersionAndMalformedShape() {
        assertFailureContains(
            AdvancedStoryImportPolicy.template.replace(
                "\"schema_version\": 1",
                "\"schema_version\": 2"
            ),
            "不支持"
        )
        assertFailureContains("""{"schema_version":1,"world_setting":[],"player":{}}""", "world_setting")
    }

    @Test
    fun parse_rejectsIdentityLabelsAndUnknownOpeningCharacters() {
        assertFailureContains(
            AdvancedStoryImportPolicy.template.replace(
                "\"name\": \"林克\",\n    \"age\"",
                "\"name\": \"主角\",\n    \"age\""
            ),
            "具体姓名"
        )
        assertFailureContains(
            AdvancedStoryImportPolicy.template.replace(
                "\"active_characters\": [\"顾宁\"]",
                "\"active_characters\": [\"邻居A\"]"
            ),
            "未在 npcs"
        )
    }

    @Test
    fun summary_reportsImportScale() {
        val result = AdvancedStoryImportPolicy.parse(AdvancedStoryImportPolicy.template)

        val summary = AdvancedStoryImportPolicy.summary(result)

        assertTrue(summary.contains("1 位 NPC"))
        assertTrue(summary.contains("3 条世界书"))
        assertTrue(summary.contains("4 条世界规则"))
    }

    @Test
    fun documentedExample_isAcceptedByTheProductionImporter() {
        val example = generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
            .map { root -> File(root, "docs/advanced_story_import_v1.example.json") }
            .first(File::isFile)

        val result = AdvancedStoryImportPolicy.parse(example.readText())

        assertEquals("海临市", result.worldSetting.name)
        assertEquals("林克", result.player.name)
        assertEquals("顾宁", result.npcs.single().name)
        assertEquals(3, result.worldbook.size)
    }

    private fun assertFailureContains(raw: String, expected: String) {
        try {
            AdvancedStoryImportPolicy.parse(raw)
            fail("Expected import to fail")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(expected))
        }
    }
}
