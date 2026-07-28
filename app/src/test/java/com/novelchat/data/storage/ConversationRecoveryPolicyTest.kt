package com.novelchat.data.storage

import com.novelchat.data.model.NarrativeEntry
import com.novelchat.data.model.ConversationEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationRecoveryPolicyTest {

    @Test
    fun create_rebuildsUsefulLobbyMetadataFromStoryFiles() {
        val recovered = ConversationRecoveryPolicy.create(
            ConversationRecoverySnapshot(
                id = 42,
                playerName = "林克",
                worldName = "城市生活模拟器",
                worldDescription = "海临市的现实生活故事",
                currentRound = 3,
                narratives = listOf(
                    NarrativeEntry(turn = 2, narrative = "林克买到咖啡。"),
                    NarrativeEntry(
                        turn = 3,
                        narrative = "老周确认林克中了一亿元。",
                        createdAt = "2026-07-18T10:20:00"
                    )
                ),
                fallbackTimestamp = "2026-07-18T09:00:00"
            )
        )

        assertEquals("林克", recovered.characterName)
        assertEquals("海临市的现实生活故事", recovered.characterDesc)
        assertEquals(3, recovered.messageCount)
        assertEquals("老周确认林克中了一亿元。", recovered.lastMessage)
        assertEquals("2026-07-18T10:20:00", recovered.lastMessageAt)
        assertEquals("recovered", recovered.type)
    }

    @Test
    fun create_usesWorldAndFallbackTimestampWhenPlayerAndNarrativeAreMissing() {
        val recovered = ConversationRecoveryPolicy.create(
            ConversationRecoverySnapshot(
                id = 43,
                playerName = "",
                worldName = "灰烬大陆",
                worldDescription = "",
                currentRound = -2,
                narratives = emptyList(),
                fallbackTimestamp = "2026-07-18T09:00:00"
            )
        )

        assertEquals("灰烬大陆", recovered.characterName)
        assertEquals("从本地存档恢复", recovered.characterDesc)
        assertEquals(0, recovered.messageCount)
        assertEquals("2026-07-18T09:00:00", recovered.lastMessageAt)
    }

    @Test
    fun merge_refreshesProvenRuntimeMetadataAndPreservesIndexDescription() {
        val existing = ConversationEntry(
            id = 44,
            characterName = "旧名字",
            characterDesc = "用户选择的自定义开局",
            type = "preset",
            presetKey = "renter",
            createdAt = "2026-07-18T08:00:00",
            lastMessageAt = "2026-07-18T09:00:00",
            messageCount = 1,
            lastMessage = "旧预览"
        )
        val recovered = ConversationEntry(
            id = 44,
            characterName = "林克",
            characterDesc = "世界设定描述",
            type = "recovered",
            createdAt = "2026-07-18T08:30:00",
            lastMessageAt = "2026-07-18T10:00:00",
            messageCount = 3,
            lastMessage = "最新剧情预览"
        )

        val merged = ConversationRecoveryPolicy.merge(existing, recovered)

        assertEquals("林克", merged.characterName)
        assertEquals("用户选择的自定义开局", merged.characterDesc)
        assertEquals("preset", merged.type)
        assertEquals("renter", merged.presetKey)
        assertEquals("2026-07-18T08:00:00", merged.createdAt)
        assertEquals("2026-07-18T10:00:00", merged.lastMessageAt)
        assertEquals(3, merged.messageCount)
        assertEquals("最新剧情预览", merged.lastMessage)
    }

    @Test
    fun merge_surfacesRecoveredCorruptionWarning() {
        val existing = ConversationEntry(
            id = 45,
            characterName = "林克",
            characterDesc = "城市租客"
        )
        val recovered = existing.copy(
            characterDesc = ConversationRecoveryPolicy.CORRUPT_DATA_DESCRIPTION,
            type = "recovered"
        )

        val merged = ConversationRecoveryPolicy.merge(existing, recovered)

        assertEquals(ConversationRecoveryPolicy.CORRUPT_DATA_DESCRIPTION, merged.characterDesc)
    }

    @Test
    fun merge_clearsCorruptionWarningAfterFilesRecover() {
        val existing = ConversationEntry(
            id = 46,
            characterName = "林克",
            characterDesc = ConversationRecoveryPolicy.CORRUPT_DATA_DESCRIPTION
        )
        val recovered = existing.copy(
            characterDesc = "海临市的现实生活故事",
            type = "recovered"
        )

        val merged = ConversationRecoveryPolicy.merge(existing, recovered)

        assertEquals("海临市的现实生活故事", merged.characterDesc)
    }
}
