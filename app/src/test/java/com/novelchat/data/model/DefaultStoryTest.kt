package com.novelchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultStoryTest {

    @Test
    fun defaultStory_isSoleShenDiaoStory() {
        assertTrue(DEFAULT_STORY_TEXT.contains("神雕侠侣 · 终南烟雨"))
        assertTrue(DEFAULT_STORY_TEXT.contains("杨过"))
        assertTrue(DEFAULT_STORY_TEXT.contains("3 次"))
        assertTrue(DEFAULT_STORY_TEXT.contains("自创江湖"))
        assertTrue(isDefaultStoryText(DEFAULT_STORY_TEXT))
        assertEquals("神雕侠侣 · 终南烟雨", defaultStoryWorldSetting().name)
    }

    @Test
    fun defaultStorySeed_hasFullPlayableSceneContext() {
        val createdAt = "2026-11-27T09:00:00"
        val player = defaultStoryPlayerState()
        val characters = defaultStoryCharacters(createdAt)
        val worldbook = defaultStoryWorldbookEntries(createdAt)
        val storyState = defaultStoryState()

        assertEquals("杨过", player.name)
        assertEquals("正史主线", player.customAttributes["story_mode"])
        assertEquals("0", player.customAttributes["destiny_nudges"])
        assertEquals(24, characters.size)
        assertEquals(42, worldbook.size)
        assertEquals("终南山重阳宫演武场", storyState.currentLocation)
        assertEquals(listOf("赵志敬", "鹿清笃", "甄志丙"), storyState.activeCharacters)
        assertTrue(storyState.pendingEvents.isNotEmpty())
    }

    @Test
    fun defaultStoryParseResult_hasStructuredStoryData() {
        val result = defaultStoryParseResult()

        assertEquals("神雕侠侣 · 终南烟雨", result.worldSetting.name)
        assertEquals("杨过", result.player.name)
        assertTrue(result.npcs.any { it.name == "小龙女" })
        assertTrue(result.npcs.any { it.name == "郭靖" })
        assertTrue(result.worldbook.any { it.name == "活死人墓" })
        assertEquals("终南山重阳宫演武场", result.opening.currentLocation)
    }
}
