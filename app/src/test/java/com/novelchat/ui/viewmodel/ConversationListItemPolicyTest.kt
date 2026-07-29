package com.novelchat.ui.viewmodel

import com.novelchat.data.model.ConversationEntry
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationListItemPolicyTest {

    private val now = LocalDateTime.of(2026, 7, 26, 15, 30)

    @Test
    fun activityLabel_includesRoundAndTodayTime() {
        val entry = ConversationEntry(
            messageCount = 7,
            lastMessageAt = "2026-07-26T15:08:00"
        )

        assertEquals(
            "7轮 · 今天 15:08",
            ConversationListItemPolicy.activityLabel(entry, now)
        )
    }

    @Test
    fun activityLabel_usesYesterdayAndCreationFallback() {
        val entry = ConversationEntry(
            createdAt = "2026-07-25T09:05:00",
            lastMessageAt = ""
        )

        assertEquals(
            "未开始 · 昨天 09:05",
            ConversationListItemPolicy.activityLabel(entry, now)
        )
    }

    @Test
    fun activityLabel_formatsOlderAndInvalidDatesSafely() {
        assertEquals(
            "2轮 · 7月20日",
            ConversationListItemPolicy.activityLabel(
                ConversationEntry(messageCount = 2, lastMessageAt = "2026-07-20T12:00:00"),
                now
            )
        )
        assertEquals(
            "1轮 · 2025年12月31日",
            ConversationListItemPolicy.activityLabel(
                ConversationEntry(messageCount = 1, lastMessageAt = "2025-12-31T23:59:00"),
                now
            )
        )
        assertEquals(
            "未开始",
            ConversationListItemPolicy.activityLabel(
                ConversationEntry(lastMessageAt = "invalid"),
                now
            )
        )
    }

    @Test
    fun contextAndPreview_areCompactAndHaveUsefulFallbacks() {
        val entry = ConversationEntry(
            characterDesc = " 城市租客\n  海临市新生活 ",
            lastMessage = " 林克走进咖啡店，\n 店员抬头看向他。 "
        )

        assertEquals("城市租客 海临市新生活", ConversationListItemPolicy.context(entry))
        assertEquals(
            "林克走进咖啡店， 店员抬头看向他。",
            ConversationListItemPolicy.preview(entry)
        )
        assertEquals(
            "互动故事",
            ConversationListItemPolicy.context(ConversationEntry())
        )
        assertEquals(
            "还没有剧情，点击进入故事",
            ConversationListItemPolicy.preview(ConversationEntry())
        )
    }

    @Test
    fun contextAndPreview_hideInternalMigrationLabels() {
        val entry = ConversationEntry(
            characterDesc = "旧版测试故事，已迁移为通用叙事开局。",
            lastMessage = "旧版测试记录，建议重新开始剧情"
        )

        assertEquals("互动故事", ConversationListItemPolicy.context(entry))
        assertEquals(
            "还没有剧情，点击进入故事",
            ConversationListItemPolicy.preview(entry)
        )
    }
}
