package com.novelchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStoryTest {

    @Test
    fun defaultStory_remoteMessageContactsAreNotPhysicallyPresent() {
        assertTrue(defaultModernStoryState().activeCharacters.isEmpty())
    }

    @Test
    fun defaultStory_isModernRealWorldSimulator() {
        assertTrue(DEFAULT_MODERN_STORY_TEXT.contains("现代真实世界模拟器 RPG"))
        assertTrue(DEFAULT_MODERN_STORY_TEXT.contains("海临市"))
        assertTrue(DEFAULT_MODERN_STORY_TEXT.contains("租房"))
        assertEquals("城市生活模拟器", defaultModernWorldSetting.name)
        assertEquals("无魔法、无超能力", defaultModernWorldSetting.magicLevel)
    }

    @Test
    fun quickStartPresets_doNotUseFantasyDefaults() {
        val joined = characterPresets.joinToString("\n") { "${it.key} ${it.name} ${it.description}" }

        assertTrue(joined.contains("城市租客"))
        assertTrue(joined.contains("社区医生"))
        assertFalse(joined.contains("城堡"))
        assertFalse(joined.contains("骑士"))
        assertFalse(joined.contains("魔法"))
    }

    @Test
    fun defaultModernSeed_hasPlayableSceneContext() {
        val createdAt = "2026-07-08T09:00:00"

        assertEquals(3, defaultModernCharacters(createdAt).size)
        assertEquals(4, defaultModernWorldbookEntries(createdAt).size)
        assertEquals("云桥里老小区", defaultModernStoryState().currentLocation)
        assertTrue(defaultModernStoryState().pendingEvents.any { it.contains("招聘顾问孟琪") })
    }

    @Test
    fun defaultModernParseResult_hasStructuredStoryData() {
        val result = defaultModernStoryParseResult()

        assertTrue(isDefaultModernStoryText(DEFAULT_MODERN_STORY_TEXT))
        assertEquals("城市生活模拟器", result.worldSetting.name)
        assertEquals("林克", result.player.name)
        assertTrue(result.npcs.map { it.name }.contains("招聘顾问孟琪"))
        assertTrue(result.worldbook.map { it.name }.contains("周一三条消息"))
        assertEquals("云桥里老小区", result.opening.currentLocation)
        assertTrue(result.opening.pendingEvents.any { it.contains("同事林越") })
    }

    @Test
    fun quickStartPresets_haveCharacterNamesSeparateFromIdentity() {
        val renter = characterPresets.first { it.key == "renter" }

        assertEquals("林克", renter.playerName)
        assertEquals("城市租客", renter.name)
    }
}
