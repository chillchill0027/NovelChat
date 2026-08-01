package com.novelchat.ui.viewmodel

import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedOpening
import com.novelchat.data.remote.model.ParsedPlayer
import com.novelchat.data.remote.model.ParsedWorldSetting
import com.novelchat.data.remote.model.ParsedWorldbookItem
import com.novelchat.data.remote.model.StoryParseResult
import org.junit.Assert.assertEquals
import org.junit.Test

class StoryParseContentPolicyTest {

    @Test
    fun normalize_boundsCollectionsAndLongFields() {
        val normalized = StoryParseContentPolicy.normalize(
            StoryParseResult(
                worldSetting = ParsedWorldSetting(description = "世".repeat(7_000)),
                player = ParsedPlayer(name = "林".repeat(100), age = 999),
                opening = ParsedOpening(
                    currentLocation = "地".repeat(100),
                    mood = "氛".repeat(3_000),
                    pendingEvents = List(50) { index -> "待办$index" }
                ),
                npcs = List(70) { index ->
                    ParsedNpc(name = "角色$index", background = "背".repeat(7_000))
                },
                worldbook = List(130) { index ->
                    ParsedWorldbookItem(
                        type = "unknown",
                        name = "条目$index",
                        description = "描".repeat(7_000)
                    )
                }
            )
        )

        assertEquals(6_000, normalized.worldSetting.description.length)
        assertEquals(80, normalized.player.name.length)
        assertEquals(120, normalized.player.age)
        assertEquals(80, normalized.opening.currentLocation.length)
        assertEquals(2_000, normalized.opening.mood.length)
        assertEquals(30, normalized.opening.pendingEvents.size)
        assertEquals(50, normalized.npcs.size)
        assertEquals(6_000, normalized.npcs.first().background.length)
        assertEquals(100, normalized.worldbook.size)
        assertEquals("event", normalized.worldbook.first().type)
        assertEquals(6_000, normalized.worldbook.first().description.length)
    }

    @Test
    fun normalize_deduplicatesNamesSkillsAndWorldbookEntries() {
        val normalized = StoryParseContentPolicy.normalize(
            StoryParseResult(
                player = ParsedPlayer(skills = listOf("观察", " 观察 ", "交涉")),
                npcs = listOf(ParsedNpc(name = "王常"), ParsedNpc(name = "王常")),
                worldbook = listOf(
                    ParsedWorldbookItem(type = "location", name = "云桥里"),
                    ParsedWorldbookItem(type = "location", name = "云桥里")
                )
            )
        )

        assertEquals(listOf("观察", "交涉"), normalized.player.skills)
        assertEquals(1, normalized.npcs.size)
        assertEquals(1, normalized.worldbook.size)
    }
}
