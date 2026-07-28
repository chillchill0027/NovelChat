package com.novelchat.ui.viewmodel

object CharacterConfirmPolicy {
    const val MAX_MODIFICATION_LENGTH = 800
    const val MAX_CONFIRMATION_LENGTH = 6_000

    fun normalizeModificationDraft(input: String): String = input.take(MAX_MODIFICATION_LENGTH)

    fun confirmationText(response: String?, fallback: String): String {
        return UserVisibleTextPolicy.multiline(
            rawText = response,
            maxLength = MAX_CONFIRMATION_LENGTH,
            fallback = fallback
        )
    }

    fun failureMessage(errorMessage: String?): String {
        return "处理失败：${UserVisibleTextPolicy.errorDetail(errorMessage)}"
    }

    fun wantsToStart(input: String): Boolean {
        val normalized = input.trim()
        if (normalized.isBlank()) return false

        val negativeHints = listOf("不开始", "不要开始", "先不开始", "还不想开始", "别开始", "暂不开始", "不要")
        if (negativeHints.any { normalized.contains(it) }) return false

        val compact = normalized
            .replace(" ", "")
            .replace("。", "")
            .replace("！", "")
            .replace("!", "")
            .replace(".", "")
        val explicitStart = setOf("开始", "开始剧情", "开始游戏", "确认开始", "没问题开始", "可以开始", "进入剧情")
        return compact in explicitStart ||
            compact.startsWith("开始剧情") ||
            compact.startsWith("开始游戏") ||
            compact == "ok" ||
            compact.equals("go", ignoreCase = true)
    }
}
