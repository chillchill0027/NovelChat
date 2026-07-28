package com.novelchat.domain.engine

import com.novelchat.data.remote.CompletionLengthException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CharacterStateRequestPolicyTest {

    @Test
    fun execute_usesExpandedInitialBudget() = runBlocking {
        val budgets = mutableListOf<Int>()

        val result = CharacterStateRequestPolicy.execute { maxTokens ->
            budgets += maxTokens
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(listOf(1_800), budgets)
    }

    @Test
    fun execute_retriesLengthFailureOnceWithLargerBudget() = runBlocking {
        val budgets = mutableListOf<Int>()

        val result = CharacterStateRequestPolicy.execute { maxTokens ->
            budgets += maxTokens
            if (budgets.size == 1) throw CompletionLengthException("length")
            "recovered"
        }

        assertEquals("recovered", result)
        assertEquals(listOf(1_800, 2_600), budgets)
    }

    @Test
    fun execute_doesNotRetryUnrelatedFailure() {
        val budgets = mutableListOf<Int>()

        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                CharacterStateRequestPolicy.execute<String> { maxTokens ->
                    budgets += maxTokens
                    throw IllegalStateException("bad json")
                }
            }
        }
        assertEquals(listOf(1_800), budgets)
    }
}
