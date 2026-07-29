package com.novelchat.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReasoningEffortFallbackPolicyTest {

    @Test
    fun retry_acceptsLocalServerUnsupportedParameterError() {
        assertTrue(
            ReasoningEffortFallbackPolicy.shouldRetryWithoutReasoningEffort(
                baseUrl = "http://192.168.31.79:1234/v1",
                statusCode = 400,
                errorBody = "reasoning_effort is an unsupported parameter"
            )
        )
    }

    @Test
    fun retry_acceptsLocalServerInvalidValueError() {
        assertTrue(
            ReasoningEffortFallbackPolicy.shouldRetryWithoutReasoningEffort(
                baseUrl = "http://127.0.0.1:1234/v1",
                statusCode = 422,
                errorBody = "Invalid 'reasoning_effort' value"
            )
        )
    }

    @Test
    fun retry_rejectsRemoteAuthAndUnrelatedErrors() {
        assertFalse(
            ReasoningEffortFallbackPolicy.shouldRetryWithoutReasoningEffort(
                baseUrl = "https://api.example.com/v1",
                statusCode = 400,
                errorBody = "reasoning_effort is unsupported"
            )
        )
        assertFalse(
            ReasoningEffortFallbackPolicy.shouldRetryWithoutReasoningEffort(
                baseUrl = "http://192.168.31.79:1234/v1",
                statusCode = 401,
                errorBody = "reasoning_effort is unsupported"
            )
        )
        assertFalse(
            ReasoningEffortFallbackPolicy.shouldRetryWithoutReasoningEffort(
                baseUrl = "http://192.168.31.79:1234/v1",
                statusCode = 400,
                errorBody = "model not found"
            )
        )
    }
}
