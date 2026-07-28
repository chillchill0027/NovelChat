package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiRetryPolicyTest {

    @Test
    fun transientStatus_onlyIncludesRetryableHttpFailures() {
        assertTrue(ApiRetryPolicy.isTransientStatus(408))
        assertTrue(ApiRetryPolicy.isTransientStatus(429))
        assertTrue(ApiRetryPolicy.isTransientStatus(503))
        assertFalse(ApiRetryPolicy.isTransientStatus(400))
        assertFalse(ApiRetryPolicy.isTransientStatus(401))
    }

    @Test
    fun delayMillis_honorsServerSecondsWithinBounds() {
        assertEquals(2_000L, ApiRetryPolicy.delayMillis("2", attempt = 0))
        assertEquals(10_000L, ApiRetryPolicy.delayMillis("120", attempt = 0))
    }

    @Test
    fun delayMillis_usesShortFallbackForInvalidHeader() {
        assertEquals(750L, ApiRetryPolicy.delayMillis("tomorrow", attempt = 0))
        assertEquals(1_500L, ApiRetryPolicy.delayMillis(null, attempt = 1))
    }
}
