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

        assertEquals("神雕侠侣 · 终南烟雨", result.worldSetting.name)
        assertTrue(result.worldSetting.lore.rules.any { it.contains("三度引流") })
        assertEquals("江湖命线", result.worldSetting.characterFields.first { it.key == "story_mode" }.label)
        assertEquals("主线引流次数", result.worldSetting.characterFields.first { it.key == "destiny_nudges" }.label)
        assertEquals("杨过", result.player.name)
        assertEquals("正史主线", result.player.customAttributes["story_mode"])
        assertEquals("0", result.player.customAttributes["destiny_nudges"])
        assertEquals(24, result.npcs.size)
        assertEquals(42, result.worldbook.size)
        assertEquals(listOf("赵志敬", "鹿清笃", "甄志丙"), result.opening.activeCharacters)
        val xiaoLongNv = result.npcs.first { it.name == "小龙女" }
        assertTrue(xiaoLongNv.lore.sampleDialogues.isNotEmpty())
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
            AdvancedStoryImportPolicy.template.replaceFirst(
                "\"name\": \"杨过\"",
                "\"name\": \"主角\""
            ),
            "具体姓名"
        )
        assertFailureContains(
            AdvancedStoryImportPolicy.template.replace(
                "\"active_characters\": [\n      \"赵志敬\"",
                "\"active_characters\": [\n      \"无名侠客\""
            ),
            "未在 npcs"
        )
    }

    @Test
    fun parse_rejectsInvalidFieldModeAndUndefinedCustomAttribute() {
        assertFailureContains(
            AdvancedStoryImportPolicy.template.replaceFirst(
                "\"update_mode\": \"dynamic\"",
                "\"update_mode\": \"sometimes\""
            ),
            "update_mode"
        )
        assertFailureContains(
            AdvancedStoryImportPolicy.template.replaceFirst(
                "\"story_mode\": \"正史主线\"",
                "\"unknown_score\": \"正史主线\""
            ),
            "没有对应"
        )
    }

    @Test
    fun summary_reportsImportScale() {
        val result = AdvancedStoryImportPolicy.parse(AdvancedStoryImportPolicy.template)

        val summary = AdvancedStoryImportPolicy.summary(result)

        assertTrue(summary.contains("24 位 NPC"))
        assertTrue(summary.contains("42 条世界书"))
        assertTrue(summary.contains("世界规则"))
    }

    @Test
    fun shendiaoExample_matchesBundledTemplate() {
        val example = generateSequence(File(System.getProperty("user.dir").orEmpty())) { it.parentFile }
            .map { root -> File(root, "docs/advanced_story_import_shendiao_v1.json") }
            .first(File::isFile)

        assertEquals(example.readText().trim(), AdvancedStoryImportPolicy.template.trim())
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
