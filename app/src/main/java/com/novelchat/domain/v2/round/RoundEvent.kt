package com.novelchat.domain.v2.round

import com.novelchat.domain.v2.context.ContextDebugInfo

sealed interface RoundEvent {
    data class Loading(val message: String) : RoundEvent
    data class NarrativeDelta(val text: String) : RoundEvent
    data object NarrativeReset : RoundEvent
    data class NarrativeCommitted(
        val turn: Int,
        val narrative: String,
        val debugInfo: ContextDebugInfo
    ) : RoundEvent
    data class Failed(val message: String) : RoundEvent
}
