package com.novelchat.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ApiResponsePolicyTest {

    @Test
    fun nextTotal_acceptsResponseAtLimit() {
        assertEquals(
            ApiResponsePolicy.MAX_RESPONSE_CHARS,
            ApiResponsePolicy.nextTotal(
                ApiResponsePolicy.MAX_RESPONSE_CHARS - 10,
                10
            )
        )
    }

    @Test
    fun nextTotal_rejectsResponseBeyondLimit() {
        assertThrows(ApiResponseTooLargeException::class.java) {
            ApiResponsePolicy.nextTotal(ApiResponsePolicy.MAX_RESPONSE_CHARS, 1)
        }
    }

    @Test
    fun nextTotal_handlesIntegerOverflowWithoutWrapping() {
        assertThrows(ApiResponseTooLargeException::class.java) {
            ApiResponsePolicy.nextTotal(Int.MAX_VALUE, Int.MAX_VALUE)
        }
    }
}
