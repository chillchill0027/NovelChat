package com.novelchat.data.remote

internal object StructuredOutputFallbackPolicy {

    private val formatMarkers = listOf(
        "response_format",
        "response format",
        "json_object",
        "json_schema",
        "json mode",
        "structured output"
    )

    private val unsupportedMarkers = listOf(
        "unsupported",
        "not support",
        "unknown",
        "unrecognized",
        "unrecognised",
        "unexpected",
        "not permitted",
        "extra input",
        "extra field",
        "invalid",
        "not implemented",
        "must be omitted",
        "must be null"
    )

    private val incompatibleTypeMarkers = listOf(
        "must be 'json_schema'",
        "must be \"json_schema\"",
        "must be json_schema",
        "expected 'json_schema'",
        "expected \"json_schema\"",
        "expected json_schema",
        "must be 'json_object'",
        "must be \"json_object\"",
        "must be json_object",
        "expected 'json_object'",
        "expected \"json_object\"",
        "expected json_object"
    )

    fun shouldRetryWithoutJsonMode(
        baseUrl: String,
        statusCode: Int,
        errorBody: String?
    ): Boolean {
        if (!ApiEndpointPolicy.isLocalEndpoint(baseUrl)) return false
        if (statusCode !in setOf(400, 404, 415, 422, 501)) return false

        val normalizedError = errorBody?.lowercase().orEmpty()
        return formatMarkers.any(normalizedError::contains) &&
            (
                unsupportedMarkers.any(normalizedError::contains) ||
                    incompatibleTypeMarkers.any(normalizedError::contains)
                )
    }
}
