package com.novelchat.ui.viewmodel

import com.novelchat.data.remote.ApiEndpointPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SetupFlowChineseImeTest {

    @Test
    fun normalizeApiUrlDraft_convertsFullWidthChinesePunctuationToAscii() {
        val raw = "http：／／localhost：1234／v1"
        val normalized = SetupFlowPolicy.normalizeApiUrlDraft(raw)
        assertEquals("http://localhost:1234/v1", normalized)
        assertNull(SetupFlowPolicy.validationError(normalized, "", "deepseek-v4-flash"))
        assertEquals("http://localhost:1234", ApiEndpointPolicy.normalizeBaseUrl(raw))
    }

    @Test
    fun normalizeApiUrlDraft_convertsFullWidthDotAndIdeographicPeriod() {
        val raw = "https：／／api。deepseek．com／v1"
        assertEquals("https://api.deepseek.com/v1", SetupFlowPolicy.normalizeApiUrlDraft(raw))
    }
}
