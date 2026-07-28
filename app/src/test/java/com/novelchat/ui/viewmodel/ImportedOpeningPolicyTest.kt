package com.novelchat.ui.viewmodel

import com.novelchat.data.remote.model.ParsedNpc
import com.novelchat.data.remote.model.ParsedOpening
import com.novelchat.data.remote.model.ParsedWorldbookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportedOpeningPolicyTest {

    @Test
    fun normalize_preservesExplicitOpeningAndCreatesStoryState() {
        val normalized = ImportedOpeningPolicy.normalize(
            opening = ParsedOpening(
                currentLocation = "云桥里老小区",
                timeOfDay = "周一早晨",
                weather = "多云",
                mood = "三条消息同时抵达",
                pendingEvents = listOf("回复同事林越的临时顶会请求")
            ),
            worldbook = emptyList()
        )

        val state = ImportedOpeningPolicy.createStoryState(normalized)

        assertEquals("云桥里老小区", state.currentLocation)
        assertEquals("周一早晨", state.timeOfDay)
        assertEquals(listOf("回复同事林越的临时顶会请求"), state.pendingEvents)
        assertTrue(state.activeCharacters.isEmpty())
    }

    @Test
    fun normalize_recoversPlayableOpeningFromWorldbook() {
        val normalized = ImportedOpeningPolicy.normalize(
            opening = ParsedOpening(),
            worldbook = listOf(
                ParsedWorldbookItem(type = "location", name = "黑钟塔"),
                ParsedWorldbookItem(
                    type = "event",
                    name = "钟声将至",
                    description = "伙伴银灯警告林克在下一次钟响前离开塔顶"
                )
            )
        )

        assertEquals("黑钟塔", normalized.currentLocation)
        assertEquals(
            listOf("伙伴银灯警告林克在下一次钟响前离开塔顶"),
            normalized.pendingEvents
        )
        assertTrue(normalized.timeOfDay.isNotBlank())
        assertTrue(normalized.mood.isNotBlank())
    }

    @Test
    fun normalize_replacesImportedIdentityLabelsWithStableCharacterNames() {
        val normalized = ImportedOpeningPolicy.normalize(
            opening = ParsedOpening(
                currentLocation = "邻居A家门外",
                mood = "邻居A和咖啡店店员都在等回复",
                pendingEvents = listOf(
                    "先回应邻居A的敲门，再请咖啡店店员确认订单",
                    "女朋友仍在家中等消息"
                )
            ),
            worldbook = emptyList(),
            npcs = listOf(
                ParsedNpc(name = "邻居A", occupation = "邻居"),
                ParsedNpc(name = "咖啡店店员", occupation = "咖啡店店员"),
                ParsedNpc(name = "女朋友", occupation = "插画师", relationshipToPlayer = "女朋友")
            )
        )

        assertEquals("王常家门外", normalized.currentLocation)
        assertTrue(normalized.mood.contains("王常"))
        assertTrue(normalized.mood.contains("阿远"))
        assertTrue(normalized.pendingEvents[0].contains("王常"))
        assertTrue(normalized.pendingEvents[0].contains("咖啡店店员阿远"))
        assertTrue(normalized.pendingEvents[1].contains("女朋友苏晚"))
        assertTrue(normalized.pendingEvents.none { it.contains("邻居A") })
    }
}
