package com.novelchat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ChatInputPolicyTest {

    @Test
    fun validate_rejectsActionUntilStoryIsReady() {
        val result = ChatInputPolicy.validate("去楼下咖啡厅", isReady = false)

        assertNull(result.action)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_rejectsOversizedAction() {
        val result = ChatInputPolicy.validate(
            "a".repeat(ChatInputPolicy.MAX_ACTION_LENGTH + 1),
            isReady = true
        )

        assertNull(result.action)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun validate_trimsUsableAction() {
        val result = ChatInputPolicy.validate("  去彩票店兑奖  ", isReady = true)

        assertEquals("去彩票店兑奖", result.action)
        assertNull(result.errorMessage)
    }

    @Test
    fun validate_rejectsNewTurnWhileStateSyncIsPending() {
        val result = ChatInputPolicy.validate(
            content = "继续填写兑奖登记表",
            isReady = true,
            pendingSyncRound = 7
        )

        assertNull(result.action)
        assertEquals("第7轮状态仍待补同步，请先完成同步再继续剧情。", result.errorMessage)
    }

    @Test
    fun normalizeDraft_capsUiInput() {
        val normalized = ChatInputPolicy.normalizeDraft(
            "x".repeat(ChatInputPolicy.MAX_ACTION_LENGTH + 20)
        )

        assertEquals(ChatInputPolicy.MAX_ACTION_LENGTH, normalized.length)
    }
}
