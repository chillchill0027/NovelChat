package com.novelchat.domain.worldbook

import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.StoryState
import com.novelchat.data.model.WorldbookEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class WorldbookMatcherTest {

    private val matcher = WorldbookMatcher()

    @Test
    fun match_ordersSceneThenRecentThenUserInput() {
        val tavern = entry("1", "location", "龙焰酒馆", listOf("酒馆"))
        val mage = entry("2", "character", "灰袍法师", listOf("法师"))
        val sword = entry("3", "item", "银剑", listOf("银剑"))

        val result = matcher.match(
            userMessage = "我拔出银剑。",
            storyState = StoryState(currentLocation = "龙焰酒馆"),
            allEntries = listOf(sword, mage, tavern),
            recentNarratives = listOf(NarrativeEntry(turn = 1, userInput = "", narrative = "灰袍法师低声提醒。")),
            maxEntries = 10
        )

        assertEquals(listOf("龙焰酒馆", "灰袍法师", "银剑"), result.map { it.name })
    }

    @Test
    fun match_respectsMaxEntries() {
        val entries = (1..4).map { index ->
            entry("$index", "item", "物品$index", listOf("关键词$index"))
        }

        val result = matcher.match(
            userMessage = "关键词1 关键词2 关键词3 关键词4",
            storyState = StoryState(),
            allEntries = entries,
            recentNarratives = emptyList(),
            maxEntries = 2
        )

        assertEquals(2, result.size)
    }

    @Test
    fun match_usesPendingEventsAsDecisionContext() {
        val rentEvent = entry("rent", "event", "周一三条消息", listOf("房租", "会议", "面试"))

        val result = matcher.match(
            userMessage = "开始游戏",
            storyState = StoryState(pendingEvents = listOf("本周交房租", "回复高薪面试邀请")),
            allEntries = listOf(rentEvent),
            recentNarratives = emptyList()
        )

        assertEquals(listOf("周一三条消息"), result.map { it.name })
    }

    private fun entry(id: String, type: String, name: String, keywords: List<String>) =
        WorldbookEntry(id = id, type = type, name = name, keywords = keywords, description = name)
}
