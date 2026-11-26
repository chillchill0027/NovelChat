package com.novelchat.domain.v2.narrator

import android.util.Log
import com.novelchat.domain.engine.NarrativeAgencyPolicy
import com.novelchat.domain.engine.NarrativeQualityPolicy

enum class GuardReason {
    EMPTY,
    TOO_SHORT,
    REPETITION,
    META_LEAK
}

sealed interface GuardVerdict {
    data class Pass(
        val cleanedText: String,
        val hadMetaLeakStripped: Boolean = false
    ) : GuardVerdict

    data class Retry(
        val reason: GuardReason,
        val cleanedText: String
    ) : GuardVerdict

    data class Fail(
        val reason: GuardReason
    ) : GuardVerdict
}

object NarrativeGuard {

    private val metaLeakSentence = Regex(
        """[^。！？!\?\n]*(?:作为(?:一个)?\s*AI|语言模型|JSON\s*(?:格式|对象|字段)?|提示词|玩家输入|第\s*\d+\s*轮)[^。！？!\?\n]*[。！？!\?]?""",
        RegexOption.IGNORE_CASE
    )
    private val excessiveNewlines = Regex("""\n(?:[ \t]*\n){2,}""")
    private val punctuationOrSpace = Regex("""[\s，,。\.！!？\?；;：:“”"'（）()\[\]【】—\-…]""")

    fun evaluate(
        cleanedText: String,
        previousNarrative: String,
        attempt: Int,
        playerName: String = "",
        rawInput: String = "",
        currentGoal: String = "",
        logSink: (String) -> Unit = { msg -> runCatching { Log.i("NovelChatGuard", msg) } }
    ): GuardVerdict {
        val stripped = stripMetaLeakSentences(cleanedText)
        val hadMeta = stripped != cleanedText.trim()

        if (stripped.isBlank()) {
            return if (attempt == 0) {
                GuardVerdict.Retry(GuardReason.EMPTY, "")
            } else {
                GuardVerdict.Fail(GuardReason.EMPTY)
            }
        }

        val nonSpaceLength = stripped.count { !it.isWhitespace() }
        if (nonSpaceLength < 80) {
            return if (attempt == 0) {
                GuardVerdict.Retry(GuardReason.TOO_SHORT, stripped)
            } else if (nonSpaceLength < 30) {
                GuardVerdict.Fail(GuardReason.TOO_SHORT)
            } else {
                GuardVerdict.Pass(stripped, hadMetaLeakStripped = hadMeta)
            }
        }

        if (previousNarrative.isNotBlank() && isRepetitive(stripped, previousNarrative)) {
            if (attempt == 0) {
                return GuardVerdict.Retry(GuardReason.REPETITION, stripped)
            }
        }

        // 自主权检测在 V2 中只记统计日志，不阻断也不重写
        if (playerName.isNotBlank()) {
            NarrativeAgencyPolicy.issue(
                narrative = stripped,
                playerName = playerName,
                continuationRequest = NarrativeQualityPolicy.isContinuationRequest(rawInput),
                authorizationContext = currentGoal
            )?.let { agencyNote ->
                logSink("agency_notice: $agencyNote")
            }
        }

        return GuardVerdict.Pass(stripped, hadMetaLeakStripped = hadMeta)
    }

    fun stripMetaLeakSentences(text: String): String {
        val replaced = metaLeakSentence.replace(text, "")
        return replaced
            .lines()
            .joinToString("\n") { it.trim() }
            .replace(excessiveNewlines, "\n\n")
            .trim()
    }

    internal fun isRepetitive(current: String, previous: String): Boolean {
        val a = normalizeForComparison(current)
        val b = normalizeForComparison(previous)
        if (a.length < 40 || b.length < 40) return false

        if (longestCommonSubstringLength(a, b) > 200) return true

        if (a.length >= 8 && b.length >= 8) {
            val prevShingles = b.windowed(8).toHashSet()
            val curShingles = a.windowed(8)
            val overlap = curShingles.count { it in prevShingles }
            if (overlap.toDouble() / curShingles.size.toDouble() > 0.40) {
                return true
            }
        }
        return false
    }

    private fun normalizeForComparison(text: String): String =
        punctuationOrSpace.replace(text, "").lowercase()

    private fun longestCommonSubstringLength(left: String, right: String): Int {
        // Limit DP work on huge texts by capping comparison slices safely; 200-char overlap on recent turns is well within window
        val a = if (left.length > 3_000) left.take(3_000) else left
        val b = if (right.length > 3_000) right.takeLast(3_000) else right
        val prev = IntArray(b.length + 1)
        val curr = IntArray(b.length + 1)
        var maxLen = 0
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                if (a[i - 1] == b[j - 1]) {
                    val v = prev[j - 1] + 1
                    curr[j] = v
                    if (v > maxLen) maxLen = v
                } else {
                    curr[j] = 0
                }
            }
            curr.copyInto(prev)
        }
        return maxLen
    }
}
