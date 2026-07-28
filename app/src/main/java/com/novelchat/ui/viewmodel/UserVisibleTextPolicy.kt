package com.novelchat.ui.viewmodel

object UserVisibleTextPolicy {
    const val MAX_ERROR_DETAIL_LENGTH = 260

    fun errorDetail(rawMessage: String?, fallback: String = "未知错误"): String {
        return compact(rawMessage, MAX_ERROR_DETAIL_LENGTH, fallback)
    }

    fun failure(prefix: String, rawMessage: String?): String {
        return "${prefix.trim().trimEnd('：', ':')}：${errorDetail(rawMessage)}"
    }

    fun compact(rawText: String?, maxLength: Int, fallback: String): String {
        require(maxLength >= 4) { "文本上限不能小于 4" }
        val sanitized = sanitizeSecretsAndMarkup(rawText.orEmpty())
            .replace(whitespacePattern, " ")
            .trim()
            .ifBlank { fallback }
        return limit(sanitized, maxLength)
    }

    fun multiline(rawText: String?, maxLength: Int, fallback: String): String {
        require(maxLength >= 4) { "文本上限不能小于 4" }
        val sanitized = sanitizeSecretsAndMarkup(rawText.orEmpty())
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .filter { char -> char == '\n' || char == '\t' || !char.isISOControl() }
            .lineSequence()
            .map { line -> line.replace(horizontalWhitespacePattern, " ").trimEnd() }
            .joinToString("\n")
            .replace(excessiveBlankLinesPattern, "\n\n")
            .trim()
            .ifBlank { fallback }
        return limit(sanitized, maxLength)
    }

    private fun sanitizeSecretsAndMarkup(value: String): String {
        return value
            .replace(markupPattern, " ")
            .replace(bearerPattern, "Bearer ***")
            .replace(apiKeyPattern, "***")
    }

    private fun limit(value: String, maxLength: Int): String {
        if (value.length <= maxLength) return value
        return value.take(maxLength - 3).trimEnd() + "..."
    }

    private val markupPattern = Regex("<[^>]+>")
    private val bearerPattern = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]{8,}")
    private val apiKeyPattern = Regex("(?i)sk-[A-Za-z0-9_-]{8,}")
    private val whitespacePattern = Regex("\\s+")
    private val horizontalWhitespacePattern = Regex("[ \\t]+")
    private val excessiveBlankLinesPattern = Regex("\\n{3,}")
}
