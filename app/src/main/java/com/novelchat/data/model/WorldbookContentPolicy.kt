package com.novelchat.data.model

object WorldbookContentPolicy {
    const val MAX_NAME_LENGTH = 80
    const val MAX_DESCRIPTION_LENGTH = 6_000
    const val MAX_KEYWORD_LENGTH = 60
    const val MAX_KEYWORDS = 24
    const val MAX_STATUS_LENGTH = 80
    const val MAX_RELATED_REFERENCES = 50
    const val MAX_REFERENCE_LENGTH = 80
    const val MAX_STATUS_HISTORY = 100
    const val MAX_HISTORY_EVENT_LENGTH = 500

    val supportedTypes = setOf("location", "item", "organization", "event", "character")

    fun normalize(entry: WorldbookEntry): WorldbookEntry {
        return entry.copy(
            type = normalizeType(entry.type),
            name = normalizeName(entry.name),
            description = normalizeDescription(entry.description),
            keywords = normalizeKeywords(entry.keywords),
            relatedCharacters = normalizeReferences(entry.relatedCharacters),
            relatedEvents = normalizeReferences(entry.relatedEvents),
            relatedLocations = normalizeReferences(entry.relatedLocations),
            status = normalizeStatus(entry.status),
            statusHistory = entry.statusHistory
                .map(::normalizeHistory)
                .distinctBy { history ->
                    listOf(history.time, history.status, history.event, history.causedBy)
                }
                .takeLast(MAX_STATUS_HISTORY)
        )
    }

    fun normalizeType(rawType: String?, fallback: String = "event"): String {
        val normalized = rawType?.trim()?.lowercase()
        return normalized?.takeIf { it in supportedTypes }
            ?: fallback.takeIf { it in supportedTypes }
            ?: "event"
    }

    fun normalizeName(value: String): String = value.trim().take(MAX_NAME_LENGTH)

    fun normalizeDescription(value: String): String = value.trim().take(MAX_DESCRIPTION_LENGTH)

    fun normalizeKeywords(values: List<String>): List<String> {
        return values
            .map { value -> value.trim().take(MAX_KEYWORD_LENGTH) }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_KEYWORDS)
    }

    fun normalizeStatus(value: String): String {
        return value.trim().take(MAX_STATUS_LENGTH).ifBlank { "正常" }
    }

    private fun normalizeReferences(values: List<String>): List<String> {
        return values
            .map { value -> value.trim().take(MAX_REFERENCE_LENGTH) }
            .filter(String::isNotBlank)
            .distinct()
            .take(MAX_RELATED_REFERENCES)
    }

    private fun normalizeHistory(history: StatusHistory): StatusHistory {
        return history.copy(
            time = history.time.trim().take(MAX_STATUS_LENGTH),
            status = history.status.trim().take(MAX_STATUS_LENGTH),
            event = history.event.trim().take(MAX_HISTORY_EVENT_LENGTH),
            causedBy = history.causedBy.trim().take(MAX_REFERENCE_LENGTH)
        )
    }
}
