package com.novelchat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatInputPolicyV2Test {

    @Test
    fun validate_allowsInputWhenPendingSyncBlockingDisabledForV2() {
        val v2Result = ChatInputPolicy.validate(
            content = "  继续推门进去  ",
            isReady = true,
            pendingSyncRound = 3,
            enforcePendingSyncBlock = false
        )
        assertEquals("继续推门进去", v2Result.action)
        assertNull(v2Result.errorMessage)

        val v1Result = ChatInputPolicy.validate(
            content = "  继续推门进去  ",
            isReady = true,
            pendingSyncRound = 3,
            enforcePendingSyncBlock = true
        )
        assertNull(v1Result.action)
        assertEquals("第3轮状态仍待补同步，请先完成同步再继续剧情。", v1Result.errorMessage)
    }
}
