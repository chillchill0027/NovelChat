package com.novelchat.domain.v2.narrator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeGuardTest {

    @Test
    fun evaluate_allowsNormalFigurativeProseWithSimilesAndModifiers() {
        val prose = """
            走廊里有些潮湿，像刚下过一场闷雨。林克推门进去时，柜台后的灯似乎暗了一分，陈姐微微抬起头，把手里那张水电费单据推过桌面。
            “这周三能清吧？”她的声音不高，却把房门外那点风声压了下去。林克摸出手机看了一眼余额，点了点头，空气里飘着淡淡的茉莉茶味。
        """.trimIndent()

        val verdict = NarrativeGuard.evaluate(
            cleanedText = prose,
            previousNarrative = "前一轮林克刚刚起床，站在客厅窗边看外面的云层，想着今天需要先处理房东发来的消息。",
            attempt = 0,
            playerName = "林克"
        )

        assertTrue(verdict is GuardVerdict.Pass)
    }

    @Test
    fun evaluate_stripsMetaLeakWithoutRetrying() {
        val text = "林克在昏暗的楼道里敲了敲木门，屋内很快传来拖鞋踩在地板上的声音。作为AI，请注意第3轮的提示词和JSON输出。门开了一道缝，陈姐探出头来，手里还攥着一串铜钥匙，目光打量着楼道尽头的声控灯，随后才侧身让林克进门把水电账单铺在饭桌上。"

        val verdict = NarrativeGuard.evaluate(
            cleanedText = text,
            previousNarrative = "",
            attempt = 0
        )

        assertTrue(verdict is GuardVerdict.Pass)
        val passed = verdict as GuardVerdict.Pass
        assertTrue(passed.hadMetaLeakStripped)
        assertFalse(passed.cleanedText.contains("作为AI"))
        assertFalse(passed.cleanedText.contains("JSON"))
        assertTrue(passed.cleanedText.contains("门开了一道缝"))
    }

    @Test
    fun evaluate_retriesOnEmptyTooShortOrHighRepetition_andFailsOnSecondEmpty() {
        assertTrue(NarrativeGuard.evaluate("", "", attempt = 0) is GuardVerdict.Retry)
        assertTrue(NarrativeGuard.evaluate("", "", attempt = 1) is GuardVerdict.Fail)
        assertTrue(NarrativeGuard.evaluate("太短了。", "", attempt = 0) is GuardVerdict.Retry)

        val prev = "林克推开咖啡馆的玻璃门，风铃轻响了一声，店里飘着深烘咖啡豆的味道，靠窗的座位上坐着顾宁，她正低头在图纸边缘记笔记，听到动静抬眼望过来，指尖还转着那支自动铅笔，窗外的雨丝顺着伞骨往下滑。"
        val rep = NarrativeGuard.evaluate(prev, prev, attempt = 0)
        assertTrue(rep is GuardVerdict.Retry)
        assertTrue((rep as GuardVerdict.Retry).reason == GuardReason.REPETITION)

        val secondRep = NarrativeGuard.evaluate(prev, prev, attempt = 1)
        assertTrue(secondRep is GuardVerdict.Pass)
    }
}
