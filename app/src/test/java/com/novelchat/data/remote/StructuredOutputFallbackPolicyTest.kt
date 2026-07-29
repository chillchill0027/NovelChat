package com.novelchat.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredOutputFallbackPolicyTest {

    @Test
    fun retry_acceptsLmStudioUnsupportedResponseFormatError() {
        assertTrue(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "http://192.168.31.84:1234/v1",
                statusCode = 400,
                errorBody = "response_format is unsupported for this model"
            )
        )
    }

    @Test
    fun retry_acceptsLocalValidationErrorForExtraJsonModeField() {
        assertTrue(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "http://127.0.0.1:1234/v1",
                statusCode = 422,
                errorBody = "response_format: extra fields not permitted"
            )
        )
    }

    @Test
    fun retry_acceptsLmStudioJsonSchemaOrTextTypeRequirement() {
        assertTrue(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "http://192.168.31.79:1234",
                statusCode = 400,
                errorBody = "{\"error\":\"'response_format.type' must be 'json_schema' or 'text'\"}"
            )
        )
    }

    @Test
    fun retry_acceptsLocalServerThatRejectsJsonSchema() {
        assertTrue(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "http://192.168.31.79:11434/v1",
                statusCode = 400,
                errorBody = "json_schema is unsupported"
            )
        )
        assertTrue(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "http://127.0.0.1:8000/v1",
                statusCode = 422,
                errorBody = "response_format.type must be 'json_object'"
            )
        )
    }

    @Test
    fun retry_rejectsRemoteAndUnrelatedErrors() {
        assertFalse(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "https://api.deepseek.com",
                statusCode = 400,
                errorBody = "response_format is unsupported"
            )
        )
        assertFalse(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "http://192.168.31.84:1234/v1",
                statusCode = 404,
                errorBody = "model not found"
            )
        )
        assertFalse(
            StructuredOutputFallbackPolicy.shouldRetryWithoutJsonMode(
                baseUrl = "http://192.168.31.84:1234/v1",
                statusCode = 401,
                errorBody = "response_format is unsupported"
            )
        )
    }
}
