package com.novelchat.data.model

import com.google.gson.Gson

object NarrativeResponseRecovery {

    private val gson = Gson()
    private val stringLiteral = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")

    fun recover(raw: String): NarrativeResult? {
        val narrativeKey = raw.indexOf("\"narrative\"")
        if (narrativeKey < 0) return null
        val narrativeColon = raw.indexOf(':', narrativeKey)
        if (narrativeColon < 0) return null

        val summaryKey = raw.indexOf("\"summary\"", narrativeColon + 1)
        val coverageKey = raw.indexOf("\"action_coverage\"", narrativeColon + 1)
        val narrativeEnd = listOf(summaryKey, coverageKey, raw.length)
            .filter { it >= 0 }
            .minOrNull() ?: raw.length
        val paragraphs = decodeStringLiterals(raw.substring(narrativeColon + 1, narrativeEnd))
            .filter { it.isNotBlank() }
        if (paragraphs.isEmpty()) return null

        val summary = if (summaryKey >= 0) {
            val colon = raw.indexOf(':', summaryKey)
            if (colon >= 0) decodeStringLiterals(raw.substring(colon + 1)).firstOrNull().orEmpty() else ""
        } else {
            ""
        }

        val coverageSection = coverageKey.takeIf { it >= 0 }?.let(raw::substring).orEmpty()
        val actions = extractFieldValues(coverageSection, "action")
        val evidence = extractFieldValues(coverageSection, "evidence")
        val coverage = actions.zip(evidence).map { (action, proof) ->
            ActionCoverage(action = action, evidence = proof)
        }

        return NarrativeResult(
            narrative = paragraphs.joinToString("\n\n"),
            summary = summary,
            actionCoverage = coverage
        )
    }

    fun cleanNarrativeText(raw: String): String {
        if (!raw.trimStart().startsWith("{") || !raw.contains("\"narrative\"")) return raw
        return recover(raw)?.narrative?.takeIf { it.isNotBlank() } ?: raw
    }

    private fun extractFieldValues(raw: String, field: String): List<String> {
        val fieldPattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        return fieldPattern.findAll(raw).mapNotNull { decode(it.groupValues[1]) }.toList()
    }

    private fun decodeStringLiterals(raw: String): List<String> {
        return stringLiteral.findAll(raw).mapNotNull { decode(it.groupValues[1]) }.toList()
    }

    private fun decode(encoded: String): String? {
        return try {
            gson.fromJson("\"$encoded\"", String::class.java)
        } catch (_: Exception) {
            null
        }
    }
}
