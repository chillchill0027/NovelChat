package com.novelchat.ui.viewmodel

object ChatHistoryPositionPolicy {
    fun initialFocusIndex(messages: List<ChatMessageItem>): Int? {
        val latestTurn = messages.maxOfOrNull { it.turn } ?: return null
        val latestUserAction = messages.indexOfFirst { message ->
            message.turn == latestTurn && message.role == "user"
        }
        if (latestUserAction >= 0) return latestUserAction

        return messages.indexOfFirst { it.turn == latestTurn }
            .takeIf { it >= 0 }
    }
}
