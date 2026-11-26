package com.novelchat.domain.v2.context

import com.novelchat.data.model.CreativityLevel
import com.novelchat.data.model.LengthPreference
import com.novelchat.data.remote.llm.LlmMessage
import com.novelchat.domain.v2.round.LengthHint

data class PlayerInput(
    val raw: String,
    val lengthPreference: LengthPreference = LengthPreference.STANDARD,
    val creativity: CreativityLevel = CreativityLevel.BALANCED
)

data class ContextDebugInfo(
    val systemChars: Int,
    val chapterChars: Int,
    val historyChars: Int,
    val historyTurnsIncluded: Int,
    val contextChars: Int,
    val presentNpcs: List<String>,
    val relatedNpcs: List<String>,
    val worldbookNames: List<String>,
    val trimmedItems: List<String>
)

data class PromptContext(
    val systemPrompt: String,
    val history: List<LlmMessage>,
    val turnContext: String,
    val userTurn: String,
    val lengthHint: LengthHint,
    val debug: ContextDebugInfo
) {
    fun toMessages(): List<LlmMessage> = buildList {
        add(LlmMessage(role = "system", content = systemPrompt))
        addAll(history)
        add(LlmMessage(role = "user", content = userTurn))
    }
}
