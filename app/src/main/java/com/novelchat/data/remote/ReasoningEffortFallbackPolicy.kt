package com.novelchat.data.remote

internal object ReasoningEffortFallbackPolicy {

    private val parameterMarkers = listOf(
        "reasoning_effort",
        "reasoning effort"
    )

    private val rejectionMarkers = listOf(
        "unsupported",
        "not support",
        "unknown",
        "unrecognized",
        "unrecognised",
        "unexpected",
        "not permitted",
        "not allowed",
        "extra input",
        "extra field",
        "invalid",
        "not implemented"
    )

    fun shouldRetryWithoutReasoningEffort(
        baseUrl: String,
        statusCode: Int,
        errorBody: String?
    ): Boolean {
        if (!ApiEndpointPolicy.isLocalEndpoint(baseUrl)) return false
        if (statusCode !in setOf(400, 404, 415, 422, 501)) return false

        val normalizedError = errorBody?.lowercase().orEmpty()
        return parameterMarkers.any(normalizedError::contains) &&
            rejectionMarkers.any(normalizedError::contains)
    }
}
