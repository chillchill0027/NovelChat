package com.novelchat.domain.v2.narrator

import com.google.gson.JsonParser
import com.novelchat.data.model.CharacterNamingPolicy
import com.novelchat.domain.parser.JsonResponseParser

object NarrativeTextCleaner {

    private val jsonParser = JsonResponseParser()

    private val codeFenceLine = Regex("""^\s*```[a-zA-Z0-9_-]*\s*$""")
    private val markdownHeading = Regex("""^#{1,6}\s+""")
    private val bulletPrefix = Regex("""^(?:[-*•])\s+""")
    private val leadingChatter = Regex(
        """^(?:(?:好的|明白|当然)[，,。！!\s]*)+(?:(?:以下是|下面为您|为您续写)[^：\n]{0,30}[：:])?$|^(?:以下是|下面为您|为您续写)[^：\n]{0,30}[：:]$"""
    )
    private val trailingChatter = Regex(
        """^(?:---+|===+|（本轮完）|\(本轮完\)|（待续）|\(待续\)|【本轮结束】)$"""
    )
    private val excessiveNewlines = Regex("""\n(?:[ \t]*\n){2,}""")
    private val sentenceEndingChars = charArrayOf('。', '！', '？', '…', '」', '”', '.', '!', '?')

    fun clean(raw: String, finishReason: String? = null): String {
        var text = raw.replace("\r\n", "\n").trim()
        if (text.isBlank()) return ""

        val initialLines = text.split('\n').toMutableList()
        while (initialLines.isNotEmpty()) {
            val head = initialLines.first().trim()
            if (head.isBlank() || leadingChatter.matches(head) || codeFenceLine.matches(head)) {
                initialLines.removeAt(0)
            } else {
                break
            }
        }
        while (initialLines.isNotEmpty()) {
            val tail = initialLines.last().trim()
            if (tail.isBlank() || trailingChatter.matches(tail) || codeFenceLine.matches(tail)) {
                initialLines.removeAt(initialLines.lastIndex)
            } else {
                break
            }
        }
        text = initialLines.joinToString("\n").trim()
        if (text.isBlank()) return ""

        // 若模型习惯性以 JSON 包裹输出，提取其中的 narrative 字段
        extractNarrativeFromJsonIfWrapped(text)?.let { extracted ->
            text = extracted.trim()
        }

        val lines = text.split('\n').map { line ->
            line.trimEnd()
                .replaceFirst(markdownHeading, "")
                .replaceFirst(bulletPrefix, "")
        }.toMutableList()

        while (lines.isNotEmpty() && (lines.first().isBlank() || leadingChatter.matches(lines.first().trim()))) {
            lines.removeAt(0)
        }
        while (lines.isNotEmpty() && (lines.last().isBlank() || trailingChatter.matches(lines.last().trim()))) {
            lines.removeAt(lines.lastIndex)
        }

        var normalized = lines.joinToString("\n")
            .replace(excessiveNewlines, "\n\n")
            .trim()

        if (finishReason == "length") {
            normalized = trimToLastCompleteSentence(normalized)
        }

        return CharacterNamingPolicy.normalizeNarrativeText(normalized).trim()
    }

    private fun extractNarrativeFromJsonIfWrapped(text: String): String? {
        if (!text.startsWith("{") && !text.contains("\"narrative\"")) return null
        val json = jsonParser.extractJson(text) ?: return null
        return runCatching {
            val obj = JsonParser.parseString(json).asJsonObject
            val field = obj.get("narrative") ?: return null
            if (field.isJsonPrimitive && field.asJsonPrimitive.isString) {
                field.asString.takeIf { it.isNotBlank() }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun trimToLastCompleteSentence(text: String): String {
        if (text.length < 40) return text
        val lastEnd = text.lastIndexOfAny(sentenceEndingChars)
        if (lastEnd < 20) return text
        // 若末尾本身已经是完整句末标点（如 。” 或 。），直接保留
        return text.substring(0, lastEnd + 1).trimEnd()
    }
}
