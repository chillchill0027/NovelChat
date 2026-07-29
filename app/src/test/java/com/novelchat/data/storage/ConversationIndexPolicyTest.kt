package com.novelchat.data.storage

import com.novelchat.data.model.ConversationEntry
import com.novelchat.data.model.NarrativeEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationIndexPolicyTest {

    @Test
    fun sanitize_removesInvalidIdsAndRepairsVisibleFields() {
        val sanitized = ConversationIndexPolicy.sanitize(
            listOf(
                ConversationEntry(id = 0, characterName = "broken"),
                ConversationEntry(id = -1, characterName = "negative"),
                ConversationEntry(id = 7, characterName = " ", messageCount = -2),
                ConversationEntry(id = 7, characterName = "duplicate")
            )
        )

        assertEquals(1, sanitized.size)
        assertEquals("未命名故事", sanitized.single().characterName)
        assertEquals(0, sanitized.single().messageCount)
    }

    @Test
    fun sort_ordersByLastActivity() {
        val old = entry(id = 1, last = "2026-07-08T08:00:00")
        val recent = entry(id = 2, last = "2026-07-08T09:00:00")
        val fallback = entry(id = 3, created = "2026-07-08T10:00:00", last = "")

        val result = ConversationIndexPolicy.sort(listOf(old, fallback, recent))

        assertEquals(listOf(3L, 2L, 1L), result.map { it.id })
    }

    @Test
    fun updateAfterRound_movesConversationToTopAndUsesNarrativePreview() {
        val target = entry(id = 1, last = "2026-07-08T08:00:00", lastMessage = "旧消息")
        val other = entry(id = 2, last = "2026-07-08T09:00:00", lastMessage = "另一条")
        val narrative = NarrativeEntry(
            turn = 3,
            userInput = "开始",
            narrative = "林越的消息弹了出来，会议提前到了上午九点半。主角必须在通勤和准备材料之间做取舍。"
        )

        val result = ConversationIndexPolicy.updateAfterRound(
            entries = listOf(other, target),
            characterId = 1,
            latestNarrative = narrative,
            round = 3,
            now = "2026-07-08T10:00:00"
        )

        assertEquals(1L, result.first().id)
        assertEquals(3, result.first().messageCount)
        assertEquals("2026-07-08T10:00:00", result.first().lastMessageAt)
        assertEquals("林越的消息弹了出来，会议提前到了上午九点半。主角必须在通勤和准备材料之间做取舍。", result.first().lastMessage)
    }

    @Test
    fun preview_fallsBackToUserInputWhenNarrativeBlank() {
        val narrative = NarrativeEntry(turn = 1, userInput = "我先去地铁站", narrative = "   ")

        assertEquals("我先去地铁站", ConversationIndexPolicy.preview(narrative))
    }

    @Test
    fun updateAfterRollbackToOpening_clearsFuturePreview() {
        val original = ConversationEntry(
            id = 7,
            messageCount = 3,
            lastMessage = "未来分支内容"
        )

        val updated = ConversationIndexPolicy.updateAfterRound(
            entries = listOf(original),
            characterId = 7,
            latestNarrative = null,
            round = 0,
            now = "2026-07-29T10:00:00"
        ).single()

        assertEquals(0, updated.messageCount)
        assertEquals("", updated.lastMessage)
    }

    private fun entry(
        id: Long,
        created: String = "2026-07-08T07:00:00",
        last: String,
        lastMessage: String = ""
    ) = ConversationEntry(
        id = id,
        characterName = "故事$id",
        createdAt = created,
        lastMessageAt = last,
        lastMessage = lastMessage
    )
}
