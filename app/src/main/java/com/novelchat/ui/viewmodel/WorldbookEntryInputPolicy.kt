package com.novelchat.ui.viewmodel

import com.novelchat.data.model.WorldbookEntry
import com.novelchat.data.model.WorldbookContentPolicy
import java.util.UUID

data class NormalizedWorldbookEntryInput(
    val type: String,
    val name: String,
    val description: String,
    val keywords: List<String>,
    val status: String
)

object WorldbookEntryInputPolicy {
    const val MAX_NAME_LENGTH = WorldbookContentPolicy.MAX_NAME_LENGTH
    const val MAX_DESCRIPTION_LENGTH = WorldbookContentPolicy.MAX_DESCRIPTION_LENGTH
    const val MAX_KEYWORD_LENGTH = WorldbookContentPolicy.MAX_KEYWORD_LENGTH
    const val MAX_KEYWORDS = WorldbookContentPolicy.MAX_KEYWORDS
    const val MAX_KEYWORDS_TEXT_LENGTH = 1_600
    const val MAX_STATUS_LENGTH = WorldbookContentPolicy.MAX_STATUS_LENGTH

    val supportedTypes = WorldbookContentPolicy.supportedTypes

    fun validationError(
        type: String,
        name: String,
        description: String,
        keywords: List<String>,
        status: String
    ): String? {
        if (type !in supportedTypes) return "世界书类型无效"
        if (name.isBlank()) return "世界书名称不能为空"
        if (name.trim().length > MAX_NAME_LENGTH) return "名称不能超过 $MAX_NAME_LENGTH 个字符"
        if (description.trim().length > MAX_DESCRIPTION_LENGTH) {
            return "描述不能超过 $MAX_DESCRIPTION_LENGTH 个字符"
        }
        val meaningfulKeywords = keywords.map(String::trim).filter(String::isNotBlank)
        if (meaningfulKeywords.size > MAX_KEYWORDS) return "关键词不能超过 $MAX_KEYWORDS 个"
        if (meaningfulKeywords.any { it.length > MAX_KEYWORD_LENGTH }) {
            return "单个关键词不能超过 $MAX_KEYWORD_LENGTH 个字符"
        }
        if (status.trim().length > MAX_STATUS_LENGTH) return "状态不能超过 $MAX_STATUS_LENGTH 个字符"
        return null
    }

    fun normalize(
        type: String,
        name: String,
        description: String,
        keywords: List<String>,
        status: String
    ): NormalizedWorldbookEntryInput {
        return NormalizedWorldbookEntryInput(
            type = type,
            name = WorldbookContentPolicy.normalizeName(name),
            description = WorldbookContentPolicy.normalizeDescription(description),
            keywords = WorldbookContentPolicy.normalizeKeywords(keywords),
            status = WorldbookContentPolicy.normalizeStatus(status)
        )
    }

    fun duplicateNameError(
        existingId: String?,
        candidateName: String,
        entries: List<WorldbookEntry>
    ): String? {
        val duplicate = entries.any { entry ->
            entry.id != existingId && entry.name.trim().equals(candidateName.trim(), ignoreCase = true)
        }
        return if (duplicate) "已经存在同名世界书条目，请使用不同名称" else null
    }

    fun newEntryId(uuid: UUID = UUID.randomUUID()): String = "wb_$uuid"

    fun normalizeNameDraft(value: String): String = value.take(MAX_NAME_LENGTH)

    fun normalizeDescriptionDraft(value: String): String = value.take(MAX_DESCRIPTION_LENGTH)

    fun normalizeKeywordsDraft(value: String): String = value.take(MAX_KEYWORDS_TEXT_LENGTH)

    fun normalizeStatusDraft(value: String): String = value.take(MAX_STATUS_LENGTH)
}
