package com.novelchat.ui.viewmodel

import com.novelchat.data.model.ConversationEntry
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ConversationListItemPolicy {
    private val storedTimestampFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val monthDayFormatter = DateTimeFormatter.ofPattern("M月d日")
    private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    private val whitespace = Regex("""\s+""")

    fun context(entry: ConversationEntry): String {
        return entry.characterDesc
            .takeUnless(::isInternalMigrationText)
            .orEmpty()
            .replace(whitespace, " ")
            .trim()
            .ifBlank { "互动故事" }
    }

    fun preview(entry: ConversationEntry): String {
        return entry.lastMessage
            .takeUnless(::isInternalMigrationText)
            .orEmpty()
            .replace(whitespace, " ")
            .trim()
            .ifBlank { "还没有剧情，点击进入故事" }
    }

    fun activityLabel(
        entry: ConversationEntry,
        now: LocalDateTime = LocalDateTime.now()
    ): String {
        val progress = if (entry.messageCount > 0) "${entry.messageCount}轮" else "未开始"
        val timestamp = entry.lastMessageAt.ifBlank { entry.createdAt }
        val activityTime = parse(timestamp)?.let { formatActivityTime(it, now) }
        return listOfNotNull(progress, activityTime).joinToString(" · ")
    }

    private fun parse(value: String): LocalDateTime? {
        if (value.isBlank()) return null
        return try {
            LocalDateTime.parse(value, storedTimestampFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun formatActivityTime(value: LocalDateTime, now: LocalDateTime): String {
        val date = value.toLocalDate()
        return when {
            date == now.toLocalDate() -> "今天 ${value.format(timeFormatter)}"
            date == now.toLocalDate().minusDays(1) -> "昨天 ${value.format(timeFormatter)}"
            date.year == now.year -> value.format(monthDayFormatter)
            else -> value.format(fullDateFormatter)
        }
    }

    private fun isInternalMigrationText(value: String): Boolean =
        value.contains("旧版测试故事") ||
            value.contains("旧版测试记录") ||
            value.contains("已迁移为通用叙事开局")
}
