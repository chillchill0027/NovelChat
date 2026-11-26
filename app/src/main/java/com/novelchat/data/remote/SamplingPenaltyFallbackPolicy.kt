package com.novelchat.data.remote

internal object SamplingPenaltyFallbackPolicy {

    private val parameterMarkers = listOf(
        "presence_penalty",
        "presence penalty",
        "frequency_penalty",
        "frequency penalty"
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

    fun shouldRetryWithoutPenalties(
        statusCode: Int,
        errorBody: String?
    ): Boolean {
        if (statusCode !in setOf(400, 404, 415, 422, 501)) return false
        val normalized = errorBody?.lowercase().orEmpty()
        return parameterMarkers.any(normalized::contains) &&
            rejectionMarkers.any(normalized::contains)
    }
}
