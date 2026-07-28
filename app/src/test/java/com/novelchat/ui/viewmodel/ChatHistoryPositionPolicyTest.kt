package com.novelchat.ui.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatHistoryPositionPolicyTest {

    @Test
    fun initialFocusIndex_startsAtLatestTurnUserAction() {
        val messages = listOf(
            message(turn = 1, role = "user"),
            message(turn = 1, role = "assistant"),
            message(turn = 2, role = "user"),
            message(turn = 2, role = "assistant")
        )

        assertEquals(2, ChatHistoryPositionPolicy.initialFocusIndex(messages))
    }

    @Test
    fun initialFocusIndex_usesHighestTurnEvenWhenInputIsNotSorted() {
        val messages = listOf(
            message(turn = 3, role = "assistant"),
            message(turn = 2, role = "user"),
            message(turn = 3, role = "user")
        )

        assertEquals(2, ChatHistoryPositionPolicy.initialFocusIndex(messages))
    }

    @Test
    fun initialFocusIndex_fallsBackToFirstMessageOfLatestTurn() {
        val messages = listOf(
            message(turn = 1, role = "assistant"),
            message(turn = 2, role = "error"),
            message(turn = 2, role = "assistant")
        )

        assertEquals(1, ChatHistoryPositionPolicy.initialFocusIndex(messages))
    }

    @Test
    fun initialFocusIndex_returnsNullForEmptyHistory() {
        assertNull(ChatHistoryPositionPolicy.initialFocusIndex(emptyList()))
    }

    private fun message(turn: Int, role: String) = ChatMessageItem(
        turn = turn,
        role = role,
        content = "$turn-$role"
    )
}
