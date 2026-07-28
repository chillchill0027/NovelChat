package com.novelchat.ui.viewmodel

data class ChatInputValidation(
    val action: String? = null,
    val errorMessage: String? = null
)

object ChatInputPolicy {
    const val MAX_ACTION_LENGTH = 2_000

    fun normalizeDraft(value: String): String = value.take(MAX_ACTION_LENGTH)

    fun validate(
        content: String,
        isReady: Boolean,
        pendingSyncRound: Int? = null
    ): ChatInputValidation {
        val action = content.trim()
        if (action.isBlank()) return ChatInputValidation()
        if (!isReady) {
            return ChatInputValidation(errorMessage = "故事尚未加载完成，请稍候再发送。")
        }
        if (pendingSyncRound != null) {
            return ChatInputValidation(
                errorMessage = "第${pendingSyncRound}轮状态仍待补同步，请先完成同步再继续剧情。"
            )
        }
        if (action.length > MAX_ACTION_LENGTH) {
            return ChatInputValidation(
                errorMessage = "单次行动不能超过 $MAX_ACTION_LENGTH 个字符，请精简后重试。"
            )
        }
        return ChatInputValidation(action = action)
    }
}
