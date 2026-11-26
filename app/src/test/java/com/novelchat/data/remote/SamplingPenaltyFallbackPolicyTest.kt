package com.novelchat.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SamplingPenaltyFallbackPolicyTest {

    @Test
    fun shouldRetryWithoutPenalties_matchesUnsupportedPenaltyErrors() {
        assertTrue(
            SamplingPenaltyFallbackPolicy.shouldRetryWithoutPenalties(
                statusCode = 400,
                errorBody = """{"error":{"message":"Extra inputs are not permitted: presence_penalty"}}"""
            )
        )
        assertTrue(
            SamplingPenaltyFallbackPolicy.shouldRetryWithoutPenalties(
                statusCode = 422,
                errorBody = """{"detail":"Unknown field frequency_penalty"}"""
            )
        )
    }

    @Test
    fun shouldRetryWithoutPenalties_ignoresUnrelatedErrors() {
        assertFalse(
            SamplingPenaltyFallbackPolicy.shouldRetryWithoutPenalties(
                statusCode = 500,
                errorBody = "presence_penalty server error"
            )
        )
        assertFalse(
            SamplingPenaltyFallbackPolicy.shouldRetryWithoutPenalties(
                statusCode = 400,
                errorBody = "Invalid model name"
            )
        )
    }
}
