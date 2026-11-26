package com.novelchat.domain.v2.round

import com.novelchat.data.model.LengthPreference
import org.junit.Assert.assertEquals
import org.junit.Test

class LengthPolicyTest {

    @Test
    fun classify_treatsOpeningShortInputAsNormal() {
        val hint = LengthPolicy.hint("开始这一天", LengthPreference.STANDARD, isOpening = true)
        assertEquals(600, hint.minChars)
        assertEquals(1000, hint.maxChars)
        assertEquals(2300, hint.maxTokens)
    }

    @Test
    fun classify_distinguishesContinuationNormalAndCompoundActions() {
        val cont = LengthPolicy.hint("继续", LengthPreference.STANDARD, isOpening = false)
        assertEquals(400, cont.minChars)
        assertEquals(700, cont.maxChars)

        val normal = LengthPolicy.hint("推开门走进便利店买杯黑咖啡", LengthPreference.SHORT, isOpening = false)
        assertEquals(300, normal.minChars)
        assertEquals(600, normal.maxChars)

        val compound = LengthPolicy.hint(
            "先把门锁好，然后去便利店买水，接着坐地铁去公司，最后把合同交给林越",
            LengthPreference.DETAILED,
            isOpening = false
        )
        assertEquals(1100, compound.minChars)
        assertEquals(1600, compound.maxChars)
        assertEquals(3500, compound.maxTokens)
    }
}
