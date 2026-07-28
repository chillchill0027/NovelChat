package com.novelchat.domain.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class NarrativeStreamPreviewPolicyTest {

    @Test
    fun extract_decodesJsonEscapesAndUnicode() {
        val raw = """{"narrative":"\u6797\u514b\n\n走进彩票店，店员说：\"请稍等\"。","summary":""}"""

        assertEquals(
            "林克\n\n走进彩票店，店员说：\"请稍等\"。",
            NarrativeStreamPreviewPolicy.extract(raw)
        )
    }

    @Test
    fun extract_waitsForIncompleteUnicodeEscape() {
        val partial = """{"narrative":"林克走到门口\u970"""

        assertEquals("林克走到门口", NarrativeStreamPreviewPolicy.extract(partial))
    }

    @Test
    fun extract_returnsPartialTextBeforeClosingQuoteArrives() {
        val partial = """{"narrative":"林克递出彩票"""

        assertEquals("林克递出彩票", NarrativeStreamPreviewPolicy.extract(partial))
    }
}
