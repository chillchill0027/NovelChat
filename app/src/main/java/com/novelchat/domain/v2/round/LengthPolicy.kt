package com.novelchat.domain.v2.round

import com.novelchat.data.model.LengthPreference
import com.novelchat.domain.engine.NarrativeQualityPolicy

data class LengthHint(
    val minChars: Int,
    val maxChars: Int,
    val maxTokens: Int
)

enum class InputLengthKind {
    CONTINUATION,
    NORMAL,
    COMPOUND
}

object LengthPolicy {

    fun classify(rawInput: String, isOpening: Boolean = false): InputLengthKind {
        val trimmed = rawInput.trim()
        if (isOpening) return InputLengthKind.NORMAL
        if (NarrativeQualityPolicy.isContinuationRequest(trimmed) || trimmed.length <= 6) {
            return InputLengthKind.CONTINUATION
        }
        if (NarrativeQualityPolicy.estimateActionCount(trimmed) >= 3) {
            return InputLengthKind.COMPOUND
        }
        return InputLengthKind.NORMAL
    }

    fun hint(
        rawInput: String,
        pref: LengthPreference,
        isOpening: Boolean = false
    ): LengthHint {
        val kind = classify(rawInput, isOpening)
        val (minChars, maxChars) = when (kind) {
            InputLengthKind.CONTINUATION -> when (pref) {
                LengthPreference.SHORT -> 200 to 400
                LengthPreference.STANDARD -> 400 to 700
                LengthPreference.DETAILED -> 700 to 1100
            }
            InputLengthKind.NORMAL -> when (pref) {
                LengthPreference.SHORT -> 300 to 600
                LengthPreference.STANDARD -> 600 to 1000
                LengthPreference.DETAILED -> 900 to 1400
            }
            InputLengthKind.COMPOUND -> when (pref) {
                LengthPreference.SHORT -> 500 to 800
                LengthPreference.STANDARD -> 800 to 1300
                LengthPreference.DETAILED -> 1100 to 1600
            }
        }
        return LengthHint(
            minChars = minChars,
            maxChars = maxChars,
            maxTokens = maxChars * 2 + 300
        )
    }
}
