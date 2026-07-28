package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiErrorPolicyTest {

    @Test
    fun message_extractsNestedProviderMessage() {
        val message = ApiErrorPolicy.message(
            404,
            """{"error":{"message":"Model deepseek-example was not found"}}"""
        )

        assertEquals("API 请求失败（404）：Model deepseek-example was not found", message)
    }

    @Test
    fun message_redactsCommonCredentialFormats() {
        val fakeApiKey = "sk-" + "1234567890abcdef"
        val fakeBearerToken = "abcdefghijklmnop"
        val message = ApiErrorPolicy.message(
            401,
            "Invalid $fakeApiKey and Bearer $fakeBearerToken"
        )

        assertFalse(message.contains(fakeApiKey))
        assertFalse(message.contains(fakeBearerToken))
        assertTrue(message.contains("***"))
    }

    @Test
    fun message_stripsMarkupAndCollapsesWhitespace() {
        val message = ApiErrorPolicy.message(502, "<h1> Bad   gateway </h1>\n retry later")

        assertEquals("API 请求失败（502）：Bad gateway retry later", message)
    }

    @Test
    fun message_usesStatusSpecificFallback() {
        assertEquals(
            "API 请求失败（429）：请求过于频繁，请稍后重试",
            ApiErrorPolicy.message(429, null)
        )
    }
}
