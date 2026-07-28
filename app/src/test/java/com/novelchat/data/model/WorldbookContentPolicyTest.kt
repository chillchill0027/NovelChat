package com.novelchat.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class WorldbookContentPolicyTest {

    @Test
    fun normalize_boundsModelGeneratedContent() {
        val normalized = WorldbookContentPolicy.normalize(
            WorldbookEntry(
                id = "wb_1",
                type = "event",
                name = "名".repeat(100),
                description = "描".repeat(7_000),
                keywords = List(30) { "关键词$it" + "长".repeat(70) },
                status = "状".repeat(100)
            )
        )

        assertEquals(80, normalized.name.length)
        assertEquals(6_000, normalized.description.length)
        assertEquals(24, normalized.keywords.size)
        assertEquals(60, normalized.keywords.first().length)
        assertEquals(80, normalized.status.length)
    }

    @Test
    fun normalizeType_usesConsistentEventFallback() {
        assertEquals("location", WorldbookContentPolicy.normalizeType(" LOCATION "))
        assertEquals("event", WorldbookContentPolicy.normalizeType("unknown"))
        assertEquals("character", WorldbookContentPolicy.normalizeType(null, fallback = "character"))
    }

    @Test
    fun normalize_keepsOnlyRecentBoundedHistory() {
        val normalized = WorldbookContentPolicy.normalize(
            WorldbookEntry(
                id = "wb_1",
                type = "location",
                name = "彩票店",
                relatedCharacters = List(60) { "角色$it" },
                statusHistory = List(120) { index ->
                    StatusHistory(
                        time = "第${index}轮",
                        status = "核验中",
                        event = "事".repeat(600) + index,
                        causedBy = "人".repeat(100)
                    )
                }
            )
        )

        assertEquals(50, normalized.relatedCharacters.size)
        assertEquals(100, normalized.statusHistory.size)
        assertEquals("第20轮", normalized.statusHistory.first().time)
        assertEquals(500, normalized.statusHistory.first().event.length)
        assertEquals(80, normalized.statusHistory.first().causedBy.length)
    }
}
