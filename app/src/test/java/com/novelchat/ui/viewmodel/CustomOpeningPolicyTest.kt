package com.novelchat.ui.viewmodel

import com.novelchat.data.model.StoryState
import org.junit.Assert.assertEquals
import org.junit.Test

class CustomOpeningPolicyTest {

    @Test
    fun apply_replacesDefaultPendingEventsWithRequestedOpening() {
        val original = StoryState(
            mood = "普通早晨",
            pendingEvents = listOf("交房租", "参加会议")
        )

        val updated = CustomOpeningPolicy.apply(
            original,
            "  下班回家时，\n门口放着一封没有署名的信。  "
        )

        assertEquals("故事即将从玩家指定的情境展开", updated.mood)
        assertEquals(listOf("下班回家时， 门口放着一封没有署名的信。"), updated.pendingEvents)
    }

    @Test
    fun apply_keepsStateWhenOpeningIsBlank() {
        val original = StoryState(pendingEvents = listOf("既有事件"))

        assertEquals(original, CustomOpeningPolicy.apply(original, "  \n  "))
    }
}
