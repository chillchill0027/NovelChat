package com.novelchat.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupFlowPolicyTest {

    @Test
    fun canContinue_allowsPrivateLocalEndpointWithoutKey() {
        assertTrue(
            SetupFlowPolicy.canContinue(
                apiUrl = "http://192.168.31.84:1234",
                apiKey = "",
                model = "gemma-4-e4b-uncensored-hauhaucs-aggressive"
            )
        )
    }

    @Test
    fun canContinue_requiresKeyForCloudEndpoint() {
        assertFalse(
            SetupFlowPolicy.canContinue(
                apiUrl = "https://api.deepseek.com",
                apiKey = "",
                model = "deepseek-v4-flash"
            )
        )
        assertTrue(
            SetupFlowPolicy.canContinue(
                apiUrl = "https://api.deepseek.com",
                apiKey = "sk-test",
                model = "deepseek-v4-flash"
            )
        )
    }

    @Test
    fun canContinue_rejectsBlankUrlOrModel() {
        assertFalse(SetupFlowPolicy.canContinue(apiUrl = "", apiKey = "", model = "gemma"))
        assertFalse(SetupFlowPolicy.canContinue(apiUrl = "http://localhost:1234", apiKey = "", model = " "))
    }

    @Test
    fun canContinue_rejectsRemoteHttpAndLanHttpWithKey() {
        assertFalse(
            SetupFlowPolicy.canContinue(
                apiUrl = "http://api.example.com",
                apiKey = "",
                model = "model"
            )
        )
        assertFalse(
            SetupFlowPolicy.canContinue(
                apiUrl = "http://192.168.31.84:1234",
                apiKey = "secret",
                model = "model"
            )
        )
    }

    @Test
    fun hasSuccessfulTest_detectsOnlySuccessResult() {
        assertTrue(SetupFlowPolicy.hasSuccessfulTest("连接成功！模型回复：OK"))
        assertFalse(SetupFlowPolicy.hasSuccessfulTest("连接失败：timeout"))
        assertFalse(SetupFlowPolicy.hasSuccessfulTest(null))
    }

    @Test
    fun connectionMessages_boundAndSanitizeProviderText() {
        val token = "sk-" + "a".repeat(24)
        val bearer = "Bearer " + "abcdefghijklmnop"
        val success = SetupFlowPolicy.connectionSuccessMessage(
            "<b>OK</b> $token " + "x".repeat(500)
        )
        val failure = SetupFlowPolicy.connectionFailureMessage("$bearer timeout")

        assertTrue(success.startsWith("连接成功！模型回复：OK ***"))
        assertTrue(success.length <= "连接成功！模型回复：".length + 160)
        assertFalse(success.contains(token))
        assertEquals("连接失败：Bearer *** timeout", failure)
    }

    @Test
    fun configurationDrafts_areBoundedBeforeEnteringUiState() {
        assertEquals(2_048, SetupFlowPolicy.normalizeApiUrlDraft("u".repeat(3_000)).length)
        assertEquals(4_096, SetupFlowPolicy.normalizeApiKeyDraft("k".repeat(5_000)).length)
        assertEquals(256, SetupFlowPolicy.normalizeModelDraft("m".repeat(500)).length)
        assertFalse(SetupFlowPolicy.canContinue("https://example.com", "k".repeat(4_097), "model"))
    }
}
