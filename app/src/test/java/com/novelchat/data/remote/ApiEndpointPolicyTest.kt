package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiEndpointPolicyTest {

    @Test
    fun validation_acceptsHttpsCloudEndpointWithKey() {
        assertNull(
            ApiEndpointPolicy.validationError(
                baseUrl = "https://api.deepseek.com",
                apiKey = "sk-test",
                model = "deepseek-v4-flash"
            )
        )
    }

    @Test
    fun validation_allowsUnkeyedLanHttpAndKeyedLoopbackHttp() {
        assertNull(ApiEndpointPolicy.validationError("http://192.168.31.84:1234", "", "local"))
        assertNull(ApiEndpointPolicy.validationError("http://127.0.0.1:1234", "local-token", "local"))
        assertNull(ApiEndpointPolicy.validationError("http://[::1]:1234", "local-token", "local"))
    }

    @Test
    fun validation_acceptsArbitraryLmStudioModelIdentifiers() {
        assertNull(ApiEndpointPolicy.validationError("http://192.168.31.84:1234/v1", "", "gamma4 e4b"))
        assertNull(ApiEndpointPolicy.validationError("http://192.168.31.84:1234/v1", "", "qwen3.5 4b"))
    }

    @Test
    fun validation_rejectsRemoteHttpEvenWithoutKey() {
        val error = ApiEndpointPolicy.validationError("http://api.example.com", "", "model")

        assertTrue(error.orEmpty().contains("HTTPS"))
    }

    @Test
    fun validation_requiresKeyForRemoteHttpsButNotLocalHttps() {
        val remoteError = ApiEndpointPolicy.validationError("https://api.example.com", "", "model")

        assertTrue(remoteError.orEmpty().contains("密钥"))
        assertNull(ApiEndpointPolicy.validationError("https://server.local", "", "model"))
    }

    @Test
    fun validation_rejectsKeyOverLanHttp() {
        val error = ApiEndpointPolicy.validationError(
            "http://192.168.31.84:1234",
            "secret",
            "model"
        )

        assertTrue(error.orEmpty().contains("API Key"))
    }

    @Test
    fun validation_rejectsMalformedOrCredentialBearingUrlsAndBlankModel() {
        assertTrue(ApiEndpointPolicy.validationError("deepseek.com", "key", "model").orEmpty().isNotBlank())
        assertTrue(ApiEndpointPolicy.validationError("https://user:pass@example.com", "", "model").orEmpty().isNotBlank())
        assertTrue(ApiEndpointPolicy.validationError("https://example.com:99999", "key", "model").orEmpty().contains("端口"))
        assertTrue(ApiEndpointPolicy.validationError("https://example.com?token=x", "", "model").orEmpty().isNotBlank())
        assertTrue(ApiEndpointPolicy.validationError("https://example.com", "", " ").orEmpty().isNotBlank())
    }

    @Test
    fun normalizeBaseUrl_removesTrailingSlashAndTerminalV1() {
        assertEquals("https://api.deepseek.com", ApiEndpointPolicy.normalizeBaseUrl(" https://api.deepseek.com/ "))
        assertEquals("https://api.groq.com/openai", ApiEndpointPolicy.normalizeBaseUrl("https://api.groq.com/openai/v1/"))
    }

    @Test
    fun localEndpoint_recognizesPrivateHostsOnly() {
        assertTrue(ApiEndpointPolicy.isLocalEndpoint("http://server.local:8080"))
        assertTrue(ApiEndpointPolicy.isLocalEndpoint("http://172.20.1.4:8080"))
        assertTrue(ApiEndpointPolicy.isLocalEndpoint("http://[fd00::12]:8080"))
        assertFalse(ApiEndpointPolicy.isLocalEndpoint("https://api.deepseek.com"))
    }

    @Test
    fun readTimeout_allowsSlowLocalGenerationWithoutDelayingCloudFailures() {
        assertEquals(
            900L,
            ApiEndpointPolicy.readTimeoutSeconds("http://192.168.31.79:1234")
        )
        assertEquals(
            180L,
            ApiEndpointPolicy.readTimeoutSeconds("https://api.deepseek.com")
        )
    }
}
