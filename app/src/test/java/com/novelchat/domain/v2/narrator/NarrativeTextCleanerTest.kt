package com.novelchat.domain.v2.narrator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NarrativeTextCleanerTest {

    @Test
    fun clean_removesFencesHeadingsBulletsAndMetaChatter() {
        val raw = """
            好的，以下是第 3 轮的剧情：
            ```markdown
            ## 傍晚的楼道
            - 林克推开铁门，走廊灯刚好闪了一下。

            陈姐端着茶杯靠在门框边，抬眼看过来。


            （本轮完）
            ```
        """.trimIndent()

        val cleaned = NarrativeTextCleaner.clean(raw)

        assertFalse(cleaned.contains("```"))
        assertFalse(cleaned.contains("##"))
        assertFalse(cleaned.contains("好的"))
        assertFalse(cleaned.contains("（本轮完）"))
        assertFalse(cleaned.contains("- 林克"))
        assertTrue(cleaned.startsWith("傍晚的楼道"))
        assertTrue(cleaned.contains("林克推开铁门，走廊灯刚好闪了一下。"))
        assertFalse(cleaned.contains("\n\n\n"))
    }

    @Test
    fun clean_extractsNarrativeFieldWhenModelWrapsOutputInJson() {
        val raw = """```json
            {"narrative":"雨点敲在窗玻璃上，顾宁把设计图折好放进包里。","summary":"顾宁收拾好图纸"}
            ```""".trimIndent()

        val cleaned = NarrativeTextCleaner.clean(raw)
        assertEquals("雨点敲在窗玻璃上，顾宁把设计图折好放进包里。", cleaned)
    }

    @Test
    fun clean_trimsDanglingSentenceOnLengthFinishReason() {
        val raw = "林克走出地铁站，顺着湿漉漉的人行道往小区门口走去。风把路灯下的积水吹出细碎的涟漪，他刚摸出手机准备看时间，突然听见身后有人叫他的"
        val cleaned = NarrativeTextCleaner.clean(raw, finishReason = "length")

        assertEquals("林克走出地铁站，顺着湿漉漉的人行道往小区门口走去。", cleaned)
    }
}
