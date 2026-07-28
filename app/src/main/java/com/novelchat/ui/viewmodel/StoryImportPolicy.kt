package com.novelchat.ui.viewmodel

object StoryImportPolicy {
    const val MAX_STORY_TEXT_LENGTH = 12_000

    fun normalizeDraft(value: String): String = value.take(MAX_STORY_TEXT_LENGTH)

    fun validationError(value: String): String? {
        if (value.isBlank()) return "故事设定不能为空"
        if (value.length > MAX_STORY_TEXT_LENGTH) {
            return "故事设定不能超过 $MAX_STORY_TEXT_LENGTH 个字符"
        }
        return null
    }
}
