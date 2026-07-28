package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatRequestPolicyTest {

    @Test
    fun reasoningEffort_disablesThinkingForLocalGemma4AndQwen3Models() {
        assertEquals(
            "none",
            ChatRequestPolicy.reasoningEffort(
                baseUrl = "http://192.168.1.20:1234/v1",
                model = "gemma4"
            )
        )
        assertEquals(
            "none",
            ChatRequestPolicy.reasoningEffort(
                baseUrl = "http://127.0.0.1:1234",
                model = "gemma-4-e4b-uncensored"
            )
        )
        assertEquals(
            "none",
            ChatRequestPolicy.reasoningEffort(
                baseUrl = "http://10.0.2.2:1234/v1",
                model = "qwen3.5-4b-uncensored"
            )
        )
    }

    @Test
    fun reasoningEffort_doesNotSendLocalCompatibilityFieldToRemoteOrOtherModels() {
        assertNull(
            ChatRequestPolicy.reasoningEffort(
                baseUrl = "https://api.example.com/v1",
                model = "gemma4"
            )
        )
        assertNull(
            ChatRequestPolicy.reasoningEffort(
                baseUrl = "http://192.168.1.20:1234/v1",
                model = "llama-3.3-70b"
            )
        )
    }

    @Test
    fun thinkingConfig_disablesDefaultThinkingForOfficialDeepSeekV4() {
        val config = ChatRequestPolicy.thinkingConfig(
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-v4-flash"
        )

        assertEquals("disabled", config?.type)
    }

    @Test
    fun thinkingConfig_supportsDeepSeekV4ProCaseInsensitively() {
        val config = ChatRequestPolicy.thinkingConfig(
            baseUrl = "https://api.deepseek.com",
            model = " DeepSeek-V4-Pro "
        )

        assertEquals("disabled", config?.type)
    }

    @Test
    fun thinkingConfig_doesNotSendVendorParameterToThirdPartyEndpoint() {
        assertNull(
            ChatRequestPolicy.thinkingConfig(
                baseUrl = "http://192.168.1.10:11434/v1",
                model = "deepseek-v4-flash"
            )
        )
    }

    @Test
    fun thinkingConfig_doesNotAffectOtherModels() {
        assertNull(
            ChatRequestPolicy.thinkingConfig(
                baseUrl = "https://api.deepseek.com",
                model = "custom-model"
            )
        )
    }
}
